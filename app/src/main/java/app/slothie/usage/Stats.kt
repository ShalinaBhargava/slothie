package app.slothie.usage

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** One stretch of one app in the foreground. Times are epoch millis. */
data class Session(val pkg: String, val start: Long, val end: Long) {
    val minutes: Double get() = (end - start) / 60_000.0
}

/**
 * Everything the UI and the model are allowed to know, computed deterministically from
 * sessions. The model is handed [summaryJson] and nothing else, so it cannot invent a number
 * that is not in here.
 */
class Profile(
    val days: Int,
    val zone: ZoneId,
    val now: Long,
    /** minutes[dayIndex][hour], dayIndex 0 = oldest day, last = today. */
    val minutes: Array<DoubleArray>,
    val dates: List<LocalDate>,
    /** 0..1: share of fully observed days where this hour had at least [HOUR_THRESHOLD_MIN] minutes. */
    val hourProbability: DoubleArray,
    val avgMinutesByHour: DoubleArray,
    val riskHours: List<RiskHour>,
    val appTotals: Map<String, Double>,
    val appToday: Map<String, Double>,
    val topAppsByHour: Map<Int, List<Pair<String, Double>>>,
    val todayMinutes: Double,
    val baselineSameTime: Double,
    val avgDailyMinutes: Double,
    val pickupsToday: Int,
    val avgPickups: Double,
    val longestBreakTodayMin: Double,
) {
    /** >1 means ahead of your usual pace at this time of day. */
    val pace: Double get() = if (baselineSameTime <= 1.0) 1.0 else todayMinutes / baselineSameTime

    val currentHour: Int get() = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).hour

    /** The risk hour that starts within the next [withinMinutes], if any. */
    fun upcomingRisk(withinMinutes: Int = 30): RiskHour? {
        val z = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        val minutesToNextHour = 60 - z.minute
        val next = (z.hour + 1) % 24
        return if (minutesToNextHour <= withinMinutes) riskHours.firstOrNull { it.hour == next } else null
    }

    fun inRiskHour(): RiskHour? = riskHours.firstOrNull { it.hour == currentHour }

    fun summaryJson(labels: (String) -> String, isDistraction: (String) -> Boolean): String {
        val top = appTotals.entries.sortedByDescending { it.value }.take(6)
        val s = Summary(
            days_observed = days,
            local_time_now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalTime().withSecond(0).withNano(0).toString(),
            today_minutes = todayMinutes.toInt(),
            usual_minutes_by_this_time = baselineSameTime.toInt(),
            usual_minutes_per_day = avgDailyMinutes.toInt(),
            pickups_today = pickupsToday,
            usual_pickups_per_day = avgPickups.toInt(),
            longest_break_today_minutes = longestBreakTodayMin.toInt(),
            risk_hours = riskHours.map {
                RiskHourSummary(
                    hour = hourLabel(it.hour),
                    chance_percent = (it.probability * 100).toInt(),
                    usual_minutes = it.avgMinutes.toInt(),
                    top_apps = it.topApps.map { (p, _) -> labels(p) },
                )
            },
            top_apps_week = top.map { (p, m) ->
                AppSummary(labels(p), (m / days).toInt(), isDistraction(p))
            },
        )
        return Json.encodeToString(s)
    }

    companion object {
        const val HOUR_THRESHOLD_MIN = 5.0
    }
}

data class RiskHour(
    val hour: Int,
    val probability: Double,
    val avgMinutes: Double,
    val topApps: List<Pair<String, Double>>,
)

@Serializable
private data class Summary(
    val days_observed: Int,
    val local_time_now: String,
    val today_minutes: Int,
    val usual_minutes_by_this_time: Int,
    val usual_minutes_per_day: Int,
    val pickups_today: Int,
    val usual_pickups_per_day: Int,
    val longest_break_today_minutes: Int,
    val risk_hours: List<RiskHourSummary>,
    val top_apps_week: List<AppSummary>,
)

@Serializable
private data class RiskHourSummary(val hour: String, val chance_percent: Int, val usual_minutes: Int, val top_apps: List<String>)

@Serializable
private data class AppSummary(val app: String, val minutes_per_day: Int, val distraction: Boolean)

fun hourLabel(h: Int): String = when {
    h == 0 -> "12 AM"
    h < 12 -> "$h AM"
    h == 12 -> "12 PM"
    else -> "${h - 12} PM"
}

fun hourRangeLabel(h: Int): String = hourLabel(h) + " to " + hourLabel((h + 1) % 24)

fun minutesLabel(m: Double): String {
    val total = m.toInt()
    val h = total / 60
    val mm = total % 60
    return if (h == 0) "${mm}m" else "${h}h ${mm}m"
}

object Stats {

