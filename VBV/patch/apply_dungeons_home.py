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
    # The Eagler-hosted branch draws two text lines in the bottom-left corner ("Eaglercraft <version>" and
    # "Rewritten by <vendor>"). With the vendor removed it left a dangling "Rewritten by", so both go.
    branding=re.compile(r'graphics\.text\(this\.font, Component\.translatableWithFallback\("eagler\.menu\.(?:brand|rewrittenBy)".*?ARGB\.white\(widgetFade\)\);',re.S)
    source=branding.sub(lambda m:"if (!(this instanceof com.melonman106.vbvclient.VBVDungeonsTitleScreen)) { "+m.group(0)+" }",source)
    for old in ("this.logoRenderer.extractRenderState(graphics, this.width, this.logoRenderer.keepLogoThroughFade() ? 1.0F : widgetFade);","this.splash.extractRenderState(graphics, this.width, this.font, widgetFade);","graphics.text(this.font, versionString, 2, this.height - 10, ARGB.white(widgetFade));"):
        if old in source: source=source.replace(old,"if (!(this instanceof com.melonman106.vbvclient.VBVDungeonsTitleScreen)) { "+old+" }",1)
    source=source.replace("public class TitleScreen","public class TitleScreen /* "+marker+" */",1); path.write_text(source,encoding="utf-8")

def patch_title_screen():
    generated=list(GAME.rglob("VBVDungeonsTitleScreen.java")); template=CLIENT/"src/main/java/com/melonman106/vbvclient/VBVDungeonsTitleScreen.java"
    if not generated or not template.is_file(): raise RuntimeError("VBV home-screen template missing")
    generated[0].write_text(template.read_text(encoding="utf-8"),encoding="utf-8")

PANORAMA_VIDEO_EXTS=(".mp4",".webm",".mov",".gif")

def _panorama_slug(stem,taken):
    slug=re.sub(r"[^a-z0-9]+","_",stem.lower()).strip("_") or "panorama"
    base=slug; n=2
    while slug in taken: slug=f"{base}_{n}"; n+=1
    taken.add(slug); return slug

def _panorama_title(stem):
    spaced=re.sub(r"(?<=[a-z0-9])(?=[A-Z])"," ",stem); spaced=re.sub(r"[_\-]+"," ",spaced).strip()
    return " ".join(w[:1].upper()+w[1:] for w in spaced.split()) or "Panorama"

def _panorama_sources(root):
    """Every video / GIF / still image in VBV/panorama, the long-standing default first, then by name."""
    files=[p for p in sorted(root.iterdir(),key=lambda q:q.name.lower()) if p.is_file() and p.suffix.lower() in PANORAMA_VIDEO_EXTS+(".png",)]
    files.sort(key=lambda p:(0 if p.stem.lower()=="panorama_overlay" else 1))
    return files

