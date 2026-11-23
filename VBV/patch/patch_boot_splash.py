#!/usr/bin/env python3
"""Give the pre-game pages the VBV boot screen look.

The single HTML starts on two plain white pages - the launch countdown and the
early-load splash that prints "Unpacking assets..." - and then hands over to the
in-game VBV loading screen, which is a dark screen with the wordmark, a segmented
bar and the running step. The jump from white pages to that screen is the ugliest
part of a cold start, so this patch rebuilds both pages in the same style and
keeps the element ids the shell's own JavaScript drives (boot_status,
boot_progress, boot_progress_bar).

Nothing about the loading order or the JavaScript that starts the game changes;
only the markup and the style of the two pages, plus the percent readout.
"""
from pathlib import Path
import os
import re
import sys

MARKER = "VBV_BOOT_SPLASH"

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

ROOT = Path(sys.argv[1]).resolve()
REL = "target_teavm/src/web/index.html"
path = ROOT / REL
if not path.is_file():
    raise SystemExit(f"::error::{REL} is missing")

text = path.read_text(encoding="utf-8")
if MARKER in text:
    print("[VBV boot] custom boot pages already installed")
    raise SystemExit(0)

ACCENT = "#E8B44A"
ACCENT_DARK = "#B98A2E"
ACCENT_LIGHT = "#FFD98A"
BG = "#08070D"
DIM = "#8b8b96"
EDITION = "EAGLER 26.2 EDITION"
_build_version = os.environ.get("VBV_CLIENT_VERSION", "").strip()
if re.fullmatch(r"[0-9A-Za-z][0-9A-Za-z._+-]{0,31}", _build_version):
    EDITION += f" · v{_build_version}"

LOADING = f"""\t\t<!-- {MARKER}: the pre-game pages use the same dark VBV screen as the in-game loader. -->
\t\t<style>
\t\t\t@keyframes vbvSweep {{ from {{ left: -14%; }} to {{ left: 100%; }} }}
\t\t\t@media (prefers-reduced-motion: reduce) {{ .vbv_sweep {{ animation: none !important; }} }}
\t\t</style>
\t\t<div id="loading_screen" style="margin:0;position:fixed;inset:0;z-index:2147483646;background:{BG};display:flex;align-items:center;justify-content:center;overflow:hidden;user-select:none;-webkit-user-select:none;font-family:'Trebuchet MS','Segoe UI',sans-serif;">
\t\t\t<div style="position:absolute;left:0;right:0;bottom:0;height:55%;background:radial-gradient(ellipse at 50% 125%, rgba(232,180,74,0.20), rgba(232,180,74,0) 70%);pointer-events:none;"></div>
\t\t\t<div style="position:absolute;left:0;right:0;top:0;height:26%;background:linear-gradient(to bottom, rgba(0,0,0,0.55), rgba(0,0,0,0));pointer-events:none;"></div>
\t\t\t<div style="position:relative;width:min(78vmin,760px);text-align:center;padding:0 4vw;box-sizing:border-box;">
\t\t\t\t<div id="vbv_boot_head">
\t\t\t\t\t<div style="font-weight:800;letter-spacing:0.34em;font-size:clamp(42px,9vmin,92px);line-height:1;color:#ffffff;text-shadow:0 0 22px rgba(232,180,74,0.35);">VBV</div>
\t\t\t\t\t<div style="margin:12px auto 0;width:34%;height:1px;background:{ACCENT};opacity:0.9;"></div>
\t\t\t\t\t<div style="margin-top:9px;letter-spacing:0.42em;font-size:clamp(9px,1.5vmin,12px);color:{DIM};">{EDITION}</div>
\t\t\t\t</div>
\t\t\t\t<div style="margin-top:clamp(26px,7vmin,62px);display:flex;align-items:flex-end;justify-content:space-between;gap:14px;">
\t\t\t\t\t<div style="text-align:left;min-width:0;">
\t\t\t\t\t\t<div style="letter-spacing:0.18em;font-size:clamp(9px,1.5vmin,11px);color:{ACCENT};">NOW LOADING</div>
\t\t\t\t\t\t<div id="boot_status" style="margin-top:5px;font-size:clamp(11px,1.8vmin,14px);line-height:1.35;color:#e8e8ee;min-height:1.2em;word-break:break-word;">Starting up&#8230;</div>
\t\t\t\t\t</div>
\t\t\t\t\t<div id="vbv_pct" style="font-weight:700;font-size:clamp(24px,5vmin,42px);line-height:1;color:#ffffff;flex:none;">0%</div>
\t\t\t\t</div>
\t\t\t\t<div id="boot_progress" style="visibility:hidden;margin-top:14px;width:100%;height:10px;background:rgba(255,255,255,0.10);position:relative;overflow:hidden;">
\t\t\t\t\t<div id="boot_progress_bar" style="width:0%;height:100%;background:linear-gradient(90deg,{ACCENT_DARK},{ACCENT} 55%,{ACCENT_LIGHT});transition:width 120ms linear;"></div>
\t\t\t\t\t<div style="position:absolute;inset:0;background-image:repeating-linear-gradient(90deg, {BG} 0 6px, rgba(8,7,13,0) 6px 8px);pointer-events:none;"></div>
\t\t\t\t\t<div class="vbv_sweep" style="position:absolute;top:0;bottom:0;width:14%;background:linear-gradient(90deg,rgba(255,255,255,0),rgba(255,255,255,0.30),rgba(255,255,255,0));animation:vbvSweep 1.8s linear infinite;"></div>
\t\t\t\t</div>
\t\t\t\t<div style="margin-top:11px;display:flex;justify-content:space-between;font-size:clamp(9px,1.4vmin,11px);color:#6c6c78;letter-spacing:0.08em;">
\t\t\t\t\t<span>VBV CLIENT</span><span>EAGLERCRAFT 26.2</span>
\t\t\t\t</div>
\t\t\t</div>
\t\t</div>
"""

