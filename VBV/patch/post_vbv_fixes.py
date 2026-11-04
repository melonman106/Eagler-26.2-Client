#!/usr/bin/env python3
"""Post-process the pinned VBV patch with fixes that must not be overwritten by it."""
from pathlib import Path
import re, shutil, sys
ROOT=Path(sys.argv[1]).resolve(); CLIENT=Path(__file__).resolve().parent.parent; SRC=CLIENT/"src/main/java/com/melonman106/vbvclient"; GAME=ROOT/"game/src/main/java"; RES=ROOT/"game/src/main/resources"
def java_files_named(name):return list(GAME.rglob(name))
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
  elif state=="line" and c=="\n":state="code"
  elif state=="block" and c=="*" and n=="/":state="code";i+=1
  i+=1
 return -1

dest=GAME/"com/melonman106/vbvclient";dest.mkdir(parents=True,exist_ok=True)
for source in SRC.rglob("*.java"):
 target=dest/source.relative_to(SRC);target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(source,target)
icon_root=CLIENT/"PackIcons";icon_dst=RES/"assets/vbvclient/textures/mods";icon_dst.mkdir(parents=True,exist_ok=True)
for a,b in (("ShulkerBoxTooltips.jpeg","shulkerboxtooltip.jpeg"),("Viabackportvisuals-client.png","viabackportvisuals-client.png")):
 src=icon_root/a
 if src.is_file():shutil.copy2(src,icon_dst/b)
mc_candidates=java_files_named("Minecraft.java")
if not mc_candidates:raise RuntimeError("Minecraft.java not found")
minecraft=next((p for p in mc_candidates if p.as_posix().endswith("/net/minecraft/client/Minecraft.java")),mc_candidates[0]);mc=minecraft.read_text(encoding="utf-8")
start="com.melonman106.vbvclient.fabriccompat.FabricCompatClient.startTick(this);";end="com.melonman106.vbvclient.fabriccompat.FabricCompatClient.endTick(this);";legacy="com.melonman106.vbvclient.fabriccompat.FabricCompatClient.tick(this);"
if legacy in mc:mc=mc.replace(legacy,end,1)
if start not in mc:
 m=re.search(r"(?:public|protected|private)\s+void\s+tick\s*\(\s*\)\s*\{",mc)
 if not m:raise RuntimeError("Minecraft.tick() not found")
 b=mc.find("{",m.start());mc=mc[:b+1]+"\n        "+start+mc[b+1:]
if end not in mc:raise RuntimeError("Fabric END tick hook not found")
minecraft.write_text(mc,encoding="utf-8")

# Raise the client render-distance option ceiling to 128 chunks.
def patch_render_distance_limit():
 candidates=java_files_named("GameOptions.java")+java_files_named("Options.java")
 if not candidates:return
 path=next((p for p in candidates if p.as_posix().endswith("/net/minecraft/client/Options.java") or p.as_posix().endswith("/net/minecraft/client/GameOptions.java")),candidates[0])
 text=path.read_text(encoding="utf-8");marker="VBV_RENDER_DISTANCE_128"
 if marker in text:return
 m=re.search(r"(viewDistance\s*=\s*new\s+OptionInstance(?:<[^>]+>)?\s*\([^;]*?)(?=;)",text,re.S)
 if not m:m=re.search(r"(\bviewDistance\b\s*=\s*new\s+[^;]*OptionInstance[^;]*)(?=;)",text,re.S)
 if not m:
  print("[VBV] viewDistance initializer not found; leaving cap unchanged.");return
 block=m.group(1)
 if not re.search(r"\b32\b",block):
  print("[VBV] viewDistance initializer has no 32 ceiling; leaving it unchanged.");return
 block=re.sub(r"\b32\b","128",block,count=1)
 text=text[:m.start(1)]+block+text[m.end(1):]
 path.write_text("// "+marker+"\n"+text,encoding="utf-8")
 print("[VBV] Raised client render-distance option ceiling to 128 chunks in",path)
