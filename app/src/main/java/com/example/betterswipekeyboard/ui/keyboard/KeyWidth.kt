package com.example.betterswipekeyboard.ui.keyboard

/** Number of key slots in the full top letter row — the reference row. */
private const val FULL_ROW_KEYS = 10

/**
 * Canonical character-key width (px): the width of one key in a full
 * 10-key row spanning [containerWidthPx] (minus horizontal padding and the
 * inter-key gaps of the reference row). Every character key in every row
 * uses this width, so letter/digit/punctuation keys are the same pixel
 * width across the whole keyboard; rows with fewer keys are centered
 * instead of stretched. Non-positive while the container is unmeasured.
 */
internal fun unitKeyWidthPx(
    containerWidthPx: Float,
    horizontalPaddingPx: Float,
    keyGapPx: Float,
): Float =
    (containerWidthPx - 2 * horizontalPaddingPx - (FULL_ROW_KEYS - 1) * keyGapPx) / FULL_ROW_KEYS
