#!/usr/bin/env node
"use strict";
/*
 * Turns a standalone Eaglercraft 26.2 single-file client into a lean
 * single-threaded client for low-memory devices.
 *
 * The VBV build already makes worker WASM lazy behind `enableWorkers`.
 * The safest lean transformation is therefore to force that existing flag to
 * false, disable the public worker options, and remove the worker payloads.
 * We deliberately do NOT rewrite the Promise expressions themselves: that
 * avoids creating nested ternaries in generated JavaScript and prevents the
 * syntax error that occurred in the previous lean build.
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

const SERVER_FLAG = /serverWorker:\s*window\.location\.search\.indexOf\("singlethread"\)\s*<\s*0\s*(?:\/\*[\s\S]*?\*\/\s*)?,/;
const MESH_FLAG = /meshWorkers:\s*window\.location\.search\.indexOf\("singlethread"\)\s*<\s*0\s*(?:\/\*[\s\S]*?\*\/\s*)?(?:&&\s*window\.location\.search\.indexOf\("nomesh"\)\s*<\s*0\s*)?,/;
const SERVER_INLINE_FLAG = /serverWorker:\s*window\.location\.search\.indexOf\("workers"\)\s*>=\s*0\s*&&\s*window\.location\.search\.indexOf\("singlethread"\)\s*<\s*0\s*(?:\/\*[\s\S]*?\*\/\s*)?,/;
const MESH_INLINE_FLAG = /meshWorkers:\s*window\.location\.search\.indexOf\("workers"\)\s*>=\s*0\s*&&\s*window\.location\.search\.indexOf\("singlethread"\)\s*<\s*0\s*(?:\/\*[\s\S]*?\*\/\s*)?(?:&&\s*window\.location\.search\.indexOf\("nomesh"\)\s*<\s*0\s*)?,/;
const SERVER_VARIABLE_FLAG = /serverWorker:\s*enableWorkers\b(?:\s*\/\*[\s\S]*?\*\/\s*)?,/;
const MESH_VARIABLE_FLAG = /meshWorkers:\s*enableWorkers\b(?:\s*\/\*[\s\S]*?\*\/\s*)?(?:&&\s*window\.location\.search\.indexOf\("nomesh"\)\s*<\s*0\s*)?,/;

const ENABLE_WORKERS = /const\s+enableWorkers\s*=\s*window\.location\.search\.indexOf\("workers"\)\s*>=\s*0\s*&&\s*window\.location\.search\.indexOf\("singlethread"\)\s*<\s*0\s*;/;
const ENABLE_WORKERS_FALSE = /const\s+enableWorkers\s*=\s*false\s*;/;
const WORKER_MODULES = /const\s+workerModules\s*=\s*enableWorkers\s*\?\s*await\s+Promise\.all\(\[meshModulePromise,\s*serverModulePromise\]\)\s*:\s*\[null,\s*null\]\s*;/;

const PAYLOAD_RE = /<script\b[^>]*\bid=["']eag-inline-(?:mesh-wasm-br|server-wasm-br)["'][^>]*>[\s\S]*?<\/script>\s*/g;
const BLOB_START = "  window.__eaglerWasmServerWorkerBootstrapURL = URL.createObjectURL(new Blob([";
const BLOB_END = '  ], { type: "text/javascript" }));';

// 1. Disable the public worker options.
if (SERVER_FLAG.test(html)) html = html.replace(SERVER_FLAG, "serverWorker: false,");
else if (SERVER_INLINE_FLAG.test(html)) html = html.replace(SERVER_INLINE_FLAG, "serverWorker: false,");
else if (SERVER_VARIABLE_FLAG.test(html)) html = html.replace(SERVER_VARIABLE_FLAG, "serverWorker: false,");
else if (!/serverWorker:\s*false\s*,/.test(html)) die("serverWorker option not found in the page shell (loader changed?)");

if (MESH_FLAG.test(html)) html = html.replace(MESH_FLAG, "meshWorkers: false,");
else if (MESH_INLINE_FLAG.test(html)) html = html.replace(MESH_INLINE_FLAG, "meshWorkers: false,");
else if (MESH_VARIABLE_FLAG.test(html)) html = html.replace(MESH_VARIABLE_FLAG, "meshWorkers: false,");
else if (!/meshWorkers:\s*false\s*,/.test(html)) die("meshWorkers option not found in the page shell (loader changed?)");

// 2. Force the existing VBV worker gate itself off. This is deliberately a
// simple declaration replacement, not a rewrite of the Promise expressions.
if (ENABLE_WORKERS.test(html)) {
  html = html.replace(ENABLE_WORKERS, "const enableWorkers = false;");
} else if (!ENABLE_WORKERS_FALSE.test(html)) {
  die("enableWorkers declaration not found in the generated shell");
}

if (!WORKER_MODULES.test(html)) {
  die("workerModules declaration not found in the generated shell");
}

// 3. Remove the worker WASM payloads. With enableWorkers=false the existing
// VBV bootstrap will resolve [null, null] without compiling these payloads.
let removed = 0;
html = html.replace(PAYLOAD_RE, () => { removed++; return ""; });
if (removed !== 2) die("expected to remove 2 worker payloads, removed " + removed);

// 4. Remove the optional server worker bootstrap blob.
const blobStart = html.indexOf(BLOB_START);
const blobEnd = blobStart < 0 ? -1 : html.indexOf(BLOB_END, blobStart);
let blobRemoved = false;
if (blobStart >= 0 && blobEnd >= 0) {
  html = html.slice(0, blobStart) + "  window.__eaglerWasmServerWorkerBootstrapURL = null;" + html.slice(blobEnd + BLOB_END.length);
  blobRemoved = true;
}

// 5. Fail closed and verify the actual final text.
for (const id of ["eag-inline-wasm-br", "eag-inline-assets", "eag-inline-sounds", "eag-inline-decoder"]) {
  if (!html.includes('id="' + id + '"') && !html.includes("id='" + id + "'")) die("required payload missing after patch: " + id);
}
if (/id=["']eag-inline-(?:mesh|server)-wasm-br["']/.test(html)) die("worker payload still present after patch");
if (!/serverWorker:\s*false\s*,/.test(html)) die("serverWorker was not disabled");
if (!/meshWorkers:\s*false\s*,/.test(html)) die("meshWorkers was not disabled");
if (!ENABLE_WORKERS_FALSE.test(html)) die("enableWorkers was not forced false");
if (!WORKER_MODULES.test(html)) die("workerModules declaration was changed unexpectedly");

fs.writeFileSync(output, html);
console.log("[lean-shell] " + input + " -> " + output);
console.log("[lean-shell] size: " + originalLength.toLocaleString() + " -> " + html.length.toLocaleString() +
  " chars (" + (originalLength - html.length).toLocaleString() + " smaller); worker payloads removed: " + removed + "; server blob removed: " + blobRemoved);
