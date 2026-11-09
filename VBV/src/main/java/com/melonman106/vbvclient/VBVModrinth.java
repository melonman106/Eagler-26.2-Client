package com.melonman106.vbvclient;

import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Int8Array;

/**
 * Modrinth resource-pack browser. All network traffic happens in the browser through a small
 * JavaScript bridge (the same pattern {@link VBVParty} uses); Java only polls its state each
 * frame, so nothing here ever blocks the render thread.
 *
 * <p>The bridge talks to the public Modrinth API v2: {@code /search} with the
 * {@code project_type:resourcepack} facet, {@code /project/{id}/version} to pick a file, and the
 * file's CDN URL to download it. Downloads are checked against the SHA-512 Modrinth publishes.
 * No long values cross the JS boundary (TeaVM WASM-GC would require BigInt), only int, double
 * and String.</p>
 */
public final class VBVModrinth {
    public static final int PAGE_SIZE = 6;
    public static final String[] SORT_IDS = {"relevance", "downloads", "follows", "newest", "updated"};
    public static final String[] SORT_NAMES = {"Relevance", "Downloads", "Follows", "Newest", "Updated"};

    /** One search result row. */
    public static final class Hit {
        public final String id;
        public final String title;
        public final String author;
        public final int downloads;
        public final String description;

        Hit(String id, String title, String author, int downloads, String description) {
            this.id = id;
            this.title = title;
            this.author = author;
            this.downloads = downloads;
            this.description = description;
        }
    }

    private VBVModrinth() {}

    public static void initialize() { initializeJS(); }

    /** Starts a search; {@code limit} is how many rows the launcher can show per page. */
    public static void search(String query, int offset, String sort, int limit) {
        searchJS(query == null ? "" : query, Math.max(0, offset), sort == null ? "relevance" : sort,
            Math.max(1, Math.min(PAGE_SIZE, limit)));
    }

    public static boolean searchBusy() { return flagJS("busy"); }
    public static String searchStatus() { return textJS("status"); }
    public static int totalHits() { return numberJS("total"); }
    public static int pageOffset() { return numberJS("offset"); }

    public static Hit[] results() {
        String raw = rowsJS();
        if (raw == null || raw.isEmpty()) return new Hit[0];
        String[] lines = raw.split("\\n");
        Hit[] hits = new Hit[lines.length];
        int count = 0;
        for (String line : lines) {
            String[] f = line.split("\\t", -1);
            if (f.length < 5) continue;
            int downloads;
            try { downloads = Integer.parseInt(f[3]); } catch (NumberFormatException e) { downloads = 0; }
            hits[count++] = new Hit(f[0], f[1], f[2], downloads, f[4]);
        }
        if (count == hits.length) return hits;
        Hit[] trimmed = new Hit[count];
        System.arraycopy(hits, 0, trimmed, 0, count);
        return trimmed;
    }

    public static void install(String projectId, String title) {
        installJS(projectId == null ? "" : projectId, title == null ? "" : title);
    }

    public static boolean installBusy() { return flagJS("installBusy"); }
    public static String installStatus() { return textJS("installStatus"); }
    /** Download percent 0-100, or -1 when no download is active. */
    public static int installProgress() { return numberJS("progress"); }
    public static boolean packReady() { return packReadyJS(); }
    public static String packName() { return textJS("packName"); }

    /** Moves the downloaded ZIP out of the bridge. Returns null when nothing is ready. */
    public static byte[] takePack() {
        if (!packReadyJS()) return null;
        ArrayBuffer buffer = packBufferJS();
        if (buffer == null) return null;
        byte[] data = new Int8Array(buffer).copyToJavaArray();
        clearPackJS();
        return data;
    }

    public static String formatCount(int value) {
        if (value >= 1_000_000) return (value / 100_000) / 10.0 + "M";
        if (value >= 1_000) return (value / 100) / 10.0 + "k";
        return Integer.toString(value);
    }

