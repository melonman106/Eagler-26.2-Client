#!/usr/bin/env python3
"""Replace TeaVM's unsafe dynamic global resolver in the standalone HTML packager."""
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve()
PACKAGER = ROOT / "wasm-toolchain" / "build-single-html.js"
if not PACKAGER.is_file():
    raise SystemExit(f"::error::u2 single-HTML packager is missing: {PACKAGER}")

text = PACKAGER.read_text(encoding="utf-8")
marker = "/* VBV safe global resolver patch */"
if marker in text:
    print("[VBV runtime] safe global resolver patch already installed")
    raise SystemExit(0)

needle = "  vbvValidateExecutableJavaScript(html);"
if text.count(needle) != 1:
    raise SystemExit(f"::error::Expected exactly one VBV final-HTML validation call; found {text.count(needle)}")

patch = r'''  /* VBV safe global resolver patch */
  const vbvGlobalBefore = `let globalsCache = new Map();
let stackDeobfuscator = null;
let exceptionFrameRegex = /.+:wasm-function\[[0-9]+\]:0x([0-9a-f]+).*/;
let getGlobalName = function(name) {
    let result = globalsCache.get(name);
    if (typeof result === "undefined") {
        result = new Function("return " + name + ";");
        globalsCache.set(name, result);
    }
    return result();
}
let setGlobalName = function(name, value) {
    new Function("value", name + " = value;")(value);
}`;
  const vbvGlobalAfter = `let globalsCache = new Map();
let stackDeobfuscator = null;
let exceptionFrameRegex = /.+:wasm-function\[[0-9]+\]:0x([0-9a-f]+).*/;
function globalPath(name) {
    if (!/^[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*$/.test(name)) {
        throw new Error("Unsafe global path: " + name);
    }
    let parts = name.split(".");
    if (parts[0] === "globalThis") parts.shift();
    return parts;
}
let getGlobalName = function(name) {
    let result = globalsCache.get(name);
    if (typeof result === "undefined") {
        let parts = globalPath(name);
        result = function() {
            let value = globalThis;
            for (let part of parts) value = value[part];
            return value;
        };
        globalsCache.set(name, result);
    }
    return result();
}
let setGlobalName = function(name, value) {
    let parts = globalPath(name);
    if (parts.length === 0) throw new Error("Cannot replace globalThis");
    let target = globalThis;
    for (let i = 0; i + 1 < parts.length; ++i) target = target[parts[i]];
    target[parts[parts.length - 1]] = value;
}`;
  if (!html.includes(vbvGlobalBefore)) {
    throw new Error("VBV safe global resolver patch: TeaVM dynamic global resolver was not found in final HTML");
  }
  if (html.indexOf(vbvGlobalBefore) !== html.lastIndexOf(vbvGlobalBefore)) {
    throw new Error("VBV safe global resolver patch: expected exactly one TeaVM dynamic global resolver");
  }
  html = html.replace(vbvGlobalBefore, vbvGlobalAfter);
  if (html.includes('new Function("return " + name + ";")') ||
      html.includes('new Function("value", name + " = value;")')) {
    throw new Error("VBV safe global resolver patch: dynamic global resolver remains in final HTML");
  }
  console.info("[VBV runtime] replaced TeaVM dynamic global resolver with safe property traversal");

'''

text = text.replace(needle, patch + needle, 1)
PACKAGER.write_text(text, encoding="utf-8")
print(f"[VBV runtime] installed safe global resolver patch: {PACKAGER}")
