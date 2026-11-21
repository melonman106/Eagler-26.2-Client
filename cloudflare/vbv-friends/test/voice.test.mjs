import test from "node:test";
import assert from "node:assert/strict";
import { createDb } from "./d1-shim.mjs";
import { handle, MAX_VOICE, VOICE_STALE_MS } from "../src/index.js";

const APP_ID = "app123";
const APP_SECRET = "s3cret-app-token";
const TURN_ID = "turnkey1";
const TURN_TOKEN = "turn-api-token";
const OFFER = "v=0\r\no=- 1 2 IN IP4 127.0.0.1\r\ns=-\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n";

/**
 * A stand-in for the parts of the Cloudflare Realtime SFU HTTPS API the Worker uses. It keeps just enough state
 * (sessions, pushed tracks, assigned mids) to check who may pull what, and it records every call.
 */
function fakeCloudflare({ appId = APP_ID, secret = APP_SECRET } = {}) {
  const sessions = new Map();
  const calls = [];
  let nextSession = 1;
  let failNext = null;
  let turnMode = "ok";
  const reply = (status, body) => new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

  async function fetchImpl(url, init = {}) {
    const u = new URL(url);
    const body = init.body ? JSON.parse(init.body) : undefined;
    const auth = (init.headers && init.headers.authorization) || "";
    calls.push({ method: init.method || "GET", path: u.pathname, body, auth });
    assert.equal(u.origin, "https://rtc.live.cloudflare.com");

    const turn = /^\/v1\/turn\/keys\/([^/]+)\/credentials\/generate-ice-servers$/.exec(u.pathname);
    if (turn) {
      if (turnMode === "down") throw new TypeError("network down");
      if (turnMode === "bad") return reply(500, { error: "boom" });
      assert.equal(turn[1], TURN_ID);
      assert.equal(auth, "Bearer " + TURN_TOKEN);
      assert.deepEqual(body, { ttl: 86400 });
      return reply(201, {
        iceServers: [
          { urls: ["stun:stun.cloudflare.com:3478"] },
          { urls: ["turn:turn.cloudflare.com:3478?transport=udp", "turns:turn.cloudflare.com:443?transport=tcp"], username: "u", credential: "c" },
        ],
      });
    }

    if (failNext) {
      const next = failNext;
      failNext = null;
      return reply(next.status, next.body);
    }
    const m = /^\/v1\/apps\/([^/]+)\/(.+)$/.exec(u.pathname);
    assert.ok(m, "unexpected path " + u.pathname);
    if (m[1] !== appId || auth !== "Bearer " + secret) return reply(401, { errorCode: "unauthorized" });
    const rest = m[2];

    if (rest === "sessions/new" && init.method === "POST") {
      const sessionId = "sess-" + nextSession++;
      sessions.set(sessionId, { pushed: new Map(), nextMid: 0, pendingOffer: false });
      return reply(201, { sessionId });
    }
    const s = /^sessions\/([^/]+)\/(tracks\/new|renegotiate)$/.exec(rest);
    if (!s || !sessions.has(s[1])) return reply(404, { errorCode: "session_not_found" });
    const session = sessions.get(s[1]);

    if (s[2] === "renegotiate") {
      assert.equal(init.method, "PUT");
      assert.equal(body.sessionDescription.type, "answer");
      assert.ok(session.pendingOffer, "renegotiate without an offer to answer");
      session.pendingOffer = false;
      return reply(200, {});
    }

    assert.equal(init.method, "POST");
    const locals = (body.tracks || []).filter((t) => t.location === "local");
    const remotes = (body.tracks || []).filter((t) => t.location === "remote");
    if (locals.length) {
      assert.equal(body.sessionDescription.type, "offer");
      for (const t of locals) session.pushed.set(t.trackName, t.mid);
      return reply(200, {
        requiresImmediateRenegotiation: false,
        sessionDescription: { type: "answer", sdp: "v=0\r\nanswer-for-" + s[1] + "\r\n" },
        tracks: locals.map((t) => ({ location: "local", mid: t.mid, trackName: t.trackName })),
      });
    }
    const out = remotes.map((t) => {
      const target = sessions.get(t.sessionId);
      if (!target || !target.pushed.has(t.trackName)) {
        return { location: "remote", sessionId: t.sessionId, trackName: t.trackName, errorCode: "track_not_found" };
      }
      return { location: "remote", sessionId: t.sessionId, trackName: t.trackName, mid: String(session.nextMid++) };
    });
    session.pendingOffer = out.some((t) => !t.errorCode);
    return reply(200, {
      requiresImmediateRenegotiation: session.pendingOffer,
      sessionDescription: session.pendingOffer ? { type: "offer", sdp: "v=0\r\noffer-for-" + s[1] + "\r\n" } : undefined,
      tracks: out,
    });
  }

  return {
    fetch: fetchImpl,
    calls,
    sessions,
    failNext: (status, body) => (failNext = { status, body }),
    turn: (mode) => (turnMode = mode),
    callsTo: (suffix) => calls.filter((c) => c.path.endsWith(suffix)),
  };
}

