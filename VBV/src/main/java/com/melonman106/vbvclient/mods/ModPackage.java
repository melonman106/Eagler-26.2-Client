package com.melonman106.vbvclient.mods;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A mod {@code .zip}, read and checked in memory. Nothing in a zip is ever executed or written outside the mod's
 * own store: paths are cleaned, sizes are capped, and only known kinds of file are kept.
 *
 * <p>Kept files: {@code info.json} / {@code fabric.mod.json}, the icon, {@code items.json} (and the other content
 * lists so they can be reported), and everything under {@code assets/} whose path is a valid resource path. A zip
 * made by zipping a folder (so everything sits inside one top-level folder) is accepted too.</p>
 */
public final class ModPackage {
    public static final int MAX_ZIP_BYTES = 24 * 1024 * 1024;
    public static final int MAX_ENTRIES = 4000;
    public static final int MAX_ENTRY_BYTES = 8 * 1024 * 1024;
    public static final int MAX_TOTAL_BYTES = 48 * 1024 * 1024;
    public static final int MAX_ICON_BYTES = 512 * 1024;
    public static final int MAX_TEXTURE_SIZE = 4096;

    /** Content lists other than items: recognised so they can be reported, not yet turned into game content. */
    public static final List<String> FUTURE_LISTS = List.of("blocks.json", "entities.json", "effects.json", "hud.json");

    private static final Pattern ASSET_PATH = Pattern.compile("assets/[a-z0-9_.-]+/[a-z0-9_./-]+");
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    public final ModManifest manifest;
    public final List<ModItems.ItemDef> items;
    /** Every kept file by its cleaned path inside the zip (for example {@code assets/ruby/textures/item/ruby.png}). */
    public final Map<String, byte[]> files;
    public final byte[] icon;
    public final List<String> problems;
    public final List<String> warnings;
    /** Content kinds this version really loads ("item"). */
    public final List<String> kinds;
    /** Content lists found that this version ignores (for example "blocks.json"). */
    public final List<String> ignoredLists;
    public final int totalBytes;
    /** The zip exactly as it was read (not copied), and its CRC-32. Two realms that load the same zip see the same crc. */
    public final byte[] source;
    public final long crc;

    private ModPackage(ModManifest manifest, List<ModItems.ItemDef> items, Map<String, byte[]> files, byte[] icon,
                       List<String> problems, List<String> warnings, List<String> kinds, List<String> ignoredLists, int totalBytes, byte[] source) {
        this.manifest = manifest;
        this.items = Collections.unmodifiableList(items);
        this.files = Collections.unmodifiableMap(files);
        this.icon = icon;
        this.problems = Collections.unmodifiableList(problems);
        this.warnings = Collections.unmodifiableList(warnings);
        this.kinds = Collections.unmodifiableList(kinds);
        this.ignoredLists = Collections.unmodifiableList(ignoredLists);
        this.totalBytes = totalBytes;
        this.source = source;
        this.crc = crc32(source);
    }

    public boolean ok() { return manifest != null && problems.isEmpty(); }