    @JSBody(script = """
        (function () {
          if (window.VBVModrinthBridge) return;
          var API = "https://api.modrinth.com/v2";
          var MAX_BYTES = 250 * 1024 * 1024 - 4096;
          var T = String.fromCharCode(9), N = String.fromCharCode(10);
          var state = { status: "Search Modrinth for resource packs", busy: false, total: 0, offset: 0,
            rows: "", seq: 0, installBusy: false, installStatus: "", progress: -1,
            packName: "", packBuffer: null };
          function clean(value) { return String(value == null ? "" : value).replace(/\\s+/g, " ").trim(); }
          function describe(error) {
            var message = String(error && error.message || error);
            return message === "Failed to fetch"
              ? "Failed to fetch (blocked by the network, or offline)" : message;
          }
          async function getJson(url) {
            var response = await fetch(url, { headers: { "Accept": "application/json" } });
            if (!response.ok) throw new Error("Modrinth HTTP " + response.status);
            return response.json();
          }
          async function sha512Hex(buffer) {
            var digest = await crypto.subtle.digest("SHA-512", buffer);
            var bytes = new Uint8Array(digest), out = "";
            for (var i = 0; i < bytes.length; i++) out += (bytes[i] < 16 ? "0" : "") + bytes[i].toString(16);
            return out;
          }
          async function search(query, offset, sort, limit) {
            var seq = ++state.seq;
            state.busy = true; state.status = "Searching Modrinth...";
            try {
              var facets = encodeURIComponent('[["project_type:resourcepack"]]');
              var url = API + "/search?query=" + encodeURIComponent(query || "")
                + "&facets=" + facets + "&index=" + encodeURIComponent(sort || "relevance")
                + "&limit=" + (limit | 0) + "&offset=" + (offset | 0);
              var data = await getJson(url);
              if (seq !== state.seq) return;
              var hits = data.hits || [];
              state.rows = hits.map(function (h) {
                return [clean(h.project_id), clean(h.title), clean(h.author),
                  String(h.downloads | 0), clean(h.description)].join(T);
              }).join(N);
              state.total = data.total_hits | 0;
              state.offset = data.offset | 0;
              state.status = hits.length ? "" : "No resource packs found";
            } catch (e) {
              if (seq === state.seq) { state.rows = ""; state.status = "Modrinth: " + describe(e); }
            } finally {
              if (seq === state.seq) state.busy = false;
            }
          }
          async function install(id, title) {
            if (state.installBusy || !id) return;
            state.installBusy = true; state.progress = 0; state.packBuffer = null; state.packName = "";
            state.installStatus = "Finding the latest version of " + (title || "pack") + "...";
            try {
              var versions = await getJson(API + "/project/" + encodeURIComponent(id) + "/version");
              if (!Array.isArray(versions) || !versions.length) throw new Error("this pack has no published versions");
              versions.sort(function (a, b) { return new Date(b.date_published) - new Date(a.date_published); });
              var pick = null, i;
              for (i = 0; i < versions.length && !pick; i++) {
                var gv = versions[i].game_versions || [];
                if (versions[i].version_type === "release" && gv.some(function (g) { return /^26[.]/.test(g); })) pick = versions[i];
              }
              for (i = 0; i < versions.length && !pick; i++) if (versions[i].version_type === "release") pick = versions[i];
              if (!pick) pick = versions[0];
              var files = (pick.files || []).filter(function (f) { return /[.]zip$/i.test(f.filename || ""); });
              var file = files.filter(function (f) { return f.primary; })[0] || files[0];
              if (!file) throw new Error("the latest version has no .zip file");
              if ((file.size | 0) > MAX_BYTES) throw new Error("the pack is larger than 250 MiB");
              state.installStatus = "Downloading " + file.filename + "...";
              var response = await fetch(file.url);
              if (!response.ok) throw new Error("download failed (HTTP " + response.status + ")");
              var total = Number(response.headers.get("content-length")) || (file.size | 0) || 0;
              var buffer;
              if (response.body && response.body.getReader) {
                var reader = response.body.getReader(), chunks = [], got = 0;
                for (;;) {
                  var part = await reader.read();
                  if (part.done) break;
                  chunks.push(part.value); got += part.value.length;
                  if (got > MAX_BYTES) throw new Error("the pack is larger than 250 MiB");
                  if (total) state.progress = Math.min(99, Math.floor(got * 100 / total));
                }
                var merged = new Uint8Array(got), pos = 0;
                for (i = 0; i < chunks.length; i++) { merged.set(chunks[i], pos); pos += chunks[i].length; }
                buffer = merged.buffer;
              } else {
                buffer = await response.arrayBuffer();
              }
              var head = new Uint8Array(buffer, 0, Math.min(4, buffer.byteLength));
              if (head.length < 4 || head[0] !== 0x50 || head[1] !== 0x4B) throw new Error("the download is not a ZIP file");
              var expected = file.hashes && file.hashes.sha512;
              if (expected && window.crypto && crypto.subtle) {
                state.installStatus = "Verifying download...";
                if ((await sha512Hex(buffer)) !== String(expected).toLowerCase()) throw new Error("checksum mismatch, download discarded");
              }
              state.packName = String(file.filename).replace(/[^A-Za-z0-9._ -]/g, "_");
              state.packBuffer = buffer;
              state.progress = 100;
              state.installStatus = "Downloaded " + file.filename;
            } catch (e) {
              state.progress = -1; state.packBuffer = null; state.packName = "";
              state.installStatus = "Install failed: " + describe(e);
            } finally {
              state.installBusy = false;
            }
          }
          window.VBVModrinthBridge = { state: state, search: search, install: install };
        })();
        """)
    private static native void initializeJS();

