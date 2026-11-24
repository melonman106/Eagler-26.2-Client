// Runs the voice bridge embedded in VBVVoice.java (the exact text, extracted from the source) together with the
// Friends bridge from VBVFriends.java, against the real Worker + schema and the fake Cloudflare Realtime SFU.
// The browser's WebRTC, microphone, audio output and Web Audio are simulated (see FakePC and friends below):
// this checks the bridge's logic and its protocol with the Worker, not real audio.
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createDb } from "./d1-shim.mjs";
import { handle } from "../src/index.js";
import { APP_ID, APP_SECRET, TURN_ID, TURN_TOKEN, OFFER, fakeCloudflare } from "./fake-cloudflare.mjs";

const DIR = new URL("../../../VBV/src/main/java/com/melonman106/vbvclient/", import.meta.url);

function textBlock(file, pattern) {
  const match = pattern.exec(readFileSync(file instanceof URL ? file : new URL(file, DIR), "utf8"));
  assert.ok(match, "initializeJS text block not found in " + file);
  return match[1].replace(/\\\\/g, "\\"); // Java text block: \\ -> \
}
const FRIENDS_JS = textBlock("VBVFriends.java", /@JSBody\(params = \{"api"\}, script = """\n([\s\S]*?)\n\s*"""\)/);
// VBV_VOICE_JAVA lets a mutation run (see below) point the tests at a deliberately broken copy of the bridge.
const VOICE_FILE = process.env.VBV_VOICE_JAVA ? new URL("file://" + process.env.VBV_VOICE_JAVA) : new URL("VBVVoice.java", DIR);
const VOICE_JS = textBlock(VOICE_FILE, /@JSBody\(script = """\n([\s\S]*?)\n\s*"""\)/);

const POLL_MS = 40;
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
async function waitFor(check, what, ms = 3000) {
  const end = Date.now() + ms;
  for (;;) {
    const value = await check();
    if (value) return value;
    if (Date.now() > end) assert.fail("timed out waiting for " + what);
    await wait(10);
  }
}

// ------------------------------------------------------------------------------------ simulated browser parts

class FakeTrack {
  constructor() { this.kind = "audio"; this.enabled = true; this.stopped = false; }
  stop() { this.stopped = true; }
}
class FakeStream {
  constructor(tracks = []) { this.tracks = tracks; this.loud = false; }
  getTracks() { return this.tracks; }
  getAudioTracks() { return this.tracks; }
}

