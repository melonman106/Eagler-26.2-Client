#!/usr/bin/env python3
"""Patch the u2 standalone HTML build for low-memory browsers.

The u2 workspace assembles the final single-HTML WASM bootstrap from generated
assets. Patch the single-HTML packager so the generated HTML is rewritten
immediately before it is written, then fail closed if the expected bootstrap
cannot be patched.
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

  const serverWorkerPattern = /serverWorker:\s*window\.location\.search\.indexOf\("singlethread"\)\s*<\s*0/g;
  const meshWorkerPattern = /meshWorkers:\s*window\.location\.search\.indexOf\("singlethread"\)\s*<\s*0/g;
  if (!serverWorkerPattern.test(html) || !meshWorkerPattern.test(html)) {
    throw new Error("VBV low-memory patch: generated HTML is missing the expected worker options");
  }
  serverWorkerPattern.lastIndex = 0;
  meshWorkerPattern.lastIndex = 0;

  html = html.replace(
    serverWorkerPattern,
    `serverWorker: ${enableWorkers} /* serverWorker: window.location.search.indexOf("singlethread") < 0 */`
  );
  html = html.replace(
    meshWorkerPattern,
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

    // Replace the complete three-declaration region. Do not use a regexp to
    // strip the terminating semicolon: the generated blocks can contain
    // comments/newlines after the expression, and leaving that semicolon in a
    // ternary expression produces the exact `Unexpected token ';'` seen by
    // Android WebView.
    const meshBlock = html.slice(meshStart, serverStart);
    const serverBlock = html.slice(serverStart, workersStart);

    const meshSemicolon = meshBlock.lastIndexOf(";");
    const serverSemicolon = serverBlock.lastIndexOf(";");
    if (meshSemicolon < 0 || serverSemicolon < 0) {
      throw new Error("VBV low-memory patch: worker promise declaration terminator not found");
    }

    const meshExpr = meshBlock.slice(0, meshSemicolon).replace(
      "const meshModulePromise = WebAssembly.compileStreaming(",
      "const meshModulePromise = enableWorkers ? WebAssembly.compileStreaming("
    );
    const serverExpr = serverBlock.slice(0, serverSemicolon).replace(
      "const serverModulePromise =",
      "const serverModulePromise = enableWorkers ?"
    );
    if (meshExpr === meshBlock.slice(0, meshSemicolon) || serverExpr === serverBlock.slice(0, serverSemicolon)) {
      throw new Error("VBV low-memory patch: worker compile expression was not rewritten");
    }

    const replacement =
      "/* VBV low-memory worker bootstrap */\n" +
      "const enableWorkers = window.location.search.indexOf(\"workers\") >= 0 && window.location.search.indexOf(\"singlethread\") < 0;\n" +
      "console.info(\"[VBV runtime] worker mode:\", enableWorkers ? \"enabled\" : \"single-threaded\");\n" +
      meshExpr +
      " : Promise.resolve(null);\n" +
      serverExpr +
      " : Promise.resolve(null);\n" +
      "const workerModules = enableWorkers ? await Promise.all([meshModulePromise, serverModulePromise]) : [null, null];";

    html = html.slice(0, meshStart) + replacement + html.slice(workersStart + workersLine.length);
  }

  const requiredRuntime = [
    "const enableWorkers = window.location.search.indexOf(\"workers\") >= 0 && window.location.search.indexOf(\"singlethread\") < 0;",
    "/* VBV low-memory worker bootstrap */",
    "const workerModules = enableWorkers ? await Promise.all([meshModulePromise, serverModulePromise]) : [null, null];"
  ];
  for (const token of requiredRuntime) {
    if (!html.includes(token)) {
      throw new Error(`VBV low-memory patch: final HTML is missing required runtime marker: ${token}`);
    }
  }

  if (!html.includes("[VBV runtime]")) {
    const bootMarker = '<div id="boot_status">';
    const diagnostic = '<script>window.addEventListener("error",function(e){console.error("[VBV runtime] uncaught startup error:",e.error||e.message,e.filename||"",e.lineno||"");});window.addEventListener("unhandledrejection",function(e){console.error("[VBV runtime] unhandled startup rejection:",e.reason);});console.info("[VBV runtime] standalone bootstrap loaded");</script>';
    const bootPos = html.indexOf(bootMarker);
    if (bootPos >= 0) html = html.slice(0, bootPos) + diagnostic + html.slice(bootPos);
  }

  return html;
}

const vbvOriginalWriteFileSync = fs.writeFileSync;
fs.writeFileSync = function(file, data, ...args) {
  if (path.resolve(String(file)) === output) {
    data = Buffer.from(vbvPatchStandaloneHtml(data), "utf8");
  }
  return vbvOriginalWriteFileSync.call(fs, file, data, ...args);
};

'''
    text = text[:pos] + patch + text[pos:]
    PACKAGER.write_text(text, encoding="utf-8")
    print(f"[VBV runtime] installed standalone packager hook: {PACKAGER}")
else:
    print(f"[VBV runtime] standalone packager hook already installed: {PACKAGER}")

print("[VBV runtime] low-memory worker startup patch installed; normal startup is single-threaded and ?workers opts in to worker compilation.")
