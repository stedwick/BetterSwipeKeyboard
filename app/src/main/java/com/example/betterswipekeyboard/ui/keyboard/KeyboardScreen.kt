package com.example.betterswipekeyboard.ui.keyboard

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.rememberUpdatedState
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
import com.example.betterswipekeyboard.spacebarHitRect
import com.example.betterswipekeyboard.layout.Key
import com.example.betterswipekeyboard.layout.KeyOutput
import com.example.betterswipekeyboard.layout.LayoutId
import com.example.betterswipekeyboard.layout.QwertyLayout
import com.example.betterswipekeyboard.layout.SymbolsLayout
import com.example.betterswipekeyboard.proofread.ProofreaderStatus
import com.example.betterswipekeyboard.swipe.KeyboardGeometry
import com.example.betterswipekeyboard.swipe.MAX_COMMIT_SCORE
import com.example.betterswipekeyboard.swipe.ScoredWord
import com.example.betterswipekeyboard.swipe.SwipeDecoder
import com.example.betterswipekeyboard.swipe.TimedPoint
import com.example.betterswipekeyboard.swipe.Vec2
import com.example.betterswipekeyboard.swipe.firstLetterContactIndex
import kotlinx.coroutines.Job
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

// Failed-swipe feedback: when the decoder drops a swipe (best score >=
// MAX_COMMIT_SCORE) the trail flashes in this deliberate warning yellow —
// jQuery-highlight style — and fades out, so the gesture reads as "seen
// but not recognized" instead of "the keyboard ignored me". Theme-
// independent like ToggleOn/ToggleOff; iOS system yellow reads on both
// the light and dark keyboard backgrounds.
private val FailedSwipeFlash = Color(0xFFFFD60A)

private const val LONG_PRESS_TIMEOUT_MS = 400L
private const val BACKSPACE_REPEAT_MS = 50L
private const val TRAIL_LINGER_MS = 200L

/** Failed-swipe yellow flash fade-out duration (tunable starting point). */
private const val FAILED_TRAIL_FADE_MS = 400

// Small aesthetic gap between the bottom key row and the system IME strip.
// The measured inset (bottomClearance) already covers the strip itself;
// user feedback: 12dp left too much dead space, so keep this minimal but
// non-zero.
private val KeyboardBottomClearance = 4.dp

/** Height of the utility row above the letter rows (and of the gesture surface's top strip). */
private val UtilityRowHeight = 44.dp

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

// Space-bar cursor scrubbing is velocity-sensitive: the per-step travel
// lives in SpacebarCursor.kt (spacebarStepSize, SPACEBAR_STEP_SLOW_DP =
// today's 14.dp feel); the gesture loop passes the density ratio.


/**
 * How far the space bar's touch-acceptance area is shrunk from its top edge
 * (hit-testing only — the visual key and the stored geometry rects are
 * unchanged). A word-swipe that starts a few px above the space bar — thumb
 * overshoot aiming at the bottom letter row — must become a letter swipe,
 * not a space-bar cursor drag that swallows the gesture. Tune on-device.
 */
