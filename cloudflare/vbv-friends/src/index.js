// VBV Friends: a small Cloudflare Worker + D1 backend for the VBV client's Friends screen.
//
// All endpoints take and return JSON and are called with POST (plus GET /health). A player is identified by
// an id and a secret token that the client creates at registration and keeps in the browser; only the
// SHA-256 hash of the token is stored. There are no passwords, e-mail addresses or IP addresses in the
// database (the IP is only used, unstored, to rate-limit registration).
//
//   POST /friends/register  { name }                     -> { ok, id, token, name }
//   POST /friends/login     { name, token }              -> { ok, id, name }          (restore on a new browser)
//   POST /friends/me        { id, token }                -> { ok, id, name, hidden }
//   POST /friends/list      { id, token }                -> { ok, friends, incoming, outgoing }
//   POST /friends/request   { id, token, name }          -> { ok, state: "sent" | "accepted" }
//   POST /friends/accept    { id, token, friend }        -> { ok }
//   POST /friends/decline   { id, token, friend }        -> { ok }   (also cancels a request you sent)
//   POST /friends/remove    { id, token, friend }        -> { ok }
//   POST /friends/presence  { id, token, server, hidden }-> { ok }   (heartbeat; "server" is what you are on)
//   POST /friends/delete    { id, token }                -> { ok }   (deletes the account and its friendships)
//
// Voice chat (Cloudflare Realtime SFU). The Worker holds the Realtime app secret and talks to Cloudflare; the
// browser only ever talks to the Worker, and the Worker only lets a player publish on their own Realtime session
// and pull the microphones of players who are in the same voice room. Needs the Worker secrets
// CF_REALTIME_APP_ID and CF_REALTIME_APP_SECRET (and optionally TURN_KEY_ID and TURN_KEY_API_TOKEN, which let
// people behind networks that block UDP, such as many schools, still connect).
//
//   POST /voice/join        { id, token, friend? }       -> { ok, room, members, iceServers }
//        (no friend: start a new room; friend: join the room that accepted friend is in)
//   POST /voice/publish     { id, token, offer, mid }    -> { ok, answer }     (publish your microphone)
//   POST /voice/pull        { id, token, members }       -> { ok, offer, renegotiate, tracks }
//   POST /voice/renegotiate { id, token, answer }        -> { ok }             (answer to the offer from /voice/pull)
//   POST /voice/state       { id, token }                -> { ok, inRoom, room, members }   (poll every few seconds)
//   POST /voice/leave       { id, token }                -> { ok }
//
// Errors are { ok: false, error } with a readable message and a matching HTTP status.

export const ONLINE_MS = 90_000;
export const MAX_FRIENDS = 100;
export const MAX_PENDING = 50;
const NAME_RE = /^[A-Za-z0-9_]{3,16}$/;
const HEX_ID_RE = /^[0-9a-f]{32}$/;
const HEX_TOKEN_RE = /^[0-9a-f]{64}$/;
const MAX_BODY = 4096;
const MAX_SDP_BODY = 40_000;

// Voice chat.
export const MAX_VOICE = 8;               // people in one room
export const VOICE_STALE_MS = 45_000;     // no state poll for this long: the player counts as gone
const VOICE_TOUCH_MS = 15_000;            // state polls write last_seen at most this often (keeps D1 writes low)
const VOICE_PRUNE_MS = 600_000;           // rows this old are deleted for good
const TRACK = "mic";
const RTC_BASE = "https://rtc.live.cloudflare.com/v1";
const STUN_ONLY = [{ urls: ["stun:stun.cloudflare.com:3478"] }];
const SDP_RE = /^v=0\r?\n/;
const MID_RE = /^[A-Za-z0-9_-]{1,16}$/;

class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

const CORS = {
  "access-control-allow-origin": "*",
  "access-control-allow-methods": "GET, POST, OPTIONS",
  "access-control-allow-headers": "content-type",
  "access-control-max-age": "86400",
};

function json(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", "cache-control": "no-store", ...CORS },
  });
}

function randomHex(bytes) {
  const data = new Uint8Array(bytes);
  crypto.getRandomValues(data);
  return Array.from(data, (b) => b.toString(16).padStart(2, "0")).join("");
}

async function sha256Hex(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
}

