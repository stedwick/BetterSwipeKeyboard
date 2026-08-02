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
 *    Doubled letters still match a single pass over one key. The LAST
 *    letter alone may re-match at a later approach basin (the basin still
 *    open at lift-off, within [REBASIN_RADIUS_KEYS]): a finger that
 *    overshoots the final key and returns to it otherwise locks the
 *    letter at the first approach mid-trail and pays distance + tail arc
 *    for a visit that genuinely happened ("keyboard"'s d — measured on
 *    captured real-hand trails). No letter follows the final one, so the
 *    re-match cannot cascade the way a stolen mid-word match would.
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
 * dwell ≥ [DWELL_DOUBLE_MS] doubles a letter ("follow"'s second L). The
 * touch-down/lift-off anchors are evidence-free hardcoded salience, so the
 * distance term skips their salience multiplier, and a mid-trail region
 * that is merely slow (not curved) counts as a deliberate key visit only
 * if the finger actually lingered on it.
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

        // Per-decode invariants, computed once and passed down: the
        // cumulative arc length was previously recomputed inside
        // computeSalience/salientKeySequence/dwelledKeys (bit-identical
        // values — same points, same summation order) and ln(maxRank + 1)
        // twice per scored word. The flat trail copy (perf A6) is what the
        // scoring loops index. All of it stays decode-LOCAL (never fields):
        // decode() can run concurrently with itself on one instance.
        val flat = FlatTrail(trail)
        val arc = FloatArray(flat.size) // cumulative arc length
        for (i in 1 until flat.size) {
            arc[i] = arc[i - 1] + flat.dist(i, i - 1)
        }
        val logMaxRank = ln(dictionary.maxRank + 1.0)

        val (salience, slowDominates) = computeSalience(flat, keyWidth, arc)
        val salientKeys = salientKeySequence(flat, salience, slowDominates, keyCenters, keyWidth)
        val dwelledKeys = dwelledKeys(flat, keyCenters, keyWidth, arc)
        // arc's left fold over the same segment distances IS
        // polylineLength's — same sums in the same order, bit-identical.
        val trailLength = arc[flat.size - 1]
        val start = trail.first().position
        val end = trail.last().position

        val firstLetters = keyCenters
            .filterValues { it.distanceTo(start) <= FIRST_LAST_KEY_RADIUS * keyWidth }
            .keys
        val lastLetters = keyCenters
            .filterValues { it.distanceTo(end) <= FIRST_LAST_KEY_RADIUS * keyWidth }
            .keys
        if (firstLetters.isEmpty() || lastLetters.isEmpty()) return emptyList()

        // Decode-LOCAL scratch (letter→center lookup + per-word buffers),
        // replacing score()'s per-candidate allocations. Local, not a field:
        // decode() can run concurrently with itself on one instance.
        val scratch = DecodeScratch(dictionary.maxWordLength, keyCenters)

        // Bounded top-N selection (see TopN's KDoc for the exact
        // sortedBy+take semantics) — no per-candidate ScoredWord, no sort.
        val top = TopN(topN)
        for (first in firstLetters) {
            for (entry in dictionary.startingWith(first)) {
                val word = entry.word
                if (word.length < MIN_WORD_LENGTH) continue
                if (word.last() !in lastLetters) continue
                if (word.any { it != '\'' && it !in keyCenters }) continue
                val score = score(word, entry.rank, flat, salience, salientKeys,
                    keyWidth, trailLength, dwelledKeys, logMaxRank, scratch)
                if (score.isFinite()) top.offer(word, score)
            }
        }
        return top.results()
    }

    // ------------------------------------------------------------------
    // Scoring
    // ------------------------------------------------------------------

    private fun score(
        word: String,
        rank: Int,
        trail: FlatTrail,
        salience: FloatArray,
        salientKeys: List<Char>,
        keyWidth: Float,
        trailLength: Float,
        dwelledKeys: Set<Char>,
        logMaxRank: Double,
        scratch: DecodeScratch,
    ): Float {
        // Apostrophe words match LETTERS ONLY: the apostrophe has no key,
        // contributes zero geometry, and stays verbatim in the committed
        // word. Using the letter count everywhere (not word.length) keeps
        // per-letter means undiluted and leaves frequency as the ONLY
        // tie-breaker between same-letter candidates (mothers/mother's).
        val letters = swipeLetters(word)
        val keyCount = letters.length
        // Fill the decode-local scratch: the same key centers the old
        // letters.map { keyCenters.getValue(it) } produced (same Vec2
        // instances — the prefilter guarantees every letter is a key), with
        // no per-word List and no boxed HashMap lookups. Only [0, keyCount)
        // is valid; the scratch is reused by the next candidate.
        val keys = scratch.keys
        for (i in 0 until keyCount) keys[i] = scratch.centerOf[letters[i].code]!!

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
        val matchIndices = scratch.matchIndices
        // Raw per-letter match distances in key-widths (BEFORE the salience
        // multiplier) — the revisit-clamp charge below reads them.
        val rawDistKeys = scratch.rawDistKeys
        var distanceCost = 0f
        var searchFrom = 0
        var lastLetterCharge = 0f
        for (i in 0 until keyCount) {
            val center = keys[i]
            var bestIdx = searchFrom
            var bestSq = trail.sqDistTo(searchFrom, center)
            // bestDist = sqrt(bestSq), recomputed only when bestSq improves:
            // the depart check below needs it every non-improving iteration
            // (the hottest loop in the decoder), and bestSq changes rarely.
            // sqrt is IEEE-exact and deterministic, so skipping redundant
            // recomputations is bit-identical.
            var bestDist = sqrt(bestSq)
            for (p in searchFrom + 1 until trail.size) {
                val sq = trail.sqDistTo(p, center)
                if (sq < bestSq) {
                    bestSq = sq
                    bestDist = sqrt(sq)
                    bestIdx = p
                } else if (sqrt(sq) - bestDist > LETTER_DEPART_KEYS * keyWidth) {
                    break // departed the basin: the visit to this key is over
                }
            }
            matchIndices[i] = bestIdx
            rawDistKeys[i] = sqrt(bestSq) / keyWidth
            // Endpoint salience is hardcoded (see computeSalience), not
            // measured, so it must not amplify the distance cost of a
            // letter matching the trail's first/last point: real lift-offs
            // land 0.5-1.5 key-widths off the intended key, and the
            // multiplier punished exactly those genuine matches.
            val salienceMultiplier =
                if (bestIdx == 0 || bestIdx == trail.size - 1) 1f
                else 1f + SALIENCE_WEIGHT * salience[bestIdx]
            val charge = (sqrt(bestSq) / keyWidth) * salienceMultiplier
            distanceCost += charge
            lastLetterCharge = charge
            // Letters after the trail's end (clamp) match its last point at
            // full distance — a word longer than the trail must pay for it.
            searchFrom = min(bestIdx + 1, trail.size - 1)
        }

        // Last-letter lift-off-basin re-match (overshoot-and-return). First-
        // basin rigidity is right for every letter but the last — a stolen
        // mid-word match cascades every following letter off the trail, but
        // no letter follows the final one, so nothing can cascade. The
        // failure this fixes: the finger overshoots the last key and
        // RETURNS to it (real lift-off behavior — 7 of 14 captured
        // "keyboard" swipes); stock matching locks the letter at the first
        // approach mid-trail, and the word pays distance + unexplained tail
        // for a visit that genuinely happened, in a basin the first-basin
        // rule can never reach. So the last letter may re-match, under
        // three gates measured on the captured sets: the finger must have
        // departed and returned (a basin must have closed — otherwise the
        // stock match already holds the best point of the still-open first
        // basin); the re-match is taken from the basin still open at the
        // trail's last point (the lift-off approach — a mid-trail drift
        // pass over a foreign key must not claim it, the same
        // deliberate-vs-drift distinction as the lift-off salience
        // grading); and the re-match must beat the stock match within
        // REBASIN_RADIUS_KEYS (the undershoot clamp above stays free — the
        // re-match only wins when the return visit is genuinely closer).
        // Two rejected variants, both measured on the captured sets: an
        // UNGATED re-match let impostor words re-claim foreign end-keys
        // (the lazy→last flip shows the geometric ambiguity is real), and
        // requiring salience/dwell evidence at the re-match point
        // re-silenced the genuine overshoots — the finger slides through
        // the return without lingering, so evidence-gating rejects exactly
        // the class it was meant to rescue.
        if (keyCount >= 2) {
            val lastIdx = keyCount - 1
            val lastKey = keys[lastIdx]
            val stockDist = sqrt(trail.sqDistTo(matchIndices[lastIdx], lastKey))
            var p = min(matchIndices[lastIdx - 1] + 1, trail.size - 1)
            var basinBestSq = trail.sqDistTo(p, lastKey)
            var basinBestIdx = p
            // Same cached-sqrt pattern as the first-basin scan above; the
            // cache tracks EVERY basinBestSq reassignment (both the
            // improvement branch and the basin-closed reset below).
            var basinBestDist = sqrt(basinBestSq)
            var basinsClosed = 0
            while (p + 1 < trail.size) {
                p++
                val sq = trail.sqDistTo(p, lastKey)
                if (sq < basinBestSq) {
                    basinBestSq = sq
                    basinBestDist = sqrt(sq)
                    basinBestIdx = p
                } else if (sqrt(sq) - basinBestDist > LETTER_DEPART_KEYS * keyWidth) {
                    basinsClosed++
                    basinBestSq = sq
                    basinBestDist = sqrt(sq)
                    basinBestIdx = p
                }
            }
            val finalDist = sqrt(basinBestSq)
            if (basinsClosed > 0 && finalDist < stockDist &&
                finalDist <= REBASIN_RADIUS_KEYS * keyWidth
            ) {
                // Same endpoint exemption as the stock match above: the
                // hardcoded endpoint salience is evidence-free and must not
                // amplify a genuine lift-off match. (basinBestIdx is never
                // 0 — the scan starts past the penultimate match.)
                val rebasinSalienceMultiplier =
                    if (basinBestIdx == trail.size - 1) 1f
                    else 1f + SALIENCE_WEIGHT * salience[basinBestIdx]
                distanceCost += finalDist / keyWidth * rebasinSalienceMultiplier - lastLetterCharge
                matchIndices[lastIdx] = basinBestIdx
                rawDistKeys[lastIdx] = finalDist / keyWidth
            }
        }
        distanceCost /= keyCount

        // End-key surcharge: a word whose LAST letter matches beyond the
        // tunnel radius pays the excess distance again, undiluted. The
        // per-letter mean above shrugs an unvisited NEIGHBOR of the visited
        // end key off to ~0.2 ("help"'s p next to "hello"'s o, 1.0kw away,
        // never approached closer than 0.8kw) — and the frequency prior
        // then decides the word (rank 163 vs 1905 is a constant +0.68 for
        // "help"). Measured on 13 captured hello trails. Computed AFTER the
        // lift-off re-match so a re-matched letter is charged on its
        // re-matched distance, and outside every normalization: the tunnel
        // grants position freedom mid-word, but the word's claim to END
        // here should cost when the trail ends off its last key.
        val lastDistKeys =
            sqrt(trail.sqDistTo(matchIndices[keyCount - 1], keys[keyCount - 1])) / keyWidth
        val endKeySurcharge = max(0f, lastDistKeys - TUNNEL_RADIUS_KEYS) * END_KEY_SURCHARGE_WEIGHT

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
        // slack covers lift-off jitter/drift ONLY: genuine
        // overshoot-AND-RETURN is owned by the last-letter re-match
        // above (it re-basins the last letter to the lift-off point,
        // leaving ~0 tail arc), and overshoot WITHOUT return now pays —
        // that free hop is how joke/joe/movie used to win joker/movies
        // trails, parking their last letter one key early while the
        // e→r (~1.07kw) / e→s (~1.05kw) arc rode free inside the old
        // 1.5kw slack (set-8 flip audit: +41 flips, 0 losses over 426
        // captured trails; decoder-investigation Addendum 9).
        var tailArc = 0f
        for (p in matchIndices[keyCount - 1] until trail.size - 1) {
            tailArc += trail.dist(p, p + 1)
        }
        val unexplainedTail =
            min(max(0f, tailArc - TAIL_ARC_FREE_KEYS * keyWidth), TAIL_ARC_CAP_KEYS * keyWidth) / keyWidth

        // Unexplained head: the mirror of the tail above — trail arc BEFORE
        // the first letter's match. A word whose first letter matches
        // mid-trail ("it" inside an "out" swipe, matched on the crossing i)
        // ignores the opening stretch; the intended word's first letter
        // matches at/near the touch-down and pays nothing. The free slack
        // equals the tail's — both cover jitter only (the tail came DOWN
        // to the head's value on the set-8 evidence; the head was never
        // the generous one): touch-down aim is far better than lift-off
        // aim (the finger starts on the key deliberately; the TAIL
        // comment's 0.5-1.5 key-width residue is a lift-off
        // phenomenon), so only the usual touch-down jitter is free.
        var headArc = 0f
        for (p in 0 until matchIndices[0]) {
            headArc += trail.dist(p, p + 1)
        }
        val unexplainedHead =
            min(max(0f, headArc - HEAD_ARC_FREE_KEYS * keyWidth), HEAD_ARC_CAP_KEYS * keyWidth) / keyWidth

        // Start-key surcharge: the mirror of the end-key surcharge above —
        // a word whose FIRST letter matches beyond the tunnel radius pays
        // the excess distance again, undiluted. The head term above cannot
        // see this miss: when the first letter matches at trail index 0,
        // head arc is 0. Measured on Philip's 24 captured 'to go to' trails:
        // on all 10 go-intended swipes 'to' matched its T at index 0 with a
        // 0.73-1.16kw miss the per-letter mean halves, and its constant
        // +1.06 frequency edge over 'go' (rank 2 vs 96) overruled go's
        // better geometry in 6 of 10 attempts. Charged on the stock first-
        // basin distance; there is no start-side re-match, because the
        // end-side disease cannot occur here: the first letter's scan starts
        // at index 0 and fully explores the touch-down basin, so no later
        // closer basin can exist that stock matching cannot reach.
        val firstDistKeys =
            sqrt(trail.sqDistTo(matchIndices[0], keys[0])) / keyWidth
        val startKeySurcharge = max(0f, firstDistKeys - TUNNEL_RADIUS_KEYS) * START_KEY_SURCHARGE_WEIGHT

        // Terms 2+3: per-leg line conformance and backtrack penalty. A word
        // whose trail ever leaves the key-to-key corridor by more than
        // CONFORMANCE_CULL_KEYS is rejected outright.
        val legCost = legCosts(keys, keyCount, matchIndices, trail, keyWidth) ?: return Float.POSITIVE_INFINITY

        // Trail length should roughly match the word's ideal key-to-key path.
        val idealLength = polylineLength(keys, keyCount)
        val lengthPenalty = abs(trailLength - idealLength) / (idealLength + keyWidth)

        // The keys under the user's deliberate turns/slowdowns should appear
        // in the word, in order (longest common subsequence). A two-letter
        // word can explain at most two deliberate points, so it must not get
        // a perfect score for free: the denominator floor removes the
        // structural lcs/length = 1.0 advantage that once let abbreviations
        // like "ak" beat real words on straight trails. The denominator is
        // ALSO floored by the salient count: a short word that under-explains
        // MEASURED salients ('the' explains 3 of [t,h,r,e,e] on a three
        // trail) must not score a perfect 1.0 either. Addendum 2 rejected
        // the salient-ONLY floor — it untied same-LCS words regardless of
        // length and unleashed long-word impostors (foxx>fox, ther>the) —
        // but keeping wordLen in the max preserves that parsimony brake:
        // long words still self-normalize. This floor only demotes short
        // words on salient-rich trails (the Addendum-9 pocketed variant,
        // landed on set-10 evidence: threw#3, three#54 —
        // decoder-investigation Addendum 11).
        val lcs = lcsLength(salientKeys, letters, scratch.lcsDp)
        val missedSalient = salientKeys.size - lcs
        val alignmentScore = lcs.toFloat() /
            max(letters.length, max(salientKeys.size, ALIGNMENT_MIN_DENOMINATOR))

        val frequencyBonus = (logMaxRank - ln(rank.toDouble())) / logMaxRank

        // Mid-word dwell skip charge: a word that SKIPS a key the finger
        // deliberately stopped on mid-word pays per key, undiluted — the
        // mid-word counterpart of the start/end-key surcharges above.
        // Those fixed "word claims an unvisited neighbor of the visited
        // end/start key" (hello→help, go→to); this fixes the mirror
        // class, "word skips a deliberately-visited mid-word key"
        // (three→the: the trail stops 200-417ms on R, but 'the' (rank 1
        // vs 157 = a constant +1.39 frequency edge) skips it and the
        // salient-channel evidence caps out at 0.3-0.9 — measured on 15
        // captured the/three trails, decoder-investigation Addendum 10).
        // The salient-key channel stays at 0.3/key for crossed keys
        // (aim noise); a ≥ MIDWORD_DWELL_MS contiguous stop is deliberate
        // and worth more — the evidence grade the 0.6→0.3 halving lacked.
        // Most trails have NO mid-word dwells: skip the membership
        // structure entirely then; otherwise indexOf membership is the same
        // char equality letters.toSet() gave, without the per-word HashSet.
        val midwordSkip = if (dwelledKeys.isEmpty()) 0f
        else dwelledKeys.count { letters.indexOf(it) < 0 } * MIDWORD_SKIP_WEIGHT

        // Revisit-clamp charge: a MID-WORD letter that matches far off the
        // trail, sandwiched between two on-trail matches, at a key the trail
        // VISITED earlier, pays its match distance again, undiluted. This
        // patches the degenerate-leg hole: when consecutive letters clamp to
        // the same trail region ("there" on a three trail matches its r
        // 1.09-1.23kw off-trail BETWEEN its two e's at the trail end), the
        // e→r→e zigzag spans zero trail arc, so legCosts prices nothing and
        // the per-letter mean dilutes the miss to ~0.2 — while the word's
        // claim "the finger went BACK to r" is exactly what the ordered scan
        // forbade the trail to show. The visit gate is the honesty check:
        // only a key the trail genuinely passed earlier (≤ REVISIT_VISIT_KEYS
        // before the predecessor's match) can be "revisited" — a corner-cut
        // letter the trail never approaches ("trees"' h on a straight
        // t→e→s slide, ~1.9kw off) is NOT a revisit and pays only the mean.
        // Mid-word letters only: the endpoints have their own surcharges.
        // Option D's narrowed variant was rejected in Addendum 1 for
        // touching no live error; the set-10 there-clamp class is that live
        // error (decoder-investigation Addendum 11; grid over all 514
        // captured records: 6 fixes, 0 losses).
        var revisitClamp = 0f
        if (keyCount >= 3) {
            for (i in 1 until keyCount - 1) {
                if (rawDistKeys[i] <= REVISIT_FAR_KEYS) continue
                if (rawDistKeys[i - 1] > REVISIT_NEAR_KEYS ||
                    rawDistKeys[i + 1] > REVISIT_NEAR_KEYS
                ) {
                    continue
                }
                var earliestVisit = Float.MAX_VALUE
                for (p in 0..matchIndices[i - 1]) {
                    val d = trail.distTo(p, keys[i])
                    if (d < earliestVisit) earliestVisit = d
                }
                if (earliestVisit <= REVISIT_VISIT_KEYS * keyWidth) {
                    revisitClamp += rawDistKeys[i]
                }
            }
        }

        return distanceCost * DISTANCE_WEIGHT +
            legCost +
            lengthPenalty * LENGTH_WEIGHT -
            alignmentScore * ALIGNMENT_WEIGHT +
            missedSalient * MISSED_SALIENT_WEIGHT -
            frequencyBonus.toFloat() * FREQUENCY_WEIGHT -
            letters.length * LENGTH_BONUS_PER_LETTER +
            unexplainedTail * TAIL_ARC_WEIGHT +
            unexplainedHead * HEAD_ARC_WEIGHT +
            endKeySurcharge +
            startKeySurcharge +
            revisitClamp * REVISIT_CLAMP_WEIGHT +
            midwordSkip
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
        keys: Array<Vec2>,
        keyCount: Int,
        matchIndices: IntArray,
        trail: FlatTrail,
        keyWidth: Float,
    ): Float? {
        val tunnel = TUNNEL_RADIUS_KEYS * keyWidth
        val cap = CONFORMANCE_CAP_KEYS * keyWidth
        val cull = CONFORMANCE_CULL_KEYS * keyWidth
        var conformanceSum = 0f
        var conformancePoints = 0
        var backtrack = 0f

        for (leg in 0 until keyCount - 1) {
            val from = matchIndices[leg]
            val to = matchIndices[leg + 1]
            val a = keys[leg]
            val b = keys[leg + 1]
            val legX = b.x - a.x
            val legY = b.y - a.y
            val legLenSq = legX * legX + legY * legY

            for (p in from..to) {
                val d = pointToSegment(trail.xs[p], trail.ys[p], a, b, legX, legY, legLenSq)
                if (d > cull) return null
                conformanceSum += if (d <= tunnel) 0f else min(d, cap) - tunnel
                conformancePoints++
            }

            if (legLenSq > 1e-6f) {
                val legLen = sqrt(legLenSq)
                for (p in from until to) {
                    val stepX = trail.xs[p + 1] - trail.xs[p]
                    val stepY = trail.ys[p + 1] - trail.ys[p]
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

    /** Distance from (px, py) to the segment a→b (b−a passed precomputed) —
     * the flat-trail variant of the old Vec2 overload, same arithmetic. */
    private fun pointToSegment(
        px: Float,
        py: Float,
        a: Vec2,
        b: Vec2,
        abX: Float,
        abY: Float,
        abLenSq: Float,
    ): Float {
        if (abLenSq < 1e-6f) {
            // doubled letter: segment is a point (p.distanceTo(a) inline)
            val dx = px - a.x
            val dy = py - a.y
            return sqrt(dx * dx + dy * dy)
        }
        val t = (((px - a.x) * abX + (py - a.y) * abY) / abLenSq).coerceIn(0f, 1f)
        val dx = px - (a.x + t * abX)
        val dy = py - (a.y + t * abY)
        return sqrt(dx * dx + dy * dy)
    }

    // ------------------------------------------------------------------
    // Salience: curvature + slowness per trail point
    // ------------------------------------------------------------------

    /**
     * Curvature + slowness per trail point, plus a per-point flag telling
     * WHICH of the two dominates. Real fingers produce dense, noisy points,
     * so both are measured over a fixed ARC LENGTH window (a fraction of a
     * key width) rather than a fixed point count — jitter cancels out over
     * the window and genuine turns remain. The flag matters downstream: a
     * genuine TURN is deliberate motion at any speed, but a slow patch is
     * only deliberate if the finger actually lingered (a brief slowdown
     * over a crossed key is just aim noise — see salientKeySequence).
     */
    private fun computeSalience(
        trail: FlatTrail,
        keyWidth: Float,
        arc: FloatArray,
    ): Pair<FloatArray, BooleanArray> {
        val n = trail.size
        val salience = FloatArray(n)
        val slowDominates = BooleanArray(n)
        if (n < 3) return salience to slowDominates

        val totalLength = arc[n - 1]
        val duration = max(trail.tMillis[n - 1] - trail.tMillis[0], 1L).toFloat()
        val avgSpeed = totalLength / duration
        val window = CURVATURE_WINDOW_KEYS * keyWidth

        for (i in 1 until n - 1) {
            var j = i
            while (j > 0 && arc[i] - arc[j] < window) j--
            var k = i
            while (k < n - 1 && arc[k] - arc[i] < window) k++

            val curvature = angleBetween(
                trail.xs[i] - trail.xs[j], trail.ys[i] - trail.ys[j],
                trail.xs[k] - trail.xs[i], trail.ys[k] - trail.ys[i],
            ) / PI.toFloat()

            val dt = max(trail.tMillis[k] - trail.tMillis[j], 1L).toFloat()
            val speed = (arc[k] - arc[j]) / dt
            // Slower than the swipe's average speed is deliberate; moving at
            // average speed or faster is not. Baseline at average speed is 0.
            val slowness = (1f - speed / (avgSpeed + 1e-6f)).coerceIn(0f, 1f)

            salience[i] = max(curvature, slowness)
            slowDominates[i] = slowness > curvature
        }
        // Hardcoded endpoint anchors: touch-down/lift-off keys are treated
        // as deliberate with zero measured evidence, so the word has
        // something to align against at the trail ends. Because the evidence
        // is fake, it must not amplify COSTS: the distance term skips the
        // salience multiplier at endpoint match indices (see score()).
        salience[0] = 0.5f
        salience[n - 1] = 0.5f
        return salience to slowDominates
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
     *
     * Two kinds of regions get extra scrutiny:
     * - A region touching the trail's start/end is the touch-down/lift-off
     *   anchor; its salience is the hardcoded 0.5, not measured motion, so
     *   the region's own peak is jitter — the key is anchored to the actual
     *   first/last trail point instead. Exception: an ISOLATED lift-off
     *   region (nothing measured reaches the last point — the finger lifted
     *   mid-flight) emits no key at all; the drift endpoint's nearest key
     *   is not evidence of a deliberate visit (dough's h, we're's r).
     *   Touch-down is exempt from the grading: the finger starts at rest
     *   on an aimed key, so the trail's first point is always deliberate.
     * - A mid-trail region dominated by SLOWNESS (not curvature) counts as
     *   a deliberate key visit only if the finger lingered at least
     *   [SLOW_REGION_MIN_DWELL_MS]: a slight slowdown over a crossed key is
     *   aim noise, and without the gate a "dog" swipe that hesitates over F
     *   decodes as "fog". A curvature region needs no dwell — a genuine
     *   turn is deliberate at any speed.
     */
    private fun salientKeySequence(
        trail: FlatTrail,
        salience: FloatArray,
        slowDominates: BooleanArray,
        keyCenters: Map<Char, Vec2>,
        keyWidth: Float,
    ): List<Char> {
        val n = trail.size
        if (n == 0) return emptyList()

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

        val keys = mutableListOf<Char>()
        for (region in regions) {
            // Lift-off evidence grading: the hardcoded 0.5 salience at the
            // last trail point always opens a region there, even when the
            // finger lifted mid-flight with no deceleration — the region is
            // then ISOLATED (nothing measured extends into the trail; any
            // earlier point with salience >= REGION_EXIT_THRESHOLD would
            // have chained into it). An evidence-free lift-off anchor lets
            // whatever key happens to be nearest the drift endpoint
            // (dough's h, we're's r) charge the intended word a missed
            // salient it never earned, so an isolated lift-off region emits
            // nothing. Touch-down keeps its anchor unconditionally: the
            // finger starts at rest on an aimed key, so the touch-down
            // point is always deliberate (the symmetric exemption was
            // measured at B3 to lose us->is and am).
            if (region.from == n - 1) continue
            val endpoint = region.from == 0 || region.to == n - 1
            val anchor = when {
                region.from == 0 -> 0
                region.to == n - 1 -> n - 1
                else -> region.peak
            }
            // distTo reproduces key.distanceTo(anchor point) bit-identically
            // (the differences are squared, so the sign flip washes out).
            val nearest = keyCenters.minByOrNull { trail.distTo(anchor, it.value) } ?: continue
            if (trail.distTo(anchor, nearest.value) > SALIENT_KEY_RADIUS * keyWidth) continue

            // A doubled letter requires the finger to hesitate on the key:
            // at least DWELL_DOUBLE_MS spent near the peak point. A merely
            // slow pass keeps moving and never lingers that long.
            val radius = STATIONARY_RADIUS_KEYS * keyWidth
            var dwell = 0L
            var p = region.peak
            while (p > region.from && trail.dist(p - 1, region.peak) <= radius) {
                dwell += trail.tMillis[p] - trail.tMillis[p - 1]
                p--
            }
            p = region.peak
            while (p < region.to && trail.dist(p + 1, region.peak) <= radius) {
                dwell += trail.tMillis[p + 1] - trail.tMillis[p]
                p++
            }

            if (!endpoint && slowDominates[region.peak] && dwell < SLOW_REGION_MIN_DWELL_MS) {
                continue
            }

            val desired = if (dwell >= DWELL_DOUBLE_MS) 2 else 1

            val alreadyThere = if (keys.lastOrNull() == nearest.key) 1 else 0
            repeat(desired - alreadyThere) { keys += nearest.key }
        }
        return keys
    }

    /**
     * Keys the finger deliberately STOPPED on mid-word — the evidence source
     * for the mid-word dwell skip charge in [score].
     *
     * A key qualifies when some trail point inside [DWELL_KEY_RADIUS_KEYS]
     * of its center sits inside a contiguous stay of at least
     * [MIDWORD_DWELL_MS] within [DWELL_STATIONARY_KEYS] of that point — the
     * same contiguous-stay idiom as the doubling dwell in
     * [salientKeySequence], but with a lower threshold and no salience
     * requirement. "Contiguous" is what separates a hesitation from a slow
     * pass: a steady crossing, however slow, never accumulates a single
     * stay of 150ms+ inside a 0.25-key-width radius (the finger keeps
     * leaving the radius), while a genuine stop does. Measured on the 15
     * captured the/three trails: the intended-three trails stop 200-417ms
     * on R; no intended-the trail stops anywhere mid-word.
     *
     * The first/last [DWELL_EDGE_EXCLUDE_KEYS] of trail arc are excluded:
     * endpoint physics (touch-down settle, lift-off deceleration) produce
     * stops that say nothing about mid-word letters, and the start/end-key
     * surcharges already own those keys. Interior-only is what keeps the
     * charge from double-charging the endpoints. Addendum 10.
     */
    private fun dwelledKeys(
        trail: FlatTrail,
        keyCenters: Map<Char, Vec2>,
        keyWidth: Float,
        arc: FloatArray,
    ): Set<Char> {
        val n = trail.size
        if (n < 3) return emptySet()
        val total = arc[n - 1]
        val excl = DWELL_EDGE_EXCLUDE_KEYS * keyWidth
        val attribRadius = DWELL_KEY_RADIUS_KEYS * keyWidth
        val stayRadius = DWELL_STATIONARY_KEYS * keyWidth
        val best = HashMap<Char, Long>()
        for (i in 1 until n) {
            if (arc[i] < excl || arc[i] > total - excl) continue
            val nearest = keyCenters.minByOrNull { trail.distTo(i, it.value) } ?: continue
            if (trail.distTo(i, nearest.value) > attribRadius) continue
            var dwell = 0L
            var j = i
            while (j > 0 && trail.dist(j - 1, i) <= stayRadius) {
                dwell += trail.tMillis[j] - trail.tMillis[j - 1]
                j--
            }
            j = i
            while (j < n - 1 && trail.dist(j + 1, i) <= stayRadius) {
                dwell += trail.tMillis[j + 1] - trail.tMillis[j]
                j++
            }
            if (dwell > (best[nearest.key] ?: 0L)) best[nearest.key] = dwell
        }
        return best.filterValues { it >= MIDWORD_DWELL_MS }.keys
    }

    // ------------------------------------------------------------------
    // Small math helpers
    // ------------------------------------------------------------------

    /** Key-to-key path length over the scratch array's [0, count) prefix —
     * the decode path's only caller (the ideal word path in score()). */
    private fun polylineLength(points: Array<Vec2>, count: Int): Float {
        var length = 0f
        for (i in 1 until count) length += points[i].distanceTo(points[i - 1])
        return length
    }

    /**
     * The trail as flat primitive arrays (perf A6): one decode-local copy of
     * the TimedPoint list, so the scoring inner loops index primitives
     * instead of chasing `List<TimedPoint>` → Vec2 references on every
     * access (bounds checks + derefs dominate on ART, PLAN §6). The arrays
     * hold the identical floats/longs in the identical order, and the
     * helpers below use the identical arithmetic as Vec2.distanceTo/sqDist,
     * so every computed value is bit-identical. Decode-local (never a
     * field): decode() can run concurrently with itself on one instance.
     */
    private class FlatTrail(trail: List<TimedPoint>) {
        val size: Int = trail.size
        val xs = FloatArray(size) { trail[it].position.x }
        val ys = FloatArray(size) { trail[it].position.y }
        val tMillis = LongArray(size) { trail[it].tMillis }

        /** point i's `distanceTo` point j. */
        fun dist(i: Int, j: Int): Float {
            val dx = xs[i] - xs[j]
            val dy = ys[i] - ys[j]
            return sqrt(dx * dx + dy * dy)
        }

        /** point i's `distanceTo` v — also reproduces v.distanceTo(point i)
         * bit-identically (the differences are squared, so signs wash out). */
        fun distTo(i: Int, v: Vec2): Float {
            val dx = xs[i] - v.x
            val dy = ys[i] - v.y
            return sqrt(dx * dx + dy * dy)
        }

        /** point i's `sqDist` to v. */
        fun sqDistTo(i: Int, v: Vec2): Float {
            val dx = xs[i] - v.x
            val dy = ys[i] - v.y
            return dx * dx + dy * dy
        }
    }

    /**
     * Decode-LOCAL reusable buffers for score()'s per-word temporaries (perf
     * A2): one set of allocations per decode instead of per candidate word.
     * NOT shared across decodes — decode() can run concurrently with itself
     * on one SwipeDecoder instance (a cancelled live decode can run to
     * completion while the final decode runs), so an instance is created
     * inside decode() and passed down, never stored in a field.
     */
    private class DecodeScratch(maxWordLength: Int, keyCenters: Map<Char, Vec2>) {
        /**
         * Letter → key center, direct-indexed by char code: replaces the
         * boxed `Map<Char, Vec2>.getValue` per letter per word. Every scored
         * letter is a key (decode()'s prefilter guarantees it), so lookups
         * never miss.
         */
        val centerOf: Array<Vec2?> = run {
            val byCode = arrayOfNulls<Vec2>(keyCenters.keys.maxOf { it.code } + 1)
            for ((letter, center) in keyCenters) byCode[letter.code] = center
            byCode
        }

        /**
         * Per-word key centers: score() fills [0, word length) before every
         * use and never reads past it. Pre-filled with a placeholder so the
         * element type stays non-null; the placeholder slots are never read.
         */
        val keys: Array<Vec2> = Array(maxWordLength) { Vec2(0f, 0f) }

        /** Per-word letter→trail match indices; only [0, word length) valid. */
        val matchIndices: IntArray = IntArray(maxWordLength)

        /** Per-word raw match distances in key-widths; same validity range. */
        val rawDistKeys: FloatArray = FloatArray(maxWordLength)

        /** lcsLength's DP row (word length + 1 used; the rest is never
         * read). Re-zeroed by lcsLength per call — a reused buffer would
         * otherwise leak the previous word's last DP row into the next
         * word's first row (the test suite caught exactly that). */
        val lcsDp: IntArray = IntArray(maxWordLength + 1)
    }

    private fun angleBetween(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val la = sqrt(ax * ax + ay * ay)
        val lb = sqrt(bx * bx + by * by)
        if (la < 1e-6f || lb < 1e-6f) return 0f
        val cosine = ((ax * bx + ay * by) / (la * lb)).coerceIn(-1f, 1f)
        return acos(cosine)
    }

    /** DP row buffer [dp] comes from the decode-local scratch (perf: was a
     * per-candidate IntArray — the last per-word allocation). Same DP, same
     * values: a fresh row would be all zeros, so the reused buffer's used
     * prefix is re-zeroed per call (O(word length) — dwarfed by the DP
     * itself); indices past b.length are never touched. */
    private fun lcsLength(a: List<Char>, b: CharSequence, dp: IntArray): Int {
        if (a.isEmpty() || b.isEmpty()) return 0
        dp.fill(0, 0, b.length + 1)
        for (ca in a) {
            var diagonal = 0
            for (j in 1..b.length) {
                val up = dp[j]
                dp[j] = when {
                    ca == b[j - 1] -> diagonal + 1
                    else -> max(dp[j], dp[j - 1])
                }
                diagonal = up
            }
        }
        return dp[b.length]
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

        /**
         * Radius of a "contiguous stay" for the mid-word dwell evidence:
         * the finger counts as dwelling while it keeps inside this many
         * key-widths of the reference point. Same 0.25 as the doubling
         * dwell's stay radius — measured on the the/three captures, a
         * steady crossing of a key never holds a 150ms+ contiguous stay
         * inside 0.25kw (the over#45 c-crossing stays <150ms), a genuine
         * hesitation does (R-stops 200-417ms).
         */
        const val DWELL_STATIONARY_KEYS = 0.25f

        /**
         * A mid-word dwell is attributed to a key only when the stopped
         * point is within this many key-widths of the key's center. 0.5 is
         * the plateau middle: 0.4 loses set9#5's R stop (0.43kw off-center),
         * 0.6 starts admitting mere crossings on adjacent keys.
         */
        const val DWELL_KEY_RADIUS_KEYS = 0.5f

        /**
         * First/last this many key-widths of trail arc are excluded from
         * mid-word dwell attribution — endpoint physics (touch-down settle,
         * lift-off deceleration) is not letter evidence, and the start/end
         * surcharges own those keys. Insensitive over 0.5-1.0 on the
         * captured sets; 0.75 sits mid-plateau.
         */
        const val DWELL_EDGE_EXCLUDE_KEYS = 0.75f

        /**
         * Contiguous stay that marks a mid-word stop as deliberate. The
         * measured plateau is {150, 175}ms: every intended-three trail's R
         * stop is ≥200ms, no intended-the trail reaches 150ms mid-word, and
         * the corpus's tightest genuine crossing (set5#45 over→ocr) sits at
         * 125-149ms — 125 breaks it, 200 leaves set9#5's 200ms R stop on
         * the edge. 150 keeps both sides off their edges.
         */
        const val MIDWORD_DWELL_MS = 150L

        /**
         * Charge per deliberately-dwelled key the word skips, undiluted by
         * any normalization — the mid-word mirror of the start/end-key
         * surcharges. The plateau is {1.0, 1.2}: 1.0 leaves set9#5 decided
         * by 0.006, 1.2 clears it, and higher values buy nothing on the
         * captured sets. Zero losses over the 426-trail corpus; set9 flips
         * 7 of 8 three-trails, set8 resolves the 10-trail lots/less class
         * (Addendum 9's frequency dead end) via dwell evidence — 'less'
         * skips both dwelled keys o,t and pays 2.4. Addendum 10.
         */
        const val MIDWORD_SKIP_WEIGHT = 1.2f

        /**
         * A mid-word letter matching beyond this many key-widths off the
         * trail is a clamp candidate for the revisit-clamp charge. 0.8
         * catches the set-10 clamped re-visits ('there's r at 1.09-1.23kw,
         * rovers' r at 0.98kw); 1.0 loses the rovers fix for no benefit
         * (measured grid cell M5). Coincides with REBASIN_RADIUS_KEYS —
         * a post-hoc resonance, labeled as such.
         */
        const val REVISIT_FAR_KEYS = 0.8f

        /**
         * Weight of the revisit-clamp charge (raw key-widths, undiluted by
         * any normalization) — the degenerate-leg patch: consecutive
         * letters clamped to one trail region give legCosts zero arc to
         * price, so the clamped letter pays its match distance AGAIN here.
         * Measured grid over all 514 captured records at FAR 0.8 / w 1.0:
         * 6 fixes (set9#14 + set10 #3/#19/#23/#50/#54), 1 wrong-to-wrong,
         * 0 previously-correct losses. Addendum 11.
         */
        const val REVISIT_CLAMP_WEIGHT = 1.0f

        /**
         * A revisit-clamp fires only when both neighbors of the far match
         * match within this many key-widths — the sandwich pattern (the
         * word performs a zigzag the trail never showed). = TUNNEL_RADIUS_KEYS:
         * the existing on-trail boundary, not a free parameter.
         */
        const val REVISIT_NEAR_KEYS = 0.5f

        /**
         * …and only when the far-matched key was genuinely VISITED earlier:
         * the trail must have come within this many key-widths of it before
         * the predecessor's match. Exempts corner-cut letters the trail
         * never approaches ("trees"' h at ~1.9kw) — a never-visited key is
         * not a "revisit". = TUNNEL_RADIUS_KEYS, same boundary.
         */
        const val REVISIT_VISIT_KEYS = 0.5f

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

        /**
         * The last letter may re-match into the lift-off basin when the
         * basin's closest approach is within this many key-widths of the
         * key (overshoot-and-return — see score()). Measured on the six
         * captured fixture sets plus 14 captured "keyboard" trails: 0.5/0.7
         * miss one fixture win (set5 dog#8); 1.0 flips set1#15 lazy→kay,
         * breaking the set-1 ratchet. 0.8 is the grid's max-win point with
         * no extra loss (12/14 keyboard commits, fixtures 237/260).
         */
        const val REBASIN_RADIUS_KEYS = 0.8f

        /** A region must stay within this radius to count as a hesitation. */
        const val STATIONARY_RADIUS_KEYS = 0.25f

        /**
         * A mid-trail salient region dominated by slowness (not curvature)
         * must dwell at least this long to count as a deliberate key visit.
         * Far below [DWELL_DOUBLE_MS]: doubling a letter needs a genuine
         * stop, but merely COUNTING the key just needs the slowdown to not
         * be a brief aim-noise dip. Calibrated on captured real-hand
         * trails: the slowdown that flipped "dog" to "fog" lingered well
         * under 60 ms, genuine key visits linger longer.
         */
        const val SLOW_REGION_MIN_DWELL_MS = 60L

        /** Curvature/speed are measured over this many key-widths of trail. */
        const val CURVATURE_WINDOW_KEYS = 0.35f

        // Tuning starting points below — sources noted per constant; they
        // still need validation against real recorded trails.

        /**
         * Off-path distance within this many key-widths is free (users cut
         * corners mid-word). SHARK2's tunnel was "one key radius".
         */
        const val TUNNEL_RADIUS_KEYS = 0.5f

        /**
         * Weight of the end-key surcharge: a word whose LAST letter matches
         * beyond [TUNNEL_RADIUS_KEYS] pays the excess distance again,
         * undiluted (see score()). Mid-word the tunnel gives position
         * freedom, but a word's claim to END on the trail should cost when
         * the trail ends off its last key — otherwise an unvisited neighbor
         * of the visited end key ("help"'s p next to "hello"'s o, 1.0kw
         * away) pays only a per-letter-diluted ~0.2 and the frequency prior
         * decides (rank 163 vs 1905 = a constant +0.68 for help).
         * Tuning starting point. Measured on 13 captured hello trails plus
         * the six fixture sets: 10/13 hello at 0.5 (all six help commits
         * flip; the residuals are the isolated-lift-off family), fixture
         * floors held 13/32/34/60/62/36 across w=0.4-0.7. The binding
         * constraint is set5 dog#8 — its lift-off-re-matched g sits at
         * 0.76kw and pays (0.76-0.5)*w: margin 0.202 -> 0.072 at w=0.5,
         * 0.02 at 0.7, and it FLIPS at 0.8 — so 0.5, mid-plateau with
         * headroom. Documented tension: the lift-off re-match licenses
         * last-letter matches up to [REBASIN_RADIUS_KEYS] 0.8kw while this
         * charges past 0.5kw; at 0.5 the max surcharge on a re-matched
         * letter is 0.15, measured tolerable.
         */
        const val END_KEY_SURCHARGE_WEIGHT = 0.5f

        /**
         * Weight of the start-key surcharge: a word whose FIRST letter
         * matches beyond [TUNNEL_RADIUS_KEYS] pays the excess distance
         * again, undiluted (see score()). Mirror of [END_KEY_SURCHARGE_WEIGHT]:
         * mid-word the tunnel grants position freedom, but a word's claim to
         * START on the trail should cost when the trail starts off its first
         * key — otherwise an unvisited NEIGHBOR of the touched start key
         * ("to"'s t next to "go"'s g, 1.0kw away, never approached closer
         * than 0.73kw) pays only a per-letter-diluted ~0.2-0.5 and the
         * frequency prior decides (to rank 2 vs go rank 96 = a constant
         * +1.06 for "to"). Measured on 24 captured 'to go to' trails plus
         * the six older fixture sets: at 0.7, five of the six go losses fix
         * (set7 18 -> 23/24; #21's t basin is only 0.73kw — its 0.23kw
         * excess cannot overcome a 0.272 margin short of w~1.2, unreachable).
         * The signed-off cost (Philip, 2026-08): the two q/w touch-down aim
         * slips flip quick->wick — set5#52 (baseline margin 0.034 vs a
         * 0.805/w differential, flips at w~0.04) and set4#54 (margin 0.236
         * vs 0.366/w, flips at w~0.64). Their signature is identical to the
         * impostor's (touch-down nearer a neighbor than the first key), so
         * no weight separates them; the exposure audit over all 260 older
         * trails found every other >0.5kw start miss safe (differential <=0
         * against the runner-up, or margin >1). No start-side re-match
         * tension: the first letter always owns the touch-down basin (see
         * score()), so the license/charge tension the end side documents
         * has no start-side counterpart.
         */
        const val START_KEY_SURCHARGE_WEIGHT = 0.7f

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
         * covers lift-off jitter/drift only (see score() for why this is
         * arc length and not endpoint distance). Genuine overshoot past
         * the last key is NOT absorbed here: overshoot-AND-return is
         * owned by the last-letter re-match (it re-basins the last letter
         * to the lift-off point, leaving ~0 tail arc), and overshoot
         * without return pays up to ~1.0 on arc that used to ride free
         * (the 0.5-1.5kw band) — that free hop is how joke/joe/movie
         * parked their last letter one key early and still won
         * joker/movies trails. Lowered 1.5 -> 0.5 on the set-8 evidence
         * (+41 flips, 0 losses over 426 captured trails; the 1.0/0.75/0.5
         * grid is monotone with a wide plateau, so this is no knife-edge;
         * 0.75 is the measured fallback if the set-9 overshoot-band
         * captures show reliance — decoder-investigation Addendum 9).
         */
        const val TAIL_ARC_FREE_KEYS = 0.5f

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

        /**
         * Trail arc before the first letter's match that is free of charge —
         * absorbs normal touch-down jitter (see score() for why this is arc
         * length). Now EQUAL to [TAIL_ARC_FREE_KEYS] — both slacks cover
         * jitter only; touch-down aim is much better than lift-off aim, so
         * the head never needed the tail's old 1.5kw overshoot allowance
         * (the set-8 change brought the tail DOWN to this value, not the
         * head up). Tuning starting point.
         */
        const val HEAD_ARC_FREE_KEYS = 0.5f

        /**
         * The unexplained-head charge saturates here (same saturation
         * rationale as [TAIL_ARC_CAP_KEYS]).
         */
        const val HEAD_ARC_CAP_KEYS = 2.0f

        /** Weight of the unexplained-head charge (per key-width past the
         * free slack). */
        const val HEAD_ARC_WEIGHT = 1f

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
