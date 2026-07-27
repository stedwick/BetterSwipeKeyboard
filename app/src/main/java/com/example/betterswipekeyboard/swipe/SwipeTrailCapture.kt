package com.example.betterswipekeyboard.swipe

import java.io.File

/**
 * Debug-only swipe trail recorder for decoder tuning.
 *
 * When enabled, every completed swipe is appended as one JSON line to
 * `files/swipe_trails.jsonl` (see [encode] for the schema) so real
 * right-swipe/wrong-swipe pairs can be replayed against the decoder while
 * tuning its constants. Pull the log with:
 *
 * ```
 * adb run-as com.example.betterswipekeyboard cat files/swipe_trails.jsonl
 * ```
 *
 * Privacy: a keyboard sees everything, and a recorded trail reveals the
 * word it decodes to — so this is OFF by default, debug-build only (the
 * toggle lives in MainActivity behind BuildConfig.DEBUG), the log never
 * leaves the device (no network, no sync, app-private storage), and the
 * toggle screen has a Clear button. Keep it off unless actively tuning.
 */
object SwipeTrailCapture {

    const val LOG_FILE_NAME = "swipe_trails.jsonl"
    const val PREFS_NAME = "swipe_trail_capture"
    const val KEY_ENABLED = "enabled"

    @Volatile
    var enabled: Boolean = false

    private var logFile: File? = null

    fun init(filesDir: File) {
        logFile = File(filesDir, LOG_FILE_NAME)
    }

    fun clear() {
        logFile?.delete()
    }

    fun record(
        trail: List<TimedPoint>,
        keyCenters: Map<Char, Vec2>,
        keyWidth: Float,
        results: List<ScoredWord>,
    ) {
        if (!enabled) return
        val file = logFile ?: return
        try {
            file.appendText(encode(trail, keyCenters, keyWidth, results) + "\n")
        } catch (e: Exception) {
            // Tuning aid only — never let logging break the keyboard.
            android.util.Log.w("SwipeTrailCapture", "record failed", e)
        }
    }

    /**
     * One swipe as a JSON line: wall-clock timestamp, key geometry, the raw
     * timed trail, and the decoder's top results. Pure string building (no
     * org.json — it is not a main-source dependency) so it is unit-testable.
     */
    internal fun encode(
        trail: List<TimedPoint>,
        keyCenters: Map<Char, Vec2>,
        keyWidth: Float,
        results: List<ScoredWord>,
    ): String {
        val sb = StringBuilder()
        sb.append("{\"ts\":").append(System.currentTimeMillis())
        sb.append(",\"keyWidth\":").append(keyWidth)
        sb.append(",\"keys\":{")
        keyCenters.entries.sortedBy { it.key }.forEachIndexed { i, (letter, center) ->
            if (i > 0) sb.append(',')
            sb.append('\"').append(letter).append("\":[").append(center.x)
                .append(',').append(center.y).append(']')
        }
        sb.append('}')
        sb.append(",\"trail\":[")
        trail.forEachIndexed { i, point ->
            if (i > 0) sb.append(',')
            sb.append('[').append(point.position.x).append(',')
                .append(point.position.y).append(',')
                .append(point.tMillis).append(']')
        }
        sb.append(']')
        sb.append(",\"results\":[")
        results.forEachIndexed { i, scored ->
            if (i > 0) sb.append(',')
            sb.append("[\"").append(escape(scored.word)).append("\",")
                .append(scored.score).append(']')
        }
        sb.append(']')
        sb.append('}')
        return sb.toString()
    }

    /** Words are letters-only by dictionary construction; escape defensively. */
    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"")
}
