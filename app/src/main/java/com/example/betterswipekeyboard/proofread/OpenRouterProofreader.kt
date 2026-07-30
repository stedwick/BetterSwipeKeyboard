package com.example.betterswipekeyboard.proofread

import com.example.betterswipekeyboard.ApiKeyStore
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * The proofreading prompt: a short job statement plus a handful of GENERIC
 * invented few-shot pairs, one per mechanism the repair requires.
 *
 * Design (feature/proofread-rewrite, replacing the old five-step SYSTEM with
 * its 33 Philip-derived examples): the prompt teaches MECHANISMS, not
 * instances — every example below is authored from scratch, and a corpus
 * guard (ProofreadPromptTest) asserts none of them contains any sentence,
 * distinctive word or incident pair from the six captured trail sets, the
 * ten-sentence TDD corpus, or the project's incident history. The keyboard
 * must work for anyone, not for one person's writing; whether the model
 * actually generalizes is measured, not hoped for, by the two-sub-corpus
 * eval in tools/eval/ (real decoded sentences vs fresh invented ones — a
 * prompt that wins on only one is overfit either way).
 *
 * What SYSTEM must state, because no model can infer it (protocol, not
 * behavior teaching):
 * - what swipe typing is and why a written word can be the decoder's wrong
 *   guess for the path;
 * - the annotation format [withSwipePaths] appends (the ordered keys the
 *   finger crossed per swiped word, plus the decoder's other guesses after
 *   '>'); typed words have no entry;
 * - the window mechanics: the input may be the previous sentence plus the
 *   one currently being typed, and a trailing continuation fragment merges
 *   back (the auto-pass can fire mid-thought);
 * - the reply contract: the reply is applied VERBATIM into the text field —
 *   corrected text only, no preamble or explanation (the echo guard
 *   [containsSwipePathsMarker] depends on it) — and correct-or-unsure text
 *   is returned unchanged (the fail-soft bias that makes auto-applying AI
 *   output acceptable).
 * The one output constraint beyond the job itself is [EVIDENCE_RULE]: a
 * replacement for a swiped word must be a word the same swipe could have
 * produced. It is a constraint grounded in evidence the model holds, not a
 * pattern to learn, and it is the historically measured catastrophic
 * failure (inventing a fluent word the trail does not support). The eval
 * runs a variant with the rule REMOVED to check whether a stronger model
 * still needs the sentence — keep the rule verbatim-removable
 * (SYSTEM.replace(EVIDENCE_RULE, "")).
 *
 * The two restraint clauses after "emoji are theirs" (telegraphic/casual
 * phrasing is not an error; the writer's punctuation is preserved verbatim)
 * are the survivors of the tools/eval p-loop sweep on the shipping model
 * (tags p0-p10, report in tools/eval/report.md): ten candidate prompt
 * changes measured over the 49-case corpus, only these two improved
 * accuracy without regressions — both SYSTEM clauses, no example changes.
 *
 * Backend split: this prompt only reaches the OpenRouter path. ML Kit's
 * ProofreadingRequest takes plain text (no system prompt, no few-shot), so
 * on-device proofreading never sees any of this. The VOICE variant targets
 * speech-recognition errors instead and is deliberately untouched by the
 * rewrite (its examples remain).
 */
object ProofreadPrompt {

    /**
     * The reasonable-mis-swipe constraint, held as a separate constant so
     * the eval's no-constraint variant can remove exactly this sentence
     * (see class KDoc). If you edit it, keep it a single sentence appearing
     * verbatim inside [SYSTEM].
     */
    internal const val EVIDENCE_RULE =
        "When you replace a swiped word, the replacement must be a word " +
            "the same swipe could have produced: consistent with its path " +
            "within normal mis-swipe tolerance (an aim slip, a nearby key, " +
            "an extra or missing letter at an end), or one of the listed " +
            "guesses - never a fluent word the evidence does not support, " +
            "no matter how well it fits the sentence."

    const val SYSTEM =
        "You repair swipe-typed text. Swipe typing means the finger drags " +
            "over the keyboard's keys and every word is guessed from the " +
            "path traced, so a written word can be the decoder's wrong " +
            "guess for the path. The text may be followed by swipe paths: " +
            "for each swiped word, the ordered keys the finger crossed, " +
            "and after '>' the decoder's other guesses for the same swipe " +
            "('hold=sold>sold,told' means 'hold' was written, the path " +
            "reads s-o-l-d, and the decoder also guessed 'sold' and " +
            "'told'). Paths are approximate - an extra letter at either " +
            "end or a missing letter is normal. Typed words have no path. " +
            "The text may contain the previous sentence followed by the " +
            "sentence currently being typed; if the last sentence is a " +
            "fragment continuing the previous one, merge the two into one " +
            "sentence, changing nothing else. " +
            EVIDENCE_RULE + " " +
            "Otherwise make the smallest fix: wrong words, typos, missing " +
            "spaces, capitals or punctuation, clear agreement errors. " +
            "Never reword, restructure, formalize or otherwise improve " +
            "text that is already fine - the writer's words, tone, " +
            "formatting and emoji are theirs. Casual or telegraphic " +
            "phrasing (dropped subjects, missing commas, run-on " +
            "sentences) is not an error: do not normalize it into " +
            "polished prose. Keep the writer's punctuation exactly as " +
            "written: never change a period to a question mark, and " +
            "never insert commas or other marks the writer did not " +
            "type; the only punctuation changes allowed are adding a " +
            "missing final period and the comma when joining a " +
            "fragment. If the text is already correct, or you are " +
            "unsure whether something is an error, " +
            "return it unchanged. Do not translate or answer questions in " +
            "the text. Return ONLY the corrected text: no preamble, no " +
            "labels, no explanations, no quotes. Your reply is applied " +
            "verbatim as the corrected sentence."

    /**
     * Generic invented few-shot pairs, ONE PER MECHANISM (see class KDoc).
     * Authorship rules, enforced by the corpus guard in ProofreadPromptTest:
     * no sentence, distinctive word or incident pair from the six captured
     * trail sets, the ten-sentence TDD corpus or the project's incident
     * history; path annotations use the PRODUCTION wire format (bare
     * crossed letters, `>` before the guesses); the committed word never
     * appears among its own guesses (swipeAlternates drops top-1 — that
     * shape cannot occur in a real annotation). Negative pairs return their
     * input's intent unchanged (caps/punctuation fixes still apply).
     */
    val EXAMPLES: List<Pair<String, String>> = listOf(
        // A word contradicting its crossed path is fixed to the path's
        // reading (path-primacy), even when the written word is a real
        // word that fits its sentence AND the intended word is not among
        // the listed guesses: spell the path, ignore junk guesses.
        "the ferry crosses the english chandler\n" +
            "(Swipe paths, approximate: the=the, ferry=ferry, " +
            "crosses=crosses, english=english, " +
            "chandler=channsel>chandelier,handler)" to
            "The ferry crosses the English channel.",
        // The intended word sits in the decoder's listed guesses: take it —
        // never invent a fluent word the evidence does not support.
        "we had tomato soap for lunch\n" +
            "(Swipe paths, approximate: we=we, had=had, tomato=tomato, " +
            "soap=soup>soup,soak, for=for, lunch=lunch)" to
            "We had tomato soup for lunch.",
        // Path approximation: an extra letter in the path is normal — the
        // short word the path spells wins over the written one.
        "i need to tie my she before we leave\n" +
            "(Swipe paths, approximate: i=i, need=need, to=to, tie=tie, " +
            "my=my, she=shoe>shoe, before=before, we=we, leave=leave)" to
            "I need to tie my shoe before we leave.",
        // End punctuation is the writer's choice too: a question asked
        // with a period keeps its period (caps still fixed). Never insert
        // a question mark the writer did not write.
        "are you coming over later." to
            "Are you coming over later.",
        // A fragment continuing the previous sentence merges into it.
        "we drove out to the lake. But it started raining." to
            "We drove out to the lake, but it started raining.",
        // Plain typed errors (no paths): caps, spelling, doubled letters.
        "the resturant on fifth street opens at noon tommorow" to
            "The restaurant on fifth street opens at noon tomorrow.",
        // Guesses exist but none fits better: the written word stays (a
        // guess list does not force a swap). Caps/punctuation still fixed.
        "keep the change\n" +
            "(Swipe paths, approximate: keep=keep, the=the, " +
            "change=change>chance,changes)" to
            "Keep the change.",
        // Informal register is the writer's choice, not an error (identity).
        "We're meeting at Mario's around seven, wanna join?" to
            "We're meeting at Mario's around seven, wanna join?",
        // Casual writing with a dropped subject is voice, not a grammar
        // error to repair: imperfect-looking but intended text returns
        // verbatim (identity).
        "Was a long day, we head out early tomorrow anyway." to
            "Was a long day, we head out early tomorrow anyway.",
    )

    /** Marker prefix of the annotation block [withSwipePaths] appends —
     * shared with the echo guard ([containsSwipePathsMarker]). */
    const val SWIPE_PATHS_MARKER = "Swipe paths"

    /** Cap on annotated words per request — bounds the token cost. */
    const val MAX_ANNOTATED_WORDS = 20

    /**
     * Appends the swipe-path block to the proofread input: the most recent
     * [MAX_ANNOTATED_WORDS] swiped words as `word=path` pairs, each with its
     * decoder runner-ups appended as `>alt1,alt2` when the decoder offered
     * any (the committed word is never among them — swipeAlternates drops
     * top-1 — and their count is the strip's score-gated cap, so the token
     * cost stays bounded: ≤ 20 words × ≤ 4 short guesses). An entry with no
     * alternates renders exactly as before the alternates channel existed.
     * Returns [text] unchanged when there is nothing to annotate (typed text).
     */
    fun withSwipePaths(text: String, swiped: List<SwipedWordLog.Entry>): String {
        if (swiped.isEmpty()) return text
        val block = swiped.takeLast(MAX_ANNOTATED_WORDS)
            .joinToString(", ") { entry ->
                val base = "${entry.word}=${entry.letters}"
                if (entry.alternates.isEmpty()) {
                    base
                } else {
                    base + ">" + entry.alternates.joinToString(",")
                }
            }
        return "$text\n($SWIPE_PATHS_MARKER, approximate: $block)"
    }

    /**
     * Echo guard: the model must reply with ONLY the corrected text, so a
     * reply containing the annotation marker means it echoed the input —
     * the caller discards such results (fail soft) rather than risk
     * inserting the annotation into the text field.
     */
    fun containsSwipePathsMarker(text: String): Boolean = text.contains(SWIPE_PATHS_MARKER)

    const val VOICE_SYSTEM =
        "You are a meticulous proofreader for text produced by voice dictation " +
            "(speech-to-text). Fix the errors speech recognition makes: homophones " +
            "and same-sound mix-ups (their/there/they're, meat/meet), wrong word " +
            "boundaries, missing punctuation and capitalization, and filler false " +
            "starts (um, uh, repeated words) which you remove. Preserve the " +
            "speaker's meaning, tone and emoji. Do not translate or answer " +
            "questions in the text. If the text is already correct, return it " +
            "unchanged. Reply with ONLY the corrected text - no quotes, no " +
            "explanations."

    val VOICE_EXAMPLES: List<Pair<String, String>> = listOf(
        "their going to meat us at the beech at for" to
            "They're going to meet us at the beach at 4.",
        "um so I was I was thinking we could grab dinner tomorrow night" to
            "So I was thinking we could grab dinner tomorrow night.",
        "its there house not ours write" to "It's their house, not ours, right?",
        "please send the report buy friday" to "Please send the report by Friday.",
        "The meeting is at 3 PM tomorrow." to "The meeting is at 3 PM tomorrow.",
    )

    fun buildRequestJson(model: String, sentence: String, voice: Boolean = false): String {
        val system = if (voice) VOICE_SYSTEM else SYSTEM
        val examples = if (voice) VOICE_EXAMPLES else EXAMPLES
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", system))
        for ((input, output) in examples) {
            messages.put(JSONObject().put("role", "user").put("content", input))
            messages.put(JSONObject().put("role", "assistant").put("content", output))
        }
        messages.put(JSONObject().put("role", "user").put("content", sentence))
        return JSONObject()
            .put("model", model)
            .put("messages", messages)
            // Deterministic repair, not creative writing — and the eval
            // scores the same sampling settings the app ships.
            .put("temperature", 0)
            // Privacy: route only to zero-data-retention endpoints that do
            // not train on user data. Narrows the provider pool; if none is
            // available the request fails and the caller fails soft.
            .put(
                "provider",
                JSONObject()
                    .put("zdr", true)
                    .put("data_collection", "deny"),
            )
            .toString()
    }

    fun parseResponse(body: String): String {
        val choices = JSONObject(body).getJSONArray("choices")
        if (choices.length() == 0) throw IOException("OpenRouter returned no choices")
        return choices.getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
    }
}

/**
 * Cloud proofreader via OpenRouter's OpenAI-compatible chat API. Sentence
 * text leaves the device — this is the fallback for devices without Gemini
 * Nano support.
 */
class OpenRouterProofreader(
    private val apiKeyStore: ApiKeyStore,
) : Proofreader {

    private val http = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun status(): ProofreaderStatus =
        if (apiKeyStore.apiKey != null) ProofreaderStatus.AVAILABLE
        else ProofreaderStatus.UNAVAILABLE

    override suspend fun proofread(text: String, mode: ProofreadMode): String =
        withContext(Dispatchers.IO) {
            val key = apiKeyStore.apiKey ?: throw IOException("no OpenRouter API key configured")
            val request = Request.Builder()
                .url(ENDPOINT)
                .header("Authorization", "Bearer $key")
                .post(
                    ProofreadPrompt.buildRequestJson(MODEL, text, voice = mode == ProofreadMode.VOICE)
                        .toRequestBody("application/json".toMediaType()),
                )
                .build()
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("OpenRouter HTTP ${response.code}: $body")
                }
                ProofreadPrompt.parseResponse(body)
            }
        }

    override fun close() {
        http.dispatcher.executorService.shutdown()
        http.connectionPool.evictAll()
    }

    internal companion object {
        const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

        /**
         * Very fast, very cheap (~$0.06/M input tokens) and — decisive for a
         * keyboard — sub-second: the tools/eval speed sweeps (tags r2-r5,
         * t1/t2) measured it at p50 ~0.6s with an 88-97% sub-1s rate while
         * the gemini-2.5-flash-lite incumbent failed the 1s bar half the time
         * under provider congestion. Accuracy trails flash-lite on the
         * hardest real-trail cases; closing that gap by iterating the PROMPT
         * (not the model) is the branch's mission. The zero-data-retention
         * pre-flight passed (the request's provider filter fails loud
         * otherwise).
         */
        const val MODEL = "amazon/nova-micro-v1"
    }
}