function world({ voice = true, turn = false } = {}) {
  const env = { DB: createDb() };
  if (voice) {
    env.CF_REALTIME_APP_ID = APP_ID;
    env.CF_REALTIME_APP_SECRET = APP_SECRET;
  }
  if (turn) {
    env.TURN_KEY_ID = TURN_ID;
    env.TURN_KEY_API_TOKEN = TURN_TOKEN;
  }
  const cf = fakeCloudflare();
  let now = 1_700_000_000_000;
  async function call(path, body, { method = "POST", ip = "1.1.1.1", raw } = {}) {
    const request = new Request("https://friends.test" + path, {
      method,
      headers: { "content-type": "application/json", "cf-connecting-ip": ip },
      body: method === "POST" ? (raw !== undefined ? raw : JSON.stringify(body || {})) : undefined,
    });
    const response = await handle(request, env, now, { fetch: cf.fetch });
    return { status: response.status, body: await response.json() };
  }
  let n = 0;
  async function register(name) {
    const r = await call("/friends/register", { name }, { ip: "9.9.9." + n++ });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    return { id: r.body.id, token: r.body.token, name };
  }
  async function befriend(a, b) {
    assert.equal((await call("/friends/request", { ...a, name: b.name })).status, 200);
    assert.equal((await call("/friends/accept", { ...b, friend: a.id })).status, 200);
  }
  async function join(user, friend) {
    const r = await call("/voice/join", friend ? { ...user, friend: friend.id } : user);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    return r.body;
  }
  async function publish(user, mid = "0") {
    const r = await call("/voice/publish", { ...user, offer: OFFER, mid });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    return r.body;
  }
  return { env, cf, call, register, befriend, join, publish, advance: (ms) => (now += ms), now: () => now };
}

const rowOf = (w, id) => w.env.DB.sqlite.prepare("SELECT * FROM voice_members WHERE user_id = ?").get(id);

test("voice needs the Realtime secrets and says so plainly", async () => {
  const w = world({ voice: false });
  const a = await w.register("Alice");
  const r = await w.call("/voice/join", a);
  assert.equal(r.status, 503);
  assert.match(r.body.error, /not set up/);
  assert.equal(w.cf.calls.length, 0);
  const health = await w.call("/health", undefined, { method: "GET" });
  assert.equal(health.body.voice, false);
});

test("health reports whether voice and TURN are configured", async () => {
  const on = world({ turn: true });
  const h = await on.call("/health", undefined, { method: "GET" });
  assert.equal(h.status, 200);
  assert.equal(h.body.voice, true);
  assert.equal(h.body.turn, true);
  assert.equal(JSON.stringify(h.body).includes(APP_SECRET), false);
  assert.equal((await world().call("/health", undefined, { method: "GET" })).body.turn, false);
});

test("starting a voice chat creates two Realtime sessions and a room, with the secret only sent to Cloudflare", async () => {
  const w = world();
  const a = await w.register("Alice");
  const joined = await w.join(a);
  assert.match(joined.room, /^[0-9a-f]{16}$/);
  assert.deepEqual(joined.members.map((m) => [m.id, m.name, m.ready]), [[a.id, "Alice", false]]);
  assert.deepEqual(joined.iceServers, [{ urls: ["stun:stun.cloudflare.com:3478"] }]);
  assert.equal(w.cf.callsTo("/sessions/new").length, 2);
  assert.ok(w.cf.calls.every((c) => c.auth === "Bearer " + APP_SECRET));
  assert.equal(JSON.stringify(joined).includes(APP_SECRET), false);
  assert.equal(JSON.stringify(joined).includes("sess-"), false, "Realtime session ids stay in the Worker");
  const row = rowOf(w, a.id);
  assert.notEqual(row.push_session, row.pull_session);
  assert.equal(row.published, 0);
});

