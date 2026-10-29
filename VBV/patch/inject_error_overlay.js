#!/usr/bin/env node
"use strict";
/*
 * Diagnostics for a client that is stuck on "Loading Eaglercraft 26.2 runtime...".
 *
 * Adds an on-screen panel (no DevTools needed, so it works on a managed
 * Chromebook) that shows:
 *   - how long the page has been loading and which boot stage the loader is on
 *   - the loader's own log (window.__log) and last error
 *   - timing of every WebAssembly compile / instantiate, and whether it finished
 *   - memory, device and browser capabilities
 *   - whether the main thread was blocked, and for how long
 * The "Copy report" button puts the whole thing on the clipboard so it can be
 * pasted into a chat. Click the panel title to collapse it.
 *
 * usage: node inject_error_overlay.js <in.html> <out.html>
 */
const fs = require("node:fs");

const [, , input, output] = process.argv;
if (!input || !output) {
  console.error("usage: node inject_error_overlay.js <in.html> <out.html>");
  process.exit(2);
}

// Serialized into the page: must be self-contained and must never throw.
function diag() {
  var t0 = Date.now();
  var wasmLines = [];
  var errors = [];
  var maxGap = 0;
  var lastBeat = Date.now();
  var collapsed = false;
  var box = null, pre = null;

  function secs(ms) { return (ms / 1000).toFixed(1) + "s"; }
  function short(v, n) { v = String(v); return v.length > n ? v.slice(0, n) + "..." : v; }
  function errText(e) { return e && e.stack ? String(e.stack).split("\n").slice(0, 4).join(" | ") : String(e); }
  function note(list, msg) { list.push("[" + secs(Date.now() - t0) + "] " + short(msg, 300)); if (list.length > 40) list.shift(); }

  window.addEventListener("error", function (e) {
    note(errors, "ERROR " + (e.message || "") + (e.error ? " :: " + errText(e.error) : ""));
  }, true);
  window.addEventListener("unhandledrejection", function (e) { note(errors, "REJECTED " + errText(e.reason)); });

  // Time every WebAssembly compile/instantiate.
  var pending = {};
  var pendingId = 0;
  if (window.WebAssembly) {
    ["compile", "instantiate", "compileStreaming", "instantiateStreaming"].forEach(function (name) {
      var original = WebAssembly[name];
      if (typeof original !== "function") return;
      WebAssembly[name] = function () {
        var id = ++pendingId, start = Date.now();
        pending[id] = name;
        note(wasmLines, "wasm." + name + " started");
        var result;
        try { result = original.apply(WebAssembly, arguments); }
        catch (e) { delete pending[id]; note(wasmLines, "wasm." + name + " THREW " + errText(e)); throw e; }
        if (result && typeof result.then === "function") {
          result.then(
            function () { delete pending[id]; note(wasmLines, "wasm." + name + " finished in " + secs(Date.now() - start)); },
            function (e) { delete pending[id]; note(wasmLines, "wasm." + name + " FAILED after " + secs(Date.now() - start) + ": " + errText(e)); }
          );
        }
        return result;
      };
    });
  }

  function capabilities() {
    var c = [];
    try { c.push("WasmGC=" + WebAssembly.validate(new Uint8Array([0, 97, 115, 109, 1, 0, 0, 0, 1, 5, 1, 95, 1, 120, 0]))); } catch (e) { c.push("WasmGC=?"); }
    c.push("compileStreaming=" + (typeof WebAssembly.compileStreaming === "function"));
    c.push("SharedArrayBuffer=" + (typeof SharedArrayBuffer !== "undefined"));
    c.push("deviceMemoryGB=" + (navigator.deviceMemory || "?"));
    c.push("cores=" + (navigator.hardwareConcurrency || "?"));
    c.push((String(navigator.userAgent).match(/(Chrome|CrOS)[^ ]*/g) || ["UA=?"]).join(" "));
    c.push("url=" + (location.protocol === "file:" ? "file:" : location.origin));
    return c.join("  ");
  }

  function memory() {
    try {
      if (performance && performance.memory) {
        return "jsHeap " + Math.round(performance.memory.usedJSHeapSize / 1048576) + " MB used / limit " +
          Math.round(performance.memory.jsHeapSizeLimit / 1048576) + " MB";
      }
    } catch (e) { /* ignore */ }
    return "jsHeap n/a";
  }

  function report() {
    var out = [];
    var stage = "?", journalErr = "";
    try {
      var snap = window.__eaglerCrashJournal && window.__eaglerCrashJournal.snapshot();
      if (snap) { stage = snap.stage + " (" + snap.state + ")"; journalErr = snap.lastWindowError || snap.error || ""; }
    } catch (e) { stage = "journal unavailable"; }
    var status = "";
    try { status = (document.getElementById("boot_status") || {}).textContent || ""; } catch (e) { /* ignore */ }
    var open = Object.keys(pending).map(function (k) { return pending[k]; });
    out.push("VBV DIAG  elapsed " + secs(Date.now() - t0) + "   main-thread max block " + secs(maxGap));
    out.push("screen text : " + status);
    out.push("loader stage: " + stage + "   loaded=" + !!window.__loaded + (window.__err ? "   __err=" + window.__err : ""));
    out.push("wasm in flight: " + (open.length ? open.join(", ") : "none"));
    out.push(memory() + "   inlinePayloadCache=" + (window.__eaglerInlinePayloadCacheBytes || 0) + " B");
    out.push(capabilities());
    if (journalErr) out.push("last window error: " + short(journalErr, 400));
    out.push("--- wasm ---");
    Array.prototype.push.apply(out, wasmLines.slice(-12));
    out.push("--- loader log (window.__log) ---");
    try { Array.prototype.push.apply(out, (window.__log || []).slice(-14).map(function (l) { return short(l, 300); })); } catch (e) { /* ignore */ }
    if (errors.length) { out.push("--- errors ---"); Array.prototype.push.apply(out, errors.slice(-8)); }
    if (Date.now() - t0 > 20000 && status.indexOf("Loading Eaglercraft") === 0) {
      out.push(">>> still on the first screen after " + secs(Date.now() - t0) + ": see 'wasm in flight' / 'max block' above");
    }
    return out.join("\n");
  }

  function copyText(text) {
    try {
      if (navigator.clipboard && navigator.clipboard.writeText) { navigator.clipboard.writeText(text); return true; }
    } catch (e) { /* fall through */ }
    try {
      var ta = document.createElement("textarea");
      ta.value = text; ta.style.cssText = "position:fixed;left:-9999px";
      document.body.appendChild(ta); ta.select();
      var ok = document.execCommand("copy");
      document.body.removeChild(ta);
      return ok;
    } catch (e) { return false; }
  }

  function ensureBox() {
    if (box && box.isConnected) return true;
    var host = document.body || document.documentElement;
    if (!host) return false;
    box = document.createElement("div");
    box.id = "vbv-diag-box";
    box.style.cssText = "position:fixed;left:0;right:0;bottom:0;max-height:55vh;overflow:auto;margin:0;" +
      "background:rgba(0,0,0,.9);color:#ffe97a;font:12px/1.35 monospace;z-index:2147483647;";
    var bar = document.createElement("div");
    bar.style.cssText = "position:sticky;top:0;background:#222;padding:4px 8px;display:flex;gap:8px;align-items:center;cursor:pointer";
    var title = document.createElement("b");
    title.textContent = "VBV diagnostics (tap to collapse)";
    title.style.flex = "1";
    bar.onclick = function () { collapsed = !collapsed; pre.style.display = collapsed ? "none" : "block"; };
    var btn = document.createElement("button");
    btn.textContent = "Copy report";
    btn.onclick = function (ev) { ev.stopPropagation(); btn.textContent = copyText(report()) ? "Copied" : "Copy failed - select text"; setTimeout(function () { btn.textContent = "Copy report"; }, 2000); };
    bar.appendChild(title); bar.appendChild(btn);
    pre = document.createElement("pre");
    pre.style.cssText = "margin:0;padding:6px 8px;white-space:pre-wrap;word-break:break-word;user-select:text";
    box.appendChild(bar); box.appendChild(pre);
    host.appendChild(box);
    return true;
  }

  setInterval(function () {
    var now = Date.now();
    maxGap = Math.max(maxGap, now - lastBeat - 1000);
    lastBeat = now;
    try { if (ensureBox() && !collapsed) pre.textContent = report(); } catch (e) { /* never break the page */ }
  }, 1000);
}

const tag = '<script id="vbv-diag-script">(' + diag.toString() + ")();</script>";

const html = fs.readFileSync(input, "utf8");
const match = /<head[^>]*>/i.exec(html);
if (!match) {
  console.error("[overlay] ERROR: no <head> found in " + input);
  process.exit(1);
}
const at = match.index + match[0].length;
const out = html.slice(0, at) + tag + html.slice(at);
fs.writeFileSync(output, out);
console.log("[overlay] injected " + tag.length + " chars after <head>: " + input + " -> " + output);
