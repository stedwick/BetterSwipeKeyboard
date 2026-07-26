package com.example.betterswipekeyboard.swipe

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

data class Vec2(val x: Float, val y: Float) {
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    fun distanceTo(other: Vec2): Float {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }
}

data class TimedPoint(val position: Vec2, val tMillis: Long)

data class ScoredWord(val word: String, val score: Float)

/**
 * Decodes a swipe trail into the most likely dictionary words.
 *
 * Approach (SHARK-style, simplified): instead of reconstructing letters from
 * the trail directly, every plausible dictionary word is scored against the
 * trail. Each letter of a word only needs *some* trail point near its key —
 * so keys crossed mid-sweep are ignored, and doubled letters match a single
 * pass over one key.
 *
 * Deliberate user motion is weighted as stronger evidence: trail points with
 * high curvature (direction change) or low speed are *salient points*. The
 * key sequence under the salient points must align with the candidate word
 * (LCS alignment), and letter mismatches at salient points cost extra.
 *
 * Pure Kotlin with no Android dependencies so it is fully unit-testable.
 */
class SwipeDecoder(private val dictionary: Dictionary) {

    fun decode(
        trail: List<TimedPoint>,
        keyCenters: Map<Char, Vec2>,
        keyWidth: Float,
        topN: Int = 5,
    ): List<ScoredWord> {
        if (trail.size < 3 || keyWidth <= 0f) return emptyList()

        val salience = computeSalience(trail, keyWidth)
        val salientKeys = salientKeySequence(trail, salience, keyCenters, keyWidth)
        val trailLength = polylineLength(trail.map { it.position })
        val start = trail.first().position
        val end = trail.last().position

        val firstLetters = keyCenters
            .filterValues { it.distanceTo(start) <= FIRST_LAST_KEY_RADIUS * keyWidth }
            .keys
        val lastLetters = keyCenters
            .filterValues { it.distanceTo(end) <= FIRST_LAST_KEY_RADIUS * keyWidth }
            .keys
        if (firstLetters.isEmpty() || lastLetters.isEmpty()) return emptyList()

        val scored = mutableListOf<ScoredWord>()
        for (first in firstLetters) {
            for (entry in dictionary.startingWith(first)) {
                val word = entry.word
                if (!wordLengthAllowed(word.length, trailLength, keyWidth)) continue
                if (word.last() !in lastLetters) continue
                if (word.any { it !in keyCenters }) continue
                val score = score(word, entry.rank, trail, salience, salientKeys,
                    keyCenters, keyWidth, trailLength)
                scored += ScoredWord(word, score)
            }
        }
        return scored.sortedBy { it.score }.take(topN)
    }

    /**
     * Two-letter words are allowed only for short swipes (the finger barely
     * traveled). On longer trails, abbreviations like "ak" otherwise outrank
     * real words — that is why the minimum used to be a flat 3.
     */
    private fun wordLengthAllowed(length: Int, trailLength: Float, keyWidth: Float): Boolean =
        length >= MIN_WORD_LENGTH ||
            (length == 2 && trailLength <= TWO_LETTER_MAX_TRAIL_KEYS * keyWidth)

    // ------------------------------------------------------------------
    // Scoring
    // ------------------------------------------------------------------

    private fun score(
        word: String,
        rank: Int,
        trail: List<TimedPoint>,
        salience: FloatArray,
        salientKeys: List<Char>,
        keyCenters: Map<Char, Vec2>,
        keyWidth: Float,
        trailLength: Float,
    ): Float {
        // Per-letter distance to the nearest trail point, weighted by that
        // point's salience: being far from a *deliberate* turn hurts more
        // than being far from a fast sweep.
        var distanceCost = 0f
        for (letter in word) {
            val center = keyCenters.getValue(letter)
            var best = Float.MAX_VALUE
            var bestWeight = 1f
            trail.forEachIndexed { i, point ->
                val d = point.position.distanceTo(center)
                if (d < best) {
                    best = d
                    bestWeight = 1f + SALIENCE_WEIGHT * salience[i]
                }
            }
            distanceCost += (best / keyWidth) * bestWeight
        }
        distanceCost /= word.length

        // Trail length should roughly match the word's ideal key-to-key path.
        val idealLength = polylineLength(word.map { keyCenters.getValue(it) })
        val lengthPenalty = abs(trailLength - idealLength) / (idealLength + keyWidth)

        // The keys under the user's deliberate turns/slowdowns should appear
        // in the word, in order (longest common subsequence).
        val lcs = lcsLength(salientKeys, word.toList())
        val missedSalient = salientKeys.size - lcs
        val alignmentScore = lcs.toFloat() / word.length

        val frequencyBonus = (ln(dictionary.maxRank + 1.0) - ln(rank.toDouble())) /
            ln(dictionary.maxRank + 1.0)

        return distanceCost * DISTANCE_WEIGHT +
            lengthPenalty * LENGTH_WEIGHT -
            alignmentScore * ALIGNMENT_WEIGHT +
            missedSalient * MISSED_SALIENT_WEIGHT -
            frequencyBonus.toFloat() * FREQUENCY_WEIGHT
    }

