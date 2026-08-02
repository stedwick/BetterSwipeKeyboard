package com.example.betterswipekeyboard.swipe

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * GOLD-STANDARD swipe corpus: 242 real-hand trail records (25 sentences
 * stress-testing every historically hard decoder class — the three/the war,
 * tail-slack impostors, reversal legs, corner cuts, frequency-prior fights,
 * apostrophe words) replayed through SwipeDecoder and compared against
 * per-record expected top-1 results.
 *
 * Baseline (captured 2026-08-02): 98.35% top-1 accuracy (238/242), with
 * exactly 4 known misses baked into the expectations:
 *   rec 82  we'll→well  (apostrophe-blindness: the trail can't express the
 *                        apostrophe, so 'well' wins on parsimony)
 *   rec 84  lots→less   rec 147  tall→talk   rec 209  well→will
 *
 * Any decoder change that flips ANY record's top-1 fails this test and names
 * the record — improvements (fixing a known miss) and regressions alike.
 * Update swipe_trails_25phrases_expected.tsv DELIBERATELY when a verified
 * decoder improvement changes an output, and say so in the commit; never
 * edit it just to make the test pass. The 98% floor below stays even then,
 * so expectations can't be quietly watered down. Target: hold or beat 98%;
 * 100% is the goal.
 *
 * Data (app/src/test/resources/): swipe_trails_25phrases.jsonl — 247
 * captured records, one per swiped word, ts-ordered; 5 dropped as
 * re-swipes/slips (58 out-of-order 'cat' slip, 78 'poll' re-swipe, 89 stray
 * 'less' slip, 217+218 'cold' re-swipes — score the last attempt); the
 * single-letter words a/A/I were tapped, not swiped, so they have no
 * records. swipe_trails_25phrases_expected.tsv — recIdx, sentence number,
 * intended word, expected top-1. keyWidth/key centers come from EACH RECORD
 * (they describe the keyboard the trails were captured on), not the project
 * layout. Alignment methodology: TRAIL_EVAL_REPORT.md (uncommitted scratch).
 */
class SwipeCorpusGoldTest {

    private lateinit var decoder: SwipeDecoder

    private val dropped = setOf(58, 78, 89, 217, 218)

    @Before
    fun setUp() {
        decoder = SwipeDecoder(Dictionary.load(javaClass.getResourceAsStream("/words_en.txt")!!))
    }

    @Test
    fun goldCorpus() {
        val lines = javaClass.getResourceAsStream("/swipe_trails_25phrases.jsonl")!!
            .bufferedReader().readLines().filter { it.isNotBlank() }
        val expected = javaClass.getResourceAsStream("/swipe_trails_25phrases_expected.tsv")!!
            .bufferedReader().readLines().filter { it.isNotBlank() }.drop(1) // header
        val aligned = lines.indices.filter { it !in dropped }
        assertEquals("alignment broke: records vs expectations", expected.size, aligned.size)

        var correct = 0
        aligned.forEachIndexed { ai, recIdx ->
            val cols = expected[ai].split("\t")
            val (sent, intended, expectedTop1) = Triple(cols[1], cols[2], cols[3])
            val rec = JSONObject(lines[recIdx])
            val keyWidth = rec.getDouble("keyWidth").toFloat()
            val keysObj = rec.getJSONObject("keys")
            val keyCenters = keysObj.keys().asSequence().associate { k ->
                val xy = keysObj.getJSONArray(k)
                k.single() to Vec2(xy.getDouble(0).toFloat(), xy.getDouble(1).toFloat())
            }
            val trailArr = rec.getJSONArray("trail")
            val trail = (0 until trailArr.length()).map { j ->
                val p = trailArr.getJSONArray(j)
                TimedPoint(Vec2(p.getDouble(0).toFloat(), p.getDouble(1).toFloat()), p.getLong(2))
            }
            val results = decoder.decode(trail, keyCenters, keyWidth, topN = 3)
            val top1 = results.firstOrNull()?.word ?: ""
            assertEquals("rec $recIdx (sentence $sent, intended '$intended')", expectedTop1, top1)
            if (top1.equals(intended, ignoreCase = true)) correct++
        }

        val accuracy = 100.0 * correct / aligned.size
        println("GOLD CORPUS: $correct/${aligned.size} = %.2f%%".format(accuracy))
        assertTrue("gold corpus accuracy %.2f%% is below the 98%% target".format(accuracy),
            accuracy >= 98.0)
    }
}
