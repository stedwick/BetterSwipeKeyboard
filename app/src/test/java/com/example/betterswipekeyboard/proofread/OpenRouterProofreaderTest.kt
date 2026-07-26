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
