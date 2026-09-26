package com.wheredidiputit.presentation.premium

import com.wheredidiputit.domain.model.FreePlan
import com.wheredidiputit.domain.model.PremiumState
import com.wheredidiputit.presentation.home.HomeUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreePlanLimitTest {

    @Test
    fun `free plan allows adding until the limit`() {
        assertFalse(HomeUiState(itemCount = FreePlan.ITEM_LIMIT - 1).limitReached)
        assertTrue(HomeUiState(itemCount = FreePlan.ITEM_LIMIT).limitReached)
    }

    @Test
    fun `premium has no limit`() {
        assertFalse(HomeUiState(itemCount = 500, isPremium = true).limitReached)
        assertFalse(PremiumUiState(premium = PremiumState(isPremium = true), itemCount = 500).limitReached)
    }

    @Test
    fun `plan notice appears one memory before the limit and hides while searching`() {
        assertFalse(HomeUiState(itemCount = FreePlan.ITEM_LIMIT - 2).showPlanNotice)
        assertTrue(HomeUiState(itemCount = FreePlan.ITEM_LIMIT - 1).showPlanNotice)
        assertFalse(HomeUiState(itemCount = FreePlan.ITEM_LIMIT, activeQuery = "key").showPlanNotice)
        assertFalse(HomeUiState(itemCount = FreePlan.ITEM_LIMIT, isPremium = true).showPlanNotice)
    }

    @Test
    fun `purchase needs a price from Google Play`() {
        assertFalse(PremiumUiState(premium = PremiumState(storeAvailable = true, offer = null)).canPurchase)
    }
}