    /** The files under {@code assets/}, keyed without that prefix ({@code ns/textures/...}), as a resource pack serves them. */
    public Map<String, byte[]> assets() {
        Map<String, byte[]> out = new TreeMap<>();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            if (entry.getKey().startsWith("assets/")) out.put(entry.getKey().substring("assets/".length()), entry.getValue());
        }
        return out;
    }

    private static ModPackage failed(List<String> problems, List<String> warnings, int total, byte[] source) {
        return new ModPackage(null, new ArrayList<>(), new LinkedHashMap<>(), null, problems, warnings, new ArrayList<>(), new ArrayList<>(), total, source);
    }

    /** CRC-32 of a byte array (0 for null). */
    public static long crc32(byte[] data) {
        if (data == null) return 0L;
        CRC32 crc = new CRC32();
        crc.update(data, 0, data.length);
        return crc.getValue();
    }

    public static ModPackage read(byte[] zip) {
        List<String> problems = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (zip == null || zip.length == 0) {
            problems.add("The file is empty.");
            return failed(problems, warnings, 0, zip);
        }
        if (zip.length > MAX_ZIP_BYTES) {
            problems.add("The zip is " + (zip.length / 1024 / 1024) + " MB; the limit is " + (MAX_ZIP_BYTES / 1024 / 1024) + " MB.");
            return failed(problems, warnings, 0, zip);
        }

        Map<String, byte[]> raw = new LinkedHashMap<>();
        int total = 0;
        int entries = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    problems.add("The zip has more than " + MAX_ENTRIES + " files.");
                    return failed(problems, warnings, total, zip);
                }
                if (entry.isDirectory()) continue;
                String path = cleanPath(entry.getName());
                if (path == null) {
                    problems.add("The zip contains an unsafe path: \"" + shorten(entry.getName()) + "\".");
                    continue;
                }
                if (isJunk(path)) continue;
                byte[] data = readLimited(in, Math.min(MAX_ENTRY_BYTES, MAX_TOTAL_BYTES - total));
                if (data == null) {
                    problems.add("\"" + shorten(path) + "\" is too large when unpacked (limit " + (MAX_ENTRY_BYTES / 1024 / 1024) + " MB per file, "
                        + (MAX_TOTAL_BYTES / 1024 / 1024) + " MB in total).");
                    return failed(problems, warnings, total, zip);
                }
                total += data.length;
                if (raw.put(path, data) != null) warnings.add("\"" + shorten(path) + "\" appears twice in the zip; the last copy is used.");
            }
        } catch (IOException | RuntimeException ex) {
            problems.add("This is not a readable .zip file (" + shorten(String.valueOf(ex.getMessage())) + ").");
            return failed(problems, warnings, total, zip);
        }
        if (raw.isEmpty()) {
            problems.add("The zip has no files in it.");
            return failed(problems, warnings, total, zip);
        }
        if (!problems.isEmpty()) return failed(problems, warnings, total, zip);

        // A zip made by zipping a folder keeps everything inside one top-level folder: look inside it.
        String prefix = wrapperPrefix(raw);
        Map<String, byte[]> files = new LinkedHashMap<>();
        int classFiles = 0;
        int skippedPaths = 0;
        for (Map.Entry<String, byte[]> entry : raw.entrySet()) {
            String path = entry.getKey();
            if (!prefix.isEmpty()) {
                if (!path.startsWith(prefix)) continue;
                path = path.substring(prefix.length());
            }
            if (path.endsWith(".class") || path.endsWith(".jar")) {
                classFiles++;
                continue;
            }
            if (keep(path)) {
                if (path.startsWith("assets/") && !ASSET_PATH.matcher(path).matches()) {
                    skippedPaths++;
                    continue;
                }
                files.put(path, entry.getValue());
            }
        }
        if (classFiles > 0) warnings.add(classFiles + " Java file(s) in the zip were ignored; Java code cannot run in the browser.");
        if (skippedPaths > 0) warnings.add(skippedPaths + " file(s) under assets/ were ignored because their names are not valid (lowercase letters, digits, _ . - / only).");

        // Manifest.
        ModManifest.Result read;
        if (files.containsKey("info.json")) {
            read = ModManifest.parse(ModManifest.Format.VBV, text(files.get("info.json")));
            if (files.containsKey("fabric.mod.json")) warnings.add("Both info.json and fabric.mod.json are present; info.json is used.");
        } else if (files.containsKey("fabric.mod.json")) {
            read = ModManifest.parse(ModManifest.Format.FABRIC, text(files.get("fabric.mod.json")));
        } else {
            problems.add("There is no info.json (or fabric.mod.json) at the top of the zip.");
            return failed(problems, warnings, total, zip);
        }
        warnings.addAll(read.warnings());
        if (!read.ok()) {
            problems.addAll(read.problems());
            return failed(problems, warnings, total, zip);
        }
        ModManifest manifest = read.manifest();

        // Icon.
        byte[] icon = null;
        if (manifest.icon != null) {
            byte[] bytes = files.get(cleanOrNull(manifest.icon));
            if (bytes == null) {
                warnings.add("The icon \"" + shorten(manifest.icon) + "\" is missing; a default icon is shown.");
            } else if (!isPng(bytes)) {
                warnings.add("The icon is not a PNG image; a default icon is shown.");
            } else if (bytes.length > MAX_ICON_BYTES) {
                warnings.add("The icon is larger than " + (MAX_ICON_BYTES / 1024) + " KB; a default icon is shown.");
            } else {
                icon = bytes;
            }
        }

        // Items.
        List<ModItems.ItemDef> items = new ArrayList<>();
        if (files.containsKey("items.json")) {
            ModItems.Result parsed = ModItems.parse(manifest.id, text(files.get("items.json")));
            items.addAll(parsed.items());
            problems.addAll(parsed.problems());
            warnings.addAll(parsed.warnings());
            checkTextures(manifest.id, items, files, problems);
        }

        // Language files must be valid, or they are dropped rather than breaking the game's language loading.
        List<String> badLang = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            if (entry.getKey().matches("assets/[^/]+/lang/[^/]+\\.json") && !isJsonObject(entry.getValue())) badLang.add(entry.getKey());
        }
        for (String path : badLang) {
            files.remove(path);
            warnings.add("\"" + path + "\" is not a valid language file and was ignored.");
        }

        // Content kinds.
        List<String> kinds = new ArrayList<>();
        if (!items.isEmpty()) kinds.add("item");
        List<String> ignored = new ArrayList<>();
        for (String list : FUTURE_LISTS) {
            if (files.containsKey(list)) {
                ignored.add(list);
                warnings.add(list + " was found, but that kind of content is not supported in this version, so it is ignored.");
            }
        }
        if (!manifest.declaredKinds.isEmpty()) {
            if (manifest.declaredKinds.contains("item") && !files.containsKey("items.json")) {
                problems.add("info.json says the mod provides items, but there is no items.json.");
            }
            if (!manifest.declaredKinds.contains("item") && !items.isEmpty()) {
                warnings.add("items.json has items but info.json's \"provides\" does not list \"item\".");
            }
            for (String kind : manifest.declaredKinds) {
                if (kind.equals("item")) continue;
                String list = listFor(kind);
                if (!ignored.contains(list)) warnings.add("info.json lists \"" + kind + "\" but " + list + " was not found.");
            }
        }

        if (!problems.isEmpty()) return new ModPackage(null, new ArrayList<>(), new LinkedHashMap<>(), null, problems, warnings, new ArrayList<>(), ignored, total, zip);
        return new ModPackage(manifest, items, files, icon, problems, warnings, kinds, ignored, total, zip);
    }

    // ---- textures ----

    private static void checkTextures(String modId, List<ModItems.ItemDef> items, Map<String, byte[]> files, List<String> problems) {
        for (ModItems.ItemDef item : items) {
            int colon = item.texture().indexOf(':');
            String namespace = item.texture().substring(0, colon);
            String path = item.texture().substring(colon + 1);
            String where = "item \"" + item.id() + "\"";
            if (namespace.equals("minecraft")) continue; // reusing a game texture
            if (!path.startsWith("item/") && !path.startsWith("block/")) {
                problems.add(where + ": texture \"" + item.texture() + "\" must be inside textures/item/ or textures/block/, or the game will not load it.");
                continue;
            }
            String file = "assets/" + namespace + "/textures/" + path + ".png";
            byte[] png = files.get(file);
            if (png == null) {
                problems.add(where + ": the texture file " + file + " is missing from the zip.");
            } else if (!isPng(png)) {
                problems.add(where + ": " + file + " is not a PNG image.");
            } else {
                int[] size = pngSize(png);
                if (size == null || size[0] <= 0 || size[1] <= 0 || size[0] > MAX_TEXTURE_SIZE || size[1] > MAX_TEXTURE_SIZE) {
                    problems.add(where + ": " + file + " must be a PNG up to " + MAX_TEXTURE_SIZE + " x " + MAX_TEXTURE_SIZE + " pixels.");
                }
            }
        }
    }

    // ---- file helpers ----

    /** Normalises a zip path, or returns null for anything that could escape the mod's folder. */
    static String cleanPath(String name) {
        if (name == null) return null;
        String path = name.replace('\\', '/');
        if (path.isEmpty() || path.startsWith("/") || path.contains("//") || path.indexOf('\0') >= 0 || path.length() > 240) return null;
        for (char c : path.toCharArray()) if (c < 32 || c == ':') return null;
        for (String segment : path.split("/", -1)) {
            if (segment.equals("..") || segment.equals(".") || segment.isEmpty()) return null;
        }
        return path;
    }

    private static String cleanOrNull(String name) {
        return cleanPath(name);
    }

    /** The file that holds a content kind: block -> blocks.json, entity -> entities.json, and so on. */
    static String listFor(String kind) {
        switch (kind) {
            case "block": return "blocks.json";
            case "entity": return "entities.json";
            case "effect": return "effects.json";
            case "hud": return "hud.json";
            default: return "items.json";
        }
    }

    private static boolean isJunk(String path) {
        return path.startsWith("__MACOSX/") || path.contains("/__MACOSX/") || path.endsWith(".DS_Store") || path.endsWith("Thumbs.db");
    }

    private static boolean keep(String path) {
        if (path.startsWith("assets/")) return true;
        return path.equals("info.json") || path.equals("fabric.mod.json") || path.equals("items.json") || FUTURE_LISTS.contains(path)
            || (!path.contains("/") && path.endsWith(".png"));
    }

    /** "my_mod/" when every file sits inside one folder that holds the manifest, else "". */
    private static String wrapperPrefix(Map<String, byte[]> raw) {
        if (raw.containsKey("info.json") || raw.containsKey("fabric.mod.json")) return "";
        String common = null;
        for (String path : raw.keySet()) {
            int slash = path.indexOf('/');
            if (slash < 0) return "";
            String first = path.substring(0, slash + 1);
            if (common == null) common = first;
            else if (!common.equals(first)) return "";
        }
        if (common != null && (raw.containsKey(common + "info.json") || raw.containsKey(common + "fabric.mod.json"))) return common;
        return "";
    }

    private static byte[] readLimited(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        int read = 0;
        while ((count = in.read(buffer)) > 0) {
            read += count;
            if (read > limit) return null;
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    static String text(byte[] bytes) {
        String value = new String(bytes, StandardCharsets.UTF_8);
        return value.startsWith("﻿") ? value.substring(1) : value;
    }

    private static boolean isJsonObject(byte[] bytes) {
        try {
            JsonElement element = JsonParser.parseString(text(bytes));
            return element.isJsonObject();
        } catch (JsonParseException | IllegalStateException ex) {
            return false;
        }
    }

    public static boolean isPng(byte[] bytes) {
        if (bytes == null || bytes.length < 24) return false;
        for (int i = 0; i < PNG.length; i++) if (bytes[i] != PNG[i]) return false;
        return true;
    }

    /** Width and height from the PNG header, or null. */
    public static int[] pngSize(byte[] bytes) {
        if (!isPng(bytes)) return null;
        int w = ((bytes[16] & 0xFF) << 24) | ((bytes[17] & 0xFF) << 16) | ((bytes[18] & 0xFF) << 8) | (bytes[19] & 0xFF);
        int h = ((bytes[20] & 0xFF) << 24) | ((bytes[21] & 0xFF) << 16) | ((bytes[22] & 0xFF) << 8) | (bytes[23] & 0xFF);
        return new int[] {w, h};
    }

    private static String shorten(String text) {
        if (text == null) return "";
        String clean = text.replaceAll("[\\p{Cntrl}]", "?");
        return clean.length() > 80 ? clean.substring(0, 80) + "..." : clean;
    }
}
