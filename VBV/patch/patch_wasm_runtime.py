#!/usr/bin/env python3
"""Patch the u2 standalone HTML build for low-memory browsers.

The u2 workspace assembles the final single-HTML WASM bootstrap from generated
assets. The worker bootstrap is therefore not a stable source template inside
wasm-toolchain/*.js. Patch the single-HTML packager so the generated HTML is
rewritten immediately before it is written.
"""
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
TOOLCHAIN = ROOT / "wasm-toolchain"
PACKAGER = TOOLCHAIN / "build-single-html.js"

if not TOOLCHAIN.is_dir():
    raise SystemExit(f"::error::WASM toolchain directory is missing: {TOOLCHAIN}")
if not PACKAGER.is_file():
    raise SystemExit(f"::error::u2 single-HTML packager is missing: {PACKAGER}")

text = PACKAGER.read_text(encoding="utf-8")
marker = "/* VBV low-memory standalone HTML patch */"

if marker not in text:
    needle = "const output = path.resolve(repo, valueAfter("
    pos = text.find(needle)
    if pos < 0:
        raise SystemExit("::error::Could not find the u2 single-HTML output declaration in build-single-html.js")

    patch = r'''/* VBV low-memory standalone HTML patch */
function vbvPatchStandaloneHtml(source) {
  let html = Buffer.isBuffer(source) ? source.toString("utf8") : String(source);
  const enableWorkers = 'window.location.search.indexOf("workers") >= 0 && window.location.search.indexOf("singlethread") < 0';

  html = html.replace(
    /serverWorker:\s*window\.location\.search\.indexOf\("singlethread"\)\s*<\s*0/g,
    `serverWorker: ${enableWorkers} /* serverWorker: window.location.search.indexOf("singlethread") < 0 */`
  );
  html = html.replace(
    /meshWorkers:\s*window\.location\.search\.indexOf\("singlethread"\)\s*<\s*0/g,
    `meshWorkers: ${enableWorkers} /* meshWorkers: window.location.search.indexOf("singlethread") < 0 */`
  );

  if (!html.includes("/* VBV low-memory worker bootstrap */")) {
    const meshStart = html.indexOf("const meshModulePromise = WebAssembly.compileStreaming(");
    const serverStart = html.indexOf("const serverModulePromise =", meshStart + 1);
    const workersLine = "const workerModules = await Promise.all([meshModulePromise, serverModulePromise]);";
    const workersStart = html.indexOf(workersLine, serverStart + 1);

    if (meshStart < 0 || serverStart < 0 || workersStart < 0) {
      throw new Error("VBV low-memory patch: generated u2 HTML does not contain the expected mesh/server worker bootstrap");
    }

    const meshBlock = html.slice(meshStart, serverStart);
    const serverBlock = html.slice(serverStart, workersStart);
    const meshExpr = meshBlock.replace(
      "const meshModulePromise = WebAssembly.compileStreaming(",
      "const meshModulePromise = enableWorkers ? WebAssembly.compileStreaming("
    );
    const meshLazy = meshExpr.replace(/;\s*$/, " : Promise.resolve(null);");
    const serverExpr = serverBlock.replace(
      "const serverModulePromise =",
      "const serverModulePromise = enableWorkers ?"
    );
    const serverLazy = serverExpr.replace(/;\s*$/, " : Promise.resolve(null);");

    const replacement =
      "/* VBV low-memory worker bootstrap */\n" +
      "const enableWorkers = window.location.search.indexOf(\"workers\") >= 0 && window.location.search.indexOf(\"singlethread\") < 0;\n" +
      meshLazy +
      serverLazy +
      "const workerModules = enableWorkers ? await Promise.all([meshModulePromise, serverModulePromise]) : [null, null];";

    html = html.slice(0, meshStart) + replacement + html.slice(workersStart + workersLine.length);
  }

  return html;
}

const vbvOriginalWriteFileSync = fs.writeFileSync;
fs.writeFileSync = function(file, data, ...args) {
  if (path.resolve(String(file)) === output) {
    data = Buffer.from(vbvPatchStandaloneHtml(data), "utf8");
  }
  return vbvOriginalWriteFileSync.call(fs, file, data, ...args);
}

'''
    text = text[:pos] + patch + text[pos:]
    PACKAGER.write_text(text, encoding="utf-8")
    print(f"[VBV runtime] installed standalone packager hook: {PACKAGER}")
else:
    print(f"[VBV runtime] standalone packager hook already installed: {PACKAGER}")

print("[VBV runtime] low-memory worker startup patch installed; normal startup is single-threaded and ?workers opts in to worker compilation.")
