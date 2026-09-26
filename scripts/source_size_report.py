#!/usr/bin/env python3
"""Source size report and size guardrails for the PowerAi refactor roadmap.

Usage:
    python scripts/source_size_report.py                 # print the report
    python scripts/source_size_report.py --check         # enforce guardrails, exit 1 on violation
    python scripts/source_size_report.py --update-baseline

The report covers production sources (``src/main``) of every module:
app, core/model-contract, core/data, engine/native, engine/ai,
feature:search-chat and benchmark.

Guardrails enforced by ``--check`` (roadmap batch R01):

* a production file that is not in the baseline (a new file) must be <= 300 lines;
* a production file that is in the baseline must stay <= 350 lines, or - if it
  already exceeded 350 lines when the baseline was taken - must not grow past
  its baseline size;
* a function that is not in the baseline must be <= 80 lines;
* a function already recorded as too long must not grow any further;
* the detekt baseline (config/detekt/detekt-baseline.xml) must not grow.

Everything is pure stdlib and deterministic: two consecutive runs produce
byte-identical output.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

MODULES = {
    "app": "app/src/main",
    "core:model-contract": "core/model-contract/src/main",
    "core:data": "core/data/src/main",
    "engine:native": "engine/native/src/main",
    "engine:ai": "engine/ai/src/main",
    "feature:search-chat": "feature/search-chat/src/main",
    "benchmark": "benchmark/src/main",
}

BASELINE_PATH = "config/quality/guardrail-baseline.json"
DETEKT_BASELINE_PATH = "config/detekt/detekt-baseline.xml"

NEW_FILE_MAX_LINES = 300
FILE_MAX_LINES = 350
NEW_FUNCTION_MAX_LINES = 80

SOURCE_SUFFIXES = (".kt", ".java")

# Kotlin declaration: optional annotations and modifiers in front of ``fun``.
_MODIFIER = r"(?:public|private|internal|protected|open|abstract|final|override|suspend|inline|tailrec|operator|infix|external|expect|actual|const|lateinit|inner|companion|enum|annotation|data|sealed|value|vararg|crossinline|noinline|reified)"
_KT_FUN_RE = re.compile(
    r"^\s*(?:@\w+(?:\([^)]*\))?\s+)*(?:" + _MODIFIER + r"\s+)*fun\s+"
    r"(?:<[^>]*>\s*)?(?:[\w.]+\.)?([A-Za-z_]\w*)"
)
# Kotlin top-level/ member type declarations (used only for extent detection).
_KT_TYPE_RE = re.compile(
    r"^\s*(?:@\w+(?:\([^)]*\))?\s+)*(?:" + _MODIFIER + r"\s+)*"
    r"(?:class|interface|object|typealias)\s+([A-Za-z_]\w*)"
)


def rel(path: str) -> str:
    """Repo-relative POSIX path."""
    return os.path.relpath(path, REPO_ROOT).replace(os.sep, "/")


def count_lines(path: str) -> int:
    with open(path, encoding="utf-8", errors="replace") as handle:
        return sum(1 for _ in handle)


def strip_noise(line: str) -> str:
    """Remove comments and string literals so brace counting stays balanced."""
    # triple-quoted raw strings on a single line
    line = re.sub(r'"""(?:.|\n)*?"""', '""', line)
    # normal string literals (keep escaped quotes inside)
    line = re.sub(r'"(?:\\.|[^"\\])*"', '""', line)
    # line comment
    idx = line.find("//")
    if idx >= 0:
        line = line[:idx]
    return line


def function_end(lines: list[str], start: int, indent: int) -> int:
    """Return the last line index of the function declared at ``start``."""
    depth = 0
    opened = False
    for idx in range(start, len(lines)):
        code = strip_noise(lines[idx])
        for char in code:
            if char == "{":
                depth += 1
                opened = True
            elif char == "}":
                depth -= 1
                if opened and depth == 0:
                    return idx
        if idx > start and not opened:
            # expression-bodied function or a multi-line signature that never
            # opened a block: it ends at the first dedent / blank line / next
            # declaration at the same indentation.
            stripped = lines[idx].strip()
            cur_indent = len(lines[idx]) - len(lines[idx].lstrip())
            if not stripped or cur_indent < indent:
                return idx - 1
            if cur_indent == indent and (
                _KT_FUN_RE.match(lines[idx])
                or _KT_TYPE_RE.match(lines[idx])
                or stripped.startswith("}")
            ):
                return idx - 1
    return len(lines) - 1


def find_functions(path: str, lines: list[str]) -> list[tuple[str, int, int]]:
    """Return ``(name, start_line, line_count)`` for functions longer than 1 line.

    Only Kotlin files are scanned (all production sources are Kotlin); the
    heuristic is intentionally simple - it is a gate for *new* code, not a
    full parser.
    """
    if not path.endswith(".kt"):
        return []
    functions = []
    in_block_comment = False
    idx = 0
    while idx < len(lines):
        stripped = lines[idx].strip()
        if in_block_comment:
            if "*/" in stripped:
                in_block_comment = False
            idx += 1
            continue
        if stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*"):
            if stripped.startswith("/*") and "*/" not in stripped:
                in_block_comment = True
            idx += 1
            continue
        match = _KT_FUN_RE.match(lines[idx])
        if match:
            indent = len(lines[idx]) - len(lines[idx].lstrip())
            end = function_end(lines, idx, indent)
            functions.append((match.group(1), idx + 1, end - idx + 1))
            idx = end + 1
            continue
        idx += 1
    return functions


def collect() -> tuple[dict, dict, dict]:
    """Return ``(modules, files, functions)``.

    ``modules``: module -> {"files": n, "lines": n}
    ``files``: relative path -> line count
    ``functions``: "path::function" -> line count, only for functions whose
    length matters for the guardrails (> 80 lines).
    """
    modules: dict[str, dict[str, int]] = {}
    files: dict[str, int] = {}
    functions: dict[str, int] = {}
    for module, source_root in MODULES.items():
        base = os.path.join(REPO_ROOT, source_root)
        file_count = 0
        line_count = 0
        for dirpath, dirnames, filenames in os.walk(base):
            dirnames.sort()
            for name in sorted(filenames):
                if not name.endswith(SOURCE_SUFFIXES):
                    continue
                path = os.path.join(dirpath, name)
                lines_count = count_lines(path)
                file_count += 1
                line_count += lines_count
                relative = rel(path)
                files[relative] = lines_count
                with open(path, encoding="utf-8", errors="replace") as handle:
                    source_lines = handle.read().splitlines()
                for func_name, _start, func_lines in find_functions(path, source_lines):
                    if func_lines > NEW_FUNCTION_MAX_LINES:
                        key = "%s::%s" % (relative, func_name)
                        # keep the longest function for a repeated name
                        if func_lines > functions.get(key, 0):
                            functions[key] = func_lines
        modules[module] = {"files": file_count, "lines": line_count}
    return modules, dict(sorted(files.items())), dict(sorted(functions.items()))


def detekt_baseline_findings() -> int | None:
    path = os.path.join(REPO_ROOT, DETEKT_BASELINE_PATH)
    if not os.path.exists(path):
        return None
    tree = ET.parse(path)
    return len(tree.getroot().findall(".//ID"))


def build_baseline() -> dict:
    modules, files, functions = collect()
    return {
        "detekt": {
            "baselineFile": DETEKT_BASELINE_PATH,
            "findings": detekt_baseline_findings(),
        },
        "sourceSize": {
            "thresholds": {
                "newFileMaxLines": NEW_FILE_MAX_LINES,
                "fileMaxLines": FILE_MAX_LINES,
                "newFunctionMaxLines": NEW_FUNCTION_MAX_LINES,
            },
            "modules": modules,
            "totals": {
                "files": sum(m["files"] for m in modules.values()),
                "lines": sum(m["lines"] for m in modules.values()),
                "appFileShare": round(
                    modules["app"]["files"] / max(1, sum(m["files"] for m in modules.values())), 4
                ),
                "appLineShare": round(
                    modules["app"]["lines"] / max(1, sum(m["lines"] for m in modules.values())), 4
                ),
            },
            "files": files,
            "longFunctions": functions,
        },
    }


def print_report() -> None:
    modules, files, functions = collect()
    total_files = sum(m["files"] for m in modules.values())
    total_lines = sum(m["lines"] for m in modules.values())

    print("PowerAi production source size report")
    print("=====================================")
    print()
    print("Per-module totals (src/main, Kotlin/Java)")
    print("%-22s %8s %10s %8s" % ("module", "files", "lines", "app/total"))
    for module in MODULES:
        entry = modules[module]
        share = (entry["lines"] / total_lines * 100) if total_lines else 0.0
        print("%-22s %8d %10d %7.1f%%" % (module, entry["files"], entry["lines"], share))
    print("%-22s %8d %10d" % ("TOTAL", total_files, total_lines))
    app_share = (modules["app"]["lines"] / total_lines * 100) if total_lines else 0.0
    app_file_share = (modules["app"]["files"] / total_files * 100) if total_files else 0.0
    print()
    print("app share: %.1f%% of lines, %.1f%% of files" % (app_share, app_file_share))

    for threshold in (300, 350, 400):
        hits = sorted(
            ((path, lines) for path, lines in files.items() if lines >= threshold),
            key=lambda item: (-item[1], item[0]),
        )
        print()
        print("Files >= %d lines (%d)" % (threshold, len(hits)))
        for path, lines in hits:
            print("  %6d  %s" % (lines, path))

    long_functions = sorted(
        ((key, lines) for key, lines in functions.items() if lines > NEW_FUNCTION_MAX_LINES),
        key=lambda item: (-item[1], item[0]),
    )
    print()
    print("Function candidates > %d lines (%d)" % (NEW_FUNCTION_MAX_LINES, len(long_functions)))
    for key, lines in long_functions:
        print("  %6d  %s" % (lines, key))

    findings = detekt_baseline_findings()
    print()
    print("detekt baseline findings: %s (%s)" % (findings, DETEKT_BASELINE_PATH))


def check() -> int:
    baseline_path = os.path.join(REPO_ROOT, BASELINE_PATH)
    if not os.path.exists(baseline_path):
        print("ERROR: %s is missing; run --update-baseline first." % BASELINE_PATH)
        return 1
    with open(baseline_path, encoding="utf-8") as handle:
        baseline = json.load(handle)

    base_files: dict = baseline["sourceSize"]["files"]
    base_functions: dict = baseline["sourceSize"]["longFunctions"]
    base_detekt = baseline.get("detekt", {}).get("findings")
    _, files, functions = collect()

    violations: list[str] = []

    for path, lines in sorted(files.items()):
        if path not in base_files:
            if lines > NEW_FILE_MAX_LINES:
                violations.append(
                    "new file %s is %d lines (max %d)" % (path, lines, NEW_FILE_MAX_LINES)
                )
            continue
        allowed = base_files[path]
        if allowed <= FILE_MAX_LINES:
            allowed = FILE_MAX_LINES
        if lines > allowed:
            violations.append(
                "file %s grew to %d lines (baseline %d, allowed %d)"
                % (path, lines, base_files[path], allowed)
            )

    for key, lines in sorted(functions.items()):
        if key not in base_functions:
            if lines > NEW_FUNCTION_MAX_LINES:
                violations.append(
                    "new function %s is %d lines (max %d)"
                    % (key, lines, NEW_FUNCTION_MAX_LINES)
                )
            continue
        if lines > base_functions[key]:
            violations.append(
                "function %s grew to %d lines (baseline %d)"
                % (key, lines, base_functions[key])
            )

    current_findings = detekt_baseline_findings()
    if current_findings is None:
        violations.append("detekt baseline %s is missing" % DETEKT_BASELINE_PATH)
    elif base_detekt is not None and current_findings > base_detekt:
        violations.append(
            "detekt baseline grew from %d to %d findings" % (base_detekt, current_findings)
        )

    if violations:
        print("source size guardrail FAILED (%d violation(s)):" % len(violations))
        for item in violations:
            print("  - %s" % item)
        print("Historical debt is frozen in %s; fix the growth above." % BASELINE_PATH)
        return 1

    print(
        "source size guardrail OK: %d files, %d lines, detekt baseline %s findings"
        % (len(files), sum(files.values()), current_findings)
    )
    return 0


def update_baseline() -> int:
    baseline = build_baseline()
    path = os.path.join(REPO_ROOT, BASELINE_PATH)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(baseline, handle, indent=2, sort_keys=True)
        handle.write("\n")
    print("wrote %s" % BASELINE_PATH)
    return 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--check", action="store_true", help="enforce the size guardrails")
    group.add_argument(
        "--update-baseline",
        action="store_true",
        help="rewrite the size baseline from the current tree",
    )
    args = parser.parse_args(argv)
    if args.check:
        return check()
    if args.update_baseline:
        return update_baseline()
    print_report()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
