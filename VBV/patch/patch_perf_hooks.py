#!/usr/bin/env python3
"""Time the engine's own frame phases so the FPS meter can say where a frame went.

Three hooks in LevelRenderer, each a pair of empty static calls with all the work done in VBVPerf:

  1. compileSections        - building chunk section meshes on the render thread.
  2. uploadTerrainBuffers   - handing the finished meshes to the GPU.
  3. solidTerrain           - drawing the opaque terrain.

The meter already knows the frame time; these turn it into "1.2 ms building, 0.4 uploading,
6.1 drawing", which is the difference between lowering the render distance and lowering LOD.

Every anchor must match exactly once or the script aborts before writing anything, so a changed
upstream tree fails the build instead of leaving a half-patched file. With the meter off the hooks
still run, but they are two `System.nanoTime()` pairs per phase.
"""
from pathlib import Path
import sys

MARKER = "VBV_PERF_PHASE"
CLS = "com.melonman106.vbvclient.VBVPerf"

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

rel = "net/minecraft/client/renderer/LevelRenderer.java"
path = Path(sys.argv[1]).resolve() / "game" / "src" / "main" / "java" / rel
if not path.is_file():
    raise SystemExit(f"::error::{rel} is missing")
text = path.read_text(encoding="utf-8")

if MARKER in text:
    print("[VBV Perf] phase hooks already present")
    raise SystemExit(0)

HOOKS = [
    (
        "section build",
        '      profiler.push("compileSections");\n'
        "      this.compileSections(cameraState);\n",
        '      profiler.push("compileSections");\n'
        f"      {CLS}.phaseStart({CLS}.PHASE_BUILD);\n"
        "      this.compileSections(cameraState);\n"
        f"      {CLS}.phaseEnd({CLS}.PHASE_BUILD);\n",
    ),
    (
        "terrain upload",
        "         try {\n"
        "            this.sectionRenderDispatcher.uploadTerrainBuffersToGpu();\n"
        "         } finally {\n"
        "            this.sectionRenderDispatcher.unlock();\n"
        "         }\n",
        f"         {CLS}.phaseStart({CLS}.PHASE_UPLOAD);\n"
        "         try {\n"
        "            this.sectionRenderDispatcher.uploadTerrainBuffersToGpu();\n"
        "         } finally {\n"
        "            this.sectionRenderDispatcher.unlock();\n"
        "         }\n"
        f"         {CLS}.phaseEnd({CLS}.PHASE_UPLOAD);\n",
    ),
    (
        "opaque terrain draw",
        '            profiler.push("solidTerrain");\n'
        "            chunkSectionsToRender.renderGroup(ChunkSectionLayerGroup.OPAQUE, this.chunkLayerSampler);\n",
        '            profiler.push("solidTerrain");\n'
        f"            {CLS}.phaseStart({CLS}.PHASE_DRAW);\n"
        "            chunkSectionsToRender.renderGroup(ChunkSectionLayerGroup.OPAQUE, this.chunkLayerSampler);\n"
        f"            {CLS}.phaseEnd({CLS}.PHASE_DRAW);\n",
    ),
]

for label, anchor, replacement in HOOKS:
    count = text.count(anchor)
    if count != 1:
        raise SystemExit(
            f"::error::Perf hook '{label}' anchor matched {count} time(s) in {rel}; expected exactly 1"
        )
    text = text.replace(anchor, replacement, 1)

lines = text.splitlines(keepends=True)
injected = 0
for index, source_line in enumerate(lines):
    if f"{CLS}.phaseStart(" not in source_line:
        continue
    indent = source_line[: len(source_line) - len(source_line.lstrip())]
    lines.insert(index + injected, f"{indent}// {MARKER}: frame phase timing for the FPS meter.\n")
    injected += 1
    break
text = "".join(lines)

path.write_text(text, encoding="utf-8")
print(f"[VBV Perf] installed {len(HOOKS)} frame-phase hooks in {rel}")
