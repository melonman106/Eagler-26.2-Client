// A stand-in for the parts of the Cloudflare Realtime SFU HTTPS API that the Worker uses, shared by the Worker
// tests (voice.test.mjs) and the browser-bridge tests (voice-bridge.test.mjs).
import assert from "node:assert/strict";

export const APP_ID = "app123";
export const APP_SECRET = "s3cret-app-token";
export const TURN_ID = "turnkey1";
export const TURN_TOKEN = "turn-api-token";
export const OFFER = "v=0\r\no=- 1 2 IN IP4 127.0.0.1\r\ns=-\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n";

/**
 * A stand-in for the parts of the Cloudflare Realtime SFU HTTPS API the Worker uses. It keeps just enough state
 * (sessions, pushed tracks, assigned mids) to check who may pull what, and it records every call.
 */
export function fakeCloudflare({ appId = APP_ID, secret = APP_SECRET } = {}) {
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
      // Like a real offer, the SDP names each new track by its mid (a=mid:N), which is how a browser knows what arrived.
      sessionDescription: session.pendingOffer
        ? {
            type: "offer",
            sdp: "v=0\r\noffer-for-" + s[1] + "\r\n" + out.filter((t) => !t.errorCode).map((t) => "a=mid:" + t.mid + "\r\n").join(""),
          }
        : undefined,
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
