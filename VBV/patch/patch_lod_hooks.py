#!/usr/bin/env python3
"""Wire VBV's Level-of-Detail (LOD) distant terrain into the generated Eagler 26.2 sources.

Six small, independent hooks. Every anchor must match exactly once or the whole script
aborts before writing anything, so the tree is never left half patched:

  1. Options screen        - adds a "Modded Settings..." button (VBVModdedSettingsScreen).
  2. RenderPipelines       - registers VBV_LOD: the debug_quads shaders, but writing depth.
  3. LevelRenderer         - draws the LOD at the start of the main pass (before real terrain).
  4. Camera                - pushes the far plane out so far terrain is not clipped.
  5. FogRenderer           - extends render-distance fog to the LOD reach and tells the LOD
                             renderer what the real fog is doing (colour, start, end).
  6. ClientChunkCache      - remembers a coarse sample of each chunk as it loads or is dropped.

Everything is a no-op while the LOD setting is Off (the default), apart from one boolean
check per hook.
"""
from pathlib import Path
import sys

MARKER = "VBV_LOD"

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

root = Path(sys.argv[1]).resolve() / "game" / "src" / "main" / "java"
client = "net/minecraft/client"

# (file, anchor, replacement builder). The anchor is replaced by the returned text.
HOOKS = []


def hook(rel, anchor, replacement, label):
    HOOKS.append((rel, anchor, replacement, label))


# 1. Options screen button, placed before Credits so the 2-column grid stays even (10 buttons).
credits = (
    "      helper.addChild(this.openScreenButton(CREDITS_AND_ATTRIBUTION, "
    "() -> new CreditsAndAttributionScreen(this)));\n"
)
hook(
    f"{client}/gui/screens/options/OptionsScreen.java",
    credits,
    "      // VBV_LOD_OPTIONS_BUTTON\n"
    "      helper.addChild(this.openScreenButton(Component.literal(\"Modded Settings...\"), "
    "() -> new com.melonman106.vbvclient.VBVModdedSettingsScreen(this)));\n" + credits,
    "Options button",
)

# 2. Pipeline: same shaders as debug_quads, but with depth writes.
quads = (
    "   public static final RenderPipeline DEBUG_QUADS = register(\n"
    "      RenderPipeline.builder(DEBUG_FILLED_SNIPPET).withLocation(\"pipeline/debug_quads\")"
    ".withCull(false).build()\n"
    "   );\n"
)
hook(
    f"{client}/renderer/RenderPipelines.java",
    quads,
    quads
    + "   // VBV_LOD_PIPELINE: flat-coloured far terrain. Same shaders as debug_quads, but it writes depth.\n"
    "   public static final RenderPipeline VBV_LOD = register(\n"
    "      RenderPipeline.builder(DEBUG_FILLED_SNIPPET)\n"
    "         .withLocation(\"pipeline/vbv_lod\")\n"
    "         .withCull(false)\n"
    "         .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))\n"
    "         .build()\n"
    "   );\n",
    "LOD pipeline",
)

# 3. Draw the LOD first inside the main pass so real chunks win the depth test.
solid = "            profiler.push(\"solidTerrain\");\n"
hook(
    f"{client}/renderer/LevelRenderer.java",
    solid,
    "            // VBV_LOD_RENDER: far terrain first; real chunks then overwrite it where they exist.\n"
    "            com.melonman106.vbvclient.VBVLodRenderer.render(\n"
    "               this.levelRenderState.cameraRenderState.pos,\n"
    "               mainTarget.get(),\n"
    "               this.levelRenderState.cameraRenderState.fogType == FogType.ATMOSPHERIC\n"
    "            );\n" + solid,
    "LOD draw call",
)

# 4. Far plane.
far = "      this.depthFar = Math.max(renderDistance * 4.0F, this.minecraft.options.cloudRange().get() * 16);\n"
hook(
    f"{client}/Camera.java",
    far,
    far
    + "      // VBV_LOD_FAR_PLANE: do not clip the LOD terrain\n"
    "      this.depthFar = Math.max(this.depthFar, com.melonman106.vbvclient.VBVLod.farPlaneBlocks());\n",
    "far plane",
)

