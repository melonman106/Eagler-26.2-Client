#!/usr/bin/env node
/**
 * Headless boot smoke test for the standalone client.
 *
 * The build can only prove that Java compiled and that the HTML contains the
 * markers the packager promised. It cannot prove that the client boots: a bad
 * injection, a missing asset, a broken pack merge or a render-start failure all
 * build fine and then fail in the browser. This script serves the built HTML
 * with the cross-origin isolation headers the threaded build needs, opens it in
 * headless Chromium and waits for `window.__loaded === true`, which the TeaVM
 * shell sets after the main module has instantiated.
 *
 * It writes `smoke-report.txt` and `smoke.png` next to its output directory so a
 * failure can be read without a browser, and exits non-zero when the client
 * never gets that far. It is deliberately tolerant of console errors: packs and
 * browsers log things that are not fatal, and a boot that completes is a pass.
 *
 * usage: node VBV/patch/smoke_test.mjs <html> [out-dir]
 * env:   VBV_SMOKE_TIMEOUT_MS  how long to wait for the client (default 480000)
 *        VBV_SMOKE_VIEWPORT    WxH, default 1280x720
 */
import { createServer } from "node:http";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

const [, , htmlArg = "eaglercraft-26.2-vbv-herz.html", outArg = "smoke-out"] = process.argv;
const timeoutMs = Number(process.env.VBV_SMOKE_TIMEOUT_MS || 480000);
const [vw, vh] = String(process.env.VBV_SMOKE_VIEWPORT || "1280x720")
  .split("x")
  .map((value) => Number(value) || 0);

const html = await readFile(htmlArg);
const outDir = resolve(outArg);
await mkdir(outDir, { recursive: true });

const isolation = {
  "cross-origin-opener-policy": "same-origin",
  "cross-origin-embedder-policy": "require-corp",
  "cross-origin-resource-policy": "same-origin",
  "cache-control": "no-store",
};

const server = createServer((request, response) => {
  const path = new URL(request.url || "/", "http://127.0.0.1").pathname;
  if (path === "/" || path === "/index.html") {
    response.writeHead(200, { ...isolation, "content-type": "text/html; charset=utf-8" });
    response.end(html);
    return;
  }
  response.writeHead(404, isolation);
  response.end("not found");
});
await new Promise((done) => server.listen(0, "127.0.0.1", done));
const port = server.address().port;

const consoleLines = [];
const pageErrors = [];
const failedRequests = [];
let browser;
let loaded = false;
let state = {};

try {
  const { chromium } = await import("playwright");
  browser = await chromium.launch({
    args: [
      // Headless Chromium has no GPU on a CI runner; SwiftShader is the only
      // path to the WebGL2 context the game needs.
      "--use-gl=angle",
      "--use-angle=swiftshader",
      "--enable-unsafe-swiftshader",
      "--disable-dev-shm-usage",
      "--no-sandbox",
    ],
  });
  const page = await browser.newPage({
    viewport: { width: vw || 1280, height: vh || 720 },
  });
  page.on("console", (message) => consoleLines.push(`[${message.type()}] ${message.text()}`));
  page.on("pageerror", (error) => pageErrors.push(String(error)));
  page.on("requestfailed", (request) =>
    failedRequests.push(`${request.url()} :: ${request.failure()?.errorText || "failed"}`));

  const started = Date.now();
  await page.goto(`http://127.0.0.1:${port}/index.html`, {
    waitUntil: "domcontentloaded",
    timeout: 120000,
  });

  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    state = await page
      .evaluate(() => ({
        loaded: window.__loaded === true,
        error: typeof window.__err === "string" ? window.__err : "",
        status: (document.getElementById("boot_status") || {}).textContent || "",
        safe: window.__vbvSafeMode === true,
        previousFailures: Number(window.__vbvPreviousFailures) || 0,
        webgl: (() => {
          try {
            const canvas = document.createElement("canvas");
            return String(!!canvas.getContext("webgl2"));
          } catch (error) {
            return "error";
          }
        })(),
      }))
      .catch(() => ({}));
    if (state.loaded === true) {
      loaded = true;
      break;
    }
    if (state.error) {
      break;
    }
    await page.waitForTimeout(2000);
  }

  const elapsed = Math.round((Date.now() - started) / 1000);
  const report = [
    "VBV headless boot smoke test",
    `html        : ${resolve(htmlArg)} (${html.length} bytes)`,
    `elapsed     : ${elapsed}s (limit ${Math.round(timeoutMs / 1000)}s)`,
    `loaded      : ${loaded}`,
    `boot status : ${state.status || "(none)"}`,
    `loader error: ${state.error || "(none)"}`,
    `safe mode   : ${state.safe === true}`,
    `webgl2      : ${state.webgl || "unknown"}`,
    `previous failed launches: ${state.previousFailures || 0}`,
    "",
    "--- page errors ---",
    ...(pageErrors.length ? pageErrors.slice(-20) : ["(none)"]),
    "",
    "--- failed requests ---",
    ...(failedRequests.length ? failedRequests.slice(-20) : ["(none)"]),
    "",
    "--- console (last 40) ---",
    ...(consoleLines.length ? consoleLines.slice(-40) : ["(none)"]),
    "",
  ].join("\n");

  await writeFile(resolve(outDir, "smoke-report.txt"), report, "utf8");
  try {
    await page.screenshot({ path: resolve(outDir, "smoke.png"), fullPage: false });
  } catch (error) {
    console.warn(`[smoke] screenshot failed: ${error}`);
  }

  console.log(report);
  if (!loaded) {
    const reason = state.error
      ? `the loader reported an error: ${state.error}`
      : `the client never set window.__loaded (last screen text: ${state.status || "(none)"})`;
    console.log(`::error::Headless boot failed after ${elapsed}s: ${reason}`);
    process.exitCode = 1;
  } else {
    console.log(`::notice::Headless boot reached the title screen in ${elapsed}s.`);
  }
} catch (error) {
  await writeFile(
    resolve(outDir, "smoke-report.txt"),
    `VBV headless boot smoke test crashed before it could observe the client.\n${error?.stack || error}\n`,
    "utf8",
  );
  console.error(error);
  console.log("::error::Headless boot smoke test could not run (see smoke-report.txt)");
  process.exitCode = 1;
} finally {
  try {
    await browser?.close();
  } catch (error) {
    /* closing a dead browser must not change the result */
  }
  await new Promise((done) => server.close(done));
}
