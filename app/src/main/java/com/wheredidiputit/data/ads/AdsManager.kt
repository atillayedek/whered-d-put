package com.wheredidiputit.data.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.wheredidiputit.BuildConfig
import com.wheredidiputit.core.di.ApplicationScope
import com.wheredidiputit.data.preferences.UserPreferences
import com.wheredidiputit.domain.ads.AdFrequencyPolicy
import com.wheredidiputit.domain.repository.PremiumRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Google AdMob interstitials, shown rarely and only at natural breaks
 * (after saving a memory or after leaving one). Frequency is limited by
 * [AdFrequencyPolicy]. Consent is collected with Google's User Messaging
 * Platform before any ad is requested, as required in the EEA and UK.
 *
 * Nothing here ever blocks the UI: if no ad is ready, nothing is shown.
 * Premium members never see or load ads.
 */
@Singleton
class AdsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: UserPreferences,
    @ApplicationScope private val scope: CoroutineScope,
    private val premiumRepository: PremiumRepository,
) {
    private val isPremium: Boolean get() = premiumRepository.state.value.isPremium

    private val enabled = BuildConfig.ADS_ENABLED && BuildConfig.ADMOB_INTERSTITIAL_ID.isNotEmpty()
    private val consentInformation: ConsentInformation = UserMessagingPlatform.getConsentInformation(context)
    private val sdkStarted = AtomicBoolean(false)

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private var interstitial: InterstitialAd? = null
    private var loadedAt = 0L
    private var isLoading = false
    private var isShowing = false

    /** Call from the activity on start. Shows the consent form only where the law requires it. */
    fun gatherConsent(activity: Activity) {
        if (!enabled) return
        consentInformation.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    updatePrivacyOptions()
                    startIfAllowed()
                }
            },
            { startIfAllowed() },
        )
        // Consent given in a previous session lets ads load right away.
        startIfAllowed()
    }

    /** Opens Google's form so the person can change their ad consent at any time. */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { updatePrivacyOptions() }
    }

    /**
     * A natural pause in the flow. Shows an interstitial if one is ready and
     * the frequency policy allows it; otherwise does nothing.
     */
    fun onNaturalBreak(activity: Activity) {
        if (!enabled || isShowing) return
        if (isPremium) {
            interstitial = null
            return
        }
        val ad = freshInterstitial() ?: run {
            preload()
            return
        }
        scope.launch(Dispatchers.Main) {
            val allowed = AdFrequencyPolicy.canShow(
                history = preferences.adHistory(),
                now = System.currentTimeMillis(),
                installedAt = installedAt(),
                zone = ZoneId.systemDefault(),
            )
            if (!allowed || isPremium) return@launch
            // Let the "Saved" confirmation land before the ad appears.
            delay(SHOW_DELAY_MS)
            if (!activity.isInForeground() || isShowing || interstitial !== ad) return@launch
            isShowing = true
            interstitial = null
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    scope.launch {
                        val now = System.currentTimeMillis()
                        preferences.setAdHistory(
                            AdFrequencyPolicy.recordShown(preferences.adHistory(), now, ZoneId.systemDefault()),
                        )
                    }
                }

                override fun onAdDismissedFullScreenContent() {
                    isShowing = false
                    preload()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    isShowing = false
                    preload()
                }
            }
            ad.show(activity)
        }
    }

    private fun updatePrivacyOptions() {
        _privacyOptionsRequired.value = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun startIfAllowed() {
        updatePrivacyOptions()
        if (!consentInformation.canRequestAds()) return
        if (sdkStarted.getAndSet(true)) {
            preload()
            return
        }
        scope.launch(Dispatchers.IO) {
            MobileAds.initialize(context) {}
            withContext(Dispatchers.Main) { preload() }
        }
    }

    private fun preload() {
        if (!enabled || isPremium || isLoading || freshInterstitial() != null || !consentInformation.canRequestAds()) return
        isLoading = true
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    loadedAt = SystemClock.elapsedRealtime()
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                    isLoading = false
                }
            },
        )
    }

    /** Loaded interstitials expire after an hour; drop stale ones. */
    private fun freshInterstitial(): InterstitialAd? {
        val ad = interstitial ?: return null
        if (SystemClock.elapsedRealtime() - loadedAt > AD_MAX_AGE_MS) {
            interstitial = null
            return null
        }
        return ad
    }

    private fun installedAt(): Long = try {
        context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    } catch (e: Exception) {
        System.currentTimeMillis()
    }

    private fun Activity.isInForeground(): Boolean =
        !isFinishing && !isDestroyed &&
            (this as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false

    private companion object {
        const val SHOW_DELAY_MS = 900L
        const val AD_MAX_AGE_MS = 55L * 60 * 1000
    }
}
