#!/usr/bin/env python3
"""Start on the main menu instead of the profile/skin screen.

Eaglercraft opens `EaglerProfileScreen26` wrapped around the title screen on every
launch, so the first thing a player sees is the skin editor. The profile editor is
still reachable from the title screen's Profile button and from Extras > Hero, so
skipping it at start-up loses nothing and gets to the menu one screen sooner.
"""
from pathlib import Path
import sys

MARKER = "VBV_SKIP_PROFILE"
BRANCH = "} else if (net.lax1dude.eaglercraft.v1_8.minecraft.EaglerHosted.isActive()"
NEXT_BRANCH = "} else {"

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

ROOT = Path(sys.argv[1]).resolve()
REL = "game/src/main/java/net/minecraft/client/gui/Gui.java"
path = ROOT / REL
if not path.is_file():
    raise SystemExit(f"::error::{REL} is missing")

text = path.read_text(encoding="utf-8")
if MARKER in text:
    print("[VBV profile] start-up already goes straight to the title screen")
    raise SystemExit(0)

branch_at = text.find(BRANCH)
if branch_at < 0 or text.count(BRANCH) != 1:
    raise SystemExit(
        "::error::VBV profile skip: the first-launch branch matched %d time(s) in %s; expected 1"
        % (text.count(BRANCH), REL)
    )

line_start = text.rfind("\n", 0, branch_at) + 1
next_at = text.find(NEXT_BRANCH, branch_at)
if next_at < 0:
    raise SystemExit("::error::VBV profile skip: could not find the else branch after the profile screen")

replacement = (
    "         } else {\n"
    "            // VBV_SKIP_PROFILE: straight to the title screen. The profile and skin editor is\n"
    "            // still one click away on the title screen, so nothing is lost by not opening it here."
)
text = text[:line_start] + replacement + text[next_at + len(NEXT_BRANCH):]

required = [
    MARKER,
    "this.setScreen(new TitleScreen(true, new LogoRenderer(onboardingScreenAdded)));",
]
for token in required:
    if token not in text:
        raise SystemExit(f"::error::VBV profile skip verification failed, missing: {token}")
if text.count(BRANCH) != 0:
    raise SystemExit("::error::VBV profile skip verification failed: the profile branch is still present")

path.write_text(text, encoding="utf-8")
print("[VBV profile] start-up now opens the title screen without the profile screen in front of it")
