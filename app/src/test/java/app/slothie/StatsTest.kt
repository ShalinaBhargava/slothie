package app.slothie

import app.slothie.usage.Profile
import app.slothie.usage.Session
import app.slothie.usage.Stats
import app.slothie.usage.hourLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class StatsTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 25)
    private fun at(dayOffset: Int, h: Int, m: Int = 0): Long =
        today.minusDays(dayOffset.toLong()).atTime(LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `an hour used every evening becomes the top risk hour`() {
        val sessions = (1..6).map { d -> Session("com.insta", at(d, 23, 5), at(d, 23, 45)) } +
            listOf(Session("com.maps", at(2, 9, 0), at(2, 9, 10)))
        val p = Stats.profile(sessions, emptyList(), days = 7, now = at(0, 12), zone = zone)
        assertEquals(23, p.riskHours.first().hour)
        assertEquals(1.0, p.hourProbability[23], 1e-9)
        assertEquals(40.0, p.avgMinutesByHour[23], 1e-6)
        // 9 AM happened on one of seven observed days (today's 9 AM has already passed at noon)
        assertEquals(1.0 / 7.0, p.hourProbability[9], 1e-9)
        assertEquals("com.insta", p.riskHours.first().topApps.first().first)
    }

    @Test
    fun `sessions are split across hour boundaries`() {
        val s = listOf(Session("a", at(1, 21, 50), at(1, 22, 20)))
        val p = Stats.profile(s, emptyList(), days = 2, now = at(0, 12), zone = zone)
        assertEquals(10.0, p.minutes[0][21], 1e-6)
        assertEquals(20.0, p.minutes[0][22], 1e-6)
    }

    @Test
    fun `today's unfinished hours are not counted as observed`() {
        // Only yesterday's 20:00 is observed; today's 20:00 has not happened at noon.
        val s = listOf(Session("a", at(1, 20, 0), at(1, 20, 30)))
        val p = Stats.profile(s, emptyList(), days = 2, now = at(0, 12), zone = zone)
        assertEquals(1.0, p.hourProbability[20], 1e-9)
    }

    @Test
    fun `pace compares today with the same time of day on previous days`() {
        val s = listOf(
            Session("a", at(1, 8, 0), at(1, 9, 0)),   // yesterday: 60 min by noon
            Session("a", at(0, 8, 0), at(0, 8, 30)),  // today: 30 min by noon
        )
        val p = Stats.profile(s, emptyList(), days = 2, now = at(0, 12), zone = zone)
        assertEquals(30.0, p.todayMinutes, 1e-6)
        assertEquals(60.0, p.baselineSameTime, 1e-6)
        assertEquals(0.5, p.pace, 1e-6)
    }

    @Test
    fun `pickups split into today and the daily average`() {
        val unlocks = listOf(at(2, 9), at(2, 10), at(1, 9), at(1, 10), at(1, 11), at(0, 9))
        val p = Stats.profile(emptyList(), unlocks, days = 3, now = at(0, 12), zone = zone)
        assertEquals(1, p.pickupsToday)
        assertEquals(2.5, p.avgPickups, 1e-9)
    }

    @Test
    fun `longest break today is measured against now`() {
        val s = listOf(Session("a", at(0, 7, 0), at(0, 7, 10)))
        val p = Stats.profile(s, emptyList(), days = 1, now = at(0, 12), zone = zone)
        // 7:10 -> 12:00 = 290 minutes
        assertEquals(290.0, p.longestBreakTodayMin, 1e-6)
    }

    @Test
    fun `summary json only carries labels and integers`() {
        val sessions = (1..3).map { d -> Session("com.insta", at(d, 23, 0), at(d, 23, 30)) }
        val p = Stats.profile(sessions, emptyList(), days = 4, now = at(0, 12), zone = zone)
        val json = p.summaryJson({ "Instagram" }, { true })
        assertTrue(json.contains("\"hour\":\"11 PM\""))
        assertTrue(json.contains("\"top_apps\":[\"Instagram\"]"))
        assertTrue(!json.contains("com.insta"))
    }

    @Test
    fun `hour labels read like a clock`() {
        assertEquals("12 AM", hourLabel(0))
        assertEquals("9 AM", hourLabel(9))
        assertEquals("12 PM", hourLabel(12))
        assertEquals("11 PM", hourLabel(23))
    }

    @Test
    fun `threshold constant is five minutes`() {
        assertEquals(5.0, Profile.HOUR_THRESHOLD_MIN, 0.0)
    }
}
