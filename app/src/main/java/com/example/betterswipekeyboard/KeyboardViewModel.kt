package com.example.betterswipekeyboard

import androidx.lifecycle.ViewModel
import com.example.betterswipekeyboard.proofread.ProofreaderBackend
import com.example.betterswipekeyboard.proofread.ProofreaderStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Reduces [KeyboardAction]s into a new [KeyboardState] and an optional
 * [KeyboardEffect] for the text field. Pure logic — no Android framework
 * types beyond [ViewModel] — so it is fully unit-testable, and so future
 * swipe input can emit actions through the same funnel.
 */
class KeyboardViewModel : ViewModel() {

    private val _state = MutableStateFlow(KeyboardState())
    val state: StateFlow<KeyboardState> = _state.asStateFlow()

    /** Returns the effect to apply to the InputConnection, or null for pure state changes. */
    fun onAction(action: KeyboardAction): KeyboardEffect? = when (action) {
        is KeyboardAction.InsertText -> {
            val current = _state.value
            val text = if (current.isCaps) action.text.uppercase() else action.text
            consumeOneShot()
            KeyboardEffect.CommitText(text)
        }

        is KeyboardAction.CommitWord -> {
            val caps = _state.value.shiftMode
            consumeOneShot()
            KeyboardEffect.CommitWord(
                when (caps) {
                    ShiftMode.ONE_SHOT -> action.word.replaceFirstChar { it.uppercase() }
                    ShiftMode.LOCKED -> action.word.uppercase()
                    ShiftMode.OFF -> action.word
                },
            )
        }

        KeyboardAction.Backspace -> KeyboardEffect.DeleteBackward

        KeyboardAction.Enter -> KeyboardEffect.PerformEnter

        KeyboardAction.Shift -> {
            _state.update {
                it.copy(
                    shiftMode = when (it.shiftMode) {
                        ShiftMode.OFF -> ShiftMode.ONE_SHOT
                        ShiftMode.ONE_SHOT, ShiftMode.LOCKED -> ShiftMode.OFF
                    },
                )
            }
            null
        }

        KeyboardAction.CapsLock -> {
            _state.update { it.copy(shiftMode = ShiftMode.LOCKED) }
            null
        }

        is KeyboardAction.SwitchLayout -> {
            _state.update { it.copy(layout = action.layout) }
            null
        }

        KeyboardAction.ToggleProofread -> {
            _state.update { it.copy(proofreadAuto = !it.proofreadAuto) }
            null
        }

        KeyboardAction.Noop -> null
    }

    /** Called by the service after async availability checks of the AI proofreader. */
    fun setProofreaderStatus(status: ProofreaderStatus, backend: ProofreaderBackend) {
        _state.update { it.copy(proofreader = status, proofreaderBackend = backend) }
    }

    /** Called by the service while a proofread request runs. */
    fun setProofreadInFlight(inFlight: Boolean) {
        _state.update { it.copy(proofreadInFlight = inFlight) }
    }

    private fun consumeOneShot() {
        _state.update {
            if (it.shiftMode == ShiftMode.ONE_SHOT) it.copy(shiftMode = ShiftMode.OFF) else it
        }
    }
}
