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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.example.betterswipekeyboard.layout.NumericLayout
import com.example.betterswipekeyboard.layout.QwertyLayout
import com.example.betterswipekeyboard.layout.SymbolsLayout
import com.example.betterswipekeyboard.proofread.ProofreaderStatus
import com.example.betterswipekeyboard.swipe.KeyboardGeometry
import com.example.betterswipekeyboard.swipe.MAX_COMMIT_SCORE
import com.example.betterswipekeyboard.swipe.ScoredWord
import com.example.betterswipekeyboard.swipe.SwipeConfidence
import com.example.betterswipekeyboard.swipe.SwipeDecoder
import com.example.betterswipekeyboard.swipe.TimedPoint
import com.example.betterswipekeyboard.swipe.Vec2
import com.example.betterswipekeyboard.swipe.crossedLetters
import com.example.betterswipekeyboard.swipe.distinctLetterKeysCrossed
import com.example.betterswipekeyboard.swipe.failedSwipeOffers
import com.example.betterswipekeyboard.swipe.firstLetterContactIndex
import com.example.betterswipekeyboard.swipe.shouldRunLiveDecode
import com.example.betterswipekeyboard.swipe.swipeAlternates
import com.example.betterswipekeyboard.swipe.swipeConfidence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

// Two-tier swipe feedback (jQuery-highlight style, see swipeConfidence):
// a FAILED swipe (nothing committed) flashes the trail RED and fades out,
// so the gesture reads as "seen but not recognized" instead of "the
// keyboard ignored me"; a COMMIT with a close runner-up flashes YELLOW —
// "committed, but you may want to re-swipe" — while confident commits
// flash nothing. Theme-independent like ToggleOn/ToggleOff; iOS system
// red/yellow read on both the light and dark keyboard backgrounds.
private val FailedSwipeFlash = Color(0xFFFF453A)
private val LowConfidenceFlash = Color(0xFFFFD60A)

private const val LONG_PRESS_TIMEOUT_MS = 400L
private const val BACKSPACE_REPEAT_MS = 50L
private const val TRAIL_LINGER_MS = 200L

/**
 * A letters-layout drag is not a swipe until its trail has crossed this many
 * DISTINCT letter keys (distinctLetterKeysCrossed). A tap whose finger drifts
 * past the touch slop jitters inside ONE key; gating on two keeps that
 * drift-tap out of the decoder (a full candidate-set score on the main
 * thread) and the gesture loop falls back to typing the down key instead —
 * a drift-tap is a wobbly tap, not a failed swipe. No word is lost in
 * principle: the dictionary has no one-letter words, so every decodable word
 * visits at least two letter keys.
 */
private const val MIN_SWIPE_LETTERS = 2

/** Swipe-feedback flash fade-out duration (tunable starting point). */
private const val TRAIL_FLASH_FADE_MS = 400

// Small aesthetic gap between the bottom key row and the system IME strip.
// The measured inset (bottomClearance) already covers the strip itself;
// user feedback: 12dp left too much dead space, so keep this minimal but
// non-zero.
private val KeyboardBottomClearance = 4.dp

/** Height of the utility row above the letter rows (and of the gesture surface's top strip). */
private val UtilityRowHeight = 44.dp

