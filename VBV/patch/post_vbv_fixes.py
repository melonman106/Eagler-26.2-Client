#!/usr/bin/env python3
"""Post-process the pinned VBV patch with fixes that must not be overwritten by it."""
from pathlib import Path
import json, re, shutil, sys
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
 candidates=java_files_named("Options.java")
 if not candidates:return
 path=next((p for p in candidates if p.as_posix().endswith("/net/minecraft/client/Options.java")),candidates[0])
 text=path.read_text(encoding="utf-8");marker="VBV_RENDER_DISTANCE_128"
 if marker in text:return
 # U2 names the field renderDistance (not viewDistance) and uses a
 # largeDistances ? 32 : 16 cap. Replace only that OptionInstance range.
 pattern=r'(this\.renderDistance\s*=\s*new\s+OptionInstance<>\s*\(.*?new\s+OptionInstance\.IntRange\(2,\s*)largeDistances\s*\?\s*32\s*:\s*16(\s*,\s*false\s*\),)'
 updated,n=re.subn(pattern,r'\g<1>128\g<2>',text,count=1,flags=re.S)
 if n!=1:
  print("[VBV] U2 renderDistance range did not match; leaving cap unchanged.")
  return
 path.write_text("// "+marker+"\n"+updated,encoding="utf-8")
 print("[VBV] Raised U2 render-distance option ceiling to 128 chunks in",path)
patch_render_distance_limit()

# Keep the native F3 debug information as text only, without per-line black boxes.
def remove_vanilla_f3_backgrounds():
 candidates = java_files_named("DebugScreenOverlay.java")
 if not candidates:return
 path = next((p for p in candidates if p.as_posix().endswith("/net/minecraft/client/gui/components/DebugScreenOverlay.java")), candidates[0])
 source = path.read_text(encoding="utf-8")
 marker = "VBV_F3_TEXT_ONLY"
 if marker in source:return
 old = "graphics.fill(left - 1, top - 1, left + width + 1, top + height - 1, -1873784752);"
 if old not in source:
  print("[VBV] Vanilla F3 background draw call not found; custom BetterF3 remains text-only.")
  return
 source = source.replace(old, "// " + marker + ": text-only debug output.", 1)
 path.write_text(source, encoding="utf-8")
 print("[VBV] Removed the vanilla F3 per-line background.")

remove_vanilla_f3_backgrounds()

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
# Keep server-worker activation compatible with the pinned U2 source.
# U2 already uses a WASM-GC auto-enable gate; replace that gate with an explicit
# cross-origin-isolation check so workers are enabled only where SharedArrayBuffer
# is actually available. Preserve the explicit ?serverworker / eaglercraftXOpts flag.
sp_path = ROOT / "platform-teavm/src/main/java/net/lax1dude/eaglercraft/v1_8/sp/internal/ClientPlatformSingleplayer.java"
if not sp_path.is_file():
    raise RuntimeError("ClientPlatformSingleplayer.java not found for server-worker opt-in fix")
sp_text = sp_path.read_text(encoding="utf-8")
sp_marker = "VBV_SERVER_WORKER_CROSS_ORIGIN_ISOLATED"
if sp_marker not in sp_text:
    worker_marker = "private static native boolean workerSupportedJS();"
    worker_method = """\t@JSBody(params = {}, script = "return typeof crossOriginIsolated !== 'undefined' && crossOriginIsolated === true && typeof SharedArrayBuffer !== 'undefined' && !(typeof location !== 'undefined' && location.search && location.search.indexOf('singlethread') >= 0);")
\tprivate static native boolean isCrossOriginIsolatedJS();"""
    if "isCrossOriginIsolatedJS()" not in sp_text:
        if worker_marker not in sp_text:
            raise RuntimeError("Could not find worker support JS bridge insertion point")
        sp_text = sp_text.replace(worker_marker, worker_marker + "\n" + worker_method, 1)

    # Pinned U2 has this expression in both isServerWorkerModeRequested() and
    # startIntegratedServer(). Match whitespace flexibly, but require exactly
    # two replacements so an unexpected upstream change fails safely.
    gate_pattern = r"net\\.lax1dude\\.eaglercraft\\.v1_8\\.internal\\.teavm\\.WasmWorkerBootstrap\\.isWasmGC\\(\\)\\s*\\|\\|\\s*isServerWorkerFlagJS\\(\\)"
    sp_text, gate_count = re.subn(
        gate_pattern,
        "(isServerWorkerFlagJS() || isCrossOriginIsolatedJS())",
        sp_text,
    )
    if gate_count != 2:
        raise RuntimeError(
            "Expected two U2 WASM-GC server-worker gates, found " + str(gate_count)
        )

    # Mark the changed policy so reruns are idempotent.
    sp_text = sp_text.replace(
        "(isServerWorkerFlagJS() || isCrossOriginIsolatedJS())",
        "(isServerWorkerFlagJS() || isCrossOriginIsolatedJS()) /* " + sp_marker + " */",
    )
    sp_path.write_text(sp_text, encoding="utf-8")
    print("[VBV singleplayer] integrated-server workers auto-enable only on cross-origin-isolated pages; explicit ?serverworker remains supported.")
else:
    print("[VBV singleplayer] cross-origin-isolated worker gate already installed.")

