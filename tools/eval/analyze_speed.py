#!/usr/bin/env python3
"""Per-model speed-sweep table from results.jsonl rows tagged r3/r4/r5."""
import json
import sys
from collections import defaultdict

RESULTS = "tools/eval/results.jsonl"
TAGS = ("r3", "r4", "r5")


def norm(s):
    return " ".join(s.split()).strip()


def classify(reply, expected):
    r, e = norm(reply), norm(expected)
    if r == e:
        return "exact"
    strip = lambda s: "".join(c for c in s.lower() if c.isalnum() or c.isspace())
    return "near-miss" if strip(r) == strip(e) else "wrong"


def pctl(values, p):
    if not values:
        return float("nan")
    values = sorted(values)
    return values[max(0, min(len(values) - 1, round(p / 100 * (len(values) - 1))))]


def table(records, label):
    models = sorted({r["model"] for r in records})
    print(f"\n=== {label} ===")
    hdr = f"{'model':<38} {'pass':>5} {'p50':>6} {'max':>6} {'acc R':>7} {'acc I':>7} {'unt':>6} {'cost/10':>9}"
    print(hdr)
    for m in models:
        rows = [r for r in records if r["model"] == m]
        n = len(rows)
        # passing = has a reply, no error, not slow
        passed = [r for r in rows if "reply" in r and "error" not in r and not r.get("slow")]
        lats = [r["latency_s"] for r in rows]
        rR = [r for r in passed if r["subcorpus"] == "R"]
        rI = [r for r in passed if r["subcorpus"] == "I"]
        def acc(rs):
            if not rs:
                return "-"
            ex = sum(1 for r in rs if classify(r["reply"], r["expected"]) == "exact")
            return f"{ex}/{len(rs)}"
        unt = [r for r in passed if norm(r["input"]) == norm(r["expected"])]
        untr = "-"
        if unt:
            kept = sum(1 for r in unt if classify(r["reply"], r["expected"]) == "exact")
            untr = f"{kept}/{len(unt)}"
        cost = sum(r.get("usage", {}).get("cost", 0) or 0 for r in rows)
        print(f"{m:<38} {len(passed):>2}/{n:<2} {pctl(lats, 50):>5.2f}s {max(lats):>5.2f}s "
              f"{acc(rR):>7} {acc(rI):>7} {untr:>6} ${cost:>8.5f}")
    # near-miss detail
    nm = [(r["model"], r["id"], r["reply"]) for r in records
          if "reply" in r and classify(r["reply"], r["expected"]) == "near-miss"]
    if nm:
        print("near-misses:")
        for m, i, rep in nm:
            print(f"  {m} {i}: {rep!r}")
    # interesting wrong replies
    wr = [(r["model"], r["id"], r["class"], r["input"], r["expected"], r["reply"])
          for r in records if "reply" in r and classify(r["reply"], r["expected"]) == "wrong"]
    if wr:
        print("wrong replies:")
        for m, i, c, inp, exp, rep in wr:
            print(f"  {m} [{i} {c}]\n    in:  {inp}\n    exp: {exp}\n    got: {rep}")


def main():
    tags = sys.argv[1:] or list(TAGS)
    records = [json.loads(l) for l in open(RESULTS) if l.strip()]
    for tag in tags:
        rows = [r for r in records if r["tag"] == tag]
        table(rows, f"tag {tag}: {len(rows)} rows")
    allrows = [r for r in records if r["tag"] in tags]
    table(allrows, f"AGGREGATE {tags}: {len(allrows)} rows")


if __name__ == "__main__":
    main()
