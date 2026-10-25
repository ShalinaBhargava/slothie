package app.slothie.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process

/**
 * Turns the system's usage event log into foreground [Session]s and unlock timestamps.
 *
 * Only ACTIVITY_RESUMED / PAUSED / STOPPED and screen/keyguard events are read. Nothing is
 * stored by this class; the matrix is recomputed on every refresh from the OS log, which keeps
 * the app stateless and the data on the phone.
 */
class UsageReader(private val context: Context) {

    private val homePackages: Set<String> by lazy {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
    }

    private val ignored: Set<String> by lazy {
        homePackages + setOf(context.packageName, "com.android.systemui", "android")
    }

    fun hasPermission(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    data class Raw(val sessions: List<Session>, val unlocks: List<Long>)

    fun read(begin: Long, end: Long): Raw {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(begin, end)
        val sessions = ArrayList<Session>()
        val unlocks = ArrayList<Long>()
        val open = HashMap<String, Long>() // pkg -> resumed-at
        val ev = UsageEvents.Event()

        fun close(pkg: String, at: Long) {
            val start = open.remove(pkg) ?: return
            if (at > start) sessions += Session(pkg, start, at)
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            when (ev.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (ev.packageName in ignored) continue
                    // One thing is in front at a time: anything else still open ends here.
                    for (p in open.keys.toList()) if (p != ev.packageName) close(p, ev.timeStamp)
                    open.putIfAbsent(ev.packageName, ev.timeStamp)
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    close(ev.packageName, ev.timeStamp)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE, UsageEvents.Event.KEYGUARD_SHOWN, UsageEvents.Event.DEVICE_SHUTDOWN ->
                    for (p in open.keys.toList()) close(p, ev.timeStamp)
                UsageEvents.Event.KEYGUARD_HIDDEN -> unlocks += ev.timeStamp
            }
        }
        for (p in open.keys.toList()) close(p, end)
        // Drop blips: an app flashing for under 3 seconds is a notification tap, not use.
        return Raw(sessions.filter { it.end - it.start >= 3_000 }, unlocks)
    }
}
