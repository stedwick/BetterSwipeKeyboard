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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.example.betterswipekeyboard.KeyboardAction
import com.example.betterswipekeyboard.KeyboardState
import com.example.betterswipekeyboard.R
import com.example.betterswipekeyboard.ShiftMode
import com.example.betterswipekeyboard.VoiceState
import com.example.betterswipekeyboard.isSpaceBar
import com.example.betterswipekeyboard.layout.Key
import com.example.betterswipekeyboard.layout.KeyOutput
import com.example.betterswipekeyboard.layout.LayoutId
import com.example.betterswipekeyboard.layout.QwertyLayout
import com.example.betterswipekeyboard.layout.SymbolsLayout
import com.example.betterswipekeyboard.proofread.ProofreaderStatus
import com.example.betterswipekeyboard.swipe.KeyboardGeometry
import com.example.betterswipekeyboard.swipe.ScoredWord
import com.example.betterswipekeyboard.swipe.SwipeDecoder
import com.example.betterswipekeyboard.swipe.TimedPoint
import com.example.betterswipekeyboard.swipe.Vec2
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Not private: EmojiPanel, ClipboardPanel and PunctuationPopup (same package)
// render with these colors.
data class KeyboardColors(
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

// Small aesthetic gap between the bottom key row and the system IME strip.
// The measured inset (bottomClearance) already covers the strip itself;
// user feedback: 12dp left too much dead space, so keep this minimal but
// non-zero.
private val KeyboardBottomClearance = 4.dp

/**
 * Content height of EVERY keyboard surface (letter rows, emoji panel,
 * clipboard panel, voice panel) — they must all be exactly this tall or
 * the IME window shifts on layout switches. Pinning matters because the
 * letter rows would otherwise sum 4 x 52.dp + 3 x 6.dp with each gap
 * rounded to whole px separately (16.5 -> 17px at density 2.75), landing
 * 1px off a pinned 226.dp panel. The letter rows are weighted to fill
 * exactly this height instead of fixing each row at 52.dp.
 * Not private: EmojiPanel and ClipboardPanel (same package) pin to it too.
 */
val KeyboardContentHeight = 226.dp

/** Horizontal travel on the space bar per cursor step (tune on-device). */
private val SpacebarCursorStep = 14.dp

/**
 * Best-guess commits above this score are too unsure. Below it we commit
 * even a weak match — a slightly-wrong word beats silence (and the AI
 * proofreader, when enabled, cleans it up a second later).
 */
private const val MAX_COMMIT_SCORE = 1.75f

@Composable
fun KeyboardScreen(
    state: KeyboardState,
    decoderProvider: () -> SwipeDecoder,
    onAction: (KeyboardAction) -> Unit,
    onSettingsClick: () -> Unit,
    onPermissionHelpClick: () -> Unit,
    bottomClearance: Dp,
    // Debug trail capture (SwipeTrailCapture): called with the trail, key
    // geometry and decoder results after every completed swipe. No-op by
    // default; the service wires it up for decoder tuning.
    onSwipeDecoded: (List<TimedPoint>, Map<Char, Vec2>, Float, List<ScoredWord>) -> Unit =
        { _, _, _, _ -> },
) {
    val colors = if (isSystemInDarkTheme()) DarkKeyboardColors else LightKeyboardColors
    val geometry = remember { KeyboardGeometry() }

    var boxOffsetInWindow by remember { mutableStateOf(Offset.Zero) }
    var boxOffsetOnScreen by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(Size.Zero) }
    var pressedKey by remember { mutableStateOf<Key?>(null) }
    var trailPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var popupChoices by remember { mutableStateOf<List<String>?>(null) }
    var popupIndex by remember { mutableStateOf(-1) }
    var popupBounds by remember { mutableStateOf<Rect?>(null) }
    var popupAnchor by remember { mutableStateOf<Rect?>(null) }
    val scope = rememberCoroutineScope()
    val trailStrokeWidth = with(LocalDensity.current) { 10.dp.toPx() }
    // Canonical character-key width: one slot of a full 10-key row. Every
    // character key in every row uses it, so keys are the same pixel width
    // across the whole keyboard. Zero until the container is measured — the
    // first frame falls back to weights.
    val unitKeyWidth = with(LocalDensity.current) {
        unitKeyWidthPx(boxSize.width, 3.dp.toPx(), 4.dp.toPx()).toDp()
    }
    val cursorStepPx = with(LocalDensity.current) { SpacebarCursorStep.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.keyboardBackground)
            // bottomClearance is measured by the service from the real window
            // insets and already covers the nav/IME strip itself; only the
            // small aesthetic gap is added on top.
            .padding(bottom = bottomClearance + KeyboardBottomClearance),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 3.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            UtilityRow(
                state = state,
                colors = colors,
                onAction = onAction,
                onSettingsClick = onSettingsClick,
            )
            when {
                // While dictating, the voice panel replaces the key rows, so
                // there is no stale key geometry to hit-test against.
                state.voice != VoiceState.OFF -> VoicePanel(
                    state = state,
                    colors = colors,
                    onToggleVoice = { onAction(KeyboardAction.ToggleVoice) },
                    onPermissionHelpClick = onPermissionHelpClick,
                )

                state.layout == LayoutId.EMOJI -> EmojiPanel(
                    colors = colors,
                    onAction = onAction,
                    suggestions = state.emojiSuggestions,
                )

                state.layout == LayoutId.CLIPBOARD -> ClipboardPanel(
                    colors = colors,
                    entries = state.clipboard,
                    onAction = onAction,
                )

                else -> {
                    val layout = when (state.layout) {
                        LayoutId.SYMBOLS -> SymbolsLayout
                        else -> QwertyLayout
                    }
                    geometry.activeLayout = layout.id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Pinned to the same height as the panels: the
                            // weighted rows below fill it exactly, so letters
                            // and panels never differ by a rounding pixel.
                            .height(KeyboardContentHeight)
                            .onGloballyPositioned {
                                boxOffsetInWindow = it.positionInWindow()
                                boxOffsetOnScreen = it.positionOnScreen()
                                boxSize = it.size.toSize()
                            }
                            // ALL pointer input for the letter/symbol rows is
                            // handled here at the container level (taps,
                            // long-presses and swipe trails) so a finger can
                            // travel across keys in a single gesture. Keys
                            // below are purely visual. The utility row and the
                            // emoji/clipboard panels deliberately live OUTSIDE
                            // this scope: utility taps must not start gestures,
                            // and panel scrolls must not be read as swipe trails.
                            .pointerInput(layout.id) {
                                val touchSlop = viewConfiguration.touchSlop
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    onAction(KeyboardAction.GestureStarted)
                                    val downKey = geometry.keyAt(down.position)
                                    pressedKey = downKey
                                    val trail = mutableListOf(
                                        TimedPoint(down.position.toVec2(), down.uptimeMillis),
                                    )

                                    // Phase 1: up = tap, travel beyond slop = swipe, timeout = long-press.
                                    val outcome = withTimeoutOrNull(LONG_PRESS_TIMEOUT_MS) {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes
                                                .firstOrNull { it.id == down.id } ?: continue
                                            if (change.changedToUp()) {
                                                return@withTimeoutOrNull GestureOutcome.TAP
                                            }
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
                                            if (layout.id == LayoutId.LETTERS &&
                                                downKey?.isLetter() == true
                                            ) {
                                                // Phase 2 (swipe): collect the trail until finger lifts.
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes
                                                        .firstOrNull { it.id == down.id }
                                                        ?: break // pointer vanished
                                                    if (change.positionChange() != Offset.Zero) {
                                                        trail += TimedPoint(
                                                            change.position.toVec2(),
                                                            change.uptimeMillis,
                                                        )
                                                        trailPoints =
                                                            trail.map { it.position.toOffset() }
                                                        pressedKey =
                                                            geometry.keyAt(change.position)
                                                        change.consume()
                                                    }
                                                    // Note: the release may arrive consumed
                                                    // (e.g. after move consumption), so check
                                                    // `pressed`, not `changedToUp()`.
                                                    if (!change.pressed) break
                                                }
                                                swipeCompleted = true
                                            } else if (isSpaceBar(downKey)) {
                                                // Space-bar drag: cursor
                                                // control, in either layout.
                                                trackSpacebarDrag(
                                                    down.id,
                                                    down.position,
                                                    cursorStepPx,
                                                    onAction,
                                                )
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
                                                        withTimeoutOrNull(BACKSPACE_REPEAT_MS) {
                                                            awaitPointerEvent()
                                                        }
                                                    if (event == null) {
                                                        // Timed out while still held: repeat.
                                                        onAction(KeyboardAction.Backspace)
                                                    } else {
                                                        val change = event.changes
                                                            .firstOrNull { it.id == down.id }
                                                        if (change != null && change.changedToUp()) {
                                                            up = true
                                                        }
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
                                                    popupAnchor =
                                                        downKey?.let { geometry.boundsOf(it) }
                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        val change =
                                                            event.changes
                                                                .firstOrNull { it.id == down.id }
                                                                ?: break
                                                        if (change.positionChange() != Offset.Zero) {
                                                            selection = popupIndexAt(
                                                                change.position, popupBounds,
                                                            )
                                                            popupIndex = selection
                                                        }
                                                        if (!change.pressed) break
                                                    }
                                                    popupChoices = null
                                                    popupIndex = -1
                                                    popupAnchor = null
                                                    onAction(
                                                        KeyboardAction.InsertText(
                                                            if (selection >= 0) {
                                                                PUNCTUATION_POPUP[selection]
                                                            } else {
                                                                "."
                                                            },
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
                                        // Read the decoder at gesture time: the
                                        // service may have rebuilt it with new
                                        // custom words since this composition
                                        // was created.
                                        val keyCenters = geometry.letterCenters()
                                        val keyWidth = geometry.keyWidth()
                                        // topN > 1 so the debug trail capture
                                        // records the runners-up for tuning;
                                        // only the top word is ever committed.
                                        val results = decoderProvider().decode(
                                            trail = trail.toList(),
                                            keyCenters = keyCenters,
                                            keyWidth = keyWidth,
                                            topN = 5,
                                        )
                                        onSwipeDecoded(trail.toList(), keyCenters, keyWidth, results)
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
                                    onAction(KeyboardAction.GestureEnded)
                                }
                            },
                    ) {
                        Column(
                            modifier = Modifier.fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            layout.rows.forEach { row ->
                                Row(
                                    // Rows split the pinned content height
                                    // equally (was: a fixed 52.dp each) so
                                    // the letter stack is EXACTLY
                                    // KeyboardContentHeight tall — 4 x 52.dp
                                    // + 3 rounded 6.dp gaps could drift 1px
                                    // off the pinned panels.
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    // Center rows that end up narrower than
                                    // the full 10-key row (e.g. the 9-key
                                    // home row) instead of stretching them —
                                    // modifier keys keep their weights and
                                    // take the remaining space.
                                    horizontalArrangement = Arrangement.spacedBy(
                                        4.dp,
                                        Alignment.CenterHorizontally,
                                    ),
                                ) {
                                    row.forEach { key ->
                                        KeyView(
                                            key = key,
                                            state = state,
                                            pressed = key == pressedKey,
                                            colors = colors,
                                            modifier = if (key.isUnitCharacterKey() && unitKeyWidth > 0.dp) {
                                                Modifier.width(unitKeyWidth)
                                            } else {
                                                Modifier.weight(key.weight)
                                            },
                                            onPositioned = { coordinates ->
                                                geometry.register(
                                                    layout.id,
                                                    key,
                                                    Rect(
                                                        coordinates.positionInWindow() -
                                                            boxOffsetInWindow,
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

                        // Punctuation popup for the period long-press: a compact
                        // 3x3 grid anchored just above the period key (bottom
                        // row = most common, closest to the thumb).
                        val choices = popupChoices
                        val anchor = popupAnchor
                        if (choices != null && anchor != null) {
                            val density = LocalDensity.current
                            PunctuationPopup(
                                choices = choices,
                                highlightIndex = popupIndex,
                                colors = colors,
                                topLeft = popupTopLeft(
                                    anchor = anchor,
                                    popupSize = with(density) {
                                        Size(PopupGridSize.toPx(), PopupGridSize.toPx())
                                    },
                                    containerSize = boxSize,
                                    gapPx = with(density) { PopupGapAboveKey.toPx() },
                                    marginPx = with(density) { PopupEdgeMargin.toPx() },
                                ),
                                onPositioned = {
                                    popupBounds = Rect(
                                        // The popup is a separate overlay
                                        // window, so compare on-screen
                                        // coordinates, not in-window ones.
                                        it.positionOnScreen() - boxOffsetOnScreen,
                                        it.size.toSize(),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class GestureOutcome { TAP, DRAG }

/** Utility row above the keys: sparkly AI proofreader, emoji, clipboard, settings. */
@Composable
private fun UtilityRow(
    state: KeyboardState,
    colors: KeyboardColors,
    onAction: (KeyboardAction) -> Unit,
    onSettingsClick: () -> Unit,
) {
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
            // The emoji key toggles: from letters/symbols into the emoji
            // panel, from the panel back to letters.
            onClick = {
                onAction(
                    KeyboardAction.SwitchLayout(
                        if (state.layout == LayoutId.EMOJI) LayoutId.LETTERS else LayoutId.EMOJI,
                    ),
                )
            },
            colors = colors,
            modifier = Modifier.weight(1f),
        ) {
            UtilityKeyLabel("😀", colors)
        }
        UtilityKey(
            // The clipboard key toggles: from letters/symbols into the
            // clipboard panel, from the panel back to letters.
            onClick = {
                onAction(
                    KeyboardAction.SwitchLayout(
                        if (state.layout == LayoutId.CLIPBOARD) LayoutId.LETTERS else LayoutId.CLIPBOARD,
                    ),
                )
            },
            colors = colors,
            modifier = Modifier.weight(1f),
        ) {
            UtilityKeyLabel("📋", colors)
        }
        UtilityKey(
            onClick = onSettingsClick,
            colors = colors,
            modifier = Modifier.weight(1f),
        ) {
            UtilityKeyLabel("⚙", colors)
        }
    }
}

/**
 * Replaces the key rows while [VoiceState] is not OFF: a mic icon, a status
 * line, the live partial transcript, and a single Done / close / help
 * button. Deliberately minimal — no waveform, no cancel button. Pins to the
 * shared [KeyboardContentHeight], so the IME window doesn't jump.
 */
@Composable
private fun VoicePanel(
    state: KeyboardState,
    colors: KeyboardColors,
    onToggleVoice: () -> Unit,
    onPermissionHelpClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(KeyboardContentHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mic),
            contentDescription = "Voice input",
            tint = if (state.voice == VoiceState.LISTENING) ToggleOn else colors.keyText,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = when (state.voice) {
                VoiceState.LISTENING -> "Listening…"
                VoiceState.PERMISSION_REQUIRED ->
                    "Microphone permission needed — grant it in the app"
                VoiceState.UNAVAILABLE -> "Voice typing not available on this device"
                VoiceState.OFF -> ""
            },
            color = colors.keyText,
            fontSize = 15.sp,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.voice == VoiceState.LISTENING && state.voicePartial.isNotEmpty()) {
            Text(
                text = state.voicePartial,
                color = colors.keyText.copy(alpha = 0.6f),
                fontSize = 15.sp,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Box(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.keyBackground)
                .clickable(
                    onClick = when (state.voice) {
                        VoiceState.PERMISSION_REQUIRED -> onPermissionHelpClick
                        else -> onToggleVoice
                    },
                )
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when (state.voice) {
                    VoiceState.LISTENING -> "Done"
                    VoiceState.PERMISSION_REQUIRED -> "Open app"
                    else -> "Close"
                },
                color = colors.keyText,
                fontSize = 15.sp,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

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

/**
 * Character keys (letters, digits, punctuation — any visible single- or
 * multi-character text output) get the fixed global width; the space bar
 * (blank text) and modifiers keep their weights.
 */
private fun Key.isUnitCharacterKey(): Boolean =
    (output as? KeyOutput.Text)?.text?.isNotBlank() == true

private fun Key.tapAction(): KeyboardAction = when (val out = output) {
    is KeyOutput.Text -> KeyboardAction.InsertText(out.text)
    KeyOutput.Backspace -> KeyboardAction.Backspace
    KeyOutput.Enter -> KeyboardAction.Enter
    KeyOutput.Shift -> KeyboardAction.Shift
    is KeyOutput.SwitchLayout -> KeyboardAction.SwitchLayout(out.layout)
    KeyOutput.Microphone -> KeyboardAction.ToggleVoice
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
            // Height comes from the weighted row (rows split the pinned
            // content height); see KeyboardContentHeight.
            .fillMaxHeight()
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
