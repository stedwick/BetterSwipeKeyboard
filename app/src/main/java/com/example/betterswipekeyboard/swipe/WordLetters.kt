package com.example.betterswipekeyboard.swipe

/**
 * The letters of a dictionary word that participate in swipe geometry.
 *
 * The generator admits tokens with exactly one apostrophe between letters
 * ("mother's", "don't") so possessives and contractions are swipeable: the
 * apostrophe has no key and contributes NO geometry — the user swipes
 * through the letters only — while the committed string keeps it verbatim.
 * Stripping it here (rather than teaching every scoring term about it)
 * keeps the decoder's per-letter means undiluted and leaves frequency as
 * the only tie-breaker between same-letter candidates (mothers/mother's).
 *
 * The apostrophe is the only non-key character the generator admits, so a
 * plain filter suffices.
 */
fun swipeLetters(word: String): String = word.filter { it != '\'' }
