#!/usr/bin/env python3
"""Lightweight architecture guardrails for the PowerAi refactor roadmap.

Usage:
    python scripts/check_architecture.py                   # check, exit 1 on new violation
    python scripts/check_architecture.py --update-baseline  # record current state

Checks (roadmap batch R01):

1. feature modules must not depend on ``app`` - neither through Gradle
   (``project(":app")``) nor through source references to app-only packages.
2. domain / usecase / query / retrieval-policy production sources must not
   import Android (UI) APIs.
3. no new duplicated source of truth: the same top-level type name must not
   appear in more than one production module unless it is recorded in the
   baseline (historical duplicates such as ``JsonUtils``).

Historical findings are frozen in config/quality/architecture-baseline.json;
only *new* findings fail the check. Output is deterministic.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# module name -> production source root
MODULES = {
    "app": "app/src/main",
    "core:model-contract": "core/model-contract/src/main",
    "core:data": "core/data/src/main",
    "engine:native": "engine/native/src/main",
    "engine:ai": "engine/ai/src/main",
    "feature:search-chat": "feature/search-chat/src/main",
    "benchmark": "benchmark/src/main",
}

BASELINE_PATH = "config/quality/architecture-baseline.json"

FEATURE_MODULES = [name for name in MODULES if name.startswith("feature:")]

# path segments that make a file part of the pure-domain guardrail
DOMAIN_PATH_MARKERS = ("/domain/", "/usecase/", "/query/", "/retrieval/")
DOMAIN_FILE_MARKERS = ("retrievalpolicy", "retrieval_policy")

IMPORT_RE = re.compile(r"^\s*import\s+([\w.]+)\s*$")
PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)")
TYPE_RE = re.compile(
    r"^(?:public\s+|internal\s+|private\s+|protected\s+)?"
    r"(?:abstract\s+|sealed\s+|data\s+|enum\s+|annotation\s+|open\s+|inner\s+|value\s+)*"
    r"(?:class|interface|object|typealias)\s+([A-Za-z_]\w*)"
)
APP_FQN_RE = re.compile(r"com\.example\.powerai(?:\.[A-Za-z_]\w*)+")


def posix(path: str) -> str:
    return path.replace(os.sep, "/")


def iter_source_files(module: str):
    base = os.path.join(REPO_ROOT, MODULES[module])
    for dirpath, dirnames, filenames in os.walk(base):
        dirnames.sort()
        for name in sorted(filenames):
            if name.endswith((".kt", ".java")):
                yield os.path.join(dirpath, name)


def read_lines(path: str) -> list[str]:
    with open(path, encoding="utf-8", errors="replace") as handle:
        return handle.read().splitlines()


def rel(path: str) -> str:
    return posix(os.path.relpath(path, REPO_ROOT))


def module_packages_and_types(module: str) -> tuple[set[str], dict[str, set[str]]]:
    """Return the packages and the top-level types (name -> set of packages)."""
    packages: set[str] = set()
    types: dict[str, set[str]] = {}
    for path in iter_source_files(module):
        package = None
        for line in read_lines(path):
            if package is None:
                match = PACKAGE_RE.match(line)
                if match:
                    package = match.group(1)
                    packages.add(package)
                continue
            if not line or line[0].isspace():
                continue
            match = TYPE_RE.match(line)
            if match:
                types.setdefault(match.group(1), set()).add(package)
    return packages, types


def check_feature_depends_on_app() -> list[str]:
    violations: list[str] = []

    # 1a. Gradle level
    for module in FEATURE_MODULES:
        module_dir = os.path.dirname(MODULES[module])
        build_file = os.path.join(REPO_ROOT, module_dir, "build.gradle.kts")
        if not os.path.exists(build_file):
            continue
        for number, line in enumerate(read_lines(build_file), start=1):
            if 'project(":app")' in line:
                violations.append(
                    "%s:%d gradle dependency on :app" % (rel(build_file), number)
                )

    # 1b. source level: references that resolve into app-only packages
    app_packages, _ = module_packages_and_types("app")
    other_packages: set[str] = set()
    for module in MODULES:
        if module == "app":
            continue
        packages, _ = module_packages_and_types(module)
        other_packages |= packages
    app_only_packages = app_packages - other_packages
    all_packages = app_packages | other_packages
    if not app_only_packages:
        return violations

    def owning_package(fqn: str) -> str:
        """Longest declared package that is a segment-prefix of ``fqn``."""
        best = ""
        for package in all_packages:
            if fqn == package or fqn.startswith(package + "."):
                if len(package) > len(best):
                    best = package
        return best

    import_re = re.compile(r"^\s*import\s+([\w.]+)")
    for module in FEATURE_MODULES:
        for path in iter_source_files(module):
            for number, line in enumerate(read_lines(path), start=1):
                match = import_re.match(line)
                candidates = [match.group(1)] if match else APP_FQN_RE.findall(line)
                for fqn in candidates:
                    if owning_package(fqn) in app_only_packages:
                        kind = "import" if match else "reference"
                        violations.append(
                            "%s:%d %s %s (app-only package)" % (rel(path), number, kind, fqn)
                        )
    return sorted(set(violations))


def check_domain_has_no_android_imports() -> list[str]:
    violations: list[str] = []
    for module in MODULES:
        for path in iter_source_files(module):
            normalized = "/" + rel(path).lower() + "/"
            is_domain = any(marker in normalized for marker in DOMAIN_PATH_MARKERS)
            if not is_domain:
                base = os.path.basename(path).lower()
                is_domain = any(marker in base for marker in DOMAIN_FILE_MARKERS)
            if not is_domain:
                continue
            for number, line in enumerate(read_lines(path), start=1):
                match = IMPORT_RE.match(line)
                if match and (
                    match.group(1).startswith("android.")
                    or match.group(1).startswith("androidx.")
                ):
                    violations.append("%s:%d import %s" % (rel(path), number, match.group(1)))
    return sorted(set(violations))


def duplicate_top_level_types() -> dict[str, list[str]]:
    owners: dict[str, set[str]] = {}
    for module in MODULES:
        _packages, types = module_packages_and_types(module)
        for name in types:
            owners.setdefault(name, set()).add(module)
    return {
        name: sorted(modules)
        for name, modules in sorted(owners.items())
        if len(modules) > 1
    }


def current_state() -> dict:
    return {
        "androidImportsInDomain": check_domain_has_no_android_imports(),
        "duplicateTopLevelTypes": duplicate_top_level_types(),
        "featureAppDependsOn": check_feature_depends_on_app(),
    }


def load_baseline() -> dict | None:
    path = os.path.join(REPO_ROOT, BASELINE_PATH)
    if not os.path.exists(path):
        return None
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def check() -> int:
    baseline = load_baseline()
    if baseline is None:
        print("ERROR: %s is missing; run --update-baseline first." % BASELINE_PATH)
        return 1
    state = current_state()
    violations: list[str] = []

    for key in ("featureAppDependsOn", "androidImportsInDomain"):
        allowed = set(baseline.get(key, []))
        for item in state[key]:
            if item not in allowed:
                violations.append("%s: %s" % (key, item))

    allowed_duplicates: dict = baseline.get("duplicateTopLevelTypes", {})
    for name, modules in state["duplicateTopLevelTypes"].items():
        recorded = allowed_duplicates.get(name)
        if recorded is None:
            violations.append(
                "duplicateTopLevelTypes: %s now exists in %s" % (name, ", ".join(modules))
            )
        elif not set(modules).issubset(set(recorded)):
            violations.append(
                "duplicateTopLevelTypes: %s extended to %s (baseline %s)"
                % (name, ", ".join(modules), ", ".join(recorded))
            )

    if violations:
        print("architecture guardrail FAILED (%d violation(s)):" % len(violations))
        for item in violations:
            print("  - %s" % item)
        print(
            "Known history lives in %s; only new violations must be fixed." % BASELINE_PATH
        )
        return 1

    print(
        "architecture guardrail OK: 0 new violations "
        "(baseline: %d feature/app refs, %d domain Android imports, %d known duplicates)"
        % (
            len(baseline.get("featureAppDependsOn", [])),
            len(baseline.get("androidImportsInDomain", [])),
            len(baseline.get("duplicateTopLevelTypes", {})),
        )
    )
    return 0


def update_baseline() -> int:
    state = current_state()
    path = os.path.join(REPO_ROOT, BASELINE_PATH)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(state, handle, indent=2, sort_keys=True)
        handle.write("\n")
    print("wrote %s" % BASELINE_PATH)
    print("  feature/app refs: %d" % len(state["featureAppDependsOn"]))
    print("  domain Android imports: %d" % len(state["androidImportsInDomain"]))
    print("  duplicate top-level types: %d" % len(state["duplicateTopLevelTypes"]))
    return 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--update-baseline", action="store_true")
    args = parser.parse_args(argv)
    if args.update_baseline:
        return update_baseline()
    return check()


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
