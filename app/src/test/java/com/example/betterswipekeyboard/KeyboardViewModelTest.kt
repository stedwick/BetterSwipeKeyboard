package com.example.betterswipekeyboard

import com.example.betterswipekeyboard.layout.LayoutId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeyboardViewModelTest {

    private fun viewModel() = KeyboardViewModel()

    @Test
    fun `text inserts lowercase when shift is off`() {
        val vm = viewModel()
        val effect = vm.onAction(KeyboardAction.InsertText("a"))
        assertEquals(KeyboardEffect.CommitText("a"), effect)
    }

    @Test
    fun `one shot shift uppercases one letter then turns off`() {
        val vm = viewModel()
        assertNull(vm.onAction(KeyboardAction.Shift))
        assertEquals(ShiftMode.ONE_SHOT, vm.state.value.shiftMode)

        assertEquals(KeyboardEffect.CommitText("A"), vm.onAction(KeyboardAction.InsertText("a")))
        assertEquals(ShiftMode.OFF, vm.state.value.shiftMode)

        assertEquals(KeyboardEffect.CommitText("b"), vm.onAction(KeyboardAction.InsertText("b")))
    }

    @Test
    fun `caps lock keeps uppercasing until shift tapped`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.CapsLock)
        assertEquals(ShiftMode.LOCKED, vm.state.value.shiftMode)

        assertEquals(KeyboardEffect.CommitText("A"), vm.onAction(KeyboardAction.InsertText("a")))
        assertEquals(KeyboardEffect.CommitText("B"), vm.onAction(KeyboardAction.InsertText("b")))
        assertEquals(ShiftMode.LOCKED, vm.state.value.shiftMode)

        vm.onAction(KeyboardAction.Shift)
        assertEquals(ShiftMode.OFF, vm.state.value.shiftMode)
    }

    @Test
    fun `layout switching produces no editor effect`() {
        val vm = viewModel()
        assertNull(vm.onAction(KeyboardAction.SwitchLayout(LayoutId.SYMBOLS)))
        assertEquals(LayoutId.SYMBOLS, vm.state.value.layout)
        assertNull(vm.onAction(KeyboardAction.SwitchLayout(LayoutId.LETTERS)))
        assertEquals(LayoutId.LETTERS, vm.state.value.layout)
    }

    @Test
    fun `numeric layout switching produces no editor effect`() {
        val vm = viewModel()
        assertNull(vm.onAction(KeyboardAction.SwitchLayout(LayoutId.NUMERIC)))
        assertEquals(LayoutId.NUMERIC, vm.state.value.layout)
        assertNull(vm.onAction(KeyboardAction.SwitchLayout(LayoutId.LETTERS)))
        assertEquals(LayoutId.LETTERS, vm.state.value.layout)
    }

    @Test
    fun `emoji layout switching produces no editor effect`() {
        val vm = viewModel()
        assertNull(vm.onAction(KeyboardAction.SwitchLayout(LayoutId.EMOJI)))
        assertEquals(LayoutId.EMOJI, vm.state.value.layout)
        assertNull(vm.onAction(KeyboardAction.SwitchLayout(LayoutId.LETTERS)))
        assertEquals(LayoutId.LETTERS, vm.state.value.layout)
    }

    @Test
    fun `emoji insert commits verbatim`() {
        val vm = viewModel()
        assertEquals(KeyboardEffect.CommitText("😀"), vm.onAction(KeyboardAction.InsertText("😀")))
    }

    @Test
    fun `one shot shift does not mangle emoji and still clears`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.Shift)
        assertEquals(KeyboardEffect.CommitText("😀"), vm.onAction(KeyboardAction.InsertText("😀")))
        assertEquals(ShiftMode.OFF, vm.state.value.shiftMode)
    }

    @Test
    fun `backspace and enter map to editor effects`() {
        val vm = viewModel()
        assertEquals(KeyboardEffect.DeleteBackward, vm.onAction(KeyboardAction.Backspace))
        assertEquals(KeyboardEffect.PerformEnter, vm.onAction(KeyboardAction.Enter))
    }

    @Test
    fun `commit word flows through as word effect`() {
        val vm = viewModel()
        assertEquals(KeyboardEffect.CommitWord("hello"), vm.onAction(KeyboardAction.CommitWord("hello")))
    }

    @Test
    fun `crossed letters pass through the commit word reduction`() {
        val vm = viewModel()
        assertEquals(
            KeyboardEffect.CommitWord("hello", "h·e·l·o"),
            vm.onAction(KeyboardAction.CommitWord("hello", "h·e·l·o")),
        )
    }

    @Test
    fun `caps transforms the word but not the crossed letters`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.Shift)
        assertEquals(
            KeyboardEffect.CommitWord("Hello", "h·e·l·o"),
            vm.onAction(KeyboardAction.CommitWord("hello", "h·e·l·o")),
        )
    }

    @Test
    fun `one shot shift capitalizes swiped word then turns off`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.Shift)
        assertEquals(KeyboardEffect.CommitWord("Hello"), vm.onAction(KeyboardAction.CommitWord("hello")))
        assertEquals(ShiftMode.OFF, vm.state.value.shiftMode)
        assertEquals(KeyboardEffect.CommitWord("world"), vm.onAction(KeyboardAction.CommitWord("world")))
    }

    @Test
    fun `caps lock uppercases whole swiped word`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.CapsLock)
        assertEquals(KeyboardEffect.CommitWord("HELLO"), vm.onAction(KeyboardAction.CommitWord("hello")))
        assertEquals(ShiftMode.LOCKED, vm.state.value.shiftMode)
    }

    @Test
    fun `proofread toggle flips auto state without editor effect`() {
        val vm = viewModel()
        assertEquals(false, vm.state.value.proofreadAuto)
        assertNull(vm.onAction(KeyboardAction.ToggleProofread))
        assertEquals(true, vm.state.value.proofreadAuto)
        assertNull(vm.onAction(KeyboardAction.ToggleProofread))
        assertEquals(false, vm.state.value.proofreadAuto)
    }

    @Test
    fun `three tapped characters suspend auto proofreading`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.ToggleProofread)
        assertEquals(KeyboardEffect.CommitText("a"), vm.onAction(KeyboardAction.InsertText("a")))
        assertEquals(KeyboardEffect.CommitText("b"), vm.onAction(KeyboardAction.InsertText("b")))
        assertEquals(true, vm.state.value.proofreadAuto)
        assertEquals(KeyboardEffect.CommitText("c"), vm.onAction(KeyboardAction.InsertText("c")))
        assertEquals(false, vm.state.value.proofreadAuto)
        assertEquals(true, vm.state.value.proofreadSuspendedByTaps)
        assertEquals(0, vm.state.value.typedTapStreak)
    }

    @Test
    fun `two taps leave auto proofreading on`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.ToggleProofread)
        vm.onAction(KeyboardAction.InsertText("a"))
        vm.onAction(KeyboardAction.InsertText("b"))
        assertEquals(true, vm.state.value.proofreadAuto)
        assertEquals(false, vm.state.value.proofreadSuspendedByTaps)
        assertEquals(2, vm.state.value.typedTapStreak)
    }

    @Test
    fun `a swipe restores auto proofreading suspended by taps`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.ToggleProofread)
        repeat(3) { vm.onAction(KeyboardAction.InsertText("a")) }
        assertEquals(false, vm.state.value.proofreadAuto)
        assertEquals(KeyboardEffect.CommitWord("hello"), vm.onAction(KeyboardAction.CommitWord("hello")))
        assertEquals(true, vm.state.value.proofreadAuto)
        assertEquals(false, vm.state.value.proofreadSuspendedByTaps)
        assertEquals(0, vm.state.value.typedTapStreak)
    }

    @Test
    fun `a swipe does not restore proofreading the user turned off`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.ToggleProofread)
        vm.onAction(KeyboardAction.ToggleProofread)
        assertEquals(KeyboardEffect.CommitWord("hello"), vm.onAction(KeyboardAction.CommitWord("hello")))
        assertEquals(false, vm.state.value.proofreadAuto)
        assertEquals(false, vm.state.value.proofreadSuspendedByTaps)
    }

    @Test
    fun `taps while the user has proofreading off do not arm a restore`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.ToggleProofread)
        vm.onAction(KeyboardAction.ToggleProofread)
        repeat(3) { vm.onAction(KeyboardAction.InsertText("a")) }
        // The streak counts but must NOT arm the suspension flag: otherwise
        // the next swipe would resurrect proofreading against explicit
        // user intent.
        assertEquals(false, vm.state.value.proofreadSuspendedByTaps)
        vm.onAction(KeyboardAction.CommitWord("hello"))
        assertEquals(false, vm.state.value.proofreadAuto)
    }

    @Test
    fun `manual toggle on while suspended stays on through the next swipe`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.ToggleProofread)
        repeat(3) { vm.onAction(KeyboardAction.InsertText("a")) }
        assertEquals(true, vm.state.value.proofreadSuspendedByTaps)
        vm.onAction(KeyboardAction.ToggleProofread)
        assertEquals(true, vm.state.value.proofreadAuto)
        assertEquals(false, vm.state.value.proofreadSuspendedByTaps)
        vm.onAction(KeyboardAction.CommitWord("hello"))
        assertEquals(true, vm.state.value.proofreadAuto)
    }

    @Test
    fun `a restore re-arms the tap streak rule`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.ToggleProofread)
        repeat(3) { vm.onAction(KeyboardAction.InsertText("a")) }
        vm.onAction(KeyboardAction.CommitWord("hello"))
        assertEquals(true, vm.state.value.proofreadAuto)
        repeat(3) { vm.onAction(KeyboardAction.InsertText("a")) }
        assertEquals(false, vm.state.value.proofreadAuto)
        assertEquals(true, vm.state.value.proofreadSuspendedByTaps)
    }

    @Test
    fun `a swipe resets the tap streak`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.ToggleProofread)
        vm.onAction(KeyboardAction.InsertText("a"))
        vm.onAction(KeyboardAction.InsertText("b"))
        vm.onAction(KeyboardAction.CommitWord("hello"))
        vm.onAction(KeyboardAction.InsertText("c"))
        vm.onAction(KeyboardAction.InsertText("d"))
        assertEquals(true, vm.state.value.proofreadAuto)
        assertEquals(2, vm.state.value.typedTapStreak)
    }

    @Test
    fun `a manual toggle resets the tap streak`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.ToggleProofread)
        vm.onAction(KeyboardAction.InsertText("a"))
        vm.onAction(KeyboardAction.InsertText("b"))
        vm.onAction(KeyboardAction.ToggleProofread)
        vm.onAction(KeyboardAction.ToggleProofread)
        vm.onAction(KeyboardAction.InsertText("c"))
        vm.onAction(KeyboardAction.InsertText("d"))
        assertEquals(true, vm.state.value.proofreadAuto)
        assertEquals(2, vm.state.value.typedTapStreak)
    }

    @Test
    fun `backspace does not reset the tap streak`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.ToggleProofread)
        vm.onAction(KeyboardAction.InsertText("a"))
        vm.onAction(KeyboardAction.InsertText("b"))
        vm.onAction(KeyboardAction.Backspace)
        vm.onAction(KeyboardAction.InsertText("c"))
        assertEquals(false, vm.state.value.proofreadAuto)
        assertEquals(true, vm.state.value.proofreadSuspendedByTaps)
    }

    @Test
    fun `gesture markers interleaved with taps do not break the streak`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.ToggleProofread)
        repeat(3) {
            vm.onAction(KeyboardAction.GestureStarted)
            vm.onAction(KeyboardAction.InsertText("a"))
            vm.onAction(KeyboardAction.GestureEnded)
        }
        assertEquals(false, vm.state.value.proofreadAuto)
        assertEquals(true, vm.state.value.proofreadSuspendedByTaps)
    }

    @Test
    fun `gesture start and end produce no effect and no state change`() {
        val vm = viewModel()
        val before = vm.state.value
        assertNull(vm.onAction(KeyboardAction.GestureStarted))
        assertNull(vm.onAction(KeyboardAction.GestureEnded))
        assertEquals(before, vm.state.value)
    }

    @Test
    fun `proofreader status updates state`() {
        val vm = viewModel()
        assertEquals(com.example.betterswipekeyboard.proofread.ProofreaderStatus.UNAVAILABLE,
            vm.state.value.proofreader)
        vm.setProofreaderStatus(
            com.example.betterswipekeyboard.proofread.ProofreaderStatus.AVAILABLE,
            com.example.betterswipekeyboard.proofread.ProofreaderBackend.CLOUD,
        )
        assertEquals(com.example.betterswipekeyboard.proofread.ProofreaderStatus.AVAILABLE,
            vm.state.value.proofreader)
        assertEquals(com.example.betterswipekeyboard.proofread.ProofreaderBackend.CLOUD,
            vm.state.value.proofreaderBackend)
        vm.setProofreadInFlight(true)
        assertEquals(true, vm.state.value.proofreadInFlight)
    }

    @Test
    fun `paste clip commits verbatim under caps lock and returns to letters`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.CapsLock)
        vm.onAction(KeyboardAction.SwitchLayout(LayoutId.CLIPBOARD))

        val effect = vm.onAction(KeyboardAction.PasteClip("hello World"))
        assertEquals(KeyboardEffect.PasteText("hello World"), effect)
        assertEquals(LayoutId.LETTERS, vm.state.value.layout)
        assertEquals(ShiftMode.LOCKED, vm.state.value.shiftMode)
    }

    @Test
    fun `paste clip consumes one shot shift`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.Shift)
        assertEquals(KeyboardEffect.PasteText("clip"), vm.onAction(KeyboardAction.PasteClip("clip")))
        assertEquals(ShiftMode.OFF, vm.state.value.shiftMode)
    }

    @Test
    fun `add clip dedups through state`() {
        val vm = viewModel()
        vm.addClip("x")
        vm.addClip("y")
        vm.addClip("x")
        assertEquals(listOf("x", "y"), vm.state.value.clipboard.map { it.text })
    }

    @Test
    fun `delete clip removes entry without editor effect`() {
        val vm = viewModel()
        vm.addClip("a")
        vm.addClip("b")
        assertNull(vm.onAction(KeyboardAction.DeleteClip("a")))
        assertEquals(listOf("b"), vm.state.value.clipboard.map { it.text })
    }

    @Test
    fun `toggle voice produces no editor effect and no state change`() {
        val vm = viewModel()
        val before = vm.state.value
        assertNull(vm.onAction(KeyboardAction.ToggleVoice))
        assertEquals(before, vm.state.value)
    }

    @Test
    fun `voice state setters drive the voice panel state`() {
        val vm = viewModel()
        assertEquals(VoiceState.OFF, vm.state.value.voice)

        vm.setVoiceState(VoiceState.LISTENING)
        assertEquals(VoiceState.LISTENING, vm.state.value.voice)

        vm.setVoicePartial("hello wor")
        assertEquals("hello wor", vm.state.value.voicePartial)

        // Leaving LISTENING clears the partial transcript.
        vm.setVoiceState(VoiceState.OFF)
        assertEquals(VoiceState.OFF, vm.state.value.voice)
        assertEquals("", vm.state.value.voicePartial)
    }

    @Test
    fun `best transcript picks first non-blank candidate trimmed`() {
        assertNull(bestTranscript(null))
        assertNull(bestTranscript(emptyList()))
        assertNull(bestTranscript(listOf("", "   ")))
        assertEquals("hello world", bestTranscript(listOf("", "  hello world ", "ignored")))
    }

    @Test
    fun `move cursor passes through as move cursor effect`() {
        val vm = viewModel()
        assertEquals(KeyboardEffect.MoveCursor(-1), vm.onAction(KeyboardAction.MoveCursor(-1)))
        assertEquals(KeyboardEffect.MoveCursor(3), vm.onAction(KeyboardAction.MoveCursor(3)))
    }

    @Test
    fun `one shot shift survives a cursor move`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.Shift)
        assertEquals(KeyboardEffect.MoveCursor(-2), vm.onAction(KeyboardAction.MoveCursor(-2)))
        assertEquals(ShiftMode.ONE_SHOT, vm.state.value.shiftMode)
        assertEquals(KeyboardEffect.CommitText("A"), vm.onAction(KeyboardAction.InsertText("a")))
        assertEquals(ShiftMode.OFF, vm.state.value.shiftMode)
    }

    @Test
    fun `backspace right after a swipe deletes the whole word`() {
        val vm = viewModel()
        assertEquals(KeyboardEffect.CommitWord("hello"), vm.onAction(KeyboardAction.CommitWord("hello")))
        assertEquals(true, vm.state.value.lastCommitWasSwipe)
        assertEquals(KeyboardEffect.DeleteWordBackward, vm.onAction(KeyboardAction.Backspace))
        assertEquals(false, vm.state.value.lastCommitWasSwipe)
    }

    @Test
    fun `backspace after a tap deletes one character`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.InsertText("a"))
        assertEquals(KeyboardEffect.DeleteBackward, vm.onAction(KeyboardAction.Backspace))
    }

    @Test
    fun `held backspace deletes the word first then characters`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.CommitWord("hello"))
        assertEquals(KeyboardEffect.DeleteWordBackward, vm.onAction(KeyboardAction.Backspace))
        assertEquals(KeyboardEffect.DeleteBackward, vm.onAction(KeyboardAction.Backspace))
        assertEquals(KeyboardEffect.DeleteBackward, vm.onAction(KeyboardAction.Backspace))
    }

    @Test
    fun `another swipe re-arms the swipe flag`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.CommitWord("hello"))
        vm.onAction(KeyboardAction.Backspace) // consumes the flag
        vm.onAction(KeyboardAction.CommitWord("world"))
        assertEquals(KeyboardEffect.DeleteWordBackward, vm.onAction(KeyboardAction.Backspace))
    }

    @Test
    fun `gesture markers around a swipe do not clear the swipe flag`() {
        // A real swipe is GestureStarted, CommitWord, GestureEnded — and a
        // backspace tap is GestureStarted, Backspace, GestureEnded, so the
        // markers must not touch the flag or the feature never fires.
        val vm = viewModel()
        vm.onAction(KeyboardAction.GestureStarted)
        vm.onAction(KeyboardAction.CommitWord("hello"))
        vm.onAction(KeyboardAction.GestureEnded)
        vm.onAction(KeyboardAction.GestureStarted)
        assertEquals(KeyboardEffect.DeleteWordBackward, vm.onAction(KeyboardAction.Backspace))
        vm.onAction(KeyboardAction.GestureEnded)
    }

    @Test
    fun `any other input action clears the swipe flag`() {
        val clearingActions = listOf(
            KeyboardAction.InsertText("a"),
            KeyboardAction.Enter,
            KeyboardAction.MoveCursor(-1),
            KeyboardAction.SwitchLayout(LayoutId.SYMBOLS),
            KeyboardAction.Shift,
            KeyboardAction.PasteClip("clip"),
            KeyboardAction.ToggleProofread,
        )
        for (action in clearingActions) {
            val vm = viewModel()
            vm.onAction(KeyboardAction.CommitWord("hello"))
            vm.onAction(action)
            assertEquals(
                "after $action",
                KeyboardEffect.DeleteBackward,
                vm.onAction(KeyboardAction.Backspace),
            )
        }
    }

    @Test
    fun `commit word stores the alternates in state`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.CommitWord("hello", alternates = listOf("hell", "help")))
        assertEquals("hello", vm.state.value.swipedWord)
        assertEquals(listOf("hell", "help"), vm.state.value.swipeAlternates)
    }

    @Test
    fun `commit word stores the wider strip offers and swaps subtract from both lists`() {
        val vm = viewModel()
        vm.onAction(
            KeyboardAction.CommitWord(
                "hello",
                alternates = listOf("hell", "held"),
                stripOffers = listOf("hell", "help", "held"),
            ),
        )
        assertEquals(listOf("hell", "help", "held"), vm.state.value.swipeStripOffers)

        vm.onAction(KeyboardAction.SelectAlternate("hell"))
        assertEquals(listOf("held"), vm.state.value.swipeAlternates)
        assertEquals(listOf("help", "held"), vm.state.value.swipeStripOffers)
    }

    @Test
    fun `commit word with no surviving alternates still arms the center word`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.CommitWord("hello"))
        assertEquals("hello", vm.state.value.swipedWord)
        assertEquals(emptyList<String>(), vm.state.value.swipeAlternates)
    }

    @Test
    fun `alternates are stored caps-transformed like the committed word`() {
        val oneShot = viewModel()
        oneShot.onAction(KeyboardAction.Shift)
        oneShot.onAction(KeyboardAction.CommitWord("hello", alternates = listOf("hell", "help")))
        assertEquals("Hello", oneShot.state.value.swipedWord)
        assertEquals(listOf("Hell", "Help"), oneShot.state.value.swipeAlternates)

        val locked = viewModel()
        locked.onAction(KeyboardAction.CapsLock)
        locked.onAction(KeyboardAction.CommitWord("hello", alternates = listOf("hell")))
        assertEquals("HELLO", locked.state.value.swipedWord)
        assertEquals(listOf("HELL"), locked.state.value.swipeAlternates)
    }

    @Test
    fun `select alternate replaces the word and re-arms the swipe flag`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.CommitWord("hello", alternates = listOf("hell", "help")))

        val effect = vm.onAction(KeyboardAction.SelectAlternate("hell"))
        assertEquals(KeyboardEffect.ReplaceSwipedWord("hell"), effect)
        // Re-armed: the next backspace word-deletes the replacement, and the
        // strip keeps the other alternate for another swap. The tapped word
        // becomes the green center word; the replaced-away "hello" is gone.
        assertEquals(true, vm.state.value.lastCommitWasSwipe)
        assertEquals("hell", vm.state.value.swipedWord)
        assertEquals(listOf("help"), vm.state.value.swipeAlternates)
        assertEquals(KeyboardEffect.DeleteWordBackward, vm.onAction(KeyboardAction.Backspace))
    }

    @Test
    fun `selecting alternates repeatedly chains swaps`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.CommitWord("hello", alternates = listOf("hell", "help")))
        assertEquals(KeyboardEffect.ReplaceSwipedWord("hell"), vm.onAction(KeyboardAction.SelectAlternate("hell")))
        assertEquals("hell", vm.state.value.swipedWord)
        assertEquals(KeyboardEffect.ReplaceSwipedWord("help"), vm.onAction(KeyboardAction.SelectAlternate("help")))
        assertEquals("help", vm.state.value.swipedWord)
        assertEquals(emptyList<String>(), vm.state.value.swipeAlternates)
        assertEquals(true, vm.state.value.lastCommitWasSwipe)
    }

    @Test
    fun `select alternate without an armed swipe is ignored`() {
        val vm = viewModel()
        val before = vm.state.value
        assertNull(vm.onAction(KeyboardAction.SelectAlternate("hell")))
        assertEquals(before, vm.state.value)

        // Also ignored after the strip was cleared by other input.
        vm.onAction(KeyboardAction.CommitWord("hello", alternates = listOf("hell")))
        vm.onAction(KeyboardAction.InsertText("a"))
        assertNull(vm.onAction(KeyboardAction.SelectAlternate("hell")))
    }

    @Test
    fun `any other input action clears the alternates too`() {
        val clearingActions = listOf(
            KeyboardAction.InsertText("a"),
            KeyboardAction.Enter,
            KeyboardAction.MoveCursor(-1),
            KeyboardAction.SwitchLayout(LayoutId.SYMBOLS),
            KeyboardAction.Shift,
            KeyboardAction.CapsLock,
            KeyboardAction.PasteClip("clip"),
            KeyboardAction.ToggleProofread,
            KeyboardAction.Backspace, // after the word-delete fires, the strip is stale
        )
        for (action in clearingActions) {
            val vm = viewModel()
            vm.onAction(KeyboardAction.CommitWord("hello", alternates = listOf("hell"), stripOffers = listOf("hell")))
            vm.onAction(action)
            assertEquals("after $action", emptyList<String>(), vm.state.value.swipeAlternates)
            assertEquals("after $action", emptyList<String>(), vm.state.value.swipeStripOffers)
            assertNull("after $action", vm.state.value.swipedWord)
        }
    }

    @Test
    fun `gesture markers do not clear the alternates`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.GestureStarted)
        vm.onAction(KeyboardAction.CommitWord("hello", alternates = listOf("hell")))
        vm.onAction(KeyboardAction.GestureEnded)
        assertEquals("hello", vm.state.value.swipedWord)
        assertEquals(listOf("hell"), vm.state.value.swipeAlternates)
    }

    @Test
    fun `voice state transitions clear the alternates`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.CommitWord("hello", alternates = listOf("hell")))
        vm.setVoiceState(VoiceState.LISTENING)
        assertEquals(emptyList<String>(), vm.state.value.swipeAlternates)
        assertNull(vm.state.value.swipedWord)
    }

    @Test
    fun `clear swipe alternates empties the strip but keeps the swipe flag`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.CommitWord("hello", alternates = listOf("hell")))
        vm.clearSwipeAlternates()
        assertEquals(emptyList<String>(), vm.state.value.swipeAlternates)
        assertNull(vm.state.value.swipedWord)
        assertEquals(true, vm.state.value.lastCommitWasSwipe)
    }

    @Test
    fun `failed-swipe offer stores offers and letters, clears the strip pair, keeps the swipe flag`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.CommitWord("hello", alternates = listOf("hell")))

        val effect = vm.onAction(
            KeyboardAction.OfferFailedSwipe(listOf("keyboard", "keyword"), "k·e·y·b·o·a·r·d"),
        )
        assertNull(effect)
        assertEquals(
            FailedSwipe(listOf("keyboard", "keyword"), "k·e·y·b·o·a·r·d"),
            vm.state.value.failedSwipe,
        )
        // The pair is cleared AS A PAIR — a stale green center among the
        // offers would lie...
        assertNull(vm.state.value.swipedWord)
        assertEquals(emptyList<String>(), vm.state.value.swipeAlternates)
        // ...but the failed gesture committed nothing: the last COMMIT still
        // owns the word-delete.
        assertEquals(true, vm.state.value.lastCommitWasSwipe)
        assertEquals(KeyboardEffect.DeleteWordBackward, vm.onAction(KeyboardAction.Backspace))
    }

    @Test
    fun `failed-swipe offer does not consume one-shot shift`() {
        // The offer tap's CommitWord consumes the still-armed shift and
        // capitalizes the picked word — nothing was committed by the offer.
        val vm = viewModel()
        vm.onAction(KeyboardAction.Shift)
        vm.onAction(KeyboardAction.OfferFailedSwipe(listOf("keyboard"), "k·e·y"))
        assertEquals(ShiftMode.ONE_SHOT, vm.state.value.shiftMode)
    }

    @Test
    fun `gesture markers do not clear the failed-swipe offers`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.GestureStarted)
        vm.onAction(KeyboardAction.OfferFailedSwipe(listOf("keyboard"), "k·e·y"))
        vm.onAction(KeyboardAction.GestureEnded)
        assertEquals(FailedSwipe(listOf("keyboard"), "k·e·y"), vm.state.value.failedSwipe)
    }

    @Test
    fun `any other input action clears the failed-swipe offers`() {
        val clearingActions = listOf(
            KeyboardAction.InsertText("a"),
            KeyboardAction.Enter,
            KeyboardAction.MoveCursor(-1),
            KeyboardAction.SwitchLayout(LayoutId.SYMBOLS),
            KeyboardAction.Shift,
            KeyboardAction.CapsLock,
            KeyboardAction.PasteClip("clip"),
            KeyboardAction.ToggleProofread,
            KeyboardAction.Backspace, // armed by the commit: word-delete path
        )
        for (action in clearingActions) {
            val vm = viewModel()
            vm.onAction(KeyboardAction.CommitWord("hello"))
            vm.onAction(KeyboardAction.OfferFailedSwipe(listOf("keyboard"), "k·e·y"))
            vm.onAction(action)
            assertNull("after $action", vm.state.value.failedSwipe)
        }
    }

    @Test
    fun `voice transitions and field starts clear the failed-swipe offers`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.OfferFailedSwipe(listOf("keyboard"), "k·e·y"))
        vm.setVoiceState(VoiceState.LISTENING)
        assertNull(vm.state.value.failedSwipe)

        val fieldStart = viewModel()
        fieldStart.onAction(KeyboardAction.OfferFailedSwipe(listOf("keyboard"), "k·e·y"))
        fieldStart.clearSwipeAlternates()
        assertNull(fieldStart.state.value.failedSwipe)
    }

    @Test
    fun `offer-tap commit lands like a decoder commit and supersedes the offers`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.OfferFailedSwipe(listOf("keyboard", "keyword"), "k·e·y"))

        // The gesture loop builds exactly this CommitWord from the tapped
        // cell: picked word, the failed trail's letters, remaining offers.
        val effect = vm.onAction(KeyboardAction.CommitWord("keyboard", "k·e·y", listOf("keyword")))
        assertEquals(KeyboardEffect.CommitWord("keyboard", "k·e·y"), effect)
        assertEquals(true, vm.state.value.lastCommitWasSwipe)
        assertEquals("keyboard", vm.state.value.swipedWord)
        assertEquals(listOf("keyword"), vm.state.value.swipeAlternates)
        assertNull(vm.state.value.failedSwipe)
        // Armed like any swipe commit: the first backspace word-deletes it.
        assertEquals(KeyboardEffect.DeleteWordBackward, vm.onAction(KeyboardAction.Backspace))
    }

    @Test
    fun `offer-tap commit under one-shot shift capitalizes the picked word`() {
        val vm = viewModel()
        vm.onAction(KeyboardAction.Shift)
        vm.onAction(KeyboardAction.OfferFailedSwipe(listOf("keyboard", "keyword"), "k·e·y"))
        vm.onAction(KeyboardAction.CommitWord("keyboard", "k·e·y", listOf("keyword")))
        assertEquals("Keyboard", vm.state.value.swipedWord)
        assertEquals(listOf("Keyword"), vm.state.value.swipeAlternates)
        assertEquals(ShiftMode.OFF, vm.state.value.shiftMode)
    }

    @Test
    fun `set tap strip sets the blue live word`() {
        val vm = viewModel()
        vm.setTapStrip(live = "hel", committed = null)
        assertEquals("hel", vm.state.value.tapLiveWord)
        assertNull(vm.state.value.tappedWord)
    }

    @Test
    fun `set tap strip sets the green ended word`() {
        val vm = viewModel()
        vm.setTapStrip(live = null, committed = "hello")
        assertNull(vm.state.value.tapLiveWord)
        assertEquals("hello", vm.state.value.tappedWord)
    }

    @Test
    fun `set tap strip overwrites both fields and clears on both null`() {
        val vm = viewModel()
        vm.setTapStrip(live = null, committed = "hello")
        vm.setTapStrip(live = "wor", committed = null)
        assertEquals("wor", vm.state.value.tapLiveWord)
        assertNull(vm.state.value.tappedWord)
        vm.setTapStrip(live = null, committed = null)
        assertNull(vm.state.value.tapLiveWord)
        assertNull(vm.state.value.tappedWord)
    }

    @Test
    fun `insert text and backspace leave the tap strip to the service hook`() {
        // The reductions deliberately do NOT touch the tap fields: the
        // service's refreshTapStrip overwrites them from field truth after
        // every text effect, so clearing here would be redundant work.
        val vm = viewModel()
        vm.setTapStrip(live = "hel", committed = null)
        vm.onAction(KeyboardAction.InsertText("l"))
        assertEquals("hel", vm.state.value.tapLiveWord)
        vm.onAction(KeyboardAction.Backspace)
        assertEquals("hel", vm.state.value.tapLiveWord)
    }

    @Test
    fun `input actions that clear the swipe strip also clear the tap strip`() {
        val clearingActions = listOf(
            KeyboardAction.Enter,
            KeyboardAction.MoveCursor(-1),
            KeyboardAction.SwitchLayout(LayoutId.SYMBOLS),
            KeyboardAction.PasteClip("clip"),
        )
        for (action in clearingActions) {
            val vm = viewModel()
            vm.setTapStrip(live = "hel", committed = null)
            vm.onAction(action)
            assertNull("after $action", vm.state.value.tapLiveWord)
            assertNull("after $action", vm.state.value.tappedWord)
        }
    }

    @Test
    fun `swipe reductions take the strip over from the tap mirror`() {
        val committed = viewModel()
        committed.setTapStrip(live = "hel", committed = null)
        committed.onAction(KeyboardAction.CommitWord("hello"))
        assertNull(committed.state.value.tapLiveWord)
        assertNull(committed.state.value.tappedWord)

        val failed = viewModel()
        failed.setTapStrip(live = "hel", committed = null)
        failed.onAction(KeyboardAction.OfferFailedSwipe(listOf("keyboard"), "k·e·y"))
        assertNull(failed.state.value.tapLiveWord)
        assertNull(failed.state.value.tappedWord)

        val swapped = viewModel()
        swapped.onAction(KeyboardAction.CommitWord("hello", alternates = listOf("hell")))
        swapped.setTapStrip(live = "hel", committed = null)
        swapped.onAction(KeyboardAction.SelectAlternate("hell"))
        assertNull(swapped.state.value.tapLiveWord)
        assertNull(swapped.state.value.tappedWord)
    }

    @Test
    fun `voice transitions and field starts clear the tap strip`() {
        val vm = viewModel()
        vm.setTapStrip(live = null, committed = "hello")
        vm.setVoiceState(VoiceState.LISTENING)
        assertNull(vm.state.value.tapLiveWord)
        assertNull(vm.state.value.tappedWord)

        val fieldStart = viewModel()
        fieldStart.setTapStrip(live = "hel", committed = null)
        fieldStart.clearSwipeAlternates()
        assertNull(fieldStart.state.value.tapLiveWord)
        assertNull(fieldStart.state.value.tappedWord)
    }
}
