package com.wheredidiputit.domain.model

import com.wheredidiputit.domain.ads.AdFrequencyPolicy

/** Settings the admin can change without an app update. */
data class AppSettings(
    val freeItemLimit: Int = FreePlan.DEFAULT_ITEM_LIMIT,
    val adsPerDay: Int = AdFrequencyPolicy.DEFAULT_MAX_PER_DAY,
)

/** A short message from the team, shown on Home until the person closes it. */
data class Announcement(
    val id: String,
    val messageTr: String,
    val messageEn: String?,
) {
    fun messageFor(languageTag: String): String =
        if (languageTag.startsWith("tr")) messageTr else messageEn ?: messageTr
}
