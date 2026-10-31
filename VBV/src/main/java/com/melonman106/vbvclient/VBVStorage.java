package com.melonman106.vbvclient;

import org.teavm.jso.JSBody;

/** Browser filesystem bridge with localStorage fallback. */
public final class VBVStorage {
    private VBVStorage() {}

    public static void openManager() { openManagerJS(); }

    public static void updateServer(String address) {
        if (address != null) updateServerJS(address);
    }

    public static String getText(String key) { return getTextJS(key); }
    public static boolean putText(String key, String value) { return putTextJS(key, value); }
    public static long revision() { return revisionJS(); }

    @JSBody(script = "try { if (window.VBVStorage) window.VBVStorage.openUI(); else window.alert('Storage Manager is unavailable in this build.'); } catch (e) { console.error(e); }")
    private static native void openManagerJS();

    @JSBody(params = {"address"}, script = "try { if (window.VBVStorage) window.VBVStorage.updateServer(address); } catch (e) { console.error(e); }")
    private static native void updateServerJS(String address);

    @JSBody(params = {"key"}, script = "try { if (window.VBVStorage) return window.VBVStorage.getTextSync(key); return window.localStorage.getItem(key) || ''; } catch (e) { return ''; }")
    private static native String getTextJS(String key);

    @JSBody(params = {"key", "value"}, script = "try { if (window.VBVStorage) return !!window.VBVStorage.setTextSync(key, value); window.localStorage.setItem(key, value); return true; } catch (e) { return false; }")
    private static native boolean putTextJS(String key, String value);

    @JSBody(script = "try { return window.VBVStorage ? Number(window.VBVStorage.revision()) || 0 : 0; } catch (e) { return 0; }")
    private static native long revisionJS();
}