    @JSBody(params = {"query", "offset", "sort", "limit"}, script = """
        try { if (window.VBVModrinthBridge) window.VBVModrinthBridge.search(query, offset, sort, limit); } catch (e) { console.error(e); }
        """)
    private static native void searchJS(String query, int offset, String sort, int limit);

    @JSBody(params = {"id", "title"}, script = """
        try { if (window.VBVModrinthBridge) window.VBVModrinthBridge.install(id, title); } catch (e) { console.error(e); }
        """)
    private static native void installJS(String id, String title);

    @JSBody(params = {"key"}, script = """
        try { return window.VBVModrinthBridge ? !!window.VBVModrinthBridge.state[key] : false; } catch (e) { return false; }
        """)
    private static native boolean flagJS(String key);

    @JSBody(params = {"key"}, script = """
        try { return window.VBVModrinthBridge ? String(window.VBVModrinthBridge.state[key] || "") : ""; } catch (e) { return ""; }
        """)
    private static native String textJS(String key);

    @JSBody(params = {"key"}, script = """
        try { return window.VBVModrinthBridge ? (Number(window.VBVModrinthBridge.state[key]) | 0) : 0; } catch (e) { return 0; }
        """)
    private static native int numberJS(String key);

    @JSBody(script = """
        try { return window.VBVModrinthBridge ? String(window.VBVModrinthBridge.state.rows || "") : ""; } catch (e) { return ""; }
        """)
    private static native String rowsJS();

    @JSBody(script = """
        try { return !!(window.VBVModrinthBridge && window.VBVModrinthBridge.state.packBuffer); } catch (e) { return false; }
        """)
    private static native boolean packReadyJS();

    @JSBody(script = """
        try { return window.VBVModrinthBridge ? window.VBVModrinthBridge.state.packBuffer : null; } catch (e) { return null; }
        """)
    private static native ArrayBuffer packBufferJS();

    @JSBody(script = """
        try { if (window.VBVModrinthBridge) { window.VBVModrinthBridge.state.packBuffer = null; window.VBVModrinthBridge.state.progress = -1; } } catch (e) { }
        """)
    private static native void clearPackJS();
}
