package com.example.betterswipekeyboard.proofread

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

class ProofreadPromptTest {

    @Test
    fun `request has model, system message, few-shot pairs, then the sentence`() {
        val json = JSONObject(ProofreadPrompt.buildRequestJson("test-model", "fix this pls"))
        assertEquals("test-model", json.getString("model"))

        val provider = json.getJSONObject("provider")
        assertEquals(true, provider.getBoolean("zdr"))
        assertEquals("deny", provider.getString("data_collection"))

        val messages = json.getJSONArray("messages")
        // 1 system + 2 per example + 1 final user message
        assertEquals(1 + ProofreadPrompt.EXAMPLES.size * 2 + 1, messages.length())

        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertTrue(messages.getJSONObject(0).getString("content").isNotBlank())

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
    fun `system prompt teaches boundary merging`() {
        assertTrue(ProofreadPrompt.SYSTEM.contains("merge"))
        assertTrue(ProofreadPrompt.SYSTEM.contains("previous sentence"))
    }

    @Test
    fun `system prompt guards against overcorrection of valid words`() {
        val system = ProofreadPrompt.SYSTEM
        // A word that fits its sentence is kept exactly as written...
        assertTrue(system.contains("never an error"))
        assertTrue(system.contains("keep it exactly as written"))
        // ...even when informal ('mum') or upgradable to a commoner word
        // (the mummy->mother and go->went proofreader damage classes).
        assertTrue(system.contains("'mum'"))
        // The smallest possible fix: no restructuring around the fix (the
        // never->member->'remembered once drinking' cascade class).
        assertTrue(system.contains("smallest possible fix"))
        assertTrue(system.contains("never restructure"))
    }

    @Test
    fun `swipe few-shot examples include informal-word and short-verb negatives`() {
        val negatives = ProofreadPrompt.SWIPE_EXAMPLES.filter { (input, output) -> input == output }
        assertTrue(negatives.any { it.first.contains("mum") })
        assertTrue(negatives.any { it.first.contains(" go ") })
    }

    @Test
    fun `system prompt teaches the swipe error classes`() {
        val system = ProofreadPrompt.SYSTEM
        assertTrue(system.contains("swipe-typed"))
        // Longer/rarer impostor, prefix truncation, same-path swap, edge slip.
        assertTrue(system.contains("'hours'"))
        assertTrue(system.contains("prefix"))
        assertTrue(system.contains("same swipe path"))
        assertTrue(system.contains("neighboring keys"))
        // Overcorrection guard.
        assertTrue(system.contains("already fits its sentence"))
    }

    @Test
    fun `swipe few-shot examples cover each measured error class`() {
        val examples = ProofreadPrompt.SWIPE_EXAMPLES
        // Post-word drag: short word read as a longer word starting the same.
        assertTrue(examples.contains("i called hours office this morning" to "I called his office this morning."))
        assertTrue(examples.contains("we took the doping for a long walk" to "We took the dog for a long walk."))
        // Tail truncation: long word shrunk to its prefix.
        assertTrue(examples.contains("my not taught me how to swim" to "My mother taught me how to swim."))
        assertTrue(examples.contains("this job only pays min wage" to "This job only pays minimum wage."))
        // Same-path swap.
        assertTrue(examples.contains("she is bounce years old today" to "She is nine years old today."))
        assertTrue(examples.contains("we had a notice time at the beach" to "We had a nice time at the beach."))
        // Edge key-slip.
        assertTrue(examples.contains("can you give me a wick answer" to "Can you give me a quick answer?"))
        // Rare word stealing a frequency tie.
        assertTrue(examples.contains("a wild folic crossed the road" to "A wild fox crossed the road."))
    }

    @Test
    fun `swipe few-shot examples include overcorrection negatives`() {
        // Plausible swipe-error-shaped words must be returned unchanged.
        val negatives = ProofreadPrompt.SWIPE_EXAMPLES.filter { (input, output) -> input == output }
        assertTrue(negatives.any { it.first.contains("hours") })
        assertTrue(negatives.any { it.first.contains("notice") })
    }

    @Test
    fun `swipe examples are part of the typed few-shot list`() {
        assertTrue(ProofreadPrompt.EXAMPLES.containsAll(ProofreadPrompt.SWIPE_EXAMPLES))
    }

    @Test
    fun `swipe examples avoid the ten-sentence retest corpus`() {
        // The retest re-records the same ten sentences the miss data derives
        // from; quoting one here would turn class generalization into
        // memorization. Keep the examples out of that corpus.
        val corpusFragments = listOf(
            "quick brown fox jumps over the lazy dog",
            "mummy did the minimum",
            "never once drank water",
            "excellent example of what to expect",
            "nice mice ran past",
            "go up to fix it",
            "ran over the hill",
            "power will follow",
            "how are you doing today",
            "had fun at the lake",
        )
        for ((input, output) in ProofreadPrompt.EXAMPLES) {
            val text = (input + " " + output).lowercase()
            for (fragment in corpusFragments) {
                assertTrue("few-shot example overlaps retest corpus: $fragment", !text.contains(fragment))
            }
        }
    }

    @Test
    fun `request json has exactly model, messages and provider keys`() {
        val json = JSONObject(ProofreadPrompt.buildRequestJson("test-model", "fix this pls"))
        assertEquals(setOf("model", "messages", "provider"), json.keys().asSequence().toSet())
    }

    @Test
    fun `typed request keeps ZDR fields with swipe examples present`() {
        // The privacy guarantees must survive the prompt growth.
        val provider = JSONObject(ProofreadPrompt.buildRequestJson("m", "x"))
            .getJSONObject("provider")
        assertEquals(true, provider.getBoolean("zdr"))
        assertEquals("deny", provider.getString("data_collection"))
    }

    @Test
    fun `system prompt teaches swipe paths and their primacy over plausibility`() {
        val system = ProofreadPrompt.SYSTEM
        assertTrue(system.contains("swipe paths"))
        // The crux: a disagreeing path overrides 'fits its sentence'.
        assertTrue(system.contains("disagrees with its path"))
        // Typed words (no path) keep the conservative rule.
        assertTrue(system.contains("Typed words have no path"))
        // Approximation is taught: extra/missing letters are normal.
        assertTrue(system.contains("approximate"))
    }

    @Test
    fun `path few-shot examples teach the annotation format`() {
        val paths = ProofreadPrompt.PATH_EXAMPLES
        // A disagreement fix: 'found' with path f-i-x becomes 'fix'.
        assertTrue(
            paths.any { (input, output) ->
                input.contains("found=f·i·x") && output.contains(" fix ")
            },
        )
        // An agreement negative: annotated correct text returned unchanged.
        assertTrue(
            paths.any { (input, output) ->
                input.startsWith("The cat sat on the mat.") &&
                    input.contains(ProofreadPrompt.SWIPE_PATHS_MARKER) &&
                    output == "The cat sat on the mat."
            },
        )
        assertTrue(ProofreadPrompt.EXAMPLES.containsAll(paths))
    }

    @Test
    fun `restyle examples are identity pairs included in the typed few-shot list`() {
        val restyle = ProofreadPrompt.RESTYLE_EXAMPLES
        assertTrue(restyle.isNotEmpty())
        for ((input, output) in restyle) {
            assertEquals("restyle example must return its input verbatim: $input", input, output)
        }
        assertTrue(ProofreadPrompt.EXAMPLES.containsAll(restyle))
    }

    @Test
    fun `restyle negatives cover register, synonym, restructure and comma-style classes`() {
        val texts = ProofreadPrompt.RESTYLE_EXAMPLES.map { it.first }
        // Register formalization (gonna/folks; the mummy->mother class).
        assertTrue(texts.any { it.contains("gonna") })
        // Synonym upgrade.
        assertTrue(texts.any { it.contains("couch") })
        // Restructuring a grammatical sentence.
        assertTrue(texts.any { it.contains("bunch of stuff") })
        // Comma/style restyle.
        assertTrue(texts.any { it.contains("long drive but") })
    }

    @Test
    fun `path few-shot teaches the path spelling over the fluent guess`() {
        // The ai3 'Nine nice men' failure class: 'move' with path m-i-c-e
        // must become 'mice', never the more fluent 'men'.
        assertTrue(
            ProofreadPrompt.PATH_EXAMPLES.any { (input, output) ->
                input.contains("move=m·i·c·e") && output.contains(" mice ")
            },
        )
    }

    @Test
    fun `withSwipePaths appends the marker block`() {
        val annotated = ProofreadPrompt.withSwipePaths(
            "the fog ram",
            listOf("fog" to "d·o·g", "ram" to "r·a·n"),
        )
        assertEquals(
            "the fog ram\n(Swipe paths, approximate: fog=d·o·g, ram=r·a·n)",
            annotated,
        )
    }

    @Test
    fun `withSwipePaths leaves unannotated text unchanged`() {
        assertEquals("plain typed text", ProofreadPrompt.withSwipePaths("plain typed text", emptyList()))
    }

    @Test
    fun `withSwipePaths caps the block at the most recent words`() {
        val paths = (1..25).map { "w$it" to "p$it" }
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

    @Test
    fun `voice prompt has no swipe-path wording`() {
        // Voice requests keep the separate prompt: dictated words have no
        // trails, and the annotation is never appended for them.
        assertTrue(!ProofreadPrompt.VOICE_SYSTEM.contains("swipe path"))
        assertTrue(ProofreadPrompt.VOICE_EXAMPLES.none { (input, _) -> input.contains("=") })
    }

    @Test
    fun `at least one example merges a continuation fragment into the previous sentence`() {
        assertTrue(
            "expected a few-shot example merging a fragment after a boundary",
            ProofreadPrompt.EXAMPLES.any { (input, output) ->
                Regex("\\. (And|But|So) ").containsMatchIn(input) && !output.contains(". And")
            },
        )
    }

    @Test
    fun `at least one two-sentence example is returned unchanged`() {
        // Guards the keep-separate direction against oscillation.
        assertTrue(
            "expected a two-sentence example returned verbatim",
            ProofreadPrompt.EXAMPLES.any { (input, output) ->
                input == output && Regex("[.!?] \\S.*[.!?]").containsMatchIn(input)
            },
        )
    }

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

    @Test
    fun `voice request uses the voice system message and examples`() {
        val json = JSONObject(
            ProofreadPrompt.buildRequestJson("test-model", "their at the beech", voice = true),
        )
        assertEquals("test-model", json.getString("model"))

        // Same ZDR privacy guarantees as the typed prompt.
        val provider = json.getJSONObject("provider")
        assertEquals(true, provider.getBoolean("zdr"))
        assertEquals("deny", provider.getString("data_collection"))

        val messages = json.getJSONArray("messages")
        // 1 system + 2 per example + 1 final user message
        assertEquals(1 + ProofreadPrompt.VOICE_EXAMPLES.size * 2 + 1, messages.length())

        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals(ProofreadPrompt.VOICE_SYSTEM, messages.getJSONObject(0).getString("content"))

        for (i in ProofreadPrompt.VOICE_EXAMPLES.indices) {
            val userMsg = messages.getJSONObject(1 + i * 2)
            val assistantMsg = messages.getJSONObject(2 + i * 2)
            assertEquals("user", userMsg.getString("role"))
            assertEquals("assistant", assistantMsg.getString("role"))
            assertEquals(ProofreadPrompt.VOICE_EXAMPLES[i].first, userMsg.getString("content"))
            assertEquals(ProofreadPrompt.VOICE_EXAMPLES[i].second, assistantMsg.getString("content"))
        }

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
}