test("a wrong Realtime secret is reported without leaking it", async () => {
  const w = world();
  w.env.CF_REALTIME_APP_SECRET = "wrong-secret";
  const a = await w.register("Alice");
  const r = await w.call("/voice/join", a);
  assert.equal(r.status, 502);
  assert.match(r.body.error, /rejected the Realtime app id or secret/);
  assert.equal(JSON.stringify(r.body).includes("wrong-secret"), false);
  assert.equal(rowOf(w, a.id), undefined, "no half-created voice row");
});

test("Cloudflare errors and outages become readable 502s", async () => {
  const w = world();
  const a = await w.register("Alice");
  w.cf.failNext(500, { errorCode: "internal", errorDescription: "SFU unavailable" });
  const r = await w.call("/voice/join", a);
  assert.equal(r.status, 502);
  assert.match(r.body.error, /SFU unavailable/);
  const down = { ...w.env };
  const res = await handle(
    new Request("https://friends.test/voice/join", { method: "POST", body: JSON.stringify(a) }),
    down,
    w.now(),
    { fetch: async () => { throw new TypeError("offline"); } },
  );
  assert.equal(res.status, 502);
  assert.match((await res.json()).error, /not reachable/);
});

test("joining a friend's voice chat needs an accepted friendship and a friend who is in voice", async () => {
  const w = world();
  const a = await w.register("Alice");
  const b = await w.register("Bobby");
  const c = await w.register("Carol");
  await w.befriend(a, b);

  assert.equal((await w.call("/voice/join", { ...b, friend: a.id })).status, 404, "Alice is not in voice yet");
  const room = await w.join(a);
  assert.equal((await w.call("/voice/join", { ...c, friend: a.id })).status, 403, "Carol is not Alice's friend");
  assert.equal((await w.call("/friends/request", { ...c, name: "Alice" })).status, 200);
  assert.equal((await w.call("/voice/join", { ...c, friend: a.id })).status, 403, "a pending request is not a friendship");
  assert.equal((await w.call("/voice/join", { ...c, friend: "nonsense" })).status, 400);

  const joined = await w.join(b, a);
  assert.equal(joined.room, room.room);
  assert.deepEqual(joined.members.map((m) => m.name), ["Alice", "Bobby"]);
  // Everyone shares one room but has their own two sessions.
  assert.notEqual(rowOf(w, a.id).push_session, rowOf(w, b.id).push_session);
});

test("a friend who appears offline cannot be joined", async () => {
  const w = world();
  const a = await w.register("Alice");
  const b = await w.register("Bobby");
  await w.befriend(a, b);
  await w.join(a);
  assert.equal((await w.call("/friends/presence", { ...a, server: "", hidden: true })).status, 200);
  const r = await w.call("/voice/join", { ...b, friend: a.id });
  assert.equal(r.status, 404);
  assert.match(r.body.error, /not in a voice chat/);
});

test("a room holds MAX_VOICE people, and a leaver frees a place", async () => {
  const w = world();
  const host = await w.register("Hosty");
  const guests = [];
  for (let i = 0; i < MAX_VOICE; i++) {
    const g = await w.register("Guest" + i);
    await w.befriend(host, g);
    guests.push(g);
  }
  await w.join(host);
  for (let i = 0; i < MAX_VOICE - 1; i++) await w.join(guests[i], host);
  const full = await w.call("/voice/join", { ...guests[MAX_VOICE - 1], friend: host.id });
  assert.equal(full.status, 409);
  assert.match(full.body.error, /full/);
  assert.equal(rowOf(w, guests[MAX_VOICE - 1].id), undefined);

  assert.equal((await w.call("/voice/leave", guests[0])).status, 200);
  const joined = await w.join(guests[MAX_VOICE - 1], host);
  assert.equal(joined.members.length, MAX_VOICE);
});

