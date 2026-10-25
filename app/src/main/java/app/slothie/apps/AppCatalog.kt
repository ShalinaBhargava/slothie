package app.slothie.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.core.content.edit

enum class Category(val label: String, val distractionByDefault: Boolean) {
    FEED("Feed", true),
    VIDEO("Video", true),
    GAME("Game", true),
    CHAT("Chat", false),
    TOOL("Tool", false),
    WORK("Work", false),
    HEALTH("Health", false),
    OTHER("Other", false);

    companion object {
        fun parse(s: String?): Category = entries.firstOrNull { it.name.equals(s?.trim(), ignoreCase = true) } ?: OTHER
    }
}

data class AppInfo(val pkg: String, val label: String, val category: Category, val distraction: Boolean, val userSet: Boolean, val modelSet: Boolean)

/**
 * Names and categories for the apps that show up in usage. Categories come from three places,
 * in order: what you set by hand, what Slothie (the model) guessed, and a tiny keyword fallback
 * so the app is useful before any model is loaded.
 */
class AppCatalog(private val context: Context) {
    private val prefs = context.getSharedPreferences("apps", Context.MODE_PRIVATE)
    private val pm: PackageManager = context.packageManager
    private val labelCache = HashMap<String, String>()

    fun label(pkg: String): String = labelCache.getOrPut(pkg) {
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    fun icon(pkg: String): Drawable? = try { pm.getApplicationIcon(pkg) } catch (_: Exception) { null }

    fun info(pkg: String): AppInfo {
        val userCat = prefs.getString("user.cat.$pkg", null)
        val modelCat = prefs.getString("model.cat.$pkg", null)
        val cat = when {
            userCat != null -> Category.parse(userCat)
            modelCat != null -> Category.parse(modelCat)
            else -> guess(pkg)
        }
        val distraction = if (prefs.contains("user.dis.$pkg")) prefs.getBoolean("user.dis.$pkg", false) else cat.distractionByDefault
        return AppInfo(pkg, label(pkg), cat, distraction, userCat != null || prefs.contains("user.dis.$pkg"), modelCat != null)
    }

    fun isDistraction(pkg: String) = info(pkg).distraction

    fun setUserCategory(pkg: String, cat: Category) = prefs.edit {
        putString("user.cat.$pkg", cat.name)
        putBoolean("user.dis.$pkg", cat.distractionByDefault)
    }

    fun setUserDistraction(pkg: String, value: Boolean) = prefs.edit { putBoolean("user.dis.$pkg", value) }

    fun setModelCategory(pkg: String, cat: Category) = prefs.edit { putString("model.cat.$pkg", cat.name) }

    fun resetUser(pkg: String) = prefs.edit { remove("user.cat.$pkg"); remove("user.dis.$pkg") }

    private fun guess(pkg: String): Category {
        val p = pkg.lowercase()
        val l = label(pkg).lowercase()
        fun has(vararg k: String) = k.any { it in p || it in l }
        return when {
            has("instagram", "facebook", "twitter", "com.twitter", "reddit", "snapchat", "tiktok", "threads", "pinterest", "linkedin") -> Category.FEED
            has("youtube", "netflix", "primevideo", "hotstar", "disney", "twitch", "jiocinema", "video") -> Category.VIDEO
            has("game", "clash", "candy", "pubg", "royale", "chess", "ludo", "wordle") -> Category.GAME
            has("whatsapp", "telegram", "messenger", "signal", "discord", "messag", "slack", "teams") -> Category.CHAT
            has("gmail", "outlook", "calendar", "docs", "sheets", "notion", "jira", "github") -> Category.WORK
            has("fit", "health", "strava", "sleep", "meditat", "headspace", "calm") -> Category.HEALTH
            has("maps", "camera", "photos", "settings", "clock", "calculator", "chrome", "browser", "files", "pay", "upi", "bank", "uber", "ola", "zomato", "swiggy") -> Category.TOOL
            else -> Category.OTHER
        }
    }
}
