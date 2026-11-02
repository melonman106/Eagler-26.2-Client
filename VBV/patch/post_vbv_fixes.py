#!/usr/bin/env python3
"""Post-process the pinned VBV patch with fixes that must not be overwritten by it."""
from pathlib import Path
import re, shutil, sys
ROOT=Path(sys.argv[1]).resolve(); CLIENT=Path(__file__).resolve().parent.parent; SRC=CLIENT/"src/main/java/com/melonman106/vbvclient"; GAME=ROOT/"game/src/main/java"; RES=ROOT/"game/src/main/resources"

def java_files_named(name): return list(GAME.rglob(name))
def matching_brace(text,brace):
    depth=0;i=brace;state="code";escaped=False
    while i<len(text):
        c=text[i];n=text[i+1] if i+1<len(text) else ""
        if state=="code":
            if c=='"':state="string"
            elif c=="'":state="char"
            elif c=="/" and n=="/":state="line";i+=1
            elif c=="/" and n=="*":state="block";i+=1
            elif c=="{":depth+=1
            elif c=="}":
                depth-=1
                if depth==0:return i
        elif state in ("string","char"):
            q='"' if state=="string" else "'"
            if escaped:escaped=False
            elif c=="\\":escaped=True
            elif c==q:state="code"
        elif state=="line":
            if c=="\n":state="code"
        elif state=="block" and c=="*" and n=="/":state="code";i+=1
        i+=1
    return -1

dest=GAME/"com/melonman106/vbvclient";dest.mkdir(parents=True,exist_ok=True)
for source in SRC.rglob("*.java"):
    target=dest/source.relative_to(SRC);target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)

# Copy the supplied Mod Menu icons into the generated resource namespace.
icon_root=CLIENT/"PackIcons"; icon_dst=RES/"assets/vbvclient/textures/mods"; icon_dst.mkdir(parents=True,exist_ok=True)
for src_name,dst_name in (("ShulkerBoxTooltips.jpeg","shulkerboxtooltip.jpeg"),("Viabackportvisuals-client.png","viabackportvisuals-client.png")):
    src=icon_root/src_name
    if src.is_file(): shutil.copy2(src,icon_dst/dst_name)

mc_candidates=java_files_named("Minecraft.java")
if not mc_candidates:raise RuntimeError("Minecraft.java not found")
minecraft=next((p for p in mc_candidates if p.as_posix().endswith("/net/minecraft/client/Minecraft.java")),mc_candidates[0]);mc_text=minecraft.read_text(encoding="utf-8")
start_call="com.melonman106.vbvclient.fabriccompat.FabricCompatClient.startTick(this);";end_call="com.melonman106.vbvclient.fabriccompat.FabricCompatClient.endTick(this);";legacy_call="com.melonman106.vbvclient.fabriccompat.FabricCompatClient.tick(this);"
if legacy_call in mc_text:mc_text=mc_text.replace(legacy_call,end_call,1)
if start_call not in mc_text:
    m=re.search(r"(?:public|protected|private)\s+void\s+tick\s*\(\s*\)\s*\{",mc_text)
    if not m:raise RuntimeError("Minecraft.tick() not found")
    b=mc_text.find("{",m.start());mc_text=mc_text[:b+1]+"\n        "+start_call+mc_text[b+1:]
if end_call not in mc_text:raise RuntimeError("Fabric END tick hook not found")
minecraft.write_text(mc_text,encoding="utf-8")

hud_candidates=list(GAME.rglob("Hud.java"))
if hud_candidates:
    hud=hud_candidates[0];t=hud.read_text(encoding="utf-8")
    call="com.melonman106.vbvclient.VBVAppleSkin.render("
    if call in t:
        t=re.sub(r"(?m)^\s*com\.melonman106\.vbvclient\.VBVAppleSkin\.render\([^;]+;\s*\n?","",t)
        food=re.search(r"(?:public|protected|private)\s+void\s+extractFood\s*\([^)]*\)\s*\{",t)
        if food:
            b=t.find("{",food.start());e=matching_brace(t,b);p=re.search(r"GuiGraphicsExtractor\s+(\w+)",food.group(0))
            if e>=0 and p:t=t[:e]+"\n        com.melonman106.vbvclient.VBVAppleSkin.render("+p.group(1)+");\n    "+t[e:]
    marker="VBV_LOW_HEALTH_PULSE_HOOK"
    if marker not in t:
        methods=list(re.finditer(r"(?:public|protected|private)\s+void\s+extractRenderState\s*\([^)]*GuiGraphicsExtractor\s+(\w+)[^)]*\)\s*\{",t))
        if methods:
            m=methods[0];b=t.find("{",m.start());e=matching_brace(t,b)
            if e<0:raise RuntimeError("Could not locate Hud.extractRenderState end")
            g=m.group(1);t=t[:e]+f"\n        // {marker}\n        com.melonman106.vbvclient.VBVLowHealthPulse.render({g});\n    "+t[e:]
    hud.write_text(t,encoding="utf-8")

storage=Path(__file__).resolve().parent/"storage_manager.js"
if storage.is_file():
    s=storage.read_text(encoding="utf-8")
    s=re.sub(r"\n\s*function addLauncher\(\)\s*\{.*?\n\s*\}\s*\n\s*if \(document\.readyState===\"loading\"\).*?window\.VBVStorage\.initialize\(\);\s*\}\s*else \{.*?\}\(\);","\n  if (document.readyState === \"loading\") document.addEventListener(\"DOMContentLoaded\", () => window.VBVStorage.initialize());\n  else window.VBVStorage.initialize();",s,flags=re.S)
    storage.write_text(s,encoding="utf-8")
print("Post-VBV fixes complete, including icons, low-health pulse, and native storage-only UI.")
