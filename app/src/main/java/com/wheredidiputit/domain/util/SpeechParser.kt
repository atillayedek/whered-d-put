package com.wheredidiputit.domain.util

import java.util.Locale

/**
 * Best-effort split of a spoken sentence into "what" and "where".
 *
 * "I put my passport in the second drawer of my desk"
 *   -> item "passport", location "in the second drawer of my desk"
 * "Yedek anahtarı siyah montumun iç cebine koydum"
 *   -> item "Yedek anahtarı", location "siyah montumun iç cebine"
 *
 * When no pattern matches, the whole sentence becomes the location and the
 * person fills in the item. The result is always shown for editing.
 */
object SpeechParser {

    data class Parsed(val item: String?, val location: String)

    private const val PREPOSITIONS =
        "in front of|next to|on top of|inside of|underneath|between|behind|beside|inside|under|above|below|into|onto|near|in|on|at|by"

    private val englishPut = Regex(
        "^(?:i(?:'ve| have)?\\s+)?(?:put|left|placed|stored|kept|hid|stashed|dropped|keep|leave|store)\\s+" +
            "(?:my\\s+|the\\s+|a\\s+|an\\s+|our\\s+)?(.+?)\\s+((?:$PREPOSITIONS)\\s+.+)$",
        RegexOption.IGNORE_CASE,
    )

    private val englishIs = Regex(
        "^(?:my\\s+|the\\s+|our\\s+)?(.+?)\\s+(?:is|are|'s)\\s+((?:$PREPOSITIONS)\\s+.+)$",
        RegexOption.IGNORE_CASE,
    )

    private val turkishVerb = Regex(
        "^(.+)\\s+(?:koydum|bıraktım|sakladım|yerleştirdim|astım|kaldırdım|attım|tıktım|koyduk|bıraktık|sakladık)$",
        RegexOption.IGNORE_CASE,
    )

    private val turkishAccusative = Regex("[ıiuüIİUÜ]$")

    fun parse(transcript: String): Parsed {
        val sentence = transcript.trim().trimEnd('.', '!', '?').trim()
        if (sentence.isEmpty()) return Parsed(null, "")

        englishPut.matchEntire(sentence)?.let { return it.toParsed() }
        englishIs.matchEntire(sentence)?.let { return it.toParsed() }
        parseTurkish(sentence)?.let { return it }

        return Parsed(item = null, location = sentence.capitalizeFirst())
    }

    private fun MatchResult.toParsed(): Parsed = Parsed(
        item = groupValues[1].trim().capitalizeFirst(),
        location = groupValues[2].trim(),
    )

    private fun parseTurkish(sentence: String): Parsed? {
        val body = turkishVerb.matchEntire(sentence)?.groupValues?.get(1)?.trim() ?: return null
        val words = body.split(Regex("\\s+"))
        if (words.size < 2) return null
        // The object usually carries the accusative suffix; everything after it is the place.
        val objectEnd = words.indexOfFirst { turkishAccusative.containsMatchIn(it) }
        if (objectEnd < 0 || objectEnd == words.lastIndex) return null
        val item = words.subList(0, objectEnd + 1).joinToString(" ")
        val location = words.subList(objectEnd + 1, words.size).joinToString(" ")
        return Parsed(item.capitalizeFirst(), location)
    }

    private fun String.capitalizeFirst(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}
