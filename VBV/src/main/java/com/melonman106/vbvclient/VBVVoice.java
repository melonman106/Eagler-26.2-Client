package com.melonman106.vbvclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.teavm.jso.JSBody;

/**
 * Voice chat for friends. The audio itself never goes through this class: a small browser-side bridge
 * ({@code window.VBVVoiceBridge}) opens the microphone, sends it to the Cloudflare Realtime SFU and plays the
 * microphones of the other people in the room. The bridge only talks to the VBV Friends Worker (through
 * {@link VBVFriends}'s signed-in session); the Worker is the one that holds the Realtime app secret.
 *
 * <p>This class is the Java face of that bridge: it starts and ends calls, mutes the microphone and reads the
 * room's members for the Friends screen and the in-game indicator. Like {@link VBVFriends} it keeps no audio
 * or network state of its own, so it costs nothing until a player starts a call.</p>
 */
public final class VBVVoice {
    private static boolean initialized;

    // Read once per client tick (not per frame) so the in-game indicator costs no interop calls while drawing.
    private static boolean live;
    private static boolean muted;
    private static String notice = "";
    private static String[] speakers = new String[0];
    private static int memberCount;

    private VBVVoice() {}

    /** Safe to call often; the bridge is created once. Needs the Friends bridge, which is created first. */
    public static void initialize() {
        VBVFriends.initialize();
        if (initialized) return;
        initialized = true;
        initializeJS();
    }

    /** Starts a new voice room that friends can join from their Friends screen. */
    public static void start() {
        initialize();
        callJS("join", "");
    }

    /** Joins the voice room a friend is in. */
    public static void join(String friendId) {
        initialize();
        callJS("join", friendId == null ? "" : friendId);
    }

    public static void leave() { if (initialized) callJS("leave", ""); }
    public static void toggleMute() { if (initialized) callJS("toggleMute", ""); }

    /** "idle", "starting" (asking for the microphone and joining) or "live". */
    public static String phase() { return initialized ? getJS("phase") : "idle"; }
    public static boolean inCall() { return !"idle".equals(phase()); }
    public static boolean isMuted() { return initialized && "1".equals(getJS("muted")); }
    /** The last thing worth telling the player, such as why the call ended. Empty when there is nothing. */
    public static String message() { return initialized ? getJS("message") : ""; }
    public static String room() { return initialized ? getJS("room") : ""; }
    /** Bumps whenever the call's structure changes (not for who is speaking, which changes constantly). */
    public static int version() { return initialized ? versionJS() : 0; }

    /**
     * The people in the room, one per row: {@code id TAB name TAB speaking(1/0) TAB state} where state is
     * {@code you}, {@code live}, {@code connecting} (their microphone is being fetched) or {@code waiting}
     * (they joined but have not started their microphone yet).
     */
    public static String[] memberRows() {
        if (!initialized) return new String[0];
        String raw = rowsJS("members");
        return raw == null || raw.isEmpty() ? new String[0] : raw.split("\\n");
    }

    // ----------------------------------------------------------------------- in-game indicator

    /** Called every client tick. */
    public static void tick(Minecraft mc) {
        if (!initialized) return;
        String phase = getJS("phase");
        live = "live".equals(phase);
        if (!live) {
            muted = false;
            memberCount = 0;
            speakers = new String[0];
            notice = getJS("notice");
            return;
        }
        muted = "1".equals(getJS("muted"));
        notice = getJS("notice");
        String[] rows = memberRows();
        memberCount = rows.length;
        int n = 0;
        String[] names = new String[rows.length];
        for (String row : rows) {
            String[] f = row.split("\\t", -1);
            if (f.length > 3 && "1".equals(f[2]) && !"you".equals(f[3])) names[n++] = f[1];
        }
        speakers = new String[n];
        System.arraycopy(names, 0, speakers, 0, n);
    }

