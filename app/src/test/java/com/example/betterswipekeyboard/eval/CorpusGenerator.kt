package com.example.betterswipekeyboard.eval

import com.example.betterswipekeyboard.proofread.ProofreadPrompt
import com.example.betterswipekeyboard.proofread.SwipedWordLog
import com.example.betterswipekeyboard.swipe.Dictionary
import com.example.betterswipekeyboard.swipe.MAX_COMMIT_SCORE
import com.example.betterswipekeyboard.swipe.SwipeDecoder
import com.example.betterswipekeyboard.swipe.TimedPoint
import com.example.betterswipekeyboard.swipe.Vec2
import com.example.betterswipekeyboard.swipe.crossedLetters
import com.example.betterswipekeyboard.swipe.swipeAlternates
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Proofread-eval corpus generator (tools/eval/corpus.jsonl). Offline — no
 * API calls. Two sub-corpora, because generalization is the claim under
 * test (a prompt that wins on only one is overfit either way):
 *
 * - R (real): the six captured trail sets replayed through the CURRENT
 *   decoder, reconstructed into the sentences Philip actually swiped
 *   ([CAPTURED_SENTENCES]). The text is what the decoder really committed
 *   (wrong where it missed — real mis-swipes), the annotation is built
 *   exactly as the service builds it ([ProofreadPrompt.withSwipePaths]
 *   over crossedLetters + swipeAlternates of the real decode). Sentences
 *   containing a SILENT swipe (nothing committed — in production the user
 *   retries, so the state is unrealistic) are skipped and logged.
 *   Sentences that decoded fully correct are emitted CAPITALIZED with
 *   expected == input (untouched-rate cases: a later auto-proofread pass
 *   sees already-repaired text and must leave it alone); broken sentences
 *   stay lowercase, so caps repair is part of the expected fix.
 * - I (invented): hand-authored cases from tools/eval/invented_cases.jsonl
 *   (mechanism-labeled, disjoint from the prompt's generic examples —
 *   asserted by the corpus guard test).
 *
 * Each corpus line carries fully-built `messages` arrays per arm, built by
 * the SHIPPED prompt code ([ProofreadPrompt.buildRequestJson]) for the new
 * prompt and by the frozen shipping prompt (tools/eval/baseline_prompt.json,
 * captured at main @ d1bdd26) for the baseline — the runner just ships
 * them, so the eval measures the real prompt code, not a paraphrase.
 *
 * Arms: A = old prompt + flash-lite (shipping baseline); B = new prompt +
 * flash-lite (isolates the prompt variable); C = new prompt + flash;
 * D = new prompt + pro; E = new prompt minus EVIDENCE_RULE + pro (is the
 * reasonable-mis-swipe sentence still needed by a strong model?).
 *
 * Run: ./gradlew :app:generateEvalCorpus
 */
