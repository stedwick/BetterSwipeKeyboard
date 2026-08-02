package com.example.betterswipekeyboard.proofread

import com.example.betterswipekeyboard.eval.capturedSentenceTexts
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProofreaderBackendTest {

    @Test
    fun `on-device wins when ML Kit is available`() {
        assertEquals(
            ProofreaderBackend.ON_DEVICE,
            selectBackend(ProofreaderStatus.AVAILABLE, hasApiKey = true),
        )
        assertEquals(
            ProofreaderBackend.ON_DEVICE,
            selectBackend(ProofreaderStatus.AVAILABLE, hasApiKey = false),
        )
    }

    @Test
    fun `cloud is the fallback when ML Kit is unavailable but a key exists`() {
        assertEquals(
            ProofreaderBackend.CLOUD,
            selectBackend(ProofreaderStatus.UNAVAILABLE, hasApiKey = true),
        )
        assertEquals(
            ProofreaderBackend.CLOUD,
            selectBackend(ProofreaderStatus.DOWNLOADING, hasApiKey = true),
        )
    }

    @Test
    fun `nothing without device AI or a key`() {
        assertEquals(
            ProofreaderBackend.NONE,
            selectBackend(ProofreaderStatus.UNAVAILABLE, hasApiKey = false),
        )
        assertEquals(
            ProofreaderBackend.NONE,
            selectBackend(ProofreaderStatus.DOWNLOADING, hasApiKey = false),
        )
    }
}

/**
 * The rewritten typed prompt (feature/proofread-rewrite): protocol
 * assertions only, never verbatim-string locks on SYSTEM's prose — the
 * prompt is expected to iterate under the tools/eval harness and the test
 * must not fossilize wording. What IS locked:
 * - the wire contract (request shape, ZDR fields, temperature 0);
 * - the corpus guard (examples stay out of every captured/test sentence,
 *   distinctive word and incident pair — the keyboard must work for
 *   anyone, not be tuned to one person's writing);
 * - mechanism coverage and real annotation shapes in the examples;
 * - EVIDENCE_RULE's verbatim removability (the eval's arm E depends on
 *   SYSTEM.replace(EVIDENCE_RULE, "")).
 */
class ProofreadPromptTest {

    // ---- Wire contract ----------------------------------------------------

    @Test
    fun `request has model, system message, few-shot pairs, then the sentence`() {
        val json = JSONObject(ProofreadPrompt.buildRequestJson("test-model", "fix this pls"))
        assertEquals("test-model", json.getString("model"))

        val provider = json.getJSONObject("provider")
        assertEquals(true, provider.getBoolean("zdr"))
        assertEquals("deny", provider.getString("data_collection"))
        assertEquals("latency", provider.getString("sort"))

        assertEquals(0.0, json.getDouble("temperature"), 0.0)

        val messages = json.getJSONArray("messages")
        // 1 system + 2 per example + 1 final user message
        assertEquals(1 + ProofreadPrompt.EXAMPLES.size * 2 + 1, messages.length())

        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals(ProofreadPrompt.SYSTEM, messages.getJSONObject(0).getString("content"))

        for (i in ProofreadPrompt.EXAMPLES.indices) {
            val userMsg = messages.getJSONObject(1 + i * 2)
            val assistantMsg = messages.getJSONObject(2 + i * 2)
            assertEquals("user", userMsg.getString("role"))
            assertEquals("assistant", assistantMsg.getString("role"))
            assertEquals(ProofreadPrompt.EXAMPLES[i].first, userMsg.getString("content"))
            assertEquals(ProofreadPrompt.EXAMPLES[i].second, assistantMsg.getString("content"))
        }

        val last = messages.getJSONObject(messages.length() - 1)
        assertEquals("user", last.getString("role"))
        assertEquals("fix this pls", last.getString("content"))
    }

