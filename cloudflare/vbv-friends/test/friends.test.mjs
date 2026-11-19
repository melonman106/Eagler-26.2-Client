import test from "node:test";
import assert from "node:assert/strict";
import { createDb } from "./d1-shim.mjs";
import { handle, ONLINE_MS, MAX_PENDING } from "../src/index.js";

function world() {
  const env = { DB: createDb() };
  let now = 1_700_000_000_000;
  async function call(path, body, { ip = "1.1.1.1", method = "POST", raw } = {}) {
    const request = new Request("https://friends.test" + path, {
      method,
      headers: { "content-type": "application/json", "cf-connecting-ip": ip },
      body: method === "POST" ? (raw !== undefined ? raw : JSON.stringify(body || {})) : undefined,
    });
    const response = await handle(request, env, now);
    return { status: response.status, body: await response.json(), headers: response.headers };
  }
  async function register(name, ip) {
    const r = await call("/friends/register", { name }, { ip });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    return { id: r.body.id, token: r.body.token, name };
  }
  return { env, call, register, advance: (ms) => (now += ms), now: () => now };
}

test("register stores only a hash and rejects bad or taken names", async () => {
  const w = world();
  const alex = await w.register("Alex_01");
  assert.match(alex.id, /^[0-9a-f]{32}$/);
  assert.match(alex.token, /^[0-9a-f]{64}$/);
  const row = w.env.DB.sqlite.prepare("SELECT * FROM users").get();
  assert.notEqual(row.token_hash, alex.token);
  assert.equal(row.token_hash.length, 64);
  assert.equal((await w.call("/friends/register", { name: "alex_01" })).status, 409, "case-insensitive");
  assert.equal((await w.call("/friends/register", { name: "ab" })).status, 400);
  assert.equal((await w.call("/friends/register", { name: "has space" })).status, 400);
  assert.equal((await w.call("/friends/register", { name: "x".repeat(17) })).status, 400);
  assert.equal((await w.call("/friends/register", { name: 42 })).status, 400);
});

test("authentication needs the right id and token", async () => {
  const w = world();
  const alex = await w.register("Alex");
  assert.equal((await w.call("/friends/me", alex)).body.name, "Alex");
  assert.equal((await w.call("/friends/me", { ...alex, token: "0".repeat(64) })).status, 401);
  assert.equal((await w.call("/friends/me", { ...alex, id: "f".repeat(32) })).status, 401);
  assert.equal((await w.call("/friends/me", { id: alex.id })).status, 401);
  assert.equal((await w.call("/friends/me", {})).status, 401);
});

test("login restores a session only with the matching key", async () => {
  const w = world();
  const alex = await w.register("Alex");
  const ok = await w.call("/friends/login", { name: "alex", token: alex.token });
  assert.equal(ok.status, 200);
  assert.equal(ok.body.id, alex.id);
  assert.equal(ok.body.name, "Alex");
  assert.equal((await w.call("/friends/login", { name: "Alex", token: "1".repeat(64) })).status, 401);
  assert.equal((await w.call("/friends/login", { name: "Nobody", token: alex.token })).status, 401);
});

test("request, accept and the friends list with presence", async () => {
  const w = world();
  const a = await w.register("Alice");
  const b = await w.register("Bobby");
  const sent = await w.call("/friends/request", { ...a, name: "bobby" });
  assert.equal(sent.body.state, "sent");

  let la = (await w.call("/friends/list", a)).body;
  assert.deepEqual(la.outgoing.map((x) => x.name), ["Bobby"]);
  assert.equal(la.friends.length, 0);
  let lb = (await w.call("/friends/list", b)).body;
  assert.deepEqual(lb.incoming.map((x) => x.name), ["Alice"]);

  // The sender cannot accept their own request; the receiver can.
  assert.equal((await w.call("/friends/accept", { ...a, friend: b.id })).status, 404);
  assert.equal((await w.call("/friends/accept", { ...b, friend: a.id })).status, 200);

  await w.call("/friends/presence", { ...b, server: "play.example.net" });
  la = (await w.call("/friends/list", a)).body;
  assert.equal(la.friends.length, 1);
  assert.equal(la.friends[0].name, "Bobby");
  assert.equal(la.friends[0].online, true);
  assert.equal(la.friends[0].server, "play.example.net");
  assert.equal(la.outgoing.length, 0);

  // Presence expires, and the server is no longer shown once offline.
  w.advance(ONLINE_MS + 1);
  la = (await w.call("/friends/list", a)).body;
  assert.equal(la.friends[0].online, false);
  assert.equal(la.friends[0].server, "");
});

test("asking someone who already asked you accepts at once", async () => {
  const w = world();
  const a = await w.register("Alice");
  const b = await w.register("Bobby");
  await w.call("/friends/request", { ...a, name: "Bobby" });
  const back = await w.call("/friends/request", { ...b, name: "Alice" });
  assert.equal(back.body.state, "accepted");
  assert.equal((await w.call("/friends/list", a)).body.friends.length, 1);
  assert.equal((await w.call("/friends/request", { ...b, name: "Alice" })).status, 409, "already friends");
});

test("duplicate, self and unknown requests are refused", async () => {
  const w = world();
  const a = await w.register("Alice");
  await w.register("Bobby");
  assert.equal((await w.call("/friends/request", { ...a, name: "Alice" })).status, 400);
  assert.equal((await w.call("/friends/request", { ...a, name: "Nobody" })).status, 404);
  assert.equal((await w.call("/friends/request", { ...a, name: "Bobby" })).status, 200);
  assert.equal((await w.call("/friends/request", { ...a, name: "Bobby" })).status, 409);
  assert.equal(w.env.DB.sqlite.prepare("SELECT COUNT(*) AS n FROM friendships").get().n, 1);
});

