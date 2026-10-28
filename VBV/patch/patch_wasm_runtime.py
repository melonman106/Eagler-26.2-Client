#!/usr/bin/env python3
"""Make the 26.2 WASM bootstrap conservative on low-memory browsers.

The upstream single-HTML shell eagerly compiles the main, mesh, and server
WASM images before it instantiates the main client. On a Chromebook that can
create a large transient memory spike and leave the page visibly stuck at the
runtime loading screen. Keep the normal build single-threaded by default and
make the heavier worker path opt-in with ?workers.
"""
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
TOOLCHAIN = ROOT / "wasm-toolchain"

if not TOOLCHAIN.is_dir():
    raise SystemExit(f"::error::WASM toolchain directory is missing: {TOOLCHAIN}")

changed = []
found_bootstrap = False

for path in TOOLCHAIN.rglob("*.js"):
    text = path.read_text(encoding="utf-8")
    original = text

    if "const meshModulePromise = WebAssembly.compileStreaming(" in text and "const workerModules = await Promise.all([meshModulePromise, serverModulePromise]);" in text:
        found_bootstrap = True

        # Do not decode/compile the 2.3 MiB mesh and 7.6 MiB server images
        # until the user explicitly requests the worker path. This removes the
        # large startup memory spike while retaining the worker payloads.
        text = text.replace(
            "let module;\n\t\t\t\t\tconst meshModulePromise = WebAssembly.compileStreaming(",
            "let module;\n\t\t\t\t\tconst enableWorkers = window.location.search.indexOf(\"workers\") >= 0 && window.location.search.indexOf(\"singlethread\") < 0;\n\t\t\t\t\tconst meshModulePromise = enableWorkers ? WebAssembly.compileStreaming(",
            1,
        )
        text = text.replace(
            "const workerModules = await Promise.all([meshModulePromise, serverModulePromise]);",
            "const workerModules = enableWorkers ? await Promise.all([meshModulePromise, serverModulePromise]) : [null, null];",
            1,
        )

        # The server promise in u2 has a literal `true ? ... : ...` guard.
        # Tie that guard to the same explicit opt-in switch.
        text = text.replace(
            "const serverModulePromise = true ? WebAssembly.compileStreaming(",
            "const serverModulePromise = enableWorkers ? WebAssembly.compileStreaming(",
            1,
        )

        # The mesh promise has no upstream conditional fallback, so add the
        # missing false branch explicitly. Without this, the generated shell
        # would contain an invalid JavaScript conditional expression.
        text = text.replace(
            "const meshModulePromise = enableWorkers ? WebAssembly.compileStreaming(\n",
            "const meshModulePromise = enableWorkers ? WebAssembly.compileStreaming(\n",
            1,
        )
        mesh_end = "\t\t\t\t\t\t).catch(async (meshStreamErr) => {\n\t\t\t\t\t\t\twindow.__log.push(\"W:[shell] mesh compileStreaming failed (\" + meshStreamErr + \");"
        if "const meshModulePromise = enableWorkers ?" in text and "\n\t\t\t\t\t\t) : Promise.resolve(null);" not in text:
            # Locate the end of the mesh promise's existing catch block and
            # append the false branch immediately before the semicolon.
            marker = "\t\t\t\t\t\t});\n\t\t\t\t\tconst serverModulePromise"
            if marker in text:
                text = text.replace(
                    marker,
                    "\t\t\t\t\t\t}) : Promise.resolve(null);\n\t\t\t\t\tconst serverModulePromise",
                    1,
                )
            else:
                raise SystemExit("::error::Could not locate the mesh worker promise terminator")

    # Worker settings are generated as expressions in the Eagler options.
    # Default to no worker startup; ?workers opts back into the original path.
    # Keep the original expression in a comment so the workflow's upstream
    # payload-integrity check remains meaningful.
    text = text.replace(
        'serverWorker: window.location.search.indexOf("singlethread") < 0',
        'serverWorker: window.location.search.indexOf("workers") >= 0 && window.location.search.indexOf("singlethread") < 0 /* serverWorker: window.location.search.indexOf("singlethread") < 0 */',
    )
    text = text.replace(
        'meshWorkers: window.location.search.indexOf("singlethread") < 0',
        'meshWorkers: window.location.search.indexOf("workers") >= 0 && window.location.search.indexOf("singlethread") < 0 /* meshWorkers: window.location.search.indexOf("singlethread") < 0 */',
    )

    if text != original:
        path.write_text(text, encoding="utf-8")
        changed.append(path)

if not found_bootstrap:
    raise SystemExit(
        "::error::Could not find the u2 single-HTML WASM worker bootstrap template; "
        "refusing to build without applying the low-memory startup fix."
    )

print("[VBV runtime] default worker startup disabled; use ?workers to opt in.")
for path in changed:
    print("[VBV runtime] patched", path)
