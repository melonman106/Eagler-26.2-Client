-- VBV Friends: accounts, friendships and a small rate-limit table.
-- Accounts are anonymous: a random secret token is created in the player's browser and only its SHA-256
-- hash is stored here. A name is first come, first served (Eaglercraft has no account system to check it).

CREATE TABLE IF NOT EXISTS users (
  id          TEXT PRIMARY KEY,               -- 32 random hex characters
  name        TEXT NOT NULL,                  -- as typed, 3-16 of A-Z a-z 0-9 _
  name_lower  TEXT NOT NULL UNIQUE,           -- lower case, for lookups and uniqueness
  token_hash  TEXT NOT NULL,                  -- SHA-256 (hex) of the secret token
  created_at  INTEGER NOT NULL,               -- unix ms
  last_seen   INTEGER NOT NULL DEFAULT 0,     -- unix ms of the last presence heartbeat
  server      TEXT NOT NULL DEFAULT '',       -- what the player is on right now ('' = menus)
  hidden      INTEGER NOT NULL DEFAULT 0      -- 1 = appear offline to friends
);

-- One row per pair. user_a < user_b always, so a pair can never be stored twice.
CREATE TABLE IF NOT EXISTS friendships (
  user_a      TEXT NOT NULL,
  user_b      TEXT NOT NULL,
  status      TEXT NOT NULL CHECK (status IN ('pending', 'accepted')),
  requester   TEXT NOT NULL,                  -- who sent the request (user_a or user_b)
  created_at  INTEGER NOT NULL,
  PRIMARY KEY (user_a, user_b)
);
CREATE INDEX IF NOT EXISTS friendships_by_b ON friendships (user_b);

-- Fixed-window counters (key + window start) so registering and requests cannot be spammed.
CREATE TABLE IF NOT EXISTS rate (
  key     TEXT NOT NULL,
  window  INTEGER NOT NULL,
  n       INTEGER NOT NULL,
  PRIMARY KEY (key, window)
);
