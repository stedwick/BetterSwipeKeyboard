package com.example.betterswipekeyboard

import android.content.Context
import com.example.betterswipekeyboard.swipe.parseCustomWords

/**
 * Stores the user's custom swipe-dictionary words in plain SharedPreferences
 * (same file as [ApiKeyStore]). Acceptable for a personal app.
 *
 * Format: one joined string — the normalized words joined with "\n". Not a
 * StringSet: a set loses order. The setup screen displays the words
 * comma-separated (it re-joins [load] itself), so the storage separator is
 * just an internal detail that [parseCustomWords] splits back apart.
 */
class CustomWordStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The stored newline-joined words, or null when none are saved. */
    val rawWords: String?
        get() = prefs.getString(KEY_CUSTOM_WORDS, null)?.takeIf { it.isNotBlank() }

    /**
     * The stored words, re-parsed on read so hand-edited or corrupted prefs
     * normalize away instead of reaching the decoder.
     */
    fun load(): List<String> = parseCustomWords(rawWords.orEmpty())

    fun save(words: List<String>) {
        prefs.edit().putString(KEY_CUSTOM_WORDS, words.joinToString("\n")).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_CUSTOM_WORDS).apply()
    }

    private companion object {
        const val PREFS_NAME = "better_swipe_keyboard"
        const val KEY_CUSTOM_WORDS = "custom_words"
    }
}
