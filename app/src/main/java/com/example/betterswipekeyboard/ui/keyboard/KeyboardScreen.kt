package com.example.betterswipekeyboard.ui.keyboard

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.betterswipekeyboard.KeyboardAction
import com.example.betterswipekeyboard.KeyboardState
import com.example.betterswipekeyboard.ShiftMode
import com.example.betterswipekeyboard.layout.Key
import com.example.betterswipekeyboard.layout.KeyOutput
import com.example.betterswipekeyboard.layout.KeyboardLayout
import com.example.betterswipekeyboard.layout.LayoutId
import com.example.betterswipekeyboard.layout.QwertyLayout
import com.example.betterswipekeyboard.layout.SymbolsLayout
import kotlinx.coroutines.delay

private val KeyboardBackground = Color(0xFF1C1C1E)
private val KeyBackground = Color(0xFF3A3A3C)
private val KeyBackgroundActive = Color(0xFF6E6E73)
private val KeyText = Color(0xFFF2F2F7)

@Composable
fun KeyboardScreen(
    state: KeyboardState,
    onAction: (KeyboardAction) -> Unit,
) {
    val layout = when (state.layout) {
        LayoutId.LETTERS -> QwertyLayout
        LayoutId.SYMBOLS -> SymbolsLayout
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(KeyboardBackground)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 3.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        layout.rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.forEach { key ->
                    KeyView(
                        key = key,
                        state = state,
                        onAction = onAction,
                        modifier = Modifier.weight(key.weight),
                    )
                }
            }
        }
    }
}

/**
 * Renders a single key and owns ALL of its pointer handling. That isolation is
 * deliberate: a future swipe/glide detector hooks in here (observing a trail
 * across keys and emitting [KeyboardAction.CommitWord]) without touching the
 * rest of the pipeline.
 */
@Composable
private fun KeyView(
    key: Key,
    state: KeyboardState,
    onAction: (KeyboardAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val isBackspace = key.output is KeyOutput.Backspace
    // Set when the long-press repeat loop fired, so the tap that follows the
    // release does not delete one extra character.
    var lastRepeatAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isPressed) {
        if (isPressed && isBackspace) {
            delay(400)
            while (true) {
                onAction(KeyboardAction.Backspace)
                lastRepeatAt = SystemClock.uptimeMillis()
                delay(50)
            }
        }
    }

    val isShiftActive = key.output is KeyOutput.Shift && state.shiftMode != ShiftMode.OFF
    val background = when {
        isShiftActive -> KeyBackgroundActive
        else -> KeyBackground
    }

    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isPressed) KeyBackgroundActive else background)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    // Suppress the release-tap after a long-press repeat run.
                    if (isBackspace &&
                        SystemClock.uptimeMillis() - lastRepeatAt < 150
                    ) {
                        return@combinedClickable
                    }
                    onAction(key.tapAction(state))
                },
                onLongClick = {
                    when (key.output) {
                        is KeyOutput.Shift -> onAction(KeyboardAction.CapsLock)
                        else -> Unit
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key.displayLabel(state),
            color = KeyText,
            fontSize = if (key.output is KeyOutput.Text) 20.sp else 15.sp,
            fontWeight = if (isShiftActive) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun Key.tapAction(state: KeyboardState): KeyboardAction = when (val out = output) {
    is KeyOutput.Text -> KeyboardAction.InsertText(out.text)
    KeyOutput.Backspace -> KeyboardAction.Backspace
    KeyOutput.Enter -> KeyboardAction.Enter
    KeyOutput.Shift -> KeyboardAction.Shift
    is KeyOutput.SwitchLayout -> KeyboardAction.SwitchLayout(out.layout)
}

private fun Key.displayLabel(state: KeyboardState): String {
    val out = output
    return if (out is KeyOutput.Text && state.isCaps) label.uppercase() else label
}
