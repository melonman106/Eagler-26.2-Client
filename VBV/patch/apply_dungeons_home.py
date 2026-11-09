#!/usr/bin/env python3
"""Build the Dungeons II-inspired title screen and title-screen-only animated panorama overlay."""
from pathlib import Path
import os, re, shutil, subprocess, sys
ROOT=Path(sys.argv[1]).resolve(); CLIENT=Path(__file__).resolve().parent.parent
GAME=ROOT/"game/src/main/java"; RES=ROOT/"game/src/main/resources"

def extract_gif(gif,out_dir):
    out_dir.mkdir(parents=True,exist_ok=True)
    for old in out_dir.glob("frame_*.png"): old.unlink()
    try:
        from PIL import Image
        with Image.open(gif) as image:
            for i in range(getattr(image,"n_frames",1)):
                image.seek(i); image.convert("RGBA").save(out_dir/f"frame_{i}.png")
        return len(list(out_dir.glob("frame_*.png")))
    except Exception:
        convert=shutil.which("magick") or shutil.which("convert")
        if not convert: raise RuntimeError("slime.gif is present but neither Pillow nor ImageMagick is available")
        subprocess.run([convert,str(gif),str(out_dir/"frame_%d.png")],check=True)
        return len(list(out_dir.glob("frame_*.png")))

def find_slime_gif():
    for p in (CLIENT/"LoadingScreen/slime.gif",CLIENT/"slime.gif"):
        if p.is_file(): return p
    return None

def patch_loading_animation(count):
    files=list(GAME.rglob("VBVServerLoadingScreen.java"))
    if not files: raise RuntimeError("VBVServerLoadingScreen.java not found")
    path=files[0]; source=path.read_text(encoding="utf-8")
    if count:
        entries=",\n            ".join(f'Identifier.parse("vbvclient:textures/loadingscreen/slime/frame_{i}.png")' for i in range(count))
        replacement="private static Identifier[] buildSlimeFrames() {\n        return new Identifier[] {\n            "+entries+"\n        };\n    }"
        source,n=re.subn(r"private static Identifier\[\] buildSlimeFrames\(\)\s*\{.*?\n    \}",replacement,source,count=1,flags=re.S)
        if n!=1: raise RuntimeError("Could not update generated slime frame list")
        path.write_text(source,encoding="utf-8")

def suppress_vanilla_title_branding():
    candidates=list(GAME.rglob("TitleScreen.java"))
    if not candidates: raise RuntimeError("TitleScreen.java not found")
    path=next((p for p in candidates if p.as_posix().endswith("/net/minecraft/client/gui/screens/TitleScreen.java")),candidates[0]); source=path.read_text(encoding="utf-8")
    marker="VBV_DUNGEONS_SUPPRESS_VANILLA_BRANDING"
    if marker in source:return
    for old in ("this.logoRenderer.extractRenderState(graphics, this.width, this.logoRenderer.keepLogoThroughFade() ? 1.0F : widgetFade);","this.splash.extractRenderState(graphics, this.width, this.font, widgetFade);","graphics.text(this.font, versionString, 2, this.height - 10, ARGB.white(widgetFade));"):
        if old in source: source=source.replace(old,"if (!(this instanceof com.melonman106.vbvclient.VBVDungeonsTitleScreen)) { "+old+" }",1)
    source=source.replace("public class TitleScreen","public class TitleScreen /* "+marker+" */",1); path.write_text(source,encoding="utf-8")

def patch_title_screen():
    generated=list(GAME.rglob("VBVDungeonsTitleScreen.java")); template=CLIENT/"src/main/java/com/melonman106/vbvclient/VBVDungeonsTitleScreen.java"
    if not generated or not template.is_file(): raise RuntimeError("VBV home-screen template missing")
    generated[0].write_text(template.read_text(encoding="utf-8"),encoding="utf-8")