patch_render_distance_limit()

hud_candidates=list(GAME.rglob("Hud.java"))
if hud_candidates:
 hud=hud_candidates[0];t=hud.read_text(encoding="utf-8")
 # Migrate the native HUD features from individual extractItemHotbar injections
 # to the shared Fabric-shaped HudRenderCallback. This also removes stale calls
 # if the post-patch script is run more than once against the same workspace.
 hud_callbacks = [
  ("VBVSimpleHud", "render"),
  ("VBVArmorHud", "render"),
  ("VBVExtraFeatures", "renderGammaStatus"),
  ("VBVAppleSkin", "render"),
  ("VBVMiniMap", "render"),
  ("VBVJadeHud", "render"),
  ("VBVCrosshair", "render"),
 ]
 for owner, method_name in hud_callbacks:
  pattern = r"(?m)^\s*com\.melonman106\.vbvclient\." + owner + r"\." + method_name + r"\(\s*\w+\s*\);\s*\n?"
  t, removed = re.subn(pattern, "", t)
  if removed:
   print("[VBV] Migrated", owner, "to HudRenderCallback (removed", removed, "legacy hook(s)).")
 marker="VBV_LOW_HEALTH_PULSE_HOOK"
 if marker not in t:
  methods=list(re.finditer(r"(?:public|protected|private)\s+void\s+extractRenderState\s*\([^)]*GuiGraphicsExtractor\s+(\w+)[^)]*\)\s*\{",t))
  if methods:
   m=methods[0];b=t.find("{",m.start());e=matching_brace(t,b)
   if e<0:raise RuntimeError("Could not locate Hud.extractRenderState end")
   g=m.group(1);t=t[:e]+f"\n        // {marker}\n        com.melonman106.vbvclient.VBVLowHealthPulse.render({g});\n    "+t[e:]
 if "VBV_CUSTOM_CROSSHAIR_SUPPRESS" not in t:
  # Wrap only the actual draw call, never the static field declaration.
  t,n=re.subn(r"(?m)^(\s*)(graphics\.blitSprite\([^;\n]*CROSSHAIR_SPRITE[^;\n]*;)",r"\1if (!com.melonman106.vbvclient.VBVCrosshairConfig.enabled) { /* VBV_CUSTOM_CROSSHAIR_SUPPRESS */\n\1    \2\n\1}",t,count=1)
  if n!=1: print("[VBV] Vanilla crosshair draw call not found; leaving it unchanged.")
 # Custom crosshair drawing now runs in the shared HUD callback; retain only
 # the vanilla crosshair suppression guard above.
 t = re.sub(r"(?m)^\s*// VBV_CUSTOM_CROSSHAIR_RENDER\s*\n?", "", t)
 # Fabric-shaped HUD render callback: run after native HUD state collection.
 hud_marker="VBV_FABRIC_HUD_RENDER_CALLBACK"
 if hud_marker not in t:
  method=re.search(r"public\s+void\s+extractRenderState\s*\(\s*final\s+GuiGraphicsExtractor\s+(\w+)\s*,\s*final\s+DeltaTracker\s+(\w+)\s*\)\s*\{",t)
  if method:
   b=t.find("{",method.start());e=matching_brace(t,b)
   if e<0: raise RuntimeError("Could not locate Hud.extractRenderState end for Fabric HUD callback")
   graphics_name,delta_name=method.group(1),method.group(2)
   t=t[:e]+f"\n        // {hud_marker}\n        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.fire(this.minecraft, {graphics_name}, {delta_name});\n    "+t[e:]
 hud.write_text(t,encoding="utf-8")

