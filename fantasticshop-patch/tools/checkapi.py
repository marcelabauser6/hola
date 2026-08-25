#!/usr/bin/env python3
"""
Guards against silently dropping members when a FantasticShop class is recompiled.

The patched classes are dropped into the original jar, so anything else in the jar still links
against them by exact signature. If a rewrite loses a method, nothing complains at build time -
it becomes a NoSuchMethodError the first time that screen opens. This compares the members of
every recompiled class against the original and fails if any are missing.

Usage: python3 checkapi.py <original-extracted-dir> <recompiled-dir>
"""
import re
import subprocess
import sys
import os

JAVAP = os.environ.get("JAVAP", "javap")

# Synthetic and compiler-generated members that legitimately differ between builds.
IGNORE = re.compile(r"\b(lambda\$|access\$|\$deserializeLambda\$|\$SwitchMap|values\(\)|valueOf\()")


def members(class_file):
    out = subprocess.run([JAVAP, "-p", class_file], capture_output=True, text=True).stdout
    found = set()
    for line in out.splitlines():
        line = line.strip().rstrip(";")
        if not line or line.startswith("Compiled from") or line.endswith("{") or line == "}":
            continue
        if IGNORE.search(line):
            continue
        # Private members cannot be linked against from another class, so they are free to change.
        if line.startswith("private "):
            continue
        # normalise whitespace so formatting differences do not register as changes
        found.add(" ".join(line.split()))
    return found


def main():
    original_dir, new_dir = sys.argv[1], sys.argv[2]
    problems = []
    checked = 0

    for root, _, files in os.walk(new_dir):
        for name in files:
            if not name.endswith(".class"):
                continue
            new_file = os.path.join(root, name)
            relative = os.path.relpath(new_file, new_dir)
            old_file = os.path.join(original_dir, relative)
            if not os.path.exists(old_file):
                print(f"  new class (nothing to compare): {relative}")
                continue
            checked += 1
            before, after = members(old_file), members(new_file)
            missing = before - after
            if missing:
                problems.append((relative, sorted(missing)))

    print(f"compared {checked} recompiled classes against the original jar")
    if problems:
        print(f"\n{len(problems)} class(es) LOST members:")
        for relative, missing in problems:
            print(f"  {relative}")
            for member in missing:
                print(f"      missing: {member}")
        sys.exit(1)
    print("no members lost")


if __name__ == "__main__":
    main()