    // ------------------------------------------------------------------
    // Salience: curvature + slowness per trail point
    // ------------------------------------------------------------------

    /**
     * Curvature + slowness per trail point. Real fingers produce dense, noisy
     * points, so both are measured over a fixed ARC LENGTH window (a fraction
     * of a key width) rather than a fixed point count — jitter cancels out
     * over the window and genuine turns remain.
     */
    private fun computeSalience(trail: List<TimedPoint>, keyWidth: Float): FloatArray {
        val n = trail.size
        val salience = FloatArray(n)
        if (n < 3) return salience

        val arc = FloatArray(n) // cumulative arc length
        for (i in 1 until n) {
            arc[i] = arc[i - 1] + trail[i].position.distanceTo(trail[i - 1].position)
        }
        val totalLength = arc[n - 1]
        val duration = max(trail.last().tMillis - trail.first().tMillis, 1L).toFloat()
        val avgSpeed = totalLength / duration
        val window = CURVATURE_WINDOW_KEYS * keyWidth

        for (i in 1 until n - 1) {
            var j = i
            while (j > 0 && arc[i] - arc[j] < window) j--
            var k = i
            while (k < n - 1 && arc[k] - arc[i] < window) k++

            val v1 = trail[i].position - trail[j].position
            val v2 = trail[k].position - trail[i].position
            val curvature = angleBetween(v1, v2) / PI.toFloat()

            val dt = max(trail[k].tMillis - trail[j].tMillis, 1L).toFloat()
            val speed = (arc[k] - arc[j]) / dt
            // Slower than the swipe's average speed is deliberate; moving at
            // average speed or faster is not. Baseline at average speed is 0.
            val slowness = (1f - speed / (avgSpeed + 1e-6f)).coerceIn(0f, 1f)

            salience[i] = max(curvature, slowness)
        }
        salience[0] = 0.5f
        salience[n - 1] = 0.5f
        return salience
    }

