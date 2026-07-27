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
 * Best-guess commits at or above this score are too unsure. Below it we
 * commit even a weak match — a slightly-wrong word beats silence (and the
 * AI proofreader, when enabled, cleans it up a second later). Single
 * source of truth: KeyboardScreen applies it at commit time, the
 * real-trail accuracy harness applies it in tests.
 *
 * Calibrated against real-hand trails: correct swipes at normal speed land
 * at dist magnitudes up to ~1.8 (real fingers are far sloppier than the
 * synthetic guard trails), so 1.75 silently dropped correct top-1s.
 */
const val MAX_COMMIT_SCORE = 1.8f

/**
 * Decodes a swipe trail into the most likely dictionary words.
 *
 * Approach (SHARK-style): instead of reconstructing letters from the trail
 * directly, every plausible dictionary word is scored against the trail.
 * Three geometric terms, all ordered — the word's letters must explain the
 * trail IN SEQUENCE, which is what separates words that share start and end
 * keys but differ in path shape ("my" straight vs "mummy" zigzag):
 *
 * 1. Ordered letter alignment: letter i matches at the minimum of the
 *    first approach basin (closest approach, then departure) AFTER
 *    letter i-1's match — never a global argmin, which lets jitter steal
 *    matches across repeated visits to the same key. A letter crossed
 *    mid-sweep ("swipe"'s i) still finds a cheap match on the passing
 *    trail, but a letter the trail never visits (the second M of "mummy"
 *    on a straight M→Y swipe) must match far off the trail and pays for it.
 *    Doubled letters still match a single pass over one key.
 * 2. Line conformance (SHARK2's "tunnel"): between two consecutive matched
 *    letters, the trail should follow the straight key-to-key segment.
 *    Off-segment distance within [TUNNEL_RADIUS_KEYS] is free (users cut
 *    corners mid-word), beyond it costs linearly up to a saturation cap,
 *    and any single point past [CONFORMANCE_CULL_KEYS] rejects the word.
 *    A correctly traced word scores ~zero regardless of trail LENGTH —
 *    which is why no word-length gate is needed: "am" on a long straight
 *    A→M trail tunnels perfectly, while a zigzag trail can no longer be
 *    explained by a two-letter word spanning its endpoints.
 * 3. Backtrack penalty: trail steps whose direction opposes the current
 *    key-to-key leg (a zigzag word's reversal leg, e.g. M→U→M) accumulate
 *    cost proportional to the backwards distance traveled.
 *
 * Deliberate user motion is weighted as stronger evidence: trail points
 * with high curvature or low speed are *salient points*; the key sequence
 * under them must align with the word (LCS), misses cost extra, and a
 * dwell ≥ [DWELL_DOUBLE_MS] doubles a letter ("follow"'s second L).
 *
 * The remaining terms: trail length vs the word's ideal key-to-key path
 * length (Swype's "expected path length" — per word, never a global gate),
 * a unigram frequency prior, and a small per-letter length bonus (FUTO's
 * rescoring) that counters the structural advantage short words have when
 * geometry ties. On a straight trail, words whose keys lie on the line
 * genuinely tie geometrically; frequency + the length bonus break the tie,
 * so the obvious frequent word wins.
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
                if (word.length < MIN_WORD_LENGTH) continue
                if (word.last() !in lastLetters) continue
                if (word.any { it !in keyCenters }) continue
                val score = score(word, entry.rank, trail, salience, salientKeys,
                    keyCenters, keyWidth, trailLength)
                if (score.isFinite()) scored += ScoredWord(word, score)
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
        val keys = word.map { keyCenters.getValue(it) }

        // Term 1: ordered letter→trail alignment. The forward scan matches
        // each letter at the minimum of the FIRST approach basin (distance
        // falls to a minimum, then rises past LETTER_DEPART_KEYS: the visit
        // is over). A global argmin would be wrong — jitter decides which of
        // two visits to the same key wins, and a later visit steals the
        // match, cascading every following letter off the trail. A letter
        // crossed mid-sweep ("swipe"'s i) still finds a cheap basin on the
        // passing trail; a letter the trail never visits (the second M of
        // "mummy" on a straight M→Y swipe) matches far off the trail and
        // pays for it. Doubled letters still match a single pass.
        val matchIndices = IntArray(keys.size)
        var distanceCost = 0f
        var searchFrom = 0
        for (i in keys.indices) {
            val center = keys[i]
            var bestIdx = searchFrom
            var bestSq = sqDist(trail[searchFrom].position, center)
            for (p in searchFrom + 1 until trail.size) {
                val sq = sqDist(trail[p].position, center)
                if (sq < bestSq) {
                    bestSq = sq
                    bestIdx = p
                } else if (sqrt(sq) - sqrt(bestSq) > LETTER_DEPART_KEYS * keyWidth) {
                    break // departed the basin: the visit to this key is over
                }
            }
            matchIndices[i] = bestIdx
            distanceCost += (sqrt(bestSq) / keyWidth) * (1f + SALIENCE_WEIGHT * salience[bestIdx])
            // Letters after the trail's end (clamp) match its last point at
            // full distance — a word longer than the trail must pay for it.
            searchFrom = min(bestIdx + 1, trail.size - 1)
        }
        distanceCost /= keys.size

        // Unexplained tail: trail arc length AFTER the last letter's
        // match. A word that tunnels only a PREFIX of the trail ("mit"
        // inside a "mother" swipe, "serif" inside "served") parks its
        // last letter mid-trail and ignores the rest. The intended
        // word's last letter matches at/near the trail end and pays
        // nothing — even when the finger UNDERSHOOTS, because the basin
        // then clamps to the trail's last point and the tail arc is 0.
        // That is what makes this safe where an endpoint-DISTANCE cost
        // was not: real lift-offs land 0.5-1.5 key-widths from the last
        // key, so endpoint residue punished intended words exactly like
        // impostors (tried on real trails, reverted — twice). The free
        // slack absorbs genuine overshoot past the last key.
        var tailArc = 0f
        for (p in matchIndices[matchIndices.size - 1] until trail.size - 1) {
            tailArc += trail[p].position.distanceTo(trail[p + 1].position)
        }
        val unexplainedTail =
            min(max(0f, tailArc - TAIL_ARC_FREE_KEYS * keyWidth), TAIL_ARC_CAP_KEYS * keyWidth) / keyWidth

        // Terms 2+3: per-leg line conformance and backtrack penalty. A word
        // whose trail ever leaves the key-to-key corridor by more than
        // CONFORMANCE_CULL_KEYS is rejected outright.
        val legCost = legCosts(keys, matchIndices, trail, keyWidth) ?: return Float.POSITIVE_INFINITY

        // Trail length should roughly match the word's ideal key-to-key path.
        val idealLength = polylineLength(keys)
        val lengthPenalty = abs(trailLength - idealLength) / (idealLength + keyWidth)

        // The keys under the user's deliberate turns/slowdowns should appear
        // in the word, in order (longest common subsequence). A two-letter
        // word can explain at most two deliberate points, so it must not get
        // a perfect score for free: the denominator floor removes the
        // structural lcs/length = 1.0 advantage that once let abbreviations
        // like "ak" beat real words on straight trails.
        val lcs = lcsLength(salientKeys, word.toList())
        val missedSalient = salientKeys.size - lcs
        val alignmentScore = lcs.toFloat() / max(word.length, ALIGNMENT_MIN_DENOMINATOR)

        val frequencyBonus = (ln(dictionary.maxRank + 1.0) - ln(rank.toDouble())) /
            ln(dictionary.maxRank + 1.0)

        return distanceCost * DISTANCE_WEIGHT +
            legCost +
            lengthPenalty * LENGTH_WEIGHT -
            alignmentScore * ALIGNMENT_WEIGHT +
            missedSalient * MISSED_SALIENT_WEIGHT -
            frequencyBonus.toFloat() * FREQUENCY_WEIGHT -
            word.length * LENGTH_BONUS_PER_LETTER +
            unexplainedTail * TAIL_ARC_WEIGHT
    }

    /**
     * Combined line-conformance and backtrack cost over the word's legs
     * (the trail intervals between consecutive matched letters), or null
     * when the trail leaves the key-to-key corridor by more than
     * [CONFORMANCE_CULL_KEYS] — the word cannot explain this trail.
     *
     * Conformance: per trail point, distance to the leg's straight
     * key-to-key segment; free inside the tunnel, linear beyond, saturating
     * at [CONFORMANCE_CAP_KEYS] so one wild excursion cannot dominate
     * (Gboard's saturating spatial cost). Averaged per point, so the cost
     * is independent of trail LENGTH — a long straight trail inside the
     * corridor scores the same as a short one.
     *
     * Backtrack: trail steps moving against the leg's direction accumulate
     * their length (opposite = full step length, perpendicular = nothing).
     * Jitter contributes little because its steps are tiny; a zigzag
     * word's reversal leg on a straight trail is long and fully opposed.
     */
    private fun legCosts(
        keys: List<Vec2>,
        matchIndices: IntArray,
        trail: List<TimedPoint>,
        keyWidth: Float,
    ): Float? {
        val tunnel = TUNNEL_RADIUS_KEYS * keyWidth
        val cap = CONFORMANCE_CAP_KEYS * keyWidth
        val cull = CONFORMANCE_CULL_KEYS * keyWidth
        var conformanceSum = 0f
        var conformancePoints = 0
        var backtrack = 0f

        for (leg in 0 until keys.size - 1) {
            val from = matchIndices[leg]
            val to = matchIndices[leg + 1]
            val a = keys[leg]
            val b = keys[leg + 1]
            val legX = b.x - a.x
            val legY = b.y - a.y
            val legLenSq = legX * legX + legY * legY

            for (p in from..to) {
                val d = pointToSegment(trail[p].position, a, b, legX, legY, legLenSq)
                if (d > cull) return null
                conformanceSum += if (d <= tunnel) 0f else min(d, cap) - tunnel
                conformancePoints++
            }

            if (legLenSq > 1e-6f) {
                val legLen = sqrt(legLenSq)
                for (p in from until to) {
                    val stepX = trail[p + 1].position.x - trail[p].position.x
                    val stepY = trail[p + 1].position.y - trail[p].position.y
                    val stepLen = sqrt(stepX * stepX + stepY * stepY)
                    if (stepLen < 1e-6f) continue
                    val cosine = (stepX * legX + stepY * legY) / (stepLen * legLen)
                    if (cosine < 0f) backtrack += stepLen * (-cosine)
                }
            }
        }

        val conformance = if (conformancePoints == 0) 0f
        else conformanceSum / conformancePoints / keyWidth
        return conformance * CONFORMANCE_WEIGHT + (backtrack / keyWidth) * BACKTRACK_WEIGHT
    }

    /** Squared distance, for argmin loops that only need ordering. */
    private fun sqDist(a: Vec2, b: Vec2): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return dx * dx + dy * dy
    }

    /** Distance from p to the segment a→b (b−a passed precomputed). */
    private fun pointToSegment(
        p: Vec2,
        a: Vec2,
        b: Vec2,
        abX: Float,
        abY: Float,
        abLenSq: Float,
    ): Float {
        if (abLenSq < 1e-6f) return p.distanceTo(a) // doubled letter: segment is a point
        val t = (((p.x - a.x) * abX + (p.y - a.y) * abY) / abLenSq).coerceIn(0f, 1f)
        val dx = p.x - (a.x + t * abX)
        val dy = p.y - (a.y + t * abY)
        return sqrt(dx * dx + dy * dy)
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

        /** One-letter swipes are taps; every shorter word is excluded. */
        const val MIN_WORD_LENGTH = 2

        /** Extra cost multiplier applied at fully-salient points. */
        const val SALIENCE_WEIGHT = 2f

        /** A salient point counts as an intended key within this radius. */
        const val SALIENT_KEY_RADIUS = 0.7f

        /**
         * A letter's visit ends once the trail has departed this many
         * key-widths past its closest approach (first-basin matching — see
         * score()). Larger = more tolerance for overshoot-and-return, but
         * weaker shape discrimination. Tuning starting point.
         */
        const val LETTER_DEPART_KEYS = 0.5f

        /** A region must stay within this radius to count as a hesitation. */
        const val STATIONARY_RADIUS_KEYS = 0.25f

        /** Curvature/speed are measured over this many key-widths of trail. */
        const val CURVATURE_WINDOW_KEYS = 0.35f

        // Tuning starting points below — sources noted per constant; they
        // still need validation against real recorded trails.

        /**
         * Off-path distance within this many key-widths is free (users cut
         * corners mid-word). SHARK2's tunnel was "one key radius".
         */
        const val TUNNEL_RADIUS_KEYS = 0.5f

        /** Per-point conformance cost saturates here (Gboard: Sivek & Riley). */
        const val CONFORMANCE_CAP_KEYS = 2.0f

        /** A single trail point this far off its leg rejects the word
         * (FUTO's legacy decoder culls at ~1.8 key-widths). */
        const val CONFORMANCE_CULL_KEYS = 1.75f

        /** Weight of the line-conformance term — the primary shape signal. */
        const val CONFORMANCE_WEIGHT = 1f

        /** Weight of backwards-travel distance along a leg. */
        const val BACKTRACK_WEIGHT = 1f

        /**
         * Trail arc past the last letter's match that is free of charge —
         * absorbs genuine overshoot past the last key (see score() for why
         * this is arc length and not endpoint distance). Tuning starting
         * point.
         */
        const val TAIL_ARC_FREE_KEYS = 1.5f

        /**
         * The unexplained-tail charge saturates here (same saturation
         * rationale as [CONFORMANCE_CAP_KEYS]): on wandering trails every
         * candidate's last basin ends mid-trail, and an unbounded linear
         * charge destroyed score calibration (correct words at 10+).
         */
        const val TAIL_ARC_CAP_KEYS = 2.0f

        /** Weight of the unexplained-tail charge (per key-width past the
         * free slack). */
        const val TAIL_ARC_WEIGHT = 1f

        /** The alignment bonus denominator never goes below this, so a
         * two-letter word cannot score a free perfect alignment. */
        const val ALIGNMENT_MIN_DENOMINATOR = 3

        /**
         * Per-letter score bonus (FUTO's β·L term): counters the structural
         * advantage short words have when geometry ties. Kept small so word
         * frequency dominates the tie-break, per the signed-off rule: on a
         * genuinely straight trail the obvious frequent short word wins.
         */
        const val LENGTH_BONUS_PER_LETTER = 0.02f

        const val DISTANCE_WEIGHT = 1f
        const val LENGTH_WEIGHT = 0.3f
        const val ALIGNMENT_WEIGHT = 0.8f
        /**
         * Cost per salient key missing from the word. Real-hand trails mark
         * keys the finger merely CROSSES as salient (sloppy aim, genuine
         * small turns), so a heavy weight lets impostor words containing
         * those crossed keys ("officer" for "over", "ther" for "the") beat
         * the intended word — 0.6 per key outweighed the frequency bonus
         * (then capped at 0.35). Halved so one crossed key costs less than
         * a strong frequency advantage. Kept at 0.3 when the frequency
         * weight was later raised to 3.0: the crossed-key class is exactly
         * the impostors the stronger prior should overrule.
         */
        const val MISSED_SALIENT_WEIGHT = 0.3f

        /**
         * Scale of the unigram frequency prior (Zipf: the bonus is linear
         * in -ln rank, full weight at rank 1, zero at the list tail).
         * Calibrated to the 56k wordfreq candidate pool: with the old 20k
         * list, "in the dictionary" implicitly meant "somewhat common", so
         * 0.35 sufficed as a tie-breaker. The 56k pool admits thousands of
         * long-tail words (names, nonce spellings) whose letters tunnel a
         * sloppy real-hand trail as well as the intended word — measured
         * geometry edges on captured trails run 0.1-0.8, far past any
         * 0.35-capped prior — so the prior must carry that discrimination:
         * on ambiguous trails the frequent word wins, per the signed-off
         * straight-trail rule. 3.0 recovered all six common-word
         * regressions of the dictionary swap (very/quick/brown/jumps/over/
         * lazy back over vey/wick/brien/humps/iver/krazy). The ceiling is
         * empirical: at 3.5, rank-5 "of" beats "pizzas" on its own trail —
         * raw frequency starts overwhelming shape. Stop at 3.0.
         */
        const val FREQUENCY_WEIGHT = 3.0f
    }
}
