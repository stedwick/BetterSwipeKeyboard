package com.example.betterswipekeyboard.clipboard

/** One remembered clipboard entry. */
data class ClipEntry(val text: String, val copiedAtMillis: Long)

/**
 * In-memory clipboard history, Gboard-style: the keyboard observes the
 * system ClipboardManager (see SwipeKeyboardService) and remembers what the
 * user copies, capped at [maxEntries] entries that expire after
 * [maxAgeMillis].
 *
 * Deliberately in-memory only: the IME process is long-lived and the 1-hour
 * expiry makes persistence nearly worthless, while writing clips to disk
 * (SharedPreferences/Room, device backups) would be a privacy regression
 * for zero user-visible gain. Sensitive clips never reach this class — the
 * service filters ClipDescription.EXTRA_IS_SENSITIVE before calling [add].
 *
 * Pure Kotlin with an injectable clock, so the ring buffer, dedup and
 * expiry are fully unit-testable.
 */
class ClipboardHistory(
    private val maxEntries: Int = 50,
    private val maxAgeMillis: Long = 60 * 60 * 1000, // 1 hour, like Gboard
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val clips = ArrayDeque<ClipEntry>()

    /**
     * Records [text] as the newest entry. Returns false when rejected:
     * blank text, or text over [MAX_TEXT_LENGTH] (memory sanity guard —
     * previews are truncated anyway).
     *
     * Re-copying an existing text dedups: the old entry is removed and the
     * text moves to the top with a fresh timestamp. Matching is
     * case-sensitive exact equality — "Hello" and "hello" are two entries
     * the user deliberately copied; second-guessing them loses data.
     */
    fun add(text: String): Boolean {
        if (text.isBlank() || text.length > MAX_TEXT_LENGTH) return false
        clips.removeAll { it.text == text }
        clips.addFirst(ClipEntry(text, now()))
        while (clips.size > maxEntries) clips.removeLast()
        return true
    }

    /** Newest first, with expired entries filtered out (and dropped). */
    fun entries(): List<ClipEntry> {
        val cutoff = now() - maxAgeMillis
        clips.removeAll { it.copiedAtMillis < cutoff }
        return clips.toList()
    }

    fun remove(text: String) {
        clips.removeAll { it.text == text }
    }

    private companion object {
        const val MAX_TEXT_LENGTH = 10_000
    }
}
