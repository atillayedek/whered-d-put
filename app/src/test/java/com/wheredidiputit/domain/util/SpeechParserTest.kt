package com.wheredidiputit.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeechParserTest {

    @Test
    fun `english put sentence splits item and location`() {
        val parsed = SpeechParser.parse("I put my passport in the second drawer of my desk.")
        assertEquals("Passport", parsed.item)
        assertEquals("in the second drawer of my desk", parsed.location)
    }

    @Test
    fun `english is sentence splits item and location`() {
        val parsed = SpeechParser.parse("The spare key is under the blue flower pot")
        assertEquals("Spare key", parsed.item)
        assertEquals("under the blue flower pot", parsed.location)
    }

    @Test
    fun `multi word prepositions are kept with the location`() {
        val parsed = SpeechParser.parse("left the charger next to the TV")
        assertEquals("Charger", parsed.item)
        assertEquals("next to the TV", parsed.location)
    }

    @Test
    fun `turkish sentence uses accusative object as item`() {
        val parsed = SpeechParser.parse("Yedek anahtarı siyah montumun iç cebine koydum.")
        assertEquals("Yedek anahtarı", parsed.item)
        assertEquals("siyah montumun iç cebine", parsed.location)
    }

    @Test
    fun `turkish single word object`() {
        val parsed = SpeechParser.parse("Pasaportumu çalışma masasının ikinci çekmecesine koydum")
        assertEquals("Pasaportumu", parsed.item)
        assertEquals("çalışma masasının ikinci çekmecesine", parsed.location)
    }

    @Test
    fun `unrecognised sentence becomes the location`() {
        val parsed = SpeechParser.parse("kitchen drawer")
        assertNull(parsed.item)
        assertEquals("Kitchen drawer", parsed.location)
    }

    @Test
    fun `blank input is empty`() {
        val parsed = SpeechParser.parse("   ")
        assertNull(parsed.item)
        assertEquals("", parsed.location)
    }
}
