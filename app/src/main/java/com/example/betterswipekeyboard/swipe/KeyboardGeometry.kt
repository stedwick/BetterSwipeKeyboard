package com.example.betterswipekeyboard.swipe

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.betterswipekeyboard.layout.Key
import com.example.betterswipekeyboard.layout.KeyOutput
import com.example.betterswipekeyboard.layout.LayoutId

/**
 * Collects the on-screen bounds of every key (reported by the UI via
 * `onGloballyPositioned`) and answers the two geometry questions the gesture
 * handler and the decoder need: which key is under a point, and where the
 * letter keys' centers are.
 */
class KeyboardGeometry {

    private val boundsByLayout = mutableMapOf<LayoutId, MutableList<Pair<Key, Rect>>>()

    /** The layout currently on screen; hit-testing applies to it only. */
    var activeLayout: LayoutId = LayoutId.LETTERS

    fun register(layout: LayoutId, key: Key, bounds: Rect) {
        val list = boundsByLayout.getOrPut(layout) { mutableListOf() }
        val existing = list.indexOfFirst { it.first == key }
        if (existing >= 0) list[existing] = key to bounds else list += key to bounds
    }

    fun keyAt(point: Offset): Key? =
        boundsByLayout[activeLayout]
            ?.firstOrNull { it.second.contains(point) }
            ?.first

    /** Bounds of a specific key in the active layout, if registered. */
    fun boundsOf(key: Key): Rect? =
        boundsByLayout[activeLayout]?.firstOrNull { it.first == key }?.second

    /** Letter key centers (a–z of the letters layout), for the decoder. */
    fun letterCenters(): Map<Char, Vec2> =
        letterKeys().associate { (key, bounds) ->
            (key.output as KeyOutput.Text).text.single() to Vec2(bounds.center.x, bounds.center.y)
        }

    /** Average letter-key width in px, used to normalize decoder distances. */
    fun keyWidth(): Float =
        letterKeys().map { it.second.width }.average().takeIf { !it.isNaN() }?.toFloat() ?: 0f

    /** Letter key bounds (a–z of the letters layout), for trail prefix trimming. */
    fun letterRects(): List<Rect> = letterKeys().map { it.second }

    private fun letterKeys(): List<Pair<Key, Rect>> =
        boundsByLayout[LayoutId.LETTERS].orEmpty().filter { (key, _) ->
            (key.output as? KeyOutput.Text)?.text?.singleOrNull()?.isLetter() == true
        }
}
