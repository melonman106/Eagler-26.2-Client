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
// Errors are { ok: false, error } with a readable message and a matching HTTP status.

export const ONLINE_MS = 90_000;
export const MAX_FRIENDS = 100;
export const MAX_PENDING = 50;
const NAME_RE = /^[A-Za-z0-9_]{3,16}$/;
const HEX_ID_RE = /^[0-9a-f]{32}$/;
const HEX_TOKEN_RE = /^[0-9a-f]{64}$/;
const MAX_BODY = 4096;

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

async function readBody(request) {
  const text = await request.text();
  if (text.length > MAX_BODY) throw new ApiError(413, "Request is too large");
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
    const friends = [];
    const incoming = [];
    const outgoing = [];
    for (const row of rows.results || []) {
      if (row.status === "accepted") {
        const online = !row.hidden && ctx.now - row.last_seen < ONLINE_MS;
        friends.push({
          id: row.id,
          name: row.name,
          online,
          server: online ? row.server : "",
          lastSeen: row.hidden ? 0 : row.last_seen,
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
    await db.prepare("DELETE FROM users WHERE id = ?").bind(me.id).run();
    return {};
  },
};

/** The whole API, separate from the Workers entry point so it can be tested with a fake D1. */
export async function handle(request, env, now = Date.now()) {
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
  const url = new URL(request.url);
  try {
    if (url.pathname === "/health") return json(200, { ok: true, service: "vbv-friends" });
    const match = /^\/friends\/([a-z]+)$/.exec(url.pathname);
    const route = match && Object.prototype.hasOwnProperty.call(routes, match[1]) ? routes[match[1]] : null;
    if (!route) throw new ApiError(404, "Unknown endpoint");
    if (request.method !== "POST") throw new ApiError(405, "Use POST");
    if (!env.DB) throw new ApiError(500, "The D1 database is not bound to this Worker");
    const body = await readBody(request);
    const ip = request.headers.get("cf-connecting-ip") || "unknown";
    const result = await route(env.DB, body, { now, ip });
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
