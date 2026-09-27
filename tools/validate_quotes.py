#!/usr/bin/env python3
"""Validate the bundled daily quotes file for Kairos Era.

Usage:
    python3 tools/validate_quotes.py [path/to/quotes.json]

With no argument, validates app/src/main/assets/motivation/quotes.json relative to
the repository root (the parent of this script's directory).

Exits 0 when the file passes, 1 when there are problems, 2 when the file cannot be read.
Mirrors com.kairosera.domain.quote.QuoteValidator, plus a few extra editorial checks.
Standard library only.
"""

import json
import re
import sys
from collections import Counter
from pathlib import Path

EXPECTED_COUNT = 365
MAX_QUOTE_LENGTH = 140
MAX_ACTION_LENGTH = 90
CATEGORY_MIN = 15
CATEGORY_MAX = 30

ALLOWED_CATEGORIES = {
    "discipline", "consistency", "learning", "courage", "focus", "patience",
    "failure", "work", "health", "reading", "self-respect", "time", "growth",
    "reflection", "beginnings", "finishing", "resilience",
}

BANNED_PHRASES = [
    "no excuses", "beast mode", "you must", "win every day", "grind", "hustle",
    "never give up", "pain is weakness", "100%",
]

# A trailing "- Name" / "— Some Name" looks like an attribution to a person.
ATTRIBUTION = re.compile(r"[-–—]\s*[A-Z][\w.]*(\s+[A-Z][\w.]*){0,2}\s*$")

DEFAULT_RELATIVE_PATH = Path("app/src/main/assets/motivation/quotes.json")


def normalize(text):
    """Lowercase and keep only letters and digits."""
    return "".join(ch for ch in text.lower() if ch.isalnum())


def first_words(text, n=4):
    words = re.findall(r"[a-z0-9']+", text.lower())
    return " ".join(words[:n])


def validate(entries):
    problems = []
    warnings = []

    if not isinstance(entries, list):
        return ["top-level JSON value must be an array"], warnings, Counter()

    if len(entries) != EXPECTED_COUNT:
        problems.append(f"expected {EXPECTED_COUNT} entries, found {len(entries)}")

    day_counts = Counter()
    seen_quotes = {}
    seen_openings = {}
    category_counts = Counter()

    for index, entry in enumerate(entries):
        if not isinstance(entry, dict):
            problems.append(f"entry #{index}: not an object")
            continue

        day = entry.get("dayOfYear")
        tag = f"day {day}" if isinstance(day, int) else f"entry #{index}"
        if not isinstance(day, int) or isinstance(day, bool):
            problems.append(f"{tag}: dayOfYear missing or not an integer")
        else:
            day_counts[day] += 1

        quote = entry.get("quote")
        action = entry.get("actionPrompt")
        category = entry.get("category")

        if not isinstance(quote, str) or not quote.strip():
            problems.append(f"{tag}: empty or missing quote")
            quote = quote if isinstance(quote, str) else ""
        if not isinstance(action, str) or not action.strip():
            problems.append(f"{tag}: empty or missing actionPrompt")
            action = action if isinstance(action, str) else ""

        if len(quote) > MAX_QUOTE_LENGTH:
            problems.append(f"{tag}: quote is {len(quote)} chars (max {MAX_QUOTE_LENGTH})")
        if len(action) > MAX_ACTION_LENGTH:
            problems.append(f"{tag}: actionPrompt is {len(action)} chars (max {MAX_ACTION_LENGTH})")

        if category not in ALLOWED_CATEGORIES:
            problems.append(f"{tag}: unknown category {category!r}")
        else:
            category_counts[category] += 1

        combined = f"{quote} {action}".lower()
        for phrase in BANNED_PHRASES:
            if phrase in combined:
                problems.append(f"{tag}: contains banned phrase {phrase!r}")

        if quote and ATTRIBUTION.search(quote):
            problems.append(f"{tag}: quote ends with what looks like an attribution")

        key = normalize(quote)
        if key:
            if key in seen_quotes:
                problems.append(f"{tag}: duplicate quote (same as {seen_quotes[key]})")
            else:
                seen_quotes[key] = tag

        opening = first_words(quote)
        if opening and len(opening.split()) == 4:
            if opening in seen_openings:
                problems.append(
                    f"{tag}: quote shares its first 4 words ({opening!r}) with {seen_openings[opening]}"
                )
            else:
                seen_openings[opening] = tag

    expected_days = set(range(1, EXPECTED_COUNT + 1))
    missing = sorted(expected_days - set(day_counts))
    if missing:
        problems.append(f"missing days: {missing}")
    duplicated = sorted(d for d, c in day_counts.items() if c > 1)
    if duplicated:
        problems.append(f"days appearing more than once: {duplicated}")
    out_of_range = sorted(d for d in day_counts if d not in expected_days)
    if out_of_range:
        problems.append(f"days out of range 1..{EXPECTED_COUNT}: {out_of_range}")

    days_in_file = [e.get("dayOfYear") for e in entries if isinstance(e, dict)]
    if not problems and days_in_file != sorted(days_in_file):
        warnings.append("entries are not in dayOfYear order")

    for category in sorted(ALLOWED_CATEGORIES):
        count = category_counts.get(category, 0)
        if count < CATEGORY_MIN or count > CATEGORY_MAX:
            warnings.append(
                f"category {category!r} has {count} entries (expected {CATEGORY_MIN}-{CATEGORY_MAX})"
            )

    return problems, warnings, category_counts


def main(argv):
    repo_root = Path(__file__).resolve().parent.parent
    if len(argv) > 1:
        path = Path(argv[1])
    else:
        path = repo_root / DEFAULT_RELATIVE_PATH

    try:
        with open(path, encoding="utf-8") as fh:
            entries = json.load(fh)
    except (OSError, ValueError) as exc:
        print(f"ERROR: cannot read {path}: {exc}", file=sys.stderr)
        return 2

    problems, warnings, category_counts = validate(entries)

    print(f"Validating {path}")
    if category_counts:
        print("Category counts:")
        for category, count in sorted(category_counts.items()):
            print(f"  {category:<14} {count}")

    for warning in warnings:
        print(f"WARNING: {warning}")

    if problems:
        print(f"FAILED with {len(problems)} problem(s):")
        for problem in problems:
            print(f"  - {problem}")
        return 1

    print(f"OK: {len(entries)} quotes passed all checks.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