def configure_panorama_overlay():
    root=CLIENT/"panorama"
    sources=_panorama_sources(root) if root.is_dir() else []
    frame_root=RES/"assets/vbvclient/textures/panorama_overlay"
    if frame_root.exists(): shutil.rmtree(frame_root)
    frame_root.mkdir(parents=True,exist_ok=True)
    title_files=list(GAME.rglob("TitleScreen.java"))
    title=next((p for p in title_files if p.as_posix().endswith("/net/minecraft/client/gui/screens/TitleScreen.java")),title_files[0]) if title_files else None
    if not sources:
        return
    # Menu backdrop frames: 8-bit palette PNGs (the game decodes textures with a PNG-only decoder, so JPEG/WebP
    # are not an option). The game downloads and unpacks every frame of every clip at start-up, so the frame
    # volume is most of the load time: at 10 fps and 1280 px the four bundled clips were ~152 MB of a 178 MB
    # assets pack. Frames are therefore stored at 5 fps and VBVPanoramaOverlay fades the next frame in over
    # the current one (34-42 dB against the dropped 10 fps frames on the bundled clips). The default panorama
    # keeps VBV_PANORAMA_WIDTH (1280 px; 854 px looked soft once stretched over a 1366/1920 px canvas and
    # bayer dithering added a cross-hatch); the other clips use VBV_PANORAMA_EXTRA_WIDTH (1024 px). Full-size
    # RGBA frames would be ~230 MB for one 13 MB video (and ~700 MB of GPU textures), so palette PNGs stay and
    # VBVPanoramaOverlay only keeps the frames around the playhead loaded. Override with VBV_PANORAMA_FPS,
    # VBV_PANORAMA_WIDTH, VBV_PANORAMA_EXTRA_WIDTH and VBV_PANORAMA_DITHER (sierra2, sierra2_4a,
    # floyd_steinberg, bayer, none) to trade size for quality.
    fps=max(1,min(30,int(os.environ.get("VBV_PANORAMA_FPS","5"))))
    max_w=max(160,int(os.environ.get("VBV_PANORAMA_WIDTH","1280")))
    extra_w=max(160,int(os.environ.get("VBV_PANORAMA_EXTRA_WIDTH","1024")))
    dither=os.environ.get("VBV_PANORAMA_DITHER","sierra2").strip()
    if dither not in ("sierra2","sierra2_4a","floyd_steinberg","bayer","none"): dither="sierra2"
    dither_arg="dither=bayer:bayer_scale=4" if dither=="bayer" else f"dither={dither}"
    ffmpeg=shutil.which("ffmpeg")
    taken=set(); entries=[]
    for src in sources:
        slug=_panorama_slug(src.stem,taken); out=frame_root/slug; out.mkdir(parents=True,exist_ok=True)
        clip_w=max_w if slug=="panorama_overlay" else min(max_w,extra_w)
        if src.suffix.lower()==".png":
            shutil.copy2(src,out/"frame_0001.png")
        else:
            if not ffmpeg: raise RuntimeError(f"{src.name} is in VBV/panorama but FFmpeg is unavailable")
            vf=(f"fps={fps},scale=w='min({clip_w},iw)':h=-2:flags=lanczos,split[a][b];"
                f"[a]palettegen=stats_mode=single[p];[b][p]paletteuse=new=1:{dither_arg}")
            subprocess.run([ffmpeg,"-y","-hide_banner","-loglevel","error","-i",str(src),"-an","-vf",vf,"-vsync","0","-compression_level","9",str(out/"frame_%04d.png")],check=True)
        count=len(list(out.glob("frame_*.png")))
        if count==0: raise RuntimeError(f"Panorama {src.name} produced no frames")
        entries.append((slug,_panorama_title(src.stem),count,round(1000/fps) if count>1 else 1000))
        mb=sum(f.stat().st_size for f in out.glob("frame_*.png"))/1048576
        print(f"[VBV panorama] {src.name} -> {slug}: {count} frame(s), {mb:.1f} MB ({clip_w}px, {fps} fps, {dither})")
    java_files=list(GAME.rglob("VBVPanoramaOverlay.java"))
    if not java_files: raise RuntimeError("VBVPanoramaOverlay.java not found")
    java=java_files[0]; source=java.read_text(encoding="utf-8")
    def table(name,java_type,values):
        nonlocal source
        body=", ".join(values)
        source,n=re.subn(r"private static final "+re.escape(java_type)+r"\[\] "+name+r" = new "+re.escape(java_type)+r"\[\] \{.*?\};",
                         lambda m:f"private static final {java_type}[] {name} = new {java_type}[] {{ {body} }};",source,count=1,flags=re.S)
        if n!=1: raise RuntimeError(f"Could not update the {name} table in VBVPanoramaOverlay.java")
    table("IDS","String",[f'"{e[0]}"' for e in entries]); table("NAMES","String",[f'"{e[1]}"' for e in entries])
    table("COUNTS","int",[str(e[2]) for e in entries]); table("FRAME_MS","long",[f"{e[3]}L" for e in entries])
    java.write_text(source,encoding="utf-8")

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
    """Replace the Mojang logo / red background / plain bar on the loading overlay with VBVBootScreen."""
    candidates = list(GAME.rglob("LoadingOverlay.java"))
    if not candidates:
        raise RuntimeError("LoadingOverlay.java not found for the VBV boot screen")
    path = next((p for p in candidates if p.as_posix().endswith("/net/minecraft/client/gui/screens/LoadingOverlay.java")), candidates[0])
    source = path.read_text(encoding="utf-8")
    marker = "VBV_BOOT_SCREEN"
    if marker in source:
        return

    # 1) Backdrop colours: the red Mojang colour and the optional black one both become the VBV backdrop.
    for old in ("   private static final int LOGO_BACKGROUND_COLOR = ARGB.color(255, 239, 50, 61);\n",
                "   private static final int LOGO_BACKGROUND_COLOR_DARK = ARGB.color(255, 0, 0, 0);\n"):
        if source.count(old) != 1:
            raise RuntimeError("Loading overlay background constant not found exactly once: " + old.strip())
    source = source.replace("   private static final int LOGO_BACKGROUND_COLOR = ARGB.color(255, 239, 50, 61);\n",
        "   private static final int LOGO_BACKGROUND_COLOR = com.melonman106.vbvclient.VBVBootScreen.BACKGROUND; // " + marker + "\n", 1)
    source = source.replace("   private static final int LOGO_BACKGROUND_COLOR_DARK = ARGB.color(255, 0, 0, 0);\n",
        "   private static final int LOGO_BACKGROUND_COLOR_DARK = com.melonman106.vbvclient.VBVBootScreen.BACKGROUND;\n", 1)

    # 2) Everything from the logo to the end of the progress block is replaced by one call.
    start_anchor = "      int contentX = (int)(graphics.guiWidth() * 0.5);\n"
    end_anchor = "      if (fadeOutAnim >= 2.0F) {\n"
    if source.count(start_anchor) != 1 or source.count(end_anchor) != 1:
        raise RuntimeError("Loading overlay render anchors not found exactly once (start %d, end %d)"
                           % (source.count(start_anchor), source.count(end_anchor)))
    start = source.index(start_anchor)
    end = source.index(end_anchor)
    if end < start:
        raise RuntimeError("Loading overlay render anchors are out of order")
    replacement = (
        "      // " + marker + ": VBV loading screen instead of the Mojang logo and plain progress bar.\n"
        "      float actualProgress = this.reload.getActualProgress();\n"
        "      this.currentProgress = Mth.clamp(this.currentProgress * 0.95F + actualProgress * 0.050000012F, 0.0F, 1.0F);\n"
        "      com.melonman106.vbvclient.VBVBootScreen.render(graphics, width, height, this.currentProgress, logoAlpha,\n"
        "         Mth.clamp(fadeOutAnim, 0.0F, 1.0F), this.reload.isDone());\n\n"
    )
    source = source[:start] + replacement + source[end:]
    path.write_text(source, encoding="utf-8")
    print("[VBV] Loading overlay now draws the VBV boot screen.")

patch_startup_console()