function fakeBrowserApi(b) {
  class FakePC {
    constructor(config) {
      this.config = config;
      this.connectionState = "new";
      this.iceGatheringState = "complete";
      this.listeners = {};
      this.transceivers = [];
      this.localDescription = null;
      this.remoteDescription = null;
      this.closed = false;
      b.peers.push(this);
    }
    addEventListener(type, fn) { (this.listeners[type] ||= []).push(fn); }
    removeEventListener(type, fn) { this.listeners[type] = (this.listeners[type] || []).filter((f) => f !== fn); }
    emit(type, event = {}) { for (const fn of [...(this.listeners[type] || [])]) fn(event); }
    setState(state) { this.connectionState = state; this.emit("connectionstatechange"); }
    addTransceiver(track, init) {
      const tx = { mid: null, track, direction: init.direction, sender: { track } };
      this.transceivers.push(tx);
      return tx;
    }
    async createOffer() { return { type: "offer", sdp: OFFER }; }
    async createAnswer() { return { type: "answer", sdp: "v=0\r\nanswer-from-browser\r\n" }; }
    async setLocalDescription(desc) {
      if (desc.type === "offer") this.transceivers.forEach((tx, i) => { if (tx.mid == null) tx.mid = String(i); });
      this.localDescription = { type: desc.type, sdp: desc.sdp };
    }
    async setRemoteDescription(desc) {
      this.remoteDescription = desc;
      if (desc.type === "answer" && b.autoConnect) setTimeout(() => !this.closed && this.setState("connected"), 0);
      if (desc.type !== "offer") return;
      // A real browser reads the new tracks (their mids) out of the SDP and fires "track" for each.
      for (const m of desc.sdp.matchAll(/a=mid:(\S+)/g)) {
        if (this.transceivers.some((tx) => tx.mid === m[1])) continue;
        const track = new FakeTrack();
        const tx = { mid: m[1], track, receiver: { track } };
        this.transceivers.push(tx);
        this.emit("track", { track, transceiver: tx, streams: [new FakeStream([track])] });
      }
      if (b.autoConnect) setTimeout(() => !this.closed && this.setState("connected"), 0);
    }
    close() { this.closed = true; this.connectionState = "closed"; }
  }

  class FakeAudio {
    constructor() { this.paused = true; this.autoplay = false; this.srcObject = null; this.plays = 0; b.audios.push(this); }
    play() {
      this.plays++;
      if (b.blockAudio) return Promise.reject(Object.assign(new Error("blocked"), { name: "NotAllowedError" }));
      this.paused = false;
      return Promise.resolve();
    }
    pause() { this.paused = true; }
  }

  class FakeAudioContext {
    constructor() { this.state = "running"; this.closed = false; b.contexts.push(this); }
    createMediaStreamSource(media) {
      const source = { media, connect(node) { node.source = source; }, disconnect() {} };
      return source;
    }
    createAnalyser() {
      const analyser = {
        fftSize: 256, source: null,
        getByteTimeDomainData(data) {
          const loud = analyser.source && analyser.source.media && analyser.source.media.loud;
          for (let i = 0; i < data.length; i++) data[i] = loud ? (i % 2 ? 200 : 56) : 128;
        },
      };
      return analyser;
    }
    resume() { this.state = "running"; return Promise.resolve(); }
    close() { this.closed = true; }
  }

  return { FakePC, FakeAudio, FakeAudioContext };
}

// ---------------------------------------------------------------------------------------------------- the world

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
  const w = { env, cf, down: false, holds: new Map(), requests: [], done: [], browsers: [] };
  w.fetch = async (url, init = {}) => {
    if (w.down) throw new TypeError("offline");
    const path = new URL(url).pathname;
    w.requests.push({ path, init });
    const hold = w.holds.get(path);
    if (hold) await hold;
    const request = new Request(url, { method: init.method || "GET", headers: init.headers, body: init.body });
    const response = await handle(request, env, Date.now(), { fetch: cf.fetch });
    w.done.push(path);
    return response;
  };
  /** Holds every request to a path until the returned function is called. */
  w.hold = (path) => {
    let release;
    w.holds.set(path, new Promise((resolve) => (release = resolve)));
    return () => { w.holds.delete(path); release(); };
  };
  w.close = () => w.browsers.forEach((b) => b.close());
  return w;
}

