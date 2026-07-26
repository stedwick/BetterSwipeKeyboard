package com.example.betterswipekeyboard.proofread

/** Which proofreading engine backs the sparkly button. */
enum class ProofreaderBackend { NONE, ON_DEVICE, CLOUD }

/**
 * On-device Gemini Nano is always preferred (private, free); the OpenRouter
 * cloud backend is the fallback for devices without Nano support.
 */
fun selectBackend(mlKit: ProofreaderStatus, hasApiKey: Boolean): ProofreaderBackend = when {
    mlKit == ProofreaderStatus.AVAILABLE -> ProofreaderBackend.ON_DEVICE
    hasApiKey -> ProofreaderBackend.CLOUD
    else -> ProofreaderBackend.NONE
}