function sameText(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

async function readBody(request, limit = MAX_BODY) {
  const text = await request.text();
  if (text.length > limit) throw new ApiError(413, "Request is too large");
  if (!text) return {};
  try {
    const value = JSON.parse(text);
    if (value === null || typeof value !== "object" || Array.isArray(value)) throw new Error("not an object");
    return value;
  } catch (_) {
    throw new ApiError(400, "Request body must be a JSON object");
  }
}

function cleanName(value) {
  const name = typeof value === "string" ? value.trim() : "";
  if (!NAME_RE.test(name)) {
    throw new ApiError(400, "Names are 3-16 characters: letters, numbers and underscores");
  }
  return name;
}

function cleanServer(value) {
  if (typeof value !== "string") return "";
  // eslint-disable-next-line no-control-regex
  return value.replace(/[\u0000-\u001f\u007f]/g, "").trim().slice(0, 64);
}

/** Fixed-window counter. Throws 429 once `max` hits have been made in the current window. */
async function hit(db, key, windowMs, max, now) {
  const window = Math.floor(now / windowMs);
  await db
    .prepare("INSERT INTO rate (key, window, n) VALUES (?, ?, 1) ON CONFLICT (key, window) DO UPDATE SET n = n + 1")
    .bind(key, window)
    .run();
  const row = await db.prepare("SELECT n FROM rate WHERE key = ? AND window = ?").bind(key, window).first();
  if (row && row.n > max) throw new ApiError(429, "Too many requests, try again later");
  // Old windows are never read again; drop them now and then so the table stays tiny.
  if (Math.random() < 0.02) {
    await db.prepare("DELETE FROM rate WHERE window < ?").bind(window - 2).run();
  }
}

async function authenticate(db, body) {
  const id = typeof body.id === "string" ? body.id : "";
  const token = typeof body.token === "string" ? body.token : "";
  if (!HEX_ID_RE.test(id) || !HEX_TOKEN_RE.test(token)) throw new ApiError(401, "Your Friends session is invalid");
  const user = await db.prepare("SELECT id, name, token_hash, hidden FROM users WHERE id = ?").bind(id).first();
  if (!user || !sameText(user.token_hash, await sha256Hex(token))) {
    throw new ApiError(401, "Your Friends session is invalid");
  }
  return user;
}

function pair(x, y) {
  return x < y ? [x, y] : [y, x];
}

async function countAccepted(db, id) {
  const row = await db
    .prepare("SELECT COUNT(*) AS n FROM friendships WHERE status = 'accepted' AND (user_a = ? OR user_b = ?)")
    .bind(id, id)
    .first();
  return row ? row.n : 0;
}

async function countPendingFor(db, id) {
  const row = await db
    .prepare("SELECT COUNT(*) AS n FROM friendships WHERE status = 'pending' AND requester <> ? AND (user_a = ? OR user_b = ?)")
    .bind(id, id, id)
    .first();
  return row ? row.n : 0;
}

async function countPendingFrom(db, id) {
  const row = await db
    .prepare("SELECT COUNT(*) AS n FROM friendships WHERE status = 'pending' AND requester = ?")
    .bind(id)
    .first();
  return row ? row.n : 0;
}

function requireFriendId(body) {
  const friend = typeof body.friend === "string" ? body.friend : "";
  if (!HEX_ID_RE.test(friend)) throw new ApiError(400, "Unknown player");
  return friend;
}

const routes = {
  async register(db, body, ctx) {
    const name = cleanName(body.name);
    await hit(db, "reg:" + ctx.ip, 3_600_000, 10, ctx.now);
    const lower = name.toLowerCase();
    const taken = await db.prepare("SELECT id FROM users WHERE name_lower = ?").bind(lower).first();
    if (taken) throw new ApiError(409, "That name is already taken");
    const id = randomHex(16);
    const token = randomHex(32);
    try {
      await db
        .prepare("INSERT INTO users (id, name, name_lower, token_hash, created_at) VALUES (?, ?, ?, ?, ?)")
        .bind(id, name, lower, await sha256Hex(token), ctx.now)
        .run();
    } catch (error) {
      if (/unique/i.test(String(error && error.message))) throw new ApiError(409, "That name is already taken");
      throw error;
    }
    return { id, token, name };
  },

  async login(db, body, ctx) {
    const name = cleanName(body.name);
    const token = typeof body.token === "string" ? body.token : "";
    await hit(db, "login:" + ctx.ip, 3_600_000, 30, ctx.now);
    const user = await db
      .prepare("SELECT id, name, token_hash FROM users WHERE name_lower = ?")
      .bind(name.toLowerCase())
      .first();
    if (!user || !HEX_TOKEN_RE.test(token) || !sameText(user.token_hash, await sha256Hex(token))) {
      throw new ApiError(401, "That name and account key do not match");
    }
    return { id: user.id, name: user.name };
  },

  async me(db, body) {
    const user = await authenticate(db, body);
    return { id: user.id, name: user.name, hidden: !!user.hidden };
  },

  async list(db, body, ctx) {
    const me = await authenticate(db, body);
    const rows = await db
      .prepare(
        "SELECT f.status AS status, f.requester AS requester, u.id AS id, u.name AS name, " +
          "u.last_seen AS last_seen, u.server AS server, u.hidden AS hidden " +
          "FROM friendships f JOIN users u ON u.id = CASE WHEN f.user_a = ? THEN f.user_b ELSE f.user_a END " +
          "WHERE f.user_a = ? OR f.user_b = ? ORDER BY u.name_lower",
      )
      .bind(me.id, me.id, me.id)
      .all();
    // Friends who are in a voice chat right now (never those who appear offline), with the size of their room.
    const voice = new Map();
    const voiceRows = await db
      .prepare(
        "SELECT m.user_id AS id, m.room_id AS room_id, " +
          "(SELECT COUNT(*) FROM voice_members x WHERE x.room_id = m.room_id AND x.last_seen > ?) AS n " +
          "FROM voice_members m JOIN users u ON u.id = m.user_id " +
          "WHERE m.last_seen > ? AND u.hidden = 0 AND m.user_id IN (" +
          "SELECT CASE WHEN user_a = ? THEN user_b ELSE user_a END FROM friendships " +
          "WHERE status = 'accepted' AND (user_a = ? OR user_b = ?))",
      )
      .bind(ctx.now - VOICE_STALE_MS, ctx.now - VOICE_STALE_MS, me.id, me.id, me.id)
      .all();
    for (const row of voiceRows.results || []) voice.set(row.id, row);
    const friends = [];
    const incoming = [];
    const outgoing = [];
    for (const row of rows.results || []) {
      if (row.status === "accepted") {
        const online = !row.hidden && ctx.now - row.last_seen < ONLINE_MS;
        const inVoice = voice.get(row.id);
        friends.push({
          id: row.id,
          name: row.name,
          online,
          server: online ? row.server : "",
          lastSeen: row.hidden ? 0 : row.last_seen,
          voice: inVoice ? inVoice.n : 0,
          voiceRoom: inVoice ? inVoice.room_id : "",
        });
      } else if (row.requester === me.id) {
        outgoing.push({ id: row.id, name: row.name });
      } else {
        incoming.push({ id: row.id, name: row.name });
      }
    }
    // Online friends first; the query already sorted by name.
    friends.sort((a, b) => Number(b.online) - Number(a.online));
    return { friends, incoming, outgoing };
  },

  async request(db, body, ctx) {
    const me = await authenticate(db, body);
    const name = cleanName(body.name);
    await hit(db, "req:" + me.id, 3_600_000, 30, ctx.now);
    const target = await db.prepare("SELECT id, name FROM users WHERE name_lower = ?").bind(name.toLowerCase()).first();
    if (!target) throw new ApiError(404, "No player called " + name + " uses Friends yet");
    if (target.id === me.id) throw new ApiError(400, "You cannot add yourself");
    const [a, b] = pair(me.id, target.id);
    const existing = await db.prepare("SELECT status, requester FROM friendships WHERE user_a = ? AND user_b = ?").bind(a, b).first();
    if (existing) {
      if (existing.status === "accepted") throw new ApiError(409, "You are already friends with " + target.name);
      if (existing.requester === me.id) throw new ApiError(409, "You already sent " + target.name + " a request");
      // They asked you first, so asking back is the same as accepting.
      if ((await countAccepted(db, me.id)) >= MAX_FRIENDS) throw new ApiError(409, "Your friends list is full");
      if ((await countAccepted(db, target.id)) >= MAX_FRIENDS) throw new ApiError(409, target.name + "'s friends list is full");
      await db.prepare("UPDATE friendships SET status = 'accepted' WHERE user_a = ? AND user_b = ?").bind(a, b).run();
      return { state: "accepted", name: target.name };
    }
    if ((await countAccepted(db, me.id)) >= MAX_FRIENDS) throw new ApiError(409, "Your friends list is full");
    if ((await countPendingFrom(db, me.id)) >= MAX_PENDING) throw new ApiError(409, "You have too many requests waiting");
    if ((await countPendingFor(db, target.id)) >= MAX_PENDING) throw new ApiError(409, target.name + " has too many requests waiting");
    await db
      .prepare("INSERT INTO friendships (user_a, user_b, status, requester, created_at) VALUES (?, ?, 'pending', ?, ?)")
      .bind(a, b, me.id, ctx.now)
      .run();
    return { state: "sent", name: target.name };
  },

  async accept(db, body) {
    const me = await authenticate(db, body);
    const friend = requireFriendId(body);
    const [a, b] = pair(me.id, friend);
    const row = await db.prepare("SELECT status, requester FROM friendships WHERE user_a = ? AND user_b = ?").bind(a, b).first();
    if (!row || row.status !== "pending" || row.requester === me.id) throw new ApiError(404, "There is no request to accept");
    if ((await countAccepted(db, me.id)) >= MAX_FRIENDS) throw new ApiError(409, "Your friends list is full");
    if ((await countAccepted(db, friend)) >= MAX_FRIENDS) throw new ApiError(409, "Their friends list is full");
    await db.prepare("UPDATE friendships SET status = 'accepted' WHERE user_a = ? AND user_b = ?").bind(a, b).run();
    return {};
  },

  async decline(db, body) {
    const me = await authenticate(db, body);
    const friend = requireFriendId(body);
    const [a, b] = pair(me.id, friend);
    await db.prepare("DELETE FROM friendships WHERE user_a = ? AND user_b = ? AND status = 'pending'").bind(a, b).run();
    return {};
  },

  async remove(db, body) {
    const me = await authenticate(db, body);
    const friend = requireFriendId(body);
    const [a, b] = pair(me.id, friend);
    await db.prepare("DELETE FROM friendships WHERE user_a = ? AND user_b = ? AND status = 'accepted'").bind(a, b).run();
    return {};
  },

  async presence(db, body, ctx) {
    const me = await authenticate(db, body);
    const hidden = typeof body.hidden === "boolean" ? (body.hidden ? 1 : 0) : me.hidden ? 1 : 0;
    await db
      .prepare("UPDATE users SET last_seen = ?, server = ?, hidden = ? WHERE id = ?")
      .bind(ctx.now, cleanServer(body.server), hidden, me.id)
      .run();
    return {};
  },

  async delete(db, body) {
    const me = await authenticate(db, body);
    await db.prepare("DELETE FROM friendships WHERE user_a = ? OR user_b = ?").bind(me.id, me.id).run();
    await db.prepare("DELETE FROM voice_members WHERE user_id = ?").bind(me.id).run();
    await db.prepare("DELETE FROM users WHERE id = ?").bind(me.id).run();
    return {};
  },
};

// ------------------------------------------------------------------------------------------------ voice chat

function voiceConfigured(env) {
  return !!(env.CF_REALTIME_APP_ID && env.CF_REALTIME_APP_SECRET);
}

function requireVoiceConfigured(env) {
  if (!voiceConfigured(env)) {
    throw new ApiError(503, "Voice chat is not set up on this service yet (the Realtime secrets are missing)");
  }
}

/** One call to the Cloudflare Realtime SFU HTTPS API. The app secret never leaves the Worker. */
async function realtime(ctx, method, path, body) {
  requireVoiceConfigured(ctx.env);
  const url = RTC_BASE + "/apps/" + encodeURIComponent(ctx.env.CF_REALTIME_APP_ID) + path;
  let response;
  try {
    response = await ctx.fetch(url, {
      method,
      headers: { authorization: "Bearer " + ctx.env.CF_REALTIME_APP_SECRET, "content-type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (error) {
    console.error("vbv-friends realtime fetch failed", method, path, error && error.message);
    throw new ApiError(502, "The voice service is not reachable right now");
  }
  let data = null;
  try {
    data = await response.json();
  } catch (_) {
    data = null; // an empty body is fine (renegotiate answers with {})
  }
  if (response.status === 401 || response.status === 403) {
    console.error("vbv-friends realtime rejected the app id or secret", response.status);
    throw new ApiError(502, "Cloudflare rejected the Realtime app id or secret; check the Worker secrets");
  }
  if (!response.ok || (data && data.errorCode)) {
    const detail = data && data.errorDescription ? String(data.errorDescription).slice(0, 120) : "HTTP " + response.status;
    console.error("vbv-friends realtime error", method, path, response.status, data && data.errorCode, detail);
    throw new ApiError(502, "The voice service refused the request (" + detail + ")");
  }
  return data || {};
}

/** STUN always; plus TURN (also over TCP 443) when a TURN key is configured, for networks that block UDP. */
async function voiceIceServers(ctx) {
  const keyId = ctx.env.TURN_KEY_ID;
  const token = ctx.env.TURN_KEY_API_TOKEN;
  if (!keyId || !token) return STUN_ONLY;
  try {
    const response = await ctx.fetch(
      RTC_BASE + "/turn/keys/" + encodeURIComponent(keyId) + "/credentials/generate-ice-servers",
      {
        method: "POST",
        headers: { authorization: "Bearer " + token, "content-type": "application/json" },
        body: JSON.stringify({ ttl: 86_400 }),
      },
    );
    const data = await response.json();
    const servers = response.ok && data && Array.isArray(data.iceServers)
      ? data.iceServers.filter((s) => s && (typeof s.urls === "string" || Array.isArray(s.urls)))
      : [];
    if (servers.length) return servers;
    console.error("vbv-friends TURN credentials were not usable", response.status);
  } catch (error) {
    console.error("vbv-friends TURN credentials failed", error && error.message);
  }
  return STUN_ONLY;
}

function cleanSdp(value) {
  if (typeof value !== "string" || value.length > 30_000 || !SDP_RE.test(value)) {
    throw new ApiError(400, "That is not a valid session description");
  }
  return value;
}

async function requireVoiceMember(db, id) {
  const row = await db.prepare("SELECT * FROM voice_members WHERE user_id = ?").bind(id).first();
  if (!row) throw new ApiError(409, "You are not in a voice chat");
  return row;
}

async function roomMembers(db, room, now) {
  const rows = await db
    .prepare(
      "SELECT m.user_id AS id, u.name AS name, m.published AS published, m.joined_at AS joined_at " +
        "FROM voice_members m JOIN users u ON u.id = m.user_id " +
        "WHERE m.room_id = ? AND m.last_seen > ? ORDER BY m.joined_at, u.name_lower",
    )
    .bind(room, now - VOICE_STALE_MS)
    .all();
  return (rows.results || []).map((row) => ({ id: row.id, name: row.name, ready: !!row.published, gen: row.joined_at }));
}

const voiceRoutes = {
  async join(db, body, ctx) {
    const me = await authenticate(db, body);
    requireVoiceConfigured(ctx.env);
    await hit(db, "vjoin:" + me.id, 3_600_000, 30, ctx.now);

    let room;
    const wanted = body.friend;
    if (wanted === undefined || wanted === null || wanted === "" || wanted === me.id) {
      room = randomHex(8);
    } else {
      const friend = requireFriendId(body);
      const [a, b] = pair(me.id, friend);
      const tie = await db
        .prepare("SELECT status FROM friendships WHERE user_a = ? AND user_b = ?")
        .bind(a, b)
        .first();
      if (!tie || tie.status !== "accepted") throw new ApiError(403, "You can only join the voice chat of a friend");
      const there = await db
        .prepare(
          "SELECT m.room_id AS room_id FROM voice_members m JOIN users u ON u.id = m.user_id " +
            "WHERE m.user_id = ? AND m.last_seen > ? AND u.hidden = 0",
        )
        .bind(friend, ctx.now - VOICE_STALE_MS)
        .first();
      if (!there) throw new ApiError(404, "They are not in a voice chat right now");
      room = there.room_id;
    }

    // Leaving any other room comes first: a player is in one voice chat at a time, and in the same room again
    // after a reconnect, so their own old row must not count against the room's size.
    await db.prepare("DELETE FROM voice_members WHERE user_id = ?").bind(me.id).run();

    const [push, pull, iceServers] = await Promise.all([
      realtime(ctx, "POST", "/sessions/new"),
      realtime(ctx, "POST", "/sessions/new"),
      voiceIceServers(ctx),
    ]);
    if (typeof push.sessionId !== "string" || typeof pull.sessionId !== "string") {
      throw new ApiError(502, "The voice service did not create a session");
    }
    // One statement checks the room size and inserts, so two people joining at the same moment cannot both get
    // the last place.
    const inserted = await db
      .prepare(
        "INSERT INTO voice_members (user_id, room_id, push_session, pull_session, published, joined_at, last_seen) " +
          "SELECT ?, ?, ?, ?, 0, ?, ? WHERE (SELECT COUNT(*) FROM voice_members WHERE room_id = ? AND last_seen > ?) < ?",
      )
      .bind(me.id, room, push.sessionId, pull.sessionId, ctx.now, ctx.now, room, ctx.now - VOICE_STALE_MS, MAX_VOICE)
      .run();
    if (!inserted.meta || inserted.meta.changes !== 1) {
      throw new ApiError(409, "That voice chat is full (" + MAX_VOICE + " people)");
    }
    if (Math.random() < 0.05) {
      await db.prepare("DELETE FROM voice_members WHERE last_seen < ?").bind(ctx.now - VOICE_PRUNE_MS).run();
    }
    return { room, members: await roomMembers(db, room, ctx.now), iceServers };
  },

  async publish(db, body, ctx) {
    const me = await authenticate(db, body);
    const row = await requireVoiceMember(db, me.id);
    await hit(db, "vpub:" + me.id, 3_600_000, 60, ctx.now);
    const offer = cleanSdp(body.offer);
    const mid = typeof body.mid === "string" && MID_RE.test(body.mid) ? body.mid : "";
    if (!mid) throw new ApiError(400, "Missing microphone track id");
    const result = await realtime(ctx, "POST", "/sessions/" + encodeURIComponent(row.push_session) + "/tracks/new", {
      sessionDescription: { type: "offer", sdp: offer },
      tracks: [{ location: "local", mid, trackName: TRACK }],
    });
    const track = Array.isArray(result.tracks) ? result.tracks[0] : null;
    const answer = result.sessionDescription;
    if ((track && track.errorCode) || !answer || answer.type !== "answer" || typeof answer.sdp !== "string") {
      throw new ApiError(502, "The voice service could not publish your microphone");
    }
    await db.prepare("UPDATE voice_members SET published = 1 WHERE user_id = ?").bind(me.id).run();
    return { answer: answer.sdp };
  },

  async pull(db, body, ctx) {
    const me = await authenticate(db, body);
    const row = await requireVoiceMember(db, me.id);
    await hit(db, "vpull:" + me.id, 3_600_000, 300, ctx.now);
    const wanted = Array.isArray(body.members) ? body.members : [];
    const ids = [];
    for (const id of wanted) {
      if (typeof id !== "string" || !HEX_ID_RE.test(id)) throw new ApiError(400, "Unknown player");
      if (id !== me.id && !ids.includes(id)) ids.push(id);
    }
    if (ids.length === 0 || ids.length > MAX_VOICE - 1) throw new ApiError(400, "Pull between 1 and " + (MAX_VOICE - 1) + " players");

    // Only players who are in the caller's room, are not stale and have published can be pulled.
    const marks = ids.map(() => "?").join(", ");
    const found = await db
      .prepare(
        "SELECT user_id, push_session FROM voice_members WHERE room_id = ? AND last_seen > ? AND published = 1 " +
          "AND user_id IN (" + marks + ")",
      )
      .bind(row.room_id, ctx.now - VOICE_STALE_MS, ...ids)
      .all();
    const sessionOf = new Map((found.results || []).map((r) => [r.user_id, r.push_session]));
    const available = ids.filter((id) => sessionOf.has(id));
    const tracks = ids.filter((id) => !sessionOf.has(id)).map((id) => ({ member: id, mid: "", error: "not available" }));
    if (available.length === 0) return { offer: null, renegotiate: false, tracks };

    const result = await realtime(ctx, "POST", "/sessions/" + encodeURIComponent(row.pull_session) + "/tracks/new", {
      tracks: available.map((id) => ({ location: "remote", sessionId: sessionOf.get(id), trackName: TRACK })),
    });
    const returned = Array.isArray(result.tracks) ? result.tracks : [];
    available.forEach((id, index) => {
      // Cloudflare answers in request order; the session id it echoes back is the safer key when present.
      const match = returned.find((t) => t && t.sessionId === sessionOf.get(id)) || returned[index] || {};
      if (match.errorCode || typeof match.mid !== "string" || match.mid === "") {
        tracks.push({ member: id, mid: "", error: "could not be pulled" });
      } else {
        tracks.push({ member: id, mid: match.mid, error: "" });
      }
    });
    const description = result.sessionDescription;
    const offer = description && description.type === "offer" && typeof description.sdp === "string" ? description.sdp : null;
    const renegotiate = !!result.requiresImmediateRenegotiation;
    if (renegotiate && !offer) throw new ApiError(502, "The voice service did not send an offer");
    return { offer, renegotiate, tracks };
  },

  async renegotiate(db, body, ctx) {
    const me = await authenticate(db, body);
    const row = await requireVoiceMember(db, me.id);
    await hit(db, "vneg:" + me.id, 3_600_000, 300, ctx.now);
    const answer = cleanSdp(body.answer);
    await realtime(ctx, "PUT", "/sessions/" + encodeURIComponent(row.pull_session) + "/renegotiate", {
      sessionDescription: { type: "answer", sdp: answer },
    });
    return {};
  },

  async state(db, body, ctx) {
    const me = await authenticate(db, body);
    const row = await db.prepare("SELECT * FROM voice_members WHERE user_id = ?").bind(me.id).first();
    if (!row) return { inRoom: false, room: "", members: [] };
    // Polling is the heartbeat. The write is skipped unless the last one is old, so a poll every few seconds
    // costs a read, not a write, most of the time.
    await db
      .prepare("UPDATE voice_members SET last_seen = ? WHERE user_id = ? AND last_seen < ?")
      .bind(ctx.now, me.id, ctx.now - VOICE_TOUCH_MS)
      .run();
    return { inRoom: true, room: row.room_id, members: await roomMembers(db, row.room_id, ctx.now) };
  },

  async leave(db, body) {
    const me = await authenticate(db, body);
    await db.prepare("DELETE FROM voice_members WHERE user_id = ?").bind(me.id).run();
    return {};
  },
};

/** The whole API, separate from the Workers entry point so it can be tested with a fake D1 and a fake Cloudflare. */
export async function handle(request, env, now = Date.now(), deps = {}) {
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  const url = new URL(request.url);
  try {
    if (url.pathname === "/health") {
      return json(200, { ok: true, service: "vbv-friends", voice: voiceConfigured(env), turn: !!(env.TURN_KEY_ID && env.TURN_KEY_API_TOKEN) });
    }
    const match = /^\/(friends|voice)\/([a-z]+)$/.exec(url.pathname);
    const table = match ? (match[1] === "voice" ? voiceRoutes : routes) : null;
    const route = table && Object.prototype.hasOwnProperty.call(table, match[2]) ? table[match[2]] : null;
    if (!route) throw new ApiError(404, "Unknown endpoint");
    if (request.method !== "POST") throw new ApiError(405, "Use POST");
    if (!env.DB) throw new ApiError(500, "The D1 database is not bound to this Worker");
    const body = await readBody(request, match[1] === "voice" ? MAX_SDP_BODY : MAX_BODY);
    const ip = request.headers.get("cf-connecting-ip") || "unknown";
    const doFetch = deps.fetch || ((...args) => fetch(...args));
    const result = await route(env.DB, body, { now, ip, env, fetch: doFetch });
    return json(200, { ok: true, ...result });
  } catch (error) {
    if (error instanceof ApiError) return json(error.status, { ok: false, error: error.message });
    console.error("vbv-friends error", error && error.stack ? error.stack : error);
    return json(500, { ok: false, error: "Friends service error" });
  }
}

export default {
  fetch(request, env) {
    return handle(request, env);
  },
};
