package com.example.betterswipekeyboard.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

/**
 * Choices in the long-press popup on the period key, as a 3x3 grid ordered
 * top (least common) to bottom (most common): the popup is anchored just
 * above the period key, so the bottom row is the closest to the thumb. The
 * bottom row holds the most-expected alternates: "!" sits bottom-center,
 * straight above the resting finger (zero lateral drag), "?" at the
 * thumb-side corner.
 */
internal val PUNCTUATION_POPUP = listOf("\"", ";", ":", "-", "'", ".", ",", "!", "?")
internal const val PUNCTUATION_POPUP_COLUMNS = 3

private val PopupTileSize = 48.dp
private val PopupTileGap = 3.dp
private val PopupPadding = 4.dp

/** Width/height of the whole grid including its inner padding. */
internal val PopupGridSize: Dp =
    PopupTileSize * PUNCTUATION_POPUP_COLUMNS +
        PopupTileGap * (PUNCTUATION_POPUP_COLUMNS - 1) +
        PopupPadding * 2

/** Gap between the anchor key's top edge and the popup's bottom edge. */
internal val PopupGapAboveKey = 6.dp

/** Minimum horizontal distance between the popup and the keyboard edges. */
internal val PopupEdgeMargin = 4.dp

/**
 * Hit-test a finger position against the punctuation popup grid (with slack).
 * The bottom slack is deliberately small: the popup sits just above the
 * anchor key, and a larger allowance would put the resting fingertip inside
 * the hit area, so release jitter could select a bottom-row character
 * instead of committing the host key's own text. Rows are derived from
 * [choices] (the prose and numeric popups differ), never from a hardcoded
 * list — an invisible size coupling mis-slices rows and can return
 * out-of-range indices.
 */
internal fun popupIndexAt(position: Offset, bounds: Rect?, choices: List<String>): Int {
    val b = bounds ?: return -1
    if (position.y < b.top - 24f || position.y > b.bottom + 40f) return -1
    if (position.x < b.left || position.x > b.right) return -1
    val column = ((position.x - b.left) / (b.width / PUNCTUATION_POPUP_COLUMNS)).toInt()
        .coerceIn(0, PUNCTUATION_POPUP_COLUMNS - 1)
    val rows = (choices.size + PUNCTUATION_POPUP_COLUMNS - 1) / PUNCTUATION_POPUP_COLUMNS
    val row = ((position.y - b.top) / (b.height / rows)).toInt().coerceIn(0, rows - 1)
    return (row * PUNCTUATION_POPUP_COLUMNS + column).coerceIn(0, choices.size - 1)
}

/**
 * Top-left position for the popup: horizontally centered over [anchor]
 * (clamped so the popup never leaves the keyboard horizontally), with its
 * bottom edge [gapPx] above the anchor key's top edge — the bottom popup row
 * is then within a minimal drag of the thumb resting on the key. All
 * coordinates are in the keyboard container's space.
 */
internal fun popupTopLeft(
    anchor: Rect,
    popupSize: Size,
    containerSize: Size,
    gapPx: Float,
    marginPx: Float,
): Offset {
    val maxX = (containerSize.width - popupSize.width - marginPx).coerceAtLeast(marginPx)
    val x = (anchor.center.x - popupSize.width / 2f).coerceIn(marginPx, maxX)
    val y = anchor.top - popupSize.height - gapPx
    return Offset(x, y)
}

/**
 * The punctuation popup for the period long-press: a compact 3x3 grid of
 * key tiles, positioned by the caller via [topLeft] (in the parent
 * keyboard's coordinate space).
 *
 * Rendered as a [Popup] — a separate window layered over the keyboard — so
 * it is a pure overlay: it never participates in the keyboard's own
 * measurement or layout, and the keyboard does not shift when it opens or
 * closes. Non-focusable so it steals neither input focus nor the ongoing
 * drag gesture.
 */
@Composable
internal fun PunctuationPopup(
    choices: List<String>,
    highlightIndex: Int,
    topLeft: Offset,
    colors: KeyboardColors,
    onPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        properties = PopupProperties(focusable = false),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(PopupTileGap),
            modifier = modifier
                .shadow(8.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(colors.keyboardBackground)
                .padding(PopupPadding)
                .onGloballyPositioned(onPositioned),
        ) {
            choices.chunked(PUNCTUATION_POPUP_COLUMNS).forEach { rowChoices ->
                Row(horizontalArrangement = Arrangement.spacedBy(PopupTileGap)) {
                    rowChoices.forEach { label ->
                        val index = choices.indexOf(label)
                        Box(
                            modifier = Modifier
                                .size(PopupTileSize)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (index == highlightIndex) {
                                        colors.keyBackgroundActive
                                    } else {
                                        colors.keyBackground
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            // A space choice is invisible as a label — show
                            // the open-box glyph but still commit " ".
                            Text(
                                text = if (label == " ") "␣" else label,
                                color = colors.keyText,
                                fontSize = 20.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
