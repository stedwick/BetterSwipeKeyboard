package com.example.betterswipekeyboard.emoji

import java.io.InputStream

/**
 * Static, offline keyword → emoji matcher behind the emoji panel's
 * suggestion row. The table is generated from Unicode CLDR annotations
 * into `assets/emoji_keywords_en.txt` by `tools/generate_emoji_keywords.py`
 * (`keyword<TAB>emoji,emoji,...` lines, best suggestion first) — no
 * network, no ML, lookups are plain HashMap hits.
 *
 * Matching rules, deliberately simple (v1):
 * - Only the last few whole words before the cursor are considered;
 *   non-letters (punctuation, digits, already-inserted emoji) are
 *   skipped, so suggestions stay relevant right after picking one.
 * - Every adjacent two-word pair (bigram) in the window is looked up
 *   before single words ("taking off" → 🛫); phrase aliases only exist
 *   as bigrams.
 * - Single words match exactly, with a naive singular fallback
 *   ("planes" → "plane"). No stemming beyond that: CLDR already lists
 *   most inflections as keywords, and a real stemmer is overkill here.
 * - Ranking: bigram hits first, then unigram hits from the most recent
 *   word backwards; within one keyword the table order wins; first
 *   occurrence dedup; capped at [MAX_SUGGESTIONS] (one row of 8).
 */
class EmojiSuggester(private val byKeyword: Map<String, List<String>>) {

    fun suggest(textBeforeCursor: String, maxResults: Int = MAX_SUGGESTIONS): List<String> {
        val tokens = WORD.findAll(textBeforeCursor)
            .map { it.value.lowercase() }
            .toList()
            .takeLast(TOKEN_WINDOW)
        if (tokens.isEmpty()) return emptyList()

        val ranked = LinkedHashSet<String>()
        // Every adjacent word pair in the window, most recent pair first.
        for (i in tokens.size - 2 downTo 0) {
            byKeyword[tokens[i] + " " + tokens[i + 1]]?.let(ranked::addAll)
        }
        for (i in tokens.indices.reversed()) {
            lookup(tokens[i])?.let(ranked::addAll)
        }
        return ranked.take(maxResults)
    }

    /** Exact match, then naive singular: strip "es", then "s". */
    private fun lookup(token: String): List<String>? {
        byKeyword[token]?.let { return it }
        if (token.length > 3 && token.endsWith("es")) {
            byKeyword[token.dropLast(2)]?.let { return it }
        }
        if (token.length > 2 && token.endsWith("s")) {
            byKeyword[token.dropLast(1)]?.let { return it }
        }
        return null
    }

    companion object {
        private val WORD = Regex("[A-Za-z]+")

        /** How many trailing words feed the matcher. */
        const val TOKEN_WINDOW = 3

        /** One suggestion row of 8 (matches EmojiPanel's grid columns). */
        const val MAX_SUGGESTIONS = 8

        fun load(stream: InputStream): EmojiSuggester {
            val table = LinkedHashMap<String, List<String>>()
            stream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    if (line.startsWith("#")) return@forEach
                    val tab = line.indexOf('\t')
                    if (tab <= 0) return@forEach
                    val emojis = line.substring(tab + 1).split(',').filter { it.isNotBlank() }
                    if (emojis.isNotEmpty()) table[line.substring(0, tab)] = emojis
                }
            }
            return EmojiSuggester(table)
        }
    }
}
