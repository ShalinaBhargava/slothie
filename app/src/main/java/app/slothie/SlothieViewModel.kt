package app.slothie

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.slothie.ai.Prompts
import app.slothie.ai.SlothBrain
import app.slothie.apps.AppCatalog
import app.slothie.apps.AppInfo
import app.slothie.apps.Category
import app.slothie.nudge.Nap
import app.slothie.nudge.NudgeScheduler
import app.slothie.ui.Mood
import app.slothie.usage.Profile
import app.slothie.usage.Stats
import app.slothie.usage.UsageReader
import app.slothie.usage.hourLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val hasUsageAccess: Boolean = false,
    val hasDndAccess: Boolean = false,
    val profile: Profile? = null,
    val days: Int = 7,
    val apps: List<Pair<AppInfo, Double>> = emptyList(),
    val mood: Mood = Mood.Chill,
    val note: String = "",
    val noteStreaming: Boolean = false,
    val noteError: String? = null,
    val sorting: Boolean = false,
    val sortMessage: String? = null,
    val nudgesEnabled: Boolean = true,
    val nappingUntil: Long = 0L,
    val lastRefresh: Long = 0L,
)

class SlothieViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx: Context get() = getApplication()
    private val reader = UsageReader(ctx)
    val catalog = AppCatalog(ctx)
    val brain = SlothBrain(ctx)
    private val settings = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _ui = MutableStateFlow(UiState(days = settings.getInt("days", 7), nudgesEnabled = NudgeScheduler.enabled(ctx)))
    val ui: StateFlow<UiState> = _ui

    private var noteJob: Job? = null

    init {
        viewModelScope.launch {
            while (true) {
                if (brain.isDownloading()) brain.pollDownload()
                delay(1000)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val has = reader.hasPermission()
            val dnd = Nap.hasAccess(ctx)
            if (!has) {
                _ui.value = _ui.value.copy(hasUsageAccess = false, hasDndAccess = dnd, profile = null, nappingUntil = Nap.napping(ctx))
                return@launch
            }
            val days = _ui.value.days
            val profile = withContext(Dispatchers.Default) {
                val now = System.currentTimeMillis()
                val begin = Stats.startOfDay(java.time.LocalDate.now().minusDays((days - 1).toLong()), java.time.ZoneId.systemDefault())
                val raw = reader.read(begin, now)
                Stats.profile(raw.sessions, raw.unlocks, days, now)
            }
            val apps = profile.appTotals.entries.sortedByDescending { it.value }
                .map { catalog.info(it.key) to it.value / days }
            _ui.value = _ui.value.copy(
                hasUsageAccess = true, hasDndAccess = dnd, profile = profile, apps = apps,
                mood = moodFor(profile), nappingUntil = Nap.napping(ctx), lastRefresh = System.currentTimeMillis(),
            )
            NudgeScheduler.schedule(
                ctx,
                profile.riskHours.map { it.hour },
                profile.riskHours.associate { it.hour to (it.probability * 100).toInt() },
                profile.riskHours.associate { it.hour to it.avgMinutes.toInt() },
                profile.riskHours.associate { r -> r.hour to r.topApps.take(2).joinToString(" and ") { catalog.label(it.first) } },
            )
            brain.refreshState()
        }
    }

    private fun moodFor(p: Profile): Mood {
        val inRisk = p.inRiskHour() != null
        val upcoming = p.upcomingRisk() != null
        return when {
            p.pace > 1.5 || (inRisk && p.pace > 1.1) -> Mood.Alarmed
            upcoming || p.pace > 1.15 -> Mood.Peeking
            p.pace < 0.6 && p.todayMinutes < p.avgDailyMinutes * 0.5 -> Mood.Sleeping
            else -> Mood.Chill
        }
    }

    fun setDays(d: Int) {
        settings.edit().putInt("days", d).apply()
        _ui.value = _ui.value.copy(days = d)
        refresh()
    }

    fun setNudges(v: Boolean) {
        NudgeScheduler.setEnabled(ctx, v)
        _ui.value = _ui.value.copy(nudgesEnabled = v)
    }

    fun napNow(minutes: Int) {
        Nap.start(ctx, minutes)
        _ui.value = _ui.value.copy(nappingUntil = Nap.napping(ctx), hasDndAccess = Nap.hasAccess(ctx))
    }

    fun endNap() {
        Nap.end(ctx)
        _ui.value = _ui.value.copy(nappingUntil = 0L)
    }

    // ------------------------------------------------------------------ model

    fun askSloth() {
        val p = _ui.value.profile ?: return
        noteJob?.cancel()
        _ui.value = _ui.value.copy(note = "", noteStreaming = true, noteError = null)
        val json = p.summaryJson({ catalog.label(it) }, { catalog.isDistraction(it) })
        noteJob = viewModelScope.launch {
            brain.stream(Prompts.SYSTEM_SLOTH, Prompts.dailyNote(json))
                .catch { e -> _ui.value = _ui.value.copy(noteError = e.message ?: "The sloth fell asleep mid-sentence.") }
                .onCompletion { _ui.value = _ui.value.copy(noteStreaming = false) }
                .collect { text -> _ui.value = _ui.value.copy(note = text) }
        }
    }

    fun sortApps() {
        val apps = _ui.value.apps.map { it.first }.filter { !it.userSet }.take(40)
        if (apps.isEmpty()) return
        _ui.value = _ui.value.copy(sorting = true, sortMessage = null)
        viewModelScope.launch {
            var text = ""
            brain.stream(Prompts.SYSTEM_SORTER, Prompts.sortApps(apps.map { it.pkg to it.label }), temperature = 0.2, maxTokens = 1200)
                .catch { e -> _ui.value = _ui.value.copy(sortMessage = e.message ?: "Sorting failed.") }
                .onCompletion {
                    val parsed = Prompts.parseSort(text)
                    for ((pkg, cat) in parsed) catalog.setModelCategory(pkg, cat)
                    _ui.value = _ui.value.copy(
                        sorting = false,
                        sortMessage = if (parsed.isEmpty()) "Slothie could not sort these. Try again." else "Slothie sorted ${parsed.size} apps. Fix any it got wrong.",
                    )
                    refresh()
                }
                .collect { text = it }
        }
    }

    fun setCategory(pkg: String, cat: Category) { catalog.setUserCategory(pkg, cat); refresh() }
    fun setDistraction(pkg: String, v: Boolean) { catalog.setUserDistraction(pkg, v); refresh() }

    fun startDownload() = brain.startDownload()
    fun loadModel() { viewModelScope.launch { brain.ensureLoaded() } }
    fun deleteModel() { brain.close(); brain.modelFile().delete(); brain.refreshState() }

    fun riskHeadline(): String? {
        val p = _ui.value.profile ?: return null
        val r = p.riskHours.firstOrNull() ?: return null
        return "${hourLabel(r.hour)}, on ${(r.probability * 100).toInt()}% of days"
    }

    override fun onCleared() { brain.close() }
}
