#!/usr/bin/env python3
from pathlib import Path
import sys

marker = 'button.id = "vbv-storage-launcher"'

if len(sys.argv) < 2:
    raise SystemExit("usage: remove_storage_launcher.py <generated-dir>")

root = Path(sys.argv[1]).resolve()
script = root / "game" / "src" / "main" / "resources" / "vbv" / "storage_manager.js"

# The storage manager is normally injected directly into the generated runtime,
# so also support the generated HTML when the source copy is not materialized.
targets = []
if script.is_file():
    targets.append(script)
targets.extend(root.rglob("*.js"))
targets.extend(root.rglob("*.html"))

removed = 0
for path in dict.fromkeys(targets):
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        continue
    if marker not in text:
        continue
    start = text.find("  function addLauncher() {")
    if start < 0:
        continue
    end = text.find("  if (document.readyState", start)
    if end < 0:
        continue
    replacement = "  // VBV native Minecraft Storage screen owns the UI; no floating JS launcher.\n"
    updated = text[:start] + replacement + text[end:]
    path.write_text(updated, encoding="utf-8")
    removed += 1

print(f"[VBV storage UI] removed {removed} floating JavaScript storage launcher(s)")
