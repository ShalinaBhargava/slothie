package app.slothie.nudge

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import app.slothie.MainActivity
import app.slothie.R
import app.slothie.SlothieApp
import app.slothie.usage.hourLabel
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A nudge is a notification a few minutes before one of your risk hours, carrying a one-tap
 * "nap" that holds Do Not Disturb for a while. Alarms are inexact on purpose: a window of ten
 * minutes needs no special permission and "a bit before 11 PM" is all a sloth needs.
 */
object NudgeScheduler {
    private const val LEAD_MINUTES = 12L
    private const val WINDOW_MS = 10 * 60_000L

    private fun prefs(c: Context) = c.getSharedPreferences("nudges", Context.MODE_PRIVATE)

    fun enabled(c: Context) = prefs(c).getBoolean("enabled", true)
    fun setEnabled(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("enabled", v).apply()
        if (v) rearmAll(c) else cancelAll(c)
    }

    /** Remember the hours and (re)arm one alarm per hour. */
    fun schedule(c: Context, hours: List<Int>, chance: Map<Int, Int>, minutes: Map<Int, Int>, apps: Map<Int, String>) {
        val p = prefs(c).edit()
        p.putString("hours", hours.joinToString(","))
        for (h in hours) {
            p.putInt("chance.$h", chance[h] ?: 0)
            p.putInt("minutes.$h", minutes[h] ?: 0)
            p.putString("apps.$h", apps[h] ?: "")
        }
        p.apply()
        cancelAll(c)
        if (enabled(c)) for (h in hours) arm(c, h)
    }

    fun rearmAll(c: Context) {
        val hours = prefs(c).getString("hours", "")!!.split(',').filter { it.isNotBlank() }.map { it.toInt() }
        if (enabled(c)) for (h in hours) arm(c, h)
    }

    fun arm(c: Context, hour: Int) {
        val am = c.getSystemService(AlarmManager::class.java)
        val zone = ZoneId.systemDefault()
        var at = LocalDateTime.now(zone).with(LocalTime.of(hour, 0)).minusMinutes(LEAD_MINUTES)
        if (!at.isAfter(LocalDateTime.now(zone))) at = at.plusDays(1)
        val trigger = at.atZone(zone).toInstant().toEpochMilli()
        am.setWindow(AlarmManager.RTC_WAKEUP, trigger, WINDOW_MS, pending(c, hour))
    }

    fun cancelAll(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        for (h in 0 until 24) am.cancel(pending(c, h))
    }

    private fun pending(c: Context, hour: Int): PendingIntent {
        val i = Intent(c, NudgeReceiver::class.java).putExtra("hour", hour)
        return PendingIntent.getBroadcast(c, 100 + hour, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun describe(c: Context, hour: Int): Triple<Int, Int, String> {
        val p = prefs(c)
        return Triple(p.getInt("chance.$hour", 0), p.getInt("minutes.$hour", 0), p.getString("apps.$hour", "") ?: "")
    }
}

class NudgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hour = intent.getIntExtra("hour", -1)
        if (hour < 0) return
        val (chance, minutes, apps) = NudgeScheduler.describe(context, hour)
        val nm = context.getSystemService(NotificationManager::class.java)
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = buildString {
            append("You are on the phone at this hour on $chance% of days, usually about $minutes min")
            if (apps.isNotBlank()) append(", mostly $apps")
            append(". Want me to hold Do Not Disturb for a while?")
        }
        val n = NotificationCompat.Builder(context, SlothieApp.CHANNEL_NUDGES)
            .setSmallIcon(R.drawable.ic_sloth_small)
            .setContentTitle("Your ${hourLabel(hour)} slump starts soon")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .addAction(0, "Nap 30 min", Nap.pending(context, Nap.ACTION_START, 30))
            .addAction(0, "Nap 60 min", Nap.pending(context, Nap.ACTION_START, 60))
            .setAutoCancel(true)
            .build()
        nm.notify(1000 + hour, n)
        // Same time tomorrow.
        NudgeScheduler.arm(context, hour)
    }
}

/** Nap = Do Not Disturb, held by Slothie for a fixed time, then let go. */
object Nap {
    const val ACTION_START = "app.slothie.NAP_START"
    const val ACTION_END = "app.slothie.NAP_END"
    private const val NOTIF_ID = 2000

    fun pending(c: Context, action: String, minutes: Int = 0): PendingIntent {
        val i = Intent(c, NapReceiver::class.java).setAction(action).putExtra("minutes", minutes)
        val code = if (action == ACTION_START) 200 + minutes else 299
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun hasAccess(c: Context) = c.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun napping(c: Context): Long = c.getSharedPreferences("nap", Context.MODE_PRIVATE).getLong("until", 0L)
        .takeIf { it > System.currentTimeMillis() } ?: 0L

    fun start(c: Context, minutes: Int) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            val open = PendingIntent.getActivity(
                c, 2, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            nm.notify(
                NOTIF_ID, NotificationCompat.Builder(c, SlothieApp.CHANNEL_NUDGES)
                    .setSmallIcon(R.drawable.ic_sloth_small)
                    .setContentTitle("I need Do Not Disturb access to nap")
                    .setContentText("Tap to allow it in Settings, then try again.")
                    .setContentIntent(open).setAutoCancel(true).build(),
            )
            return
        }
        val until = System.currentTimeMillis() + minutes * 60_000L
        c.getSharedPreferences("nap", Context.MODE_PRIVATE).edit()
            .putInt("previous_filter", nm.currentInterruptionFilter).putLong("until", until).apply()
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        c.getSystemService(AlarmManager::class.java)
            .setWindow(AlarmManager.RTC_WAKEUP, until, 60_000L, pending(c, ACTION_END))
        val time = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(until), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("h:mm a"))
        nm.notify(
            NOTIF_ID, NotificationCompat.Builder(c, SlothieApp.CHANNEL_NAP)
                .setSmallIcon(R.drawable.ic_sloth_small)
                .setContentTitle("Napping until $time")
                .setContentText("Do Not Disturb is on. I will let go by myself.")
                .setOngoing(true)
                .addAction(0, "End nap", pending(c, ACTION_END))
                .build(),
        )
    }

    fun end(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val p = c.getSharedPreferences("nap", Context.MODE_PRIVATE)
        if (nm.isNotificationPolicyAccessGranted) {
            val prev = p.getInt("previous_filter", NotificationManager.INTERRUPTION_FILTER_ALL)
            nm.setInterruptionFilter(if (prev == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) NotificationManager.INTERRUPTION_FILTER_ALL else prev)
        }
        p.edit().remove("until").apply()
        nm.cancel(NOTIF_ID)
    }
}

class NapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Nap.ACTION_START -> Nap.start(context, intent.getIntExtra("minutes", 30).coerceIn(5, 180))
            Nap.ACTION_END -> Nap.end(context)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) NudgeScheduler.rearmAll(context)
    }
}
