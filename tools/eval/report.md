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
| amazon/nova-micro-v1 | amazon/nova-micro-v1 | R | 27 | 16/27 (59%) | 2 | - | 0.7s | 2.1s | $0.0009 |
| amazon/nova-micro-v1 | amazon/nova-micro-v1 | I | 22 | 19/22 (86%) | 2 | 3/4 | 0.6s | 2.8s | $0.0006 |
| google/gemini-2.5-flash-lite | google/gemini-2.5-flash-lite | R | 27 | 18/27 (67%) | 2 | - | 2.2s | 7.6s | $0.0023 |
| google/gemini-2.5-flash-lite | google/gemini-2.5-flash-lite | I | 22 | 20/22 (91%) | 1 | 3/4 | 2.4s | 7.3s | $0.0016 |
| meta-llama/llama-3.1-8b-instruct | meta-llama/llama-3.1-8b-instruct | R | 27 | 13/27 (48%) | 0 | - | 0.8s | 1.7s | $0.0005 |
| meta-llama/llama-3.1-8b-instruct | meta-llama/llama-3.1-8b-instruct | I | 22 | 18/22 (82%) | 3 | 2/4 | 1.0s | 1.5s | $0.0004 |
| meta-llama/llama-3.2-3b-instruct | meta-llama/llama-3.2-3b-instruct | R | 27 | 12/27 (44%) | 0 | - | 0.3s | 0.9s | $0.0013 |
| meta-llama/llama-3.2-3b-instruct | meta-llama/llama-3.2-3b-instruct | I | 22 | 19/22 (86%) | 2 | 2/4 | 0.3s | 1.3s | $0.0009 |
| openai/gpt-4.1-nano | openai/gpt-4.1-nano | R | 27 | 16/27 (59%) | 1 | - | 1.1s | 2.3s | $0.0025 |
| openai/gpt-4.1-nano | openai/gpt-4.1-nano | I | 22 | 20/22 (91%) | 1 | 3/4 | 1.1s | 1.6s | $0.0018 |
| openai/gpt-5-nano | openai/gpt-5-nano | R | 27 | 18/27 (67%) | 3 | - | 25.1s | 55.4s | $0.0272 |
| openai/gpt-5-nano | openai/gpt-5-nano | I | 22 | 20/22 (91%) | 2 | 4/4 | 11.5s | 26.9s | $0.0083 |

App HTTP timeout is 15s; p95 must stay well under it.
Raw replies: results.jsonl (the table summarizes; the transcript is the evidence).
