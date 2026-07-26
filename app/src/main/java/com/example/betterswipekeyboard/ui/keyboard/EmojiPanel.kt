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
import com.example.betterswipekeyboard.layout.categoryJumpIndex
import kotlinx.coroutines.launch

// Matches the letter-rows block (4 x 52dp rows + 3 x 6dp gaps) so switching
// between letters and emoji never resizes the IME window.
private val EmojiPanelHeight = 226.dp
private const val GRID_COLUMNS = 8

/**
 * The emoji panel: ONE scroll surface (a single grid) holding the optional
 * suggestion block (keyword-matched from the text before the cursor,
 * hidden when there are no matches), the "Categories" label, the category
 * bar, and all emoji sections back to back (Gboard-style jump-to-section,
 * no per-category pages) — plus a fixed bottom bar with ABC (back to
 * letters) and backspace. Unlike Gboard, the category bar scrolls away
 * with the content; only the bottom bar is pinned.
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
    suggestions: List<String> = emptyList(),
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(EmojiPanelHeight),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // ONE scroll surface: the suggestion block (when present), the
        // Categories label, the category bar and every emoji section are
        // items of a single grid, so scrolling uses the whole panel. The
        // full-span leading items must match categoryJumpIndex's
        // accounting exactly, or category jumps land mid-list.
        LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            state = gridState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Suggestion row: keyword-matched emoji for the text before
            // the cursor, hidden entirely (label and all) when there is
            // nothing to suggest. Tapping a suggestion commits exactly
            // like a grid tap.
            if (suggestions.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionLabel(text = "Suggestions", colors = colors)
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        suggestions.forEach { emoji ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
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

            // Category bar: tap an icon to jump the grid to that section.
            // It deliberately scrolls away with the content (one scroll
            // surface, per request) instead of staying pinned like Gboard.
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel(text = "Categories", colors = colors)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                            categoryJumpIndex(
                                                EmojiCategories,
                                                suggestions.isNotEmpty(),
                                                index,
                                            ),
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = category.icon, fontSize = 16.sp)
                        }
                    }
                }
            }

            EmojiCategories.forEach { category ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionLabel(
                        text = category.title,
                        colors = colors,
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
        // simplification vs Gboard. Deletion itself is grapheme-aware
        // (see InputConnectionEditor.backspace), so one tap removes one
        // whole emoji, never half a surrogate pair.
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
private fun SectionLabel(
    text: String,
    colors: KeyboardColors,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = colors.keyText.copy(alpha = 0.6f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier,
    )
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
