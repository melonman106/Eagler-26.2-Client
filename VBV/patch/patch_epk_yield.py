#!/usr/bin/env python3
"""Make the asset pack unpack in slices instead of one blocking call.

Eaglercraft's assets.epk holds every asset in a single compressed stream, so
`EPKDownloadHelper.loadEPK` inflates it in one loop with no point where the
browser can repaint. On a slow machine that loop is seconds long: the tab looks
frozen, the boot splash stops moving, and Chrome may even show the page as not
responding. The fix does not change what is unpacked or the order it arrives in.
It only hands control back to the page for a moment every slice, and moves the
boot progress bar while it does, so the freeze becomes visible progress.

The yield starts only after the unpack has already taken `START_AFTER_MS`, so a
fast machine pays nothing at all.
"""
from pathlib import Path
import sys

MARKER = "VBV_EPK_YIELD"

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

ROOT = Path(sys.argv[1]).resolve()
REL = "platform-teavm/src/main/java/net/lax1dude/eaglercraft/v1_8/internal/teavm/EPKDownloadHelper.java"
path = ROOT / REL
if not path.is_file():
    raise SystemExit(f"::error::{REL} is missing")

text = path.read_text(encoding="utf-8")
if MARKER in text:
    print("[VBV EPK] unpack slicing already installed")
    raise SystemExit(0)

ANCHOR = (
    "\t\tEPKDecompiler dec = new EPKDecompiler(epkFileData);\n"
    "\t\ttry {\n"
    "\t\t\tEPKDecompiler.FileEntry ent;\n"
    "\t\t\twhile((ent = dec.readFile()) != null) {\n"
    "\t\t\t\tif(\"FILE\".equals(ent.type)) {\n"
    "\t\t\t\t\tloadedFiles.put(path + ent.name, ent.data);\n"
    "\t\t\t\t}\n"
    "\t\t\t}\n"
    "\t\t}finally {\n"
    "\t\t\tdec.close();\n"
    "\t\t}\n"
)

REPLACEMENT = (
    "\t\tEPKDecompiler dec = new EPKDecompiler(epkFileData);\n"
    "\t\t// VBV_EPK_YIELD: the whole pack is one compressed stream, so this loop used to inflate\n"
    "\t\t// every asset without letting the page repaint once. On a slow machine that is seconds\n"
    "\t\t// of a locked-up tab with a boot splash that cannot move.\n"
    "\t\tint vbvTotal = dec.getNumFiles();\n"
    "\t\tint vbvRead = 0;\n"
    "\t\tlong vbvStarted = System.currentTimeMillis();\n"
    "\t\tlong vbvYieldAt = vbvStarted + VBV_EPK_START_AFTER_MS;\n"
    "\t\ttry {\n"
    "\t\t\tEPKDecompiler.FileEntry ent;\n"
    "\t\t\twhile((ent = dec.readFile()) != null) {\n"
    "\t\t\t\tif(\"FILE\".equals(ent.type)) {\n"
    "\t\t\t\t\tloadedFiles.put(path + ent.name, ent.data);\n"
    "\t\t\t\t}\n"
    "\t\t\t\t++vbvRead;\n"
    "\t\t\t\tif((vbvRead & 31) == 0) {\n"
    "\t\t\t\t\tlong vbvNow = System.currentTimeMillis();\n"
    "\t\t\t\t\tif(vbvNow >= vbvYieldAt) {\n"
    "\t\t\t\t\t\tvbvYieldAt = vbvNow + VBV_EPK_SLICE_MS;\n"
    "\t\t\t\t\t\tvbvUnpackProgress(vbvRead, vbvTotal);\n"
    "\t\t\t\t\t\tPlatformRuntime.sleep(0);\n"
    "\t\t\t\t\t}\n"
    "\t\t\t\t}\n"
    "\t\t\t}\n"
    "\t\t}finally {\n"
    "\t\t\tdec.close();\n"
    "\t\t}\n"
    "\t\tif(vbvRead > 0) {\n"
    "\t\t\tlogger.info(\"Unpacked {} assets in {} ms\", Integer.valueOf(vbvRead),\n"
    "\t\t\t\t\tLong.valueOf(System.currentTimeMillis() - vbvStarted));\n"
    "\t\t}\n"
)

count = text.count(ANCHOR)
if count != 1:
    raise SystemExit(
        "::error::VBV EPK unpack anchor matched %d time(s) in %s; expected exactly 1" % (count, REL)
    )
text = text.replace(ANCHOR, REPLACEMENT, 1)

HELPER_ANCHOR = "\tprivate static String injectCacheInvalidationHack(String url, String cacheFixStr) {\n"
HELPERS = (
    "\t/** Slice length for the unpack loop: work for this long, then let the page paint. */\n"
    "\tprivate static final int VBV_EPK_SLICE_MS = 24;\n"
    "\t/** Only start slicing once the unpack is clearly slow, so fast machines pay nothing. */\n"
    "\tprivate static final long VBV_EPK_START_AFTER_MS = 1500L;\n"
    "\n"
    "\t/**\n"
    "\t * Moves the boot splash through the unpack. The splash sits at 30 % when the download\n"
    "\t * starts and at 65 % once the assets are in, so the unpack fills that gap.\n"
    "\t */\n"
    "\tprivate static void vbvUnpackProgress(int read, int total) {\n"
    "\t\tint percent = total > 0 ? (int) (((long) read * 100L) / (long) total) : -1;\n"
    "\t\tif(percent < 0) {\n"
    "\t\t\tvbvBootStage(45, \"Unpacking assets...\");\n"
    "\t\t}else {\n"
    "\t\t\tvbvBootStage(30 + (percent * 35) / 100, \"Unpacking assets \" + percent + \"%\");\n"
    "\t\t}\n"
    "\t}\n"
    "\n"
    "\t/** The same boot-splash hook ClientMain uses, and a no-op once the splash has handed off. */\n"
    "\t@org.teavm.jso.JSBody(params = { \"pct\", \"text\" }, script =\n"
    "\t\t\t\"try { if(typeof window.__eaglerBoot === 'function') window.__eaglerBoot(pct, text); } catch(e) {}\")\n"
    "\tprivate static native void vbvBootStage(int pct, String text);\n"
    "\n"
)

if text.count(HELPER_ANCHOR) != 1:
    raise SystemExit(
        "::error::VBV EPK helper anchor matched %d time(s) in %s; expected exactly 1"
        % (text.count(HELPER_ANCHOR), REL)
    )
text = text.replace(HELPER_ANCHOR, HELPERS + HELPER_ANCHOR, 1)

required = [MARKER, "PlatformRuntime.sleep(0);", "vbvUnpackProgress(vbvRead, vbvTotal);"]
for token in required:
    if token not in text:
        raise SystemExit(f"::error::VBV EPK patch verification failed, missing: {token}")

path.write_text(text, encoding="utf-8")
print(f"[VBV EPK] unpack now slices every {24} ms after the first 1.5 s and drives the boot splash")
