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

App HTTP timeout is 15s; p95 must stay well under it.
Raw replies: results.jsonl (the table summarizes; the transcript is the evidence).