# Connect Fabric-style screen events to the real 26.2 Screen lifecycle.
screen_candidates=java_files_named("Screen.java")
if screen_candidates:
 screen=next((p for p in screen_candidates if p.as_posix().endswith("/net/minecraft/client/gui/screens/Screen.java")),screen_candidates[0])
 st=screen.read_text(encoding="utf-8")
 def inject_screen_event(pattern, marker, before="", after=""):
  nonlocal_text=st
  if marker in nonlocal_text:return nonlocal_text
  m=re.search(pattern,nonlocal_text)
  if not m:
   print("[VBV] Screen hook target not found:",marker)
   return nonlocal_text
  brace=nonlocal_text.find("{",m.start());end=matching_brace(nonlocal_text,brace)
  if end<0:raise RuntimeError("Could not locate Screen method end: "+marker)
  return nonlocal_text[:brace+1]+before+nonlocal_text[brace+1:end]+after+nonlocal_text[end:]
 st=inject_screen_event(r"public\s+final\s+void\s+init\s*\(\s*final\s+int\s+(\w+)\s*,\s*final\s+int\s+(\w+)\s*\)\s*\{",
  "VBV_FABRIC_SCREEN_INIT",
  before="\n      // VBV_FABRIC_SCREEN_INIT\n      net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.fireBeforeInit(this, width, height);",
  after="\n      net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.fireAfterInit(this, width, height);")
 st=inject_screen_event(r"public\s+void\s+extractRenderState\s*\(\s*final\s+GuiGraphicsExtractor\s+(\w+)\s*,\s*final\s+int\s+(\w+)\s*,\s*final\s+int\s+(\w+)\s*,\s*final\s+float\s+(\w+)\s*\)\s*\{",
  "VBV_FABRIC_SCREEN_RENDER",
  before="\n      // VBV_FABRIC_SCREEN_RENDER\n      net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.fireBeforeRender(this, graphics, mouseX, mouseY, a);",
  after="\n      net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.fireAfterRender(this, graphics, mouseX, mouseY, a);")
 st=inject_screen_event(r"public\s+void\s+tick\s*\(\s*\)\s*\{",
  "VBV_FABRIC_SCREEN_TICK", after="\n      net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.fireAfterTick(this);")
 st=inject_screen_event(r"public\s+void\s+removed\s*\(\s*\)\s*\{",
  "VBV_FABRIC_SCREEN_REMOVE",
  before="\n      net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.fireRemove(this);")
 screen.write_text(st,encoding="utf-8")

# Fire the client resource reload event after Minecraft reports a successful pack reload.
minecraft_candidates=java_files_named("Minecraft.java")
if minecraft_candidates:
 minecraft=next((p for p in minecraft_candidates if p.as_posix().endswith("/net/minecraft/client/Minecraft.java")),minecraft_candidates[0])
 mt=minecraft.read_text(encoding="utf-8")
 # World-map work now runs through ClientTickEvents.END_CLIENT_TICK.
 mt, map_tick_removed = re.subn(
  r"(?m)^\s*com\.melonman106\.vbvclient\.VBVWorldMap\.tick\(\);\s*\n?", "", mt
 )
 if map_tick_removed:
  print("[VBV] Moved explored-map tick onto ClientTickEvents.END_CLIENT_TICK.")
 reload_marker="VBV_FABRIC_RESOURCE_RELOAD"
 if reload_marker not in mt:
  complete="result.complete(null);"
  if mt.count(complete)==1:
   mt=mt.replace(complete,"net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientResourceEvents.fireReload(this); // "+reload_marker+"\n                  "+complete,1)
   minecraft.write_text(mt,encoding="utf-8")
  else:
   print("[VBV] Could not uniquely locate successful resource reload completion; resource callback remains manually callable.")

storage=Path(__file__).resolve().parent/"storage_manager.js"
if storage.is_file():
 s=storage.read_text(encoding="utf-8")
 s=re.sub(r"\n\s*function addLauncher\(\)\s*\{.*?\n\s*\}\s*\n\s*if \(document\.readyState===\"loading\"\).*?window\.VBVStorage\.initialize\(\);\s*\}\s*else \{.*?\}\(\);","\n  if (document.readyState === \"loading\") document.addEventListener(\"DOMContentLoaded\", () => window.VBVStorage.initialize());\n  else window.VBVStorage.initialize();",s,flags=re.S)
 storage.write_text(s,encoding="utf-8")
