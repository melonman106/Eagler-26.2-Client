package com.melonman106.vbvclient;

import java.nio.charset.StandardCharsets;
import net.lax1dude.eaglercraft.v1_8.EagRuntime;
import org.teavm.jso.JSBody;

/**
 * Downloads the saved VBV settings as a small text file.
 *
 * <p>The settings normally live only in the browser's storage for this origin,
 * which is easy to lose by clearing site data and impossible to move to another
 * browser. The raw key/value body is already text, so exporting it needs no new
 * format: the file opens in any editor and can be compared between builds.
 * Import is not wired up yet; that needs a file picker and an asynchronous
 * callback into the config store.</p>
 */
public final class VBVSettingsFile {
    private static final String STORAGE_KEY = "vbv_feature_config_v2";

    private VBVSettingsFile() {}

    /** Writes the settings to a download; returns false when the browser refused. */
    public static boolean exportSettings() {
        try {
            byte[] bytes = EagRuntime.getStorage(STORAGE_KEY);
            String body = bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
            if (body.isEmpty()) {
                body = "(no saved settings yet)\n";
            }
            return downloadJS("vbv-settings.txt",
                "VBV settings export\nclient " + VBVClient.VERSION + "\n" + body);
        } catch (Throwable error) {
            System.err.println("[VBV settings] export failed: " + error);
            return false;
        }
    }

    @JSBody(params = { "name", "text" }, script = "try {"
        + "var a=document.createElement('a');"
        + "a.href=URL.createObjectURL(new Blob([text],{type:'text/plain;charset=utf-8'}));"
        + "a.download=name;document.body.appendChild(a);a.click();a.remove();"
        + "setTimeout(function(){URL.revokeObjectURL(a.href);},2000);"
        + "return true;"
        + "} catch(e) { console.error(e); return false; }")
    private static native boolean downloadJS(String name, String text);
}