/** One simulated browser tab with a Friends account's bridge and the voice bridge loaded. */
function browser(w, { webrtc = true, secure = true } = {}) {
  const b = {
    peers: [], audios: [], contexts: [], mics: [], micRequests: [], blockAudio: false, autoConnect: true,
    micDenied: false, micGate: null, stateGate: null, timers: new Set(), handlers: {},
  };
  const api = fakeBrowserApi(b);
  const storage = new Map();
  const unref = (t) => { t.unref && t.unref(); return t; };
  const window = {
    VBV_VOICE_POLL_MS: POLL_MS,
    VBV_VOICE_GATHER_MS: 50,
    isSecureContext: secure,
    setInterval: (fn, ms) => { const t = unref(setInterval(fn, ms)); b.timers.add(t); return t; },
    clearInterval: (t) => { clearInterval(t); b.timers.delete(t); },
    setTimeout: (fn, ms) => { const t = unref(setTimeout(fn, ms)); b.timers.add(t); return t; },
    clearTimeout: (t) => { clearTimeout(t); b.timers.delete(t); },
    addEventListener: (type, fn) => { (b.handlers[type] ||= []).push(fn); },
    Audio: api.FakeAudio,
    MediaStream: FakeStream,
    AudioContext: api.FakeAudioContext,
    navigator: {
      mediaDevices: {
        getUserMedia: async (constraints) => {
          b.micRequests.push(constraints);
          if (b.micGate) await b.micGate;
          if (b.micDenied) throw Object.assign(new Error("Permission denied"), { name: "NotAllowedError" });
          const stream = new FakeStream([new FakeTrack()]);
          b.mics.push(stream);
          return stream;
        },
      },
    },
  };
  if (webrtc) window.RTCPeerConnection = api.FakePC;
  const localStorage = {
    getItem: (k) => (storage.has(k) ? storage.get(k) : null),
    setItem: (k, v) => storage.set(k, String(v)),
    removeItem: (k) => storage.delete(k),
  };
  const fakeNavigator = { clipboard: { writeText: async () => {} } };
  // Lets a test freeze this browser's polling of the room (b.stateGate = a promise) while others change it.
  const fetchHere = async (url, init) => {
    if (b.stateGate && new URL(url).pathname === "/voice/state") await b.stateGate;
    return w.fetch(url, init);
  };
  new Function("window", "localStorage", "fetch", "navigator", "api", FRIENDS_JS)(
    window, localStorage, fetchHere, fakeNavigator, "https://friends.test");
  new Function("window", "fetch", VOICE_JS)(window, fetchHere);

  b.window = window;
  b.friends = window.VBVFriendsBridge;
  b.voice = window.VBVVoiceBridge;
  assert.ok(b.voice && b.voice.initialized);
  b.close = () => {
    for (const t of b.timers) { clearInterval(t); clearTimeout(t); }
    b.timers.clear();
  };
  b.fire = (type) => (b.handlers[type] || []).forEach((fn) => fn({}));
  b.phase = () => b.voice.get("phase");
  b.members = () => (b.voice.rows("members") ? b.voice.rows("members").split("\n").map((r) => r.split("\t")) : []);
  b.mic = () => b.mics[b.mics.length - 1];
  b.act = async (name, a, c) => {
    b.friends.call(name, a, c);
    for (let i = 0; i < 400 && b.friends.get("busy") === "1"; i++) await wait(5);
    await wait(5);
  };
  b.join = (friendId = "") => b.voice.call("join", friendId);
  b.live = () => waitFor(() => b.phase() === "live", b.friends.get("name") + " to be live");
  w.browsers.push(b);
  return b;
}

async function player(w, name, options) {
  const b = browser(w, options);
  await b.act("register", name);
  assert.equal(b.friends.get("signedIn"), "1");
  return b;
}
async function befriend(a, b) {
  await a.act("request", b.friends.get("name"));
  await b.act("refresh");
  const incoming = b.friends.rows("incoming").split("\n")[0].split("\t");
  await b.act("accept", incoming[0]);
  await a.act("refresh");
}
async function friendsInVoice(listener, friendName) {
  // The Friends list is where a JOIN button comes from: wait until it reports that friend as in a voice chat.
  return waitFor(async () => {
    await listener.act("refresh");
    const row = listener.friends.rows("friends").split("\n").map((r) => r.split("\t")).find((r) => r[1] === friendName);
    return row && Number(row[4]) > 0 ? row : null;
  }, friendName + " to show as in a voice chat");
}
const voiceRows = (w) => w.env.DB.prepare("SELECT * FROM voice_members").all().then((r) => r.results || []);

// ---------------------------------------------------------------------------------------------------- tests

