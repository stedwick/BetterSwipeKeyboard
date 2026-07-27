package com.example.betterswipekeyboard.swipe

/** Sanity cap on how many custom words are kept from one input. */
const val MAX_CUSTOM_WORDS = 500

/** Sanity cap on the length of a single custom word. */
const val MAX_CUSTOM_WORD_LENGTH = 32

/**
 * Splits free-form user input into custom dictionary words.
 *
 * The input is split on ANY run of characters that are not Unicode letters,
 * so spaces, commas, newlines, tabs, semicolons, digits and punctuation all
 * act as word breaks.
 *
 * Two deliberate choices, kept simple on purpose:
 *
 * - An apostrophe BETWEEN two letters is intra-word ("spielberg's",
 *   "don't" stay whole); every other non-letter still breaks. The
 *   apostrophe has no key and no geometry, but the WORDFORM is the
 *   swipeable target: the decoder matches such words letter-only and
 *   commits the apostrophe verbatim (same mechanism as the generated
 *   dictionary), so custom possessives work. Leading/trailing
 *   apostrophes are stripped. Hyphens remain word breaks
 *   ("mother-in-law" -> "mother", "in", "law") — there is no hyphenated
 *   wordform mechanism anywhere in the pipeline.
 * - Non-ASCII letters (é, ß, 中…) are KEPT here — this parser stays
 *   keyboard-agnostic. The decoder already prunes any word containing a
 *   character with no key (`word.any { it != '\'' && it !in keyCenters }`
 *   in [SwipeDecoder.decode]), so such words are stored harmlessly and
 *   simply never match.
 *
 * Tokens are lowercased (locale-independent), empties dropped, tokens
 * longer than [maxWordLength] dropped, only the first [maxWords] kept, and
 * duplicates removed preserving first-occurrence order.
 *
 * Pure Kotlin with no Android dependencies so it is fully unit-testable.
 */
fun parseCustomWords(
    input: String,
    maxWords: Int = MAX_CUSTOM_WORDS,
    maxWordLength: Int = MAX_CUSTOM_WORD_LENGTH,
): List<String> {
    val seen = LinkedHashSet<String>()
    for (token in input.split(WORD_BREAK)) {
        val word = token.trim('\'').lowercase()
        if (word.isEmpty() || word.length > maxWordLength) continue
        seen += word
        if (seen.size >= maxWords) break
    }
    return seen.toList()
}

/** Any run of characters that are neither letters nor apostrophes is a
 * word break (the apostrophe is trimmed back off at the token edges). */
private val WORD_BREAK = Regex("[^\\p{L}']+")
