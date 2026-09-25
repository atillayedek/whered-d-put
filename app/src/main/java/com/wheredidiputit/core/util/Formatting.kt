package com.wheredidiputit.core.util

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wheredidiputit.R
import java.time.Instant

/** "just now", "5 minutes ago", "2 days ago" — localised by the platform. */
@Composable
fun relativeTime(instant: Instant, now: Long = System.currentTimeMillis()): String {
    val millis = instant.toEpochMilli()
    return if (now - millis < DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.time_just_now)
    } else {
        DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
}
