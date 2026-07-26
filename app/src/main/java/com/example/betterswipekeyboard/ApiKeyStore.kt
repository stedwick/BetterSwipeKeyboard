package com.example.betterswipekeyboard

import android.content.Context

/**
 * Stores the OpenRouter API key in plain SharedPreferences. Acceptable for a
 * personal app; a production keyboard app should use encrypted storage.
 */
class ApiKeyStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val apiKey: String?
        get() = prefs.getString(KEY_API, null)?.takeIf { it.isNotBlank() }

    fun save(key: String) {
        prefs.edit().putString(KEY_API, key.trim()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_API).apply()
    }

    private companion object {
        const val PREFS_NAME = "better_swipe_keyboard"
        const val KEY_API = "openrouter_api_key"
    }
}
