package com.example.betterswipekeyboard

import com.example.betterswipekeyboard.layout.LayoutId
import com.example.betterswipekeyboard.proofread.ProofreaderBackend
import com.example.betterswipekeyboard.proofread.ProofreaderStatus

enum class ShiftMode { OFF, ONE_SHOT, LOCKED }

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
    val proofreadInFlight: Boolean = false,
) {
    /** Letter labels render uppercase whenever any caps mode is active. */
    val isCaps: Boolean get() = shiftMode != ShiftMode.OFF
}
