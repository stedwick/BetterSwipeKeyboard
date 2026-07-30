package com.example.betterswipekeyboard.proofread

/**
 * Reply-sanity guard: the last line of defense before a proofread reply is
 * applied VERBATIM into the user's text field. The echo guard
 * ([ProofreadPrompt.containsSwipePathsMarker]) catches annotation echo; this
 * guard catches everything else the model should never have said — observed
 * in the tools/eval loops when nova-micro answered an incoherent sentence
 * with "Sorry, I can't provide a correction for this text ...", which would
 * have been auto-committed as if it were the corrected sentence.
 *
 * Three rejection rules (all fail-soft: a rejected reply is logged and
 * swallowed, the text field simply keeps the un-proofread sentence):
 *
 * a) Refusal/apology markers. The list targets refusal FORMULAE, not bare
 *    words: "I can't believe we won" is ordinary user text, so "i can't"
 *    alone is NOT a marker — "can't provide" / "cannot help" are. "sorry"
 *    is the exception, checked only as the reply's FIRST word (refusals
 *    lead with it; a mid-sentence "sorry" is the user's own content).
 *    Trade-off, accepted: a refusal phrased without any listed formula
 *    still has to pass the word-overlap gate below.
 * b) Blank reply (defense in depth — the apply path already checks this,
 *    but the guard is unit-tested in isolation).
 * c) Word overlap: at least [MIN_WORD_OVERLAP] of the reply's content words
 *    must appear in the input window. A proofread corrects a handful of
 *    words, so a legit reply shares nearly all of them; a refusal, an
 *    answer to the text, or a hallucinated rewrite shares almost none.
 *    Tokens are lowercase letter runs (apostrophes kept), stopwords
 *    included — simplicity over precision; the refusal class sits near 0
 *    and legit corrections near 1, so the threshold has a wide margin.
 */
object ReplySanity {

    /** Minimum share of the reply's content words present in the input. */
    const val MIN_WORD_OVERLAP = 0.5

    /** Refusal formulae, matched case-insensitively anywhere in the reply. */
    private val REFUSAL_PHRASES = listOf(
        "as an ai",
        "can't provide",
        "cannot provide",
        "can't help",
        "cannot help",
        "can't assist",
        "cannot assist",
        "i'm unable",
        "i am unable",
    )

    private val WORD = Regex("[a-z']+")

    /**
     * Returns WHY [reply] must not be applied to the text field, or null
     * when it is safe to apply. [inputText] is the plain window text the
     * proofread was asked to correct (BEFORE any annotation block).
     */
    fun rejectionReason(inputText: String, reply: String): String? {
        if (reply.isBlank()) return "blank reply"
        val lower = reply.lowercase()
        val trimmed = lower.trimStart()
        if (trimmed == "sorry" || trimmed.startsWith("sorry,") || trimmed.startsWith("sorry ")) {
            return "refusal marker: leading 'sorry'"
        }
        for (phrase in REFUSAL_PHRASES) {
            if (phrase in lower) return "refusal marker: '$phrase'"
        }
        val inputWords = WORD.findAll(inputText.lowercase()).map { it.value }.toSet()
        val replyWords = WORD.findAll(lower).map { it.value }.toList()
        if (replyWords.isEmpty()) return "no content words"
        val shared = replyWords.count { it in inputWords }
        if (shared.toDouble() / replyWords.size < MIN_WORD_OVERLAP) {
            return "word overlap $shared/${replyWords.size} < $MIN_WORD_OVERLAP"
        }
        return null
    }

    /** Convenience predicate form of [rejectionReason]. */
    fun isSaneReply(inputText: String, reply: String): Boolean =
        rejectionReason(inputText, reply) == null
}