def configure_panorama_overlay():
    root=CLIENT/"panorama"; png=root/"panorama_overlay.png"; gif=root/"panorama_overlay.gif"; mp4=root/"panorama_overlay.mp4"
    animated=mp4 if mp4.is_file() else gif if gif.is_file() else None
    frame_dir=RES/"assets/vbvclient/textures/panorama_overlay"; frame_dir.mkdir(parents=True,exist_ok=True)
    for old in frame_dir.glob("frame_*.png"): old.unlink()
    title_files=list(GAME.rglob("TitleScreen.java"))
    title=next((p for p in title_files if p.as_posix().endswith("/net/minecraft/client/gui/screens/TitleScreen.java")),title_files[0]) if title_files else None
    if animated:
        ffmpeg=shutil.which("ffmpeg")
        if not ffmpeg: raise RuntimeError("panorama_overlay.gif/mp4 is present but FFmpeg is unavailable")
        # Menu backdrop frames: 8-bit palette PNGs at a modest size. Full-size RGBA frames
        # made a 13 MB video grow to ~230 MB of PNGs (and ~700 MB of GPU textures).
        # The browser decodes palette PNGs natively, so they load like any other texture.
        # Override with VBV_PANORAMA_FPS / VBV_PANORAMA_WIDTH if you want to trade size for quality.
        fps=max(1,min(30,int(os.environ.get("VBV_PANORAMA_FPS","10")))); max_w=max(160,int(os.environ.get("VBV_PANORAMA_WIDTH","854")))
        vf=(f"fps={fps},scale=w='min({max_w},iw)':h=-2:flags=lanczos,split[a][b];"
            "[a]palettegen=stats_mode=single[p];[b][p]paletteuse=new=1:dither=bayer:bayer_scale=4")
        subprocess.run([ffmpeg,"-y","-hide_banner","-loglevel","error","-i",str(animated),"-an","-vf",vf,"-vsync","0","-compression_level","9",str(frame_dir/"frame_%04d.png")],check=True)
        frames=sorted(frame_dir.glob("frame_*.png")); java_files=list(GAME.rglob("VBVPanoramaOverlay.java"))
        if not frames or not java_files: raise RuntimeError("Panorama animation produced no frames")
        java=java_files[0]; source=java.read_text(encoding="utf-8"); entries=",\n        ".join(f'Identifier.parse("vbvclient:textures/panorama_overlay/{p.name}")' for p in frames)
        source=re.sub(r"private static final Identifier\[\] FRAMES = new Identifier\[\] \{.*?\};","private static final Identifier[] FRAMES = new Identifier[] {\n        "+entries+"\n    };",source,count=1,flags=re.S); source=re.sub(r"FRAME_MS = \d+L",f"FRAME_MS = {round(1000/fps)}L",source,count=1); java.write_text(source,encoding="utf-8")
    elif png.is_file():
        shutil.copy2(png,frame_dir/"frame_0001.png")
        java_files=list(GAME.rglob("VBVPanoramaOverlay.java"))
        if java_files:
            java=java_files[0]; source=java.read_text(encoding="utf-8")
            source=re.sub(r"private static final Identifier\[\] FRAMES = new Identifier\[\] \{.*?\};","private static final Identifier[] FRAMES = new Identifier[] {\n        Identifier.parse(\"vbvclient:textures/panorama_overlay/frame_0001.png\")\n    };",source,count=1,flags=re.S); java.write_text(source,encoding="utf-8")
    else:
        return

    # Hook Screen.extractPanorama(): the one method every menu-with-a-panorama goes through
    # (TitleScreen, options, world/server lists, generic message screens, loading screens).
    # Screen.extractBackground only calls it while no level is loaded, so inventory, chat,
    # pause and other in-world screens are unaffected.
    screen_files=[p for p in GAME.rglob("Screen.java") if p.as_posix().endswith("/net/minecraft/client/gui/screens/Screen.java")]
    if not screen_files: raise RuntimeError("Screen.java not found for panorama overlay hook")
    screen=screen_files[0]; ss=screen.read_text(encoding="utf-8"); marker="VBV_PANORAMA_OVERLAY_RENDER"
    if marker not in ss:
        anchor="this.minecraft.gameRenderer.panorama().extractRenderState(graphics, this.width, this.height);"
        if ss.count(anchor)!=1: raise RuntimeError("Could not uniquely locate Screen.extractPanorama panorama render call")
        hook=anchor+f"\n      // {marker}\n      com.melonman106.vbvclient.VBVPanoramaOverlay.render(graphics, this.width, this.height);"
        screen.write_text(ss.replace(anchor,hook,1),encoding="utf-8")
    # An older build hooked TitleScreen directly; drop it so the overlay is never drawn twice.
    if title is not None:
        ts=title.read_text(encoding="utf-8"); old_hook="\n      // VBV_PANORAMA_OVERLAY_RENDER\n      com.melonman106.vbvclient.VBVPanoramaOverlay.render(graphics, this.width, this.height);"
        if old_hook in ts: title.write_text(ts.replace(old_hook,"",1),encoding="utf-8")

def _matching_brace(text, brace):
    depth=0; i=brace; quote=None; escaped=False
    while i<len(text):
        c=text[i]; n=text[i+1] if i+1<len(text) else ""
        if quote:
            if escaped: escaped=False
            elif c=="\\": escaped=True
            elif c==quote: quote=None
        elif c in ('"', "'"): quote=c
        elif c=="/" and n=="/":
            i=text.find("\n",i)
            if i<0:return len(text)-1
        elif c=="/" and n=="*":
            i=text.find("*/",i+2)
            if i<0:return len(text)-1
            i+=1
        elif c=="{": depth+=1
        elif c=="}":
            depth-=1
            if depth==0:return i
        i+=1
    return -1