    @Test
    fun `request json has exactly model, messages, temperature and provider keys`() {
        val json = JSONObject(ProofreadPrompt.buildRequestJson("test-model", "fix this pls"))
        assertEquals(
            setOf("model", "messages", "temperature", "provider"),
            json.keys().asSequence().toSet(),
        )
    }

    @Test
    fun `voice request carries the same ZDR and temperature guarantees`() {
        val json = JSONObject(ProofreadPrompt.buildRequestJson("m", "x", voice = true))
        val provider = json.getJSONObject("provider")
        assertEquals(true, provider.getBoolean("zdr"))
        assertEquals("deny", provider.getString("data_collection"))
        assertEquals("latency", provider.getString("sort"))
        assertEquals(0.0, json.getDouble("temperature"), 0.0)
    }

    // ---- SYSTEM: loose semantic checks ------------------------------------

    @Test
    fun `system prompt states the job, the evidence format and the protocol`() {
        val system = ProofreadPrompt.SYSTEM
        // The job and why a written word can be wrong.
        assertTrue(system.contains("swipe"))
        // The annotation format: paths, the '>' guesses marker, typed words.
        assertTrue(system.contains("swipe paths"))
        assertTrue(system.contains("guesses"))
        assertTrue(system.contains("Typed words have no path"))
        // The window mechanics: previous sentence + continuation fragment.
        assertTrue(system.contains("previous sentence"))
        assertTrue(system.contains("merge"))
        // The protocol: verbatim application, unchanged-when-correct.
        assertTrue(system.contains("verbatim"))
        assertTrue(system.contains("ONLY the corrected text"))
        assertTrue(system.contains("unchanged"))
        // Restraint.
        assertTrue(system.contains("Never reword"))
        // The p-loop eval survivors: telegraphic phrasing is not an error,
        // and the writer's punctuation is preserved verbatim.
        assertTrue(system.contains("telegraphic"))
        assertTrue(system.contains("period to a question mark"))
    }

    @Test
    fun `evidence rule is a single sentence verbatim inside SYSTEM`() {
        // The eval's arm E removes exactly this sentence; if it stops being
        // verbatim-removable the arm silently stops testing anything.
        assertTrue(ProofreadPrompt.SYSTEM.contains(ProofreadPrompt.EVIDENCE_RULE))
        assertTrue(
            "removing EVIDENCE_RULE must change SYSTEM",
            ProofreadPrompt.SYSTEM.replace(ProofreadPrompt.EVIDENCE_RULE, "") != ProofreadPrompt.SYSTEM,
        )
    }

    // ---- Corpus guard (strengthened) ---------------------------------------

    /**
     * Distinctive captured/incident words that must never appear in a
     * prompt example (whole-word, case-insensitive). Single COMMON words
     * from the corpora (the, we, had...) cannot be banned without banning
     * English — the guard works at sentence level (below), distinctive-word
     * level (here) and incident-pair level (below).
     */
    private val BANNED_WORDS = listOf(
        // Captured-corpus distinctive words.
        "mummy", "minimum", "pizzas", "excellent", "mortimer",
        // Incident/junk words from the decoder and proofreader history.
        "folic", "wick", "doping", "bounce", "trek", "wars", "east", "star",
        "mice", "mum", "hours", "notice", "norbert", "krazy", "doh",
        "dough", "ewe", "yup", "brien", "liszt",
    )

    /** Historical incident PAIRS: both words co-occurring in one example
     * means the example encodes the incident, generic or not. */
    private val BANNED_PAIRS = listOf(
        "star" to "east", "star" to "wars", "wars" to "trek",
        "nine" to "mice", "nice" to "mice", "mice" to "men",
        "his" to "hours", "dog" to "doping", "fox" to "folic",
        "mother" to "not", "quick" to "wick", "nine" to "bounce",
        "nice" to "notice", "mummy" to "minimum",
    )

    private fun exampleTexts(): List<String> =
        ProofreadPrompt.EXAMPLES.flatMap { (input, output) -> listOf(input, output) }

