package com.example.betterswipekeyboard.eval

/**
 * Sentence reconstruction tables for the six captured trail sets in
 * app/src/test/resources — which trail indices form which intended
 * sentence. Single source for the eval corpus generator (sub-corpus R)
 * AND the prompt corpus guard (ProofreadPromptTest): the guard asserts no
 * prompt example contains any of these sentences, so both sides read the
 * same table.
 *
 * Set backgrounds (see SwipeRealTrailAccuracyTest's KDoc):
 * - set1: two phrases, first capture.
 * - set2: both phrases twice; #33 is a genuine user mis-swipe ('-'), so
 *   the second pangram (s4) is unscoreable and excluded.
 * - set3: phrase variants with 'bought'/'sold', pangram twice; #36 is a
 *   lone 'mother' retry — a singleton, not a sentence, excluded.
 * - set4: the ten-sentence TDD corpus, one pass; #23/#25 are the FAILED
 *   first attempts at excellent/example Philip immediately retried, so
 *   s4 uses the retries #24/#26 and drops #23/#25. s8's 'a' was TAPPED
 *   (one letter is never swiped) — modeled as a tapped insertion.
 * - set5: the ten-sentence corpus re-recorded, one pass, no retries; same
 *   tapped 'a' in s8.
 * - set6: short-word paragraph, swiped twice (pass 1 deliberate stops,
 *   pass 2 natural drift lift-offs); #34/#35 are probable echo swipes of
 *   'it' ('-'), dropped. The intended punctuation of the paragraph is not
 *   recorded, so each pass is treated as one sentence (expected casing +
 *   final period are conventions, noted in the corpus).
 */
data class CapturedSentence(
    val set: String,
    val label: String,
    /** Trail indices into the set's jsonl, in word order. */
    val trailIndices: List<Int>,
    /** The intended words, one per trail index. */
    val intentWords: List<String>,
    /** Tapped (never swiped) words to insert AFTER the given word position. */
    val tappedAfter: Map<Int, String> = emptyMap(),
) {
    /** The intended sentence text, lowercase, as the words were meant. */
    fun intentText(): String {
        val words = intentWords.toMutableList()
        tappedAfter.toSortedMap().entries.reversed().forEach { (pos, w) ->
            words.add(pos + 1, w)
        }
        return words.joinToString(" ")
    }
}

private const val SET1 = "swipe_trails_philip"
private const val SET2 = "swipe_trails2_philip"
private const val SET3 = "swipe_trails3_philip"
private const val SET4 = "swipe_trails4_normal_philip"
private const val SET5 = "swipe_trails5_normal2_philip"
private const val SET6 = "swipe_trails6_short_words_philip"

private val PANGRAM = "the quick brown fox jumps over the lazy dog".split(" ")
private val SET6_WORDS =
    "am well and we go up the hill to ask if you will fix it hello it is fun".split(" ")

val CAPTURED_SENTENCES: List<CapturedSentence> = buildList {
    fun s(set: String, label: String, indices: List<Int>, words: List<String>, tappedAfter: Map<Int, String> = emptyMap()) {
        require(indices.size == words.size) { "$set $label: indices/words size mismatch" }
        add(CapturedSentence(set, label, indices, words, tappedAfter))
    }

    s(SET1, "s1", (0..7).toList(), "my very excellent mother just served us nine".split(" "))
    s(SET1, "s2", (8..16).toList(), PANGRAM)

    s(SET2, "s1", (0..8).toList(), "my very excellent mother just served us nine pizzas".split(" "))
    s(SET2, "s2", (9..17).toList(), PANGRAM)
    s(SET2, "s3", (18..26).toList(), "my very excellent mother just served us nine pizzas".split(" "))
    // set2 s4 (#27-36) excluded: #33 is a genuine user mis-swipe ('-').

    s(SET3, "s1", (0..8).toList(), "my very excellent mother just bought us nine pizzas".split(" "))
    s(SET3, "s2", (9..17).toList(), PANGRAM)
    s(SET3, "s3", (18..26).toList(), "my very excellent mother just sold us nine pizzas".split(" "))
    s(SET3, "s4", (27..35).toList(), PANGRAM)
    // set3 #36 excluded: lone 'mother' retry, not a sentence.

    s(SET4, "s1", (0..8).toList(), PANGRAM)
    s(SET4, "s2", (9..13).toList(), "my mummy did the minimum".split(" "))
    s(SET4, "s3", (14..21).toList(), "his mother never once drank water after dark".split(" "))
    // Retries: #24/#26 (kept) replace the failed first attempts #23/#25.
    s(SET4, "s4", listOf(22, 24, 26, 27, 28, 29, 30), "an excellent example of what to expect".split(" "))
    s(SET4, "s5", (31..37).toList(), "nine nice mice ran past the fox".split(" "))
    s(SET4, "s6", (38..43).toList(), "we go up to fix it".split(" "))
    s(SET4, "s7", (44..49).toList(), "the dog ran over the hill".split(" "))
    // 'a' was TAPPED between 'follow' (position 3) and 'quick' — inserted.
    s(SET4, "s8", (50..55).toList(), "the power will follow quick swipe".split(" "), tappedAfter = mapOf(3 to "a"))
    s(SET4, "s9", (56..60).toList(), "how are you doing today".split(" "))
    s(SET4, "s10", (61..66).toList(), "we had fun at the lake".split(" "))

    s(SET5, "s1", (0..8).toList(), PANGRAM)
    s(SET5, "s2", (9..13).toList(), "my mummy did the minimum".split(" "))
    s(SET5, "s3", (14..21).toList(), "his mother never once drank water after dark".split(" "))
    s(SET5, "s4", (22..28).toList(), "an excellent example of what to expect".split(" "))
    s(SET5, "s5", (29..35).toList(), "nine nice mice ran past the fox".split(" "))
    s(SET5, "s6", (36..41).toList(), "we go up to fix it".split(" "))
    s(SET5, "s7", (42..47).toList(), "the dog ran over the hill".split(" "))
    s(SET5, "s8", (48..53).toList(), "the power will follow quick swipe".split(" "), tappedAfter = mapOf(3 to "a"))
    s(SET5, "s9", (54..58).toList(), "how are you doing today".split(" "))
    s(SET5, "s10", (59..64).toList(), "we had fun at the lake".split(" "))

    s(SET6, "pass1", (0..18).toList(), SET6_WORDS)
    // Pass 2: #34/#35 are probable echo swipes of 'it' ('-'), dropped.
    s(SET6, "pass2", (19..33).toList() + (36..39).toList(), SET6_WORDS)
}

/** The captured sentences as plain lowercase text — the corpus guard's
 * sentence-level ban list. */
fun capturedSentenceTexts(): List<String> = CAPTURED_SENTENCES.map { it.intentText() }
