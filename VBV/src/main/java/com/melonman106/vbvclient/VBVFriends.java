package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import org.teavm.jso.JSBody;

/**
 * Friends list client. The list lives in the VBV Friends Worker + D1 database (cloudflare/vbv-friends);
 * this class is the Java face of a small browser-side bridge that does the HTTP calls, keeps the account
 * key in {@code localStorage} and refreshes the list in the background.
 *
 * <p>An account is anonymous: the browser creates a random id and key when the player picks a name, and
 * only a hash of the key is stored on the server. The key is shown (copyable) in the Friends screen so the
 * account can be restored in another browser.</p>
 */
public final class VBVFriends {
    /**
     * Where a Worker named {@code vbv-friends} is served on this account's workers.dev subdomain (the same
     * subdomain as the party relay). Deploying cloudflare/vbv-friends with workflow 06 produces exactly this
     * address; a different address can be saved from the Friends screen.
     */
    public static final String DEFAULT_API = "https://vbv-friends.u2471966200.workers.dev";

    private static String lastServerLabel = null;

    private VBVFriends() {}

    public static void initialize() { initializeJS(DEFAULT_API); }

    /** Called every client tick: tells the bridge what the player is doing so friends can see it. */
    public static void tick(Minecraft mc) {
        String label = "";
        if (mc != null && mc.level != null) {
            if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null) {
                label = mc.getCurrentServer().ip;
            } else {
                label = mc.isLocalServer() ? "Singleplayer" : "In game";
            }
        }
        if (!label.equals(lastServerLabel)) {
            lastServerLabel = label;
            setServerJS(label);
        }
    }

    /** True while the Friends screen is open: the list is then refreshed every few seconds. */
    public static void watch(boolean open) { watchJS(open); }

    public static void register(String name) { callJS("register", name, ""); }
    public static void login(String name, String key) { callJS("login", name, key); }
    public static void refresh() { callJS("refresh", "", ""); }
    public static void request(String name) { callJS("request", name, ""); }
    public static void accept(String id) { callJS("accept", id, ""); }
    public static void decline(String id) { callJS("decline", id, ""); }
    public static void remove(String id) { callJS("remove", id, ""); }
    public static void signOut() { callJS("signOut", "", ""); }
    public static void deleteAccount() { callJS("deleteAccount", "", ""); }
    public static void setApi(String url) { callJS("setApi", url, ""); }
    public static void setHidden(boolean hidden) { callJS("setHidden", hidden ? "1" : "0", ""); }
    public static void copyKey() { callJS("copyKey", "", ""); }

    public static boolean signedIn() { return "1".equals(getJS("signedIn")); }
    public static boolean busy() { return "1".equals(getJS("busy")); }
    public static boolean hidden() { return "1".equals(getJS("hidden")); }
    public static String name() { return getJS("name"); }
    public static String status() { return getJS("status"); }
    public static String api() { return getJS("api"); }
    public static int version() { return versionJS(); }

    /** Friends, one per row: {@code id TAB name TAB online(1/0) TAB server TAB voiceCount TAB voiceRoom}. */
    public static String[] friendRows() { return rows("friends"); }
    /** Requests other players sent to you: {@code id TAB name}. */
    public static String[] incomingRows() { return rows("incoming"); }
    /** Requests you sent that are still waiting: {@code id TAB name}. */
    public static String[] outgoingRows() { return rows("outgoing"); }

    public static int onlineCount() {
        int n = 0;
        for (String row : friendRows()) {
            String[] f = row.split("\\t", -1);
            if (f.length > 2 && "1".equals(f[2])) n++;
        }
        return n;
    }

    private static String[] rows(String kind) {
        String raw = rowsJS(kind);
        return raw == null || raw.isEmpty() ? new String[0] : raw.split("\\n");
    }

    @JSBody(params = {"api"}, script = """
        (function () {
          if (window.VBVFriendsBridge && window.VBVFriendsBridge.initialized) return;
          const SESSION_KEY = "vbv.friends.session.v1";
          const API_KEY = "vbv.friends.api";
          const HIDDEN_KEY = "vbv.friends.hidden";
          const state = {
            api: "", session: null, status: "", busy: false, hidden: false, server: "",
            friends: [], incoming: [], outgoing: [], watching: false, version: 0,
            lastList: 0, lastBeat: 0, beatSoon: false, listing: false
          };
          function store(key, value) {
            try { if (value === null) localStorage.removeItem(key); else localStorage.setItem(key, value); } catch (_) {}
          }
          function recall(key) { try { return localStorage.getItem(key) || ""; } catch (_) { return ""; } }
          function trimApi(url) { return String(url || "").trim().replace(/\\/+$/, ""); }
          state.api = trimApi(recall(API_KEY) || window.VBV_FRIENDS_API || api);
          state.hidden = recall(HIDDEN_KEY) === "1";
          try {
            const saved = JSON.parse(recall(SESSION_KEY) || "null");
            if (saved && saved.id && saved.token && saved.name) state.session = saved;
          } catch (_) {}
          state.status = state.session ? "" : "Create a Friends account to add friends.";

          function bump() { state.version++; }
          function say(text) { if (state.status !== text) { state.status = text; bump(); } }

          async function post(path, body) {
            if (!state.api) throw new Error("No Friends service address is set");
            let response;
            try {
              response = await fetch(state.api + "/" + (path.indexOf("/") >= 0 ? path : "friends/" + path), {
                method: "POST", headers: { "content-type": "application/json" },
                cache: "no-store", body: JSON.stringify(body || {})
              });
            } catch (_) {
              throw new Error("Friends service is not reachable. Deploy it (workflow 06) or check the service address.");
            }
            let result;
            try { result = await response.json(); }
            catch (_) { throw new Error("Nothing answers as the Friends service at that address (HTTP " + response.status + ")"); }
            if (!result || !result.ok) {
              const error = new Error(result && result.error ? result.error : ("Friends service HTTP " + response.status));
              error.status = response.status;
              throw error;
            }
            return result;
          }
          function dropSession(message) {
            state.session = null; state.friends = []; state.incoming = []; state.outgoing = [];
            store(SESSION_KEY, null);
            state.status = message; bump();
            try { if (window.VBVVoiceBridge) window.VBVVoiceBridge.reset(); } catch (_) {}
          }
          async function authed(path, extra) {
            if (!state.session) throw new Error("Create a Friends account first");
            try {
              return await post(path, Object.assign({ id: state.session.id, token: state.session.token }, extra || {}));
            } catch (error) {
              if (error && error.status === 401) {
                dropSession("Your Friends session is no longer valid. Create or restore an account.");
                throw new Error("session invalid");
              }
              throw error;
            }
          }
          function guard(fn) {
            return async function () {
              if (state.busy) return;
              state.busy = true; bump();
              try { await fn.apply(null, arguments); }
              catch (error) { if (!(error && error.message === "session invalid")) say(String(error && error.message || error)); }
              finally { state.busy = false; bump(); }
            };
          }
          function same(a, b) { return JSON.stringify(a) === JSON.stringify(b); }

          async function list() {
            if (!state.session || state.listing) return;
            state.listing = true;
            try {
              const result = await authed("list");
              state.lastList = Date.now();
              if (!same(result.friends, state.friends) || !same(result.incoming, state.incoming) ||
                  !same(result.outgoing, state.outgoing)) {
                state.friends = result.friends || []; state.incoming = result.incoming || [];
                state.outgoing = result.outgoing || []; bump();
              }
            } finally { state.listing = false; }
          }
          async function beat() {
            if (!state.session) return;
            state.lastBeat = Date.now(); state.beatSoon = false;
            await authed("presence", { server: state.server, hidden: state.hidden });
          }
          async function begin(result, name) {
            state.session = { id: result.id, token: result.token || state.pendingToken, name: result.name || name };
            store(SESSION_KEY, JSON.stringify(state.session));
            await Promise.all([beat(), list()]);
          }

          const register = guard(async function (name) {
            const result = await post("register", { name: String(name || "").trim() });
            await begin(result, result.name);
            state.status = "Account created. Add friends by their name."; bump();
          });
          const login = guard(async function (name, key) {
            const k = String(key || "").trim().toLowerCase();
            const result = await post("login", { name: String(name || "").trim(), token: k });
            state.pendingToken = k;
            await begin(result, result.name);
            state.pendingToken = "";
            state.status = "Signed in as " + state.session.name + "."; bump();
          });
          const refresh = guard(async function () { await Promise.all([beat(), list()]); say(""); });
          const request = guard(async function (name) {
            const result = await authed("request", { name: String(name || "").trim() });
            await list();
            say(result.state === "accepted" ? "You are now friends with " + result.name + "."
                                            : "Request sent to " + result.name + ".");
          });
          const accept = guard(async function (id) { await authed("accept", { friend: id }); await list(); say("Friend added."); });
          const decline = guard(async function (id) { await authed("decline", { friend: id }); await list(); say(""); });
          const remove = guard(async function (id) { await authed("remove", { friend: id }); await list(); say("Friend removed."); });
          const deleteAccount = guard(async function () {
            await authed("delete");
            dropSession("Your Friends account was deleted.");
          });
          function signOut() {
            if (!state.session) return;
            dropSession("Signed out. Your account key still works if you saved it.");
          }
          const setApi = guard(async function (url) {
            const next = trimApi(url);
            if (!/^https?:\\/\\//i.test(next)) throw new Error("The service address must start with https://");
            state.api = next; store(API_KEY, next === trimApi(api) ? null : next);
            let response;
            try { response = await fetch(next + "/health", { cache: "no-store" }); }
            catch (_) { throw new Error("Saved, but nothing is reachable at " + next); }
            const info = await response.json().catch(() => null);
            say(info && info.ok ? "Service address saved. Friends service is online." : "Saved, but that address is not a Friends service.");
          });
          function setHidden(flag) {
            state.hidden = flag === "1"; store(HIDDEN_KEY, state.hidden ? "1" : "0");
            bump(); state.beatSoon = true;
          }
          async function copyKey() {
            if (!state.session) return;
            try { await navigator.clipboard.writeText(state.session.token); say("Account key copied. Keep it somewhere safe."); }
            catch (_) { say("Could not copy. Your account key: " + state.session.token); }
          }
          function setServer(text) {
            const next = String(text || "");
            if (next !== state.server) { state.server = next; state.beatSoon = true; }
          }

          const actions = { register, login, refresh, request, accept, decline, remove, deleteAccount,
                            signOut, setApi, setHidden, copyKey };
          const bridge = {
            initialized: true,
            call: function (name, a, b) { const fn = actions[name]; if (fn) fn(a, b); },
            watch: function (open) { state.watching = !!open; if (open) state.lastList = 0; },
            authed: authed,
            session: function () { return state.session; },
            setServer: setServer,
            version: function () { return state.version; },
            get: function (key) {
              switch (key) {
                case "signedIn": return state.session ? "1" : "0";
                case "busy": return state.busy ? "1" : "0";
                case "hidden": return state.hidden ? "1" : "0";
                case "name": return state.session ? state.session.name : "";
                case "id": return state.session ? state.session.id : "";
                case "status": return state.status;
                case "api": return state.api;
                default: return "";
              }
            },
            rows: function (kind) {
              const clean = s => String(s == null ? "" : s).replace(/[\\t\\r\\n]/g, " ");
              if (kind === "friends") {
                return state.friends.map(f => f.id + "\\t" + clean(f.name) + "\\t" + (f.online ? "1" : "0") +
                  "\\t" + clean(f.server) + "\\t" + (f.voice | 0) + "\\t" + clean(f.voiceRoom)).join("\\n");
              }
              const rows = kind === "incoming" ? state.incoming : state.outgoing;
              return rows.map(r => r.id + "\\t" + clean(r.name)).join("\\n");
            }
          };
          window.VBVFriendsBridge = bridge;
          window.setInterval(function () {
            if (!state.session || state.busy) return;
            const now = Date.now();
            Promise.resolve().then(function () {
              if (state.beatSoon || now - state.lastBeat > 30000) return beat();
            }).then(function () {
              if (now - state.lastList > (state.watching ? 4500 : 60000)) return list();
            }).catch(function () {});
          }, 4000);
          if (state.session) { list().catch(function () {}); beat().catch(function () {}); }
        })();
        """)
    private static native void initializeJS(String api);

    @JSBody(params = {"name", "a", "b"}, script =
        "try { window.VBVFriendsBridge && window.VBVFriendsBridge.call(String(name), String(a || ''), String(b || '')); } catch(e) {}")
    private static native void callJS(String name, String a, String b);

    @JSBody(params = {"open"}, script = "try { window.VBVFriendsBridge && window.VBVFriendsBridge.watch(!!open); } catch(e) {}")
    private static native void watchJS(boolean open);

    @JSBody(params = {"text"}, script = "try { window.VBVFriendsBridge && window.VBVFriendsBridge.setServer(String(text || '')); } catch(e) {}")
    private static native void setServerJS(String text);

    @JSBody(params = {"key"}, script = "try { return window.VBVFriendsBridge ? String(window.VBVFriendsBridge.get(key) || '') : ''; } catch(e) { return ''; }")
    private static native String getJS(String key);

    @JSBody(params = {"kind"}, script = "try { return window.VBVFriendsBridge ? String(window.VBVFriendsBridge.rows(kind) || '') : ''; } catch(e) { return ''; }")
    private static native String rowsJS(String kind);

    @JSBody(script = "try { return window.VBVFriendsBridge ? (window.VBVFriendsBridge.version() | 0) : 0; } catch(e) { return 0; }")
    private static native int versionJS();
}
