#!/usr/bin/env python3
"""Generates app/src/main/assets/words_en.txt, the swipe decoder's
frequency-ordered dictionary (`word<TAB>rank` lines, lower rank = more
frequent — the decoder's frequency bonus is a function of rank/maxRank).

Sources:
  - wordfreq v3 (https://github.com/rspeer/wordfreq) top-60000 English
    list — a ~2021 multi-corpus snapshot (Wikipedia, OpenSubtitles 2018,
    SUBTLEX, Google Books Ngrams, OSCAR web, Twitter, Reddit), so it
    covers modern tech terms, brands, names and slang that the old
    google-10000 (2006 web n-grams) list lacked. Data license:
    CC BY-SA 4.0 — see the NOTICE file in the repository root; keep the
    attribution header in the generated file intact.
  - SCOWL 2018.04.16 word lists (http://wordlist.aspell.net, MIT-like
    license), used as the standard-lexicon cross-check for the junk
    filters below. Fetched from the rdeits/SCOWL-mirror GitHub mirror
    into a temp-dir cache; pass a directory as the 2nd argument to use
    pre-downloaded files instead (same basenames, e.g.
    english-words.10.txt, latin-1 converted to UTF-8, CR stripped).

Pipeline:
  1. top_n_list('en', 60000), filtered to ^[a-z]{2,}$ (the decoder only
     has a-z keys; 2-letter words are kept because it admits them on very
     short trails, single letters/digits/contractions are useless).
  2. Vowel-less tokens of 3+ letters are dropped ("pwr", "thx", "njpw",
     "msg"-class Twitter/texting abbreviations): they otherwise become
     short-subsequence candidates that beat the intended word on swipe
     trails (empirically, "pwr" out-scored "power" on the p-o-w-e-r
     trail). Words with 'y' as their vowel stay ("sky", "gym", "rhythm").
     Known collateral: typed initialisms go too ("html", "pdf", "php",
     "xml", "rss", "dvd", "nfl") — accepted, they are tapped, not swiped;
     any the user wants back belong in SUPPLEMENT below.
  3. Junk that steals swipes is dropped by WORD CLASS (never by rank —
     keepers like "pizzas" sit at the same ranks as junk like "wick"):
     a. RARE PROPER NAMES: SCOWL proper-names list members that are not
        also dictionary words and are individually rare (zipf < 2.8).
        wordfreq folds case, so "Brien"/"Iver"/"Vey" leak in as
        lowercase tokens from film credits and out-score "brown"/"over"/
        "very" on real trails. Common names stay: "maria"/"jose" are
        dictionary words, "niko"/"siri"/"alexa" are above the floor.
     b. NONCE RESPELLINGS: non-dictionary tokens one letter SUBSTITUTION
        away from a ≥100x more frequent dictionary word (zipf gap ≥ 2.0),
        below zipf 3.1 ("krazy"->"crazy", "definately"->"definitely",
        "seperate"->"separate"). Substitution-only is deliberate:
        insertions/deletions conflate word formation with misspelling
        ("json"->"son", "cron"->"iron" would die, "andy"->"and" too).
     c. KEEP_EXCEPTIONS: a small hand-audited list of modern
        brands/terms the respelling rule provably catches but users
        demonstrably type ("lyft"-class) — the only hand-maintained
        list; everything else is rule-based.
     Known survivors (real SCOWL words, no principled rule drops them):
     "doh", "dix", "folic".
  4. The previous word list's words that are NOT in the final list
     (legacy 2006-web junk like "phentermine", plus filter drops) are
     reported on stdout and optionally written to a review file.
  5. The manual SUPPLEMENT below (keyboard-era vocabulary) merges at each
     word's wordfreq rank; supplement words unknown to wordfreq append at
     the tail (lowest frequency), same convention as before. Supplement
     words are exempt from every filter.

Requires the `wordfreq` pip package. Install it in a venv, never globally:

    python3 -m venv .venv-wordfreq
    .venv-wordfreq/bin/pip install wordfreq
    .venv-wordfreq/bin/python tools/generate_words_en.py [dropped.txt [scowl_dir]]

Run manually from the repo root, never from Gradle.
"""

import re
import sys
import tempfile
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
OUT_FILE = REPO_ROOT / "app/src/main/assets/words_en.txt"

TOP_N = 60000

SCOWL_MIRROR = (
    "https://raw.githubusercontent.com/rdeits/SCOWL-mirror/master/final/"
)
SCOWL_WORD_LEVELS = ["10", "20", "35", "40", "50", "55", "60", "70", "80", "95"]
SCOWL_NAME_LEVELS = ["35", "40", "50", "60", "70", "80", "95"]
SCOWL_FILES = (
    [f"english-words.{l}" for l in SCOWL_WORD_LEVELS]
    + [f"american-words.{l}" for l in SCOWL_WORD_LEVELS]
    + [f"english-proper-names.{l}" for l in SCOWL_NAME_LEVELS]
    + ["american-proper-names.80", "american-proper-names.95"]
)

