package com.example.betterswipekeyboard.swipe

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * Pure, unit-tested: the index of the first trail point inside any of
 * [letterRects], or -1 when the trail never touches a letter key.
 *
 * A swipe may start anywhere in the keyboard rectangle (utility row,
 * modifier keys, dead space), but the decoder's geometry is letter-to-
 * letter: the prefix before the first letter-key contact is approach, not
 * word, and would poison the ordered letter alignment (a start on the
 * shift key otherwise reads as wandering far off the first letter's
 * basin). `KeyboardScreen` trims the trail — visual trail and decode
 * alike — to start at this index. Only the PREFIX trims: mid-trail
 * excursions between keys and lift-off wander are the decoder's own
 * business (tail-arc term, endpoint gate).
 */
fun firstLetterContactIndex(points: List<Vec2>, letterRects: List<Rect>): Int =
    points.indexOfFirst { p -> letterRects.any { it.contains(Offset(p.x, p.y)) } }

/**
 * Pure, unit-tested: how many DISTINCT letter-key rects a trail crossed
 * (order and revisits irrelevant — an e→w→e trail crosses 2).
 *
 * This is the tap-vs-swipe gate: a gesture is not a swipe until its trail has
 * crossed at least two distinct letter keys. A tap whose finger drifts past
 * the touch slop jitters inside ONE key, and gating on two keeps that
 * drift-tap out of the decoder (which scores the whole candidate set on the
 * main thread) — the gesture loop falls back to typing the down key instead.
 * No dictionary word is lost in principle: the dictionary has no one-letter
 * words, so every decodable word needs at least two letter keys visited.
 * Counting the full collected trail vs the trimmed trail is identical: points
 * before [firstLetterContactIndex] contain no letter rect by construction.
 */
fun distinctLetterKeysCrossed(points: List<Vec2>, letterRects: List<Rect>): Int =
    letterRects.count { rect -> points.any { p -> rect.contains(Offset(p.x, p.y)) } }
