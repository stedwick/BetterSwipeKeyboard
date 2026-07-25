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
