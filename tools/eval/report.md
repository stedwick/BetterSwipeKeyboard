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
| amazon/nova-micro-v1 | amazon/nova-micro-v1 | R | 42 | 25/42 (60%) | 2 | - | 0.6s | 2.1s | $0.0014 |
| amazon/nova-micro-v1 | amazon/nova-micro-v1 | I | 37 | 31/37 (84%) | 2 | 3/7 | 0.6s | 1.0s | $0.0010 |
| google/gemini-2.5-flash-lite | google/gemini-2.5-flash-lite | R | 37 | 24/37 (65%) | 2 | - | 1.6s | 7.3s | $0.0032 |
| google/gemini-2.5-flash-lite | google/gemini-2.5-flash-lite | I | 34 | 29/34 (85%) | 1 | 3/7 | 0.8s | 7.3s | $0.0025 |
| google/gemini-3.1-flash-lite | google/gemini-3.1-flash-lite | R | 15 | 9/15 (60%) | 0 | - | 1.1s | 1.4s | $0.0033 |
| google/gemini-3.1-flash-lite | google/gemini-3.1-flash-lite | I | 15 | 12/15 (80%) | 0 | 0/3 | 1.1s | 1.6s | $0.0028 |
| meta-llama/llama-3.1-8b-instruct | meta-llama/llama-3.1-8b-instruct | R | 41 | 19/41 (46%) | 0 | - | 0.7s | 1.7s | $0.0010 |
| meta-llama/llama-3.1-8b-instruct | meta-llama/llama-3.1-8b-instruct | I | 36 | 29/36 (81%) | 3 | 2/7 | 0.9s | 1.5s | $0.0007 |
| meta-llama/llama-3.2-3b-instruct | meta-llama/llama-3.2-3b-instruct | R | 40 | 17/40 (42%) | 0 | - | 0.4s | 0.9s | $0.0019 |
| meta-llama/llama-3.2-3b-instruct | meta-llama/llama-3.2-3b-instruct | I | 36 | 31/36 (86%) | 2 | 2/6 | 0.4s | 0.9s | $0.0015 |
| openai/gpt-4.1-nano | openai/gpt-4.1-nano | R | 42 | 25/42 (60%) | 1 | - | 1.1s | 2.3s | $0.0039 |
| openai/gpt-4.1-nano | openai/gpt-4.1-nano | I | 37 | 32/37 (86%) | 1 | 3/7 | 1.1s | 1.7s | $0.0030 |
| openai/gpt-5-nano | openai/gpt-5-nano | R | 32 | 21/32 (66%) | 3 | - | 27.4s | 55.4s | $0.0336 |
| openai/gpt-5-nano | openai/gpt-5-nano | I | 27 | 25/27 (93%) | 2 | 5/5 | 11.5s | 28.6s | $0.0107 |

App HTTP timeout is 15s; p95 must stay well under it.
Raw replies: results.jsonl (the table summarizes; the transcript is the evidence).