test("starting a call: asks for the microphone, joins, publishes it and is live", async () => {
  const w = world();
  const alice = await player(w, "Alice");
  assert.equal(alice.phase(), "idle");
  alice.join();
  assert.equal(alice.phase(), "starting");
  assert.match(alice.voice.get("message"), /microphone/i);
  await alice.live();

  assert.equal(alice.micRequests.length, 1);
  assert.deepEqual(alice.micRequests[0].video, false);
  assert.equal(alice.micRequests[0].audio.echoCancellation, true);
  assert.deepEqual(alice.members().map((r) => [r[1], r[3]]), [["Alice", "you"]]);
  assert.match(alice.voice.get("room"), /\S/);
  assert.equal(alice.voice.get("message"), "");

  // two connections: one sends the microphone, one is for listening
  assert.equal(alice.peers.length, 2);
  const [push, pull] = alice.peers;
  assert.equal(push.transceivers.length, 1);
  assert.equal(push.transceivers[0].direction, "sendonly");
  assert.equal(push.transceivers[0].track, alice.mic().getAudioTracks()[0]);
  assert.equal(push.remoteDescription.type, "answer");
  assert.ok(Array.isArray(push.config.iceServers) && push.config.iceServers.length > 0);
  assert.equal(push.config.bundlePolicy, "max-bundle");
  assert.equal(pull.transceivers.length, 0);

  const published = w.cf.callsTo("/tracks/new")[0].body;
  assert.equal(published.tracks[0].location, "local");
  assert.equal(published.tracks[0].mid, "0", "the mid the browser assigned to the microphone is what the service is told");
  assert.equal(published.sessionDescription.sdp, OFFER);
  assert.equal((await voiceRows(w)).length, 1);
  w.close();
});

test("two friends hear each other; the join button's room is the one that is joined", async () => {
  const w = world();
  const alice = await player(w, "Alice");
  const bob = await player(w, "Bob");
  await befriend(alice, bob);

  alice.join();
  await alice.live();
  const row = await friendsInVoice(bob, "Alice");
  assert.equal(row[4], "1", "one person in the room");
  assert.equal(row[5], alice.voice.get("room"));

  bob.join(row[0]);
  await bob.live();
  assert.equal(bob.voice.get("room"), alice.voice.get("room"));

  await waitFor(() => alice.audios.length === 1 && bob.audios.length === 1, "both to hear the other");
  for (const [listener, name] of [[alice, "Bob"], [bob, "Alice"]]) {
    const audio = listener.audios[0];
    assert.equal(audio.autoplay, true);
    assert.ok(audio.srcObject, "the remote microphone is attached");
    assert.equal(audio.paused, false, "and playing");
    const rows = listener.members();
    assert.deepEqual(rows.map((r) => r[1]).sort(), ["Alice", "Bob"]);
    assert.equal(rows.find((r) => r[1] === name)[3], "live");
  }
  // the listening connection answered the service's offer
  // (the sound starts when the offer is applied, a moment before the answer has been sent back)
  const listening = alice.peers[1];
  assert.equal(listening.remoteDescription.type, "offer");
  await waitFor(() => listening.localDescription && w.cf.callsTo("/renegotiate").length >= 2, "both answers to reach the service");
  assert.equal(listening.localDescription.type, "answer");
  w.close();
});

test("three friends: each hears the other two once, even across several polls", async () => {
  const w = world();
  const [a, b, c] = [await player(w, "Alice"), await player(w, "Bob"), await player(w, "Carol")];
  await befriend(a, b);
  await befriend(a, c);
  await befriend(b, c);
  a.join();
  await a.live();
  const roomRow = await friendsInVoice(b, "Alice");
  b.join(roomRow[0]);
  await b.live();
  const roomRowC = await friendsInVoice(c, "Alice");
  c.join(roomRowC[0]);
  await c.live();

  await waitFor(() => [a, b, c].every((x) => x.audios.length === 2), "everyone to hear two others");
  await wait(POLL_MS * 6); // more polls: nobody is pulled twice
  for (const x of [a, b, c]) {
    assert.equal(x.audios.length, 2);
    assert.equal(x.members().length, 3);
  }
  w.close();
});

test("mute silences the microphone track but keeps the call", async () => {
  const w = world();
  const alice = await player(w, "Alice");
  alice.voice.call("toggleMute");
  assert.equal(alice.voice.get("muted"), "0", "nothing to mute outside a call");
  alice.join();
  await alice.live();
  const track = alice.mic().getAudioTracks()[0];
  assert.equal(track.enabled, true);
  const before = alice.voice.version();
  alice.voice.call("toggleMute");
  assert.equal(alice.voice.get("muted"), "1");
  assert.equal(track.enabled, false);
  assert.ok(alice.voice.version() > before, "the screen is told to redraw");
  assert.equal(alice.phase(), "live");
  alice.voice.call("toggleMute");
  assert.equal(track.enabled, true);
  assert.equal(alice.voice.get("muted"), "0");
  w.close();
});

