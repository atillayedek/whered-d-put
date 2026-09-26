package com.wheredidiputit.data.billing

import android.app.Activity
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.wheredidiputit.core.config.AppConfig
import com.wheredidiputit.core.di.ApplicationScope
import com.wheredidiputit.data.preferences.UserPreferences
import com.wheredidiputit.domain.model.PremiumOffer
import com.wheredidiputit.domain.model.PremiumState
import com.wheredidiputit.domain.model.PurchaseOutcome
import com.wheredidiputit.domain.repository.PremiumRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Premium through Google Play Billing. Google Play is the source of truth:
 * the subscription is re-read every time the app comes to the foreground, so
 * a cancelled or expired subscription ends Premium on its own. The last
 * answer is cached so a Premium member sees no ads while offline.
 */
@Singleton
class BillingManager @Inject constructor(
    @ApplicationContext context: Context,
    private val preferences: UserPreferences,
    @ApplicationScope private val scope: CoroutineScope,
) : PremiumRepository, PurchasesUpdatedListener {

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val _state = MutableStateFlow(PremiumState())
    override val state: StateFlow<PremiumState> = _state.asStateFlow()

    private val outcomes = Channel<PurchaseOutcome>(Channel.BUFFERED)
    override val purchaseOutcomes: Flow<PurchaseOutcome> = outcomes.receiveAsFlow()

    private val connecting = AtomicBoolean(false)
    private val refreshMutex = Mutex()

    @Volatile private var productDetails: ProductDetails? = null
    @Volatile private var offerToken: String? = null

    init {
        scope.launch {
            if (preferences.premiumCached()) _state.update { it.copy(isPremium = true) }
        }
        scope.launch(Dispatchers.Main) {
            // Also fires right away, because the process is already started.
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_START) refresh() },
            )
        }
    }

    override fun refresh() {
        if (!client.isReady) {
            connect()
            return
        }
        scope.launch {
            refreshMutex.withLock {
                loadOffer()
                loadPurchases()
            }
        }
    }

    override fun launchPurchase(activity: Activity): Boolean {
        val details = productDetails
        val token = offerToken
        if (!client.isReady || details == null || token == null) {
            refresh()
            return false
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(token)
                        .build(),
                ),
            )
            .build()
        return client.launchBillingFlow(activity, params).responseCode == BillingResponseCode.OK
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val premium = purchases.orEmpty().filter { AppConfig.PREMIUM_PRODUCT_ID in it.products }
        scope.launch {
            val outcome = when (result.responseCode) {
                BillingResponseCode.OK -> when {
                    premium.any { it.purchaseState == Purchase.PurchaseState.PURCHASED } -> PurchaseOutcome.PURCHASED
                    premium.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> PurchaseOutcome.PENDING
                    else -> PurchaseOutcome.FAILED
                }
                BillingResponseCode.USER_CANCELED -> PurchaseOutcome.CANCELLED
                BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseOutcome.PURCHASED
                else -> PurchaseOutcome.FAILED
            }
            if (result.responseCode == BillingResponseCode.OK) {
                refreshMutex.withLock { applyPurchases(premium, replaceAll = false) }
            }
            outcomes.send(outcome)
            refresh()
        }
    }

    private fun connect() {
        if (!connecting.compareAndSet(false, true)) return
        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connecting.set(false)
                    if (result.responseCode == BillingResponseCode.OK) {
                        refresh()
                    } else {
                        _state.update { it.copy(storeAvailable = false) }
                    }
                }

                override fun onBillingServiceDisconnected() {
                    // Calls made later reconnect automatically (enableAutoServiceReconnection).
                    connecting.set(false)
                }
            },
        )
    }

    /** Current price of the monthly base plan in the person's Play country. */
    private suspend fun loadOffer() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(AppConfig.PREMIUM_PRODUCT_ID)
                        .setProductType(ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingResponseCode.OK) {
            _state.update { it.copy(storeAvailable = false) }
            return
        }
        val details = result.productDetailsList.orEmpty().firstOrNull()
        val offers = details?.subscriptionOfferDetails.orEmpty()
            .filter { it.basePlanId == AppConfig.PREMIUM_BASE_PLAN }
        // The base plan itself (no promotional offer attached).
        val offer = offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull()
        val recurring = offer?.pricingPhases?.pricingPhaseList?.lastOrNull()

        productDetails = details
        offerToken = offer?.offerToken
        _state.update {
            it.copy(
                storeAvailable = offer != null,
                offer = recurring?.let { phase -> PremiumOffer(phase.formattedPrice, phase.billingPeriod) },
            )
        }
    }

    private suspend fun loadPurchases() {
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build()
        val result = client.queryPurchasesAsync(params)
        // On failure keep the last known answer instead of taking Premium away.
        if (result.billingResult.responseCode != BillingResponseCode.OK) return
        val premium = result.purchasesList.filter { AppConfig.PREMIUM_PRODUCT_ID in it.products }
        applyPurchases(premium, replaceAll = true)
    }

    /**
     * [replaceAll] is true for the full list of the person's subscriptions and
     * false for the few purchases a single purchase flow reports.
     */
    private suspend fun applyPurchases(purchases: List<Purchase>, replaceAll: Boolean) {
        val active = purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val pending = purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING }

        // Unacknowledged purchases are refunded by Google Play after three days.
        active.filterNot { it.isAcknowledged }.forEach { purchase ->
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
            )
        }

        val isPremium = active.isNotEmpty() || (!replaceAll && _state.value.isPremium)
        preferences.setPremiumCached(isPremium)
        _state.update { it.copy(isPremium = isPremium, isPending = pending && !isPremium) }
    }
}