COUNTDOWN = f"""\t\t<!-- {MARKER}: the launch countdown matches the boot screen instead of a white page. -->
\t\t<div id="launch_countdown_screen" style="margin:0;position:fixed;inset:0;z-index:2147483647;background:{BG};width:100%;height:100%;overflow:hidden;display:flex;align-items:center;justify-content:center;user-select:none;-webkit-user-select:none;font-family:'Trebuchet MS','Segoe UI',sans-serif;">
\t\t\t<div style="position:absolute;left:0;right:0;bottom:0;height:55%;background:radial-gradient(ellipse at 50% 125%, rgba(232,180,74,0.18), rgba(232,180,74,0) 70%);pointer-events:none;"></div>
\t\t\t<div style="position:relative;width:min(78vmin,760px);padding:0 4vw;text-align:center;box-sizing:border-box;">
\t\t\t\t<div style="font-weight:800;letter-spacing:0.34em;font-size:clamp(40px,8vmin,84px);line-height:1;color:#ffffff;text-shadow:0 0 22px rgba(232,180,74,0.35);">VBV</div>
\t\t\t\t<div style="margin:12px auto 0;width:34%;height:1px;background:{ACCENT};opacity:0.9;"></div>
\t\t\t\t<div style="margin-top:10px;letter-spacing:0.42em;font-size:clamp(9px,1.5vmin,12px);color:{DIM};">EAGLERCRAFT 26.2</div>
\t\t\t\t<div style="margin-top:clamp(26px,7vmin,58px);letter-spacing:0.14em;font-size:clamp(10px,1.7vmin,13px);color:{ACCENT};">LAUNCHING IN <span id="launchCountdownNumber">5</span></div>
\t\t\t\t<div style="margin-top:12px;width:100%;height:10px;background:rgba(255,255,255,0.10);position:relative;overflow:hidden;">
\t\t\t\t\t<div id="launchCountdownProgress" style="background:linear-gradient(90deg,{ACCENT_DARK},{ACCENT} 55%,{ACCENT_LIGHT});width:0%;height:100%;"></div>
\t\t\t\t\t<div style="position:absolute;inset:0;background-image:repeating-linear-gradient(90deg, {BG} 0 6px, rgba(8,7,13,0) 6px 8px);pointer-events:none;"></div>
\t\t\t\t</div>
\t\t\t\t<div style="margin-top:22px;">
\t\t\t\t\t<button id="skipCountdown" autofocus style="font:inherit;font-size:clamp(12px,1.9vmin,15px);letter-spacing:0.12em;padding:10px 26px;cursor:pointer;color:#f4e7c8;background:rgba(232,180,74,0.10);border:1px solid rgba(232,180,74,0.55);border-radius:2px;">SKIP</button>
\t\t\t\t</div>
\t\t\t</div>
\t\t</div>
"""