test("speaking indicators follow the audio level, and a muted microphone never shows as speaking", async () => {
  const w = world();
  const alice = await player(w, "Alice");
  const bob = await player(w, "Bob");
  await befriend(alice, bob);
  alice.join();
  await alice.live();
  bob.join((await friendsInVoice(bob, "Alice"))[0]);
  await bob.live();
  await waitFor(() => alice.audios.length === 1, "Alice to hear Bob");

  const speakingOf = (viewer, name) => viewer.members().find((r) => r[1] === name)[2];
  assert.equal(speakingOf(alice, "Bob"), "0");
  alice.audios[0].srcObject.loud = true;
  await waitFor(() => speakingOf(alice, "Bob") === "1", "Bob to show as speaking");
  const version = alice.voice.version();
  await wait(POLL_MS * 3);
  assert.equal(alice.voice.version(), version, "who is speaking does not rebuild the screen");
  alice.audios[0].srcObject.loud = false;
  await waitFor(() => speakingOf(alice, "Bob") === "0", "Bob to stop showing as speaking");

  alice.mic().loud = true;
  await waitFor(() => speakingOf(alice, "Alice") === "1", "Alice to show as speaking");
  alice.voice.call("toggleMute");
  await waitFor(() => speakingOf(alice, "Alice") === "0", "a muted Alice to stop showing as speaking");
  w.close();
});

test("leaving stops the microphone, closes the connections, silences others and frees the room", async () => {
  const w = world();
  const alice = await player(w, "Alice");
  const bob = await player(w, "Bob");
  await befriend(alice, bob);
  alice.join();
  await alice.live();
  bob.join((await friendsInVoice(bob, "Alice"))[0]);
  await bob.live();
  await waitFor(() => alice.audios.length === 1 && bob.audios.length === 1, "both to hear");

  alice.voice.call("leave");
  assert.equal(alice.phase(), "idle");
  assert.ok(alice.peers.every((pc) => pc.closed));
  assert.ok(alice.mic().getTracks().every((t) => t.stopped));
  assert.ok(alice.audios.every((a) => a.paused && a.srcObject === null));
  assert.deepEqual(alice.members(), []);
  assert.match(alice.voice.get("message"), /left/i);

  // Bob notices on a poll: Alice's sound is dropped, Bob stays in the room
  await waitFor(() => bob.members().length === 1, "Bob's list to drop Alice");
  assert.ok(bob.audios[0].paused && bob.audios[0].srcObject === null);
  assert.equal(bob.phase(), "live");
  assert.equal((await voiceRows(w)).length, 1);
  w.close();
});

test("a friend who leaves and joins again is heard again, once", async () => {
  const w = world();
  const alice = await player(w, "Alice");
  const bob = await player(w, "Bob");
  await befriend(alice, bob);
  alice.join();
  await alice.live();
  bob.join((await friendsInVoice(bob, "Alice"))[0]);
  await bob.live();
  await waitFor(() => alice.audios.length === 1, "Alice to hear Bob");
  const first = alice.audios[0];

  bob.voice.call("leave");
  await waitFor(() => alice.members().length === 1 && first.paused, "Alice to drop Bob");
  bob.join((await friendsInVoice(bob, "Alice"))[0]);
  await bob.live();
  await waitFor(() => alice.audios.length === 2, "Alice to hear Bob's new microphone");
  assert.equal(first.paused, true, "the old sound stays stopped");
  assert.equal(alice.audios[1].paused, false);
  assert.equal(alice.members().find((r) => r[1] === "Bob")[3], "live");
  await wait(POLL_MS * 5);
  assert.equal(alice.audios.length, 2);
  w.close();
});

