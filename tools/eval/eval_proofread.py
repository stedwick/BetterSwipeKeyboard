#!/usr/bin/env python3
"""Proofread eval runner (tools/eval/).

Runs corpus.jsonl (built by `./gradlew :app:generateEvalCorpus`) through the
OpenRouter chat API for every arm and scores the results:

- intent-recovery rate (reply == expected, trimmed), per arm, SPLIT by
  sub-corpus R (real decoded sentences) and I (invented) — the overfit
  check: a prompt that wins on only one column is overfit either way;
- untouched rate on cases whose input == expected (must not be modified);
- per-class pass/fail table;
- latency (wall, p50/p95) per arm vs the app's 15 s HTTP timeout;
- cost from OpenRouter usage accounting when present.

Usage:
    cp tools/eval/.env.template tools/eval/.env   # fill in the key
    python3 tools/eval/eval_proofread.py [--arms A,B,C] [--repeat-tag r2]

The API key lives in tools/eval/.env (gitignored — NEVER commit it) as
OPENROUTER_API_KEY=sk-or-... . Results append to results.jsonl (resumable:
case+arm+tag triples already present are skipped). Raw replies are kept in
results.jsonl — the report table summarizes, the transcript is the evidence.

Pre-flight: before spending, each arm's model gets one minimal request
carrying the app's privacy block (provider.zdr=true, data_collection=deny).
A model with no zero-data-retention endpoint FAILS LOUD here (OpenRouter
rejects the request) and its arms are skipped — the privacy contract
outranks the benchmark.
"""

import argparse
import json
import os
import ssl
import sys
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed

# CA bundle: python3.org's macOS Python does not use the system keychain, so
# urllib fails CERTIFICATE_VERIFY_FAILED there; certifi supplies the bundle.
try:
    import certifi

    SSL_CTX = ssl.create_default_context(cafile=certifi.where())
except ImportError:
    SSL_CTX = ssl.create_default_context()

HERE = os.path.dirname(os.path.abspath(__file__))
CORPUS = os.path.join(HERE, "corpus.jsonl")
RESULTS = os.path.join(HERE, "results.jsonl")
REPORT = os.path.join(HERE, "report.md")
ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

# The app's privacy contract (OpenRouterProofreader.buildRequestJson).
PROVIDER_BLOCK = {"zdr": True, "data_collection": "deny"}
APP_TIMEOUT_S = 15.0  # OpenRouterProofreader's OkHttp call timeout
REQUEST_TIMEOUT_S = 90  # generous ceiling for thinking models


def load_env(path):
    env = {}
    if os.path.isfile(path):
        for line in open(path):
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                env[k.strip()] = v.strip().strip('"').strip("'")
    return env


