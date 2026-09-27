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
        assertFalse(HomeUiState(itemCount = FreePlan.DEFAULT_ITEM_LIMIT - 1).limitReached)
        assertTrue(HomeUiState(itemCount = FreePlan.DEFAULT_ITEM_LIMIT).limitReached)
    }

    @Test
    fun `premium has no limit`() {
        assertFalse(HomeUiState(itemCount = 500, isPremium = true).limitReached)
        assertFalse(PremiumUiState(premium = PremiumState(isPremium = true), itemCount = 500).limitReached)
    }

    @Test
    fun `plan notice appears one memory before the limit and hides while searching`() {
        assertFalse(HomeUiState(itemCount = FreePlan.DEFAULT_ITEM_LIMIT - 2).showPlanNotice)
        assertTrue(HomeUiState(itemCount = FreePlan.DEFAULT_ITEM_LIMIT - 1).showPlanNotice)
        assertFalse(HomeUiState(itemCount = FreePlan.DEFAULT_ITEM_LIMIT, activeQuery = "key").showPlanNotice)
        assertFalse(HomeUiState(itemCount = FreePlan.DEFAULT_ITEM_LIMIT, isPremium = true).showPlanNotice)
    }

    @Test
    fun `limit set in the admin panel is used`() {
        assertFalse(HomeUiState(itemCount = 7, itemLimit = 10).limitReached)
        assertTrue(HomeUiState(itemCount = 10, itemLimit = 10).limitReached)
        assertTrue(PremiumUiState(itemCount = 2, itemLimit = 2).limitReached)
    }

    @Test
    fun `purchase needs a price from Google Play`() {
        assertFalse(PremiumUiState(premium = PremiumState(storeAvailable = true, offer = null)).canPurchase)
    }
}