test("a friend who rejoins between two polls is still heard on their new microphone", async () => {
  const w = world();
  const alice = await player(w, "Alice");
  const bob = await player(w, "Bob");
  await befriend(alice, bob);
  alice.join();
  await alice.live();
  bob.join((await friendsInVoice(bob, "Alice"))[0]);
  await bob.live();
  await waitFor(() => alice.audios.length === 1, "Alice to hear Bob");
  const first = alice.audios[0];

  // Alice's polling is frozen while Bob leaves and comes back, so she never sees him missing from the room.
  let thaw;
  alice.stateGate = new Promise((resolve) => (thaw = resolve));
  await wait(POLL_MS * 2);
  bob.voice.call("leave");
  await waitFor(async () => (await voiceRows(w)).length === 1, "Bob's row to go");
  bob.join((await friendsInVoice(bob, "Alice"))[0]);
  await bob.live();
  thaw();
  alice.stateGate = null;
  await waitFor(() => alice.audios.length === 2, "Alice to hear Bob's new microphone");
  assert.equal(first.paused, true, "the old sound was dropped");
  assert.equal(alice.audios[1].paused, false);
  w.close();
});

test("a blocked microphone says so and never joins the room", async () => {
  const w = world();
  const alice = await player(w, "Alice");
  alice.micDenied = true;
  alice.join();
  await waitFor(() => alice.phase() === "idle" && alice.voice.get("message"), "the refusal");
  assert.match(alice.voice.get("message"), /microphone is blocked/i);
  assert.equal(w.cf.callsTo("/sessions/new").length, 0, "no sessions were created at the service");
  assert.equal((await voiceRows(w)).length, 0);
  assert.equal(alice.peers.length, 0);
  w.close();
});

test("the service not being set up is reported in the Worker's words, and the microphone is released", async () => {
  const w = world({ voice: false });
  const alice = await player(w, "Alice");
  alice.join();
  await waitFor(() => alice.phase() === "idle" && alice.voice.get("message"), "the error");
  assert.match(alice.voice.get("message"), /not (set up|configured)|voice/i);
  assert.ok(alice.mic().getTracks().every((t) => t.stopped), "the microphone light goes off");
  assert.match(alice.voice.get("notice"), /\S/, "the in-game indicator can show it");
  w.close();
});

test("joining a friend who is not in a voice chat is refused clearly", async () => {
  const w = world();
  const alice = await player(w, "Alice");
  const bob = await player(w, "Bob");
  await befriend(alice, bob);
  const bobId = bob.friends.get("id");
  alice.join(bobId);
  await waitFor(() => alice.phase() === "idle" && alice.voice.get("message"), "the refusal");
  assert.match(alice.voice.get("message"), /not in a voice chat/i);
  assert.ok(alice.mic().getTracks().every((t) => t.stopped));
  w.close();
});

test("leaving while the microphone prompt is open ends the attempt and releases the microphone", async () => {
  const w = world();
  const alice = await player(w, "Alice");
  let allow;
  alice.micGate = new Promise((resolve) => (allow = resolve));
  alice.join();
  assert.equal(alice.phase(), "starting");
  alice.voice.call("leave");
  assert.equal(alice.phase(), "idle");
  allow();
  await waitFor(() => alice.mics.length === 1, "the microphone to arrive");
  await waitFor(() => alice.mic().getTracks().every((t) => t.stopped), "the late microphone to be released");
  assert.equal(w.cf.callsTo("/sessions/new").length, 0);
  assert.equal(alice.phase(), "idle");
  w.close();
});

test("leaving while the join request is in flight does not leave a ghost in the room", async () => {
  const w = world();
  const alice = await player(w, "Alice");
  const release = w.hold("/voice/join");
  alice.join();
  await waitFor(() => w.requests.some((r) => r.path === "/voice/join"), "the join request to be sent");
  alice.voice.call("leave");
  assert.equal(alice.phase(), "idle");
  const leavesBefore = w.requests.filter((r) => r.path === "/voice/leave").length;
  release();
  await waitFor(() => w.done.includes("/voice/join"), "the late join to reach the service");
  // The join created a row after the player had already left; the bridge must notice and undo it.
  await waitFor(async () => w.requests.filter((r) => r.path === "/voice/leave").length === leavesBefore + 1 &&
    w.done.filter((p) => p === "/voice/leave").length === leavesBefore + 1, "a second leave after the late join");
  assert.equal((await voiceRows(w)).length, 0);
  assert.equal(alice.phase(), "idle");
  assert.equal(alice.peers.length, 0, "no connections were opened for the cancelled call");
  w.close();
});