private val SpacebarTopHitInset = 6.dp

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
    // Failed-swipe flash: while true, the trail renders in FailedSwipeFlash
    // yellow with alpha scaled by trailFade, animated 1 -> 0 by fadeJob.
    var trailFailed by remember { mutableStateOf(false) }
    val trailFade = remember { Animatable(1f) }
    var fadeJob by remember { mutableStateOf<Job?>(null) }
    var popupChoices by remember { mutableStateOf<List<String>?>(null) }
    var popupIndex by remember { mutableStateOf(-1) }
    var popupBounds by remember { mutableStateOf<Rect?>(null) }
    var popupAnchor by remember { mutableStateOf<Rect?>(null) }
    // Gesture-mode utility row (letters/symbols layouts): key bounds for
    // hit-testing, and which key is held, for the pressed highlight.
    val utilityRects = remember { mutableMapOf<UtilityKeyId, Rect>() }
    var pressedUtility by remember { mutableStateOf<UtilityKeyId?>(null) }
    // The gesture loop reads the AI key's enabled state at tap time;
    // pointerInput captures would otherwise freeze it at composition time.
    val currentState by rememberUpdatedState(state)
    val scope = rememberCoroutineScope()
    val trailStrokeWidth = with(LocalDensity.current) { 10.dp.toPx() }
    // Canonical character-key width: one slot of a full 10-key row. Every
    // character key in every row uses it, so keys are the same pixel width
    // across the whole keyboard. Zero until the container is measured — the
    // first frame falls back to weights.
    val unitKeyWidth = with(LocalDensity.current) {
        unitKeyWidthPx(boxSize.width, 3.dp.toPx(), 4.dp.toPx()).toDp()
    }
    val pxPerDp = LocalDensity.current.density
    val spacebarTopInsetPx = with(LocalDensity.current) { SpacebarTopHitInset.toPx() }

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
            when {
                // Panels and the voice screen keep the utility row OUTSIDE
                // the gesture surface (plain clickable keys): panel scrolls
                // must not be read as swipe trails, and there is no letter
                // decoding there.
                state.voice != VoiceState.OFF -> {
                    UtilityRow(
                        state = state,
                        colors = colors,
                        onAction = onAction,
                        onSettingsClick = onSettingsClick,
                    )
                    // While dictating, the voice panel replaces the key rows,
                    // so there is no stale key geometry to hit-test against.
                    VoicePanel(
                        state = state,
                        colors = colors,
                        onToggleVoice = { onAction(KeyboardAction.ToggleVoice) },
                        onPermissionHelpClick = onPermissionHelpClick,
                    )
                }

                state.layout == LayoutId.EMOJI -> {
                    UtilityRow(
                        state = state,
                        colors = colors,
                        onAction = onAction,
                        onSettingsClick = onSettingsClick,
                    )
                    EmojiPanel(
                        colors = colors,
                        onAction = onAction,
                        suggestions = state.emojiSuggestions,
                    )
                }

                state.layout == LayoutId.CLIPBOARD -> {
                    UtilityRow(
                        state = state,
                        colors = colors,
                        onAction = onAction,
                        onSettingsClick = onSettingsClick,
                    )
                    ClipboardPanel(
                        colors = colors,
                        entries = state.clipboard,
                        onAction = onAction,
                    )
                }

                else -> {
                    val layout = when (state.layout) {
                        LayoutId.SYMBOLS -> SymbolsLayout
                        else -> QwertyLayout
                    }
                    geometry.activeLayout = layout.id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Utility row + key rows form ONE gesture surface
                            // (a swipe may start anywhere in the keyboard
                            // rectangle). Height = utility row + the 6.dp gap
                            // + the pinned content height, so the letter rows
                            // keep exactly KeyboardContentHeight and stay
                            // pixel-equal with the panels.
                            .height(UtilityRowHeight + 6.dp + KeyboardContentHeight)
                            .onGloballyPositioned {
                                boxOffsetInWindow = it.positionInWindow()
                                boxOffsetOnScreen = it.positionOnScreen()
                                boxSize = it.size.toSize()
                            }
                            // ALL pointer input for the utility row and the
                            // letter/symbol rows is handled here at the
                            // container level (taps, long-presses and swipe
                            // trails) so a finger can travel across keys in a
                            // single gesture. Keys are purely visual; taps are
                            // re-dispatched semantically in the gesture loop.
                            // The emoji/clipboard/voice surfaces deliberately
                            // live OUTSIDE this scope: panel scrolls must not
                            // be read as swipe trails.
                            .pointerInput(layout.id) {
                                val touchSlop = viewConfiguration.touchSlop
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    onAction(KeyboardAction.GestureStarted)
                                    // Utility-row hit: on the letters/symbols
                                    // layouts the row is inside this gesture
                                    // surface, so a down there is either a
                                    // utility tap (dispatched below) or the
                                    // start of a swipe trail.
                                    val downUtility = utilityRects.entries
                                        .firstOrNull { it.value.contains(down.position) }?.key
                                    // Space-bar overshoot slack: a down in
                                    // the bar's top slack strip counts as
                                    // "no key", so an overshoot word-swipe
                                    // starting there collects a trail (see
                                    // the DRAG branch) instead of being
                                    // eaten by the space-bar cursor drag.
                                    // Missing rect (can't happen for a
                                    // registered key) keeps the space bar.
                                    val hitKey = geometry.keyAt(down.position)
                                    val downKey = hitKey?.takeUnless {
                                        isSpaceBar(it) &&
                                            geometry.boundsOf(it)?.let { rect ->
                                                spacebarHitRect(rect, spacebarTopInsetPx)
                                                    .contains(down.position)
                                            } == false
                                    }
                                    pressedKey = downKey
                                    pressedUtility = downUtility
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
                                    // Index into trail of the first point on
                                    // a letter key; -1 while the drag has not
                                    // touched one. The trail (visual and
                                    // decode alike) starts there — the prefix
                                    // is approach, not word.
                                    var trailStart = -1
                                    when (outcome) {
                                        GestureOutcome.TAP -> when {
                                            // Utility-row tap, re-dispatched
                                            // semantically (settings opens the
                                            // app Activity — a service
                                            // callback, not a KeyboardAction).
                                            downUtility == UtilityKeyId.SETTINGS -> onSettingsClick()
                                            downUtility != null -> utilityTapAction(
                                                downUtility,
                                                currentState.proofreader ==
                                                    ProofreaderStatus.AVAILABLE,
                                            )?.let(onAction)
                                            else -> downKey?.let { onAction(it.tapAction()) }
                                        }

                                        GestureOutcome.DRAG ->
                                            if (layout.id == LayoutId.LETTERS &&
                                                !isSpaceBar(downKey)
                                            ) {
                                                // Phase 2 (swipe): collect the
                                                // trail until finger lifts.
                                                // On the letters layout a
                                                // swipe may start ANYWHERE in
                                                // the keyboard rectangle:
                                                // letter keys, dead space (key
                                                // gaps, the space bar's top
                                                // slack strip), modifier keys
                                                // (shift/numbers/mic/enter/
                                                // backspace) and the utility
                                                // row. Off-key starts used to
                                                // be just trail before the
                                                // first letter basin — but
                                                // that prefix poisons the
                                                // decoder's letter alignment,
                                                // so the trail only BEGINS at
                                                // the first point on a letter
                                                // key (firstLetterContact-
                                                // Index). Junk trails that do
                                                // reach a letter are filtered
                                                // by MAX_COMMIT_SCORE; a drag
                                                // that never touches a letter
                                                // is no swipe at all —
                                                // nothing drawn, nothing
                                                // decoded. Only the space bar
                                                // itself keeps cursor drag.
                                                // A new trail supersedes a
                                                // failed-swipe flash still
                                                // fading: cancel it before
                                                // it can clear the NEW
                                                // trail's points.
                                                fadeJob?.cancel()
                                                trailFailed = false
                                                trailPoints = emptyList()
                                                val letterRects = geometry.letterRects()
                                                trailStart = firstLetterContactIndex(
                                                    trail.map { it.position },
                                                    letterRects,
                                                )
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
                                                        if (trailStart < 0) {
                                                            trailStart =
                                                                firstLetterContactIndex(
                                                                    trail.map { it.position },
                                                                    letterRects,
                                                                )
                                                        }
                                                        trailPoints =
                                                            if (trailStart >= 0) {
                                                                trail.subList(
                                                                    trailStart, trail.size,
                                                                ).map { it.position.toOffset() }
                                                            } else {
                                                                emptyList()
                                                            }
                                                        pressedKey =
                                                            geometry.keyAt(change.position)
                                                        change.consume()
                                                    }
                                                    // Note: the release may arrive consumed
                                                    // (e.g. after move consumption), so check
                                                    // `pressed`, not `changedToUp()`.
                                                    if (!change.pressed) break
                                                }
                                                // Only a trail that reached a
                                                // letter key is a swipe attempt.
                                                swipeCompleted = trailStart >= 0
                                            } else if (isSpaceBar(downKey)) {
                                                // Space-bar drag: cursor
                                                // control, in either layout.
                                                trackSpacebarDrag(
                                                    down.id,
                                                    down.position,
                                                    down.uptimeMillis,
                                                    pxPerDp,
                                                    onAction,
                                                )
                                            } else {
                                                // Symbols layout: no letter
                                                // decoding — a drag from a
                                                // non-spacebar key (or the
                                                // utility row) is not a swipe;
                                                // swallow it.
                                                awaitUp(down.id)
                                            }

                                        null -> when (downKey?.output) {
                                            // Phase 2 (long-press): repeat / caps-lock until finger lifts.
                                            is KeyOutput.Backspace -> {
                                                onAction(KeyboardAction.Backspace)
                                                // Fixed-cadence repeat: fire at
                                                // BACKSPACE_REPEAT_MS boundaries,
                                                // not BACKSPACE_REPEAT_MS after
                                                // each step returns. Every step
                                                // makes synchronous Binder
                                                // round-trips into the target
                                                // app; adding that latency to
                                                // the clock both slowed the
                                                // repeat and made it stutter
                                                // with the app's response jitter.
                                                // A step that overruns its slot
                                                // fires again as soon as the IPC
                                                // returns (nextRepeatAt reset to
                                                // now — no catch-up bursts).
                                                var nextRepeatAt =
                                                    SystemClock.uptimeMillis() +
                                                        BACKSPACE_REPEAT_MS
                                                var up = false
                                                while (!up) {
                                                    // Await even when behind
                                                    // schedule (1 ms floor) so a
                                                    // finger-up that arrived
                                                    // during the IPC is seen
                                                    // before the next delete.
                                                    val waitMs =
                                                        (nextRepeatAt -
                                                            SystemClock.uptimeMillis())
                                                            .coerceAtLeast(1)
                                                    val event =
                                                        withTimeoutOrNull(waitMs) {
                                                            awaitPointerEvent()
                                                        }
                                                    if (event == null) {
                                                        // Timed out while still held: repeat.
                                                        onAction(KeyboardAction.Backspace)
                                                        nextRepeatAt += BACKSPACE_REPEAT_MS
                                                        val now = SystemClock.uptimeMillis()
                                                        if (nextRepeatAt < now) {
                                                            nextRepeatAt = now
                                                        }
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
                                    pressedUtility = null

                                    if (swipeCompleted) {
                                        // Decode the TRIMMED trail (from the
                                        // first letter-key point — the same
                                        // points the visual trail drew), never
                                        // the off-letter approach prefix.
                                        val decodedTrail = trail.subList(trailStart, trail.size).toList()
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
                                            trail = decodedTrail,
                                            keyCenters = keyCenters,
                                            keyWidth = keyWidth,
                                            topN = 5,
                                        )
                                        onSwipeDecoded(decodedTrail, keyCenters, keyWidth, results)
                                        val best = results.firstOrNull()
                                        if (best != null && best.score < MAX_COMMIT_SCORE) {
                                            onAction(KeyboardAction.CommitWord(best.word))
                                            // Let the trail linger briefly, then clear it.
                                            scope.launch {
                                                delay(TRAIL_LINGER_MS)
                                                trailPoints = emptyList()
                                            }
                                        } else {
                                            // Failed swipe: the trail flashes
                                            // yellow and fades out (jQuery-
                                            // highlight style) — feedback
                                            // that the gesture was seen but
                                            // rejected. Purely cosmetic: the
                                            // animation never blocks or
                                            // captures input, and the next
                                            // gesture can start mid-fade (the
                                            // DRAG branch cancels fadeJob).
                                            trailFailed = true
                                            fadeJob?.cancel()
                                            fadeJob = scope.launch {
                                                trailFade.snapTo(1f)
                                                trailFade.animateTo(
                                                    0f,
                                                    tween(
                                                        FAILED_TRAIL_FADE_MS,
                                                        easing = LinearEasing,
                                                    ),
                                                )
                                                trailPoints = emptyList()
                                                trailFailed = false
                                            }
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
                            // Gesture mode: purely visual keys; the gesture
                            // loop hit-tests their registered bounds and
                            // re-dispatches taps semantically.
                            UtilityRow(
                                state = state,
                                colors = colors,
                                onAction = onAction,
                                onSettingsClick = onSettingsClick,
                                pressedId = pressedUtility,
                                onKeyPositioned = { id, coordinates ->
                                    utilityRects[id] = Rect(
                                        coordinates.positionInWindow() -
                                            boxOffsetInWindow,
                                        coordinates.size.toSize(),
                                    )
                                },
                            )
                            Column(
                                // Pinned to the same height as the panels:
                                // the weighted rows below fill it exactly,
                                // so letters and panels never differ by a
                                // rounding pixel.
                                modifier = Modifier.height(KeyboardContentHeight),
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
                        }

                        // Swipe trail overlay: recent points bright, older points fading out.
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val points = trailPoints
                            if (points.size >= 2) {
                                // A failed swipe flashes yellow and fades;
                                // a live/successful trail keeps the theme
                                // color at full strength.
                                val baseColor = if (trailFailed) FailedSwipeFlash else colors.trail
                                val fade = if (trailFailed) trailFade.value else 1f
                                for (i in 1 until points.size) {
                                    drawLine(
                                        color = baseColor.copy(
                                            alpha = trailSegmentAlpha(i, points.size, fade),
                                        ),
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
    // Gesture mode (letters/symbols layouts, where the row lives inside the
    // gesture surface): keys are purely visual and register their bounds for
    // the gesture loop's hit-testing; pressedId drives the held highlight.
    // Null onKeyPositioned = clickable mode (panels/voice): each key handles
    // its own clicks, exactly as before.
    pressedId: UtilityKeyId? = null,
    onKeyPositioned: ((UtilityKeyId, LayoutCoordinates) -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        UtilityKey(
            id = UtilityKeyId.AI,
            onClick = { onAction(KeyboardAction.ToggleProofread) },
            enabled = state.proofreader == ProofreaderStatus.AVAILABLE,
            active = state.proofreadAuto,
            colors = colors,
            modifier = Modifier.weight(2f),
            pressedId = pressedId,
            onKeyPositioned = onKeyPositioned,
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
            id = UtilityKeyId.EMOJI,
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
            pressedId = pressedId,
            onKeyPositioned = onKeyPositioned,
        ) {
            UtilityKeyLabel("😀", colors)
        }
        UtilityKey(
            id = UtilityKeyId.CLIPBOARD,
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
            pressedId = pressedId,
            onKeyPositioned = onKeyPositioned,
        ) {
            UtilityKeyLabel("📋", colors)
        }
        UtilityKey(
            id = UtilityKeyId.SETTINGS,
            onClick = onSettingsClick,
            colors = colors,
            modifier = Modifier.weight(1f),
            pressedId = pressedId,
            onKeyPositioned = onKeyPositioned,
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
    id: UtilityKeyId,
    onClick: () -> Unit,
    colors: KeyboardColors,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    pressedId: UtilityKeyId? = null,
    onKeyPositioned: ((UtilityKeyId, LayoutCoordinates) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // Gesture mode (onKeyPositioned != null): no clickable — the container
    // gesture loop hit-tests the registered bounds and re-dispatches taps.
    // Clickable mode: unchanged behavior for the panel/voice surfaces.
    val gestureMode = onKeyPositioned != null
    Box(
        modifier = modifier
            .height(UtilityRowHeight)
            .then(
                if (onKeyPositioned != null) {
                    Modifier.onGloballyPositioned { onKeyPositioned(id, it) }
                } else {
                    Modifier
                },
            )
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (active || (gestureMode && pressedId == id)) {
                    colors.keyBackgroundActive
                } else {
                    colors.keyBackground
                },
            )
            .then(
                if (!gestureMode) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                },
            ),
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
