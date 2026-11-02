package com.melonman106.vbvclient;

import org.teavm.jso.JSBody;

/** Small persistent player-profile bridge used by the Hero and party UI. */
public final class VBVProfile {
    private VBVProfile() {}

    public static String name() { return getNameJS(); }
    public static void saveName(String name) { saveNameJS(name); }

    @JSBody(script = "try { return localStorage.getItem('vbv.profile.name') || (window.minecraft && window.minecraft.user ? String(window.minecraft.user.name) : 'Player'); } catch(e) { return 'Player'; }")
    private static native String getNameJS();

    @JSBody(params = {"name"}, script = "try { localStorage.setItem('vbv.profile.name', String(name || 'Player')); } catch(e) {}")
    private static native void saveNameJS(String name);
}
