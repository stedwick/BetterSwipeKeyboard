package com.example.betterswipekeyboard

import androidx.lifecycle.ViewModel
import com.example.betterswipekeyboard.clipboard.ClipboardHistory
import com.example.betterswipekeyboard.layout.LayoutId
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

    private val clipboardHistory = ClipboardHistory()

    /** Returns the effect to apply to the InputConnection, or null for pure state changes. */
    fun onAction(action: KeyboardAction): KeyboardEffect? = when (action) {
        is KeyboardAction.InsertText -> {
            val current = _state.value
            val text = if (current.isCaps) action.text.uppercase() else action.text
            consumeOneShot()
            clearSwipeFlag()
            KeyboardEffect.CommitText(text)
        }

        is KeyboardAction.CommitWord -> {
            val caps = _state.value.shiftMode
            consumeOneShot()
            _state.update { it.copy(lastCommitWasSwipe = true) }
            KeyboardEffect.CommitWord(
                when (caps) {
                    ShiftMode.ONE_SHOT -> action.word.replaceFirstChar { it.uppercase() }
                    ShiftMode.LOCKED -> action.word.uppercase()
                    ShiftMode.OFF -> action.word
                },
            )
        }

        // First backspace after a swipe deletes the whole just-swiped word
        // (and consumes the flag); anything after is a plain char delete —
        // so a held-backspace repeat goes word, then characters.
        KeyboardAction.Backspace ->
            if (_state.value.lastCommitWasSwipe) {
                clearSwipeFlag()
                KeyboardEffect.DeleteWordBackward
            } else {
                KeyboardEffect.DeleteBackward
            }

        KeyboardAction.Enter -> {
            clearSwipeFlag()
            KeyboardEffect.PerformEnter
        }

        // Cursor moves don't consume one-shot shift: move, then type the
        // shifted letter.
        is KeyboardAction.MoveCursor -> {
            clearSwipeFlag()
            KeyboardEffect.MoveCursor(action.steps)
        }

        KeyboardAction.Shift -> {
            _state.update {
                it.copy(
                    lastCommitWasSwipe = false,
                    shiftMode = when (it.shiftMode) {
                        ShiftMode.OFF -> ShiftMode.ONE_SHOT
                        ShiftMode.ONE_SHOT, ShiftMode.LOCKED -> ShiftMode.OFF
                    },
                )
            }
            null
        }

        KeyboardAction.CapsLock -> {
            _state.update { it.copy(lastCommitWasSwipe = false, shiftMode = ShiftMode.LOCKED) }
            null
        }

        is KeyboardAction.SwitchLayout -> {
            _state.update { it.copy(lastCommitWasSwipe = false, layout = action.layout) }
            null
        }

        KeyboardAction.ToggleProofread -> {
            _state.update {
                it.copy(lastCommitWasSwipe = false, proofreadAuto = !it.proofreadAuto)
            }
            null
        }

        // No state change: the service uses these solely to suspend and
        // restart the auto-proofread inactivity timer around gestures.
        // They deliberately do NOT clear lastCommitWasSwipe: they wrap
        // every gesture (GestureStarted, the action, GestureEnded), so the
        // swipe's own CommitWord arrives between them and a backspace
        // tap's GestureStarted precedes the Backspace that must still see
        // the flag.
        KeyboardAction.GestureStarted -> null
        KeyboardAction.GestureEnded -> null

        // Starting/stopping dictation needs permission + availability checks
        // (Android types), so the service decides and reports back via
        // setVoiceState/setVoicePartial; the reducer only records outcomes.
        KeyboardAction.ToggleVoice -> null

        is KeyboardAction.PasteClip -> {
            // Unlike InsertText, clips commit verbatim: uppercasing a paste
            // under caps lock would corrupt it, and PasteText skips the
            // leading-space rules and auto-proofread for the same reason.
            // Pasting returns to letters.
            consumeOneShot()
            _state.update { it.copy(lastCommitWasSwipe = false, layout = LayoutId.LETTERS) }
            KeyboardEffect.PasteText(action.text)
        }

        is KeyboardAction.DeleteClip -> {
            clipboardHistory.remove(action.text)
            clearSwipeFlag()
            refreshClipboard()
            null
        }

        KeyboardAction.Noop -> null
    }

    /**
     * Called by the service's ClipboardManager listener for each accepted
     * clip. Refreshes the state snapshot so the clipboard panel stays
     * current; copies are rare and the snapshot is ≤ 50 entries.
     */
    fun addClip(text: String) {
        if (clipboardHistory.add(text)) refreshClipboard()
    }

    /** Called by the service after async availability checks of the AI proofreader. */
    fun setProofreaderStatus(status: ProofreaderStatus, backend: ProofreaderBackend) {
        _state.update { it.copy(proofreader = status, proofreaderBackend = backend) }
    }

    /** Called by the service while a proofread request runs. */
    fun setProofreadInFlight(inFlight: Boolean) {
        _state.update { it.copy(proofreadInFlight = inFlight) }
    }

    /** Called by the service on every voice-state transition. */
    fun setVoiceState(state: VoiceState) {
        _state.update {
            // The partial transcript is only meaningful while listening.
            if (state == VoiceState.LISTENING) it.copy(voice = state)
            else it.copy(voice = state, voicePartial = "")
        }
    }

    /** Called by the service as partial dictation results arrive. */
    fun setVoicePartial(text: String) {
        _state.update { it.copy(voicePartial = text) }
    }

    /** Called by the service when the emoji-panel suggestions change. */
    fun setEmojiSuggestions(suggestions: List<String>) {
        _state.update { it.copy(emojiSuggestions = suggestions) }
    }

    private fun refreshClipboard() {
        _state.update { it.copy(clipboard = clipboardHistory.entries()) }
    }

    private fun consumeOneShot() {
        _state.update {
            if (it.shiftMode == ShiftMode.ONE_SHOT) it.copy(shiftMode = ShiftMode.OFF) else it
        }
    }

    private fun clearSwipeFlag() {
        _state.update {
            if (it.lastCommitWasSwipe) it.copy(lastCommitWasSwipe = false) else it
        }
    }
}