# Make the existing inventory hooks use the container's actual quick-move action.
container_candidates = java_files_named("AbstractContainerScreen.java")
if container_candidates:
    container = next((p for p in container_candidates if p.as_posix().endswith("/net/minecraft/client/gui/screens/inventory/AbstractContainerScreen.java")), container_candidates[0])
    ct = container.read_text(encoding="utf-8")
    wheel_call = "this.slotClicked(this.hoveredSlot, this.hoveredSlot.index, button, ContainerInput.PICKUP);"
    quick_move_call = "this.slotClicked(this.hoveredSlot, this.hoveredSlot.index, 0, ContainerInput.QUICK_MOVE);"
    if wheel_call in ct:
        ct = ct.replace(wheel_call, quick_move_call, 1)
        print("[VBV Mouse Tweaks] Wheel action now transfers the hovered stack between inventories.")
    # Replace the complete qualified expression. Replacing only the
    # inner "Minecraft.getInstance()" substring would produce the invalid
    # package net.minecraft.client.net.minecraft.client.gui.screens.
    # The U2 Screen class has no static hasShiftDown() method. Keep the
    # original key mapping lookup, which is supported by this workspace.
    good_screen = "net.minecraft.client.Minecraft.getInstance().options.keyShift.isDown()"
    bad_expressions = (
        "net.minecraft.client.net.minecraft.client.gui.screens.Screen.hasShiftDown()",
        "net.minecraft.client.gui.screens.Screen.hasShiftDown()",
    )
    for bad_expression in bad_expressions:
        if bad_expression in ct:
            ct = ct.replace(bad_expression, good_screen)
            print("[VBV ShulkerBoxTooltip] Restored the supported keyShift.isDown() lookup.")
    # Normalize the entire qualified expression. The upstream injection may
    # already prepend net.minecraft.client.; replacing only the inner
    # Minecraft token would duplicate that package prefix.
    shift_pattern = r"(?:net\\.minecraft\\.client\\.)*Minecraft\\.getInstance\\(\\)\\.options\\.keyShift\\.isDown\\(\\)"
    ct, shift_count = re.subn(
        shift_pattern,
        "net.minecraft.client.Minecraft.getInstance().options.keyShift.isDown()",
        ct,
    )
    if shift_count:
        print("[VBV ShulkerBoxTooltip] Normalized", shift_count, "Shift-key lookup(s).")
    container.write_text(ct, encoding="utf-8")

# Activate the bundled Shine bloom post-chain on supported renderers.
renderer_candidates = java_files_named("GameRenderer.java")
if renderer_candidates:
    renderer = next((p for p in renderer_candidates if p.as_posix().endswith("/net/minecraft/client/renderer/GameRenderer.java")), renderer_candidates[0])
    rt = renderer.read_text(encoding="utf-8")
    shine_marker = "VBV_SHINE_POST_CHAIN"
    if shine_marker not in rt:
        field_anchor = "private boolean eaglerOptimizedShaderFailed;"
        if field_anchor not in rt:
            raise RuntimeError("GameRenderer optimized-shader state field not found for Shine")
        rt = rt.replace(field_anchor, field_anchor + "\n   private boolean vbvShineFailed;", 1)
        anchor = "this.minecraft.levelRenderer.doEntityOutline();"
        if rt.count(anchor) != 1:
            raise RuntimeError("Could not uniquely locate GameRenderer world post-processing point for Shine")
        shine_hook = anchor + """
         // VBV_SHINE_POST_CHAIN: safely process the bundled screen-space bloom.
         if (!this.vbvShineFailed
               && com.melonman106.vbvclient.ShineConfig.bloom()
               && this.isEaglerOptimizedShaderSupported()) {
            try {
               net.minecraft.client.renderer.PostChain vbvShine = this.minecraft.getShaderManager()
                  .getPostChain(net.minecraft.resources.Identifier.parse("vbvclient:shine_bloom"),
                     net.minecraft.client.renderer.LevelTargetBundle.MAIN_TARGETS);
               if (vbvShine != null) {
                  vbvShine.process(this.mainRenderTarget, this.resourcePool);
               } else {
                  this.vbvShineFailed = true;
                  System.err.println("[VBV Shine] Bloom post-chain is unavailable or failed to compile; disabled.");
               }
            } catch (Throwable error) {
               this.vbvShineFailed = true;
               System.err.println("[VBV Shine] Bloom post-chain disabled after renderer error: " + error);
            }
         }
"""
        rt = rt.replace(anchor, shine_hook, 1)
        renderer.write_text(rt, encoding="utf-8")
        print("[VBV Shine] Bloom post-chain hook installed.")

# Keep every generated resource-pack entry on the same client release version.
generated_pack_class = GAME / "com" / "melonman106" / "vbvclient" / "VBVGeneratedPackMods.java"
if generated_pack_class.is_file():
    pack_source = generated_pack_class.read_text(encoding="utf-8")
    pack_source = pack_source.replace('"1.0.0-26.2"', '"1.5.3"')
    generated_pack_class.write_text(pack_source, encoding="utf-8")
pack_registry = ROOT / "game" / "src" / "main" / "resources" / "assets" / "vbvclient" / "mods"
if pack_registry.is_dir():
    for metadata in pack_registry.rglob("mod.json"):
        try:
            data = json.loads(metadata.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        data["version"] = "1.5.3"
        description = str(data.get("description", ""))
        if description.startswith("Resource-pack client mod imported from "):
            data["description"] = (
                f"Client-side Eaglercraft 26.2 resource pack. {data.get('name', 'This pack')} "
                "provides its bundled textures, models and audio assets through the native resource pipeline."
            )
        metadata.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

print("Post-VBV fixes complete: icons, client cap, HUD, storage, upstream async sleep callback, and explicit singleplayer worker opt-in.")
