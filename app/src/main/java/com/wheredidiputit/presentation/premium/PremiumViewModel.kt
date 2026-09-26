package com.wheredidiputit.presentation.premium

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wheredidiputit.domain.model.FreePlan
import com.wheredidiputit.domain.model.PremiumState
import com.wheredidiputit.domain.model.PurchaseOutcome
import com.wheredidiputit.domain.repository.ItemRepository
import com.wheredidiputit.domain.repository.PremiumRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

data class PremiumUiState(
    val premium: PremiumState = PremiumState(),
    val itemCount: Int = 0,
    /** Google Play's purchase sheet is open. */
    val isPurchasing: Boolean = false,
) {
    val limitReached: Boolean get() = !premium.isPremium && itemCount >= FreePlan.ITEM_LIMIT
    val canPurchase: Boolean get() = premium.storeAvailable && premium.offer != null && !isPurchasing
}

@HiltViewModel
class PremiumViewModel @Inject constructor(
    private val premiumRepository: PremiumRepository,
    itemRepository: ItemRepository,
) : ViewModel() {

    private val purchasing = MutableStateFlow(false)

    val uiState: StateFlow<PremiumUiState> = combine(
        premiumRepository.state,
        itemRepository.observeActiveCount(),
        purchasing,
    ) { premium, count, isPurchasing ->
        PremiumUiState(premium = premium, itemCount = count, isPurchasing = isPurchasing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PremiumUiState())

    /** Results of the purchase sheet; the screen reacts to each one. */
    val outcomes: Flow<PurchaseOutcome> = premiumRepository.purchaseOutcomes.onEach { purchasing.value = false }

    init {
        premiumRepository.refresh()
    }

    /** Returns false when Google Play's purchase sheet couldn't be opened. */
    fun subscribe(activity: Activity): Boolean {
        if (!uiState.value.canPurchase) return false
        val opened = premiumRepository.launchPurchase(activity)
        purchasing.value = opened
        return opened
    }

    fun retry() = premiumRepository.refresh()
}
