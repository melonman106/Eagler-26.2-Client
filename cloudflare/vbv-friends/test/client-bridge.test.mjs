// Runs the browser bridge that is embedded in VBVFriends.java (the exact text, extracted from the source)
// against the real Worker + schema, with two simulated browsers.
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createDb } from "./d1-shim.mjs";
import { handle } from "../src/index.js";

const JAVA = new URL("../../../VBV/src/main/java/com/melonman106/vbvclient/VBVFriends.java", import.meta.url);

function bridgeSource() {
  const text = readFileSync(JAVA, "utf8");
  const match = /@JSBody\(params = \{"api"\}, script = """\n([\s\S]*?)\n\s*"""\)/.exec(text);
  assert.ok(match, "initializeJS text block not found in VBVFriends.java");
  return match[1].replace(/\\\\/g, "\\"); // Java text block: \\ -> \
}

const SOURCE = bridgeSource();
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** One simulated browser tab: its own localStorage, window and timers, sharing the same "internet". */
function browser(env, { api = "https://friends.test", fetchImpl, seed = {} } = {}) {
  const storage = new Map(Object.entries(seed));
  const timers = [];
  const clipboard = { text: null, fail: false };
  const window = {
    setInterval: (fn) => timers.push(fn),
  };
  const localStorage = {
    getItem: (k) => (storage.has(k) ? storage.get(k) : null),
    setItem: (k, v) => storage.set(k, String(v)),
    removeItem: (k) => storage.delete(k),
  };
  const fetchRoute =
    fetchImpl ||
    (async (url, init = {}) => {
      assert.ok(url.startsWith(api), "calls go to the configured service: " + url);
      const request = new Request(url, { method: init.method || "GET", headers: init.headers, body: init.body });
      return handle(request, env);
    });
  const navigator = {
    clipboard: {
      writeText: async (t) => {
        if (clipboard.fail) throw new Error("denied");
        clipboard.text = t;
      },
    },
  };
  new Function("window", "localStorage", "fetch", "navigator", "api", SOURCE)(window, localStorage, fetchRoute, navigator, api);
  const bridge = window.VBVFriendsBridge;
  assert.ok(bridge && bridge.initialized);
  return {
    bridge, storage, clipboard,
    tickTimers: async () => { for (const fn of timers) fn(); await wait(30); },
    /** Calls an action like Java does and waits until the bridge is idle again. */
    async act(name, a, b) {
      bridge.call(name, a, b);
      for (let i = 0; i < 200 && bridge.get("busy") === "1"; i++) await wait(5);
      await wait(5);
    },
    rows: (kind) => (bridge.rows(kind) ? bridge.rows(kind).split("\n").map((r) => r.split("\t")) : []),
  };
}

test("bridge: create account, add a friend, accept, see presence and a hidden toggle", async () => {
  const env = { DB: createDb() };
  const alice = browser(env);
  const bobby = browser(env);

  assert.equal(alice.bridge.get("signedIn"), "0");
  assert.match(alice.bridge.get("status"), /Create a Friends account/);

  await alice.act("register", "Alice");
  assert.equal(alice.bridge.get("signedIn"), "1");
  assert.equal(alice.bridge.get("name"), "Alice");
  assert.match(alice.bridge.get("status"), /Account created/);
  const saved = JSON.parse(alice.storage.get("vbv.friends.session.v1"));
  assert.equal(saved.name, "Alice");
  assert.match(saved.token, /^[0-9a-f]{64}$/);

  await bobby.act("register", "Bobby");
  await alice.act("request", "bobby");
  assert.match(alice.bridge.get("status"), /Request sent to Bobby/);
  assert.deepEqual(alice.rows("outgoing").map((r) => r[1]), ["Bobby"]);

  await bobby.act("refresh");
  assert.deepEqual(bobby.rows("incoming").map((r) => r[1]), ["Alice"]);
  await bobby.act("accept", bobby.rows("incoming")[0][0]);
  assert.match(bobby.bridge.get("status"), /Friend added/);
  assert.equal(bobby.rows("incoming").length, 0);

  // Bobby starts playing on a server; the next heartbeat tells Alice's list.
  bobby.bridge.setServer("play.example.net");
  await bobby.tickTimers();
  alice.bridge.watch(true);
  await alice.tickTimers();
  const friend = alice.rows("friends")[0];
  assert.equal(friend[1], "Bobby");
  assert.equal(friend[2], "1", "online");
  assert.equal(friend[3], "play.example.net");

  // Appear offline.
  const before = bobby.bridge.version();
  bobby.bridge.call("setHidden", "1");
  assert.equal(bobby.bridge.get("hidden"), "1");
  assert.ok(bobby.bridge.version() > before);
  await bobby.tickTimers();
  alice.bridge.watch(true);
  await alice.tickTimers();
  assert.equal(alice.rows("friends")[0][2], "0");
  assert.equal(bobby.storage.get("vbv.friends.hidden"), "1");

  await alice.act("remove", alice.rows("friends")[0][0]);
  assert.equal(alice.rows("friends").length, 0);
});