# Keep the upstream TeaVM async sleep callback. The custom VBVSleepTimer
# token-map bridge was introduced as a workaround, but the runtime later
# crashed inside VBVSleepTimer.onTimer with a Number/BigInt conversion error.
# Background-tab throttling can delay setTimeout; it does not itself explain
# a Number-to-BigInt type mismatch. Retain the original TimerHandler lambda.
sleep_path = ROOT / "platform-teavm/src/main/java/net/lax1dude/eaglercraft/v1_8/internal/PlatformRuntime.java"
if not sleep_path.is_file():
    raise RuntimeError("platform-teavm PlatformRuntime.java not found for sleep callback validation")
sleep_text = sleep_path.read_text(encoding="utf-8")
sleep_marker = "VBV_ASYNC_SLEEP_CALLBACK_BRIDGE"
custom_start = sleep_text.find("private static final java.util.Map<Integer, AsyncCallback<Void>> vbvPendingSleepCallbacks")
if custom_start >= 0 or sleep_marker in sleep_text:
    method_end = sleep_text.find("\n\tpublic static void postCreate()", custom_start)
    if custom_start < 0 or method_end < 0:
        raise RuntimeError("Found a partial custom VBVSleepTimer bridge; refusing unsafe partial removal")
    upstream_sleep = """private static void sleep(int millis, final AsyncCallback<Void> callback) {
\t\tWindow.setTimeout(() -> callback.complete(null), millis);
\t}"""
    sleep_text = sleep_text[:custom_start] + upstream_sleep + "\n\n" + sleep_text[method_end:]
    sleep_path.write_text(sleep_text, encoding="utf-8")
elif "private static void sleep(int millis, final AsyncCallback<Void> callback)" not in sleep_text:
    raise RuntimeError("Expected upstream PlatformRuntime.sleep implementation was not found")
print("[VBV runtime] using upstream TeaVM async sleep callback; custom VBVSleepTimer bridge is disabled.")
# Respect the explicit worker opt-in on WASM-GC too. The pinned workspace
# treated wasmGC itself as permission to force serverWorker, overriding the
# generated HTML's safe single-thread default and leaving Singleplayer on the
# worker startup path even when ?workers was not requested.
sp_path = ROOT / "platform-teavm/src/main/java/net/lax1dude/eaglercraft/v1_8/sp/internal/ClientPlatformSingleplayer.java"
if not sp_path.is_file():
    raise RuntimeError("ClientPlatformSingleplayer.java not found for server-worker opt-in fix")
sp_text = sp_path.read_text(encoding="utf-8")
sp_marker = "VBV_SERVER_WORKER_EXPLICIT_OPT_IN"
if sp_marker not in sp_text:
    requested_old = """\t\t\t\t&& (net.lax1dude.eaglercraft.v1_8.internal.teavm.WasmWorkerBootstrap.isWasmGC()
\t\t\t\t\t\t|| isServerWorkerFlagJS());"""
    requested_new = """\t\t\t\t&& isServerWorkerFlagJS(); // VBV_SERVER_WORKER_EXPLICIT_OPT_IN"""
    if requested_old not in sp_text:
        raise RuntimeError("Could not find server-worker request gate in ClientPlatformSingleplayer")
    sp_text = sp_text.replace(requested_old, requested_new, 1)
    force_old = """\t\tboolean wasmGC = net.lax1dude.eaglercraft.v1_8.internal.teavm.WasmWorkerBootstrap.isWasmGC();
\t\tboolean forceServerWorker = !net.lax1dude.eaglercraft.v1_8.minecraft.EaglerHosted.liteModeEnabled
\t\t\t\t&& workerSupportedJS() && (wasmGC || isServerWorkerFlagJS());"""
    force_new = """\t\tboolean wasmGC = net.lax1dude.eaglercraft.v1_8.internal.teavm.WasmWorkerBootstrap.isWasmGC();
\t\tboolean forceServerWorker = !net.lax1dude.eaglercraft.v1_8.minecraft.EaglerHosted.liteModeEnabled
\t\t\t\t&& workerSupportedJS() && isServerWorkerFlagJS(); // VBV_SERVER_WORKER_EXPLICIT_OPT_IN"""
    if force_old not in sp_text:
        raise RuntimeError("Could not find integrated-server worker force gate in ClientPlatformSingleplayer")
    sp_text = sp_text.replace(force_old, force_new, 1)
    sp_path.write_text(sp_text, encoding="utf-8")
    print("[VBV singleplayer] server workers now require explicit ?workers/serverWorker opt-in; default uses the single-thread controller.")
