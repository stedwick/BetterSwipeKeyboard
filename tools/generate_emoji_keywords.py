#!/usr/bin/env python3
"""Generates app/src/main/assets/emoji_keywords_en.txt, the static
keyword -> emoji table behind the emoji panel's suggestion row.

Source data: Unicode CLDR emoji annotations (English), fetched from
unicode-org/cldr-json (cldr-annotations-full). The table is inverted:
one `keyword<TAB>emoji,emoji,...` line per keyword, sorted by keyword.

Within each keyword, emoji are ordered by first appearance in the emoji
panel (layout/EmojiData.kt — our hand-picked order is a rough popularity
ranking), then by CLDR document order for emoji outside the panel.

A hand-tuned alias block (phrases the tokenizer can only reach via a
bigram, like "taking off", and shortcuts like "lol") is appended at the
end of the generated file — same convention as the manual supplement in
words_en.txt. Alias emoji are merged AHEAD of any CLDR emoji for the
same keyword, so the hand pick wins the ranking.

Stdlib only. Run manually from the repo root, never from Gradle:

    python3 tools/generate_emoji_keywords.py [annotations.json]

Pass a previously downloaded annotations.json to skip the network fetch
(useful on machines where Python's SSL roots are missing — fetch with
curl instead).
"""

import json
import re
import sys
import urllib.request
from pathlib import Path

CLDR_URL = (
    "https://raw.githubusercontent.com/unicode-org/cldr-json/main/"
    "cldr-json/cldr-annotations-full/annotations/en/annotations.json"
)

REPO_ROOT = Path(__file__).resolve().parent.parent
EMOJI_DATA_KT = REPO_ROOT / "app/src/main/java/com/example/betterswipekeyboard/layout/EmojiData.kt"
OUT_FILE = REPO_ROOT / "app/src/main/assets/emoji_keywords_en.txt"

# Only plain lowercase words and word-spaces make useful lookup keys: the
# matcher tokenizes on letter runs, so "check-in" or "O'clock" style
# keywords could never be hit.
KEYWORD_OK = re.compile(r"^[a-z ]+$")

# Hand-tuned aliases. Two kinds:
#  - bigram phrases the unigram table cannot express ("taking off")
#  - common texting shortcuts CLDR does not list ("lol", "pls")
# Alias emoji are placed ahead of any CLDR emoji for the same keyword.
ALIASES = {
    # phrases (matched as bigrams)
    "taking off": ["🛫", "✈️"],
    "good morning": ["☀️", "🌅", "☕"],
    "good night": ["🌙", "😴", "💤"],
    "happy birthday": ["🎂", "🎉", "🥳", "🎈"],
    "merry christmas": ["🎄", "🎅", "🎁"],
    "happy new year": ["🎉", "🥂", "🎆"],
    "trick or treat": ["🎃", "👻", "🍬"],
    "on fire": ["🔥"],
    "thumbs up": ["👍"],
    "thumbs down": ["👎"],
    "fingers crossed": ["🤞"],
    "high five": ["🙌", "✋"],
    "peace out": ["✌️"],
    "heart eyes": ["😍"],
    "side eye": ["🙄", "👀"],
    "mind blown": ["🤯"],
    "mic drop": ["🎤"],
    "road trip": ["🚗", "🛣️"],
    "date night": ["❤️", "🍷", "🌃"],
    "clinking glasses": ["🥂"],
    # texting shortcuts / gaps in CLDR
    "lol": ["😂", "🤣"],
    "lmao": ["🤣", "😂", "💀"],
    "ok": ["👌", "👍"],
    "okay": ["👌", "👍"],
    "pls": ["🥺", "🙏"],
    "please": ["🥺", "🙏"],
    "omg": ["😱", "🤯"],
    "congrats": ["🎉", "👏", "🥳"],
    "thanks": ["🙏", "😊"],
    "yum": ["😋", "🤤"],
    "cheers": ["🍻", "🥂"],
    "lit": ["🔥"],
    "hug": ["🤗"],
    "kiss": ["😘", "💋"],
    "cry": ["😢", "😭"],
    "clap": ["👏"],
    "pray": ["🙏"],
    "flex": ["💪"],
    "poop": ["💩"],
    "gym": ["💪", "🏋️"],
    "workout": ["💪", "🏋️"],
    "plane": ["✈️", "🛫", "🛬"],
    "flight": ["✈️", "🛫"],
    "beer": ["🍺", "🍻"],
    "coffee": ["☕"],
    "pizza": ["🍕"],
    "money": ["💰", "🤑", "💸"],
    "phone": ["📱"],
    "beach": ["🏖️", "🏝️"],
    "rain": ["🌧️", "☔"],
    "snow": ["❄️", "⛄", "🌨️"],
    "rocket": ["🚀"],
    "moon": ["🌙"],
    "sun": ["☀️", "🌞"],
    "rainbow": ["🌈"],
    "flower": ["🌸", "🌺", "🌻", "🌹"],
    "dog": ["🐶", "🐕"],
    "cat": ["🐱", "🐈"],
    "birthday": ["🎂", "🎉", "🥳", "🎈"],
    "christmas": ["🎄", "🎅", "🎁"],
    "halloween": ["🎃", "👻"],
}


