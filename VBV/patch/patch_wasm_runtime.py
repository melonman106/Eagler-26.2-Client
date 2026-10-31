#!/usr/bin/env python3
"""Patch the u2 standalone HTML build for low-memory browsers.

The u2 workspace assembles the final single-HTML WASM bootstrap from generated
assets. Patch the single-HTML packager so the generated HTML is rewritten
immediately before it is written, then fail closed if the expected bootstrap
cannot be patched.
"""
from pathlib import Path
import json
import sys

ROOT = Path(sys.argv[1]).resolve()
TOOLCHAIN = ROOT / "wasm-toolchain"
PACKAGER = TOOLCHAIN / "build-single-html.js"
STORAGE_SCRIPT = Path(__file__).resolve().parent / "storage_manager.js"
if not STORAGE_SCRIPT.is_file():
    raise SystemExit(f"::error::VBV storage manager script is missing: {STORAGE_SCRIPT}")
STORAGE_SCRIPT_TEXT = STORAGE_SCRIPT.read_text(encoding="utf-8")

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
function vbvValidateExecutableJavaScript(html) {
  // The standalone HTML contains large application/octet-stream script tags
  // carrying binary WASM/assets. Only parse executable JavaScript blocks.
  const blocks = [...html.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/gi)];
  let checked = 0;
  for (let i = 0; i < blocks.length; ++i) {
    const attrs = blocks[i][1] || "";
    const script = blocks[i][2] || "";
    const typeMatch = attrs.match(/\btype=["']([^"']+)["']/i);
    const type = typeMatch ? typeMatch[1].toLowerCase() : "text/javascript";
    if (!["text/javascript", "application/javascript", "module", "text/ecmascript"].includes(type)) continue;
    if (!script.trim()) continue;
    try {
      const vm = require("node:vm");
      if (type === "module") {
        // vm.Script is deliberately used for classic script syntax only. A
        // module block is still checked by wrapping it as a Script-compatible
        // source where possible; if it contains imports/exports, skip it here
        // rather than falsely rejecting valid module syntax.
        if (/^\s*(?:import|export)\b/m.test(script)) continue;
      }
      new vm.Script(script, { filename: `vbv-standalone-script-${i}.js` });
      checked++;
    } catch (error) {
      throw new Error(`VBV final HTML JavaScript syntax error in script block ${i}: ${error.message}`);
    }
  }
  if (checked === 0) throw new Error("VBV final HTML validation found no executable JavaScript blocks");
  console.log(`[VBV runtime] validated ${checked} executable JavaScript blocks before writing standalone HTML`);
}

function vbvPatchStandaloneHtml(source) {
  let html = Buffer.isBuffer(source) ? source.toString("utf8") : String(source);
  const storageScript = __VBV_STORAGE_SCRIPT_JSON__;
  if (!html.includes("VBV_STORAGE_MANAGER_V1")) {
    const storageTag = "<script>\\n" + storageScript + "\\n</script>";
    const headEnd = html.lastIndexOf("</head>");
    if (headEnd < 0) throw new Error("VBV storage manager: standalone HTML has no closing </head> tag");
    html = html.slice(0, headEnd) + storageTag + html.slice(headEnd);
  }
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

    // Find the first top-level semicolon terminating each declaration.
    // lastIndexOf() is unsafe because callbacks/comments may contain semicolons.
    function findDeclarationSemicolon(source) {
      let round = 0, square = 0, curly = 0;
      let quote = null, escaped = false, lineComment = false, blockComment = false;
      for (let i = 0; i < source.length; ++i) {
        const ch = source[i], next = source[i + 1];
        if (lineComment) { if (ch === "\n") lineComment = false; continue; }
        if (blockComment) {
          if (ch === "*" && next === "/") { blockComment = false; ++i; }
          continue;
        }
        if (quote !== null) {
          if (escaped) { escaped = false; continue; }
          if (ch === "\\") { escaped = true; continue; }
          if (ch.charCodeAt(0) === quote) quote = null;
          continue;
        }
        if (ch === "/" && next === "/") { lineComment = true; ++i; continue; }
        if (ch === "/" && next === "*") { blockComment = true; ++i; continue; }
        if (ch === "'" || ch === '"') { quote = ch.charCodeAt(0); continue; }
        if (ch.charCodeAt(0) === 96) { quote = 96; continue; }
        if (ch === "(") ++round;
        else if (ch === ")") --round;
        else if (ch === "[") ++square;
        else if (ch === "]") --square;
        else if (ch === "{") ++curly;
        else if (ch === "}") --curly;
        else if (ch === ";" && round === 0 && square === 0 && curly === 0) return i;
      }
      return -1;
    }

    const meshSemicolon = findDeclarationSemicolon(meshBlock);
    const serverSemicolon = findDeclarationSemicolon(serverBlock);
    if (meshSemicolon < 0 || serverSemicolon < 0) {
      throw new Error("VBV low-memory patch: top-level worker promise declaration terminator not found");
    }

    const meshBefore = meshBlock.slice(0, meshSemicolon);
    const serverBefore = serverBlock.slice(0, serverSemicolon);
    const meshExpr = meshBefore.replace(
      "const meshModulePromise = WebAssembly.compileStreaming(",
      "const meshModulePromise = enableWorkers ? WebAssembly.compileStreaming("
    );
    const serverExpr = serverBefore.replace(
      "const serverModulePromise =",
      "const serverModulePromise = enableWorkers ?"
    );
    if (meshExpr === meshBefore || serverExpr === serverBefore) {
      throw new Error("VBV low-memory patch: worker compile expression was not rewritten");
    }

    const replacement =
      "/* VBV low-memory worker bootstrap */\n" +
      "const enableWorkers = window.location.search.indexOf(\"workers\") >= 0 && window.location.search.indexOf(\"singlethread\") < 0;\n" +
      "console.info(\"[VBV runtime] worker mode:\", enableWorkers ? \"enabled\" : \"single-threaded\");\n" +
      meshExpr + " : Promise.resolve(null);" + meshBlock.slice(meshSemicolon + 1) +
      serverExpr + " : Promise.resolve(null);" + serverBlock.slice(serverSemicolon + 1) +
      "const workerModules = enableWorkers ? await Promise.all([meshModulePromise, serverModulePromise]) : [null, null];";

    html = html.slice(0, meshStart) + replacement + html.slice(workersStart + workersLine.length);
  }

  const requiredRuntime = [
    "const enableWorkers = window.location.search.indexOf(\"workers\") >= 0 && window.location.search.indexOf(\"singlethread\") < 0;",
    "/* VBV low-memory worker bootstrap */",
    "const workerModules = enableWorkers ? await Promise.all([meshModulePromise, serverModulePromise]) : [null, null];",
    "VBV_STORAGE_MANAGER_V1"
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

  vbvValidateExecutableJavaScript(html);
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
    patch = patch.replace("__VBV_STORAGE_SCRIPT_JSON__", json.dumps(STORAGE_SCRIPT_TEXT))
    if "__VBV_STORAGE_SCRIPT_JSON__" in patch:
        raise SystemExit("::error::VBV storage manager script placeholder was not replaced")
    text = text[:pos] + patch + text[pos:]
    PACKAGER.write_text(text, encoding="utf-8")
    print(f"[VBV runtime] installed standalone packager hook: {PACKAGER}")
else:
    print(f"[VBV runtime] standalone packager hook already installed: {PACKAGER}")

print("[VBV runtime] low-memory worker startup patch installed; normal startup is single-threaded and ?workers opts in to worker compilation.")
