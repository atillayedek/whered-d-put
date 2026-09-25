package com.wheredidiputit.domain.util

import java.text.Normalizer
import java.util.Locale

/**
 * Normalises text for forgiving, case-insensitive and accent-insensitive
 * search: "Çekmece", "cekmece" and "ÇEKMECE" all fold to "cekmece".
 */
object SearchText {
    private val combiningMarks = Regex("\\p{Mn}+")
    private val whitespace = Regex("\\s+")

    fun fold(input: String): String {
        val lower = input.lowercase(Locale.ROOT)
            .replace('ı', 'i')
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .replace(whitespace, " ")
            .trim()
    }

    /** Escapes SQL LIKE wildcards; queries use `ESCAPE '\'`. */
    fun escapeLike(input: String): String = buildString(input.length) {
        for (char in input) {
            if (char == '\\' || char == '%' || char == '_') append('\\')
            append(char)
        }
    }
}
