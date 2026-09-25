package com.wheredidiputit.domain.ads

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdFrequencyPolicyTest {

    private val zone = ZoneId.of("Europe/Istanbul")
    private val installedAt = at(2026, 9, 1, 9)
    private val hour = 60L * 60 * 1000

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `no ads during the first day after install`() {
        assertFalse(AdFrequencyPolicy.canShow(AdHistory(), installedAt + 23 * hour, installedAt, zone))
        assertTrue(AdFrequencyPolicy.canShow(AdHistory(), installedAt + 25 * hour, installedAt, zone))
    }

    @Test
    fun `at least two hours between ads`() {
        val shown = AdFrequencyPolicy.recordShown(AdHistory(), at(2026, 9, 10, 10), zone)
        assertFalse(AdFrequencyPolicy.canShow(shown, at(2026, 9, 10, 11, 59), installedAt, zone))
        assertTrue(AdFrequencyPolicy.canShow(shown, at(2026, 9, 10, 12, 0), installedAt, zone))
    }

    @Test
    fun `at most three ads per day`() {
        var history = AdHistory()
        listOf(8, 11, 14).forEach { h -> history = AdFrequencyPolicy.recordShown(history, at(2026, 9, 10, h), zone) }
        assertEquals(3, history.shownToday)
        assertFalse(AdFrequencyPolicy.canShow(history, at(2026, 9, 10, 20), installedAt, zone))
    }

    @Test
    fun `daily count resets on a new local day`() {
        var history = AdHistory()
        listOf(8, 11, 14).forEach { h -> history = AdFrequencyPolicy.recordShown(history, at(2026, 9, 10, h), zone) }
        assertTrue(AdFrequencyPolicy.canShow(history, at(2026, 9, 11, 8), installedAt, zone))
        assertEquals(1, AdFrequencyPolicy.recordShown(history, at(2026, 9, 11, 8), zone).shownToday)
    }
}
