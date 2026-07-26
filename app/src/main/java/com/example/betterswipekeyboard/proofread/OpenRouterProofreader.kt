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
 * The proofreading prompt: a strict system message plus a few-shot example
 * per behavior we want (conservative fixes, nearby-key typos, homophones,
 * missing spaces, tone/emoji preservation, and leaving correct text alone).
 * The VOICE variant targets speech-recognition errors instead (homophones,
 * word boundaries, missing punctuation, filler false starts).
 * Pure data/functions so it is unit-testable.
 */
object ProofreadPrompt {

    const val SYSTEM =
        "You are a meticulous proofreader. Correct spelling, grammar, punctuation " +
            "and capitalization. Preserve the writer's meaning, tone, formatting and emoji. " +
            "Do not translate or answer questions in the text. If the text is already " +
            "correct, return it unchanged. Reply with ONLY the corrected text - no " +
            "quotes, no explanations."

    val EXAMPLES: List<Pair<String, String>> = listOf(
        "this is a short msg" to "This is a short msg.",
        "The praject is compleet but needs too be reviewd" to
            "The project is complete but needs to be reviewed.",
        "their going to love you're idea, its great" to
            "They're going to love your idea, it's great.",
        "ill call you wheni get home" to "I'll call you when I get home.",
        "omg cant wait for the concert friday!! 🎉 its gonna be lit" to
            "Omg, can't wait for the concert Friday!! 🎉 It's gonna be lit.",
        "Meeting moved to 3 PM tomorrow." to "Meeting moved to 3 PM tomorrow.",
    )

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