fun main() {
    val outFile = File("tools/eval/corpus.jsonl")
    val baselineFile = File("tools/eval/baseline_prompt.json")
    val inventedFile = File("tools/eval/invented_cases.jsonl")
    require(baselineFile.isFile) { "missing $baselineFile — freeze the baseline prompt first" }
    require(inventedFile.isFile) { "missing $inventedFile" }

    val decoder = SwipeDecoder(
        Dictionary.load(
            checkNotNull(object {}.javaClass.getResourceAsStream("/words_en.txt")) {
                "words_en.txt not on the test classpath"
            },
        ),
    )
    val baseline = JSONObject(baselineFile.readText())

    val cases = mutableListOf<JSONObject>()
    val skipped = mutableListOf<String>()

    // ---- Sub-corpus R: real decoded sentences ------------------------------
    val trailCache = mutableMapOf<String, List<TrailRecord>>()
    val intentsCache = mutableMapOf<String, Map<Int, String>>()
    for (sentence in CAPTURED_SENTENCES) {
        val trails = trailCache.getOrPut(sentence.set) { loadTrails(sentence.set) }
        val intents = intentsCache.getOrPut(sentence.set) { loadIntents(sentence.set) }

        val committedWords = mutableListOf<String>()
        val entries = mutableListOf<SwipedWordLog.Entry>()
        val misses = mutableListOf<String>()
        var silent = false
        sentence.trailIndices.forEachIndexed { wordPos, trailIndex ->
            val intent = intents.getValue(trailIndex)
            check(intent == sentence.intentWords[wordPos]) {
                "${sentence.set}#${trailIndex}: table drift — TSV says '$intent', " +
                    "CapturedSentences says '${sentence.intentWords[wordPos]}'"
            }
            val rec = trails[trailIndex]
            val results = decoder.decode(rec.trail, rec.keyCenters, rec.keyWidth, topN = 5)
            val top = results.firstOrNull()
            val committed = top?.takeIf { it.score < MAX_COMMIT_SCORE }?.word
            if (committed == null) {
                silent = true
                return@forEachIndexed
            }
            committedWords += committed
            entries += SwipedWordLog.Entry(
                committed,
                crossedLetters(rec.trail.map { it.position }, rec.keyCenters),
                swipeAlternates(results),
            )
            if (committed != intent) misses += "$intent->$committed"
        }
        val id = "${sentence.set.removePrefix("swipe_").removeSuffix("_philip")}:${sentence.label}"
        if (silent) {
            skipped += "$id (a swipe committed nothing — unrealistic user state, " +
                "in production the user retries)"
            continue
        }

        // Tapped insertions join the text with no annotation entry.
        val textWords = committedWords.toMutableList()
        sentence.tappedAfter.toSortedMap().entries.reversed().forEach { (pos, w) ->
            textWords.add(pos + 1, w)
        }
        val isControl = misses.isEmpty()
        val (text, expected, annotatedEntries) = if (isControl) {
            // Untouched-rate case: present as an already-repaired later pass
            // would see it — capitalized, final period; expected == input.
            val t = textWords.joinToString(" ").capitalizeFirst() + "."
            val e = entries.mapIndexed { i, entry ->
                if (i == 0) entry.copy(word = entry.word.capitalizeFirst()) else entry
            }
            Triple(t, t, e)
        } else {
            val t = textWords.joinToString(" ")
            Triple(t, sentence.intentText().capitalizeFirst() + ".", entries)
        }
        cases += buildCase(
            id = "r-$id",
            subcorpus = "R",
            clazz = if (isControl) "control" else "real-miss",
            notes = if (isControl) "" else misses.joinToString(", "),
            text = text,
            expected = expected,
            entries = annotatedEntries,
            baseline = baseline,
        )
    }

    // ---- Sub-corpus I: invented cases --------------------------------------
    inventedFile.readLines().filter { it.isNotBlank() }.forEach { line ->
        val raw = JSONObject(line)
        val annotation = raw.optJSONArray("annotation") ?: JSONArray()
        val entries = (0 until annotation.length()).map { i ->
            val e = annotation.getJSONObject(i)
            val alts = e.optJSONArray("alternates") ?: JSONArray()
            SwipedWordLog.Entry(
                e.getString("word"),
                e.getString("letters"),
                (0 until alts.length()).map { alts.getString(it) },
            )
        }
        cases += buildCase(
            id = raw.getString("id"),
            subcorpus = "I",
            clazz = raw.getString("mechanism"),
            notes = raw.optString("notes", ""),
            text = raw.getString("text"),
            expected = raw.getString("expected"),
            entries = entries,
            baseline = baseline,
        )
    }

    outFile.parentFile.mkdirs()
    outFile.writeText(cases.joinToString("\n") { it.toString() } + "\n")

    val rCount = cases.count { it.getString("subcorpus") == "R" }
    val iCount = cases.size - rCount
    println("wrote ${outFile.path}: $rCount R cases + $iCount I cases = ${cases.size} total")
    skipped.forEach { println("skipped $it") }
}

// ---------------------------------------------------------------------------

private data class TrailRecord(
    val keyWidth: Float,
    val keyCenters: Map<Char, Vec2>,
    val trail: List<TimedPoint>,
)

