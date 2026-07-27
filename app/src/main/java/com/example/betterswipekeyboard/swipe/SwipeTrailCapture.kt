package com.example.betterswipekeyboard.swipe

import java.io.File

/**
 * Debug-only swipe trail recorder for decoder tuning.
 *
 * When enabled, every completed swipe is appended as one JSON line to
 * `swipe_trails.jsonl` in the app-specific EXTERNAL files dir (see
 * [encode] for the schema) so real right-swipe/wrong-swipe pairs can be
 * replayed against the decoder while tuning its constants. Pull the log
 * (works on emulator and device, no root):
 *
 * ```
 * adb pull /sdcard/Android/data/com.example.betterswipekeyboard/files/swipe_trails.jsonl
 * ```
 *
 * The log lives in external app-specific storage (`getExternalFilesDir`)
 * rather than internal storage because platform-tools 37 removed
 * `adb run-as`, leaving no way to read app-private internal files on a
 * production device. No permission is needed for the app-specific dir.
 * [init] migrates any legacy internal-storage log into the external file.
 *
 * Privacy: a keyboard sees everything, and a recorded trail reveals the
 * word it decodes to — so this is OFF by default, debug-build only (the
 * toggle lives in MainActivity behind BuildConfig.DEBUG), the log never
 * leaves the device by itself (no network, no sync), and the toggle
 * screen has a Clear button. Keep it off unless actively tuning.
 */
object SwipeTrailCapture {

    const val LOG_FILE_NAME = "swipe_trails.jsonl"
    const val PREFS_NAME = "swipe_trail_capture"
    const val KEY_ENABLED = "enabled"

    @Volatile
    var enabled: Boolean = false

    private var logFile: File? = null

    /**
     * Points the log at [externalFilesDir] (falling back to internal when
     * external storage is unavailable) and migrates a legacy internal log:
     * its contents are appended to the external file and the internal copy
     * deleted, so trails recorded before the move are not lost.
     */
    fun init(internalFilesDir: File, externalFilesDir: File?) {
        val legacy = File(internalFilesDir, LOG_FILE_NAME)
        val external = externalFilesDir?.let { File(it, LOG_FILE_NAME) }
        logFile = external ?: legacy
        if (external != null && legacy.exists()) {
            try {
                external.appendText(legacy.readText())
                legacy.delete()
            } catch (e: Exception) {
                // Tuning aid only — keep the legacy file rather than crash.
                android.util.Log.w("SwipeTrailCapture", "migration failed", e)
            }
        }
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
