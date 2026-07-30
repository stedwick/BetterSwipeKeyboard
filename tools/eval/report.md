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
| amazon/nova-micro-v1 | amazon/nova-micro-v1 | R | 126 | 76/126 (60%) | 7 | - | 0.8s | 6.5s | $0.0053 |
| amazon/nova-micro-v1 | amazon/nova-micro-v1 | I | 106 | 90/106 (85%) | 8 | 12/20 | 0.7s | 5.1s | $0.0038 |
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
