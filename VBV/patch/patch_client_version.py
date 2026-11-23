#!/usr/bin/env python3
"""Stamp the client version chosen in the build workflow into the generated client.

The workflow asks for a client version (for example 2.5.0) and exports it as
VBV_CLIENT_VERSION. `VBVClient.VERSION` is what the boot pages, the in-game
loading screen, the Mods list, the diagnostics export and the GitHub release
update check read, so this stage writes that exact string into the generated
source. A local patch run without the variable keeps the checked-in version.
"""
from pathlib import Path
import os
import re
import sys

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

generated_root = Path(sys.argv[1]).resolve()
client = generated_root / "game" / "src" / "main" / "java" / "com" / "melonman106" / "vbvclient" / "VBVClient.java"
if not client.is_file():
    raise SystemExit(f"::error::Required client source is missing: {client}")

version = os.environ.get("VBV_CLIENT_VERSION", "").strip()
text = client.read_text(encoding="utf-8")
pattern = re.compile(r'(?m)^([ \t]*public static final String VERSION = ")([^"\r\n]*)(";)$')
matches = pattern.findall(text)
if len(matches) != 1:
    raise SystemExit(
        f"::error::VBVClient.VERSION declaration matched {len(matches)} time(s); expected exactly 1"
    )

if not version:
    print(f"[VBV version] VBV_CLIENT_VERSION is not set; keeping v{matches[0][1]}")
    raise SystemExit(0)

if not re.fullmatch(r"[0-9A-Za-z][0-9A-Za-z._+-]{0,31}", version):
    raise SystemExit(
        "::error::Invalid client version; use 1-32 characters from letters, digits, '.', '_', '+' or '-'"
    )

client.write_text(pattern.sub(lambda m: m.group(1) + version + m.group(3), text, count=1), encoding="utf-8")
print(f"[VBV version] stamped client version v{version}")
