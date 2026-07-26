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
}
