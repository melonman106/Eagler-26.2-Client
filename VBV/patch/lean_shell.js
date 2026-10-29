#!/usr/bin/env node
"use strict";
/*
 * Turns a standalone Eaglercraft 26.2 single-file client into a "lean" client
 * for low-memory devices: multiplayer only, with both worker WASM payloads
 * removed after the shell is changed to skip those modules.
 *
 * Accepts the original u2 shell and the VBV runtime-patched shell.
 *
 * usage: node lean_shell.js <in.html> <out.html>
 */
const fs = require("node:fs");

const [, , input, output] = process.argv;
if (!input || !output) {
  console.error("usage: node lean_shell.js <in.html> <out.html>");
  process.exit(2);
}

function die(message) {
  console.error("[lean-shell] ERROR: " + message);
  process.exit(1);
}

let html = fs.readFileSync(input, "utf8");
const originalLength = html.length;

// Original upstream u2 expressions.
const SERVER_FLAG = /serverWorker:\s*window\.location\.search\.indexOf\("singlethread"\)\s*<\s*0\s*(?:\/\*[\s\S]*?\*\/\s*)?,/;
const MESH_FLAG = /meshWorkers:\s*window\.location\.search\.indexOf\("singlethread"\)\s*<\s*0\s*(?:&&\s*window\.location\.search\.indexOf\("nomesh"\)\s*<\s*0\s*)?(?:\/\*[\s\S]*?\*\/\s*)?,/;

// Current VBV runtime patch inlines the worker gate into each option and may
// leave the original expression in a trailing comment. These regexes are
// deliberately regex literals (not String.raw) so the escaping is exact.
const SERVER_INLINE_FLAG = /serverWorker:\s*window\.location\.search\.indexOf\("workers"\)\s*>=\s*0\s*&&\s*window\.location\.search\.indexOf\("singlethread"\)\s*<\s*0\s*(?:\/\*[\s\S]*?\*\/\s*)?,/;
const MESH_INLINE_FLAG = /meshWorkers:\s*window\.location\.search\.indexOf\("workers"\)\s*>=\s*0\s*&&\s*window\.location\.search\.indexOf\("singlethread"\)\s*<\s*0\s*(?:\/\*[\s\S]*?\*\/\s*)?(?:&&\s*window\.location\.search\.indexOf\("nomesh"\)\s*<\s*0\s*)?,/;
const SERVER_VARIABLE_FLAG = /serverWorker:\s*enableWorkers\b(?:\s*\/\*[\s\S]*?\*\/\s*)?,/;
const MESH_VARIABLE_FLAG = /meshWorkers:\s*enableWorkers\b(?:\s*\/\*[\s\S]*?\*\/\s*)?(?:&&\s*window\.location\.search\.indexOf\("nomesh"\)\s*<\s*0\s*)?,/;