test("joining another room leaves the first, and rejoining the same room is allowed when it is full", async () => {
  const w = world();
  const a = await w.register("Alice");
  const b = await w.register("Bobby");
  await w.befriend(a, b);
  const roomA = (await w.join(a)).room;
  const roomB = (await w.join(b)).room;
  assert.notEqual(roomA, roomB);
  const back = await w.join(a, b);
  assert.equal(back.room, roomB);
  assert.equal(w.env.DB.sqlite.prepare("SELECT COUNT(*) AS n FROM voice_members").get().n, 2);
  // Starting over (a reconnect) gives a new joined time so other clients notice.
  const before = rowOf(w, a.id).joined_at;
  w.advance(5000);
  await w.join(a, b);
  assert.ok(rowOf(w, a.id).joined_at > before);
});

test("publish sends the offer for the caller's own session only and marks the microphone as ready", async () => {
  const w = world();
  const a = await w.register("Alice");
  const b = await w.register("Bobby");
  await w.join(a);
  await w.join(b);
  const out = await w.publish(a, "0");
  assert.match(out.answer, /^v=0/);
  const call = w.cf.callsTo("/tracks/new").at(-1);
  assert.equal(call.path, `/v1/apps/${APP_ID}/sessions/${rowOf(w, a.id).push_session}/tracks/new`);
  assert.deepEqual(call.body, {
    sessionDescription: { type: "offer", sdp: OFFER },
    tracks: [{ location: "local", mid: "0", trackName: "mic" }],
  });
  assert.equal(rowOf(w, a.id).published, 1);
  assert.equal(rowOf(w, b.id).published, 0);
});

test("publish validates the request and the caller", async () => {
  const w = world();
  const a = await w.register("Alice");
  assert.equal((await w.call("/voice/publish", { ...a, offer: OFFER, mid: "0" })).status, 409, "not in a voice chat");
  await w.join(a);
  assert.equal((await w.call("/voice/publish", { ...a, offer: "nope", mid: "0" })).status, 400);
  assert.equal((await w.call("/voice/publish", { ...a, offer: OFFER, mid: "../0" })).status, 400);
  assert.equal((await w.call("/voice/publish", { ...a, offer: OFFER })).status, 400);
  assert.equal((await w.call("/voice/publish", { ...a, offer: OFFER + "x".repeat(30_000), mid: "0" })).status, 400);
  assert.equal((await w.call("/voice/publish", { ...a, offer: OFFER + "x".repeat(50_000), mid: "0" })).status, 413);
  assert.equal((await w.call("/voice/publish", { id: a.id, token: "0".repeat(64), offer: OFFER, mid: "0" })).status, 401);
  assert.equal(rowOf(w, a.id).published, 0);
});

test("a publish Cloudflare refuses leaves the microphone not ready", async () => {
  const w = world();
  const a = await w.register("Alice");
  await w.join(a);
  w.cf.failNext(200, { tracks: [{ mid: "0", errorCode: "bad_sdp" }], sessionDescription: { type: "answer", sdp: "v=0\r\n" } });
  const r = await w.call("/voice/publish", { ...a, offer: OFFER, mid: "0" });
  assert.equal(r.status, 502);
  assert.equal(rowOf(w, a.id).published, 0);
});

test("pull maps players to track ids, and only players in the caller's room who have published can be pulled", async () => {
  const w = world();
  const a = await w.register("Alice");
  const b = await w.register("Bobby");
  const c = await w.register("Carol");
  const outsider = await w.register("Outsider");
  await w.befriend(a, b);
  await w.befriend(a, c);
  await w.join(a);
  await w.join(b, a);
  await w.join(c, a);
  await w.join(outsider); // a different room
  await w.publish(b);
  await w.publish(outsider);

  const before = w.cf.callsTo("/tracks/new").length;
  const r = await w.call("/voice/pull", { ...a, members: [b.id, c.id, outsider.id] });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.renegotiate, true);
  assert.match(r.body.offer, /^v=0/);
  const byMember = Object.fromEntries(r.body.tracks.map((t) => [t.member, t]));
  assert.equal(byMember[b.id].error, "");
  assert.match(byMember[b.id].mid, /^\d+$/);
  assert.equal(byMember[c.id].error, "not available", "Carol has not published yet");
  assert.equal(byMember[outsider.id].error, "not available", "Outsider is in another room");

  const calls = w.cf.callsTo("/tracks/new").slice(before);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].path, `/v1/apps/${APP_ID}/sessions/${rowOf(w, a.id).pull_session}/tracks/new`);
  assert.deepEqual(calls[0].body.tracks, [{ location: "remote", sessionId: rowOf(w, b.id).push_session, trackName: "mic" }]);
  assert.equal(JSON.stringify(calls).includes(rowOf(w, outsider.id).push_session), false);
});

