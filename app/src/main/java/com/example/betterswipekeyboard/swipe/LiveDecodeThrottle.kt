package com.example.betterswipekeyboard.swipe

/**
 * Minimum wall time between live mid-swipe decodes. Move events arrive at
 * frame rate (~60-120 Hz); decoding per event would waste battery for
 * suggestions that change at best a few times per swipe. Tuning starting
 * point — tune on-device.
 */
const val LIVE_DECODE_MIN_INTERVAL_MS = 120L

/**
 * Minimum NEW trail points since the last decode's snapshot before another
 * live decode fires: a paused finger must not re-decode the identical trail
 * on every interval tick. Tuning starting point — tune on-device.
 */
const val LIVE_DECODE_MIN_NEW_POINTS = 6

/**
 * Minimum trimmed-trail points before live decoding starts at all: with fewer
 * points the candidate field is geometrically ambiguous and the suggestions
 * are noise. Tuning starting point — tune on-device.
 */
const val LIVE_DECODE_MIN_TRAIL_POINTS = 10

/**
 * Pure, unit-tested: should a live mid-swipe decode start now?
 *
 * All three gates must pass: enough trail to say anything
 * ([LIVE_DECODE_MIN_TRAIL_POINTS]), long enough since the last decode started
 * ([LIVE_DECODE_MIN_INTERVAL_MS]), and enough new trail since the last
 * decode's snapshot ([LIVE_DECODE_MIN_NEW_POINTS]). A fourth condition — no
 * decode job currently running — is enforced by the caller (it owns the job
 * handle) and deliberately kept out of this function.
 *
 * "No previous decode" is encoded as [lastDecodeStartMillis] /
 * [pointsAtLastDecode] both 0: the interval gate then passes for any
 * [nowMillis] ≥ [LIVE_DECODE_MIN_INTERVAL_MS] (gestures are timed on
 * `SystemClock.uptimeMillis`, always ≫ 120 ms by the time a trail exists),
 * so the first decode fires as soon as the trail is long enough.
 */
fun shouldRunLiveDecode(
    nowMillis: Long,
    lastDecodeStartMillis: Long,
    trailPoints: Int,
    pointsAtLastDecode: Int,
): Boolean =
    trailPoints >= LIVE_DECODE_MIN_TRAIL_POINTS &&
        nowMillis - lastDecodeStartMillis >= LIVE_DECODE_MIN_INTERVAL_MS &&
        trailPoints - pointsAtLastDecode >= LIVE_DECODE_MIN_NEW_POINTS