# Junk-filter tuning (zipf = log10 occurrences per billion):
# Rare-name floor: the swipe-stealing names measured at zipf 2.50-2.66
# ("brien" 2.50, "vey" 2.60, "iver" 2.66) while names people actually
# type start higher ("niko" 3.02, "siri" 3.30, "alexa" 3.52) — 2.8 sits
# in the measured gap, it is not a rank cutoff: it only scopes the
# SCOWL-verified name class.
NAME_FLOOR = 2.8
# Respelling ceiling/gap: common misspellings and eye-dialect cluster at
# zipf 2.5-3.1 ("krazy" 2.55, "definately" 2.87, "seperate" 2.82); the
# gap demands the dictionary neighbor be ~100x more frequent.
RESPELL_MAX = 3.1
RESPELL_GAP = 2.0

# The decoder's candidate universe is the QWERTY letter keys; anything else
# (digits, apostrophes, unicode) can never be scored and only bloats the
# asset. 2-letter words stay: the decoder admits them on trails <= 3.5 key
# widths ("hi", "up").
WORD_OK = re.compile(r"^[a-z]{2,}$")

# Vowel-less tokens of 3+ letters ("pwr", "thx", "njpw", "msg") are texting
# abbreviations, not swipe targets — and worse, as short consonant
# subsequences of real words' key paths they hijack swipes ("pwr" beat
# "power" on the p-o-w-e-r trail in SwipeDecoderTest). 'y' counts as a
# vowel here so "sky", "gym", "spy", "rhythm" survive.
VOWEL_LESS = re.compile(r"^[bcdfghjklmnpqrstvwxz]{3,}$")

# Modern brands/terms the respelling rule provably catches (one
# substitution from a much more common word) but users demonstrably type.
# Keep this list short and audited — it is the ONLY hand-maintained
# exception list; if a word here stops being caught by the rule, drop it.
KEEP_EXCEPTIONS = {
    "cron",      # -> "iron"; unix/tech term
    "vimeo",     # -> "video"; brand
    "binance",   # -> "finance"; crypto exchange
    "publix",    # -> "public"; US grocery chain
    "ihop",      # -> "shop"; restaurant chain
    "sonos",     # -> "songs"; speaker brand
    "telus",     # -> "tells"; carrier
    "citi",      # -> "city"; bank
    "dota",      # -> "data"; game
    "snes",      # -> "ones"; console
    "yeet",      # -> "meet"; slang
    "thanos",    # -> "thanks"; Marvel character people type
    "faqs",      # -> "fans"; plural of faq
    "misc",      # -> "miss"; common clipping
    "comms",     # -> "comes"; common clipping ("comms")
    "calc",      # -> "call"; common clipping
    "panty",     # -> "party"; real word, missing from SCOWL 2018
}

# Hand-maintained supplement: words a keyboard user expects regardless of
# corpus frequency. Words present in the wordfreq list keep their wordfreq
# rank; the rest append at the tail. Exempt from every filter.
SUPPLEMENT = [
    "swipe",
    "swipes",
    "swiped",
    "swiping",
    "emoji",
    "emojis",
    "texting",
    "texter",
    "autocomplete",
    "autocorrect",
    "touchscreen",
    "gestures",
]

HEADER = [
    "# English swipe dictionary, generated by tools/generate_words_en.py.",
    "# Source: wordfreq v3 (https://github.com/rspeer/wordfreq) by Robyn",
    "# Speer — multi-corpus word frequencies (~2021 snapshot: Wikipedia,",
    "# OpenSubtitles 2018, SUBTLEX, Google Books Ngrams, OSCAR, Twitter,",
    "# Reddit), junk-filtered against the SCOWL lexicon (rare proper",
    "# names, nonce respellings, vowel-less abbreviations).",
    "# Data license: CC BY-SA 4.0",
    "# (https://creativecommons.org/licenses/by-sa/4.0/) — full attribution",
    "# in the NOTICE file at the repository root.",
    "# Format: word<TAB>rank, lower rank = more frequent. Dictionary.load",
    "# skips lines without this shape (like these comments).",
    "# A manual supplement (keyboard vocabulary like \"swipe\") is merged",
    "# by the generator's SUPPLEMENT list — edit there and regenerate.",
]


