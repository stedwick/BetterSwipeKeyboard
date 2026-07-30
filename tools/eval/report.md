# Proofread eval report

| arm | model | sub | n | intent-recovery | near-miss | untouched | p50 lat | p95 lat | cost |
|-----|-------|-----|---|-----------------|-----------|-----------|---------|---------|------|
| A | google/gemini-2.5-flash-lite | R | 27 | 20/27 (74%) | 2 | - | 4.2s | 17.6s | $0.0065 |
| A | google/gemini-2.5-flash-lite | I | 22 | 21/22 (95%) | 1 | 4/4 | 0.7s | 6.9s | $0.0037 |
| B | google/gemini-2.5-flash-lite | R | 27 | 18/27 (67%) | 2 | - | 1.0s | 11.5s | $0.0023 |
| B | google/gemini-2.5-flash-lite | I | 22 | 20/22 (91%) | 1 | 3/4 | 1.5s | 7.5s | $0.0016 |
| C | google/gemini-2.5-flash | R | 27 | 18/27 (67%) | 2 | - | 3.9s | 10.6s | $0.0073 |
| C | google/gemini-2.5-flash | I | 22 | 19/22 (86%) | 2 | 3/4 | 4.1s | 9.2s | $0.0051 |
| D | google/gemini-2.5-pro | R | 27 | 16/27 (59%) | 2 | - | 19.6s | 91.5s | $0.7188 |
| D | google/gemini-2.5-pro | I | 22 | 20/22 (91%) | 2 | 4/4 | 4.9s | 10.4s | $0.1085 |
| E | google/gemini-2.5-pro | R | 27 | 18/27 (67%) | 2 | - | 11.6s | 94.7s | $0.7356 |
| E | google/gemini-2.5-pro | I | 22 | 20/22 (91%) | 2 | 4/4 | 4.9s | 6.5s | $0.1243 |
| amazon/nova-lite-v1 | amazon/nova-lite-v1 | R | 3 | 1/3 (33%) | 0 | - | 0.8s | 0.9s | $0.0002 |
| amazon/nova-lite-v1 | amazon/nova-lite-v1 | I | 3 | 2/3 (67%) | 0 | 0/1 | 1.0s | 2.9s | $0.0001 |
| amazon/nova-micro-v1 | amazon/nova-micro-v1 | R | 990 | 655/990 (66%) | 33 | - | 0.8s | 10.4s | $0.0425 |
| amazon/nova-micro-v1 | amazon/nova-micro-v1 | I | 810 | 690/810 (85%) | 79 | 99/148 | 0.9s | 10.2s | $0.0302 |
| google/gemini-2.5-flash-lite | google/gemini-2.5-flash-lite | R | 37 | 24/37 (65%) | 2 | - | 1.6s | 7.3s | $0.0032 |
| google/gemini-2.5-flash-lite | google/gemini-2.5-flash-lite | I | 34 | 29/34 (85%) | 1 | 3/7 | 0.8s | 7.3s | $0.0025 |
| google/gemini-3.1-flash-lite | google/gemini-3.1-flash-lite | R | 16 | 10/16 (62%) | 0 | - | 1.1s | 1.4s | $0.0035 |
| google/gemini-3.1-flash-lite | google/gemini-3.1-flash-lite | I | 15 | 12/15 (80%) | 0 | 0/3 | 1.1s | 1.6s | $0.0028 |
| google/gemma-3-4b-it | google/gemma-3-4b-it | R | 1 | 0/1 (0%) | 0 | - | 1.0s | 1.0s | $0.0000 |
| meta-llama/llama-3.1-8b-instruct | meta-llama/llama-3.1-8b-instruct | R | 44 | 20/44 (45%) | 0 | - | 0.7s | 1.7s | $0.0011 |
| meta-llama/llama-3.1-8b-instruct | meta-llama/llama-3.1-8b-instruct | I | 39 | 31/39 (79%) | 3 | 2/8 | 0.9s | 2.0s | $0.0008 |
| meta-llama/llama-3.2-3b-instruct | meta-llama/llama-3.2-3b-instruct | R | 43 | 18/43 (42%) | 0 | - | 0.4s | 0.9s | $0.0021 |
| meta-llama/llama-3.2-3b-instruct | meta-llama/llama-3.2-3b-instruct | I | 39 | 33/39 (85%) | 2 | 2/7 | 0.4s | 0.9s | $0.0017 |
| openai/gpt-4.1-nano | openai/gpt-4.1-nano | R | 43 | 26/43 (60%) | 1 | - | 1.2s | 2.3s | $0.0040 |
| openai/gpt-4.1-nano | openai/gpt-4.1-nano | I | 37 | 32/37 (86%) | 1 | 3/7 | 1.1s | 1.7s | $0.0030 |
| openai/gpt-4o-mini | openai/gpt-4o-mini | R | 1 | 1/1 (100%) | 0 | - | 2.2s | 2.2s | $0.0001 |
| openai/gpt-5-nano | openai/gpt-5-nano | R | 32 | 21/32 (66%) | 3 | - | 27.4s | 55.4s | $0.0336 |
| openai/gpt-5-nano | openai/gpt-5-nano | I | 27 | 25/27 (93%) | 2 | 5/5 | 11.5s | 28.6s | $0.0107 |