const PAYLOAD_RE = /<script\b[^>]*\bid=["']eag-inline-(?:mesh-wasm-br|server-wasm-br)["'][^>]*>[\s\S]*?<\/script>\s*/g;
const MESH_PROMISE = /(const\s+meshModulePromise\s*=\s*)/;
const SERVER_PROMISE = /(const\s+serverModulePromise\s*=\s*)/;
const BLOB_START = "  window.__eaglerWasmServerWorkerBootstrapURL = URL.createObjectURL(new Blob([";
const BLOB_END = '  ], { type: "text/javascript" }));';

const alreadyLean =
  /serverWorker:\s*false\s*,/.test(html) &&
  /meshWorkers:\s*false\s*,/.test(html) &&
  !/id=["']eag-inline-(?:mesh|server)-wasm-br["']/.test(html);
if (alreadyLean) {
  console.log("[lean-shell] input is already lean; copying unchanged");
  fs.writeFileSync(output, html);
  process.exit(0);
}

// 1. Force both worker options off. Accept untouched upstream expressions,
// the older enableWorkers form, and the current inlined condition.
if (SERVER_FLAG.test(html)) {
  html = html.replace(SERVER_FLAG, "serverWorker: false,");
} else if (SERVER_INLINE_FLAG.test(html)) {
  html = html.replace(SERVER_INLINE_FLAG, "serverWorker: false,");
} else if (SERVER_VARIABLE_FLAG.test(html)) {
  html = html.replace(SERVER_VARIABLE_FLAG, "serverWorker: false,");
} else if (!/serverWorker:\s*false\s*,/.test(html)) {
  die("serverWorker option not found in the page shell (loader changed?)");
}

if (MESH_FLAG.test(html)) {
  html = html.replace(MESH_FLAG, "meshWorkers: false,");
} else if (MESH_INLINE_FLAG.test(html)) {
  html = html.replace(MESH_INLINE_FLAG, "meshWorkers: false,");
} else if (MESH_VARIABLE_FLAG.test(html)) {
  html = html.replace(MESH_VARIABLE_FLAG, "meshWorkers: false,");
} else if (!/meshWorkers:\s*false\s*,/.test(html)) {
  die("meshWorkers option not found in the page shell (loader changed?)");
}

// 2. The shell must skip the worker modules when disabled. If the runtime
// patch already installed the guards, leave them intact; otherwise install.
if (!MESH_PROMISE.test(html)) die("shell 'meshModulePromise' not found (loader changed?)");
if (!SERVER_PROMISE.test(html)) die("shell 'serverModulePromise' not found (loader changed?)");
if (!html.includes("eaglercraftXOpts && window.eaglercraftXOpts.meshWorkers === false")) {
  html = html.replace(
    MESH_PROMISE,
    "$1(window.eaglercraftXOpts && window.eaglercraftXOpts.meshWorkers === false) ? Promise.resolve(null) : "
  );
}
if (!html.includes("eaglercraftXOpts && window.eaglercraftXOpts.serverWorker === false")) {
  html = html.replace(
    SERVER_PROMISE,
    "$1(window.eaglercraftXOpts && window.eaglercraftXOpts.serverWorker === false) ? Promise.resolve(null) : "
  );
}

// 3. Remove worker payloads only after the shell has been changed so it will
// not attempt to decode or compile them when disabled.
let removed = 0;
html = html.replace(PAYLOAD_RE, () => { removed++; return ""; });
if (removed !== 2) die("expected to remove 2 worker payloads, removed " + removed);

// 4. Remove the server bootstrap blob (optional optimisation).
const blobStart = html.indexOf(BLOB_START);
const blobEnd = blobStart < 0 ? -1 : html.indexOf(BLOB_END, blobStart);
let blobRemoved = false;
if (blobStart >= 0 && blobEnd >= 0) {
  html = html.slice(0, blobStart) + "  window.__eaglerWasmServerWorkerBootstrapURL = null;" + html.slice(blobEnd + BLOB_END.length);
  blobRemoved = true;
} else {
  console.warn("[lean-shell] WARNING: server bootstrap blob markers not found; leaving it in place");
}

// Verification of the result.
for (const id of ["eag-inline-wasm-br", "eag-inline-assets", "eag-inline-sounds", "eag-inline-decoder"]) {
  if (!html.includes('id="' + id + '"') && !html.includes("id='" + id + "'")) die("required payload missing after patch: " + id);
}
if (/id=["']eag-inline-(?:mesh|server)-wasm-br["']/.test(html)) die("worker payload still present after patch");
if (!/serverWorker:\s*false\s*,/.test(html) || !/meshWorkers:\s*false\s*,/.test(html)) die("flags were not installed");
if (!html.includes("eaglercraftXOpts.meshWorkers === false")) die("mesh shell guard was not installed");
if (!html.includes("eaglercraftXOpts.serverWorker === false")) die("server shell guard was not installed");

fs.writeFileSync(output, html);
console.log("[lean-shell] " + input + " -> " + output);
console.log("[lean-shell] size: " + originalLength.toLocaleString() + " -> " + html.length.toLocaleString() +
  " chars (" + (originalLength - html.length).toLocaleString() + " smaller); worker payloads removed: " + removed + "; server blob removed: " + blobRemoved);