else:
    print("[VBV singleplayer] explicit server-worker opt-in already installed.")

# Wire the inventory helpers into the real 26.2 container screen and render
# the shulker contents preview after the normal item tooltip.
container_candidates = java_files_named("AbstractContainerScreen.java")
if container_candidates:
    container = next((p for p in container_candidates if p.as_posix().endswith("/net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java")), container_candidates[0])
    ct = container.read_text(encoding="utf-8")
    preview_marker = "VBV_SHULKER_PREVIEW_RENDER"
    if preview_marker not in ct:
        tooltip_anchor = "this.extractTooltip(graphics, mouseX, mouseY);"
        if ct.count(tooltip_anchor) != 1:
            raise RuntimeError("Could not uniquely locate container tooltip render call for ShulkerBoxTooltip")
        preview = tooltip_anchor + """
      // VBV_SHULKER_PREVIEW_RENDER
      com.melonman106.vbvclient.VBVShulkerViewer.render(
          graphics, this.hoveredSlot, mouseX, mouseY,
          net.minecraft.client.gui.screens.Screen.hasShiftDown());
"""
        ct = ct.replace(tooltip_anchor, preview, 1)

    wheel_marker = "VBV_MOUSE_TWEAK_WHEEL"
    if wheel_marker not in ct:
        wheel_method = re.search(r"public\\s+boolean\\s+mouseScrolled\\s*\\([^)]*\\)\\s*\\{", ct)
        if not wheel_method:
            raise RuntimeError("AbstractContainerScreen.mouseScrolled() not found for Mouse Tweaks")
        wheel_brace = ct.find("{", wheel_method.start())
        wheel_hook = """
      // VBV_MOUSE_TWEAK_WHEEL
      if (com.melonman106.vbvclient.VBVMouseTweaks.isWheelTweakEnabled()
            && this.hoveredSlot != null && this.hoveredSlot.hasItem()
            && this.menu.getCarried().isEmpty()) {
         this.slotClicked(this.hoveredSlot, this.hoveredSlot.index, 0, ContainerInput.QUICK_MOVE);
         return true;
      }
"""
        ct = ct[:wheel_brace + 1] + wheel_hook + ct[wheel_brace + 1:]

    ct = ct.replace(
        "boolean quickKey = slotId != -999 && event.hasShiftDown();",
        "boolean quickKey = slotId != -999 && event.hasShiftDown() && com.melonman106.vbvclient.VBVMouseTweaks.isQuickMoveEnabled();"
    )
    ct = ct.replace(
        "this.quickCraftingType = 0;",
        "this.quickCraftingType = com.melonman106.vbvclient.VBVMouseTweaks.isDragEvenlyEnabled() ? 0 : 1;"
    )
    container.write_text(ct, encoding="utf-8")
    print("[VBV inventory] Shulker preview and Mouse Tweaks hooks installed.")

print("Post-VBV fixes complete: icons, client cap, HUD, storage, upstream async sleep callback, and explicit singleplayer worker opt-in.")