test("a member who has joined but not published yet shows as waiting, then live", async () => {
  const w = world();
  const alice = await player(w, "Alice");
  const bob = await player(w, "Bob");
  await befriend(alice, bob);
  alice.join();
  await alice.live();
  const row = await friendsInVoice(bob, "Alice");
  const release = w.hold("/voice/publish");
  const before = alice.voice.version();
  bob.join(row[0]);
  await waitFor(() => alice.members().some((r) => r[1] === "Bob"), "Bob to appear in Alice's list");
  assert.equal(alice.members().find((r) => r[1] === "Bob")[3], "waiting");
  assert.ok(alice.voice.version() > before, "a new member redraws the screen even before they can be heard");
  assert.equal(alice.audios.length, 0);
  release();
  await waitFor(() => alice.audios.length === 1, "Alice to hear Bob once he publishes");
  assert.equal(alice.members().find((r) => r[1] === "Bob")[3], "live");
  w.close();
});

test("a failed connection ends the call with a hint about school networks, and leaves the room", async () => {
  const w = world();
  const alice = await player(w, "Alice");
  alice.join();
  await alice.live();
  alice.peers[0].setState("failed");
  assert.equal(alice.phase(), "idle");
  assert.match(alice.voice.get("message"), /TURN/);
  assert.match(alice.voice.get("notice"), /TURN/);
  assert.ok(alice.mic().getTracks().every((t) => t.stopped));
  await waitFor(async () => (await voiceRows(w)).length === 0, "the server side to be cleared");
  w.close();
});

test("a connection that drops briefly recovers without ending the call", async () => {
  const w = world();
  const alice = await player(w, "Alice");
  alice.join();
  await alice.live();
  alice.peers[0].setState("disconnected");
  assert.match(alice.voice.get("message"), /unstable/i);
  assert.equal(alice.phase(), "live");
  alice.peers[0].setState("connected");
  assert.equal(alice.voice.get("message"), "");
  assert.equal(alice.phase(), "live");
  w.close();
});

test("losing the network ends the call after a few missed polls", async () => {
  const w = world();
  const alice = await player(w, "Alice");
  alice.join();
  await alice.live();
  w.down = true;
  await waitFor(() => alice.phase() === "idle", "the call to end", 4000);
  assert.match(alice.voice.get("message"), /not reachable|stopped answering/i);
  w.down = false;
  w.close();
});

test("being dropped by the service (stale row) ends the call", async () => {
  const w = world();
  const alice = await player(w, "Alice");
  alice.join();
  await alice.live();
  await w.env.DB.prepare("DELETE FROM voice_members").run();
  await waitFor(() => alice.phase() === "idle", "the call to end");
  assert.match(alice.voice.get("message"), /removed|no longer in the voice chat/i);
  assert.ok(alice.peers.every((pc) => pc.closed));
  w.close();
});

test("a failed pull is retried on the next poll and the friend is heard", async () => {
  const w = world();
  const alice = await player(w, "Alice");
  const bob = await player(w, "Bob");
  await befriend(alice, bob);
  alice.join();
  await alice.live();
  const row = await friendsInVoice(bob, "Alice");
  // Alice's next call to the SFU is her pull of Bob, once Bob has published; fail that one.
  bob.join(row[0]);
  await bob.live();
  w.cf.failNext(500, { errorCode: "boom" });
  await waitFor(() => alice.audios.length === 1, "Alice to hear Bob after a retry", 4000);
  assert.equal(alice.phase(), "live");
  w.close();
});