    @Test
    fun `examples contain no captured sentence`() {
        val captured = capturedSentenceTexts().map { it.lowercase() }
        for (text in exampleTexts()) {
            val lower = text.lowercase()
            for (sentence in captured) {
                assertTrue(
                    "example contains captured sentence '$sentence': $text",
                    !lower.contains(sentence),
                )
            }
        }
    }

    @Test
    fun `examples contain no distinctive captured or incident word`() {
        for (text in exampleTexts()) {
            val words = Regex("[a-z']+").findAll(text.lowercase()).map { it.value }.toSet()
            for (banned in BANNED_WORDS) {
                assertTrue(
                    "example contains banned word '$banned': $text",
                    banned !in words,
                )
            }
        }
    }

    @Test
    fun `examples contain no incident word pair`() {
        for (text in exampleTexts()) {
            val words = Regex("[a-z']+").findAll(text.lowercase()).map { it.value }.toSet()
            for ((a, b) in BANNED_PAIRS) {
                assertTrue(
                    "example contains incident pair '$a'+'$b': $text",
                    a !in words || b !in words,
                )
            }
        }
    }

    @Test
    fun `examples are disjoint from the invented eval cases`() {
        // The exam's clean half and the teaching material must never be the
        // same sentences — the overfit check is meaningless otherwise.
        val file = File("../tools/eval/invented_cases.jsonl")
        assertTrue("invented cases file missing: ${file.absolutePath}", file.isFile)
        val invented = file.readLines().filter { it.isNotBlank() }
            .flatMap { line ->
                val json = JSONObject(line)
                listOf(json.getString("text").lowercase(), json.getString("expected").lowercase())
            }
        for (text in exampleTexts()) {
            val lower = text.lowercase().substringBefore("\n")
            for (case in invented) {
                assertTrue(
                    "example overlaps invented eval case: $text",
                    !lower.contains(case) && !case.contains(lower),
                )
            }
        }
    }

    // ---- Mechanism coverage and real shapes --------------------------------

    @Test
    fun `examples cover the mechanisms`() {
        val examples = ProofreadPrompt.EXAMPLES
        // Path-contradiction fixes: annotated input, changed output.
        assertTrue(
            examples.count { (i, o) -> i.contains(ProofreadPrompt.SWIPE_PATHS_MARKER) && i != o } >= 2,
        )
        // At least one annotated pair carries the '>' guesses channel.
        assertTrue(examples.any { (i, _) -> i.contains(">") })
        // A fragment merge.
        assertTrue(
            examples.any { (i, o) ->
                Regex("\\. (And|But|So) ").containsMatchIn(i) && !o.contains(". And")
            },
        )
        // Identity pairs (restraint).
        assertTrue(examples.count { (i, o) -> i == o } >= 2)
        // A plain typed repair (no annotation, output changed).
        assertTrue(
            examples.any { (i, o) -> !i.contains(ProofreadPrompt.SWIPE_PATHS_MARKER) && i != o },
        )
    }

    @Test
    fun `examples never list the committed word among its own guesses`() {
        // swipeAlternates drops top-1, so a real annotation can never carry
        // the committed word in its own guess list; examples must not teach
        // impossible shapes.
        val withGuesses = Regex("(\\w+)=\\S+?>([\\w',]+)")
        var checked = 0
        val texts = exampleTexts() + ProofreadPrompt.SYSTEM
        for (text in texts) {
            for (match in withGuesses.findAll(text)) {
                val word = match.groupValues[1]
                val guesses = match.groupValues[2].split(",")
                assertTrue("$word listed among its own guesses", word !in guesses)
                checked++
            }
        }
        assertTrue("no guess lists found — the guard would be vacuous", checked > 0)
    }

    // ---- Annotation helpers (unchanged pipeline) ---------------------------

