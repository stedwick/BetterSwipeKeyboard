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
    fun `commit word is reserved funnel for swipe output`() {
        val vm = viewModel()
        assertEquals(KeyboardEffect.CommitText("hello"), vm.onAction(KeyboardAction.CommitWord("hello")))
    }
}
