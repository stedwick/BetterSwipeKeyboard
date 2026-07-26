package com.example.betterswipekeyboard.swipe

import java.io.InputStream

data class WordEntry(val word: String, val rank: Int)

/**
 * Frequency-ordered word list loaded from `assets/words_en.txt`
 * (`word<TAB>rank` lines, lower rank = more frequent). Indexed by first
 * letter so the decoder can prune candidates cheaply.
 */
class Dictionary(words: List<WordEntry>) {

    private val byFirstLetter: Map<Char, List<WordEntry>> =
        words.groupBy { it.word.first() }

    val maxRank: Int = words.maxOfOrNull { it.rank } ?: 1

    /** All words starting with [first], most frequent first. */
    fun startingWith(first: Char): List<WordEntry> =
        byFirstLetter[first].orEmpty()

    /**
     * Returns a copy with [customWords] added at rank 1 (top frequency).
     * Rank 1 gives custom words the decoder's maximum frequency bonus, so a
     * custom word wins whenever its geometric trail fit is comparable to a
     * common word's — while geometry still dominates, so common-word
     * decoding is not distorted. Words already in the dictionary keep their
     * existing entry (dedup); [maxRank] is unaffected because built-in ranks
     * already reach it.
     */
    fun withCustomWords(customWords: List<String>): Dictionary {
        if (customWords.isEmpty()) return this
        val existing = byFirstLetter.values.flatten().mapTo(HashSet()) { it.word }
        val customEntries = customWords
            .filter { it.isNotEmpty() && it !in existing }
            .map { WordEntry(it, rank = 1) }
        if (customEntries.isEmpty()) return this
        return Dictionary(byFirstLetter.values.flatten() + customEntries)
    }

    companion object {
        fun load(stream: InputStream): Dictionary {
            val entries = stream.bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    val tab = line.indexOf('\t')
                    if (tab <= 0) return@mapNotNull null
                    val rank = line.substring(tab + 1).toIntOrNull() ?: return@mapNotNull null
                    WordEntry(line.substring(0, tab), rank)
                }.toList()
            }
            return Dictionary(entries)
        }
    }
}
