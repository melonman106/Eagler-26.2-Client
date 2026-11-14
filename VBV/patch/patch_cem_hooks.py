#!/usr/bin/env python3
"""Wire VBV's OptiFine-style custom entity models (.jem/.jpm) into the generated Eagler 26.2 sources.

Four small hooks in four files. Every anchor must match exactly once, in every file, or the
script aborts before writing anything, so the tree is never left half patched:

  1. EntityModelSet.bakeLayer                      - bakes the layer through VBVCem (JEM parts replace vanilla ones).
  2. EntityRenderDispatcher.onResourceManagerReload - tells VBVCem the new resource manager (reloads the JEMs).
  3. TextureManager.getTexture                      - lets a JEM's "texture" replace a vanilla entity texture.
  4. ModelFeatureRenderer.prepareModel              - runs the JEM's animation expressions after the vanilla model posed itself.

With Custom Entity Models switched off (or on any error) VBVCem returns the vanilla result.
"""
from pathlib import Path
import sys

MARKER = "VBV_CEM"

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

JAVA = Path(sys.argv[1]).resolve() / "game" / "src" / "main" / "java"

EDITS = [
    (
        "net/minecraft/client/model/geom/EntityModelSet.java",
        "bakeLayer",
        "         return result.bakeRoot();\n",
        "         // VBV_CEM: resource-pack .jem models may replace parts of this layer\n"
        "         return com.melonman106.vbvclient.VBVCem.bake(id, result);\n",
    ),
    (
        "net/minecraft/client/renderer/entity/EntityRenderDispatcher.java",
        "onResourceManagerReload",
        "   public void onResourceManagerReload(final ResourceManager resourceManager) {\n",
        "   public void onResourceManagerReload(final ResourceManager resourceManager) {\n"
        "      // VBV_CEM: load the pack's .jem models before the renderers bake their layers\n"
        "      com.melonman106.vbvclient.VBVCem.reload(resourceManager);\n",
    ),
    (
        "net/minecraft/client/renderer/texture/TextureManager.java",
        "getTexture",
        "   public AbstractTexture getTexture(final Identifier location) {\n",
        "   public AbstractTexture getTexture(final Identifier requestedLocation) {\n"
        "      // VBV_CEM: a .jem model can point a vanilla entity texture at the pack's own texture\n"
        "      final Identifier location = com.melonman106.vbvclient.VBVCem.redirect(requestedLocation);\n",
    ),
    (
        "net/minecraft/client/renderer/feature/ModelFeatureRenderer.java",
        "prepareModel",
        "      model.setupAnim(submit.state());\n",
        "      model.setupAnim(submit.state());\n"
        "      // VBV_CEM: run the .jem animation expressions on top of the vanilla pose\n"
        "      com.melonman106.vbvclient.VBVCem.animate(model, submit.state());\n",
    ),
]

# Read and check everything first.
pending = []
for rel, label, anchor, replacement in EDITS:
    path = JAVA / rel
    if not path.is_file():
        raise SystemExit(f"::error::{rel} is missing")
    text = path.read_text(encoding="utf-8")
    if MARKER in text:
        pending.append(None)
        continue
    count = text.count(anchor)
    if count != 1:
        raise SystemExit(f"::error::Custom Entity Models hook '{label}' anchor matched {count} time(s) in {rel}; expected exactly 1")
    pending.append((path, text.replace(anchor, replacement, 1)))

if all(item is None for item in pending):
    print("[VBV CEM] hooks already present")
    raise SystemExit(0)
if any(item is None for item in pending):
    raise SystemExit("::error::Custom Entity Models hooks are only partly installed; refusing to patch")

for path, text in pending:
    path.write_text(text, encoding="utf-8")
print(f"[VBV CEM] installed {len(EDITS)} hooks")
