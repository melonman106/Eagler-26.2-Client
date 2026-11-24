# vbv-friends

Cloudflare Worker + D1 backend for the Friends screen of the VBV client. See the main README ("Friends") for what
it does and how to deploy it with the manual workflow `06 - Deploy VBV Friends`.

```
src/index.js               the Worker (no dependencies); `handle()` is the whole API
migrations/0001_init.sql   users, friendships, rate-limit counters
test/                      node --test: the API against the real schema, and the browser bridges from
                           VBVFriends.java and VBVVoice.java against the API (D1 is stood in for by node:sqlite;
                           fake-cloudflare.mjs stands in for the Realtime SFU, and the browser's WebRTC is faked)
wrangler.toml              Worker + D1 binding (the database id is filled in by the workflow)
```

API (all `POST`, JSON, CORS open; `GET /health` for a check): `/friends/register`, `login`, `me`, `list`,
`request`, `accept`, `decline`, `remove`, `presence`, `delete`; and for voice chat `/voice/join`, `publish`, `pull`,
`renegotiate`, `state`, `leave` (they need the Realtime secrets below, and answer 503 "not set up" without them).
The header comment in `src/index.js` lists the request and response of each.

## Deploying by hand

```
cd cloudflare/vbv-friends
npx wrangler d1 create vbv-friends          # paste the database_id into wrangler.toml
npx wrangler d1 migrations apply vbv-friends --remote
npx wrangler deploy
```

## Voice secrets

Set as Worker secrets (the deploy workflow does it from repository secrets): `CF_REALTIME_APP_ID` and
`CF_REALTIME_APP_SECRET` (Realtime > SFU application), and optionally `TURN_KEY_ID` and `TURN_KEY_API_TOKEN`
(Realtime > TURN key). By hand: `npx wrangler secret put CF_REALTIME_APP_ID`, and so on. `GET /health` reports
`voice` and `turn` as true once they are set.

## Notes

- Only a SHA-256 hash of each account key is stored. Losing the key loses the account (the name stays taken).
- `last_seen` older than 90 seconds counts as offline; the client sends a heartbeat every 30 seconds.
- Schema changes go in a new numbered file in `migrations/`; never edit one that has been applied.
