#!/usr/bin/env python3
"""Replace the World Map file-import @JSBody with TeaVM-safe JavaScript."""
from pathlib import Path
import re
import sys

ROOT = Path(sys.argv[1]).resolve()
SOURCE = ROOT / "game" / "src" / "main" / "java" / "com" / "melonman106" / "vbvclient" / "VBVWorldMap.java"
if not SOURCE.is_file():
    raise SystemExit(f"::error::World Map source is missing: {SOURCE}")

text = SOURCE.read_text(encoding="utf-8")
marker = "/* VBV world-map import JS patch */"
if marker in text:
    print("[VBV World Map] import JavaScript patch already installed")
    raise SystemExit(0)

pattern = re.compile(
    r'    @JSBody\(params = \{"input"\}, script = ".*?"\)\s+private static native void importBackupFromFile\(\);',
    re.DOTALL,
)
if not pattern.search(text):
    raise SystemExit("::error::Could not locate the existing importBackupFromFile @JSBody")

# Keep the Java string deliberately simple: no nested template strings and no
# arrow functions. This avoids TeaVM parser warnings in the generated JS.
script = (
    '    /* VBV world-map import JS patch */\n'
    '    @JSBody(script = "var input=document.createElement(\\\'input\\\'); input.type=\\\'file\\\'; input.accept=\\\'.txt,.vbvmap\\\'; input.onchange=function(){ var file=input.files&&input.files[0]; if(!file)return; var reader=new FileReader(); reader.onload=function(){ try { var text=String(reader.result||\\\'\\\'); var lines=text.split(/\\\\r?\\\\n/); if(lines[0]!==\\\'VBV-WORLDMAP-BACKUP-v1\\\'||!lines[1]||lines[1].indexOf(\\\'WORLD:\\')!==0) throw new Error(\\\'This is not a VBV World Map backup\\\'); var token=lines[1].substring(6); var payload=lines.slice(2).join(\\\'\\\\n\\\'); var key=\\\'vbv.worldmap.v1.\\\'+token; if(!window.VBVStorage||!window.VBVStorage.setTextSync(key,payload)) throw new Error(\\\'Could not write the selected storage folder\\\'); window.location.reload(); } catch(e) { window.alert(\\\'Could not import VBV World Map backup: \\' + e); } }; reader.readAsText(file); }; input.click();")\n'
    '    private static native void importBackupFromFile();'
)

text = pattern.sub(script, text, count=1)
SOURCE.write_text(text, encoding="utf-8")
print(f"[VBV World Map] installed TeaVM-safe import JavaScript: {SOURCE}")
