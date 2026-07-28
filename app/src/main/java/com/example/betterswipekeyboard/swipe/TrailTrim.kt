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
