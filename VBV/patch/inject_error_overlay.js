#!/usr/bin/env node
"use strict";
/*
 * Adds an always-visible on-screen VBV runtime console to a standalone
 * Eaglercraft HTML file. It needs no DevTools and is intended for managed
 * Chromebooks.
 *
 * usage: node inject_error_overlay.js <in.html> <out.html>
 */
const fs = require("node:fs");

const [, , input, output] = process.argv;
if (!input || !output) {
  console.error("usage: node inject_error_overlay.js <in.html> <out.html>");
  process.exit(2);
}

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
  function note(list, msg) {
    list.push("[" + secs(Date.now() - t0) + "] " + short(msg, 300));
    if (list.length > 40) list.shift();
  }

  window.__vbvDiagReady = true;
  try { console.log("[VBV console] diagnostics overlay started"); } catch (e) { /* ignore */ }

  window.addEventListener("error", function (e) {
    note(errors, "ERROR " + (e.message || "") + (e.error ? " :: " + errText(e.error) : ""));
  }, true);
  window.addEventListener("unhandledrejection", function (e) {
    note(errors, "REJECTED " + errText(e.reason));
  });

  var pending = {};
  var pendingId = 0;
  if (window.WebAssembly) {
    ["compile", "instantiate", "compileStreaming", "instantiateStreaming"].forEach(function (name) {
      var original = WebAssembly[name];
      if (typeof original !== "function") return;
      try {
        WebAssembly[name] = function () {
          var id = ++pendingId, start = Date.now();
          pending[id] = name;
          note(wasmLines, "wasm." + name + " started");
          var result;
          try { result = original.apply(WebAssembly, arguments); }
          catch (e) {
            delete pending[id];
            note(wasmLines, "wasm." + name + " THREW " + errText(e));
            throw e;
          }
          if (result && typeof result.then === "function") {
            result.then(
              function () { delete pending[id]; note(wasmLines, "wasm." + name + " finished in " + secs(Date.now() - start)); },
              function (e) { delete pending[id]; note(wasmLines, "wasm." + name + " FAILED after " + secs(Date.now() - start) + ": " + errText(e)); }
            );
          }
          return result;
        };
      } catch (e) {
        note(errors, "Could not hook WebAssembly." + name + ": " + errText(e));
      }
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
    out.push("VBV RUNTIME CONSOLE  elapsed " + secs(Date.now() - t0) + "   main-thread max block " + secs(maxGap));
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
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text);
        return true;
      }
    } catch (e) { /* fall through */ }
    try {
      var ta = document.createElement("textarea");
      ta.value = text;
      ta.style.cssText = "position:fixed;left:-9999px";
      document.body.appendChild(ta);
      ta.select();
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
      "background:rgba(0,0,0,.92);color:#ffe97a;font:12px/1.35 monospace;z-index:2147483647;" +
      "border-top:2px solid #ffe97a;pointer-events:auto;";
    var bar = document.createElement("div");
    bar.style.cssText = "position:sticky;top:0;background:#222;padding:5px 8px;display:flex;gap:8px;align-items:center;cursor:pointer";
    var title = document.createElement("b");
    title.textContent = "VBV runtime console (tap to collapse)";
    title.style.flex = "1";
    bar.onclick = function () {
      collapsed = !collapsed;
      pre.style.display = collapsed ? "none" : "block";
    };
    var btn = document.createElement("button");
    btn.textContent = "Copy report";
    btn.onclick = function (ev) {
      ev.stopPropagation();
      btn.textContent = copyText(report()) ? "Copied" : "Copy failed - select text";
      setTimeout(function () { btn.textContent = "Copy report"; }, 2000);
    };
    bar.appendChild(title);
    bar.appendChild(btn);
    pre = document.createElement("pre");
    pre.style.cssText = "margin:0;padding:6px 8px;white-space:pre-wrap;word-break:break-word;user-select:text";
    box.appendChild(bar);
    box.appendChild(pre);
    host.appendChild(box);
    return true;
  }

  function refresh() {
    try {
      if (ensureBox() && !collapsed) pre.textContent = report();
    } catch (e) {
      try { if (pre) pre.textContent = "VBV console internal error: " + errText(e); } catch (ignored) { /* ignore */ }
    }
  }

  refresh();
  setInterval(function () {
    var now = Date.now();
    maxGap = Math.max(maxGap, now - lastBeat - 1000);
    lastBeat = now;
    refresh();
  }, 1000);
}

const tag = '<script id="vbv-diag-script">(' + diag.toString() + ")();</script>";
const html = fs.readFileSync(input, "utf8");

// Do not inject a second console when workflow 04 processes the normal
// workflow-02 artifact, which already contains this console.
if (html.includes('id="vbv-diag-script"')) {
  fs.writeFileSync(output, html);
  console.log("[overlay] input already contains vbv-diag-script; copied unchanged");
  process.exit(0);
}

// Prefer the body start so the console has a real DOM host immediately. Fall
// back to <head> for shells that do not contain a body element.
const body = /<body\b[^>]*>/i.exec(html);
const head = /<head[^>]*>/i.exec(html);
let at;
if (body) {
  at = body.index + body[0].length;
} else if (head) {
  at = head.index + head[0].length;
} else {
  console.error("[overlay] ERROR: no <body> or <head> found in " + input);
  process.exit(1);
}

const out = html.slice(0, at) + tag + html.slice(at);
fs.writeFileSync(output, out);
console.log("[overlay] injected " + tag.length + " chars at " + (body ? "<body>" : "<head>") + ": " + input + " -> " + output);
