package com.example.betterswipekeyboard.proofread

import android.content.Context
import android.util.Log
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.proofreading.ProofreaderOptions
import com.google.mlkit.genai.proofreading.Proofreading
import com.google.mlkit.genai.proofreading.ProofreadingRequest
import kotlinx.coroutines.guava.await

enum class ProofreaderStatus { UNAVAILABLE, DOWNLOADING, AVAILABLE }

/**
 * What produced the text being proofread. Speech-to-text makes different
 * errors than typing/swiping (homophones, wrong word boundaries, missing
 * punctuation, filler false starts), so dictation gets its own treatment.
 */
enum class ProofreadMode { TYPED, VOICE }

/**
 * Abstraction over the on-device AI proofreader (ML Kit GenAI Proofreading,
 * Gemini Nano in AICore — text never leaves the device). The interface keeps
 * a future LiteRT fallback possible and lets unit tests fake it.
 */
interface Proofreader {
    suspend fun status(): ProofreaderStatus
    suspend fun proofread(text: String, mode: ProofreadMode): String
    fun close()
}

class MlKitProofreader(context: Context) : Proofreader {

    // ML Kit's ProofreadingRequest carries only the text — there is no
    // custom-prompt hook. The sole tuning knob is ProofreaderOptions at
    // client construction, and InputType is fixed per client, so dictation
    // proofreading needs a second client configured with InputType.VOICE
    // (Google tunes it for same-pronunciation mix-ups instead of
    // nearby-key typos).
    private val keyboardClient = Proofreading.getClient(
        ProofreaderOptions.builder(context.applicationContext)
            // KEYBOARD tunes the model for keyboard-typical typos (nearby keys).
            .setInputType(ProofreaderOptions.InputType.KEYBOARD)
            .setLanguage(ProofreaderOptions.Language.ENGLISH)
            .build(),
    )

    private val voiceClient = Proofreading.getClient(
        ProofreaderOptions.builder(context.applicationContext)
            // VOICE tunes the model for speech-recognition errors (homophones).
            .setInputType(ProofreaderOptions.InputType.VOICE)
            .setLanguage(ProofreaderOptions.Language.ENGLISH)
            .build(),
    )

    override suspend fun status(): ProofreaderStatus =
        // Feature status is per-ProofreaderOptions, but the keyboard client
        // drives the UI toggle; a voice-client failure fails soft at
        // inference time like any proofread failure.
        when (val status = keyboardClient.checkFeatureStatus().await()) {
            FeatureStatus.AVAILABLE -> ProofreaderStatus.AVAILABLE
            FeatureStatus.DOWNLOADING -> ProofreaderStatus.DOWNLOADING
            FeatureStatus.DOWNLOADABLE -> {
                // Per Google's guidance: kick off the model download ourselves
                // instead of waiting for the first inference to trigger it.
                Log.i(TAG, "proofreading feature downloadable; starting download")
                downloadFeature()
                ProofreaderStatus.DOWNLOADING
            }
            else -> {
                Log.w(TAG, "proofreading feature status=$status (unavailable)")
                ProofreaderStatus.UNAVAILABLE
            }
        }

    private fun downloadFeature() {
        keyboardClient.downloadFeature(object : DownloadCallback {
            override fun onDownloadStarted(bytesToDownload: Long) {}
            override fun onDownloadProgress(totalBytesDownloaded: Long) {}
            override fun onDownloadCompleted() {
                Log.i(TAG, "proofreading model download completed")
            }
            override fun onDownloadFailed(e: GenAiException) {
                Log.w(TAG, "proofreading model download failed", e)
            }
        })
    }

    override suspend fun proofread(text: String, mode: ProofreadMode): String {
        // The ML Kit API takes only plain input text — no system prompt, no
        // few-shot. It receives the two-sentence window like the cloud
        // backend, but merging a continuation fragment into the previous
        // sentence (taught via ProofreadPrompt on the OpenRouter path) is
        // best-effort model behavior here and cannot be prompted.
        val client = when (mode) {
            ProofreadMode.TYPED -> keyboardClient
            ProofreadMode.VOICE -> voiceClient
        }
        val request = ProofreadingRequest.builder(text).build()
        val result = client.runInference(request).await()
        return result.results.firstOrNull()?.text.orEmpty()
    }

    override fun close() {
        keyboardClient.close()
        voiceClient.close()
    }

    private companion object {
        const val TAG = "SwipeKeyboard"
    }
}
