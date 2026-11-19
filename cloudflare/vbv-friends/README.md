# vbv-friends

Cloudflare Worker + D1 backend for the Friends screen of the VBV client. See the main README ("Friends") for what
it does and how to deploy it with the manual workflow `06 - Deploy VBV Friends`.

```
src/index.js               the Worker (no dependencies); `handle()` is the whole API
migrations/0001_init.sql   users, friendships, rate-limit counters
test/                      node --test: the API against the real schema, and the browser bridge from
                           VBVFriends.java against the API (D1 is stood in for by node:sqlite)
wrangler.toml              Worker + D1 binding (the database id is filled in by the workflow)
```

API (all `POST`, JSON, CORS open; `GET /health` for a check): `/friends/register`, `login`, `me`, `list`,
`request`, `accept`, `decline`, `remove`, `presence`, `delete`. The header comment in `src/index.js` lists the
request and response of each.

## Deploying by hand

```
cd cloudflare/vbv-friends
npx wrangler d1 create vbv-friends          # paste the database_id into wrangler.toml
npx wrangler d1 migrations apply vbv-friends --remote
npx wrangler deploy
```

## Notes

- Only a SHA-256 hash of each account key is stored. Losing the key loses the account (the name stays taken).
- `last_seen` older than 90 seconds counts as offline; the client sends a heartbeat every 30 seconds.
- Schema changes go in a new numbered file in `migrations/`; never edit one that has been applied.