VS16 = "️"  # variation selector-16: forces emoji presentation


def panel_order(path: Path) -> dict:
    """Emoji -> index of first appearance in EmojiData.kt (panel order)."""
    text = path.read_text(encoding="utf-8")
    order = {}
    for match in re.finditer(r'"([^"\\]+)"', text):
        s = match.group(1)
        # Emoji literals are the only non-ASCII strings in the file
        # (titles are plain English).
        if any(ord(c) > 0x2000 for c in s) and s not in order:
            order[s] = len(order)
    return order


def make_canonical(panel: dict):
    """CLDR annotation keys drop VS16 where our panel keeps it ("✈" vs
    "✈️"). Map every emoji to the panel's canonical form when the panel
    has one, so suggestions commit the exact strings the grid does."""
    by_bare = {e.replace(VS16, ""): e for e in panel}

    def canonical(emoji: str) -> str:
        if emoji in panel:
            return emoji
        return by_bare.get(emoji.replace(VS16, ""), emoji)

    return canonical


def fetch_annotations() -> dict:
    if len(sys.argv) > 1:
        data = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    else:
        with urllib.request.urlopen(CLDR_URL, timeout=60) as response:
            data = json.load(response)
    return data["annotations"]["annotations"]


def as_word_list(value) -> list:
    """cldr-json uses a list for `default` and (usually) a string for `tts`."""
    if isinstance(value, list):
        return [str(v) for v in value]
    return [str(value)]


def build_table(annotations: dict, panel: dict) -> dict:
    canonical = make_canonical(panel)
    alias_emoji = {e for emojis in ALIASES.values() for e in emojis}

    def keep(emoji: str) -> bool:
        # cldr-annotations-full also annotates math/technical symbols
        # (e.g. "above" -> ⪍) that are not emoji in any useful sense.
        # Keep real emoji: anything in the SMP emoji blocks, anything
        # carrying VS16 (forces emoji presentation), anything we picked
        # for the panel or an alias.
        return (any(ord(c) >= 0x1F000 for c in emoji)
                or VS16 in emoji
                or emoji in panel
                or emoji in alias_emoji)

    # keyword -> (emoji -> sort rank), dicts preserve insertion order.
    by_keyword = {}
    for cldr_index, (emoji, entry) in enumerate(annotations.items()):
        emoji = canonical(emoji)
        if not keep(emoji):
            continue
        keywords = set()
        for kw in as_word_list(entry.get("default", [])):
            kw = kw.strip().lower()
            keywords.add(kw)
            # Index multiword keywords ("grinning face") whole — the
            # bigram matcher can hit them — and per word.
            if " " in kw:
                keywords.update(kw.split())
        for tts in as_word_list(entry.get("tts", "")):
            keywords.update(tts.strip().lower().split())
        rank = (panel.get(emoji, len(panel)), cldr_index)
        for kw in keywords:
            if len(kw) < 2 or not KEYWORD_OK.match(kw):
                continue
            by_keyword.setdefault(kw, {})[emoji] = rank

    table = {}
    for kw, emoji_ranks in by_keyword.items():
        ordered = sorted(emoji_ranks, key=emoji_ranks.get)
        table[kw] = ordered

    # Aliases merge ahead of CLDR emoji, first occurrence wins.
    known_bare = {e.replace(VS16, "") for e in annotations}
    for kw, emojis in ALIASES.items():
        merged = list(emojis)
        for emoji in table.get(kw, []):
            if emoji not in merged:
                merged.append(emoji)
        table[kw] = merged
        for emoji in emojis:
            if emoji.replace(VS16, "") not in known_bare:
                print(f"warning: alias {kw!r} uses {emoji!r}, "
                      f"which is not in the CLDR annotations", file=sys.stderr)
    return table


def main() -> None:
    panel = panel_order(EMOJI_DATA_KT)
    annotations = fetch_annotations()
    table = build_table(annotations, panel)

    lines = [
        "# Emoji suggestion keywords, generated by tools/generate_emoji_keywords.py",
        "# from Unicode CLDR emoji annotations (English).",
        "# Data (c) Unicode, Inc. - https://www.unicode.org/copyright.html",
        "# License: https://www.unicode.org/license.txt",
        "# Format: keyword<TAB>emoji,emoji,... (best suggestion first).",
        "# Hand-tuned aliases (e.g. 'taking off', 'lol') are merged into the",
        "# table by the generator's ALIASES dict — edit there and regenerate.",
    ]
    for kw in sorted(table):
        lines.append(f"{kw}\t{','.join(table[kw])}")

    OUT_FILE.write_text("\n".join(lines) + "\n", encoding="utf-8")

    emoji_covered = {e for emojis in table.values() for e in emojis}
    panel_covered = sum(1 for e in panel if e in emoji_covered)
    size = OUT_FILE.stat().st_size
    print(f"wrote {OUT_FILE}")
    print(f"  keywords:      {len(table)}")
    print(f"  emoji covered: {len(emoji_covered)} (of {len(annotations)} in CLDR)")
    print(f"  panel emoji:   {panel_covered}/{len(panel)} covered by >= 1 keyword")
    print(f"  file size:     {size} bytes ({size / 1024:.0f} KiB)")


if __name__ == "__main__":
    main()
