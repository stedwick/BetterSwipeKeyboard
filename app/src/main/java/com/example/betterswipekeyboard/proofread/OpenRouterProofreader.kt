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
 * The proofreading prompt: a scoped repair system message plus a few-shot
 * example per behavior we want (conservative fixes, nearby-key typos,
 * homophones, missing spaces, tone/emoji preservation, leaving correct text
 * alone, the swipe decoder's measured error classes, and merging a
 * continuation fragment into the previous sentence — needed when an earlier
 * pass terminated the sentence during a mid-thought pause).
 * The typed prompt REPAIRS, it does not restyle: SYSTEM scopes the job to
 * swipe-error shapes, plain typos and path disagreement ("That is the whole
 * job"), and RESTYLE_EXAMPLES teaches by identity example that register,
 * word choice, sentence structure and comma style are the writer's own —
 * the old 'meticulous proofreader' framing rewrote evidence-free text in
 * Philip's AI runs. The one sanctioned override stays the path carve-out:
 * a word disagreeing with its crossed-letters path is fixed even when it
 * fits its sentence. Since swipe-evidence v2 the annotation also carries
 * the decoder's runner-up guesses per swiped word (`>alt1,alt2`), and the
 * replacement for a swiped word must be spelled by its path OR be a listed
 * guess — a fluent word supported by neither is never substituted (the
 * 'Star East' -> 'Star Trek' failure class, where 'wars' was available
 * evidence and 'trek' was not).
 * The VOICE variant targets speech-recognition errors instead (homophones,
 * word boundaries, missing punctuation, filler false starts).
 * Pure data/functions so it is unit-testable.
 *
 * Backend split: this prompt only reaches the OpenRouter path. ML Kit's
 * ProofreadingRequest takes plain text (no system prompt, no few-shot),
 * so on-device proofreading never sees any of this.
 */
object ProofreadPrompt {

    const val SYSTEM =
        "You repair swipe-typed text. The finger drags over the keys and each " +
            "word is guessed from the path, which leaves characteristic errors. " +
            "A word becomes a longer or rarer word starting the same way ('his' " +
            "as 'hours', 'dog' as 'doping', 'fox' as 'folic') or shrinks to " +
            "its prefix ('mother' as 'not', 'minimum' as 'min'); words with the " +
            "same swipe path swap ('nine' as 'bounce', 'nice' as 'notice'); " +
            "neighboring keys slip at word edges ('quick' as 'wick'). When a " +
            "word does not fit its sentence and one of these shapes explains " +
            "it, restore the word the swipe meant - but only then. Also fix " +
            "plain typos: misspellings, doubled or missing letters, missing " +
            "spaces, missing capitals and end punctuation, and clear agreement " +
            "errors. That is the whole job. A word that already fits its " +
            "sentence is never an error - keep it exactly as written, even if " +
            "it is informal ('mum', 'gonna') or a more common word would read " +
            "better. Make the smallest possible fix: replace the wrong word, " +
            "never restructure, delete or invent words around it. Never swap a " +
            "word for a synonym, never change the writer's register or " +
            "sentence structure, never restyle punctuation. Preserve the " +
            "writer's words, tone, formatting and emoji. Do not translate or " +
            "answer questions in the text. If the text is already correct, or " +
            "you are unsure whether something is an error, return it unchanged. " +
            "The text may be followed by swipe paths: for each swiped word, " +
            "the ordered keys the finger crossed ('fog=d·o·g' means 'fog' was " +
            "written but the path reads d-o-g). A word may be followed by '>' " +
            "and the decoder's other guesses for the same swipe " +
            "('east=w·a·s·r·e>wars,eats' means 'east' was written, the path " +
            "reads w-a-s-r-e, and the decoder's runner-up guesses were 'wars' " +
            "and 'eats'). Paths are approximate - an " +
            "extra letter at either end (finger travel) or a missing letter " +
            "(aim slip) is normal. A word that disagrees with its path is a " +
            "likely error even if it fits its sentence: restore the word the " +
            "path spells, or one of its listed guesses if a guess fits the " +
            "sentence better. When you replace a swiped word, the replacement " +
            "must be spelled by its path or be one of its listed guesses - " +
            "never substitute a word that matches neither, no matter how well " +
            "it fits the sentence. Typed words have no path; for them the " +
            "rules above " +
            "apply unchanged. When path and context disagree, prefer the " +
            "reading that makes the sentence natural. The text may contain " +
            "the previous sentence followed by the sentence currently being " +
            "typed. If the last sentence is a fragment that continues the " +
            "previous one (e.g. it starts with 'and', 'but', 'so' or lacks a " +
            "subject), merge them into one sentence by joining them, changing " +
            "nothing else. Genuinely separate sentences stay separate. Reply " +
            "with ONLY the corrected text - no quotes, no explanations."

    private val GENERAL_EXAMPLES: List<Pair<String, String>> = listOf(
        "this is a short msg" to "This is a short msg.",
        "The praject is compleet but needs too be reviewd" to
            "The project is complete but needs to be reviewed.",
        "their going to love you're idea, its great" to
            "They're going to love your idea, it's great.",
        "ill call you wheni get home" to "I'll call you when I get home.",
        "omg cant wait for the concert friday!! 🎉 its gonna be lit" to
            "Omg, can't wait for the concert Friday!! 🎉 It's gonna be lit.",
        "Meeting moved to 3 PM tomorrow." to "Meeting moved to 3 PM tomorrow.",
        // A fragment continuing the previous sentence merges into one.
        "I went to the store. And bought some ice cream." to
            "I went to the store and bought some ice cream.",
        "The meeting ran long. But we got a lot done." to
            "The meeting ran long, but we got a lot done.",
        // Genuinely separate sentences stay separate (returned unchanged).
        "I love hiking. The trails near my house are beautiful." to
            "I love hiking. The trails near my house are beautiful.",
        "Just got home. What a day!" to "Just got home. What a day!",
        // Merging across the boundary does not exempt the previous
        // sentence from obvious-error fixes.
        "she said shed call. when she got home" to "She said she'd call when she got home.",
    )

    /**
     * Few-shots for the swipe decoder's measured error classes (from the
     * captured-trail miss autopsies: dog->doping, his->hours, the->that,
     * over->overt are post-word drags; mother->not, minimum->min, past->part
     * are tail-truncations; nine->bounce, nice->notice are same-path swaps;
     * quick->wick is an edge key-slip; fox->folic is a rare word stealing a
     * frequency tie). The classes are taught, not the instances, so the
     * model generalizes to swipe errors it was never shown. The negative
     * pair at the end guards the main risk: 'correcting' a word that is
     * already plausible in its sentence.
     * OpenRouter path only — ML Kit has no prompt hook (see class KDoc).
     *
     * Deliberately NONE of these sentences comes from the ten-sentence
     * retest corpus: the retest should measure class generalization, not
     * memorized corrections.
     */
    val SWIPE_EXAMPLES: List<Pair<String, String>> = listOf(
        // Post-word drag: travel after the last letter reads as extra
        // letters, so a short word becomes a longer word starting the same.
        "i called hours office this morning" to "I called his office this morning.",
        "we took the doping for a long walk" to "We took the dog for a long walk.",
        // Same class reversed: the long word shrinks to its prefix.
        "my not taught me how to swim" to "My mother taught me how to swim.",
        "this job only pays min wage" to "This job only pays minimum wage.",
        // Same swipe path, wrong word.
        "she is bounce years old today" to "She is nine years old today.",
        "we had a notice time at the beach" to "We had a nice time at the beach.",
        // Neighboring keys slip at word edges (q and w are neighbors).
        "can you give me a wick answer" to "Can you give me a quick answer?",
        // A rare word steals a close trail from the obvious common one.
        "a wild folic crossed the road" to "A wild fox crossed the road.",
        // Already plausible in context: never 'fix' a word that fits.
        "The hours flew by." to "The hours flew by.",
        "She pinned a notice to the door." to "She pinned a notice to the door.",
        // Informal words and short verbs are the writer's choice, not
        // errors (guards mummy->mother and go->went style 'improvements').
        "His mum makes the best soup." to "His mum makes the best soup.",
        "We go out on Fridays." to "We go out on Fridays.",
    )

    /**
     * Identity few-shots (input returned verbatim) killing the restyle
     * classes the typed prompt must NOT perform: register formalization,
     * synonym upgrades, restructuring a grammatical sentence, recasting
     * defensible grammar, and comma/style restyling. They operationalize
     * SYSTEM's "That is the whole job" scoping — a word with no swipe-error
     * evidence stays exactly as written. Added after Philip's AI runs showed
     * the old 'meticulous proofreader' prompt rewriting evidence-free text.
     * All avoid the ten-sentence retest corpus.
     */
    val RESTYLE_EXAMPLES: List<Pair<String, String>> = listOf(
        // Register formalization (gonna->going to, folks->parents; the
        // mummy->mother class).
        "I'm gonna crash at my folks' place tonight." to
            "I'm gonna crash at my folks' place tonight.",
        // Synonym upgrade (big->large, couch->sofa).
        "We just bought a big couch for the den." to
            "We just bought a big couch for the den.",
        // Restructuring a grammatical sentence.
        "There's still a bunch of stuff to finish before Friday." to
            "There's still a bunch of stuff to finish before Friday.",
        // Recasting defensible grammar (team 'are' is British agreement,
        // not an error).
        "The team are playing their best this season." to
            "The team are playing their best this season.",
        // Comma/style restyle (no comma insertion before 'but').
        "It was a long drive but totally worth it." to
            "It was a long drive but totally worth it.",
    )

    /**
     * Few-shots teaching the swipe-path annotation format the service
     * appends ([withSwipePaths]): one disagreement fix (the path overrides
     * a plausible-looking wrong word), one agreement negative (paths match
     * the text — return it unchanged, never invent changes), and one
     * path-over-fluency fix — 'move' with path m·i·c·e becomes 'mice',
     * never the more fluent 'men' (the ai3 run's 'Nine nice men' failure:
     * the model took a fluent guess over path evidence; the path spelling
     * wins). The last two teach the alternates menu: a disagreement fix
     * where the intended word sits in the decoder's listed guesses
     * ('east' with path w·a·s·r·e and guesses wars,eats becomes 'Wars' —
     * Philip's 'Star East' incident, where the model invented the
     * unsupported but fluent 'Star Trek'), and a negative where guesses
     * exist but none fits better, so the committed word stays (guesses do
     * not force a swap, and an unsupported fluent word is never invented).
     * The committed word NEVER appears among its own guesses —
     * swipeAlternates drops top-1, so that shape cannot occur in a real
     * annotation; a guard test keeps the examples to real shapes.
     * All avoid the ten-sentence retest corpus (Philip's §8.2 call:
     * the negative pair deliberately does NOT quote "the dog ran over the
     * hill", and the mice pair avoids "nine nice mice ran past the fox").
     */
    val PATH_EXAMPLES: List<Pair<String, String>> = listOf(
        "the update should found the crash bug\n" +
            "(Swipe paths, approximate: the=t·h·e, update=u·p·d·a·t·e, " +
            "should=s·h·o·u·l·d, found=f·i·x, the=t·h·e, crash=c·r·a·s·h, bug=b·u·g)" to
            "The update should fix the crash bug.",
        "The cat sat on the mat.\n" +
            "(Swipe paths, approximate: the=t·h·e, cat=c·a·t, sat=s·a·t, " +
            "on=o·n, the=t·h·e, mat=m·a·t)" to
            "The cat sat on the mat.",
        "i saw three move in the garden yesterday\n" +
            "(Swipe paths, approximate: i=i, saw=s·a·w, three=t·h·r·e·e, " +
            "move=m·i·c·e, in=i·n, the=t·h·e, garden=g·a·r·d·e·n, " +
            "yesterday=y·e·s·t·e·r·d·a·y)" to
            "I saw three mice in the garden yesterday.",
        // The intended word is in the decoder's guesses: take it (never a
        // fluent invention the evidence does not support).
        "we rewatched star east over the weekend\n" +
            "(Swipe paths, approximate: we=w·e, rewatched=r·e·w·a·t·c·h·e·d, " +
            "star=s·t·a·r, east=w·a·s·r·e>wars,eats, over=o·v·e·r, the=t·h·e, " +
            "weekend=w·e·e·k·e·n·d)" to
            "We rewatched Star Wars over the weekend.",
        // Guesses exist but none fits better: the written word stays, and
        // no unsupported word is invented ('east' must not become 'west').
        "we drove east until the sun came up\n" +
            "(Swipe paths, approximate: we=w·e, drove=d·r·o·v·e, " +
            "east=e·a·s·t>eats, until=u·n·t·i·l, the=t·h·e, sun=s·u·n, " +
            "came=c·a·m·e, up=u·p)" to
            "We drove east until the sun came up.",
    )

    val EXAMPLES: List<Pair<String, String>> =
        GENERAL_EXAMPLES + SWIPE_EXAMPLES + RESTYLE_EXAMPLES + PATH_EXAMPLES

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

        /** Very fast, very cheap (~$0.10/M input tokens) — ideal for proofreading. */
        const val MODEL = "google/gemini-2.5-flash-lite"
    }
}