    /**
     * A small panel on the right edge while a call is running: whether the microphone is muted, the mute key
     * and who is speaking. After a call ends by itself it shows why for a few seconds (the bridge clears the
     * notice), so a player who is looking at the game and not at the Friends screen still finds out.
     */
    public static void render(GuiGraphicsExtractor graphics) {
        if (!initialized || (!live && notice.isEmpty())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.font == null) return;
        Font font = mc.font;
        int maxWidth = Math.min(180, graphics.guiWidth() / 3);
        java.util.List<String> lines = new java.util.ArrayList<>();
        java.util.List<Integer> colors = new java.util.ArrayList<>();
        if (live) {
            String key = VBVKeybinds.keyName(VBVKeybinds.code(VBVKeybinds.VOICE));
            lines.add((muted ? "MIC MUTED" : "VOICE") + " (" + memberCount + ")  [" + key + "]");
            colors.add(muted ? 0xFFFF6B6B : 0xFFFFFFFF);
            for (String name : speakers) {
                lines.add("> " + name);
                colors.add(0xFF63E08A);
            }
        } else {
            lines.add("VOICE: " + notice);
            colors.add(0xFFFF6B6B);
        }
        int lineH = font.lineHeight + 2;
        int widest = 0;
        for (int i = 0; i < lines.size(); i++) {
            lines.set(i, VBVUi.fit(font, lines.get(i), maxWidth));
            widest = Math.max(widest, font.width(lines.get(i)));
        }
        int w = widest + 10;
        int h = lines.size() * lineH + 4;
        int x = graphics.guiWidth() - w - 6;
        int y = graphics.guiHeight() / 2 - h / 2;
        graphics.fill(x, y, x + w, y + h, VBVTheme.panel(0xA8));
        graphics.fill(x, y, x + 2, y + h, muted || !live ? 0xB0FF6B6B : VBVTheme.accentAlpha(0xB0));
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), x + 6, y + 3 + i * lineH, colors.get(i), true);
        }
    }

    public static boolean liveCached() { return live; }
    public static boolean mutedCached() { return muted; }
    public static String noticeCached() { return notice; }
    public static String[] speakersCached() { return speakers; }
    public static int memberCountCached() { return memberCount; }

    // ------------------------------------------------------------------------------- bridge

    @JSBody(script = """
        (function () {
          if (window.VBVVoiceBridge && window.VBVVoiceBridge.initialized) return;
          const POLL_MS = window.VBV_VOICE_POLL_MS || 5000;
          const GATHER_MS = window.VBV_VOICE_GATHER_MS || 1500;
          const MEASURE_MS = 150;
          const NOTICE_MS = 8000;
          const SPEAK_LEVEL = 0.035;
          const SPEAK_HOLD_MS = 350;
          const MAX_PULL_TRIES = 5;
          const state = {
            phase: "idle", message: "", notice: "", room: "", members: [], muted: false, version: 0, selfId: ""
          };
          let epoch = 0;
          let stream = null, pushPc = null, pullPc = null, audioCtx = null;
          let pollTimer = 0, measureTimer = 0, noticeTimer = 0, unstableTimer = 0;
          let polling = false, pollFailures = 0, lastSig = "";
          let chain = Promise.resolve();
          let local = null;
          const playing = new Map();     // "id:gen" -> { el, tap }
          const requested = new Set();   // keys asked for from the service (kept after success)
          const failed = new Map();      // key -> { n, next }
          const midToKey = new Map();

          function friends() {
            const bridge = window.VBVFriendsBridge;
            if (!bridge || !bridge.authed || !bridge.session()) throw new Error("Create a Friends account first");
            return bridge;
          }
          function authed(path, extra) { return friends().authed(path, extra); }
          function keyOf(m) { return m.id + ":" + m.gen; }
          function bump() { state.version++; }
          function signature() {
            return state.phase + "|" + state.message + "|" + state.muted + "|" + state.room + "|" +
              JSON.stringify(state.members) + "|" + Array.from(playing.keys()).join(",") + "|" + state.notice;
          }
          function changed() {
            const sig = signature();
            if (sig !== lastSig) { lastSig = sig; bump(); }
          }
          function say(text) { state.message = text || ""; changed(); }
          function setNotice(text) {
            state.notice = text || "";
            if (noticeTimer) window.clearTimeout(noticeTimer);
            noticeTimer = 0;
            if (text) {
              noticeTimer = window.setTimeout(function () { state.notice = ""; noticeTimer = 0; changed(); }, NOTICE_MS);
            }
          }
          function describe(error) {
            if (!error) return "Voice chat failed.";
            if (error.message === "session invalid") return "Your Friends session is no longer valid.";
            if (error.name === "NotAllowedError" || error.name === "SecurityError") {
              return "The microphone is blocked. Allow it for this page in the browser's site settings (a school-managed device may block it).";
            }
            if (error.name === "NotFoundError" || error.name === "OverconstrainedError") return "No microphone was found.";
            if (error.name === "NotReadableError") return "The microphone is in use by another app.";
            return String(error.message || error);
          }

          // ------------------------------------------------------------------ levels (who is speaking)
          function context() {
            if (audioCtx) return audioCtx;
            const Ctx = window.AudioContext || window.webkitAudioContext;
            if (!Ctx) return null;
            try { audioCtx = new Ctx(); } catch (_) { audioCtx = null; }
            return audioCtx;
          }
          function tap(media) {
            try {
              const ctx = context();
              if (!ctx) return null;
              const source = ctx.createMediaStreamSource(media);
              const analyser = ctx.createAnalyser();
              analyser.fftSize = 512;
              source.connect(analyser);
              return { source: source, analyser: analyser, data: new Uint8Array(analyser.fftSize), until: 0, speaking: false };
            } catch (_) { return null; }
          }
          function measureOne(t, silenced, now) {
            if (!t) return;
            t.analyser.getByteTimeDomainData(t.data);
            let sum = 0;
            for (let i = 0; i < t.data.length; i++) { const v = (t.data[i] - 128) / 128; sum += v * v; }
            if (!silenced && Math.sqrt(sum / t.data.length) > SPEAK_LEVEL) t.until = now + SPEAK_HOLD_MS;
            t.speaking = now < t.until;
          }
          function measure() {
            const now = Date.now();
            try {
              if (audioCtx && audioCtx.state === "suspended") audioCtx.resume().catch(function () {});
              measureOne(local, state.muted, now);
              playing.forEach(function (entry) { measureOne(entry.tap, false, now); });
            } catch (_) {}
          }

          // --------------------------------------------------------------------------- playback
          function startPlay(el) {
            try {
              const result = el.play();
              if (result && result.catch) result.catch(function () { state.autoplayBlocked = true; });
            } catch (_) { state.autoplayBlocked = true; }
          }
          function resumePlayback() {
            if (!state.autoplayBlocked) return;
            state.autoplayBlocked = false;
            playing.forEach(function (entry) { if (entry.el.paused) startPlay(entry.el); });
          }
          function release(entry) {
            try { entry.el.pause(); entry.el.srcObject = null; } catch (_) {}
            try { if (entry.tap) entry.tap.source.disconnect(); } catch (_) {}
          }
          function drop(key) {
            const entry = playing.get(key);
            if (entry) release(entry);
            playing.delete(key);
            requested.delete(key);
            failed.delete(key);
            midToKey.forEach(function (value, mid) { if (value === key) midToKey.delete(mid); });
          }
          function onTrack(event) {
            const mid = event.transceiver && event.transceiver.mid != null ? String(event.transceiver.mid) : "";
            const key = midToKey.get(mid);
            if (!key) return;
            const old = playing.get(key);
            if (old) release(old);
            const media = (event.streams && event.streams[0]) || new window.MediaStream([event.track]);
            const el = new window.Audio();
            el.autoplay = true;
            el.srcObject = media;
            playing.set(key, { el: el, tap: tap(media) });
            startPlay(el);
            changed();
          }

          // ---------------------------------------------------------------------- connections
          function queue(fn) {
            const next = chain.then(fn, fn);
            chain = next.catch(function () {});
            return next;
          }
          function gathered(pc) {
            if (pc.iceGatheringState === "complete") return Promise.resolve();
            return new Promise(function (resolve) {
              let timer = 0;
              function done() {
                pc.removeEventListener("icegatheringstatechange", check);
                if (timer) window.clearTimeout(timer);
                resolve();
              }
              function check() { if (pc.iceGatheringState === "complete") done(); }
              pc.addEventListener("icegatheringstatechange", check);
              timer = window.setTimeout(done, GATHER_MS);
            });
          }
          function watch(pc, run, label) {
            pc.addEventListener("connectionstatechange", function () {
              if (run !== epoch) return;
              const s = pc.connectionState;
              if (s === "failed") {
                fail("The " + label + " connection failed. Some networks (many schools) block voice unless a TURN key is set up for the service.");
              } else if (s === "disconnected") {
                say("Connection unstable...");
                if (!unstableTimer) {
                  unstableTimer = window.setTimeout(function () {
                    unstableTimer = 0;
                    if (run === epoch && pc.connectionState !== "connected") fail("The voice connection was lost.");
                  }, 12000);
                }
              } else if (s === "connected" && unstableTimer) {
                window.clearTimeout(unstableTimer); unstableTimer = 0; say("");
              }
            });
          }

          async function pull(run, wanted) {
            if (run !== epoch) return;
            const keys = wanted.map(keyOf);
            keys.forEach(function (k) { requested.add(k); });
            let result;
            try { result = await authed("voice/pull", { members: wanted.map(function (m) { return m.id; }) }); }
            catch (error) { keys.forEach(function (k) { requested.delete(k); }); throw error; }
            if (run !== epoch) return;
            const tracks = result.tracks || [];
            wanted.forEach(function (m) {
              const k = keyOf(m);
              const t = tracks.find(function (x) { return x.member === m.id; });
              if (t && t.mid) {
                midToKey.set(String(t.mid), k);
                failed.delete(k);
              } else {
                const f = failed.get(k) || { n: 0, next: 0 };
                f.n++; f.next = Date.now() + Math.min(20000, 3000 * f.n);
                failed.set(k, f);
                // After a few tries stop asking for this one until they rejoin (a new "gen" is a new key).
                if (f.n < MAX_PULL_TRIES) requested.delete(k);
              }
            });
            if (result.offer) {
              await pullPc.setRemoteDescription({ type: "offer", sdp: result.offer });
              const answer = await pullPc.createAnswer();
              await pullPc.setLocalDescription(answer);
              await authed("voice/renegotiate", { answer: pullPc.localDescription.sdp });
            }
          }

          function sync(list, run) {
            state.members = (list || []).map(function (m) { return { id: m.id, name: m.name, ready: !!m.ready, gen: m.gen }; });
            const present = new Set(state.members.map(keyOf));
            Array.from(playing.keys()).forEach(function (k) { if (!present.has(k)) drop(k); });
            Array.from(requested).forEach(function (k) { if (!present.has(k)) requested.delete(k); });
            Array.from(failed.keys()).forEach(function (k) { if (!present.has(k)) failed.delete(k); });
            changed();
            const now = Date.now();
            const wanted = state.members.filter(function (m) {
              const k = keyOf(m);
              const f = failed.get(k);
              return m.id !== state.selfId && m.ready && !requested.has(k) && (!f || f.next <= now);
            });
            if (!wanted.length) return Promise.resolve();
            return queue(function () { return pull(run, wanted); });
          }

          async function poll(run) {
            if (run !== epoch || polling) return;
            polling = true;
            try {
              const result = await authed("voice/state");
              if (run !== epoch) return;
              pollFailures = 0;
              if (!result.inRoom) { fail("You are no longer in the voice chat."); return; }
              await sync(result.members, run);
            } catch (error) {
              if (run !== epoch) return;
              if (error && error.status === 409) fail("You were removed from the voice chat.");
              else if (error && error.message === "session invalid") fail("Your Friends session is no longer valid.");
              else if (++pollFailures >= 3) fail("The voice service stopped answering.");
              else if (error && error.message) say(describe(error));
            } finally {
              if (run === epoch) polling = false;
            }
          }

          // ------------------------------------------------------------------------- lifecycle
          function teardown() {
            epoch++;
            if (pollTimer) window.clearInterval(pollTimer);
            if (measureTimer) window.clearInterval(measureTimer);
            if (unstableTimer) window.clearTimeout(unstableTimer);
            pollTimer = measureTimer = unstableTimer = 0;
            polling = false; pollFailures = 0;
            playing.forEach(release);
            playing.clear(); requested.clear(); failed.clear(); midToKey.clear();
            if (local) { try { local.source.disconnect(); } catch (_) {} }
            local = null;
            [pushPc, pullPc].forEach(function (pc) { try { if (pc) pc.close(); } catch (_) {} });
            pushPc = pullPc = null;
            if (stream) { try { stream.getTracks().forEach(function (t) { t.stop(); }); } catch (_) {} }
            stream = null;
            if (audioCtx) { try { audioCtx.close(); } catch (_) {} audioCtx = null; }
            chain = Promise.resolve();
            state.phase = "idle"; state.room = ""; state.members = []; state.autoplayBlocked = false;
          }
          function fail(text, leaveServer) {
            const inRoom = leaveServer === undefined ? state.room !== "" : leaveServer;
            teardown();
            state.message = text; setNotice(text);
            changed();
            // Tell the service so the room does not wait for this player (it would notice after about 45 s).
            if (inRoom) { try { authed("voice/leave").catch(function () {}); } catch (_) {} }
          }

          async function join(friend) {
            if (state.phase !== "idle") return;
            let bridge;
            try { bridge = friends(); } catch (error) { state.message = describe(error); changed(); return; }
            const media = window.navigator && window.navigator.mediaDevices;
            if (!window.RTCPeerConnection || !media || !media.getUserMedia) {
              state.message = window.isSecureContext === false
                ? "Voice chat needs a secure (https) page to use the microphone."
                : "This browser cannot do voice chat (no WebRTC or microphone access).";
              changed(); return;
            }
            const run = ++epoch;
            state.phase = "starting"; state.muted = false; state.selfId = String(bridge.get("id") || "");
            say("Allow the microphone to start voice chat...");
            let joined = false;
            try {
              stream = await media.getUserMedia({
                audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true, channelCount: 1 },
                video: false
              });
              if (run !== epoch) { stream.getTracks().forEach(function (t) { t.stop(); }); return; }
              say("Joining...");
              const info = await authed("voice/join", friend ? { friend: friend } : {});
              joined = true;
              if (run !== epoch) { authed("voice/leave").catch(function () {}); return; }
              state.room = info.room;
              state.members = (info.members || []).map(function (m) { return { id: m.id, name: m.name, ready: !!m.ready, gen: m.gen }; });
              const config = { iceServers: info.iceServers || [], bundlePolicy: "max-bundle" };

              say("Connecting your microphone...");
              pushPc = new window.RTCPeerConnection(config);
              pullPc = new window.RTCPeerConnection(config);
              watch(pushPc, run, "microphone"); watch(pullPc, run, "listening");
              pullPc.addEventListener("track", onTrack);
              const track = stream.getAudioTracks()[0];
              if (!track) throw new Error("No microphone was found.");
              track.enabled = true;
              const sender = pushPc.addTransceiver(track, { direction: "sendonly", streams: [stream] });
              await pushPc.setLocalDescription(await pushPc.createOffer());
              await gathered(pushPc);
              if (run !== epoch) return;
              const mid = sender.mid != null ? String(sender.mid) : "0";
              const published = await authed("voice/publish", { offer: pushPc.localDescription.sdp, mid: mid });
              if (run !== epoch) return;
              await pushPc.setRemoteDescription({ type: "answer", sdp: published.answer });
              if (run !== epoch) return;

              local = tap(stream);
              state.phase = "live"; say("");
              setNotice("");
              measureTimer = window.setInterval(measure, MEASURE_MS);
              pollTimer = window.setInterval(function () { poll(run); }, POLL_MS);
              await poll(run);
              if (run === epoch) await sync(state.members, run).catch(function () {});
            } catch (error) {
              if (run !== epoch) return;
              fail(describe(error), joined);
            }
          }

          async function leave() {
            if (state.phase === "idle") return;
            teardown();
            say("You left the voice chat.");
            try { await authed("voice/leave"); } catch (_) {}
          }
          function setMuted(flag) {
            state.muted = !!flag;
            if (stream) stream.getAudioTracks().forEach(function (t) { t.enabled = !state.muted; });
            changed();
          }
          function reset() {
            if (state.phase === "idle") return;
            teardown(); state.message = ""; changed();
          }

          const actions = {
            join: function (friend) { return join(String(friend || "")); },
            leave: leave,
            toggleMute: function () { if (state.phase === "live") setMuted(!state.muted); }
          };
          window.VBVVoiceBridge = {
            initialized: true,
            call: function (name, a) { const fn = actions[name]; if (fn) { Promise.resolve(fn(a)).catch(function () {}); } },
            reset: reset,
            version: function () { return state.version; },
            get: function (key) {
              switch (key) {
                case "phase": return state.phase;
                case "message": return state.message;
                case "notice": return state.notice;
                case "room": return state.room;
                case "muted": return state.muted ? "1" : "0";
                default: return "";
              }
            },
            rows: function (kind) {
              if (kind !== "members") return "";
              const clean = function (s) { return String(s == null ? "" : s).replace(/[\\t\\r\\n]/g, " "); };
              return state.members.map(function (m) {
                const me = m.id === state.selfId;
                const entry = playing.get(keyOf(m));
                const speaking = me ? !!(local && local.speaking && !state.muted) : !!(entry && entry.tap && entry.tap.speaking);
                const status = me ? "you" : (entry ? "live" : (m.ready ? "connecting" : "waiting"));
                return m.id + "\\t" + clean(m.name) + "\\t" + (speaking ? "1" : "0") + "\\t" + status;
              }).join("\\n");
            }
          };
          if (window.addEventListener) {
            // Browsers only let a page play sound after the player has interacted with it; retry on the next click.
            window.addEventListener("pointerdown", resumePlayback, true);
            window.addEventListener("keydown", resumePlayback, true);
            // Leaving the page ends the call at once instead of waiting for the service to notice (about 45 s).
            window.addEventListener("pagehide", function () {
              if (state.phase === "idle") return;
              try {
                const bridge = friends();
                fetch(bridge.get("api") + "/voice/leave", {
                  method: "POST", keepalive: true, headers: { "content-type": "application/json" },
                  body: JSON.stringify({ id: bridge.session().id, token: bridge.session().token })
                }).catch(function () {});
              } catch (_) {}
            });
          }
        })();
        """)
    private static native void initializeJS();

    @JSBody(params = {"name", "a"}, script =
        "try { window.VBVVoiceBridge && window.VBVVoiceBridge.call(String(name), String(a || '')); } catch(e) {}")
    private static native void callJS(String name, String a);

    @JSBody(params = {"key"}, script = "try { return window.VBVVoiceBridge ? String(window.VBVVoiceBridge.get(key) || '') : ''; } catch(e) { return ''; }")
    private static native String getJS(String key);

    @JSBody(params = {"kind"}, script = "try { return window.VBVVoiceBridge ? String(window.VBVVoiceBridge.rows(kind) || '') : ''; } catch(e) { return ''; }")
    private static native String rowsJS(String kind);

    @JSBody(script = "try { return window.VBVVoiceBridge ? (window.VBVVoiceBridge.version() | 0) : 0; } catch(e) { return 0; }")
    private static native int versionJS();
}
