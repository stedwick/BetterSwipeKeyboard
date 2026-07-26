package com.example.betterswipekeyboard.ime

/**
 * Bottom clearance (px) needed to keep the keyboard fully above any system
 * navigation/IME strip: the largest of the candidate inset bottoms.
 * [navBottom] covers the gesture-navigation strip, [tappableBottom] the
 * 3-button navigation bar, [gestureBottom] the mandatory system gesture
 * area. All zero means no strip is present, so no dead space is added.
 */
fun bottomClearancePx(navBottom: Int, tappableBottom: Int, gestureBottom: Int): Int =
    maxOf(navBottom, tappableBottom, gestureBottom)
