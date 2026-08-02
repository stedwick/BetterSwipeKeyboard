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
            // Tap streak: TAPPING (not swiping) TAP_TYPING_DISABLE_THRESHOLD
            // characters suspends auto-proofreading — fast tap-typing means the
            // user is mid-flow and the 2s AI pass is unwanted. Only an active
            // auto mode can be suspended: taps while the USER has it off must
            // not arm proofreadSuspendedByTaps, or the next swipe would
            // restore proofreading against explicit user intent.
            _state.update {
                val streak = it.typedTapStreak + 1
                if (it.proofreadAuto && streak >= TAP_TYPING_DISABLE_THRESHOLD) {
                    it.copy(
                        proofreadAuto = false,
                        proofreadSuspendedByTaps = true,
                        typedTapStreak = 0,
                    )
                } else {
                    it.copy(typedTapStreak = streak)
                }
            }
            KeyboardEffect.CommitText(text)
        }

        is KeyboardAction.CommitWord -> {
            val caps = _state.value.shiftMode
            consumeOneShot()
            _state.update {
                it.copy(
                    lastCommitWasSwipe = true,
                    // The strip's green center cell: the word exactly as
                    // committed (caps-transformed — see below).
                    swipedWord = applyCaps(action.word, caps),
                    // The strip renders what a tap commits: one-shot shift is
                    // consumed by this commit, so the alternates are stored
                    // already caps-transformed and SelectAlternate needs no
                    // caps logic of its own.
                    swipeAlternates = action.alternates.map { alt -> applyCaps(alt, caps) },
                    // The wider near-miss-band flank list the live strip
                    // showed (see KeyboardState.swipeStripOffers); the
                    // committed strip places these so survivors keep their
                    // mid-swipe slots.
                    swipeStripOffers = action.stripOffers.map { alt -> applyCaps(alt, caps) },
                    // A commit supersedes any failed swipe's offers — the
                    // offer tap itself commits through here and lands the
                    // picked word in the center cell.
                    failedSwipe = null,
                    // A swipe ends the tap streak and restores
                    // auto-proofreading if (and only if) the streak rule
                    // suspended it ("swiping remembers the AI was on") —
                    // user-off stays off because suspended is false then.
                    typedTapStreak = 0,
                    proofreadAuto = it.proofreadAuto || it.proofreadSuspendedByTaps,
                    proofreadSuspendedByTaps = false,
                    // A swipe owns the strip: the tap mirror goes.
                    tapLiveWord = null,
                    tappedWord = null,
                )
            }
            KeyboardEffect.CommitWord(
                applyCaps(action.word, caps),
                // Trail letters pass through unchanged: they describe keys,
                // not characters, so caps never applies to them.
                crossedLetters = action.crossedLetters,
            )
        }

        // Tap an alternate in the strip: replace the just-swiped word with
        // it. Re-arms the swipe flag (the replacement is still "the word the
        // swipe produced" — the next backspace word-deletes it, and tapping
        // another alternate swaps again), moves the picked word into the
        // strip's green center cell and drops it from the alternates; the
        // replaced-away old word disappears (no swap-back). Ignored when no
        // swipe is armed, so a stale strip can never delete text in a
        // context the user has moved on from.
        is KeyboardAction.SelectAlternate ->
            if (_state.value.lastCommitWasSwipe) {
                _state.update {
                    it.copy(
                        swipedWord = action.word,
                        swipeAlternates = it.swipeAlternates - action.word,
                        swipeStripOffers = it.swipeStripOffers - action.word,
                        tapLiveWord = null,
                        tappedWord = null,
                    )
                }
                KeyboardEffect.ReplaceSwipedWord(action.word)
            } else {
                null
            }

        // A swipe failed to commit but landed in the near-miss band: store
        // the offers for the strip (one-tap insertions, no center cell). The
        // pair is cleared AS A PAIR — a stale green center among the offers
        // would lie. lastCommitWasSwipe is deliberately untouched: the last
        // COMMIT still owns the word-delete; this gesture committed nothing.
        // No effect and no one-shot consumption — the offer tap's CommitWord
        // consumes the still-armed shift and capitalizes the picked word.
        is KeyboardAction.OfferFailedSwipe -> {
            _state.update {
                it.copy(
                    failedSwipe = FailedSwipe(action.offers, action.letters),
                    swipedWord = null,
                    swipeAlternates = emptyList(),
                    swipeStripOffers = emptyList(),
                    tapLiveWord = null,
                    tappedWord = null,
                )
            }
            null
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
            // A newline is a hard boundary for the tap mirror too. Cleared
            // here, not in clearSwipeFlag: no text-effect hook re-reads the
            // field after PerformEnter, so a stale word would linger.
            _state.update { it.copy(tapLiveWord = null, tappedWord = null) }
            KeyboardEffect.PerformEnter
        }

        // Cursor moves don't consume one-shot shift: move, then type the
        // shifted letter.
        is KeyboardAction.MoveCursor -> {
            clearSwipeFlag()
            // The word before the new cursor position is unknown here and no
            // field read follows a cursor move — a stale mirror would lie.
            _state.update { it.copy(tapLiveWord = null, tappedWord = null) }
            KeyboardEffect.MoveCursor(action.steps)
        }

        KeyboardAction.Shift -> {
            _state.update {
                it.copy(
                    lastCommitWasSwipe = false,
                    swipedWord = null,
                    swipeAlternates = emptyList(),
                    swipeStripOffers = emptyList(),
                    failedSwipe = null,
                    shiftMode = when (it.shiftMode) {
                        ShiftMode.OFF -> ShiftMode.ONE_SHOT
                        ShiftMode.ONE_SHOT, ShiftMode.LOCKED -> ShiftMode.OFF
                    },
                )
            }
            null
        }

        KeyboardAction.CapsLock -> {
            _state.update {
                it.copy(
                    lastCommitWasSwipe = false,
                    swipedWord = null,
                    swipeAlternates = emptyList(),
                    swipeStripOffers = emptyList(),
                    failedSwipe = null,
                    shiftMode = ShiftMode.LOCKED,
                )
            }
            null
        }

        is KeyboardAction.SwitchLayout -> {
            _state.update {
                it.copy(
                    lastCommitWasSwipe = false,
                    swipedWord = null,
                    swipeAlternates = emptyList(),
                    swipeStripOffers = emptyList(),
                    failedSwipe = null,
                    tapLiveWord = null,
                    tappedWord = null,
                    layout = action.layout,
                )
            }
            null
        }

        KeyboardAction.ToggleProofread -> {
            _state.update {
                it.copy(
                    lastCommitWasSwipe = false,
                    swipedWord = null,
                    swipeAlternates = emptyList(),
                    swipeStripOffers = emptyList(),
                    failedSwipe = null,
                    proofreadAuto = !it.proofreadAuto,
                    // Manual toggle is explicit user intent: it clears the
                    // tap-streak suspension memory (toggling ON while
                    // suspended stays on through the next swipe; toggling
                    // OFF leaves nothing to restore) and starts a fresh
                    // streak.
                    proofreadSuspendedByTaps = false,
                    typedTapStreak = 0,
                )
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
            _state.update {
                it.copy(
                    lastCommitWasSwipe = false,
                    swipedWord = null,
                    swipeAlternates = emptyList(),
                    swipeStripOffers = emptyList(),
                    failedSwipe = null,
                    tapLiveWord = null,
                    tappedWord = null,
                    layout = LayoutId.LETTERS,
                )
            }
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
            // Dictation ends the swipe context (it bypasses the reducer, so
            // no input action ever clears the strip), so the alternates go.
            // The tap mirror goes too: dictation replaces whatever was
            // mid-tap, and no field read re-arms it from here.
            if (state == VoiceState.LISTENING) {
                it.copy(voice = state, swipedWord = null, swipeAlternates = emptyList(), swipeStripOffers = emptyList(), failedSwipe = null, tapLiveWord = null, tappedWord = null)
            } else {
                it.copy(voice = state, voicePartial = "", swipedWord = null, swipeAlternates = emptyList(), swipeStripOffers = emptyList(), failedSwipe = null, tapLiveWord = null, tappedWord = null)
            }
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

    /**
     * Called by the service after every tap/backspace text effect with the
     * tap-strip words read from the FIELD text: exactly one of [live] (the
     * word mid-tap → blue center) and [committed] (the just-ended word →
     * green center) non-null, both null clears the tap strip.
     */
    fun setTapStrip(live: String?, committed: String?) {
        _state.update { it.copy(tapLiveWord = live, tappedWord = committed) }
    }

    /**
     * Called by the service on a fresh field start: the strip's words belong
     * to the previous field's text, so they must not be offered (or worse,
     * replace text) in the new one.
     */
    fun clearSwipeAlternates() {
        _state.update {
            if (it.swipedWord != null || it.swipeAlternates.isNotEmpty() || it.swipeStripOffers.isNotEmpty() || it.failedSwipe != null || it.tapLiveWord != null || it.tappedWord != null) {
                it.copy(swipedWord = null, swipeAlternates = emptyList(), swipeStripOffers = emptyList(), failedSwipe = null, tapLiveWord = null, tappedWord = null)
            } else {
                it
            }
        }
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
            if (it.lastCommitWasSwipe || it.swipedWord != null || it.swipeAlternates.isNotEmpty() || it.swipeStripOffers.isNotEmpty() || it.failedSwipe != null) {
                it.copy(
                    lastCommitWasSwipe = false,
                    swipedWord = null,
                    swipeAlternates = emptyList(),
                    swipeStripOffers = emptyList(),
                    failedSwipe = null,
                )
            } else {
                it
            }
        }
    }

    private fun applyCaps(word: String, caps: ShiftMode): String = when (caps) {
        ShiftMode.ONE_SHOT -> word.replaceFirstChar { it.uppercase() }
        ShiftMode.LOCKED -> word.uppercase()
        ShiftMode.OFF -> word
    }

    private companion object {
        /**
         * Tapped characters in a row that suspend auto-proofreading (Philip's
         * rule: three or more tapped characters turn the AI off; the next
         * swipe restores it if it was on — see
         * [KeyboardState.proofreadSuspendedByTaps]).
         */
        const val TAP_TYPING_DISABLE_THRESHOLD = 3
    }
}
