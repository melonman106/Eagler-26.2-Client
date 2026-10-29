#!/usr/bin/env node
"use strict";
/*
 * Turns a standalone Eaglercraft 26.2 single-file client into a "lean" client
 * for low-memory devices (school Chromebooks): multiplayer only.
 *
 * Why this exists
 *   The u2 page shell compiles ALL THREE embedded WebAssembly modules at boot
 *   (client, mesh worker, integrated-server worker) no matter what
 *   serverWorker / meshWorkers are set to. Flipping those two flags alone (or
 *   deleting the worker payloads, as workflow 04 used to) therefore does not
 *   save memory; deleting the payloads without changing the shell even makes
 *   the shell's compile-once path throw and fall back to compiling the 103 MB
 *   client module a second time.
 *
 *   This script changes the shell so it skips the worker modules whenever
 *   they are disabled, and only then removes their payloads:
 *     1. serverWorker / meshWorkers  -> false
 *     2. shell: do not decode/compile the mesh / server wasm when disabled
 *     3. remove the mesh + server brotli payloads (~13 MB of the file)
 *     4. remove the server-worker bootstrap Blob (holds a second copy of
 *        assets.epk as text)
 *
 * Every step is anchored. If the page shell no longer looks the way this
 * script expects it exits non-zero WITHOUT writing a half-patched file.
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

const SERVER_FLAG = /serverWorker:\s*window\.location\.search\.indexOf\("singlethread"\)\s*<\s*0\s*,/;
const MESH_FLAG =
  /meshWorkers:\s*window\.location\.search\.indexOf\("singlethread"\)\s*<\s*0\s*&&\s*window\.location\.search\.indexOf\("nomesh"\)\s*<\s*0\s*,/;
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

// 1. flags (a build may already have them flipped to false by an earlier step)
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

// 2. shell must skip the worker modules when they are disabled
if (!MESH_PROMISE.test(html)) die("shell 'meshModulePromise' not found (loader changed?)");
if (!SERVER_PROMISE.test(html)) die("shell 'serverModulePromise' not found (loader changed?)");
html = html
  .replace(MESH_PROMISE, "$1(window.eaglercraftXOpts && window.eaglercraftXOpts.meshWorkers === false) ? Promise.resolve(null) : ")
  .replace(SERVER_PROMISE, "$1(window.eaglercraftXOpts && window.eaglercraftXOpts.serverWorker === false) ? Promise.resolve(null) : ");

// 3. remove payloads
let removed = 0;
html = html.replace(PAYLOAD_RE, () => { removed++; return ""; });
if (removed !== 2) die("expected to remove 2 worker payloads, removed " + removed);

// 4. server bootstrap blob (optional optimisation)
const blobStart = html.indexOf(BLOB_START);
const blobEnd = blobStart < 0 ? -1 : html.indexOf(BLOB_END, blobStart);
let blobRemoved = false;
if (blobStart >= 0 && blobEnd >= 0) {
  html = html.slice(0, blobStart) + "  window.__eaglerWasmServerWorkerBootstrapURL = null;" + html.slice(blobEnd + BLOB_END.length);
  blobRemoved = true;
} else {
  console.warn("[lean-shell] WARNING: server bootstrap blob markers not found; leaving it in place");
}

// verification of the result
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
