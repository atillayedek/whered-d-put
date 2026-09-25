package com.wheredidiputit.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTextTest {

    @Test
    fun `folding is case insensitive`() {
        assertEquals(SearchText.fold("passport"), SearchText.fold("PassPORT"))
    }

    @Test
    fun `folding removes accents and turkish dotless i`() {
        assertEquals("cekmece", SearchText.fold("Çekmece"))
        assertEquals("kirmizi canta", SearchText.fold("KIRMIZI  çanta"))
        assertEquals("istanbul", SearchText.fold("İstanbul"))
    }

    @Test
    fun `like wildcards are escaped`() {
        assertEquals("50\\% off\\_now\\\\", SearchText.escapeLike("50% off_now\\"))
    }
}
