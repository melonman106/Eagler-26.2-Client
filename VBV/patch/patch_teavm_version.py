#!/usr/bin/env python3
"""Upgrade the generated U2 workspace to TeaVM 0.13.1."""
from pathlib import Path
import re
import sys

ROOT = Path(sys.argv[1]).resolve()
if not ROOT.is_dir():
    raise SystemExit(f"::error::TeaVM patch root does not exist: {ROOT}")

files = [
    ROOT / "platform-teavm" / "build.gradle.kts",
    ROOT / "target_teavm" / "build.gradle.kts",
    ROOT / "teavm-compat" / "build.gradle.kts",
]

changed = []
for path in files:
    if not path.is_file():
        raise SystemExit(f"::error::Expected TeaVM build file is missing: {path}")
    text = path.read_text(encoding="utf-8")
    updated = re.sub(r"(?<![0-9])0\.13\.0(?![0-9])", "0.13.1", text)
    if updated == text:
        raise SystemExit(f"::error::No TeaVM 0.13.0 declaration found in {path}")
    path.write_text(updated, encoding="utf-8")
    changed.append(str(path.relative_to(ROOT)))

for path in files:
    text = path.read_text(encoding="utf-8")
    if "0.13.1" not in text:
        raise SystemExit(f"::error::TeaVM 0.13.1 verification failed: {path}")
    if re.search(r"(?<![0-9])0\.13\.0(?![0-9])", text):
        raise SystemExit(f"::error::TeaVM 0.13.0 remains in Gradle file: {path}")

print("[VBV TeaVM] upgraded generated U2 Gradle dependencies from 0.13.0 to 0.13.1")
for item in changed:
    print(f"[VBV TeaVM] patched {item}")