def post(api_key, payload):
    req = urllib.request.Request(
        ENDPOINT,
        data=json.dumps(payload).encode(),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    started = time.monotonic()
    try:
        with urllib.request.urlopen(req, timeout=REQUEST_TIMEOUT_S, context=SSL_CTX) as resp:
            body = resp.read().decode()
            return time.monotonic() - started, resp.status, body, None
    except urllib.error.HTTPError as e:
        return time.monotonic() - started, e.code, e.read().decode(errors="replace"), None
    except Exception as e:  # noqa: BLE001 - network failure is data, not a crash
        return time.monotonic() - started, None, "", str(e)


def preflight(api_key, models):
    """One minimal ZDR-filtered request per model; returns the OK set."""
    ok = set()
    for model in sorted(models):
        payload = {
            "model": model,
            "messages": [{"role": "user", "content": "Say ok."}],
            "temperature": 0,
            "provider": PROVIDER_BLOCK,
        }
        latency, status, body, err = post(api_key, payload)
        if status == 200:
            print(f"pre-flight {model}: OK ({latency:.1f}s, ZDR endpoint exists)")
            ok.add(model)
        else:
            detail = (err or body)[:300]
            print(f"pre-flight {model}: FAILED (HTTP {status}) — no compliant endpoint? {detail}")
            print(f"  -> arms using {model} will be SKIPPED (privacy contract outranks the benchmark)")
    return ok


def norm(s):
    return " ".join(s.split()).strip()


def classify(reply, expected, inp):
    """exact | near-miss | wrong; untouched handled by caller (input==expected)."""
    r, e = norm(reply), norm(expected)
    if r == e:
        return "exact"
    strip = lambda s: "".join(c for c in s.lower() if c.isalnum() or c.isspace())
    if strip(r) == strip(e):
        return "near-miss"
    return "wrong"


def percentile(values, p):
    if not values:
        return float("nan")
    values = sorted(values)
    k = max(0, min(len(values) - 1, round(p / 100 * (len(values) - 1))))
    return values[k]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--arms", default="A,B,C,D,E")
    ap.add_argument("--repeat-tag", default="r1", help="run tag; results dedupe on case+arm+tag")
    ap.add_argument("--cases", default=None, help="comma-separated case id filter (debug)")
    args = ap.parse_args()

    env = load_env(os.path.join(HERE, ".env"))
    api_key = env.get("OPENROUTER_API_KEY") or os.environ.get("OPENROUTER_API_KEY")
    if not api_key:
        sys.exit("no API key: put OPENROUTER_API_KEY=... in tools/eval/.env (gitignored)")

    arms = [a.strip() for a in args.arms.split(",") if a.strip()]
    cases = [json.loads(line) for line in open(CORPUS) if line.strip()]
    if args.cases:
        wanted = set(args.cases.split(","))
        cases = [c for c in cases if c["id"] in wanted]
    if not cases:
        sys.exit("empty corpus selection")

    models = {c["requests"][a]["model"] for c in cases for a in arms}
    ok_models = preflight(api_key, models)

    done = set()
    if os.path.isfile(RESULTS):
        for line in open(RESULTS):
            if line.strip():
                r = json.loads(line)
                done.add((r["id"], r["arm"], r["tag"]))

    out = open(RESULTS, "a")
    write_lock = threading.Lock()
    total = skipped = failed = 0

    # Work list built up front (resume-skip preserved), then fired
    # CONCURRENTLY — 245 sequential calls was the design flaw; the API is
    # happy with 16 in flight and the wall clock drops an order of
    # magnitude. Pre-flight already happened once per model above.
    work = []
    for case in cases:
        for arm in arms:
            if (case["id"], arm, args.repeat_tag) in done:
                skipped += 1
                continue
            req = case["requests"][arm]
            if req["model"] not in ok_models:
                continue
            work.append((case, arm, req))

    def run_one(case, arm, req):
        payload = {
            "model": req["model"],
            "messages": req["messages"],
            "temperature": 0,
            "provider": PROVIDER_BLOCK,
            "usage": {"include": True},
        }
        latency, status, body, err = post(api_key, payload)
        record = {
            "id": case["id"], "arm": arm, "tag": args.repeat_tag,
            "subcorpus": case["subcorpus"], "class": case["class"],
            "expected": case["expected"], "input": case["input"],
            "model": req["model"], "latency_s": round(latency, 3), "http": status,
        }
        call_failed = False
        if status == 200:
            try:
                parsed = json.loads(body)
                content = parsed["choices"][0]["message"].get("content")
                # Thinking models may return null content (reasoning lives
                # in a separate field) — a null reply is a failed call for
                # scoring purposes, not a crash.
                if isinstance(content, str) and content.strip():
                    record["reply"] = content.strip()
                    record["usage"] = parsed.get("usage", {})
                else:
                    record["error"] = "empty or null content in response"
                    record["usage"] = parsed.get("usage", {})
                    call_failed = True
            except (KeyError, json.JSONDecodeError) as e:
                record["error"] = f"bad response: {e}"
                # Keep the raw body snippet — a 200 without choices is
                # otherwise undebuggable after the fact.
                record["error_body"] = body[:500]
                call_failed = True
        else:
            record["error"] = (err or body)[:500]
            call_failed = True
        verdict = classify(record.get("reply", "\x00"), case["expected"], case["input"])
        with write_lock:
            out.write(json.dumps(record, ensure_ascii=False) + "\n")
            out.flush()
        return call_failed, f"{case['id']:>28} {arm} {status} {latency:5.1f}s {verdict}"

    started = time.monotonic()
    with ThreadPoolExecutor(max_workers=16) as pool:
        futures = [pool.submit(run_one, case, arm, req) for case, arm, req in work]
        for future in as_completed(futures):
            call_failed, line = future.result()
            failed += int(call_failed)
            total += 1
            print(line)
    out.close()
    print(
        f"done: {total} calls ({failed} failed), {skipped} skipped as already present, "
        f"wall {time.monotonic() - started:.1f}s for the pool"
    )
    write_report()


def write_report():
    if not os.path.isfile(RESULTS):
        return
    records = [json.loads(line) for line in open(RESULTS) if line.strip()]
    arms = sorted({r["arm"] for r in records})

    lines = ["# Proofread eval report", ""]
    lines.append("| arm | model | sub | n | intent-recovery | near-miss | untouched | p50 lat | p95 lat | cost |")
    lines.append("|-----|-------|-----|---|-----------------|-----------|-----------|---------|---------|------|")
    for arm in arms:
        for sub in ("R", "I"):
            rows = [r for r in records if r["arm"] == arm and r["subcorpus"] == sub and "reply" in r]
            if not rows:
                continue
            exact = sum(1 for r in rows if classify(r["reply"], r["expected"], r["input"]) == "exact")
            near = sum(1 for r in rows if classify(r["reply"], r["expected"], r["input"]) == "near-miss")
            unt = [r for r in rows if norm(r["input"]) == norm(r["expected"])]
            untouched = (
                f"{sum(1 for r in unt if norm(r['reply']) == norm(r['expected']))}/{len(unt)}"
                if unt else "-"
            )
            lats = [r["latency_s"] for r in rows]
            cost = sum(r.get("usage", {}).get("cost", 0) or 0 for r in rows)
            lines.append(
                f"| {arm} | {rows[0]['model']} | {sub} | {len(rows)} | "
                f"{exact}/{len(rows)} ({100 * exact / len(rows):.0f}%) | {near} | {untouched} | "
                f"{percentile(lats, 50):.1f}s | {percentile(lats, 95):.1f}s | ${cost:.4f} |"
            )
    lines.append("")
    lines.append(f"App HTTP timeout is {APP_TIMEOUT_S:.0f}s; p95 must stay well under it.")
    lines.append("Raw replies: results.jsonl (the table summarizes; the transcript is the evidence).")
    with open(REPORT, "w") as f:
        f.write("\n".join(lines) + "\n")
    print(f"wrote {REPORT}")


if __name__ == "__main__":
    main()
