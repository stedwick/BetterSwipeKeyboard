package com.example.betterswipekeyboard

import com.example.betterswipekeyboard.clipboard.ClipEntry
import com.example.betterswipekeyboard.layout.LayoutId
import com.example.betterswipekeyboard.proofread.ProofreaderBackend
import com.example.betterswipekeyboard.proofread.ProofreaderStatus

enum class ShiftMode { OFF, ONE_SHOT, LOCKED }

/** Voice dictation state machine; transitions are driven by the service. */
enum class VoiceState { OFF, LISTENING, PERMISSION_REQUIRED, UNAVAILABLE }

/**
 * Everything the keyboard UI needs to render. When swipe typing arrives,
 * transient swipe state (trail in progress, candidate words) should be added
 * here so the UI keeps a single source of truth.
 */
data class KeyboardState(
    val shiftMode: ShiftMode = ShiftMode.OFF,
    val layout: LayoutId = LayoutId.LETTERS,
    val proofreader: ProofreaderStatus = ProofreaderStatus.UNAVAILABLE,
    val proofreaderBackend: ProofreaderBackend = ProofreaderBackend.NONE,
    /** Auto-proofreading toggle: while on, text is proofread after 1s of idle. */
    val proofreadAuto: Boolean = false,
    val proofreadInFlight: Boolean = false,
    /** While not OFF, the key rows are replaced by the voice panel. */
    val voice: VoiceState = VoiceState.OFF,
    /** Live partial transcript shown in the voice panel while LISTENING. */
    val voicePartial: String = "",
    /** Mirror of the service-observed clipboard history, newest first. */
    val clipboard: List<ClipEntry> = emptyList(),
    /** Suggestions shown in the row atop the emoji panel; service-computed. */
    val emojiSuggestions: List<String> = emptyList(),
) {
    /** Letter labels render uppercase whenever any caps mode is active. */
    val isCaps: Boolean get() = shiftMode != ShiftMode.OFF
}

/**
 * Pure, unit-tested: the first non-blank recognition hypothesis, trimmed —
 * or null when there is nothing worth committing.
 */
fun bestTranscript(candidates: List<String>?): String? =
    candidates?.firstOrNull { it.isNotBlank() }?.trim()
