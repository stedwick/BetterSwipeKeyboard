package com.example.betterswipekeyboard.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Height of the always-visible swipe-alternates strip between the utility
 * row and the content below it. Fixed on every surface (key layouts AND
 * panels/voice) so the IME window never jumps: every branch of
 * KeyboardScreen renders one, keeping each surface exactly
 * `UtilityRowHeight + 6 + AlternatesStripHeight + 6 + KeyboardContentHeight`
 * tall. Matches the 40.dp bars in EmojiPanel/ClipboardPanel.
 */
val AlternatesStripHeight = 40.dp

/**
 * Green of the strip's center cell (the committed word), so it is obvious
 * which word was actually written. Theme-independent like ToggleOn in
 * KeyboardScreen: the iOS system green reads on both the light and the dark
 * keyboard background.
 */
private val CommittedWordGreen = Color(0xFF30D158)

/**
 * Light blue of the LIVE strip's center cell while swiping: the word that
 * WOULD commit if the finger lifted now ([StripCell.isLiveLeader]). The iOS
 * system blue (also the light theme's trail color) reads on both keyboard
 * backgrounds like [CommittedWordGreen]; on finger-up the same cell turns
 * green — same position, same weight, only the color changes.
 */
private val LiveLeaderBlue = Color(0xFF0A84FF)

/**
 * The swipe-alternates strip: after a swipe commit it shows the committed
 * word as a green, bold CENTER cell (tap = no-op) with the score-ranked
 * runner-ups flanking it ([stripCells]); tapping a runner-up replaces the
 * just-committed word and moves it into the center. WHILE swiping, the same
 * layout shows the live decode's top-1 in the center — light blue when it
 * would commit on finger-up, plain otherwise — so lifting the finger only
 * recolors the center, never rearranges the row. Always visible (space
 * reserved, Gboard-style); while empty it shows a gray italic placeholder so
 * the row explains itself.
 *
 * Two render modes, exactly like [UtilityRow]: on the key layouts the strip
 * lives INSIDE the gesture surface, so cells are purely visual and register
 * their bounds for the container gesture loop's hit-testing
 * ([onAlternatePositioned], [pressedIndex] drives the held highlight); on
 * the panel/voice surfaces (null callback) it is inert — the alts are
 * already cleared by the layout switch / voice transition anyway.
 */
@Composable
fun SwipeAlternatesStrip(
    cells: List<StripCell>,
    colors: KeyboardColors,
    modifier: Modifier = Modifier,
    pressedIndex: Int? = null,
    onAlternatePositioned: ((Int, LayoutCoordinates) -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AlternatesStripHeight),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cells.isEmpty()) {
            Text(
                text = "Alternatives will appear here",
                color = colors.keyText.copy(alpha = 0.4f),
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            cells.forEachIndexed { index, cell ->
                Box(
                    modifier = Modifier
                        .then(
                            if (onAlternatePositioned != null) {
                                Modifier.onGloballyPositioned {
                                    onAlternatePositioned(index, it)
                                }
                            } else {
                                Modifier
                            },
                        )
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (index == pressedIndex) {
                                colors.keyBackgroundActive
                            } else {
                                Color.Transparent
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = cell.word,
                        // Placeholders are dropped band-mismatch flanks:
                        // invisible (alpha 0) but rendered, so they reserve
                        // their slot and the surviving cells never move.
                        color = when {
                            cell.isPlaceholder -> colors.keyText.copy(alpha = 0f)
                            cell.isCenter -> CommittedWordGreen
                            cell.isLiveLeader -> LiveLeaderBlue
                            else -> colors.keyText
                        },
                        fontSize = 16.sp,
                        fontWeight =
                            if (cell.isCenter || cell.isLiveLeader) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
