package com.wheredidiputit.domain.model

/** What the free plan allows. Deleting a memory frees its place. */
object FreePlan {
    const val ITEM_LIMIT = 5
}

/**
 * The Premium subscription as Google Play sells it in the person's country.
 * [formattedPrice] is already localised (for example "₺34,99");
 * [billingPeriod] is an ISO 8601 period such as "P1M".
 */
data class PremiumOffer(
    val formattedPrice: String,
    val billingPeriod: String,
)

data class PremiumState(
    /** Unlimited memories and no ads. */
    val isPremium: Boolean = false,
    /** Bought, but Google Play is still waiting for the payment. */
    val isPending: Boolean = false,
    /** False when Google Play Billing can't be reached (no Play Store, offline, not set up yet). */
    val storeAvailable: Boolean = false,
    val offer: PremiumOffer? = null,
)

enum class PurchaseOutcome { PURCHASED, PENDING, CANCELLED, FAILED }
