package com.example.betterswipekeyboard.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.betterswipekeyboard.KeyboardAction
import com.example.betterswipekeyboard.layout.EmojiCategories
import com.example.betterswipekeyboard.layout.LayoutId
import com.example.betterswipekeyboard.layout.categoryStartIndex
import kotlinx.coroutines.launch

// Matches the letter-rows block (4 x 52dp rows + 3 x 6dp gaps) so switching
// between letters and emoji never resizes the IME window.
private val EmojiPanelHeight = 226.dp
private const val GRID_COLUMNS = 8

/**
 * The emoji panel: category bar on top, a single scrollable grid of all
 * categories (Gboard-style jump-to-section, no per-category pages), and a
 * bottom bar with ABC (back to letters) and backspace.
 *
 * Rendered as a sibling of the letter gesture container, never inside it:
 * the panel needs its own scroll/click handling and a swipe over the grid
 * must never be read as a glide-typing trail. Taps leave this file as plain
 * [KeyboardAction]s, exactly like letter keys.
 */
@Composable
fun EmojiPanel(
    colors: KeyboardColors,
    onAction: (KeyboardAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(EmojiPanelHeight),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Category bar: tap an icon to jump the grid to that section.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EmojiCategories.forEachIndexed { index, category ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.keyBackground)
                        .clickable {
                            scope.launch {
                                gridState.animateScrollToItem(
                                    categoryStartIndex(EmojiCategories, index),
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = category.icon, fontSize = 16.sp)
                }
            }
        }

        // One grid, all categories back to back, each with a full-span
        // header. Item order must match categoryStartIndex's accounting
        // (1 header + N emojis per category) or jumps land mid-section.
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            state = gridState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EmojiCategories.forEach { category ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = category.title,
                        color = colors.keyText.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                category.emojis.forEach { emoji ->
                    item {
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.keyBackground)
                                .clickable { onAction(KeyboardAction.InsertText(emoji)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = emoji, fontSize = 22.sp)
                        }
                    }
                }
            }
        }

        // Bottom bar: ABC returns to letters, backspace deletes. Backspace
        // is deliberately tap-only (no hold-to-repeat) — a known
        // simplification vs Gboard, see PLAN/edge cases.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PanelKey(colors = colors, modifier = Modifier.weight(1f),
                onClick = { onAction(KeyboardAction.SwitchLayout(LayoutId.LETTERS)) }) {
                Text(
                    text = "ABC",
                    color = colors.keyText,
                    fontSize = 15.sp,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            PanelKey(colors = colors, modifier = Modifier.weight(1f),
                onClick = { onAction(KeyboardAction.Backspace) }) {
                Text(
                    text = "⌫",
                    color = colors.keyText,
                    fontSize = 15.sp,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun PanelKey(
    colors: KeyboardColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.keyBackground)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
