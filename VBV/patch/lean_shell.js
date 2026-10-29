#!/usr/bin/env node
"use strict";
/*
 * Turns a standalone Eaglercraft 26.2 single-file client into a "lean" client
 * for low-memory devices (school Chromebooks): multiplayer only.
 *
 * The normal VBV runtime patch may already have rewritten the worker options
 * to use an explicit runtime expression and left the original expression in
 * a comment. This script accepts both the upstream u2 shell and that patched
 * form, then makes the final lean shell unconditionally single-threaded.
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

// Accept the original u2 options as well as the VBV runtime-patched form.
// The runtime patch intentionally leaves the upstream expression in a
// comment, so the old "expression immediately followed by comma" regex was
// too strict and caused this step to fail with "loader changed?".
const SERVER_FLAG = /serverWorker:\s*window\.location\.search\.indexOf\("singlethread"\)\s*<\s*0\s*(?:\/\*[\s\S]*?\*\/\s*)?,/;
const MESH_FLAG =
  /meshWorkers:\s*window\.location\.search\.indexOf\("singlethread"\)\s*<\s*0\s*(?:&&\s*window\.location\.search\.indexOf\("nomesh"\)\s*<\s*0\s*)?(?:\/\*[\s\S]*?\*\/\s*)?,/;
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

// 1. flags. A build may contain either the untouched upstream expression or
// the VBV runtime-patched expression with a trailing explanatory comment.
if (SERVER_FLAG.test(html)) {
  html = html.replace(SERVER_FLAG, "serverWorker: false,");
} else if (!/serverWorker:\s*false\s*,/.test(html)) {
  die("serverWorker option not found in the page shell (loader changed?)");
}
if (MESH_FLAG.test(html)) {
  html = html.replace(MESH_FLAG, "meshWorkers: false,");
} else if (!/meshWorkers:\s*false\s*,/.test(html)) {
  die("meshWorkers option not found in the page shell (loader changed?)");
}

// 2. shell must skip the worker modules when they are disabled.
if (!MESH_PROMISE.test(html)) die("shell 'meshModulePromise' not found (loader changed?)");
if (!SERVER_PROMISE.test(html)) die("shell 'serverModulePromise' not found (loader changed?)");
html = html
  .replace(MESH_PROMISE, "$1(window.eaglercraftXOpts && window.eaglercraftXOpts.meshWorkers === false) ? Promise.resolve(null) : ")
  .replace(SERVER_PROMISE, "$1(window.eaglercraftXOpts && window.eaglercraftXOpts.serverWorker === false) ? Promise.resolve(null) : ");

// 3. remove worker payloads only after the shell no longer tries to compile
// them when disabled.
let removed = 0;
html = html.replace(PAYLOAD_RE, () => { removed++; return ""; });
if (removed !== 2) die("expected to remove 2 worker payloads, removed " + removed);

// 4. server bootstrap blob (optional optimisation).
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
if (!html.includes("eaglercraftXOpts.meshWorkers === false")) die("shell guard was not installed");

fs.writeFileSync(output, html);
console.log("[lean-shell] " + input + " -> " + output);
console.log("[lean-shell] size: " + originalLength.toLocaleString() + " -> " + html.length.toLocaleString() +
  " chars (" + (originalLength - html.length).toLocaleString() + " smaller); server blob removed: " + blobRemoved);
