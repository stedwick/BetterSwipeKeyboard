# AI proofreading — reference

Read this before touching `proofread/`, the prompts, the eval harness
(`tools/eval/`), or the auto-proofread scheduling in
`SwipeKeyboardService`. `AGENTS.md` carries the backend-selection rule,
the privacy invariants and the eval ship rule; this file carries the
mechanics.

## Backends

- `Proofreader` interface; `MlKitProofreader` (on-device Gemini Nano via
  `com.google.mlkit:genai-proofreading`, keyboard-tuned input type, kicks
  off model download itself) and `OpenRouterProofreader` (OkHttp +
  org.json, model `google/gemini-2.5-flash-lite` — restored 2026-08
  after `amazon/nova-micro-v1` proved unreliable in production;
  flash-lite posted the eval table's best intent-recovery, its known
  weakness is latency tails under provider congestion (tools/eval
  sweeps; few-shot prompt in `ProofreadPrompt`, temperature 0,
  zero-data-retention providers only, `provider.sort = "latency"` — the
  fastest ZDR endpoint wins over the default price-weighted choice).
- `selectBackend`: on-device wins when available; cloud when an API key
  is configured; otherwise none.
- OpenRouter API key stored in plain SharedPreferences by `ApiKeyStore`
  (personal app; not production-grade).

## Auto-proofread timing

Auto-proofread debounced 2 s after last *user activity*
(`SwipeKeyboardService.scheduleAutoProofread`): `KeyboardScreen` emits
`GestureStarted`/`GestureEnded` around every gesture (a swipe produces
no actions until finger-up); a touch cancels the timer; finger-up
reschedules only when the dirty flag says text changed since the last
attempt. Scheduling during a gesture is deferred to finger-up
(`gestureActive`). Whenever `proofreadAuto` is off, no pended debounce
job survives (`onKeyboardAction`). Two behaviors on top (reducer-owned
state, service-side timing):

- Tapping 3+ chars suspends auto-proofreading: `typedTapStreak` counts
  consecutive `InsertText`s; at `TAP_TYPING_DISABLE_THRESHOLD` (3)
  while `proofreadAuto` is on, it flips off and arms
  `proofreadSuspendedByTaps`. The next swipe (`CommitWord`) restores
  it; taps while the USER has it off never arm the flag; manual toggle
  clears it. Backspace/Enter/cursor moves don't break the streak; only
  `CommitWord` and `ToggleProofread` reset it.
- Toggling the AI key ON fires ONE immediate proofread (`runProofread`
  directly), with the pended debounce job cancelled FIRST; then the 2 s
  debounce resumes.

`SentenceExtractor.currentWindow`: current fragment *plus the previous
sentence* before the cursor (a continuation fragment merges back into a
sentence an earlier pass terminated). Never crosses the last newline —
text before it is neither analyzed nor editable, the newline can never
be removed, merging only within a paragraph. Whole window = input +
replacement span; result applied only if the user hasn't typed since.
Failures are logged and swallowed — never depend on the AI.

## Typed prompt — internals

- Typed prompt (rewritten on feature/proofread-rewrite, replacing the old
  five-step SYSTEM + 33 Philip-derived examples): a short job statement
  plus ~8 GENERIC invented few-shot pairs, one per mechanism (a word
  contradicting its crossed path, picking the intended word from the
  decoder's guesses instead of inventing a fluent unsupported one, path
  approximation, fragment merge, plain-typo repair, restraint
  identities). The examples are deliberately NOT derived from captured
  trails, test sentences or project incidents — the keyboard must work
  for anyone, not be tuned to one person's writing. A strengthened corpus
  guard in `ProofreadPromptTest` enforces this mechanically: no example
  may contain any captured sentence (all six sets, via
  `eval/CapturedSentences.kt` — single source shared with the eval corpus
  generator), any distinctive corpus/incident word (mummy, folic, wars,
  mice...), any incident word PAIR (star+east, nine+mice, his+hours...),
  or overlap the invented eval cases. SYSTEM states only what the model
  cannot infer: what swipe typing is, the annotation format, the
  window/merge mechanics, the reply protocol (applied VERBATIM into the
  text field — corrected text only; correct-or-unsure text returned
  unchanged — the echo guard and fail-soft bias depend on both),
  restraint (fix errors, never restyle), and EVIDENCE_RULE — the
  reasonable-mis-swipe constraint, held as a separate constant so the
  eval's arm E can remove exactly that sentence and test whether a
  stronger model still needs it. Two SYSTEM restraint clauses
  (telegraphic/casual phrasing is not an error; the writer's punctuation
  is preserved verbatim) are the survivors of the p-loop sweep (eval tags
  p0-p10) on the shipping model — the only two of ten measured changes
  that improved accuracy without regressions. Nova-micro quirks measured
  in the sweeps, relevant to any future prompt work on this model: it
  echoes freshly-added few-shot outputs into unrelated replies (twice
  observed), occasionally emits refusals (production is covered by
  `ReplySanity`), and its grammar priors (committee-have, that-vs-the,
  path-primacy on fluent words) are prompt-immovable — model limits, not
  prompt gaps.
- The merge behavior is taught only on the OpenRouter path (a generic
  few-shot example in `ProofreadPrompt.EXAMPLES`). The ML Kit API takes
  plain text only — no system prompt, no few-shot — so on-device merging
  is best-effort model behavior and parity between backends is not
  guaranteed.

## Prompt eval harness

`tools/eval/` — the quality gate for any prompt or model change, no merge
without its table. `./gradlew :app:generateEvalCorpus` rebuilds
`corpus.jsonl` — sub-corpus R (six fixture sets replayed through the
current decoder: real wrong commits, real crossed-letter annotations via
the shipped `withSwipePaths`) and sub-corpus I (~20 invented cases,
mechanism-labeled, disjoint from prompt examples). `eval_proofread.py`
(key from gitignored `tools/eval/.env`) runs arms — A: frozen shipping
prompt (`baseline_prompt.json`, main @ d1bdd26) + flash-lite; B: new
prompt + flash-lite; C/D: new prompt + flash/pro; E: new prompt minus
EVIDENCE_RULE + pro — after a ZDR pre-flight (no zero-retention endpoint
→ arms skipped; privacy outranks the benchmark). Scoring splits R/I
columns (winning only one = overfit), untouched-rate, per-class table,
p50/p95 latency vs the 15 s app timeout, real cost. Ship rule: beat A on
intent-recovery on BOTH sub-corpora, no untouched regression, p95 well
under timeout.

## Swipe-path annotation

- Mechanics: at commit `KeyboardScreen` attaches `crossedLetters` to
  `CommitWord` (caps applies to the word, never the letters); the service
  records it in `SwipedWordLog` (pure, in-memory, cap 100) with the
  action's `alternates` (RAW runner-ups, uncapped). Alignment is
  TEXT-ANCHORED — reconciled against `textBeforeCursor` at proofread
  time; every invalidation resolves to a safe drop.
- Full detail: at swipe-commit time `KeyboardScreen` attaches
  `crossedLetters` to `KeyboardAction.CommitWord` (the ViewModel passes
  it through; caps applies to the word, never the letters), and the
  service records it in `SwipedWordLog` (pure, in-memory, cap 100) —
  together with the action's `alternates` (the decoder's RAW runner-up
  words, uncapped; the caps-transformed copies in
  `KeyboardState.swipeAlternates` are for the strip). The keyboard never
  observes external edits, so alignment is TEXT-ANCHORED: at proofread
  time the log is reconciled against `textBeforeCursor` (whole-word,
  case-sensitive, commit order) and every invalidation — edited, deleted,
  retried or externally changed word — resolves to a safe drop. Matching
  words inside the window annotate the request as `(Swipe paths,
  approximate: word=path>alt1,alt2)` — `path` is the BARE concatenated
  crossed keys (`fog=dog`; the old prompt's few-shots showed a dotted
  `d·o·g` notation that production never sent — the rewrite's examples
  and SYSTEM use the real wire format; max 20 most recent; the `>alts`
  suffix is omitted when the decoder offered no score-gated runner-ups,
  and the committed word can never appear among its own guesses —
  `swipeAlternates` drops top-1), ONLY for the OpenRouter typed prompt —
  ML Kit and voice requests get plain text. The prompt teaches (generic
  examples + EVIDENCE_RULE) that a word disagreeing with its path is a
  likely error even if it fits its sentence, and that a replacement for a
  swiped word must be a plausible result of that same swipe: consistent
  with the path within normal mis-swipe tolerance (aim slip, a nearby
  key, an extra or missing letter at an end) OR one of the decoder's
  listed guesses (the decoder's own reasonable mis-swipe readings of the
  trail) — never a fluent word no reasonable swipe of that trail could
  produce (the historical 'Star East'→'Star Trek' failure class: 'wars'
  was in the decoder's guesses, 'trek' was invented). A reply echoing the
  marker is discarded by the echo guard, never applied.

## Debugging workflow

For AI complaints ("the proofreader broke/missed X"): replay the
session's captured trails (`swipe_trails*.jsonl`) through the decoder,
diff its commits against the post-proofread transcript. Every
discrepancy lands in a fault class: decoder-miss, proofreader-miss,
proofreader-damage, or window/merge-rule. Never tune prompt or decoder
from the transcript alone — attribution first.
