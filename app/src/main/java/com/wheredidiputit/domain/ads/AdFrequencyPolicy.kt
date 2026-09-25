package com.wheredidiputit.domain.ads

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What has been shown so far on this device. */
data class AdHistory(
    val lastShownAt: Long? = null,
    /** Local calendar day (ISO yyyy-MM-dd) that [shownToday] counts. */
    val day: String? = null,
    val shownToday: Int = 0,
)

/**
 * Keeps ads rare so they never get in the way:
 * - at most [MAX_PER_DAY] full-screen ads per local calendar day,
 * - at least [MIN_GAP_MILLIS] between two ads,
 * - none during the first [GRACE_PERIOD_MILLIS] after install.
 */
object AdFrequencyPolicy {
    const val MAX_PER_DAY = 3
    const val MIN_GAP_MILLIS = 2L * 60 * 60 * 1000
    const val GRACE_PERIOD_MILLIS = 24L * 60 * 60 * 1000

    fun canShow(history: AdHistory, now: Long, installedAt: Long, zone: ZoneId): Boolean {
        if (now - installedAt < GRACE_PERIOD_MILLIS) return false
        val last = history.lastShownAt
        if (last != null && now - last < MIN_GAP_MILLIS) return false
        val today = dayOf(now, zone)
        val shownToday = if (history.day == today) history.shownToday else 0
        return shownToday < MAX_PER_DAY
    }

    fun recordShown(history: AdHistory, now: Long, zone: ZoneId): AdHistory {
        val today = dayOf(now, zone)
        val shownToday = if (history.day == today) history.shownToday else 0
        return AdHistory(lastShownAt = now, day = today, shownToday = shownToday + 1)
    }

    private fun dayOf(millis: Long, zone: ZoneId): String =
        LocalDate.ofInstant(Instant.ofEpochMilli(millis), zone).toString()
}