    /**
     * Fold sessions into a per-day, per-hour matrix and derive the profile.
     *
     * @param sessions foreground sessions, any order
     * @param unlocks epoch millis of each phone unlock
     * @param days how many calendar days back to include (today counts as one)
     */
    fun profile(
        sessions: List<Session>,
        unlocks: List<Long>,
        days: Int,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Profile {
        val today = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
        val dates = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }
        val dayIndex = dates.withIndex().associate { (i, d) -> d to i }
        val minutes = Array(days) { DoubleArray(24) }
        val appTotals = HashMap<String, Double>()
        val appToday = HashMap<String, Double>()
        val appByHour = HashMap<Int, HashMap<String, Double>>()

        for (s in sessions) {
            var cursor = s.start.coerceAtLeast(startOfDay(dates.first(), zone))
            val end = s.end.coerceAtMost(now)
            while (cursor < end) {
                val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(cursor), zone)
                val hourEnd = zdt.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
                val sliceEnd = minOf(hourEnd, end)
                val mins = (sliceEnd - cursor) / 60_000.0
                val di = dayIndex[zdt.toLocalDate()]
                if (di != null) {
                    minutes[di][zdt.hour] += mins
                    appTotals.merge(s.pkg, mins, Double::plus)
                    if (di == days - 1) appToday.merge(s.pkg, mins, Double::plus)
                    appByHour.getOrPut(zdt.hour) { HashMap() }.merge(s.pkg, mins, Double::plus)
                }
                cursor = sliceEnd
            }
        }

        val nowZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        val currentHour = nowZdt.hour
        // A day/hour cell is "observed" once that hour has fully elapsed.
        fun observed(di: Int, h: Int) = di < days - 1 || h < currentHour

        val probability = DoubleArray(24)
        val avgByHour = DoubleArray(24)
        for (h in 0 until 24) {
            var n = 0
            var hit = 0
            var sum = 0.0
            for (di in 0 until days) if (observed(di, h)) {
                n++
                sum += minutes[di][h]
                if (minutes[di][h] >= Profile.HOUR_THRESHOLD_MIN) hit++
            }
            probability[h] = if (n == 0) 0.0 else hit.toDouble() / n
            avgByHour[h] = if (n == 0) 0.0 else sum / n
        }

        val topAppsByHour = (0 until 24).associateWith { h ->
            (appByHour[h] ?: emptyMap<String, Double>()).entries
                .sortedByDescending { it.value }.take(3).map { it.key to it.value }
        }

        // Risk = how likely you are on the phone AND how long you stay. Rank, then keep the top 3
        // that are not just the neighbour of a stronger hour.
        val ranked = (0 until 24).sortedByDescending { probability[it] * avgByHour[it] }
        val risk = ArrayList<RiskHour>()
        for (h in ranked) {
            if (probability[h] <= 0.0) break
            if (risk.any { abs(it.hour - h) <= 1 || abs(it.hour - h) == 23 }) continue
            risk += RiskHour(h, probability[h], avgByHour[h], topAppsByHour[h] ?: emptyList())
            if (risk.size == 3) break
        }

        val todayMinutes = minutes[days - 1].sum()
        // Baseline: on previous days, how many minutes had accrued by this time of day.
        val minuteOfHour = nowZdt.minute / 60.0
        val baseline = if (days <= 1) 0.0 else (0 until days - 1).map { di ->
            (0 until currentHour).sumOf { minutes[di][it] } + minutes[di][currentHour] * minuteOfHour
        }.average()
        val avgDaily = if (days <= 1) todayMinutes else (0 until days - 1).map { minutes[it].sum() }.average()

        val startToday = startOfDay(today, zone)
        val unlocksToday = unlocks.count { it >= startToday }
        val avgPickups = if (days <= 1) unlocksToday.toDouble() else (unlocks.size - unlocksToday).toDouble() / (days - 1)

        // Longest stretch without the phone since the first pickup today. Starting at the first
        // session rather than midnight keeps a night's sleep from counting as willpower.
        val todaySessions = sessions.filter { it.end > startToday }.sortedBy { it.start }
        var longestBreak = 0.0
        var lastEnd: Long? = null
        for (s in todaySessions) {
            if (lastEnd != null) longestBreak = maxOf(longestBreak, (s.start - lastEnd) / 60_000.0)
            lastEnd = maxOf(lastEnd ?: s.end, s.end)
        }
        longestBreak = maxOf(longestBreak, (now - (lastEnd ?: startToday)) / 60_000.0)

        return Profile(
            days = days, zone = zone, now = now, minutes = minutes, dates = dates,
            hourProbability = probability, avgMinutesByHour = avgByHour, riskHours = risk,
            appTotals = appTotals, appToday = appToday, topAppsByHour = topAppsByHour,
            todayMinutes = todayMinutes, baselineSameTime = baseline, avgDailyMinutes = avgDaily,
            pickupsToday = unlocksToday, avgPickups = avgPickups, longestBreakTodayMin = longestBreak,
        )
    }

    fun startOfDay(d: LocalDate, zone: ZoneId): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()
}