def load_current_words(path: Path) -> list[str]:
    """Words of the existing list, in file order. Same lenient parsing as
    Dictionary.load: a line needs a tab and an integer rank to count."""
    words = []
    for line in path.read_text(encoding="utf-8").splitlines():
        tab = line.find("\t")
        if tab <= 0:
            continue
        try:
            int(line[tab + 1:])
        except ValueError:
            continue
        words.append(line[:tab])
    return words


def load_scowl(scowl_dir: Path) -> tuple[set[str], set[str]]:
    """SCOWL dictionary words and proper names (lowercased). Missing files
    are fetched from the GitHub mirror into scowl_dir."""
    scowl_dir.mkdir(parents=True, exist_ok=True)
    words, names = set(), set()
    for name in SCOWL_FILES:
        path = scowl_dir / f"{name}.txt"
        if not path.exists():
            print(f"fetching SCOWL file {name} ...", file=sys.stderr)
            with urllib.request.urlopen(SCOWL_MIRROR + name, timeout=60) as r:
                raw = r.read().decode("latin-1").replace("\r", "")
            path.write_text(raw, encoding="utf-8")
        content = set(path.read_text(encoding="utf-8").split())
        if "proper-names" in name:
            names |= {w.lower() for w in content}
        else:
            words |= content
    return words, names


def one_substitution_away(word: str):
    """All same-length tokens differing from word in exactly one letter —
    the true respelling/misspelling class. Insertions and deletions are
    excluded on purpose: they conflate word formation with misspelling
    and would kill brands and compounds ("json"->"son", "cron"->"iron",
    "andy"->"and", "thanos"->"thanks")."""
    out = set()
    for i, orig in enumerate(word):
        for c in "abcdefghijklmnopqrstuvwxyz":
            if c != orig:
                out.add(word[:i] + c + word[i + 1:])
    return out


def main() -> None:
    try:
        from wordfreq import top_n_list, zipf_frequency
    except ImportError:
        sys.exit(
            "wordfreq is not installed. Create a venv and install it there:\n"
            "  python3 -m venv .venv-wordfreq\n"
            "  .venv-wordfreq/bin/pip install wordfreq\n"
            "  .venv-wordfreq/bin/python tools/generate_words_en.py"
        )

    scowl_dir = (Path(sys.argv[2]) if len(sys.argv) > 2
                 else Path(tempfile.gettempdir()) / "scowl-2018.04.16")
    scowl_words, scowl_names = load_scowl(scowl_dir)

    candidates = [w for w in top_n_list("en", TOP_N)
                  if WORD_OK.fullmatch(w) and not VOWEL_LESS.fullmatch(w)]

    rare_names, respellings = [], []
    for w in candidates:
        if w in KEEP_EXCEPTIONS:
            continue
        z = zipf_frequency(w, "en")
        if w in scowl_names and w not in scowl_words and z < NAME_FLOOR:
            rare_names.append(w)  # "brien", "iver", "vey"
        elif (len(w) >= 4 and w not in scowl_words and z < RESPELL_MAX
              and any(n in scowl_words and zipf_frequency(n, "en") >= z + RESPELL_GAP
                      for n in one_substitution_away(w))):
            respellings.append(w)  # "krazy" -> "crazy"
    dropped_by_filter = set(rare_names) | set(respellings)
    words = [w for w in candidates if w not in dropped_by_filter]
    word_set = set(words)

    # Supplement: merge at wordfreq rank when known, append at the tail
    # (lowest frequency) otherwise.
    tail = [w for w in SUPPLEMENT if w not in word_set]
    words.extend(tail)
    final_set = word_set | set(tail)

    # Report which words of the previous list did not survive — legacy
    # junk and filter drops alike; review before committing.
    dropped = [w for w in load_current_words(OUT_FILE) if w not in final_set]

    lines = HEADER + [f"{word}\t{rank}" for rank, word in enumerate(words, start=1)]
    OUT_FILE.write_text("\n".join(lines) + "\n", encoding="utf-8")

    if len(sys.argv) > 1:
        Path(sys.argv[1]).write_text("\n".join(dropped) + "\n", encoding="utf-8")

    size = OUT_FILE.stat().st_size
    print(f"wrote {OUT_FILE}")
    print(f"  words:         {len(words)} ({len(word_set)} after filters, "
          f"{len(tail)} supplement-only at tail)")
    print(f"  filtered:      {len(rare_names)} rare proper names, "
          f"{len(respellings)} nonce respellings "
          f"(of {len(candidates)} candidates)")
    print(f"  dropped:       {len(dropped)} words from the previous list"
          + (f" (listed in {sys.argv[1]})" if len(sys.argv) > 1 else
             " (pass an output path to list them)"))
    print(f"  file size:     {size} bytes ({size / 1024:.0f} KiB)")


if __name__ == "__main__":
    main()
