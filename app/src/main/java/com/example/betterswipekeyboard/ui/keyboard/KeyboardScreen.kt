package com.example.betterswipekeyboard.ui.keyboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.example.betterswipekeyboard.KeyboardAction
import com.example.betterswipekeyboard.KeyboardState
import com.example.betterswipekeyboard.R
import com.example.betterswipekeyboard.ShiftMode
import com.example.betterswipekeyboard.layout.Key
import com.example.betterswipekeyboard.layout.KeyOutput
import com.example.betterswipekeyboard.layout.LayoutId
import com.example.betterswipekeyboard.layout.QwertyLayout
import com.example.betterswipekeyboard.layout.SymbolsLayout
import com.example.betterswipekeyboard.proofread.ProofreaderStatus
import com.example.betterswipekeyboard.swipe.KeyboardGeometry
import com.example.betterswipekeyboard.swipe.SwipeDecoder
import com.example.betterswipekeyboard.swipe.TimedPoint
import com.example.betterswipekeyboard.swipe.Vec2
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private data class KeyboardColors(
    val keyboardBackground: Color,
    val keyBackground: Color,
    val keyBackgroundActive: Color,
    val keyText: Color,
    val trail: Color,
)

private val DarkKeyboardColors = KeyboardColors(
    keyboardBackground = Color(0xFF1C1C1E),
    keyBackground = Color(0xFF3A3A3C),
    keyBackgroundActive = Color(0xFF6E6E73),
    keyText = Color(0xFFF2F2F7),
    trail = Color(0xFF64D2FF),
)

private val LightKeyboardColors = KeyboardColors(
    keyboardBackground = Color(0xFFD1D5DB),
    keyBackground = Color(0xFFFFFFFF),
    keyBackgroundActive = Color(0xFFAEB3BC),
    keyText = Color(0xFF1C1C1E),
    trail = Color(0xFF0A84FF),
)

private val ToggleOn = Color(0xFF30D158)
private val ToggleOff = Color(0xFFFF453A)

private const val LONG_PRESS_TIMEOUT_MS = 400L
private const val BACKSPACE_REPEAT_MS = 50L
private const val TRAIL_LINGER_MS = 200L
private val KeyboardBottomClearance = 12.dp

/** Choices in the long-press popup on the period key. */
private val PUNCTUATION_POPUP = listOf("!", "?", ",", ";", ":", "-", "\"", "'")

/** Hit-test a finger position against the punctuation popup (with slack). */
private fun popupIndexAt(position: Offset, bounds: Rect?): Int {
    val b = bounds ?: return -1
    if (position.y < b.top - 24f || position.y > b.bottom + 160f) return -1
    if (position.x < b.left || position.x > b.right) return -1
    val index = ((position.x - b.left) / (b.width / PUNCTUATION_POPUP.size)).toInt()
    return index.coerceIn(0, PUNCTUATION_POPUP.size - 1)
}

/**
 * Best-guess commits above this score are too unsure. Below it we commit
 * even a weak match — a slightly-wrong word beats silence (and the AI
 * proofreader, when enabled, cleans it up a second later).
 */
private const val MAX_COMMIT_SCORE = 1.75f