test("pull with nobody available makes no Cloudflare call, and bad lists are rejected", async () => {
  const w = world();
  const a = await w.register("Alice");
  const b = await w.register("Bobby");
  await w.befriend(a, b);
  await w.join(a);
  await w.join(b, a);
  const before = w.cf.calls.length;
  const r = await w.call("/voice/pull", { ...a, members: [b.id] });
  assert.equal(r.status, 200);
  assert.equal(r.body.offer, null);
  assert.equal(r.body.renegotiate, false);
  assert.deepEqual(r.body.tracks, [{ member: b.id, mid: "", error: "not available" }]);
  assert.equal(w.cf.calls.length, before);

  assert.equal((await w.call("/voice/pull", { ...a, members: [] })).status, 400);
  assert.equal((await w.call("/voice/pull", { ...a, members: [a.id] })).status, 400, "cannot pull yourself");
  assert.equal((await w.call("/voice/pull", { ...a, members: ["zz"] })).status, 400);
  assert.equal((await w.call("/voice/pull", { ...a, members: "nope" })).status, 400);
  assert.equal((await w.call("/voice/pull", { ...a, members: Array.from({ length: 9 }, (_, i) => String(i).repeat(32)) })).status, 400);
});

test("a stale player cannot be pulled", async () => {
  const w = world();
  const a = await w.register("Alice");
  const b = await w.register("Bobby");
  await w.befriend(a, b);
  await w.join(a);
  await w.join(b, a);
  await w.publish(b);
  w.advance(VOICE_STALE_MS + 1000);
  await w.call("/voice/state", a); // Alice keeps polling, Bobby does not
  const r = await w.call("/voice/pull", { ...a, members: [b.id] });
  assert.deepEqual(r.body.tracks, [{ member: b.id, mid: "", error: "not available" }]);
});

test("renegotiate sends the answer to the caller's pull session", async () => {
  const w = world();
  const a = await w.register("Alice");
  const b = await w.register("Bobby");
  await w.befriend(a, b);
  await w.join(a);
  await w.join(b, a);
  await w.publish(b);
  assert.equal((await w.call("/voice/pull", { ...a, members: [b.id] })).status, 200);
  const r = await w.call("/voice/renegotiate", { ...a, answer: OFFER });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const call = w.cf.calls.at(-1);
  assert.equal(call.method, "PUT");
  assert.equal(call.path, `/v1/apps/${APP_ID}/sessions/${rowOf(w, a.id).pull_session}/renegotiate`);
  assert.deepEqual(call.body, { sessionDescription: { type: "answer", sdp: OFFER } });
  assert.equal((await w.call("/voice/renegotiate", { ...a, answer: 5 })).status, 400);
});

test("state is the heartbeat: members, readiness, stale players and few writes", async () => {
  const w = world();
  const a = await w.register("Alice");
  const b = await w.register("Bobby");
  await w.befriend(a, b);
  assert.deepEqual((await w.call("/voice/state", a)).body, { ok: true, inRoom: false, room: "", members: [] });
  await w.join(a);
  await w.join(b, a);
  await w.publish(b);

  let s = (await w.call("/voice/state", a)).body;
  assert.equal(s.inRoom, true);
  assert.deepEqual(s.members.map((m) => [m.name, m.ready]), [["Alice", false], ["Bobby", true]]);

  // A poll soon after the last write does not write again.
  const t0 = rowOf(w, a.id).last_seen;
  w.advance(4000);
  await w.call("/voice/state", a);
  assert.equal(rowOf(w, a.id).last_seen, t0);
  w.advance(12_000);
  await w.call("/voice/state", a);
  assert.equal(rowOf(w, a.id).last_seen, w.now());

  // Bobby stops polling: after the stale window Alice no longer sees him.
  w.advance(VOICE_STALE_MS);
  s = (await w.call("/voice/state", a)).body;
  assert.deepEqual(s.members.map((m) => m.name), ["Alice"]);
  // He comes back by polling and is listed again, unchanged.
  s = (await w.call("/voice/state", b)).body;
  assert.equal(s.inRoom, true);
  assert.deepEqual(s.members.map((m) => m.name), ["Alice", "Bobby"]);
});

