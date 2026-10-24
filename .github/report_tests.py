"""Turns Gradle test output into GitHub annotations: one summary notice, failures as errors."""
import glob
import os
import sys
import xml.etree.ElementTree as ET


def esc(text):
    return text.replace("%", "%25").replace("\r", "").replace("\n", "%0A")


log_path, results_dir = sys.argv[1], sys.argv[2]
title = sys.argv[3] if len(sys.argv) > 3 else "Unit tests"
total = failed = skipped = 0
failures = []
for path in glob.glob(os.path.join(results_dir, "**", "*.xml"), recursive=True):
    suite = ET.parse(path).getroot()
    for case in suite.iter("testcase"):
        total += 1
        if case.find("skipped") is not None:
            skipped += 1
        problem = case.find("failure")
        if problem is None:
            problem = case.find("error")
        if problem is not None:
            failed += 1
            detail = (problem.get("message") or "") + "\n" + (problem.text or "")[:1500]
            failures.append(f"{case.get('classname')}.{case.get('name')}: {detail}")

print(f"::notice title={title}::{total} tests, {failed} failed, {skipped} skipped")
for f in failures[:9]:
    print(f"::error title=Test failure::{esc(f)}")
if len(failures) > 9:
    print(f"::error title=Test failures::{esc(chr(10).join(x.splitlines()[0] for x in failures[9:]))}")

log = open(log_path, errors="replace").read() if os.path.exists(log_path) else ""
# No results usually means compilation failed; a failed build without failed tests too (e.g. the instrumented
# test didn't compile after the unit tests passed): show the relevant Gradle output.
build_failed = "BUILD FAILED" in log
if total == 0 or (build_failed and not failed):
    lines = log.splitlines()
    keep = [l for l in lines if l.startswith("e: ") or "error:" in l or "FAILED" in l]
    for i, l in enumerate(lines):
        if "What went wrong" in l:
            keep += lines[i:i + 20]
    print(f"::error title=Gradle output::{esc(chr(10).join(keep[-60:]) or chr(10).join(lines[-60:]))}")
    print(f"::error title=Gradle output (end)::{esc(chr(10).join(lines[-40:]))}")

# Gradle can pass without running anything (e.g. the APK failed to install), so no tests is a failure.
sys.exit(1 if failed or total == 0 or build_failed else 0)
