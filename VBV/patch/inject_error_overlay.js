#!/usr/bin/env node
"use strict";
/* Small, dependency-free runtime console for the standalone Eagler HTML. */
const fs = require("node:fs");
const [, , input, output] = process.argv;
if (!input || !output) { console.error("usage: node inject_error_overlay.js <in.html> <out.html>"); process.exit(2); }

function diag() {
  var t0 = Date.now(), errors = [], wasmLines = [], maxGap = 0, lastBeat = t0;
  var box = null, pre = null, collapsed = false;
  function secs(ms) { return (ms / 1000).toFixed(1) + "s"; }
  function short(v, n) { v = String(v); return v.length > n ? v.slice(0, n) + "..." : v; }
  function errText(e) { return e && e.stack ? String(e.stack).split("\n").slice(0, 4).join(" | ") : String(e); }
  function note(list, msg) { list.push("[" + secs(Date.now() - t0) + "] " + short(msg, 300)); if (list.length > 40) list.shift(); }

  // Do not monkey-patch Function/WebAssembly or any Eagler runtime primitive.
  // The console must be observational only so it cannot itself break startup.
  window.__vbvDiagReady = true;
  window.addEventListener("error", function(e) {
    var detail = "ERROR ";
    if (e && e.message) detail += e.message;
    if (e && e.filename) detail += " @ " + e.filename + ":" + (e.lineno || "?") + ":" + (e.colno || "?");
    if (e && e.error) detail += " :: " + errText(e.error);
    note(errors, detail);
  }, true);
  window.addEventListener("unhandledrejection", function(e) { note(errors, "REJECTED " + errText(e.reason)); });

  function capabilities() {
    var c = [];
    try { c.push("WasmGC=" + WebAssembly.validate(new Uint8Array([0,97,115,109,1,0,0,0,1,5,1,95,1,120,0]))); } catch(e) { c.push("WasmGC=?"); }
    c.push("compileStreaming=" + (typeof WebAssembly.compileStreaming === "function"));
    c.push("SharedArrayBuffer=" + (typeof SharedArrayBuffer !== "undefined"));
    c.push("deviceMemoryGB=" + (navigator.deviceMemory || "?"));
    c.push("cores=" + (navigator.hardwareConcurrency || "?"));
    return c.join("  ");
  }
  function memory() {
    try { if (performance && performance.memory) return "jsHeap " + Math.round(performance.memory.usedJSHeapSize / 1048576) + " MB used / limit " + Math.round(performance.memory.jsHeapSizeLimit / 1048576) + " MB"; } catch(e) {}
    return "jsHeap n/a";
  }
  function report() {
    var out = [], status = "", stage = "?";
    try { status = (document.getElementById("boot_status") || {}).textContent || ""; } catch(e) {}
    try { var s = window.__eaglerCrashJournal && window.__eaglerCrashJournal.snapshot(); if (s) stage = s.stage + " (" + s.state + ")"; } catch(e) {}
    out.push("VBV RUNTIME CONSOLE  elapsed " + secs(Date.now() - t0) + "   main-thread max block " + secs(maxGap));
    out.push("screen text : " + status);
    out.push("loader stage: " + stage + "   loaded=" + !!window.__loaded + (window.__err ? "   __err=" + window.__err : ""));
    out.push(memory());
    out.push(capabilities());
    out.push("--- loader log ---");
    try { Array.prototype.push.apply(out, (window.__log || []).slice(-16).map(function(x) { return short(x, 320); })); } catch(e) {}
    if (errors.length) { out.push("--- errors ---"); Array.prototype.push.apply(out, errors.slice(-10)); }
    if (wasmLines.length) { out.push("--- runtime traps ---"); Array.prototype.push.apply(out, wasmLines.slice(-10)); }
    if (Date.now() - t0 > 20000 && status.indexOf("Loading Eaglercraft") === 0) out.push(">>> startup has not completed");
    return out.join("\n");
  }
  function ensureBox() {
    if (box && box.isConnected) return true;
    var host = document.body || document.documentElement;
    if (!host) return false;
    box = document.createElement("div");
    box.id = "vbv-diag-box";
    box.style.cssText = "position:fixed;left:0;right:0;bottom:0;max-height:55vh;overflow:auto;background:rgba(0,0,0,.94);color:#ffe97a;font:12px/1.35 monospace;z-index:2147483647;border-top:2px solid #ffe97a;pointer-events:auto";
    var bar = document.createElement("div");
    bar.style.cssText = "position:sticky;top:0;background:#222;padding:5px 8px;display:flex;gap:8px;align-items:center";
    var title = document.createElement("b");
    title.textContent = "VBV runtime console (tap to collapse)";
    title.style.flex = "1";
    bar.onclick = function() { collapsed = !collapsed; pre.style.display = collapsed ? "none" : "block"; };
    var btn = document.createElement("button");
    btn.textContent = "Save report";
    btn.onclick = function(ev) {
      ev.stopPropagation();
      try {
        var a = document.createElement("a");
        a.href = URL.createObjectURL(new Blob([report()], {type:"text/plain"}));
        a.download = "vbv-runtime-report.txt";
        a.click();
        setTimeout(function() { URL.revokeObjectURL(a.href); }, 1000);
        btn.textContent = "Saved";
      } catch(e) { btn.textContent = "Save failed"; }
      setTimeout(function() { btn.textContent = "Save report"; }, 2000);
    };
    bar.appendChild(title); bar.appendChild(btn);
    pre = document.createElement("pre");
    pre.style.cssText = "margin:0;padding:6px 8px;white-space:pre-wrap;word-break:break-word;user-select:text";
    box.appendChild(bar); box.appendChild(pre); host.appendChild(box); return true;
  }
  function refresh() { try { if (ensureBox() && !collapsed) pre.textContent = report(); } catch(e) { try { if(pre) pre.textContent = "VBV console internal error: " + errText(e); } catch(ignore) {} } }
  refresh();
  setInterval(function() { var now = Date.now(); maxGap = Math.max(maxGap, now - lastBeat - 1000); lastBeat = now; refresh(); }, 1000);
}

const tag = '<script id="vbv-diag-script">(' + diag.toString() + ")();</script>";
const html = fs.readFileSync(input, "utf8");
if (html.includes('id="vbv-diag-script"')) { fs.writeFileSync(output, html); console.log("[overlay] already present; copied unchanged"); process.exit(0); }
const body = /<body\b[^>]*>/i.exec(html), head = /<head[^>]*>/i.exec(html);
if (!body && !head) { console.error("[overlay] ERROR: no <body> or <head> found"); process.exit(1); }
const at = body ? body.index + body[0].length : head.index + head[0].length;
fs.writeFileSync(output, html.slice(0, at) + tag + html.slice(at));
console.log("[overlay] injected visible runtime console into " + output);
