package com.example.betterswipekeyboard.proofread

import android.content.Context
import com.google.mlkit.genai.common.FeatureStatus
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
        ProofreaderOptions.builder(context.applicationContext).build(),
    )

    override suspend fun status(): ProofreaderStatus =
        when (client.checkFeatureStatus().await()) {
            FeatureStatus.AVAILABLE -> ProofreaderStatus.AVAILABLE
            FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING ->
                ProofreaderStatus.DOWNLOADING
            else -> ProofreaderStatus.UNAVAILABLE
        }

    override suspend fun proofread(text: String): String {
        val request = ProofreadingRequest.builder(text).build()
        val result = client.runInference(request).await()
        return result.results.firstOrNull()?.text.orEmpty()
    }

    override fun close() {
        client.close()
    }
}