# ---- 1. the two pages -------------------------------------------------------
start = text.find('<div id="loading_screen"')
countdown_at = text.find('<div id="launch_countdown_screen"')
game_frame_at = text.find('<div id="game_frame"')
if start < 0 or countdown_at < 0 or game_frame_at < 0 or not (start < countdown_at < game_frame_at):
    raise SystemExit("::error::VBV boot splash: could not find the loading / countdown / game_frame blocks in " + REL)

text = text[:start] + LOADING + COUNTDOWN + text[game_frame_at:]

# ---- 2. __eaglerBoot also drives the percent readout ------------------------
boot_fn = re.compile(
    r"[ \t]*// Java updates the early boot status and progress bar\.\r?\n"
    r"[ \t]*window\.__eaglerBoot = function\(pct, text\) \{.*?\n[ \t]*\};\r?\n",
    re.S,
)
if len(boot_fn.findall(text)) != 1:
    raise SystemExit("::error::VBV boot splash: __eaglerBoot block matched %d time(s); expected 1"
                     % len(boot_fn.findall(text)))

NEW_FN = (
    "\t\t\t\t// VBV_BOOT_SPLASH: Java updates the boot status, the bar and the percent readout.\n"
    "\t\t\t\twindow.__eaglerBoot = function(pct, text) {\n"
    "\t\t\t\t\ttry {\n"
    "\t\t\t\t\t\tvar shown = typeof pct === \"number\" ? Math.max(0, Math.min(100, pct)) : null;\n"
    "\t\t\t\t\t\tvar st = document.getElementById(\"boot_status\");\n"
    "\t\t\t\t\t\tif(st && text) { st.textContent = text; }\n"
    "\t\t\t\t\t\tvar bar = document.getElementById(\"boot_progress_bar\");\n"
    "\t\t\t\t\t\tif(bar && shown !== null) { bar.style.width = shown + \"%\"; }\n"
    "\t\t\t\t\t\tvar wrap = document.getElementById(\"boot_progress\");\n"
    "\t\t\t\t\t\tif(wrap) { wrap.style.visibility = \"visible\"; }\n"
    "\t\t\t\t\t\tvar pctEl = document.getElementById(\"vbv_pct\");\n"
    "\t\t\t\t\t\tif(pctEl && shown !== null) { pctEl.textContent = Math.round(shown) + \"%\"; }\n"
    "\t\t\t\t\t} catch(e) {}\n"
    "\t\t\t\t};\n"
)
text = boot_fn.sub(lambda _m: NEW_FN, text, count=1)

# ---- 3. the sound gate hides the wordmark block as well --------------------
gate = re.compile(
    r"([ \t]*var img = document\.getElementById\(\"boot_image\"\);\r?\n"
    r"[ \t]*if\(img\) \{ img\.style\.display = \"none\"; \}\r?\n)",
    re.S,
)
if len(gate.findall(text)) != 1:
    raise SystemExit("::error::VBV boot splash: sound-gate block matched %d time(s); expected 1"
                     % len(gate.findall(text)))
text = gate.sub(
    lambda m: m.group(1)
    + "\t\t\t\t\t\t\t\t\tvar head = document.getElementById(\"vbv_boot_head\");\n"
    + "\t\t\t\t\t\t\t\t\tif(head) { head.style.display = \"none\"; }\n"
    + "\t\t\t\t\t\t\t\t\tvar pctEl = document.getElementById(\"vbv_pct\");\n"
    + "\t\t\t\t\t\t\t\t\tif(pctEl) { pctEl.style.display = \"none\"; }\n",
    text,
    count=1,
)

for token in (MARKER, "vbv_boot_head", "vbv_pct", "launchCountdownProgress"):
    if token not in text:
        raise SystemExit(f"::error::VBV boot splash verification failed, missing: {token}")

path.write_text(text, encoding="utf-8")
print("[VBV boot] countdown and early-load pages rebuilt as the VBV boot screen")
