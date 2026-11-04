package com.melonman106.vbvclient;

import org.teavm.jso.JSBody;

/** Network-backed party client. Session credentials remain local to this browser. */
public final class VBVParty {
    private static final String API_BASE =
        "https://eagler-minecraft-relay.u2471966200.workers.dev/party";

    private VBVParty() {}

    public static void initialize() { initializeJS(API_BASE); }
    public static void create(String name) { createJS(name); }
    public static void join(String code, String name) { joinJS(code, name); }
    public static void poll() { pollJS(); }
    public static void leave() { leaveJS(); }
    public static void disband() { disbandJS(); }
    public static void kick(String memberId) { kickJS(memberId); }

    public static String code() { return stateJS("code"); }
    public static String status() { return stateJS("status"); }
    public static boolean isHost() { return "1".equals(stateJS("host")); }
    public static String[] memberRows() {
        String raw = memberRowsJS();
        return raw == null || raw.isEmpty() ? new String[0] : raw.split("\\n");
    }
    public static String[] players() {
        String[] rows = memberRows();
        String[] names = new String[rows.length];
        for (int i = 0; i < rows.length; i++) {
            String[] fields = rows[i].split("\\t", -1);
            names[i] = fields.length > 0 ? fields[0] : "";
        }
        return names;
    }

    @JSBody(params = {"api"}, script = """
        (function () {
          if (window.VBVPartyBridge && window.VBVPartyBridge.initialized) return;
          const endpoint = String(window.VBV_PARTY_API || api || "").replace(/\\/+$/, "");
          const storageKey = "vbv.party.session.v2";
          const state = { code: "", token: "", memberId: "", host: false,
            status: "Not in a party", members: [], busy: false };
          try {
            const saved = JSON.parse(localStorage.getItem(storageKey) || "{}");
            if (saved && saved.code && saved.token && saved.memberId) Object.assign(state, saved);
          } catch (_) {}
          let polling = false;
          function persist() {
            try {
              localStorage.setItem(storageKey, JSON.stringify({
                code: state.code, token: state.token, memberId: state.memberId, host: state.host
              }));
            } catch (_) {}
          }
          function clearSession() {
            state.code = ""; state.token = ""; state.memberId = "";
            state.host = false; state.members = [];
            try { localStorage.removeItem(storageKey); } catch (_) {}
          }
          async function post(action, body) {
            if (!endpoint) throw new Error("Party API URL is not configured");
            const response = await fetch(endpoint + "/" + action, {
              method: "POST",
              headers: { "content-type": "application/json" },
              cache: "no-store",
              body: JSON.stringify(body || {})
            });
            let result;
            try { result = await response.json(); }
            catch (_) { throw new Error("Party service returned an invalid response"); }
            if (!response.ok || !result || !result.ok) {
              throw new Error(result && result.error ? result.error : ("Party service HTTP " + response.status));
            }
            return result;
          }
          function setError(error) {
            state.status = "Party service: " + String(error && error.message || error);
          }
          async function create(name) {
            state.busy = true; state.status = "Creating party...";
            try {
              const result = await post("create", { name: String(name || "").trim() });
              state.code = result.code; state.token = result.token;
              state.memberId = result.memberId; state.host = true;
              state.members = result.members || []; persist();
              state.status = "Party created. Share the code with your players.";
            } catch (error) { setError(error); }
            finally { state.busy = false; }
          }
          async function join(code, name) {
            state.busy = true; state.status = "Joining party...";
            try {
              const result = await post("join", {
                code: String(code || "").trim().toUpperCase(),
                name: String(name || "").trim()
              });
              state.code = result.code; state.token = result.token;
              state.memberId = result.memberId; state.host = false;
              state.members = []; persist();
              state.status = "Joined party. Synchronizing members...";
              await poll(true);
            } catch (error) { setError(error); }
            finally { state.busy = false; }
          }
          async function poll(force) {
            if (polling || !state.code || !state.token) return;
            polling = true;
            try {
              const result = await post("poll", { code: state.code, token: state.token });
              state.host = !!result.host;
              state.members = Array.isArray(result.members) ? result.members : [];
              state.status = state.members.length + (state.members.length === 1 ? " player in party" : " players in party");
            } catch (error) {
              const message = String(error && error.message || error);
              if (/party has ended|party not found|session is invalid/i.test(message)) {
                clearSession();
                state.status = "Party ended or session expired. Create or join another party.";
              } else setError(error);
            } finally { polling = false; }
          }
          async function leave() {
            if (!state.code || !state.token) return;
            state.busy = true;
            try { await post("leave", { code: state.code, token: state.token }); }
            catch (error) { setError(error); }
            finally { clearSession(); state.status = "You left the party."; state.busy = false; }
          }
          async function disband() {
            if (!state.code || !state.token || !state.host) return;
            state.busy = true;
            try { await post("disband", { code: state.code, token: state.token }); }
            catch (error) { setError(error); }
            finally { clearSession(); state.status = "Party disbanded."; state.busy = false; }
          }
          async function kick(memberId) {
            if (!state.code || !state.token || !state.host || !memberId) return;
            try {
              await post("kick", { code: state.code, token: state.token, memberId: String(memberId) });
              await poll(true);
            } catch (error) { setError(error); }
          }
          const bridge = {
            initialized: true, create, join, poll: () => poll(true), leave, disband, kick,
            state: key => key === "code" ? state.code :
              key === "host" ? (state.host ? "1" : "0") : state.status,
            members: () => state.members.map(member =>
              String(member.name || "").replace(/[\\t\\r\\n]/g, " ") + "\\t" +
              String(member.id || "") + "\\t" + (member.host ? "1" : "0")
            ).join("\\n")
          };
          window.VBVPartyBridge = bridge;
          window.setInterval(() => poll(false), 5000);
          poll(true);
        })();
        """)
    private static native void initializeJS(String api);

    @JSBody(params = {"name"}, script = "try { window.VBVPartyBridge && window.VBVPartyBridge.create(String(name || '')); } catch(e) {}")
    private static native void createJS(String name);

    @JSBody(params = {"code", "name"}, script = "try { window.VBVPartyBridge && window.VBVPartyBridge.join(String(code || ''), String(name || '')); } catch(e) {}")
    private static native void joinJS(String code, String name);

    @JSBody(script = "try { window.VBVPartyBridge && window.VBVPartyBridge.poll(); } catch(e) {}")
    private static native void pollJS();

    @JSBody(script = "try { window.VBVPartyBridge && window.VBVPartyBridge.leave(); } catch(e) {}")
    private static native void leaveJS();

    @JSBody(script = "try { window.VBVPartyBridge && window.VBVPartyBridge.disband(); } catch(e) {}")
    private static native void disbandJS();

    @JSBody(params = {"memberId"}, script = "try { window.VBVPartyBridge && window.VBVPartyBridge.kick(String(memberId || '')); } catch(e) {}")
    private static native void kickJS(String memberId);

    @JSBody(params = {"key"}, script = "try { return window.VBVPartyBridge ? String(window.VBVPartyBridge.state(key) || '') : ''; } catch(e) { return ''; }")
    private static native String stateJS(String key);

    @JSBody(script = "try { return window.VBVPartyBridge ? String(window.VBVPartyBridge.members() || '') : ''; } catch(e) { return ''; }")
    private static native String memberRowsJS();
}