App HTTP timeout is 15s; p95 must stay well under it.
Raw replies: results.jsonl (the table summarizes; the transcript is the evidence).

---

## p-loops: new-prompt ceiling on nova-micro (final)

Baseline p0 (= shipped 4681f36, re-measured): R 17/27, I 18/22, unt 18/24.
Same discipline as the m-loops but corpus guard fully in force (generic invented
examples only); all 10 loops run regardless of verdicts, per Philip.

| tag | change | R | I | unt | verdict |
|-----|--------|---|---|-----|---------|
| p0 | shipped n10 prompt | 17/27 | 18/22 | 18/24 | baseline |
| p1 | SYSTEM: alts never a reason to alter a correct word | 16/27 | 19/22 | 19/24 | REVERTED (R -1) |
| p2 | +store=story path-over-alts example | 17/27 | 19/22 | 20/24 | REVERTED (R flat) |
| p3 | SYSTEM: telegraphic/casual phrasing is not an error | 18+20/27 | 18/22 | 20+21/24 | KEPT (double-run) |
| p4 | +store=story example on p3 base | 18/27 | 19/22 | 21/24 | REVERTED (R in-band) |
| p5 | +polished-input But-merge example | 18/27 | 19/22 | 20/24 | REVERTED (fixed i-d2, but echoed the new example into s5's reply) |
| p6 | +question-with-period identity example | 19/27 | 19/22 | 20/24 | REVERTED (R in-band; fixed i-c1) |
| p7 | +British-plural identity example | 17/27 | 19/22 | 20/24 | REVERTED (R -1, i-e3 still failed) |
| p8 | +telegraphic run-on identity example | 18/27 | 18/22 | 19/24 | REVERTED (unt -1; second example-echo leak) |
| p9 | SYSTEM: punctuation preserved verbatim (period stays period, no new commas; carve-outs for final period + fragment comma) | 20+19/27 | 19+18/22 | 22+21/24 | KEPT (double-run; both runs dominate p3 state) |
| p10 | +question-with-period example on p9 base | 19/27 | 19/22 | 20/24 | REVERTED (unt -1; i-c1 unfixed; s5 drew a refusal) |

Final state = p3+p9 (tools/eval/p_prompt.json).

### Ceiling comparison (nova-micro, 49-case corpus, temperature 0)

| prompt | R | I | unt |
|--------|---|---|-----|
| new generic p3+p9 (ceiling) | 19-20/27 | 18-19/22 | 21-22/24 |
| old + m1/m2 (ceiling) | 19/27 | 19/22 | 22/24 |
| new generic shipped (p0) | 17/27 | 18/22 | 18/24 |

The gap CLOSED: the p-loops lifted the generic prompt from 17/18/18 to statistical
parity with the old prompt's ceiling on all three axes, with corpus-clean generic
examples. Notable model findings: nova-micro echoes freshly-added example outputs
into unrelated replies (p5, p8), occasionally emits refusals (p10 s5 - production
is covered by the ReplySanity guard), and its grammar priors (committee-have,
that-vs-the, path-primacy on fluent words) are prompt-immovable.