    /**
     * Keys under the trail's salient points, in trail order. Rather than every
     * point above the salience threshold, contiguous salient regions are
     * collapsed to their single peak point (non-maximum suppression) — a slow
     * region otherwise paints "intended keys" over several neighboring keys.
     *
     * A region where the finger genuinely HESITATES (near-stationary for
     * [DWELL_DOUBLE_MS] or more) emits its key TWICE — this is how "follow"
     * earns its second L from a single pass over the L key. A merely slow
     * pass keeps moving and does not double.
     */
    private fun salientKeySequence(
        trail: List<TimedPoint>,
        salience: FloatArray,
        keyCenters: Map<Char, Vec2>,
        keyWidth: Float,
    ): List<Char> {
        val n = trail.size
        if (n == 0) return emptyList()

        val arc = FloatArray(n)
        for (i in 1 until n) {
            arc[i] = arc[i - 1] + trail[i].position.distanceTo(trail[i - 1].position)
        }

        data class Region(val peak: Int, val from: Int, val to: Int)

        // Contiguous salient regions with hysteresis: a region starts above
        // SALIENCE_THRESHOLD and only ends below REGION_EXIT_THRESHOLD, so a
        // small jitter dip doesn't fragment one deliberate motion into two.
        val regions = mutableListOf<Region>()
        var i = 0
        while (i < n) {
            if (salience[i] < SALIENCE_THRESHOLD) {
                i++
                continue
            }
            var j = i
            var peak = i
            while (j + 1 < n &&
                salience[j + 1] >= REGION_EXIT_THRESHOLD
            ) {
                j++
                if (salience[j] > salience[peak]) peak = j
            }
            regions += Region(peak, i, j)
            i = j + 1
        }

        val result = mutableListOf<Char>()
        for (region in regions) {
            val position = trail[region.peak].position
            val nearest = keyCenters.minByOrNull { it.value.distanceTo(position) } ?: continue
            if (nearest.value.distanceTo(position) > SALIENT_KEY_RADIUS * keyWidth) continue

            // A doubled letter requires the finger to hesitate on the key:
            // at least DWELL_DOUBLE_MS spent near the peak point. A merely
            // slow pass keeps moving and never lingers that long.
            val peakPos = trail[region.peak].position
            val radius = STATIONARY_RADIUS_KEYS * keyWidth
            var dwell = 0L
            var p = region.peak
            while (p > region.from && trail[p - 1].position.distanceTo(peakPos) <= radius) {
                dwell += trail[p].tMillis - trail[p - 1].tMillis
                p--
            }
            p = region.peak
            while (p < region.to && trail[p + 1].position.distanceTo(peakPos) <= radius) {
                dwell += trail[p + 1].tMillis - trail[p].tMillis
                p++
            }
            val desired = if (dwell >= DWELL_DOUBLE_MS) 2 else 1

            val alreadyThere = if (result.lastOrNull() == nearest.key) 1 else 0
            repeat(desired - alreadyThere) { result += nearest.key }
        }
        return result
    }

    // ------------------------------------------------------------------
    // Small math helpers
    // ------------------------------------------------------------------

    private fun polylineLength(points: List<Vec2>): Float {
        var length = 0f
        for (i in 1 until points.size) length += points[i].distanceTo(points[i - 1])
        return length
    }

    private fun angleBetween(a: Vec2, b: Vec2): Float {
        val la = sqrt(a.x * a.x + a.y * a.y)
        val lb = sqrt(b.x * b.x + b.y * b.y)
        if (la < 1e-6f || lb < 1e-6f) return 0f
        val cosine = ((a.x * b.x + a.y * b.y) / (la * lb)).coerceIn(-1f, 1f)
        return acos(cosine)
    }

    private fun lcsLength(a: List<Char>, b: List<Char>): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        val dp = IntArray(b.size + 1)
        for (ca in a) {
            var diagonal = 0
            for (j in 1..b.size) {
                val up = dp[j]
                dp[j] = when {
                    ca == b[j - 1] -> diagonal + 1
                    else -> max(dp[j], dp[j - 1])
                }
                diagonal = up
            }
        }
        return dp[b.size]
    }

    private companion object {
        /** First/last word letter must start/end within this many key-widths. */
        const val FIRST_LAST_KEY_RADIUS = 1.5f

        /** Salience above which a trail point counts as deliberate. */
        const val SALIENCE_THRESHOLD = 0.45f

        /** A salient region ends only when salience falls below this. */
        const val REGION_EXIT_THRESHOLD = 0.30f

        /** How long the finger must linger on a key for a doubled letter. */
        const val DWELL_DOUBLE_MS = 300L

        /** Swiping is not worth it for very short words; they are tapped. */
        const val MIN_WORD_LENGTH = 3

        /** Two-letter words are allowed only on trails this short (key widths). */
        const val TWO_LETTER_MAX_TRAIL_KEYS = 3.5f

        /** Extra cost multiplier applied at fully-salient points. */
        const val SALIENCE_WEIGHT = 2f

        /** A salient point counts as an intended key within this radius. */
        const val SALIENT_KEY_RADIUS = 0.7f

        /** A region must stay within this radius to count as a hesitation. */
        const val STATIONARY_RADIUS_KEYS = 0.25f

        /** Curvature/speed are measured over this many key-widths of trail. */
        const val CURVATURE_WINDOW_KEYS = 0.35f

        const val DISTANCE_WEIGHT = 1f
        const val LENGTH_WEIGHT = 0.3f
        const val ALIGNMENT_WEIGHT = 0.8f
        const val MISSED_SALIENT_WEIGHT = 0.6f
        const val FREQUENCY_WEIGHT = 0.35f
    }
}