def patch_platform_input_raf():
    candidates=list((ROOT/"platform-teavm/src/main/java").rglob("PlatformInput.java"))
    if not candidates:return
    path=candidates[0]; source=path.read_text(encoding="utf-8"); marker="VBV_WASM_GC_RAF_CALLBACK_BRIDGE"
    if marker in source:return
    method=re.search(r"private\s+static\s+void\s+armRequestAnimationFrame\s*\(\s*\)\s*\{",source)
    if not method: raise RuntimeError("PlatformInput.armRequestAnimationFrame() not found")
    method_open=source.find("{",method.start()); method_end=_matching_brace(source,method_open)
    if method_end<0: raise RuntimeError("Could not locate armRequestAnimationFrame() end")
    body=source[method_open+1:method_end]; lambda_match=re.search(r"Window\.requestAnimationFrame\s*\(\s*\(d\)\s*->\s*\{",body)
    if not lambda_match: raise RuntimeError("PlatformInput requestAnimationFrame lambda not found")
    lambda_open=body.find("{",lambda_match.start()); lambda_end=_matching_brace(body,lambda_open)
    if lambda_end<0: raise RuntimeError("Could not locate requestAnimationFrame lambda end")
    callback_body=body[lambda_open+1:lambda_end].replace("completed[0]", "vsyncCallbackCompleted")
    callback_decl="\n\tprivate static boolean vsyncCallbackCompleted = false;\n\n\tprivate static final org.teavm.jso.browser.AnimationFrameCallback VBV_VSYNC_CALLBACK = (d) -> {"+callback_body+"\n\t};\n\n"
    body=body[:lambda_match.start()]+"Window.requestAnimationFrame(VBV_VSYNC_CALLBACK)"+body[lambda_end+2:]
    body=body.replace("final boolean[] completed = new boolean[] { false };", "vsyncCallbackCompleted = false;").replace("completed[0]", "vsyncCallbackCompleted")
    source=source[:method_open+1]+body+source[method_end:]; source=source[:method.start()]+callback_decl+source[method.start():]
    source=source.replace("Window\\.requestAnimationFrame", "Window.requestAnimationFrame"); source=source.replace("public class PlatformInput {", "public class PlatformInput {\n\n\t// "+marker, 1)
    path.write_text(source,encoding="utf-8")

def remove_oxer_credit():
    # The upstream source has used more than one credits implementation.
    # Remove the vendor from the version constants and every generated Java
    # source string so it cannot reappear through the menu or credits screen.
    roots = [ROOT/"platform/src/main/java", GAME]
    for root in roots:
        if not root.exists():
            continue
        for path in root.rglob("*.java"):
            source = path.read_text(encoding="utf-8")
            source = source.replace('public static final String projectForkVendor = "o_xer";',
                                    'public static final String projectForkVendor = "";')
            source = source.replace('public static final String mainMenuStringF = "Made by " + projectForkVendor;',
                                    'public static final String mainMenuStringF = "";')
            source = source.replace("Made by o_xer", "")
            source = source.replace("o_xer", "")
            path.write_text(source, encoding="utf-8")

# The multiplayer connection screen uses vanilla again; no custom loading assets.
loading_assets = RES/"assets/vbvclient/textures/loadingscreen"
if loading_assets.exists():
    shutil.rmtree(loading_assets)
suppress_vanilla_title_branding(); patch_title_screen(); configure_panorama_overlay(); patch_platform_input_raf(); remove_oxer_credit()

def patch_startup_console():
    candidates = list(GAME.rglob("LoadingOverlay.java"))
    if not candidates:
        raise RuntimeError("LoadingOverlay.java not found for startup progress console")
    path = next((p for p in candidates if p.as_posix().endswith("/net/minecraft/client/gui/screens/LoadingOverlay.java")), candidates[0])
    source = path.read_text(encoding="utf-8")
    marker = "VBV_STARTUP_CONSOLE"
    if marker in source:
        return
    anchor = "int progressPercent = Mth.clamp(Math.round(this.currentProgress * 100.0F), 0, 100);"
    if source.count(anchor) != 1:
        raise RuntimeError("Could not uniquely locate Mojang loading progress percentage")
    console = anchor + """
          // VBV_STARTUP_CONSOLE: compact diagnostics panel on the red Mojang screen.
          String vbvStage = this.reload.isDone() ? "Finalizing resources" : "Loading textures, fonts and models";
          int vbvConsoleX = 12;
          int vbvConsoleY = barY - 92;
          graphics.fill(vbvConsoleX, vbvConsoleY, vbvConsoleX + 224, vbvConsoleY + 58, 0xB9000000);
          graphics.text(this.minecraft.font, "STARTUP CONSOLE", vbvConsoleX + 8, vbvConsoleY + 6, 0xFFB98AFF, true);
          graphics.text(this.minecraft.font, "> " + vbvStage, vbvConsoleX + 8, vbvConsoleY + 21, 0xFFFFFFFF, true);
          graphics.text(this.minecraft.font, "Progress: " + progressPercent + "%", vbvConsoleX + 8, vbvConsoleY + 36, 0xFFB8FFB8, true);
          int vbvFilled = progressPercent / 5;
          String vbvBar = "[" + "#".repeat(vbvFilled) + "-".repeat(20 - vbvFilled) + "]";
          graphics.text(this.minecraft.font, vbvBar, vbvConsoleX + 116, vbvConsoleY + 36, 0xFFFFFFFF, true);
"""
    path.write_text(source.replace(anchor, console, 1), encoding="utf-8")
    print("[VBV] Added startup console with current resource stage and progress percentage.")

patch_startup_console()
