package com.example.betterswipekeyboard.swipe

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
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

        val salience = computeSalience(trail)
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
                if (word.length < MIN_WORD_LENGTH || word.last() !in lastLetters) continue
                if (word.any { it !in keyCenters }) continue
                val score = score(word, entry.rank, trail, salience, salientKeys,
                    keyCenters, keyWidth, trailLength)
                scored += ScoredWord(word, score)
            }
        }
        return scored.sortedBy { it.score }.take(topN)
    }

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

    private fun computeSalience(trail: List<TimedPoint>): FloatArray {
        val n = trail.size
        val salience = FloatArray(n)
        if (n < 3) return salience

        var totalLength = 0f
        for (i in 1 until n) totalLength += trail[i].position.distanceTo(trail[i - 1].position)
        val duration = max(trail.last().tMillis - trail.first().tMillis, 1L).toFloat()
        val avgSpeed = totalLength / duration

        for (i in 1 until n - 1) {
            val prev = trail[max(0, i - 2)].position
            val here = trail[i].position
            val next = trail[min(n - 1, i + 2)].position
            val v1 = here - prev
            val v2 = next - here
            val curvature = angleBetween(v1, v2) / PI.toFloat()

            val dt = max(trail[min(n - 1, i + 1)].tMillis - trail[max(0, i - 1)].tMillis, 1L)
                .toFloat()
            val speed = trail[min(n - 1, i + 1)].position
                .distanceTo(trail[max(0, i - 1)].position) / dt
            val slowness = (1f - speed / (avgSpeed * SLOWNESS_REFERENCE + 1e-6f))
                .coerceIn(0f, 1f)

            salience[i] = max(curvature, slowness)
        }
        salience[0] = 0.5f
        salience[n - 1] = 0.5f
        return salience
    }

    /**
     * Keys under salient points, in trail order, consecutive dupes collapsed.
     * Lingering on a key (a salient cluster lasting [DWELL_DOUBLE_MS] or more)
     * counts as intending that letter TWICE — this is how "follow" earns its
     * second L from a single pass over the L key.
     */
    private fun salientKeySequence(
        trail: List<TimedPoint>,
        salience: FloatArray,
        keyCenters: Map<Char, Vec2>,
        keyWidth: Float,
    ): List<Char> {
        // Collect salient points near keys as clusters: key + start/end time.
        data class Cluster(val key: Char, val start: Long, var end: Long)
        val clusters = mutableListOf<Cluster>()
        trail.forEachIndexed { i, point ->
            if (salience[i] < SALIENCE_THRESHOLD) return@forEachIndexed
            val nearest = keyCenters.minByOrNull { it.value.distanceTo(point.position) }
                ?: return@forEachIndexed
            if (nearest.value.distanceTo(point.position) > SALIENT_KEY_RADIUS * keyWidth) {
                return@forEachIndexed
            }
            val last = clusters.lastOrNull()
            if (last != null && last.key == nearest.key) {
                last.end = point.tMillis
            } else {
                clusters += Cluster(nearest.key, point.tMillis, point.tMillis)
            }
        }
        // Collapse clusters; lingering on a key emits it twice (e.g. "follow"
        // earns its second L by hesitating on the L key).
        val result = mutableListOf<Char>()
        for (cluster in clusters) {
            val desired = if (cluster.end - cluster.start >= DWELL_DOUBLE_MS) 2 else 1
            val alreadyThere = if (result.lastOrNull() == cluster.key) 1 else 0
            repeat(desired - alreadyThere) { result += cluster.key }
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

        /** How long a salient cluster must last to count as a doubled letter. */
        const val DWELL_DOUBLE_MS = 150L

        /** Swiping is not worth it for very short words; they are tapped. */
        const val MIN_WORD_LENGTH = 3

        /** Extra cost multiplier applied at fully-salient points. */
        const val SALIENCE_WEIGHT = 2f

        /** A salient point counts as an intended key within this radius. */
        const val SALIENT_KEY_RADIUS = 0.7f

        /** Speed below this fraction of 1.5× average counts as slow. */
        const val SLOWNESS_REFERENCE = 1.5f

        const val DISTANCE_WEIGHT = 1f
        const val LENGTH_WEIGHT = 0.3f
        const val ALIGNMENT_WEIGHT = 0.8f
        const val MISSED_SALIENT_WEIGHT = 0.6f
        const val FREQUENCY_WEIGHT = 0.35f
    }
}