test("decline, cancel and remove", async () => {
  const w = world();
  const a = await w.register("Alice");
  const b = await w.register("Bobby");
  const c = await w.register("Carol");
  await w.call("/friends/request", { ...a, name: "Bobby" });
  await w.call("/friends/decline", { ...b, friend: a.id });
  assert.equal((await w.call("/friends/list", b)).body.incoming.length, 0);

  await w.call("/friends/request", { ...a, name: "Carol" });
  await w.call("/friends/decline", { ...a, friend: c.id }); // cancelling your own request
  assert.equal((await w.call("/friends/list", c)).body.incoming.length, 0);

  await w.call("/friends/request", { ...a, name: "Bobby" });
  await w.call("/friends/accept", { ...b, friend: a.id });
  // decline must not delete an accepted friendship; remove must.
  await w.call("/friends/decline", { ...a, friend: b.id });
  assert.equal((await w.call("/friends/list", a)).body.friends.length, 1);
  await w.call("/friends/remove", { ...a, friend: b.id });
  assert.equal((await w.call("/friends/list", a)).body.friends.length, 0);
  assert.equal((await w.call("/friends/list", b)).body.friends.length, 0);
});

test("hidden players look offline and keep their server private", async () => {
  const w = world();
  const a = await w.register("Alice");
  const b = await w.register("Bobby");
  await w.call("/friends/request", { ...a, name: "Bobby" });
  await w.call("/friends/accept", { ...b, friend: a.id });
  await w.call("/friends/presence", { ...b, server: "secret.example", hidden: true });
  const entry = (await w.call("/friends/list", a)).body.friends[0];
  assert.equal(entry.online, false);
  assert.equal(entry.server, "");
  // A heartbeat without the flag keeps it hidden.
  await w.call("/friends/presence", { ...b, server: "secret.example" });
  assert.equal((await w.call("/friends/list", a)).body.friends[0].online, false);
  await w.call("/friends/presence", { ...b, server: "x", hidden: false });
  assert.equal((await w.call("/friends/list", a)).body.friends[0].online, true);
});

test("presence server text is cleaned and capped", async () => {
  const w = world();
  const a = await w.register("Alice");
  await w.call("/friends/presence", { ...a, server: "  evil\n\u0007host" + "y".repeat(200) + "  " });
  const stored = w.env.DB.sqlite.prepare("SELECT server FROM users").get().server;
  assert.ok(stored.length <= 64);
  assert.ok(!/[\u0000-\u001f]/.test(stored));
});

test("too many pending requests are refused", async () => {
  const w = world();
  const target = await w.register("Target");
  for (let i = 0; i < MAX_PENDING; i++) {
    const user = await w.register("User" + String(i).padStart(3, "0"), "9.9.9." + (i % 250));
    const r = await w.call("/friends/request", { ...user, name: "Target" });
    assert.equal(r.status, 200, "request " + i);
  }
  const last = await w.register("Latecomer", "8.8.8.8");
  assert.equal((await w.call("/friends/request", { ...last, name: "Target" })).status, 409);
  assert.equal((await w.call("/friends/list", target)).body.incoming.length, MAX_PENDING);
});

test("registration and requests are rate limited", async () => {
  const w = world();
  for (let i = 0; i < 10; i++) await w.register("Player" + String(i).padStart(2, "0"), "5.5.5.5");
  assert.equal((await w.call("/friends/register", { name: "OneTooMany" }, { ip: "5.5.5.5" })).status, 429);
  // Another address is unaffected, and the window reopens an hour later.
  assert.equal((await w.call("/friends/register", { name: "Elsewhere" }, { ip: "6.6.6.6" })).status, 200);
  w.advance(3_600_000);
  assert.equal((await w.call("/friends/register", { name: "NextHour" }, { ip: "5.5.5.5" })).status, 200);
});

test("deleting an account removes its friendships", async () => {
  const w = world();
  const a = await w.register("Alice");
  const b = await w.register("Bobby");
  await w.call("/friends/request", { ...a, name: "Bobby" });
  await w.call("/friends/accept", { ...b, friend: a.id });
  assert.equal((await w.call("/friends/delete", a)).status, 200);
  assert.equal((await w.call("/friends/me", a)).status, 401);
  assert.equal((await w.call("/friends/list", b)).body.friends.length, 0);
  assert.equal(w.env.DB.sqlite.prepare("SELECT COUNT(*) AS n FROM friendships").get().n, 0);
  // The name is free again.
  assert.equal((await w.call("/friends/register", { name: "Alice" })).status, 200);
});

test("protocol: CORS, health, methods, bad bodies, unknown paths", async () => {
  const w = world();
  const pre = await handle(new Request("https://friends.test/friends/list", { method: "OPTIONS" }), w.env);
  assert.equal(pre.status, 204);
  assert.equal(pre.headers.get("access-control-allow-origin"), "*");
  const health = await w.call("/health", null, { method: "GET" });
  assert.equal(health.status, 200);
  assert.equal(health.headers.get("access-control-allow-origin"), "*");
  assert.equal((await w.call("/friends/list", null, { method: "GET" })).status, 405);
  assert.equal((await w.call("/friends/register", null, { raw: "{not json" })).status, 400);
  assert.equal((await w.call("/friends/register", null, { raw: "[]" })).status, 400);
  assert.equal((await w.call("/friends/register", null, { raw: "x".repeat(5000) })).status, 413);
  assert.equal((await w.call("/friends/nothing", {})).status, 404);
  assert.equal((await w.call("/friends/constructor", {})).status, 404, "no prototype lookups");
  assert.equal((await w.call("/other", {})).status, 404);
});
