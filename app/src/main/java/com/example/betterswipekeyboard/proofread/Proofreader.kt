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
 * Abstraction over the on-device AI proofreader (ML Kit GenAI Proofreading,
 * Gemini Nano in AICore — text never leaves the device). The interface keeps
 * a future LiteRT fallback possible and lets unit tests fake it.
 */
interface Proofreader {
    suspend fun status(): ProofreaderStatus
    suspend fun proofread(text: String): String
    fun close()
}

class MlKitProofreader(context: Context) : Proofreader {

    private val client = Proofreading.getClient(
        ProofreaderOptions.builder(context.applicationContext)
            // KEYBOARD tunes the model for keyboard-typical typos (nearby keys).
            .setInputType(ProofreaderOptions.InputType.KEYBOARD)
            .setLanguage(ProofreaderOptions.Language.ENGLISH)
            .build(),
    )

    override suspend fun status(): ProofreaderStatus =
        when (val status = client.checkFeatureStatus().await()) {
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
        client.downloadFeature(object : DownloadCallback {
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

    override suspend fun proofread(text: String): String {
        val request = ProofreadingRequest.builder(text).build()
        val result = client.runInference(request).await()
        return result.results.firstOrNull()?.text.orEmpty()
    }

    override fun close() {
        client.close()
    }

    private companion object {
        const val TAG = "SwipeKeyboard"
    }
}