test("leaving removes the row, and leaving twice is fine", async () => {
  const w = world();
  const a = await w.register("Alice");
  await w.join(a);
  assert.equal((await w.call("/voice/leave", a)).status, 200);
  assert.equal(rowOf(w, a.id), undefined);
  assert.equal((await w.call("/voice/leave", a)).status, 200);
  assert.equal((await w.call("/voice/state", a)).body.inRoom, false);
});

test("the friends list shows who is in voice (not hidden players) and how many are in the room", async () => {
  const w = world();
  const a = await w.register("Alice");
  const b = await w.register("Bobby");
  const c = await w.register("Carol");
  const d = await w.register("Dave");
  await w.befriend(a, b);
  await w.befriend(a, c);
  await w.befriend(a, d);
  const room = (await w.join(b)).room;
  await w.join(c);
  await w.join(d);
  await w.call("/friends/presence", { ...d, server: "", hidden: true });

  const list = (await w.call("/friends/list", a)).body.friends;
  const by = Object.fromEntries(list.map((f) => [f.name, f]));
  assert.equal(by.Bobby.voice, 1);
  assert.equal(by.Bobby.voiceRoom, room);
  assert.equal(by.Carol.voice, 1);
  assert.equal(by.Dave.voice, 0, "hidden players do not show as in voice");
  assert.equal(by.Dave.voiceRoom, "");

  // Strangers never see anyone's voice room.
  const stranger = await w.register("Stella");
  assert.deepEqual((await w.call("/friends/list", stranger)).body.friends, []);

  // Stale rows stop showing.
  w.advance(VOICE_STALE_MS + 1);
  const later = (await w.call("/friends/list", a)).body.friends;
  assert.ok(later.every((f) => f.voice === 0 && f.voiceRoom === ""));
});

test("deleting an account also removes it from voice", async () => {
  const w = world();
  const a = await w.register("Alice");
  await w.join(a);
  assert.equal((await w.call("/friends/delete", a)).status, 200);
  assert.equal(rowOf(w, a.id), undefined);
});

test("TURN credentials are added when a TURN key is configured, and fall back to STUN when they fail", async () => {
  const w = world({ turn: true });
  const a = await w.register("Alice");
  const joined = await w.join(a);
  assert.equal(joined.iceServers.length, 2);
  assert.ok(joined.iceServers[1].urls.some((u) => u.startsWith("turns:") && u.includes(":443")));
  assert.equal(joined.iceServers[1].username, "u");

  w.cf.turn("bad");
  assert.deepEqual((await w.join(a)).iceServers, [{ urls: ["stun:stun.cloudflare.com:3478"] }]);
  w.cf.turn("down");
  assert.deepEqual((await w.join(a)).iceServers, [{ urls: ["stun:stun.cloudflare.com:3478"] }]);
});

test("voice joins are rate limited", async () => {
  const w = world();
  const a = await w.register("Alice");
  for (let i = 0; i < 30; i++) assert.equal((await w.call("/voice/join", a)).status, 200);
  assert.equal((await w.call("/voice/join", a)).status, 429);
  w.advance(3_600_000);
  assert.equal((await w.call("/voice/join", a)).status, 200);
});

test("voice endpoints need a valid session and POST, and unknown ones 404", async () => {
  const w = world();
  for (const name of ["join", "publish", "pull", "renegotiate", "state", "leave"]) {
    assert.equal((await w.call("/voice/" + name, {})).status, 401, name);
    assert.equal((await w.call("/voice/" + name, undefined, { method: "GET" })).status, 405, name);
  }
  assert.equal((await w.call("/voice/nothing", {})).status, 404);
  assert.equal((await w.call("/voice/__proto__", {})).status, 404);
  assert.equal(w.cf.calls.length, 0);
});
