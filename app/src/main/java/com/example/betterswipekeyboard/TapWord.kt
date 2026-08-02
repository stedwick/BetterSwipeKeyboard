package com.example.betterswipekeyboard

/**
 * Pure, unit-tested word extraction for the tap-typing mirror in the
 * alternates strip. The strip shows the word the user is currently
 * TAP-typing (blue) and the word a boundary character just ended (green) —
 * display only, taken VERBATIM from the field text (caps as typed), never
 * from the dictionary. A word character is a Unicode letter or `'` (so
 * "don't" reads as one word); ANY other character — space, punctuation,
 * digit, emoji — is a word boundary.
 */

/** Word characters: Unicode letters plus the apostrophe ("don't"). */
private fun Char.isTapWordChar(): Boolean = isLetter() || this == '\''

/**
 * The partial word ending exactly at the cursor: the trailing run of word
 * characters in [beforeCursor]. Empty when the text ends with a boundary
 * character (or is empty) — i.e. no word is mid-tap.
 */
fun currentWordPrefix(beforeCursor: String): String {
    var start = beforeCursor.length
    while (start > 0 && beforeCursor[start - 1].isTapWordChar()) start--
    return beforeCursor.substring(start)
}

/**
 * The word that ENDED just before the cursor: skips the trailing run of
 * boundary characters (the space/period/comma the user finished the word
 * with), then reads the word behind it. A newline is a hard boundary that is
 * never crossed — "hello\n" has no tapped word to show (Enter also clears
 * the strip in the reducer). Null when there is no such word: fresh start,
 * a boundary run with no word behind it, or a trailing newline.
 */
fun tappedWordBeforeBoundary(beforeCursor: String): String? {
    var end = beforeCursor.length
    while (end > 0 && beforeCursor[end - 1] != '\n' && !beforeCursor[end - 1].isTapWordChar()) end--
    if (end == 0 || beforeCursor[end - 1] == '\n') return null
    var start = end
    while (start > 0 && beforeCursor[start - 1].isTapWordChar()) start--
    return beforeCursor.substring(start, end)
}
