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
  // Match the resolver structurally instead of comparing its complete source.
  // TeaVM's generated runtime can change harmless whitespace between builds.
  const resolverStart = html.indexOf("let globalsCache = new Map();");
  const resolverEndMarker = "let setGlobalName = function(name, value) {";
  const getGlobalMarker = "let getGlobalName = function(name) {";
  const setGlobalStart = html.indexOf(resolverEndMarker, resolverStart + 1);
  const getGlobalStart = html.indexOf(getGlobalMarker, resolverStart + 1);
  if (resolverStart < 0 || getGlobalStart < 0 || setGlobalStart < 0 ||
      html.indexOf("let globalsCache = new Map();", resolverStart + 1) >= 0 ||
      html.indexOf(getGlobalMarker, getGlobalStart + 1) >= 0 ||
      html.indexOf(resolverEndMarker, setGlobalStart + 1) >= 0 ||
      !(resolverStart < getGlobalStart && getGlobalStart < setGlobalStart)) {
    throw new Error("VBV safe global resolver patch: expected one complete TeaVM global resolver block; found globals=" +
      (resolverStart >= 0) + ", getter=" + (getGlobalStart >= 0) + ", setter=" + (setGlobalStart >= 0));
  }

  // Keep TeaVM's exception-frame regular expression verbatim. It is unrelated
  // to global lookup and is used by the runtime's stack deobfuscator.
  const exceptionMarker = "let exceptionFrameRegex = ";
  const exceptionStart = html.indexOf(exceptionMarker, resolverStart);
  const exceptionEnd = exceptionStart < 0 ? -1 : html.indexOf("\n", exceptionStart);
  if (exceptionStart < 0 || exceptionEnd < 0 || exceptionStart > getGlobalStart) {
    throw new Error("VBV safe global resolver patch: TeaVM exception-frame regex was not found in the resolver block");
  }
  const exceptionLine = html.slice(exceptionStart, exceptionEnd);

  // The setter is the final function in this block. Its closing brace is the
  // first line-level closing brace after its declaration.
  const setterEnd = html.indexOf("\n}", setGlobalStart);
  if (setterEnd < 0) {
    throw new Error("VBV safe global resolver patch: TeaVM global setter terminator was not found");
  }
  const resolverEnd = setterEnd + 2;

  const safeResolver = String.raw`let globalsCache = new Map();
let stackDeobfuscator = null;
${exceptionLine}
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

  html = html.slice(0, resolverStart) + safeResolver + html.slice(resolverEnd);
  if (html.includes('new Function("return " + name + ";")') ||
      html.includes('new Function("value", name + " = value;")')) {
    throw new Error("VBV safe global resolver patch: dynamic global resolver remains in final HTML");
  }
  if (!html.includes("function globalPath(name)") ||
      !html.includes("A-Za-z0-9_$]*(?:\\.[A-Za-z_$]")) {
    throw new Error("VBV safe global resolver patch: safe property-path validator was not emitted correctly");
  }
  console.info("[VBV runtime] replaced TeaVM dynamic global resolver with validated property traversal");

'''
text = text.replace(needle, patch + needle, 1)
PACKAGER.write_text(text, encoding="utf-8")
print(f"[VBV runtime] installed safe global resolver patch: {PACKAGER}")
