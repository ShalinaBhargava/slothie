package app.slothie.ai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The open-weight brain. Gemma 4 E2B (Apache 2.0) runs on the phone through LiteRT-LM; nothing
 * is sent anywhere. The engine loads lazily on first use and stays warm.
 *
 * Model file lives in the app's external files dir so it can arrive two ways: the in-app
 * download from Hugging Face, or `adb push` during development.
 */
class SlothBrain(private val context: Context) {

    sealed class ModelState {
        data object Missing : ModelState()
        data class Downloading(val progress: Float, val downloadedMb: Long, val totalMb: Long) : ModelState()
        data object OnDisk : ModelState()
        data object Loading : ModelState()
        data class Ready(val backend: String) : ModelState()
        data class Error(val message: String) : ModelState()
    }

    private val _state = MutableStateFlow<ModelState>(if (modelFile().exists()) ModelState.OnDisk else ModelState.Missing)
    val state: StateFlow<ModelState> = _state

    private var engine: Engine? = null
    private var backendName = ""
    private val lock = Mutex()

    fun modelFile(): File = File(context.getExternalFilesDir(null), "models/$MODEL_FILE")

    fun refreshState() {
        if (engine != null) return
        val f = modelFile()
        _state.value = when {
            f.exists() && f.length() > 1_000_000_000L -> ModelState.OnDisk
            else -> ModelState.Missing
        }
    }

    // ---------------------------------------------------------------- download

    private val prefs = context.getSharedPreferences("brain", Context.MODE_PRIVATE)

    fun startDownload() {
        val dm = context.getSystemService(DownloadManager::class.java)
        modelFile().parentFile?.mkdirs()
        modelFile().delete()
        val req = DownloadManager.Request(Uri.parse(MODEL_URL))
            .setTitle("Slothie brain (Gemma 4 E2B)")
            .setDescription("2.6 GB, downloads once, runs offline forever")
            .setDestinationInExternalFilesDir(context, null, "models/$MODEL_FILE")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setAllowedOverMetered(false)
        val id = dm.enqueue(req)
        prefs.edit().putLong("download_id", id).apply()
        _state.value = ModelState.Downloading(0f, 0, 2600)
    }

    /** Poll the DownloadManager; call every second or so while [ModelState.Downloading]. */
    fun pollDownload() {
        val id = prefs.getLong("download_id", -1L)
        if (id < 0) return
        val dm = context.getSystemService(DownloadManager::class.java)
        dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) {
                prefs.edit().remove("download_id").apply(); refreshState(); return
            }
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    prefs.edit().remove("download_id").apply(); _state.value = ModelState.OnDisk
                }
                DownloadManager.STATUS_FAILED -> {
                    val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    prefs.edit().remove("download_id").apply()
                    _state.value = ModelState.Error("Download failed (code $reason). Try again on Wi-Fi.")
                }
                else -> {
                    val p = if (total > 0) done.toFloat() / total else 0f
                    _state.value = ModelState.Downloading(p, done / 1_000_000, if (total > 0) total / 1_000_000 else 2600)
                }
            }
        }
    }

    fun isDownloading(): Boolean = prefs.getLong("download_id", -1L) >= 0

    // ---------------------------------------------------------------- engine

    suspend fun ensureLoaded(): Boolean = lock.withLock {
        if (engine != null) return true
        val f = modelFile()
        if (!f.exists()) { _state.value = ModelState.Missing; return false }
        _state.value = ModelState.Loading
        withContext(Dispatchers.IO) {
            // GPU first (fast prefill on Tensor/Adreno), CPU as the safe fallback.
            val attempts = listOf("GPU" to { Backend.GPU() }, "CPU" to { Backend.CPU() })
            var lastError: Throwable? = null
            for ((name, make) in attempts) {
                try {
                    val e = Engine(EngineConfig(modelPath = f.absolutePath, backend = make(), cacheDir = context.cacheDir.path))
                    e.initialize()
                    engine = e
                    backendName = name
                    _state.value = ModelState.Ready(name)
                    Log.i(TAG, "Gemma loaded on $name")
                    return@withContext
                } catch (t: Throwable) {
                    Log.w(TAG, "backend $name failed: ${t.message}")
                    lastError = t
                }
            }
            _state.value = ModelState.Error("Could not start the model: ${lastError?.message ?: "unknown error"}")
        }
        engine != null
    }

    /**
     * Stream a single answer. A fresh conversation per request: every call carries its own
     * complete context, so nothing from a previous answer can leak into this one.
     */
    fun stream(system: String, user: String, temperature: Double = 0.6, maxTokens: Int = 400): Flow<String> = flow {
        if (!ensureLoaded()) throw IllegalStateException("model not loaded")
        val e = engine ?: throw IllegalStateException("model not loaded")
        val config = ConversationConfig(
            systemInstruction = Contents.of(system),
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = temperature),
            maxOutputToken = maxTokens,
            // Gemma 4 can "think" first; a sloth note does not need a scratchpad, and this keeps
            // the first token quick.
            thinkingConfig = ThinkingConfig(enableThinking = false),
        )
        e.createConversation(config).use { conv ->
            var acc = ""
            conv.sendMessageAsync(user, extraContext = mapOf("enable_thinking" to false))
                .catch { throw it }
                .collect { msg ->
                    val chunk = msg.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                    // Be tolerant of either delta or cumulative streaming.
                    acc = if (chunk.startsWith(acc) && chunk.length >= acc.length) chunk else acc + chunk
                    emit(acc)
                }
        }
    }.flowOn(Dispatchers.IO)

    fun close() {
        engine?.close(); engine = null
        refreshState()
    }

    companion object {
        private const val TAG = "SlothBrain"
        const val MODEL_FILE = "gemma-4-E2B-it.litertlm"
        const val MODEL_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
        const val MODEL_PAGE = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm"
    }
}
