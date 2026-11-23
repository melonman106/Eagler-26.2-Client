package com.melonman106.vbvclient;

import java.util.Calendar;
import java.util.Locale;

import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Int8Array;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;

/**
 * Screenshot Manager storage bridge.
 *
 * <p>Vanilla's screenshot path is replaced (in {@code Screenshot.grab}) by
 * {@link #capture(Minecraft)}: the frame is read back once, converted to a PNG on a canvas and
 * written into the managed storage folder as {@code Screenshots/<name>.png}. When no storage folder
 * is connected the same PNG is downloaded by the browser, exactly like a vanilla screenshot, so the
 * key never silently loses a capture. The index lives in {@code vbv.screenshots.v1} and
 * {@link VBVScreenshotsScreen} lists it.</p>
 */
public final class VBVScreenshots {
    public static final String INDEX_KEY = "vbv.screenshots.v1";
    public static String lastStatus = "";

    private VBVScreenshots() {}

    /**
     * Takes the screenshot and hands the pixels to the storage bridge. Returns false only when the
     * frame cannot be read at all, which lets the vanilla screenshot path run instead.
     */
    public static boolean capture(Minecraft minecraft) {
        if (minecraft == null) return false;
        try {
            Screenshot.takeScreenshot(minecraft.gameRenderer.mainRenderTarget(), image -> {
                try (image) {
                    int width = image.getWidth();
                    int height = image.getHeight();
                    if (width <= 0 || height <= 0) return;
                    byte[] rgba = image.getPixelsRGBABytes();
                    if (rgba == null || rgba.length != width * height * 4) return;
                    String name = nextName();
                    String path = store(Int8Array.fromJavaArray(rgba), width, height, name);
                    if (path == null || path.isEmpty()) return;
                    lastStatus = path;
                    minecraft.execute(() -> {
                        minecraft.showDebugChat(Component.literal("Screenshot saved to " + path));
                        VBVNotifications.push("Screenshot saved", path, null);
                    });
                } catch (RuntimeException ignored) {
                    // A failed read back leaves the vanilla path to report the error.
                }
            });
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String nextName() {
        Calendar now = Calendar.getInstance();
        return String.format(Locale.ROOT, "screenshot_%04d-%02d-%02d_%02d.%02d.%02d_%03d.png",
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH),
            now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.SECOND),
            now.get(Calendar.MILLISECOND));
    }

    /** One index row: {@code name, path, bytes, width, height, epochMillis}. */
    public static String[] entries() {
        String raw = VBVStorage.getText(INDEX_KEY);
        if (raw == null || raw.isEmpty()) return new String[0];
        return raw.split("\n");
    }

    public static int count() {
        int count = 0;
        for (String line : entries()) {
            if (!line.trim().isEmpty()) count++;
        }
        return count;
    }

    public static boolean connected() {
        try {
            return VBVStorage.connected();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static void open(String path) {
        try {
            openJS(path == null ? "" : path);
        } catch (RuntimeException ignored) {
        }
    }

    public static void download(String path) {
        try {
            downloadJS(path == null ? "" : path);
        } catch (RuntimeException ignored) {
        }
    }

    public static void delete(String path) {
        try {
            deleteJS(path == null ? "" : path);
        } catch (RuntimeException ignored) {
        }
    }

    public static void chooseFolder() {
        VBVStorage.chooseFolder();
    }

    @JSBody(params = {"rgba", "width", "height", "name"}, script = """
        try {
          var canvas = document.createElement("canvas");
          canvas.width = width; canvas.height = height;
          var ctx = canvas.getContext("2d");
          if (!ctx) return "";
          var data = ctx.createImageData(width, height);
          data.data.set(rgba);
          ctx.putImageData(data, 0, 0);
          var path = "";
          if (window.VBVStorage && window.VBVStorage.saveScreenshotData) {
            path = String(window.VBVStorage.saveScreenshotData(canvas, name, width, height) || "");
          }
          if (!path) {
            // The storage script has not initialized yet: download the PNG directly so the capture
            // is never lost.
            var url = canvas.toDataURL("image/png");
            var el = document.createElement("a");
            el.style.display = "none"; el.href = url; el.download = name;
            document.body.appendChild(el); el.click(); document.body.removeChild(el);
            path = "Downloads/" + name;
          }
          return path;
        } catch (e) { console.error(e); return ""; }
        """)
    private static native String store(Int8Array rgba, int width, int height, String name);

    @JSBody(params = {"path"}, script = "try { if (window.VBVStorage) window.VBVStorage.openScreenshot(path); } catch (e) { console.error(e); }")
    private static native void openJS(String path);

    @JSBody(params = {"path"}, script = "try { if (window.VBVStorage) window.VBVStorage.downloadScreenshot(path); } catch (e) { console.error(e); }")
    private static native void downloadJS(String path);

    @JSBody(params = {"path"}, script = "try { if (window.VBVStorage) window.VBVStorage.deleteScreenshot(path); } catch (e) { console.error(e); }")
    private static native void deleteJS(String path);
}
