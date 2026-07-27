package com.example.betterswipekeyboard.ui.keyboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.betterswipekeyboard.KeyboardAction
import com.example.betterswipekeyboard.clipboard.ClipEntry
import com.example.betterswipekeyboard.layout.LayoutId

// The panel pins to KeyboardContentHeight (KeyboardScreen.kt, same package)
// so switching between letters and the clipboard panel never resizes the
// IME window.

/**
 * The clipboard-history panel: recent clips newest first, tap to paste
 * (and return to letters), long-press to delete an entry, and a bottom bar
 * with ABC (back to letters).
 *
 * Rendered as a sibling of the letter gesture container, never inside it:
 * the panel needs its own scroll/click handling and a drag over the list
 * must never be read as a glide-typing trail. Taps leave this file as plain
 * [KeyboardAction]s, exactly like letter keys.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipboardPanel(
    colors: KeyboardColors,
    entries: List<ClipEntry>,
    onAction: (KeyboardAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(KeyboardContentHeight),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nothing copied yet",
                    color = colors.keyText.copy(alpha = 0.6f),
                    fontSize = 15.sp,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(entries, key = { it.text }) { entry ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.keyBackground)
                            .combinedClickable(
                                onClick = { onAction(KeyboardAction.PasteClip(entry.text)) },
                                onLongClick = { onAction(KeyboardAction.DeleteClip(entry.text)) },
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        // 2-line preview: a long clip (e.g. an OTP message)
                        // is not fully displayed at a glance.
                        Text(
                            text = entry.text,
                            color = colors.keyText,
                            fontSize = 15.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        // Bottom bar: ABC returns to letters.
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
