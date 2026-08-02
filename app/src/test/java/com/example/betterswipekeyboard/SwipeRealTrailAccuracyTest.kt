package com.example.betterswipekeyboard

import com.example.betterswipekeyboard.swipe.Dictionary
import com.example.betterswipekeyboard.swipe.MAX_COMMIT_SCORE
import com.example.betterswipekeyboard.swipe.SwipeDecoder
import com.example.betterswipekeyboard.swipe.TimedPoint
import com.example.betterswipekeyboard.swipe.Vec2
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Real-hand accuracy harness: replays Philip's captured swipes (recorded
 * on a Galaxy Fold via SwipeTrailCapture) with their confirmed intended
 * words through the decoder and applies the commit rule
 * ([MAX_COMMIT_SCORE]) exactly as KeyboardScreen does. Four sets:
 * `swipe_trails_philip.*` (first capture), `swipe_trails2_philip.*`
 * (second capture, both phrases twice; intent `-` marks a genuine
 * mis-swipe — the user's typo, excluded from the counts),
 * `swipe_trails3_philip.*` (third capture: sentence 1 with 'bought',
 * sentence 1 with 'sold', the pangram twice, then a lone 'mother'
 * retry — recorded on the combined build with the frequency prior
 * at 3.0 and the filtered word list), and
 * `swipe_trails4_normal_philip.*` (fourth capture: the ten-sentence
 * TDD corpus at normal speed, one pass — 65 word slots plus two
 * genuine retries in sentence 4, 'excellent' #23/#24 and 'example'
 * #25/#26, both scored; sentence 8's one-letter 'a' was tapped, not
 * swiped, so it has no record), `swipe_trails5_normal2_philip.*` (the
 * ten-sentence corpus re-recorded at normal speed, one pass), and
 * `swipe_trails6_short_words_philip.*` (short-word paragraph — 19
 * swipeable words: am well and we go up the hill to ask if you will
 * fix it hello it is fun — swiped TWICE: pass 1 with a deliberate stop
 * on each last letter, pass 2 with natural drift lift-offs; #34/#35 are
 * probable echo swipes of 'it', not paragraph words, marked `-`).
 * The TSV's third column labels the pass for the reader; the pass split
 * is visible in the printed table by record index (pass 1 = #0-18,
 * pass 2 = #19-39), and
 * `swipe_trails7_to_go_to_philip.*` (seventh capture: 24 swipes of the
 * phrase 'to go to' — 14 to-intended (touch-down 0.01-0.20kw from T)
 * and 10 go-intended (#1,4,7,10,13,16,18,20,21,22; touch-down
 * 0.05-0.31kw from G, the trail never within 0.73kw of T) — the driving
 * evidence for the start-key surcharge: on the go trails 'to' matched
 * its T at trail index 0 with a 0.73-1.16kw miss that the per-letter
 * mean halves and the unexplained-head term cannot see (head arc = 0),
 * so its frequency prior (rank 2 vs go's 96 = a constant +1.06)
 * overruled go's genuinely better geometry in 6 of 10 attempts), and
 * `swipe_trails8_joker_lots_movies_philip.*` (eighth capture: the
 * joker/lots/movies paragraph — 36 joker + 24 lots + 16 movies scored
 * swipes — plus the sentence 'i'm joker and watch lots of movies' five
 * times; #0-40 are a→s / a→d / s→e warm-up calibration drags with no
 * known intent, marked `-`; #57-59/#109/#141 are mis-swipes whose
 * honest geometric read IS a different word, marked `-` — see SET8),
 * and `swipe_trails9_the_three_philip.*` (ninth capture: 15 the/three
 * swipes — 8 three-intended with a deliberate mid-word stop on R, 7
 * the-intended passing wide of R; intents inferred from the
 * dwell/geometry split, confirmed by Philip — the driving evidence for
 * the mid-word dwell skip charge, decoder-investigation Addendum 10),
 * and `swipe_trails10_the_elves_philip.*` (tenth capture: 70 swipes of
 * the sentence 'the three elves threw their three trees' — 10 cycles of
 * 7 words, alignment verified geometrically; #10 is a mistyped
 * 'through' performance, marked `-` (confirmed by Philip); #23/#65
 * elves touch down nearest R, #56/#63 the lift off nearest R — scored,
 * notes in the TSV; the driving evidence for the revisit-clamp charge
 * and the salient-floored alignment denominator, decoder-investigation
 * Addendum 11).
 *
 * This is a RATCHET: the MIN_COMMITTED_CORRECT constants are the best
 * committed-correct counts achieved so far per set; bump them every time
 * tuning gains a trail, never lower them. The per-trail table is printed
 * for the tuning loop (see the test report's standard output).
 */
class SwipeRealTrailAccuracyTest {

    private lateinit var decoder: SwipeDecoder

    @Before
    fun setUp() {
        decoder = SwipeDecoder(
            Dictionary.load(javaClass.getResourceAsStream("/words_en.txt")!!),
        )
    }

    @Test
    fun `first capture keeps its committed-correct count`() {
        val correct = replay("swipe_trails_philip")
        assertTrue(
            "ratchet: committed-correct dropped below $MIN_COMMITTED_CORRECT_SET1",
            correct >= MIN_COMMITTED_CORRECT_SET1,
        )
    }

    @Test
    fun `second capture keeps its committed-correct count`() {
        val correct = replay("swipe_trails2_philip")
        assertTrue(
            "ratchet: committed-correct dropped below $MIN_COMMITTED_CORRECT_SET2",
            correct >= MIN_COMMITTED_CORRECT_SET2,
        )
    }

    @Test
    fun `third capture keeps its committed-correct count`() {
        val correct = replay("swipe_trails3_philip")
        assertTrue(
            "ratchet: committed-correct dropped below $MIN_COMMITTED_CORRECT_SET3",
            correct >= MIN_COMMITTED_CORRECT_SET3,
        )
    }

    @Test
    fun `fourth capture keeps its committed-correct count`() {
        val correct = replay("swipe_trails4_normal_philip")
        assertTrue(
            "ratchet: committed-correct dropped below $MIN_COMMITTED_CORRECT_SET4",
            correct >= MIN_COMMITTED_CORRECT_SET4,
        )
    }

    @Test
    fun `fifth capture keeps its committed-correct count`() {
        val correct = replay("swipe_trails5_normal2_philip")
        assertTrue(
            "ratchet: committed-correct dropped below $MIN_COMMITTED_CORRECT_SET5",
            correct >= MIN_COMMITTED_CORRECT_SET5,
        )
    }

    @Test
    fun `sixth capture keeps its committed-correct count`() {
        val correct = replay("swipe_trails6_short_words_philip")
        assertTrue(
            "ratchet: committed-correct dropped below $MIN_COMMITTED_CORRECT_SET6",
            correct >= MIN_COMMITTED_CORRECT_SET6,
        )
    }

    @Test
    fun `seventh capture keeps its committed-correct count`() {
        val correct = replay("swipe_trails7_to_go_to_philip")
        assertTrue(
            "ratchet: committed-correct dropped below $MIN_COMMITTED_CORRECT_SET7",
            correct >= MIN_COMMITTED_CORRECT_SET7,
        )
    }

    @Test
    fun `eighth capture keeps its committed-correct count`() {
        val correct = replay("swipe_trails8_joker_lots_movies_philip")
        assertTrue(
            "ratchet: committed-correct dropped below $MIN_COMMITTED_CORRECT_SET8",
            correct >= MIN_COMMITTED_CORRECT_SET8,
        )
    }

    @Test
    fun `ninth capture keeps its committed-correct count`() {
        val correct = replay("swipe_trails9_the_three_philip")
        assertTrue(
            "ratchet: committed-correct dropped below $MIN_COMMITTED_CORRECT_SET9",
            correct >= MIN_COMMITTED_CORRECT_SET9,
        )
    }

    @Test
    fun `tenth capture keeps its committed-correct count`() {
        val correct = replay("swipe_trails10_the_elves_philip")
        assertTrue(
            "ratchet: committed-correct dropped below $MIN_COMMITTED_CORRECT_SET10",
            correct >= MIN_COMMITTED_CORRECT_SET10,
        )
    }

    /** Replays one capture set, prints the per-trail table, returns the
     * committed-correct count. */
    private fun replay(resourceBase: String): Int {
        val intents = javaClass.getResourceAsStream("/$resourceBase.intents.tsv")!!
            .bufferedReader().readLines()
            .map { it.split('\t').let { cols -> cols[0].toInt() to cols[1] } }.toMap()
        val lines = javaClass.getResourceAsStream("/$resourceBase.jsonl")!!
            .bufferedReader().readLines().filter { it.isNotBlank() }

        var committedCorrect = 0
        var topCorrect = 0
        var scored = 0
        lines.forEachIndexed { i, line ->
            val rec = JSONObject(line)
            val keyWidth = rec.getDouble("keyWidth").toFloat()
            val keysObj = rec.getJSONObject("keys")
            val keyCenters = keysObj.keys().asSequence().associate { k ->
                val xy = keysObj.getJSONArray(k)
                k.single() to Vec2(xy.getDouble(0).toFloat(), xy.getDouble(1).toFloat())
            }
            val trailArr = rec.getJSONArray("trail")
            val trail = (0 until trailArr.length()).map { j ->
                val p = trailArr.getJSONArray(j)
                TimedPoint(
                    Vec2(p.getDouble(0).toFloat(), p.getDouble(1).toFloat()),
                    p.getLong(2),
                )
            }
            val intent = intents.getValue(i)
            val results = decoder.decode(trail, keyCenters, keyWidth, topN = 5)
            val intentRank = results.indexOfFirst { it.word == intent }
            val intentScore = results.getOrNull(intentRank)?.score
            val top = results.firstOrNull()
            val committed = top?.takeIf { it.score < MAX_COMMIT_SCORE }
            if (intent == "-") {
                println(
                    "#%-3d MIS-SWIPE (user typo, not scored) top=%-11s (%6.2f)".format(
                        i, top?.word ?: "-", top?.score ?: Float.NaN,
                    ),
                )
                return@forEachIndexed
            }
            scored++
            if (top?.word == intent) topCorrect++
            if (committed?.word == intent) committedCorrect++
            println(
                "#%-3d intent=%-10s top=%-11s (%6.2f) intentRank=%-3s intentScore=%6s committed=%-11s %s".format(
                    i,
                    intent,
                    top?.word ?: "-",
                    top?.score ?: Float.NaN,
                    if (intentRank < 0) "out" else "#${intentRank + 1}",
                    intentScore?.let { "%.2f".format(it) } ?: "-",
                    committed?.word ?: "(none)",
                    if (committed?.word == intent) "OK" else "MISS",
                ),
            )
        }
        println(
            "ACCURACY[$resourceBase] committed-correct=$committedCorrect/$scored " +
                "top1-correct=$topCorrect/$scored " +
                "(commit threshold $MAX_COMMIT_SCORE)",
        )
        return committedCorrect
    }

    private companion object {
        /**
         * Best achieved so far per set — raise on every win, never lower.
         *
         * History (set 1): the wordfreq dictionary swap (56k words,
         * replacing google-10000) regressed 6 common-word trails
         * (very->vey, quick->wick, brown->brien, jumps->humps,
         * over->iver, lazy->krazy), dropping set 1 from 11 to 5. The
         * wordlist junk filter (feature/wordlist-filter: rare proper
         * names + nonce respellings dropped by word class) removed vey,
         * brien, iver and krazy, recovering "very" and "lazy" -> 7/17.
         * The remaining misses are real-word or surviving-word
         * competitors (wick, humps, overt, britten, dix, doh) — decoder
         * scoring territory, for the frequency-tuning branch.
         *
         * Combined state (frequency weight 3.0 + filtered list, measured
         * in qa/acc-combined): set 1 = 12/17, set 2 = 32/36 — the
         * frequency prior and the filter compound (set 1 beats either
         * branch alone: 11 freq-only, 7 filter-only). Set-1 floor raised
         * 11 -> 12, earned.
         *
         * feature/endpoint-b3g (endpoint evidence grading: endpoint
         * re-anchoring, mid-trail dwell gate, no salience multiplier at
         * endpoint match indices, unexplained-head charge): set 1 #12
         * "jumps" fixed (the head charge sinks "humps", whose H basin
         * sits past the touch-down). Set-1 floor raised 12 -> 13, earned.
         * Set 2 unchanged at 32 — the endpoint classes this package
         * targets were already held by the frequency prior there.
         *
         * Tail slack 0.5 (TAIL_ARC_FREE_KEYS 1.5 -> 0.5, the set-8 fix):
         * set 2 #31 jumped -> jumps FIXED — the same free-tail-hop class
         * as the joker/movies fixes: 'jumped' parked its D one key early
         * and the hop to the trail's end rode free inside the 1.5kw
         * slack; at 0.5 it pays. Set-2 floor raised 32 -> 33, earned.
         * Set 1 unchanged at 13.
         */
        const val MIN_COMMITTED_CORRECT_SET1 = 13
        const val MIN_COMMITTED_CORRECT_SET2 = 33

        /** Third capture's baseline at the B3 decoder: 34/37. The three
         * misses are mother x2 (#3 silence, #21 wrong-commits 'not') —
         * the known line-conformance-cull victims: deleting the 1.75kw
         * hard cull was MEASURED and rejected (it resurrected
         * ict/mortimer/lay/pad/liszt across all three sets and mother
         * still lost to 'mothed'/'moths' junk — the cull is not
         * mother's real blocker, the saturating conformance mean is) —
         * and lazy #34 ('lay' coin-flip at the commit threshold).
         *
         * Lift-off evidence grading (isolated lift-off region emits no
         * salient key): lazy #34 fixed — the drift lift-off's free anchor
         * had been handing 'lay' its y. 34 -> 35, earned. The mother
         * misses shuffle impostors (#3 now silence via 'misinterpret',
         * #21 wrong-commits 'norbert') — same class, unchanged count.
         *
         * Last-letter lift-off re-match (overshoot-and-return,
         * REBASIN_RADIUS_KEYS 0.8 — the 'keyboard' fix): lazy #34 flips
         * back to 'last' (-0.54 over lazy's 0.13) — a LOWERED floor,
         * 35 -> 34, explicitly signed off by Philip: the trail's lift-off
         * basin sits 0.41kw from Y vs 0.49kw from T, genuine geometric
         * ambiguity, and 'last' (rank 136) outranks 'lazy' (rank 4711),
         * so frequency arbitrates the tie exactly as the signed-off
         * straight-trail rule prescribes. Nothing else in this set moves. */
        const val MIN_COMMITTED_CORRECT_SET3 = 34

        /** Fourth capture (ten-sentence TDD corpus, normal speed) baseline
         * at the B3 decoder: 60/67. The seven misses, by class:
         * excellent #23 + example #25 (wild-trail SILENCE — intent absent
         * from top-300, whole candidate field scores >3.3; both are
         * first attempts that Philip immediately retried successfully);
         * nine #31 + nice #32 (intent absent from top-300 while long
         * words bounce/notice partition the trail and win — the
         * mother-cull mechanism, NOT the predicted frequency tie);
         * past #35 (loses 'part' by 0.06); the #48 (loses 'that' by
         * 0.53); how #56 (loses 'hire' by 1.01 — a CONTROL-sentence
         * breach at baseline, fix is TDD territory).
         *
         * Start-key surcharge (START_KEY_SURCHARGE_WEIGHT 0.7, the
         * go->to fix): quick #54 FLIPPED to wick (by 0.021) — a LOWERED
         * floor, 60 -> 59, explicitly signed off by Philip ("do the big
         * fix", 2026-08): the trail's touch-down sits 0.87kw from Q vs
         * 0.34kw from W (a q/w aim slip) — the identical geometric
         * signature to 'to' on the go trails, so no surcharge weight
         * separates them, and the charge differential overrules quick's
         * frequency edge (rank 1093 vs wick's 18472) exactly as the
         * to/go coin-flips did. The trade bought five go fixes on set7
         * (see SET7). */
        const val MIN_COMMITTED_CORRECT_SET4 = 59

        /** Fifth capture (ten-sentence corpus RE-recorded at normal speed,
         * one pass, no retries; 'a' tapped in s8) baseline on main 7d48da9:
         * 58/65. Misses: dog #8 (doping 1.01, intent out of top-5 — the
         * lift-off drift class), minimum #13 (min 2.53 silence), his #14
         * (hours -0.89, intent out of top-5), past #33 (part by 0.11),
         * we #36 (were by 0.05), fix #40 (fox 0.05, intent out of top-5),
         * quick #52 (wick by 0.01). The set-4 mis-swipe candidates
         * (how/nine/nice/example/excellent) all pass in this re-recording,
         * confirming the diagnosis.
         *
         * Lift-off evidence grading: we #36 + quick #52 fixed (drift
         * lift-offs near r/k had handed were/wick a free salient),
         * had #60 FLIPPED to has (by 0.10) — the symmetric cost: that
         * lift-off also shows no deceleration, but the drift happened to
         * end 0.38kw from the RIGHT key (d), so the dropped anchor was
         * luck helping a thin margin, not evidence. Net 58 -> 59, earned.
         *
         * Last-letter lift-off re-match (REBASIN_RADIUS_KEYS 0.8): dog #8
         * (doping -> dog), his #14 (hours -> his) and fix #40 (fox -> fix)
         * — all three were overshoot-and-return lift-offs whose genuine
         * final visit sat in a basin first-basin matching could not reach.
         * 59 -> 62, earned.
         *
         * Start-key surcharge (START_KEY_SURCHARGE_WEIGHT 0.7): quick #52
         * FLIPPED BACK to wick (by 0.53) — a LOWERED floor, 62 -> 61,
         * explicitly signed off by Philip ("do the big fix", 2026-08),
         * undoing the grading session's win above: the touch-down sits
         * 1.31kw from Q vs 0.44kw from W (essentially on W — the q/w aim
         * slip), so the surcharge differential (0.805/w) beats the 0.034
         * baseline margin at any weight past ~0.04. Same signature as
         * set4#54; the trade bought five go fixes on set7 (see SET7).
         *
         * Tail slack 0.5 (TAIL_ARC_FREE_KEYS 1.5 -> 0.5, the set-8 fix):
         * had #60 FLIPPED BACK to had — 'has' parked its S one key early
         * and the hop to the trail's end rode free inside the 1.5kw
         * slack; at 0.5 it pays. This reverts the lift-off grading's
         * documented symmetric cost above (the dropped end anchor had
         * been luck helping a thin margin, not evidence — the tail term
         * now charges the geometry instead). Set-5 floor raised 61 -> 62,
         * earned (decoder-investigation Addendum 9). */
        const val MIN_COMMITTED_CORRECT_SET5 = 62

        /** Sixth capture (short-word paragraph, two passes) baseline on
         * feature/endpoint-evidence 103d75b: 34/38. Pass 1 (deliberate
         * stops on the last letter, #0-18): 18/19 — only and #2 misses
         * (amd -0.38, intent out of top-5). Pass 2 (natural drift
         * lift-offs, #19-39 minus the two '-' echo swipes): 16/19 —
         * we #22 (were by 0.03: drift ended 0.5kw from R and the
         * hardcoded lift-off anchor handed 'were' a free salient),
         * you #30 (yoy -0.56: drift ended 0.7kw from Y), hello #36
         * (help by 0.01). The 18/19-vs-16/19 deliberate-vs-drift split
         * is the captured contrast the lift-off grading targets: drift
         * lift-offs land near wrong keys, deliberate stops do not.
         * #34/#35 are excluded ('-'): timestamps + i->t endpoint
         * geometry say they are echo swipes of 'it', not paragraph
         * words (probable, not proven — flagged to Philip).
         *
         * Lift-off evidence grading: we #22 fixed (same r-anchor disease
         * as set5's we #36) — 34 -> 35, earned. Pass 1 unchanged (18/19):
         * deliberate stops decelerate, so their end regions are
         * non-isolated and keep their keys. Remaining: and #2, you #30
         * (yoy), hello #36 (help).
         *
         * Last-letter lift-off re-match (REBASIN_RADIUS_KEYS 0.8): and #2
         * fixed (amd -> and, the pass-1 holdout — the deliberate stop
         * overshot D and returned). 35 -> 36, earned. */
        const val MIN_COMMITTED_CORRECT_SET6 = 36

        /** Seventh capture ('to go to' x8, 24 trails) baseline at the
         * pre-start-surcharge decoder: 18/24 = all 14 to + 4 of 10 go.
         * The 6 go losses (#4,7,18,20,21,22) all wrong-commit 'to' with
         * 'go' at #2 — margins 0.13 / 0.07 / 0.19 / 0.21 / 0.27 / 0.13
         * respectively: 'to' matches its T at trail index 0 (0.73-1.16kw
         * miss, halved by the per-letter mean, invisible to the head-arc
         * term) and its constant +1.06 frequency edge over 'go' overrules
         * go's better geometry. This is the class the start-key surcharge
         * targets.
         *
         * Start-key surcharge (START_KEY_SURCHARGE_WEIGHT 0.7): five of
         * the six go losses FIXED (#4,7,18,20,22 — 'to' now pays its
         * 0.73-1.16kw start miss undiluted) — 18 -> 23, earned. Residual
         * #21 stays 'to' (by 0.108): its t basin is only 0.73kw, so the
         * 0.23kw excess cannot overcome a 0.272 margin short of w~1.2 —
         * carried by the alternates strip + crossed-letters proofreader
         * (the hello#11 precedent). The cost is the set4/set5 quick
         * flips (see their comments). */
        const val MIN_COMMITTED_CORRECT_SET7 = 23

        /** Eighth capture (joker/lots/movies paragraph + five passes of
         * 'i'm joker and watch lots of movies', 142 records, 96 scored)
         * baseline at the 1.5-tail-slack decoder: 30/96 = joker 3/36,
         * lots 2/24, movies 5/16, i'm/and/watch/of 20/20. #0-40 (a→s /
         * a→d / s→e warm-up calibration drags, intent unknown) and
         * #57-59/#109/#141 are excluded ('-'): those five are mis-swipes
         * whose honest geometric read IS a different word (joe/jobs —
         * the trail never comes within 1.03kw of K on #57-59; the trail
         * ends ON e on #141, so 'movie' is the honest read) — user-shape
         * errors, not decoder failures (the set2/set6 precedent); scoring
         * them would bake five permanent misses into the denominator.
         * This is the driving evidence for TAIL_ARC_FREE_KEYS 1.5 -> 0.5
         * (decoder-investigation Addendum 9): joke/joe/movie park their
         * last letter one key early and the e→r (~1.07kw) / e→s (~1.05kw)
         * hop rides free inside the 1.5kw slack; at 0.5 it pays ~0.55
         * undiluted. The joker losses split into the five excluded
         * mis-swipes, thin-frequency joe/joke wins, and one pruned
         * lastGate; the lots losses are frequency-shaped ('less' ends on
         * the trail's end key — tail arc 0, no tail lever can touch it,
         * Addendum 9's documented dead end).
         *
         * Tail slack 0.5 (TAIL_ARC_FREE_KEYS 1.5 -> 0.5 — this capture is
         * its driving evidence): joker 3 -> 32/36, movies 5 -> 15/16,
         * lots unchanged 2/24, others 20/20 — floor raised 30 -> 69,
         * earned. The four residual joker misses (#65/67/69/70) are
         * thin-frequency joe/joke wins (joker #3 — the strip offers it);
         * movies #94 loses to 'movie' by 0.010. Flip audit over all 426
         * captured records: +41 flips, 0 losses (29 joker + 10 movies +
         * set2 #31 jumped->jumps + set5 #60 has->had).
         *
         * Mid-word dwell skip charge (MIDWORD_DWELL_MS 150,
         * MIDWORD_SKIP_WEIGHT 1.2 — decoder-investigation Addendum 10):
         * the lots/less class, Addendum 9's documented frequency dead
         * end, resolves on dwell evidence — intended-lots trails stop
         * on O and T mid-word, and 'less' skips BOTH dwelled keys and
         * pays 2.4 undiluted. lots 2 -> 12/24 (9x less->lots +
         * 1x loss->lots: #75,76,77,79,80,82,83,84,90,135), floor raised
         * 69 -> 79, earned. The Addendum-9 signed-off coin-flips are
         * preserved (past/part stays 'part' at T=150, quick->wick
         * unchanged, set2#31/set5#60 tail-slack wins unchanged); one
         * unscored '-' trail flips swipe->super (#40), benign. */
        const val MIN_COMMITTED_CORRECT_SET8 = 79

        /** Ninth capture (the/three discrimination, 15 trails: 8
         * three-intended with a deliberate 200-417ms mid-word stop on R,
         * 7 the-intended passing 0.6-1.0kw from R without stopping)
         * baseline at the pre-dwell-charge decoder: **7/15** — every
         * three-trail wrong-commits 'the' (rank 1 vs 157 = a constant
         * +1.39 frequency edge that caps out every mid-word evidence
         * channel). Intents were INFERRED from the dwell/geometry split
         * (interior R dwell 200-417ms vs 0ms; nearest-R approach
         * 0.08-0.21kw vs 0.59-0.98kw) and confirmed by Philip (2026-08).
         *
         * Mid-word dwell skip charge (MIDWORD_DWELL_MS 150,
         * MIDWORD_SKIP_WEIGHT 1.2 — the three-vs-the fix,
         * decoder-investigation Addendum 10): the seven strongest
         * three-trails flip (#0,1,5,6,7,8,13) — 'the' now pays 1.2 for
         * skipping the deliberately dwelled R — ratchet starts at the
         * post-fix 14/15. Residue #14: flips the->there (wrong->wrong) —
         * 'there' contains R so it escapes the charge and outranks three
         * on frequency (0.08); three is #2, carried by the alternates
         * strip. Its R stop is 159ms, 9ms over the threshold — the
         * thin-margin trail.
         *
         * Revisit-clamp charge (decoder-investigation Addendum 11): #14
         * resolves — 'there' clamps its R 1.13kw off-trail between its
         * two e's and pays 1.13 undiluted, three wins by 1.05. The dwell
         * floor stays 150; this is pure geometry. Ratchet 14 -> 15. */
        const val MIN_COMMITTED_CORRECT_SET9 = 15

        /** Tenth capture (70 swipes of 'the three elves threw their
         * three trees' — 10 cycles of 7 words, alignment verified
         * geometrically from start/end keys + arc lengths; #10 is a
         * mistyped 'through', marked '-' — confirmed by Philip; #23/#65
         * elves touch down nearest R, #56/#63 the lift off nearest R —
         * scored, notes in the TSV) baseline at the Addendum-10 decoder:
         * **48/70** — the 10/10, their 10/10, trees 10/10 (corner-cut
         * trails ride frequency over tres), elves 8/10 (#23 rovers, #65
         * rivers: touch-down off E + corner-cut L), threw 4/10 (rank
         * 3216 = a 2.22 prior handicap vs weak W evidence), three 6/20
         * (11x the — natural-speed R visits leave no salient/dwell
         * trace, the 1.39 prior decides; 3x there — 'there' clamps its
         * R 1.09-1.23kw off-trail BETWEEN two near E matches, a
         * degenerate zigzag legCosts cannot see and the mean dilutes).
         *
         * Revisit-clamp charge (REVISIT_FAR_KEYS 0.8,
         * REVISIT_CLAMP_WEIGHT 1.0 — decoder-investigation Addendum 11)
         * + salient-floored alignment denominator: #3 the->threw,
         * #19/#50/#54 there->three, #23 rovers->elves — ratchet starts
         * at the post-fix 53/70. Residue: 11x class-A three (R slows
         * 17-134ms are real but unpriceable — the 60-150ms sub-band is
         * a measured dead end: pizzas/excellent silences + elves#58),
         * threw #24 (there->three wrong->wrong churn), #38/#59/#66
         * (honest weak-W reads), elves #65 (l never visited). */
        const val MIN_COMMITTED_CORRECT_SET10 = 53
    }
}