@Composable
fun KeyboardScreen(
    state: KeyboardState,
    decoder: SwipeDecoder,
    onAction: (KeyboardAction) -> Unit,
    onSettingsClick: () -> Unit,
) {
    val colors = if (isSystemInDarkTheme()) DarkKeyboardColors else LightKeyboardColors
    val layout = when (state.layout) {
        LayoutId.LETTERS -> QwertyLayout
        LayoutId.SYMBOLS -> SymbolsLayout
    }
    val geometry = remember { KeyboardGeometry() }
    geometry.activeLayout = layout.id

    var boxOffsetInWindow by remember { mutableStateOf(Offset.Zero) }
    var pressedKey by remember { mutableStateOf<Key?>(null) }
    var trailPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var popupChoices by remember { mutableStateOf<List<String>?>(null) }
    var popupIndex by remember { mutableStateOf(-1) }
    var popupBounds by remember { mutableStateOf<Rect?>(null) }
    val scope = rememberCoroutineScope()
    val trailStrokeWidth = with(LocalDensity.current) { 10.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.keyboardBackground)
            .windowInsetsPadding(WindowInsets.navigationBars)
            // Extra clearance so the system nav strip (gesture pill,
            // hide-keyboard and IME-switcher buttons) never overlaps keys.
            .padding(bottom = KeyboardBottomClearance)
            .onGloballyPositioned { boxOffsetInWindow = it.positionInWindow() }
            // ALL pointer input is handled here at the container level (taps,
            // long-presses and swipe trails) so a finger can travel across
            // keys in a single gesture. Keys below are purely visual.
            .pointerInput(layout.id) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val downKey = geometry.keyAt(down.position)
                    pressedKey = downKey
                    val trail = mutableListOf(
                        TimedPoint(down.position.toVec2(), down.uptimeMillis),
                    )

                    // Phase 1: up = tap, travel beyond slop = swipe, timeout = long-press.
                    val outcome = withTimeoutOrNull(LONG_PRESS_TIMEOUT_MS) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                            if (change.changedToUp()) return@withTimeoutOrNull GestureOutcome.TAP
                            if ((change.position - down.position).getDistance() > touchSlop) {
                                return@withTimeoutOrNull GestureOutcome.DRAG
                            }
                        }
                        @Suppress("UNREACHABLE_CODE")
                        error("unreachable")
                    }

                    var swipeCompleted = false
                    when (outcome) {
                        GestureOutcome.TAP ->
                            downKey?.let { onAction(it.tapAction()) }

                        GestureOutcome.DRAG ->
                            if (layout.id == LayoutId.LETTERS && downKey?.isLetter() == true) {
                                // Phase 2 (swipe): collect the trail until finger lifts.
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: break // pointer vanished
                                    if (change.positionChange() != Offset.Zero) {
                                        trail += TimedPoint(
                                            change.position.toVec2(), change.uptimeMillis,
                                        )
                                        trailPoints = trail.map { it.position.toOffset() }
                                        pressedKey = geometry.keyAt(change.position)
                                        change.consume()
                                    }
                                    // Note: the release may arrive consumed (e.g. after
                                    // move consumption), so check `pressed`, not
                                    // `changedToUp()`.
                                    if (!change.pressed) break
                                }
                                swipeCompleted = true
                            } else {
                                // Drag from a non-letter key is not a swipe; swallow it.
                                awaitUp(down.id)
                            }

                        null -> when (downKey?.output) {
                            // Phase 2 (long-press): repeat / caps-lock until finger lifts.
                            is KeyOutput.Backspace -> {
                                onAction(KeyboardAction.Backspace)
                                var up = false
                                while (!up) {
                                    val event =
                                        withTimeoutOrNull(BACKSPACE_REPEAT_MS) { awaitPointerEvent() }
                                    if (event == null) {
                                        // Timed out while still held: repeat.
                                        onAction(KeyboardAction.Backspace)
                                    } else {
                                        val change = event.changes
                                            .firstOrNull { it.id == down.id }
                                        if (change != null && change.changedToUp()) up = true
                                    }
                                }
                            }

                            is KeyOutput.Shift -> {
                                onAction(KeyboardAction.CapsLock)
                                awaitUp(down.id)
                            }

                            is KeyOutput.Text -> {
                                if ((downKey.output as KeyOutput.Text).text == ".") {
                                    // Long-press period: punctuation popup with drag-select.
                                    var selection = -1
                                    popupChoices = PUNCTUATION_POPUP
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change =
                                            event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (change.positionChange() != Offset.Zero) {
                                            selection = popupIndexAt(change.position, popupBounds)
                                            popupIndex = selection
                                        }
                                        if (!change.pressed) break
                                    }
                                    popupChoices = null
                                    popupIndex = -1
                                    onAction(
                                        KeyboardAction.InsertText(
                                            if (selection >= 0) PUNCTUATION_POPUP[selection] else ".",
                                        ),
                                    )
                                } else {
                                    awaitUp(down.id)
                                }
                            }

                            else -> awaitUp(down.id)
                        }
                    }
                    pressedKey = null

                    if (swipeCompleted) {
                        val results = decoder.decode(
                            trail = trail.toList(),
                            keyCenters = geometry.letterCenters(),
                            keyWidth = geometry.keyWidth(),
                            topN = 1,
                        )
                        val best = results.firstOrNull()
                        if (best != null && best.score < MAX_COMMIT_SCORE) {
                            onAction(KeyboardAction.CommitWord(best.word))
                        }
                        // Let the trail linger briefly, then clear it.
                        scope.launch {
                            delay(TRAIL_LINGER_MS)
                            trailPoints = emptyList()
                        }
                    }
                }
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 3.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Utility row: sparkly AI proofreader, emoji, microphone.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                UtilityKey(
                    onClick = { onAction(KeyboardAction.ToggleProofread) },
                    enabled = state.proofreader == ProofreaderStatus.AVAILABLE,
                    active = state.proofreadAuto,
                    colors = colors,
                    modifier = Modifier.weight(2f),
                ) {
                    if (state.proofreadInFlight) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.keyText,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UtilityKeyLabel("✨ AI ", colors)
                            UtilityKeyLabel(
                                text = if (state.proofreadAuto) "on" else "off",
                                colors = colors,
                                color = if (state.proofreadAuto) ToggleOn else ToggleOff,
                            )
                        }
                    }
                }
                UtilityKey(
                    onClick = { /* TODO: emoji panel */ },
                    colors = colors,
                    modifier = Modifier.weight(1f),
                ) {
                    UtilityKeyLabel("😀", colors)
                }
                UtilityKey(
                    onClick = onSettingsClick,
                    colors = colors,
                    modifier = Modifier.weight(1f),
                ) {
                    UtilityKeyLabel("⚙", colors)
                }
            }
            layout.rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    row.forEach { key ->
                        KeyView(
                            key = key,
                            state = state,
                            pressed = key == pressedKey,
                            colors = colors,
                            modifier = Modifier.weight(key.weight),
                            onPositioned = { coordinates ->
                                geometry.register(
                                    layout.id,
                                    key,
                                    Rect(
                                        coordinates.positionInWindow() - boxOffsetInWindow,
                                        coordinates.size.toSize(),
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }

        // Swipe trail overlay: recent points bright, older points fading out.
        Canvas(modifier = Modifier.matchParentSize()) {
            val points = trailPoints
            if (points.size >= 2) {
                for (i in 1 until points.size) {
                    val alpha = 0.15f + 0.75f * (i.toFloat() / points.size)
                    drawLine(
                        color = colors.trail.copy(alpha = alpha),
                        start = points[i - 1],
                        end = points[i],
                        strokeWidth = trailStrokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }

        // Punctuation popup for the period long-press: a compact floating
        // panel of key tiles, clearly separated from the keys behind it.
        val choices = popupChoices
        if (choices != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 76.dp)
                    .shadow(8.dp, RoundedCornerShape(10.dp))
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.keyboardBackground)
                    .padding(4.dp)
                    .onGloballyPositioned {
                        popupBounds = Rect(
                            it.positionInWindow() - boxOffsetInWindow,
                            it.size.toSize(),
                        )
                    },
            ) {
                choices.forEachIndexed { index, label ->
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (index == popupIndex) {
                                    colors.keyBackgroundActive
                                } else {
                                    colors.keyBackground
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = label, color = colors.keyText, fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

private enum class GestureOutcome { TAP, DRAG }

/** A key in the utility row above the letter rows. */
@Composable
private fun UtilityKey(
    onClick: () -> Unit,
    colors: KeyboardColors,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) colors.keyBackgroundActive else colors.keyBackground)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun UtilityKeyLabel(
    text: String,
    colors: KeyboardColors,
    color: Color = colors.keyText,
) {
    Text(
        text = text,
        color = color,
        fontSize = 15.sp,
        style = MaterialTheme.typography.bodyMedium,
    )
}

private suspend fun AwaitPointerEventScope.awaitUp(id: PointerId) {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: continue
        if (change.changedToUp()) return
    }
}

private fun Offset.toVec2() = Vec2(x, y)

private fun Vec2.toOffset() = Offset(x, y)

private fun Key.isLetter(): Boolean =
    (output as? KeyOutput.Text)?.text?.singleOrNull()?.isLetter() == true

private fun Key.tapAction(): KeyboardAction = when (val out = output) {
    is KeyOutput.Text -> KeyboardAction.InsertText(out.text)
    KeyOutput.Backspace -> KeyboardAction.Backspace
    KeyOutput.Enter -> KeyboardAction.Enter
    KeyOutput.Shift -> KeyboardAction.Shift
    is KeyOutput.SwitchLayout -> KeyboardAction.SwitchLayout(out.layout)
    KeyOutput.Microphone -> KeyboardAction.Noop
}

@Composable
private fun KeyView(
    key: Key,
    state: KeyboardState,
    pressed: Boolean,
    colors: KeyboardColors,
    onPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isShiftActive = key.output is KeyOutput.Shift && state.shiftMode != ShiftMode.OFF
    val background = when {
        pressed || isShiftActive -> colors.keyBackgroundActive
        else -> colors.keyBackground
    }

    Box(
        modifier = modifier
            .onGloballyPositioned(onPositioned)
            .height(52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        if (key.output is KeyOutput.Microphone) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = "Voice input",
                tint = colors.keyText,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = key.displayLabel(state),
                color = colors.keyText,
                fontSize = if (key.output is KeyOutput.Text) 20.sp else 15.sp,
                fontWeight = if (isShiftActive) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        // Long-press hint in the corner of the period key.
        if ((key.output as? KeyOutput.Text)?.text == ".") {
            Text(
                text = "!",
                color = colors.keyText.copy(alpha = 0.4f),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 6.dp),
            )
        }
    }
}

private fun Key.displayLabel(state: KeyboardState): String {
    val out = output
    return if (out is KeyOutput.Text && state.isCaps) label.uppercase() else label
}