test("sound that the browser blocks starts on the next click", async () => {
  const w = world();
  const alice = await player(w, "Alice");
  const bob = await player(w, "Bob");
  await befriend(alice, bob);
  alice.blockAudio = true;
  alice.join();
  await alice.live();
  bob.join((await friendsInVoice(bob, "Alice"))[0]);
  await bob.live();
  await waitFor(() => alice.audios.length === 1 && alice.audios[0].plays >= 1, "Alice's audio to try playing");
  assert.equal(alice.audios[0].paused, true);
  alice.blockAudio = false;
  alice.fire("pointerdown");
  assert.equal(alice.audios[0].paused, false);
  w.close();
});

test("closing the page leaves the voice chat right away", async () => {
  const w = world();
  const alice = await player(w, "Alice");
  alice.join();
  await alice.live();
  assert.equal((await voiceRows(w)).length, 1);
  alice.fire("pagehide");
  await waitFor(async () => (await voiceRows(w)).length === 0, "the room to be freed");
  const leave = w.requests.filter((r) => r.path === "/voice/leave").pop();
  assert.equal(leave.init.keepalive, true);
  w.close();
});

test("signing out or deleting the account ends the call", async () => {
  const w = world();
  const alice = await player(w, "Alice");
  alice.join();
  await alice.live();
  alice.friends.call("signOut");
  assert.equal(alice.phase(), "idle");
  assert.ok(alice.peers.every((pc) => pc.closed));
  assert.ok(alice.mic().getTracks().every((t) => t.stopped));
  w.close();
});

test("without an account, WebRTC or a secure page the call does not start and says why", async () => {
  const w = world();
  const anon = browser(w);
  anon.join();
  assert.equal(anon.phase(), "idle");
  assert.match(anon.voice.get("message"), /Friends account/);

  const old = await player(w, "Olive", { webrtc: false });
  old.join();
  assert.equal(old.phase(), "idle");
  assert.match(old.voice.get("message"), /cannot do voice chat/);

  const http = await player(w, "Hettie", { webrtc: false, secure: false });
  http.join();
  assert.match(http.voice.get("message"), /secure \(https\) page/);
  assert.equal(old.micRequests.length + http.micRequests.length, 0);
  w.close();
});

test("a second join while a call is running is ignored", async () => {
  const w = world();
  const alice = await player(w, "Alice");
  alice.join();
  alice.join();
  await alice.live();
  alice.join();
  await wait(POLL_MS * 2);
  assert.equal(alice.micRequests.length, 1);
  assert.equal(alice.peers.length, 2);
  assert.equal(w.cf.callsTo("/sessions/new").length, 2);
  w.close();
});

test("TURN servers from the Worker reach both connections", async () => {
  const w = world({ turn: true });
  const alice = await player(w, "Alice");
  alice.join();
  await alice.live();
  for (const pc of alice.peers) {
    assert.ok(pc.config.iceServers.some((s) => s.username === "u" && s.credential === "c"), "TURN credentials are passed on");
  }
  w.close();
});

test("the bridge is created once, and member rows are tab-separated for the Java side", async () => {
  const w = world();
  const alice = await player(w, "Alice");
  const first = alice.window.VBVVoiceBridge;
  new Function("window", "fetch", VOICE_JS)(alice.window, w.fetch);
  assert.equal(alice.window.VBVVoiceBridge, first, "a second initialize keeps the running bridge");
  alice.join();
  await alice.live();
  const raw = alice.voice.rows("members");
  assert.match(raw, /^[0-9a-f]+\tAlice\t[01]\tyou$/);
  assert.equal(alice.voice.rows("other"), "");
  assert.equal(alice.voice.get("nothing"), "");
  w.close();
});

test("the source has no long values at the Java boundary and only the expected calls into Java", () => {
  const java = readFileSync(new URL("VBVVoice.java", DIR), "utf8");
  assert.doesNotMatch(java, /@JSBody[^]*?\b(long|Long)\b[^;]*native/);
  assert.equal((java.match(/@JSBody/g) || []).length, 5);
  assert.ok(APP_ID && APP_SECRET && TURN_ID && TURN_TOKEN);
});
