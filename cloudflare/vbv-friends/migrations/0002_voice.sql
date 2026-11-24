-- VBV Friends: voice chat (Cloudflare Realtime SFU).
-- One row per player who is in a voice chat right now. The row ties a player to a voice room and to the two
-- Realtime sessions the Worker created for them: one the player publishes their microphone on, one they pull
-- everyone else's microphones from. The Realtime session ids never leave the Worker, so a client can only ever
-- pull the tracks of players who are in the same room as it.
-- user_id is the primary key, so a player can only be in one voice chat at a time.

CREATE TABLE IF NOT EXISTS voice_members (
  user_id       TEXT PRIMARY KEY,
  room_id       TEXT NOT NULL,              -- 16 random hex characters, shared by everyone in the call
  push_session  TEXT NOT NULL,              -- Realtime session the microphone is published on
  pull_session  TEXT NOT NULL,              -- Realtime session the other microphones are pulled on
  published     INTEGER NOT NULL DEFAULT 0, -- 1 once the microphone track exists and can be pulled
  joined_at     INTEGER NOT NULL,           -- unix ms; changes on every (re)join, so clients can tell a rejoin
  last_seen     INTEGER NOT NULL            -- unix ms of the last state poll; stale rows are ignored
);
CREATE INDEX IF NOT EXISTS voice_members_by_room ON voice_members (room_id);
