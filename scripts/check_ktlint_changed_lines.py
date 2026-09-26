#!/usr/bin/env python3
"""Incremental ktlint gate: fail only for violations on lines you changed.

Usage:
    python scripts/check_ktlint_changed_lines.py [--base origin/main]

Requires ktlint reports first::

    ./gradlew ktlintCheck

ktlint's historical debt is too large to block on globally, and its own
baseline is line-number based (any edit would re-expose old violations), so
this script provides the "modified files block, history does not" behaviour of
roadmap batch R01: a violation fails the gate only when it sits on a line that
the current change adds relative to the merge base of ``--base``.

Exit codes: 0 = clean, 1 = violations on changed lines, 2 = usage error.
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import xml.etree.ElementTree as ET

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPORT_ROOT = REPO_ROOT


def git(*args: str) -> str:
    result = subprocess.run(
        ["git", "-C", REPO_ROOT, *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or ("git %s failed" % " ".join(args)))
    return result.stdout


def merge_base(base: str) -> str:
    try:
        return git("merge-base", base, "HEAD").strip()
    except RuntimeError:
        # fall back to the ref itself (detached/shallow checkouts)
        return base


def changed_lines(base: str) -> dict[str, set[int]]:
    """Return added-line numbers per repo-relative file against ``base``."""
    sha = merge_base(base)
    diff = git("diff", "--unified=0", "--no-color", sha, "--")
    changes: dict[str, set[int]] = {}
    current: str | None = None
    for line in diff.splitlines():
        if line.startswith("+++ "):
            target = line[4:].strip()
            if target.startswith("b/"):
                target = target[2:]
            if target == "/dev/null":
                current = None
            else:
                current = target.strip('"').replace("\\", "/")
            continue
        if not line.startswith("@@") or current is None:
            continue
        # @@ -oldStart,oldCount +newStart,newCount @@
        try:
            plus = line.split("+", 1)[1]
            spec = plus.split(" ", 1)[0]
            if "," in spec:
                start_text, count_text = spec.split(",", 1)
                count = int(count_text)
            else:
                start_text, count = spec, 1
            start = int(start_text)
        except (IndexError, ValueError):
            continue
        lines = changes.setdefault(current, set())
        lines.update(range(start, start + count))

    # Brand-new files are untracked, so `git diff` cannot see them yet; treat
    # every line of an untracked Kotlin source as changed.
    untracked = git("ls-files", "--others", "--exclude-standard").splitlines()
    for path in untracked:
        if not path.endswith((".kt", ".kts")):
            continue
        full = os.path.join(REPO_ROOT, path)
        if not os.path.isfile(full):
            continue
        with open(full, encoding="utf-8", errors="replace") as handle:
            count = sum(1 for _ in handle)
        changes.setdefault(path.replace("\\", "/"), set()).update(range(1, count + 1))
    return changes


def repo_relative(path_text: str) -> str:
    path_text = path_text.strip().strip('"')
    if os.path.isabs(path_text):
        return os.path.relpath(path_text, REPO_ROOT).replace(os.sep, "/")
    return path_text.replace("\\", "/")


def load_violations() -> list[tuple[str, int, str, str]]:
    """Return ``(file, line, rule, message)`` from every ktlint XML report."""
    violations: list[tuple[str, int, str, str]] = []
    seen: set[tuple[str, int, str]] = set()
    found_report = False
    for dirpath, _dirnames, filenames in os.walk(REPORT_ROOT):
        normalized = dirpath.replace(os.sep, "/")
        if "/build/reports/ktlint" not in normalized:
            continue
        for name in sorted(filenames):
            if not name.endswith(".xml"):
                continue
            found_report = True
            tree = ET.parse(os.path.join(dirpath, name))
            for file_node in tree.getroot().iter("file"):
                target = repo_relative(file_node.get("name", ""))
                for error in file_node.iter("error"):
                    line = int(error.get("line", "0"))
                    rule = (error.get("source") or "").split(":")[-1]
                    key = (target, line, rule)
                    if key in seen:
                        continue
                    seen.add(key)
                    violations.append((target, line, rule, error.get("message", "")))
    if not found_report:
        print("ERROR: no ktlint reports found; run ./gradlew ktlintCheck first.")
        sys.exit(2)
    return sorted(violations)


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--base",
        default="origin/main",
        help="git ref to diff against (default: origin/main)",
    )
    args = parser.parse_args(argv)

    try:
        changes = changed_lines(args.base)
    except RuntimeError as error:
        print("ERROR: cannot diff against %s: %s" % (args.base, error))
        return 2

    violations = load_violations()
    blocking = [
        item
        for item in violations
        if item[0] in changes and item[1] in changes[item[0]]
    ]

    print(
        "ktlint changed-lines gate: %d changed file(s), %d historical violation(s), "
        "%d on changed lines"
        % (len(changes), len(violations), len(blocking))
    )
    if blocking:
        print("FAILED - fix these ktlint violations on lines you touched:")
        for target, line, rule, message in blocking:
            print("  %s:%d: %s (%s)" % (target, line, message, rule))
        return 1
    print("OK - no ktlint violations on changed lines")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
