package com.wheredidiputit.domain.repository

import android.app.Activity
import com.wheredidiputit.domain.model.PremiumState
import com.wheredidiputit.domain.model.PurchaseOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The Premium subscription (unlimited memories, no ads), sold through Google Play. */
interface PremiumRepository {
    val state: StateFlow<PremiumState>

    /** Results of purchases started with [launchPurchase]. */
    val purchaseOutcomes: Flow<PurchaseOutcome>

    /** Re-reads the person's subscriptions and the current price from Google Play. */
    fun refresh()

    /** Opens Google Play's purchase sheet. Returns false when it can't be opened. */
    fun launchPurchase(activity: Activity): Boolean
}