test("bridge: errors are shown in plain words", async () => {
  const env = { DB: createDb() };
  const a = browser(env);
  const b = browser(env);
  await a.act("register", "Alice");
  await b.act("register", "alice");
  assert.equal(b.bridge.get("signedIn"), "0");
  assert.match(b.bridge.get("status"), /already taken/);
  await a.act("request", "Nobody");
  assert.match(a.bridge.get("status"), /No player called Nobody/);
  await a.act("request", "Alice");
  assert.match(a.bridge.get("status"), /cannot add yourself/);
  await b.act("register", "no");
  assert.match(b.bridge.get("status"), /3-16 characters/);
});

test("bridge: restore with the account key on another browser, and wrong keys", async () => {
  const env = { DB: createDb() };
  const home = browser(env);
  await home.act("register", "Alice");
  const key = JSON.parse(home.storage.get("vbv.friends.session.v1")).token;

  const school = browser(env);
  await school.act("login", "Alice", "0".repeat(64));
  assert.equal(school.bridge.get("signedIn"), "0");
  assert.match(school.bridge.get("status"), /do not match/);
  await school.act("login", "alice", key.toUpperCase());
  assert.equal(school.bridge.get("signedIn"), "1");
  assert.equal(school.bridge.get("name"), "Alice");
  assert.match(school.bridge.get("status"), /Signed in as Alice/);
  // The restored session is usable, not just displayed.
  await school.act("refresh");
  assert.equal(school.bridge.get("signedIn"), "1");

  // A reload of the page restores the session from localStorage alone.
  const again = browser(env, { seed: { "vbv.friends.session.v1": school.storage.get("vbv.friends.session.v1") } });
  assert.equal(again.bridge.get("signedIn"), "1");
  assert.equal(again.bridge.get("name"), "Alice");
  await again.act("refresh");
  assert.equal(again.bridge.get("signedIn"), "1", "the stored key still works after a reload");
});

test("bridge: copy key, sign out and delete account", async () => {
  const env = { DB: createDb() };
  const a = browser(env);
  await a.act("register", "Alice");
  const key = JSON.parse(a.storage.get("vbv.friends.session.v1")).token;
  await a.act("copyKey");
  assert.equal(a.clipboard.text, key);
  a.clipboard.fail = true;
  await a.act("copyKey");
  assert.ok(a.bridge.get("status").includes(key), "falls back to showing the key");

  a.bridge.call("signOut");
  assert.equal(a.bridge.get("signedIn"), "0");
  assert.equal(a.storage.has("vbv.friends.session.v1"), false);

  await a.act("login", "Alice", key);
  await a.act("deleteAccount");
  assert.equal(a.bridge.get("signedIn"), "0");
  assert.match(a.bridge.get("status"), /deleted/);
  assert.equal(env.DB.sqlite.prepare("SELECT COUNT(*) AS n FROM users").get().n, 0);
});

test("bridge: a session the server no longer knows is dropped", async () => {
  const env = { DB: createDb() };
  const a = browser(env);
  await a.act("register", "Alice");
  env.DB.sqlite.exec("DELETE FROM users");
  await a.act("refresh");
  assert.equal(a.bridge.get("signedIn"), "0");
  assert.match(a.bridge.get("status"), /no longer valid/);
});

test("bridge: unreachable or wrong service gives a useful message", async () => {
  const env = { DB: createDb() };
  const down = browser(env, { fetchImpl: async () => { throw new TypeError("Failed to fetch"); } });
  await down.act("register", "Alice");
  assert.match(down.bridge.get("status"), /not reachable/);

  const html = browser(env, { fetchImpl: async () => new Response("<html>nope</html>", { status: 404 }) });
  await html.act("register", "Alice");
  assert.match(html.bridge.get("status"), /Nothing answers as the Friends service/);
});

test("bridge: the service address can be changed and is checked", async () => {
  const env = { DB: createDb() };
  const seen = [];
  const fetchImpl = async (url, init = {}) => {
    seen.push(url);
    return handle(new Request(url, { method: init.method || "GET", headers: init.headers, body: init.body }), env);
  };
  const b = browser(env, { api: "https://default.test", fetchImpl });
  assert.equal(b.bridge.get("api"), "https://default.test");
  await b.act("setApi", "ftp://nope");
  assert.match(b.bridge.get("status"), /must start with https/);
  await b.act("setApi", "https://mine.test/");
  assert.equal(b.bridge.get("api"), "https://mine.test");
  assert.match(b.bridge.get("status"), /online/);
  assert.equal(b.storage.get("vbv.friends.api"), "https://mine.test");
  assert.ok(seen.includes("https://mine.test/health"));
  await b.act("register", "Alice");
  assert.ok(seen.some((u) => u === "https://mine.test/friends/register"));
});
