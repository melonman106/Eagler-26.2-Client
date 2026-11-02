package com.melonman106.vbvclient;

import org.teavm.jso.JSBody;

/** Local party prototype. A network/database backend will replace this later. */
public final class VBVParty {
    private VBVParty() {}

    public static String code() { return codeJS(); }
    public static String newCode() { return newCodeJS(); }
    public static void addPlayer(String name) { addPlayerJS(name); }
    public static String[] players() {
        String raw = playersJS();
        if (raw == null || raw.isEmpty()) return new String[0];
        return raw.split("\\n");
    }

    @JSBody(script = "try { return localStorage.getItem('vbv.party.code') || newCodeInternal(); } catch(e) { return 'VBV-0000'; } function newCodeInternal(){ var s=Math.random().toString(36).slice(2,6).toUpperCase(); var c='VBV-'+s; localStorage.setItem('vbv.party.code',c); return c; }")
    private static native String codeJS();

    @JSBody(script = "try { var s=Math.random().toString(36).slice(2,6).toUpperCase(); var c='VBV-'+s; localStorage.setItem('vbv.party.code',c); return c; } catch(e) { return 'VBV-0000'; }")
    private static native String newCodeJS();

    @JSBody(params = {"name"}, script = "try { var a=[]; try { a=JSON.parse(localStorage.getItem('vbv.party.players')||'[]'); } catch(e) {} if(a.indexOf(String(name))<0) a.push(String(name)); localStorage.setItem('vbv.party.players',JSON.stringify(a)); } catch(e) {}")
    private static native void addPlayerJS(String name);

    @JSBody(script = "try { var a=JSON.parse(localStorage.getItem('vbv.party.players')||'[]'); return a.join('\\n'); } catch(e) { return ''; }")
    private static native String playersJS();
}