    @Test
    fun `withSwipePaths appends the marker block`() {
        val annotated = ProofreadPrompt.withSwipePaths(
            "the fog ram",
            listOf(
                SwipedWordLog.Entry("fog", "dog"),
                SwipedWordLog.Entry("ram", "ran"),
            ),
        )
        assertEquals(
            "the fog ram\n(Swipe paths, approximate: fog=dog, ram=ran)",
            annotated,
        )
    }

    @Test
    fun `withSwipePaths appends decoder alternates after a greater-than`() {
        val annotated = ProofreadPrompt.withSwipePaths(
            "she wore a down",
            listOf(
                SwipedWordLog.Entry("wore", "wore"),
                SwipedWordLog.Entry("down", "gown", listOf("gown", "gone")),
            ),
        )
        assertEquals(
            "she wore a down\n(Swipe paths, approximate: wore=wore, down=gown>gown,gone)",
            annotated,
        )
    }

    @Test
    fun `withSwipePaths leaves unannotated text unchanged`() {
        assertEquals("plain typed text", ProofreadPrompt.withSwipePaths("plain typed text", emptyList()))
    }

    @Test
    fun `withSwipePaths caps the block at the most recent words`() {
        val paths = (1..25).map { SwipedWordLog.Entry("w$it", "p$it") }
        val annotated = ProofreadPrompt.withSwipePaths("text", paths)
        // w1..w5 dropped, w6..w25 kept.
        assertTrue(!annotated.contains("w5="))
        assertTrue(annotated.contains("w6="))
        assertTrue(annotated.contains("w25="))
        assertEquals(
            ProofreadPrompt.MAX_ANNOTATED_WORDS,
            Regex("w\\d+=").findAll(annotated).count(),
        )
    }

    @Test
    fun `echo guard detects an annotated reply`() {
        assertTrue(ProofreadPrompt.containsSwipePathsMarker("Fixed. (Swipe paths, approximate: x=y)"))
        assertTrue(!ProofreadPrompt.containsSwipePathsMarker("Just the fixed text."))
    }

    // ---- Voice prompt (out of scope for the rewrite, locked as-is) ---------

    @Test
    fun `voice prompt has no swipe-path wording`() {
        assertTrue(!ProofreadPrompt.VOICE_SYSTEM.contains("swipe path"))
        assertTrue(ProofreadPrompt.VOICE_EXAMPLES.none { (input, _) -> input.contains("=") })
    }

    @Test
    fun `voice request uses the voice system message and examples`() {
        val json = JSONObject(
            ProofreadPrompt.buildRequestJson("test-model", "their at the beech", voice = true),
        )
        assertEquals("test-model", json.getString("model"))

        val messages = json.getJSONArray("messages")
        // 1 system + 2 per example + 1 final user message
        assertEquals(1 + ProofreadPrompt.VOICE_EXAMPLES.size * 2 + 1, messages.length())

        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals(ProofreadPrompt.VOICE_SYSTEM, messages.getJSONObject(0).getString("content"))

        val last = messages.getJSONObject(messages.length() - 1)
        assertEquals("user", last.getString("role"))
        assertEquals("their at the beech", last.getString("content"))
    }

    @Test
    fun `default request keeps the typed prompt`() {
        val json = JSONObject(ProofreadPrompt.buildRequestJson("test-model", "fix this pls"))
        val messages = json.getJSONArray("messages")
        assertEquals(ProofreadPrompt.SYSTEM, messages.getJSONObject(0).getString("content"))
    }

    // ---- Response parsing ---------------------------------------------------

    @Test
    fun `parseResponse returns trimmed content of first choice`() {
        val body = """
            {"choices": [{"message": {"role": "assistant", "content": "  Fixed text.  "}}]}
        """.trimIndent()
        assertEquals("Fixed text.", ProofreadPrompt.parseResponse(body))
    }

    @Test(expected = Exception::class)
    fun `parseResponse throws on empty choices`() {
        ProofreadPrompt.parseResponse("""{"choices": []}""")
    }
}
