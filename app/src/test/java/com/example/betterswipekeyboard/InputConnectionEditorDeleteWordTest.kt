package com.example.betterswipekeyboard

import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [InputConnectionEditor.deleteWordBackward] (first backspace
 * after a swipe) and its pure companion helper
 * [InputConnectionEditor.precedingWordLength].
 */
class InputConnectionEditorDeleteWordTest {

    /** Hand-written fake; only the methods deleteWordBackward() uses are real. */
    private class FakeInputConnection(
        var selectedText: CharSequence? = null,
        var textBefore: CharSequence = "",
    ) : InputConnection {
        var getSelectedTextCalls = 0
            private set
        var deletedBefore = 0
            private set
        var committed: String? = null
            private set

        override fun getSelectedText(flags: Int): CharSequence? {
            getSelectedTextCalls++
            return selectedText
        }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = textBefore

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            deletedBefore += beforeLength
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            committed = text?.toString()
            return true
        }

        private fun unsupported(): Nothing = throw UnsupportedOperationException()

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = unsupported()
        override fun getCursorCapsMode(reqModes: Int): Int = unsupported()
        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText =
            unsupported()

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int) =
            unsupported()

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int) = unsupported()
        override fun setComposingRegion(start: Int, end: Int) = unsupported()
        override fun finishComposingText() = unsupported()
        override fun commitCompletion(text: CompletionInfo?) = unsupported()
        override fun commitCorrection(correctionInfo: CorrectionInfo?) = unsupported()
        override fun setSelection(start: Int, end: Int) = unsupported()
        override fun performEditorAction(editorAction: Int) = unsupported()
        override fun performContextMenuAction(id: Int) = unsupported()
        override fun beginBatchEdit() = unsupported()
        override fun endBatchEdit() = unsupported()
        override fun sendKeyEvent(event: KeyEvent?) = unsupported()
        override fun clearMetaKeyStates(states: Int) = unsupported()
        override fun reportFullscreenMode(enabled: Boolean) = unsupported()
        override fun performPrivateCommand(action: String?, data: Bundle?) = unsupported()
        override fun requestCursorUpdates(cursorUpdateMode: Int) = unsupported()
        override fun getHandler(): Handler = unsupported()
        override fun closeConnection() = unsupported()
        override fun commitContent(
            inputContentInfo: android.view.inputmethod.InputContentInfo,
            flags: Int,
            opts: Bundle?,
        ) = unsupported()
    }

    private fun editorFor(connection: InputConnection?) =
        InputConnectionEditor { connection }

    @Test
    fun `precedingWordLength returns 0 for null or empty text`() {
        assertEquals(0, InputConnectionEditor.precedingWordLength(null))
        assertEquals(0, InputConnectionEditor.precedingWordLength(""))
    }

    @Test
    fun `precedingWordLength consumes the word and the auto-inserted leading space`() {
        // "hello" + swipe → "hello world"; deleting returns to "hello".
        assertEquals(6, InputConnectionEditor.precedingWordLength("hello world"))
    }

    @Test
    fun `precedingWordLength consumes multiple spaces but never a newline`() {
        assertEquals(7, InputConnectionEditor.precedingWordLength("hello  world"))
        // commitWord adds no space after whitespace, so the newline is
        // user-typed and must survive: only "world" goes.
        assertEquals(5, InputConnectionEditor.precedingWordLength("hello\nworld"))
    }

    @Test
    fun `precedingWordLength without a leading space deletes just the word`() {
        assertEquals(5, InputConnectionEditor.precedingWordLength("world"))
    }

    @Test
    fun `precedingWordLength never splits an emoji`() {
        // The emoji is a surrogate pair (2 UTF-16 units); deleting it whole
        // or not at all is safe, halving it would leave U+FFFD.
        assertEquals(2, InputConnectionEditor.precedingWordLength("ab😀"))
    }

    @Test
    fun `deleteWordBackward deletes the swiped word and its leading space`() {
        val ic = FakeInputConnection(textBefore = "hello world")
        editorFor(ic).deleteWordBackward()
        assertEquals(6, ic.deletedBefore)
        assertNull(ic.committed)
    }

    @Test
    fun `deleteWordBackward with a selection deletes the selection instead`() {
        val ic = FakeInputConnection(selectedText = "selected", textBefore = "hello world")
        editorFor(ic).deleteWordBackward()
        assertEquals("", ic.committed)
        assertEquals(0, ic.deletedBefore)
    }

    @Test
    fun `a held-backspace repeat continues with char deletes after the word`() {
        val ic = FakeInputConnection(textBefore = "hello world")
        val editor = editorFor(ic)
        editor.deleteWordBackward()
        ic.textBefore = "hello" // word gone; the repeat fires again
        editor.backspace()
        // The word delete joined the delete streak, so the char step did
        // not re-check the selection (one Binder round-trip saved).
        assertEquals(1, ic.getSelectedTextCalls)
        assertEquals(6 + 1, ic.deletedBefore)
    }

    @Test
    fun `null connection is a no-op`() {
        editorFor(null).deleteWordBackward() // must not throw
        assertNull(null)
    }
}
