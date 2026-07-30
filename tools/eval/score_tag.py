#!/usr/bin/env python3
"""Score one or more eval tags for the nova prompt-iteration loops.

Accuracy basis: ALL replies (latency is not being measured in these loops).
A call with an error/no reply counts as a failure in the denominator.
Untouched = identity cases (typed text == expected) replied exactly.

Usage: python3 tools/eval/score_tag.py n0 [n1 ...] [--model MODEL]
"""
import json
import sys
from collections import defaultdict

RESULTS = "tools/eval/results.jsonl"


def typed(inp):
    return inp.split("\n(Swipe paths")[0]


def norm(s):
    return " ".join(s.split()).strip()


def exact(reply, expected):
    return norm(reply) == norm(expected)


def score(records, model=None):
    if model:
        records = [r for r in records if r["model"] == model]
    out = {}
    for sub in ("R", "I"):
        rows = [r for r in records if r["subcorpus"] == sub]
        ex = sum(1 for r in rows if "reply" in r and exact(r["reply"], r["expected"]))
        out[sub] = (ex, len(rows))
    ident = [r for r in records if norm(typed(r["input"])) == norm(r["expected"])]
    un = sum(1 for r in ident if "reply" in r and exact(r["reply"], r["expected"]))
    out["unt"] = (un, len(ident))
    # per-class failure listing
    fails = defaultdict(list)
    for r in records:
        if "reply" not in r or not exact(r.get("reply", ""), r["expected"]):
            fails[r["class"]].append(r)
    out["fails"] = fails
    return out


def main():
    tags = [a for a in sys.argv[1:] if not a.startswith("--")]
    model = None
    if "--model" in sys.argv:
        model = sys.argv[sys.argv.index("--model") + 1]
    records = [json.loads(l) for l in open(RESULTS) if l.strip()]
    for tag in tags:
        rows = [r for r in records if r["tag"] == tag]
        s = score(rows, model)
        r, i, u = s["R"], s["I"], s["unt"]
        print(f"{tag}: n={len(rows)}  R {r[0]}/{r[1]} ({100*r[0]/max(1,r[1]):.0f}%)  "
              f"I {i[0]}/{i[1]} ({100*i[0]/max(1,i[1]):.0f}%)  unt {u[0]}/{u[1]}")
        for cls, frs in sorted(s["fails"].items()):
            print(f"  {cls} ({len(frs)}):")
            for r_ in frs:
                rep = r_.get("reply", "<error: " + r_.get("error", "?")[:60] + ">")
                print(f"    [{r_['id']}] got {rep[:90]!r}")
                print(f"    {' ' * len(r_['id'])  } exp {r_['expected'][:90]!r}")


if __name__ == "__main__":
    main()
