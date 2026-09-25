package com.wheredidiputit.presentation.common

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What screens may ask of the ad system; bound to the current activity. */
interface AdsController {
    /** True when the person must be able to change their ad consent from Settings. */
    val privacyOptionsRequired: StateFlow<Boolean>

    /** Signals a natural pause (after saving, after leaving a memory). */
    fun onNaturalBreak()

    fun openPrivacyOptions()
}

/** Used when no activity-bound controller is provided: shows nothing. */
private object AdsOff : AdsController {
    override val privacyOptionsRequired: StateFlow<Boolean> = MutableStateFlow(false)
    override fun onNaturalBreak() = Unit
    override fun openPrivacyOptions() = Unit
}

val LocalAdsController = staticCompositionLocalOf<AdsController> { AdsOff }
