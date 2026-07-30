package com.example.betterswipekeyboard.proofread

/**
 * Per-word crossed-letter memory for the AI proofreader: which words were
 * SWIPED (not typed/pasted/dictated), the ordered keys each trail crossed,
 * and the decoder's runner-up guesses for the same trail. The service
 * appends on every swipe commit; at proofread time the log is reconciled
 * against the actual text before the cursor and surviving entries annotate
 * the OpenRouter request (see ProofreadPrompt).
 *
 * Alignment is TEXT-ANCHORED, never position-tracked: the keyboard does
 * not observe external edits, so any stored offset would go stale the
 * moment the target app changes the text. Instead [reconcile] re-finds
 * each entry's word in the text in commit order, whole-word and
 * case-sensitive. Every invalidation resolves to a safe drop — an edited,
 * deleted or externally changed word simply no longer matches, and the
 * proofreader never receives stale letters. Word-delete-after-swipe
 * (DeleteWordBackward) removes the whole word, so a retried swipe cleanly
 * replaces its entry. A strip-tap replacement (ReplaceSwipedWord) has no
 * trail and records nothing; the replaced word's entry drops at the next
 * reconciliation because the text no longer contains it.
 *
 * In-memory only, dies with the service — nothing is persisted
 * (clipboard-history precedent). Alignment bugs feed the model wrong
 * letters; the matching rules are pure and exhaustively unit-tested in
 * SwipedWordLogTest.
 */
class SwipedWordLog(private val cap: Int = MAX_ENTRIES) {

    /**
     * [word] is the caps-transformed committed word (reconciliation matches
     * the text case-sensitively); [letters] the ordered crossed keys.
     * [alternates] are the decoder's RAW runner-up words for the same trail
     * (lowercase, uncapped — the caps-transformed copies live in
     * KeyboardState for the alternates strip; the proofreader wants the
     * decoder's actual guesses and derives casing from sentence context).
     * The committed word never appears among them (swipeAlternates drops
     * top-1).
     */
    data class Entry(val word: String, val letters: String, val alternates: List<String> = emptyList())

    /** A reconciled entry: [endIndex] is the index just past its word's
     * match in the text, so callers can tell whether the match falls
     * inside the proofread window. */
    data class Match(val entry: Entry, val startIndex: Int, val endIndex: Int)

    private val entries = ArrayDeque<Entry>()

    fun record(word: String, letters: String, alternates: List<String> = emptyList()) {
        if (word.isBlank() || letters.isBlank()) return
        entries.addLast(Entry(word, letters, alternates))
        while (entries.size > cap) entries.removeFirst()
    }

    /** All entries whose words still appear in [text], in commit order. */
    fun reconcile(text: String): List<Match> = reconcile(entries.toList(), text)

    internal companion object {
        const val MAX_ENTRIES = 100

        /**
         * Matches each entry's word in [text], oldest first, each search
         * continuing past the previous match's end (so duplicate words map
         * to duplicate occurrences in commit order). An entry that matches
         * nowhere is dropped and consumes nothing. Matching is whole-word
         * (a letter, digit or apostrophe on either side rejects the match —
         * `cat` matches neither `cats` nor `scat`, `mother` does not match
         * inside `mother's`) and case-sensitive (entries store the
         * post-caps committed word; a case change is an edit → drop).
         */
        fun reconcile(entries: List<Entry>, text: String): List<Match> {
            val matches = mutableListOf<Match>()
            var from = 0
            for (entry in entries) {
                val at = findWholeWord(text, entry.word, from) ?: continue
                matches += Match(entry, at, at + entry.word.length)
                from = at + entry.word.length
            }
            return matches
        }

        private fun findWholeWord(text: String, word: String, from: Int): Int? {
            var i = text.indexOf(word, from)
            while (i >= 0) {
                val before = text.getOrNull(i - 1)
                val after = text.getOrNull(i + word.length)
                if (before?.isWordChar() != true && after?.isWordChar() != true) return i
                i = text.indexOf(word, i + 1)
            }
            return null
        }

        private fun Char.isWordChar(): Boolean = isLetterOrDigit() || this == '\''
    }
}