/**
 * Content height below the alternates strip on EVERY keyboard surface
 * (letter rows, emoji panel, clipboard panel, voice panel) — they must all
 * be exactly this tall or the IME window shifts on layout switches. Pinning
 * matters because the letter rows would otherwise sum 4 x 52.dp + 3 x 6.dp
 * with each gap rounded to whole px separately (16.5 -> 17px at density
 * 2.75), landing 1px off a pinned 226.dp panel. The letter rows are
 * weighted to fill exactly this height instead of fixing each row at 52.dp.
 * Every surface is UtilityRowHeight + gap + AlternatesStripHeight + gap +
 * KeyboardContentHeight tall (= 322.dp); the strip is always visible, so
 * the total is static and the window never resizes at runtime.
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
    // Swipe-feedback flash: while non-null, the trail renders in this color
    // (red = failed, yellow = low-confidence commit) with alpha scaled by
    // trailFade, animated 1 -> 0 by fadeJob.
    var trailFlashColor by remember { mutableStateOf<Color?>(null) }
    val trailFade = remember { Animatable(1f) }
    var fadeJob by remember { mutableStateOf<Job?>(null) }
    // Live swipe suggestions: the latest mid-swipe decode's offers (the leader
    // marked when top-1 would commit on finger-up) shown in the alternates
    // strip's middle tier while the gesture runs. Transient UI state like
    // trailPoints — it never enters KeyboardState; a canceled swipe's offers
    // persist via OfferFailedSwipe at gesture end (see the decode block).
    // liveGen invalidates in-flight decode results when a newer decode starts
    // or the gesture ends; the decode runs on Dispatchers.Default from the
    // same scope as the fade jobs.
    var liveOffers by remember { mutableStateOf<LiveOffers?>(null) }
    var liveDecodeJob by remember { mutableStateOf<Job?>(null) }
    var liveGen by remember { mutableStateOf(0) }
    var liveLastDecodeStartMs by remember { mutableStateOf(0L) }
    var livePointsAtLastDecode by remember { mutableStateOf(0) }
    var popupChoices by remember { mutableStateOf<List<String>?>(null) }
    var popupIndex by remember { mutableStateOf(-1) }
    var popupBounds by remember { mutableStateOf<Rect?>(null) }
    var popupAnchor by remember { mutableStateOf<Rect?>(null) }
    // Gesture-mode utility row (letters/symbols layouts): key bounds for
    // hit-testing, and which key is held, for the pressed highlight.
    val utilityRects = remember { mutableMapOf<UtilityKeyId, Rect>() }
    var pressedUtility by remember { mutableStateOf<UtilityKeyId?>(null) }
    // Alternates strip (also inside the gesture surface): cell bounds for
    // hit-testing, and which cell is held, for the pressed highlight.
    val altRects = remember { mutableMapOf<Int, Rect>() }
    var pressedAlt by remember { mutableStateOf<Int?>(null) }
    // The gesture loop reads the AI key's enabled state at tap time;
    // pointerInput captures would otherwise freeze it at composition time.
    val currentState by rememberUpdatedState(state)
    // Alternates-strip cells (committed word centered, runners-up flanking):
    // computed once here so the strip rendering and the gesture loop's
    // hit-testing see the SAME list. The alt count tracks the keyboard
    // width (the IME spans the screen): 2 alts on phones, 4 on wide screens.
    // The gesture loop reads them via rememberUpdatedState for the same
    // pointerInput-capture reason as currentState.
    // A FAILED swipe's near-miss offers take over the strip (top-1 in the
    // center slot, PLAIN — nothing was committed, green or blue would lie);
    // the OfferFailedSwipe reduction already cleared the pair, so the Elvis
    // is belt-and-braces. maxAlternates is hoisted so the decode branch caps
    // offers from the same width-adaptive count.
    // Middle tier: a RUNNING swipe's live decode offers (top-1 in the center
    // slot, light blue via isLiveLeader exactly when it would commit on
    // finger-up — never green). They lose to a persisted failed swipe and to
    // a commit's strip, and they clear at gesture end, so they show only
    // mid-gesture. All three tiers share one placement rule (centeredCells
    // in StripCells.kt), so lifting the finger only recolors the center,
    // never rearranges the row.
    // Bottom tiers: the TAP-typing mirror (service-owned state, refreshed
    // from field truth after every tap/backspace) — the word mid-tap in
    // blue, then the just-ended word in green. The blue tier borrows the
    // LiveOffers leader flag PURELY for its LiveLeaderBlue rendering: in the
    // tap flow nothing commits on finger-up, so leaderWouldCommit = true is
    // a color choice, not a promise. Both cells are display-only: the green
    // center is untappable by construction, and the blue center's
    // SelectAlternate dispatch dies on the reduction's lastCommitWasSwipe
    // guard. The swipe tiers win above belt-and-braces — the reducer already
    // clears the tap fields on every swipe reduction.
    val maxAlternates = alternateCountForWidth(LocalConfiguration.current.screenWidthDp.toFloat())
    val altCells = state.failedSwipe?.let { failedOfferCells(it.offers, maxAlternates) }
        ?: liveOffers?.let { liveOfferCells(it, maxAlternates) }
        // takeIf is load-bearing: stripCells returns an EMPTY list (not
        // null) when no swipe is armed — without it the Elvis chain would
        // stop here and the tap tiers below would be unreachable.
        ?: stripCells(state.swipedWord, state.swipeStripOffers, state.swipeAlternates, maxAlternates).takeIf { it.isNotEmpty() }
        ?: state.tapLiveWord?.let {
            liveOfferCells(LiveOffers(listOf(it), leaderWouldCommit = true), maxAlternates)
        }
        ?: state.tappedWord?.let { stripCells(it, emptyList(), emptyList(), maxAlternates) }
        ?: emptyList()
    val currentAltCells by rememberUpdatedState(altCells)
    val currentMaxAlternates by rememberUpdatedState(maxAlternates)
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
                    // The strip renders on every surface (blank here: voice
                    // transitions clear the alts) so the window height stays
                    // pixel-equal with the key layouts. Inert outside the
                    // gesture surface.
                    SwipeAlternatesStrip(cells = altCells, colors = colors)
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
                    // Blank strip (SwitchLayout cleared the alts); keeps the
                    // surface height pixel-equal with the key layouts.
                    SwipeAlternatesStrip(cells = altCells, colors = colors)
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
                    // Blank strip (SwitchLayout cleared the alts); keeps the
                    // surface height pixel-equal with the key layouts.
                    SwipeAlternatesStrip(cells = altCells, colors = colors)
                    ClipboardPanel(
                        colors = colors,
                        entries = state.clipboard,
                        onAction = onAction,
                    )
                }

                else -> {
                    val layout = when (state.layout) {
                        LayoutId.SYMBOLS -> SymbolsLayout
                        LayoutId.NUMERIC -> NumericLayout
                        else -> QwertyLayout
                    }
                    geometry.activeLayout = layout.id
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Utility row + alternates strip + key rows form
                            // ONE gesture surface (a swipe may start anywhere
                            // in the keyboard rectangle). Height = utility row
                            // + strip + gaps + the pinned content height, so
                            // the letter rows keep exactly KeyboardContentHeight
                            // and stay pixel-equal with the panels (which render
                            // the same strip above their pinned content).
                            .height(
                                UtilityRowHeight + 6.dp + AlternatesStripHeight +
                                    6.dp + KeyboardContentHeight,
                            )
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
                                    // Alternates-strip hit: same inside-the-
                                    // gesture-surface re-dispatch pattern as
                                    // the utility row. Rects can briefly
                                    // outlive a shrinking list (recomposition
                                    // hasn't re-reported yet), so accept only
                                    // cells that still exist.
                                    val downAlt = altRects.entries
                                        .firstOrNull { it.value.contains(down.position) }
                                        ?.key
                                        ?.takeIf { it < currentAltCells.size }
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
                                    pressedAlt = downAlt
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
                                    // Distinct letter keys the trail has
                                    // crossed so far; the swipe gate
                                    // (MIN_SWIPE_LETTERS) that keeps
                                    // drift-taps out of the decoder.
                                    var lettersCrossed = 0
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
                                                currentState.layout,
                                            )?.let(onAction)
                                            // Alternates-strip tap. A FAILED
                                            // swipe's offer commits as a word:
                                            // the normal CommitWord path gives
                                            // leading-space rules, word-delete
                                            // arming and the green-center strip
                                            // (remaining offers as alternates),
                                            // and the trail's crossed letters
                                            // ride along as proofreader
                                            // evidence. A committed strip's
                                            // off-center cell replaces the word;
                                            // its green center (the committed
                                            // word itself) is a no-op.
                                            downAlt != null -> {
                                                val failed = currentState.failedSwipe
                                                val cell = currentAltCells.getOrNull(downAlt)
                                                when {
                                                    failed != null && cell != null ->
                                                        onAction(
                                                            KeyboardAction.CommitWord(
                                                                cell.word,
                                                                failed.letters,
                                                                failed.offers - cell.word,
                                                                // All offers are
                                                                // inside the
                                                                // near-miss band:
                                                                // the wide strip
                                                                // list IS the
                                                                // remaining
                                                                // offers, so the
                                                                // committed strip
                                                                // keeps the
                                                                // failed strip's
                                                                // exact layout.
                                                                stripOffers =
                                                                    failed.offers - cell.word,
                                                            ),
                                                        )
                                                    // Invisible band-mismatch
                                                    // placeholders reserve
                                                    // their slot but are
                                                    // never tappable.
                                                    cell != null && !cell.isCenter && !cell.isPlaceholder ->
                                                        onAction(KeyboardAction.SelectAlternate(cell.word))
                                                    else -> Unit
                                                }
                                            }
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
                                                // decoded. A drag that
                                                // touches letters but never
                                                // crosses MIN_SWIPE_LETTERS
                                                // distinct letter keys is no
                                                // swipe either: it is a
                                                // drift-tap and falls back to
                                                // typing the down key (below).
                                                // Only the space bar
                                                // itself keeps cursor drag.
                                                // A new trail supersedes a
                                                // swipe-feedback flash still
                                                // fading: cancel it before
                                                // it can clear the NEW
                                                // trail's points.
                                                fadeJob?.cancel()
                                                trailFlashColor = null
                                                trailPoints = emptyList()
                                                // Live suggestions from any
                                                // previous gesture are already
                                                // cleared at gesture end; reset
                                                // the throttle bookkeeping for
                                                // the new trail.
                                                liveLastDecodeStartMs = 0L
                                                livePointsAtLastDecode = 0
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
                                                        // Swipe gate: the
                                                        // trail only becomes
                                                        // visible (and later
                                                        // decodable) once it
                                                        // has crossed
                                                        // MIN_SWIPE_LETTERS
                                                        // distinct letter
                                                        // keys. Recomputed
                                                        // per move only while
                                                        // the gate is
                                                        // unpassed — same
                                                        // cost class as the
                                                        // firstLetterContact-
                                                        // Index recompute
                                                        // above.
                                                        if (lettersCrossed < MIN_SWIPE_LETTERS) {
                                                            lettersCrossed =
                                                                distinctLetterKeysCrossed(
                                                                    trail.map { it.position },
                                                                    letterRects,
                                                                )
                                                        }
                                                        trailPoints =
                                                            if (lettersCrossed >= MIN_SWIPE_LETTERS) {
                                                                trail.subList(
                                                                    trailStart, trail.size,
                                                                ).map { it.position.toOffset() }
                                                            } else {
                                                                emptyList()
                                                            }
                                                        // Live suggestions: a
                                                        // throttled background
                                                        // decode of the trimmed
                                                        // trail-so-far (same
                                                        // points the final
                                                        // decode uses), landing
                                                        // in liveOffers for the
                                                        // strip's middle tier.
                                                        // Skip while a decode is
                                                        // still running instead
                                                        // of preempting it.
                                                        if (
                                                            trailStart >= 0 &&
                                                            // Integration note
                                                            // (tapfix + liveswipe):
                                                            // without this conjunct
                                                            // a long drift-tap
                                                            // dwelling on one key
                                                            // fires background
                                                            // decodes that are
                                                            // always discarded.
                                                            lettersCrossed >= MIN_SWIPE_LETTERS &&
                                                            liveDecodeJob == null &&
                                                            shouldRunLiveDecode(
                                                                nowMillis = change.uptimeMillis,
                                                                lastDecodeStartMillis =
                                                                    liveLastDecodeStartMs,
                                                                trailPoints =
                                                                    trail.size - trailStart,
                                                                pointsAtLastDecode =
                                                                    livePointsAtLastDecode,
                                                            )
                                                        ) {
                                                            val snapshot = trail.subList(
                                                                trailStart, trail.size,
                                                            ).toList()
                                                            val gen = ++liveGen
                                                            liveLastDecodeStartMs =
                                                                change.uptimeMillis
                                                            livePointsAtLastDecode =
                                                                trail.size - trailStart
                                                            liveDecodeJob = scope.launch {
                                                                val liveResults =
                                                                    withContext(
                                                                        Dispatchers.Default,
                                                                    ) {
                                                                        // Read at decode
                                                                        // time: the service
                                                                        // may have rebuilt
                                                                        // it with new
                                                                        // custom words.
                                                                        decoderProvider().decode(
                                                                            trail = snapshot,
                                                                            keyCenters =
                                                                                geometry
                                                                                    .letterCenters(),
                                                                            keyWidth =
                                                                                geometry
                                                                                    .keyWidth(),
                                                                            topN = 5,
                                                                        )
                                                                    }
                                                                // Stale-result guard:
                                                                // a newer decode or the
                                                                // gesture's end
                                                                // supersedes this one.
                                                                if (gen == liveGen) {
                                                                    liveOffers =
                                                                        failedSwipeOffers(
                                                                            liveResults,
                                                                            // Top-1 (center)
                                                                            // PLUS the
                                                                            // maxAlternates
                                                                            // flanks.
                                                                            currentMaxAlternates + 1,
                                                                        )?.let {
                                                                            LiveOffers(
                                                                                it,
                                                                                // The leader
                                                                                // mark's
                                                                                // honest
                                                                                // rule:
                                                                                // light blue
                                                                                // only for
                                                                                // what a
                                                                                // finger-up
                                                                                // would
                                                                                // commit.
                                                                                liveResults
                                                                                    .first()
                                                                                    .score <
                                                                                    MAX_COMMIT_SCORE,
                                                                            )
                                                                        }
                                                                    liveDecodeJob = null
                                                                }
                                                            }
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
                                                // Only a trail that crossed
                                                // at least MIN_SWIPE_LETTERS
                                                // distinct letter keys is a
                                                // swipe attempt; anything
                                                // shorter is a drift-tap.
                                                swipeCompleted =
                                                    trailStart >= 0 &&
                                                        lettersCrossed >= MIN_SWIPE_LETTERS
                                                if (!swipeCompleted) {
                                                    // Drift-tap fallback: the
                                                    // finger wandered past
                                                    // the touch slop but the
                                                    // trail never crossed a
                                                    // second letter key —
                                                    // that is a wobbly TAP,
                                                    // not a swipe, so type
                                                    // the down key exactly as
                                                    // the TAP outcome would
                                                    // (a backspace drift
                                                    // deletes once, a shift
                                                    // drift toggles caps; a
                                                    // dead-space or utility-
                                                    // row start has no
                                                    // downKey and types
                                                    // nothing). No trail was
                                                    // ever drawn (gated
                                                    // above) and no decode
                                                    // runs.
                                                    downKey?.let { onAction(it.tapAction()) }
                                                }
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
                                                // Symbols/numeric layout: no
                                                // letter decoding — a drag from a
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
                                                // Long-press a popup host key
                                                // ("." on letters/symbols, "0"
                                                // on the numpad): punctuation
                                                // popup with drag-select; a
                                                // no-drag release commits the
                                                // host key's own text.
                                                val popup = keyPopup(layout.id, downKey)
                                                if (popup != null) {
                                                    var selection = -1
                                                    popupChoices = popup.choices
                                                    popupAnchor = geometry.boundsOf(downKey)
                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        val change =
                                                            event.changes
                                                                .firstOrNull { it.id == down.id }
                                                                ?: break
                                                        if (change.positionChange() != Offset.Zero) {
                                                            selection = popupIndexAt(
                                                                change.position,
                                                                popupBounds,
                                                                popup.choices,
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
                                                                popup.choices[selection]
                                                            } else {
                                                                (downKey.output as KeyOutput.Text)
                                                                    .text
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
                                    pressedAlt = null

                                    // Live-swipe decode teardown, for EVERY
                                    // gesture outcome (swipe, tap, long-press,
                                    // swallowed drag): cancel any in-flight
                                    // live decode and bump the generation so a
                                    // decode that finishes anyway cannot land
                                    // stale offers — below, the synchronous
                                    // final decode owns the trail.
                                    liveDecodeJob?.cancel()
                                    liveDecodeJob = null
                                    liveGen++

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
                                        val confidence = swipeConfidence(results)
                                        // The trail's crossed keys, hoisted:
                                        // a commit AND a failed swipe's offers
                                        // both carry them as proofreader
                                        // evidence.
                                        val letters = crossedLetters(
                                            decodedTrail.map { it.position },
                                            keyCenters,
                                        )
                                        if (best != null && confidence != SwipeConfidence.FAILED) {
                                            onAction(
                                                KeyboardAction.CommitWord(
                                                    best.word,
                                                    letters,
                                                    // Runner-ups for the
                                                    // alternates strip; the
                                                    // reducer stores them in
                                                    // KeyboardState and the
                                                    // strip renders from there.
                                                    swipeAlternates(results),
                                                    // The WIDER near-miss-band
                                                    // runner-ups (top-1
                                                    // excluded): the exact
                                                    // flank list the live
                                                    // strip showed, so the
                                                    // committed strip keeps
                                                    // every surviving word in
                                                    // its mid-swipe slot
                                                    // (band-mismatch dropouts
                                                    // become placeholders).
                                                    stripOffers = failedSwipeOffers(
                                                        results,
                                                        currentMaxAlternates + 1,
                                                    )?.drop(1) ?: emptyList(),
                                                ),
                                            )
                                        } else if (confidence == SwipeConfidence.FAILED) {
                                            // Near-miss rescue: top-1 inside
                                            // the offer band populates the
                                            // strip as one-tap insertions
                                            // (nothing committed; the red
                                            // flash below still fires). An
                                            // empty band emits nothing — the
                                            // placeholder shows, exactly as
                                            // before.
                                            failedSwipeOffers(results, currentMaxAlternates + 1)
                                                ?.let {
                                                    onAction(KeyboardAction.OfferFailedSwipe(it, letters))
                                                }
                                                // Canceled-swipe fallback: the
                                                // final decode's band is empty,
                                                // but the strip showed LIVE
                                                // offers mid-swipe — persist
                                                // those through the same
                                                // OfferFailedSwipe path so they
                                                // stay tappable. leaderWould-
                                                // Commit is live-only and does
                                                // NOT persist: after finger-up
                                                // nothing auto-commits, so the
                                                // center cell renders plain —
                                                // no blue, no green.
                                                ?: liveOffers?.let {
                                                    onAction(
                                                        KeyboardAction.OfferFailedSwipe(
                                                            it.words,
                                                            letters,
                                                        ),
                                                    )
                                                }
                                        }
                                        when (confidence) {
                                            SwipeConfidence.CONFIDENT -> {
                                                // Let the trail linger briefly, then clear it.
                                                scope.launch {
                                                    delay(TRAIL_LINGER_MS)
                                                    trailPoints = emptyList()
                                                }
                                            }
                                            SwipeConfidence.LOW,
                                            SwipeConfidence.FAILED -> {
                                                // Two-tier feedback flash
                                                // (jQuery-highlight style):
                                                // red = seen but rejected,
                                                // yellow = committed but the
                                                // runner-up was close — maybe
                                                // re-swipe. Purely cosmetic:
                                                // the animation never blocks
                                                // or captures input, and the
                                                // next gesture can start
                                                // mid-fade (the DRAG branch
                                                // cancels fadeJob).
                                                trailFlashColor =
                                                    if (confidence == SwipeConfidence.FAILED) {
                                                        FailedSwipeFlash
                                                    } else {
                                                        LowConfidenceFlash
                                                    }
                                                fadeJob?.cancel()
                                                fadeJob = scope.launch {
                                                    trailFade.snapTo(1f)
                                                    trailFade.animateTo(
                                                        0f,
                                                        tween(
                                                            TRAIL_FLASH_FADE_MS,
                                                            easing = LinearEasing,
                                                        ),
                                                    )
                                                    trailPoints = emptyList()
                                                    trailFlashColor = null
                                                }
                                            }
                                        }
                                    }
                                    // Live offers are gesture-scoped: whatever
                                    // persisted (a commit's strip via
                                    // CommitWord, or a canceled swipe's offers
                                    // via OfferFailedSwipe) now lives in
                                    // KeyboardState and wins the altCells
                                    // tiers.
                                    liveOffers = null
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
                            // Always-visible alternates strip between the
                            // utility row and the key rows. Inside the
                            // gesture surface like the utility row: cells
                            // are purely visual, register their bounds, and
                            // taps are re-dispatched in the gesture loop
                            // (a clickable child would have its touches
                            // swallowed by the container pointerInput).
                            SwipeAlternatesStrip(
                                cells = altCells,
                                colors = colors,
                                pressedIndex = pressedAlt,
                                onAlternatePositioned = { index, coordinates ->
                                    altRects[index] = Rect(
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
                                                modifier = if (
                                                    // The numpad keeps its own
                                                    // weights (uniform 1/3-width
                                                    // dial keys), not the fixed
                                                    // letter-key width.
                                                    layout.id != LayoutId.NUMERIC &&
                                                    key.isUnitCharacterKey() &&
                                                    unitKeyWidth > 0.dp
                                                ) {
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
                                                popupHint = keyPopup(layout.id, key)?.hint,
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
                                // A failed or low-confidence swipe flashes
                                // red/yellow and fades; a live or confident
                                // trail keeps the theme color at full strength.
                                val flashColor = trailFlashColor
                                val baseColor = flashColor ?: colors.trail
                                val fade = if (flashColor != null) trailFade.value else 1f
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
            id = UtilityKeyId.NUMERIC,
            // The numpad key toggles: from letters/symbols into the numeric
            // layout, from the numpad back to letters.
            onClick = {
                onAction(
                    KeyboardAction.SwitchLayout(
                        if (state.layout == LayoutId.NUMERIC) LayoutId.LETTERS else LayoutId.NUMERIC,
                    ),
                )
            },
            colors = colors,
            modifier = Modifier.weight(1f),
            pressedId = pressedId,
            onKeyPositioned = onKeyPositioned,
        ) {
            // Contextual label: "123" goes to the numpad, "ABC" returns.
            UtilityKeyLabel(
                text = if (state.layout == LayoutId.NUMERIC) "ABC" else "123",
                colors = colors,
            )
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
    /** Corner hint for the key's long-press popup ("!" on ".", "#" on numpad 0). */
    popupHint: String? = null,
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
        // Long-press hint in the corner of popup host keys ("." / numpad "0").
        if (popupHint != null) {
            Text(
                text = popupHint,
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