private fun loadTrails(resourceBase: String): List<TrailRecord> {
    val stream = checkNotNull(object {}.javaClass.getResourceAsStream("/$resourceBase.jsonl"))
    return stream.bufferedReader().readLines().filter { it.isNotBlank() }.map { line ->
        val rec = JSONObject(line)
        val keysObj = rec.getJSONObject("keys")
        TrailRecord(
            keyWidth = rec.getDouble("keyWidth").toFloat(),
            keyCenters = keysObj.keys().asSequence().associate { k ->
                val xy = keysObj.getJSONArray(k)
                k.single() to Vec2(xy.getDouble(0).toFloat(), xy.getDouble(1).toFloat())
            },
            trail = rec.getJSONArray("trail").let { arr ->
                (0 until arr.length()).map { j ->
                    val p = arr.getJSONArray(j)
                    TimedPoint(
                        Vec2(p.getDouble(0).toFloat(), p.getDouble(1).toFloat()),
                        p.getLong(2),
                    )
                }
            },
        )
    }
}

private fun loadIntents(resourceBase: String): Map<Int, String> =
    checkNotNull(object {}.javaClass.getResourceAsStream("/$resourceBase.intents.tsv"))
        .bufferedReader().readLines()
        .map { it.split('\t').let { cols -> cols[0].toInt() to cols[1] } }.toMap()

private fun String.capitalizeFirst(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

/** The arm grid: model per arm, prompt variant per arm. */
private val ARM_MODELS = linkedMapOf(
    "A" to "google/gemini-2.5-flash-lite", // old prompt (baseline)
    "B" to "google/gemini-2.5-flash-lite", // new prompt — isolates the prompt variable
    "C" to "google/gemini-2.5-flash", // new prompt
    "D" to "google/gemini-2.5-pro", // new prompt
    "E" to "google/gemini-2.5-pro", // new prompt minus EVIDENCE_RULE
)

private fun buildCase(
    id: String,
    subcorpus: String,
    clazz: String,
    notes: String,
    text: String,
    expected: String,
    entries: List<SwipedWordLog.Entry>,
    baseline: JSONObject,
): JSONObject {
    val input = ProofreadPrompt.withSwipePaths(text, entries)

    // Arm A: the frozen shipping prompt, verbatim (system + 33 few-shot
    // pairs) — the eval measures what users actually got.
    val oldMessages = JSONArray()
    oldMessages.put(JSONObject().put("role", "system").put("content", baseline.getString("system")))
    val baselineExamples = baseline.getJSONArray("examples")
    for (i in 0 until baselineExamples.length()) {
        val pair = baselineExamples.getJSONArray(i)
        oldMessages.put(JSONObject().put("role", "user").put("content", pair.getString(0)))
        oldMessages.put(JSONObject().put("role", "assistant").put("content", pair.getString(1)))
    }
    oldMessages.put(JSONObject().put("role", "user").put("content", input))

    // Arms B/C/D: the shipped NEW prompt, built by the shipped code.
    val newMessages = JSONObject(ProofreadPrompt.buildRequestJson("m", input)).getJSONArray("messages")

    // Arm E: the new prompt minus the reasonable-mis-swipe rule — the rule
    // must be verbatim-removable (ProofreadPromptTest guards the contract).
    val stripped = ProofreadPrompt.SYSTEM.replace(ProofreadPrompt.EVIDENCE_RULE, "")
    check(stripped != ProofreadPrompt.SYSTEM) { "EVIDENCE_RULE no longer verbatim in SYSTEM" }
    val noRuleMessages = JSONArray()
    noRuleMessages.put(JSONObject().put("role", "system").put("content", stripped))
    for (i in 1 until newMessages.length()) {
        noRuleMessages.put(newMessages.getJSONObject(i))
    }

    val requests = JSONObject()
    requests.put("A", JSONObject().put("model", ARM_MODELS["A"]).put("messages", oldMessages))
    requests.put("B", JSONObject().put("model", ARM_MODELS["B"]).put("messages", newMessages))
    requests.put("C", JSONObject().put("model", ARM_MODELS["C"]).put("messages", newMessages))
    requests.put("D", JSONObject().put("model", ARM_MODELS["D"]).put("messages", newMessages))
    requests.put("E", JSONObject().put("model", ARM_MODELS["E"]).put("messages", noRuleMessages))

    return JSONObject()
        .put("id", id)
        .put("subcorpus", subcorpus)
        .put("class", clazz)
        .put("notes", notes)
        .put("input", input)
        .put("expected", expected)
        .put("requests", requests)
}
