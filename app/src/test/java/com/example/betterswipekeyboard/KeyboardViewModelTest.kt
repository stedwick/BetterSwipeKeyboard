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
            vm.onAction(KeyboardAction.CommitWord("hello", alternates = listOf("hell")))
            vm.onAction(action)
            assertEquals("after $action", emptyList<String>(), vm.state.value.swipeAlternates)
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
}
