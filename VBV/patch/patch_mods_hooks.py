#!/usr/bin/env python3
"""Wire VBV's importable-mods system into the generated Eagler 26.2 sources.

Three hooks, each with its own exact marker so a re-run never applies one twice:

* BuiltInRegistries.bootStrap(): register the imported mods' items after the game's own registries are filled and
  before they freeze (the same moment Fabric lets a mod register content). This runs on the page and in the
  integrated-server worker, which is how both sides end up with the same item ids.
* Minecraft constructor: offer every registered mod as a required resource pack (textures, models, language files).
* CreativeModeTab.buildContents(): append a mod's items to the creative tabs it asked for.

Fail closed: every anchor must match exactly once, and nothing is written unless all of them do.
"""
from pathlib import Path
import sys

if len(sys.argv) < 2:
    raise SystemExit("::error::Generated U2 workspace path was not supplied")

JAVA = Path(sys.argv[1]).resolve() / "game" / "src" / "main" / "java"

EDITS = [
    (
        "net/minecraft/core/registries/BuiltInRegistries.java",
        "register-items",
        "// VBV_MODS_HOOK: register-items",
        "   public static void bootStrap() {\n"
        "      createContents();\n"
        "      freeze();\n",
        "   public static void bootStrap() {\n"
        "      createContents();\n"
        "      // VBV_MODS_HOOK: register-items\n"
        "      com.melonman106.vbvclient.mods.ModBootstrap.registerAll();\n"
        "      freeze();\n",
    ),
    (
        "net/minecraft/client/Minecraft.java",
        "resource-pack-source",
        "// VBV_MODS_HOOK: resource-pack-source",
        "      this.resourcePackRepository = new PackRepository(clientPackSource, this.downloadedPackSource.createRepositorySource(), directoryPacks);\n",
        "      // VBV_MODS_HOOK: resource-pack-source\n"
        "      this.resourcePackRepository = new PackRepository(\n"
        "         clientPackSource,\n"
        "         this.downloadedPackSource.createRepositorySource(),\n"
        "         directoryPacks,\n"
        "         new com.melonman106.vbvclient.mods.ModPackSource()\n"
        "      );\n",
    ),
    (
        "net/minecraft/world/item/CreativeModeTab.java",
        "creative-tabs",
        "// VBV_MODS_HOOK: creative-tabs",
        "      this.displayItemsGenerator.accept(parameters, displayList);\n",
        "      this.displayItemsGenerator.accept(parameters, displayList);\n"
        "      // VBV_MODS_HOOK: creative-tabs\n"
        "      com.melonman106.vbvclient.mods.ModItemGroups.append(this, stack -> displayList.accept(stack));\n",
    ),
]

# Validate and stage every edit before writing any file.
staged = {}
for rel, label, marker, anchor, replacement in EDITS:
    path = JAVA / rel
    if not path.is_file():
        raise SystemExit(f"::error::{rel} is missing")

    text = staged.get(path)
    if text is None:
        text = path.read_text(encoding="utf-8")

    if marker in text:
        staged[path] = text
        continue

    count = text.count(anchor)
    if count != 1:
        raise SystemExit(
            f"::error::VBV mods hook '{label}' anchor matched {count} time(s) "
            f"in {rel}; expected exactly 1"
        )

    staged[path] = text.replace(anchor, replacement, 1)

changed = []
for path, text in staged.items():
    if text != path.read_text(encoding="utf-8"):
        changed.append(path)

for path in changed:
    path.write_text(staged[path], encoding="utf-8")

print(
    f"[VBV mods] verified {len(EDITS)} distinct hooks "
    f"({len(changed)} file(s) updated)"
)
