package com.melonman106.vbvclient.mods;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Helpers shared by the self-tests: checks, in-memory storage, fake PNGs and zip building. */
final class TestKit {
    private TestKit() {}

    static int checks;
    static int failures;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    static boolean any(List<String> lines, String fragment) {
        for (String line : lines) if (line.contains(fragment)) return true;
        return false;
    }

    /** A map-backed {@link ModFiles}. */
    static final class MemoryFiles implements ModFiles {
        final Map<String, byte[]> map = new TreeMap<>();
        boolean failWrites;

        @Override public byte[] read(String path) { return map.get(path); }

        @Override public boolean write(String path, byte[] data) {
            if (failWrites) return false;
            map.put(path, data);
            return true;
        }

        @Override public void delete(String path) { map.remove(path); }

        @Override public List<String> list(String folder) {
            List<String> names = new ArrayList<>();
            String prefix = folder + "/";
            for (String key : map.keySet()) {
                if (key.startsWith(prefix) && key.indexOf('/', prefix.length()) < 0) names.add(key.substring(prefix.length()));
            }
            return names;
        }
    }

    /** 24 bytes that pass as the start of a PNG of the given size (enough for the checks that look at the header). */
    static byte[] png(int width, int height) {
        byte[] out = new byte[33];
        byte[] sig = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(sig, 0, out, 0, 8);
        out[11] = 13;
        out[12] = 'I'; out[13] = 'H'; out[14] = 'D'; out[15] = 'R';
        for (int i = 0; i < 4; i++) {
            out[16 + i] = (byte) (width >>> (24 - 8 * i));
            out[20 + i] = (byte) (height >>> (24 - 8 * i));
        }
        return out;
    }

    static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }

    /** Builds a zip from path to bytes, in insertion order. */
    static byte[] zip(Map<String, byte[]> entries) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue());
                    zip.closeEntry();
                }
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** A small valid mod with one sword, for tests to change. */
    static Map<String, byte[]> goodMod(String id) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("info.json", bytes("{\"schema\":1,\"id\":\"" + id + "\",\"name\":\"Ruby Tools\",\"version\":\"1.0.0\",\"description\":\"A sword.\","
            + "\"authors\":[\"Alexander\"],\"provides\":[\"item\"],\"survival\":true,\"multiplayer\":false}"));
        files.put("pack.png", png(64, 64));
        files.put("items.json", bytes("{\"items\":[{\"id\":\"ruby_sword\",\"name\":\"Ruby Sword\",\"attack_damage\":8,\"attack_speed\":1.6,"
            + "\"durability\":1800,\"rarity\":\"rare\",\"repair_with\":\"minecraft:diamond\"}]}"));
        files.put("assets/" + id + "/textures/item/ruby_sword.png", png(16, 16));
        return files;
    }

    static ModPackage read(Map<String, byte[]> files) { return ModPackage.read(zip(files)); }
}
