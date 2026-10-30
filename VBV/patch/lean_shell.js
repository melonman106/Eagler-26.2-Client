#!/usr/bin/env node
"use strict";
/* Build a safe single-threaded lean Eaglercraft HTML. */
const fs = require("node:fs");
const [, , input, output] = process.argv;
if (!input || !output) { console.error("usage: node lean_shell.js <in.html> <out.html>"); process.exit(2); }
function die(message) { console.error("[lean-shell] ERROR: " + message); process.exit(1); }
let html = fs.readFileSync(input, "utf8");
const originalLength = html.length;
function has(id) { return html.includes('id="' + id + '"') || html.includes("id='" + id + "'"); }

const SERVER_FLAG = /serverWorker:\s*(?:window\.location\.search\.indexOf\("singlethread"\)\s*<\s*0|window\.location\.search\.indexOf\("workers"\)\s*>=\s*0\s*&&\s*window\.location\.search\.indexOf\("singlethread"\)\s*<\s*0|enableWorkers\b)(?:\s*\/\*[\s\S]*?\*\/\s*)?,/;
const MESH_FLAG = /meshWorkers:\s*(?:window\.location\.search\.indexOf\("singlethread"\)\s*<\s*0|window\.location\.search\.indexOf\("workers"\)\s*>=\s*0\s*&&\s*window\.location\.search\.indexOf\("singlethread"\)\s*<\s*0|enableWorkers\b)(?:\s*\/\*[\s\S]*?\*\/\s*)?,/;

if (SERVER_FLAG.test(html)) html = html.replace(SERVER_FLAG, "serverWorker: false,");
else if (!/serverWorker:\s*false\s*,/.test(html)) die("serverWorker option not found");
if (MESH_FLAG.test(html)) html = html.replace(MESH_FLAG, "meshWorkers: false,");
else if (!/meshWorkers:\s*false\s*,/.test(html)) die("meshWorkers option not found");

// Find the generated worker declarations without depending on a marker added
// by another patch. This works with both the raw u2 output and the VBV-patched
// output, while replacing the whole declaration region in one operation.
const meshStart = html.indexOf("const meshModulePromise =");
const serverStart = meshStart < 0 ? -1 : html.indexOf("const serverModulePromise =", meshStart);
const workerModulesStart = serverStart < 0 ? -1 : html.indexOf("const workerModules =", serverStart);
if (meshStart < 0 || serverStart < 0 || workerModulesStart < 0) die("worker bootstrap declarations not found");

// The generated workerModules declaration ends at its first semicolon. The
// preceding mesh/server declarations are replaced together, so no nested
// ternary or async expression from an earlier patch can survive.
const workerModulesEnd = html.indexOf(";", workerModulesStart);
if (workerModulesEnd < 0) die("workerModules terminator not found");

const inert =
  "/* VBV low-memory worker bootstrap */\n" +
  "const enableWorkers = false;\n" +
  "console.info(\"[VBV runtime] worker mode: single-threaded\");\n" +
  "const meshModulePromise = Promise.resolve(null);\n" +
  "const serverModulePromise = Promise.resolve(null);\n" +
  "const workerModules = [null, null];";
html = html.slice(0, meshStart) + inert + html.slice(workerModulesEnd + 1);

// Remove the dedicated worker WASM payloads only after their runtime code has
// been neutralized. Keep the normal client WASM, decoder, assets and sounds.
const PAYLOAD_RE = /<script\b[^>]*\bid=["']eag-inline-(?:mesh-wasm-br|server-wasm-br)["'][^>]*>[\s\S]*?<\/script>\s*/g;
let removed = 0;
html = html.replace(PAYLOAD_RE, () => { removed++; return ""; });
if (removed !== 2) die("expected 2 worker payloads, removed " + removed);

// Remove a server worker bootstrap blob if present.
const blobStartText = "window.__eaglerWasmServerWorkerBootstrapURL = URL.createObjectURL(new Blob([";
const blobEndText = '], { type: "text/javascript" }));';
const blobStart = html.indexOf(blobStartText);
const blobEnd = blobStart < 0 ? -1 : html.indexOf(blobEndText, blobStart);
if (blobStart >= 0 && blobEnd >= 0) {
  html = html.slice(0, blobStart) + "window.__eaglerWasmServerWorkerBootstrapURL = null;" + html.slice(blobEnd + blobEndText.length);
}

if (!/serverWorker:\s*false\s*,/.test(html)) die("serverWorker was not disabled");
if (!/meshWorkers:\s*false\s*,/.test(html)) die("meshWorkers was not disabled");
if (!/const enableWorkers = false;/.test(html)) die("single-threaded gate missing");
if (!/const meshModulePromise = Promise\.resolve\(null\);/.test(html)) die("mesh promise not neutralized");
if (!/const serverModulePromise = Promise\.resolve\(null\);/.test(html)) die("server promise not neutralized");
if (!/const workerModules = \[null, null\];/.test(html)) die("workerModules not neutralized");
if (has("eag-inline-mesh-wasm-br") || has("eag-inline-server-wasm-br")) die("worker payload remains");
for (const id of ["eag-inline-wasm-br", "eag-inline-assets", "eag-inline-sounds", "eag-inline-decoder"]) if (!has(id)) die("required payload missing: " + id);

fs.writeFileSync(output, html);
console.log("[lean-shell] " + input + " -> " + output);
console.log("[lean-shell] size: " + originalLength.toLocaleString() + " -> " + html.length.toLocaleString() + " chars; worker payloads removed: " + removed);