# 5. Fog.
fog_end = "      fog.renderDistanceEnd = renderDistanceInBlocks;\n"
hook(
    f"{client}/renderer/fog/FogRenderer.java",
    fog_end,
    fog_end
    + "      // VBV_LOD_FOG: fade out at the LOD reach instead of the render distance, and tell the\n"
    "      // LOD renderer what the real fog looks like so both match.\n"
    "      if (com.melonman106.vbvclient.VBVLod.isActive() && fogType == FogType.ATMOSPHERIC) {\n"
    "         float vbvLodEnd = com.melonman106.vbvclient.VBVLod.fogEndBlocks();\n"
    "         if (vbvLodEnd > fog.renderDistanceEnd) {\n"
    "            fog.renderDistanceEnd = vbvLodEnd;\n"
    "            fog.renderDistanceStart = Math.max(fog.renderDistanceStart, com.melonman106.vbvclient.VBVLod.fogStartBlocks());\n"
    "         }\n"
    "         com.melonman106.vbvclient.VBVLodRenderer.setFog(\n"
    "            fog.color.x(), fog.color.y(), fog.color.z(),\n"
    "            fog.environmentalStart, fog.environmentalEnd, fog.renderDistanceStart, fog.renderDistanceEnd\n"
    "         );\n"
    "      }\n",
    "fog",
)

# 6. Chunk capture (load and drop).
chunk_rel = f"{client}/multiplayer/ClientChunkCache.java"
loaded = "      this.level.onChunkLoaded(pos);\n"
hook(
    chunk_rel,
    loaded,
    "      // VBV_LOD_CAPTURE: remember a coarse picture of the chunk for the LOD renderer\n"
    "      com.melonman106.vbvclient.VBVLodCache.capture(this.level, chunk);\n" + loaded,
    "chunk load capture",
)
dropped = "            this.storage.drop(index, currentChunk);\n"
hook(
    chunk_rel,
    dropped,
    "            // VBV_LOD_CAPTURE: last look at the chunk before it is unloaded\n"
    "            com.melonman106.vbvclient.VBVLodCache.capture(this.level, currentChunk);\n" + dropped,
    "chunk drop capture",
)

# ---- Validate everything first, then write ------------------------------------------------
files = {}
pending = {}
for rel, anchor, replacement, label in HOOKS:
    path = root / rel
    if not path.is_file():
        raise SystemExit(f"::error::{rel} is missing ({label})")
    if rel not in files:
        files[rel] = path.read_text(encoding="utf-8")
        pending[rel] = files[rel]

already = [rel for rel, text in files.items() if MARKER in text]
if already and len(already) == len(files):
    print("[VBV LOD] hooks already present")
    raise SystemExit(0)
if already:
    raise SystemExit(f"::error::LOD hooks are partly applied ({', '.join(already)}); refusing to continue")

for rel, anchor, replacement, label in HOOKS:
    count = pending[rel].count(anchor)
    if count != 1:
        raise SystemExit(f"::error::LOD hook '{label}' anchor matched {count} time(s) in {rel}; expected exactly 1")
    pending[rel] = pending[rel].replace(anchor, replacement, 1)

# Imports the injected code relies on must already be there.
needs = {
    f"{client}/gui/screens/options/OptionsScreen.java": "import net.minecraft.network.chat.Component;",
    f"{client}/renderer/LevelRenderer.java": "import net.minecraft.world.level.material.FogType;",
    f"{client}/renderer/fog/FogRenderer.java": "import net.minecraft.world.level.material.FogType;",
    f"{client}/renderer/RenderPipelines.java": "import com.mojang.blaze3d.platform.CompareOp;",
}
for rel, line in needs.items():
    if line not in files[rel]:
        raise SystemExit(f"::error::{rel} no longer has `{line}`; the LOD hook needs it")

for rel, text in pending.items():
    (root / rel).write_text(text, encoding="utf-8")
print(f"[VBV LOD] installed {len(HOOKS)} hooks in {len(pending)} files")
