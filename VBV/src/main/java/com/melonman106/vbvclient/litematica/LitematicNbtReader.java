package com.melonman106.vbvclient.litematica;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Browser-client-safe core reader for vanilla NBT-based .litematic files.
 *
 * This deliberately has no Fabric, Mixin, or Minecraft dependencies so it can
 * compile into Eaglercraft's TeaVM client. Rendering and screen integration are
 * kept outside the file-format layer.
 */
public final class LitematicNbtReader {
    private static final int MAX_DEPTH = 64;
    private static final int MAX_COLLECTION = 16_000_000;

    private LitematicNbtReader() {}

    public static Schematic readGzip(InputStream source) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(source)))) {
            byte type = in.readByte();
            if (type != 10) throw new IOException("Litematic root tag must be a compound");
            readUtf(in); // root name
            Object root = readPayload(in, type, 0);
            if (!(root instanceof Map<?, ?> rootMap)) throw new IOException("Invalid NBT root compound");
            Object regionsValue = child(child(rootMap, "Regions"), ""); // never used; validates compound handling
            Object rawRegions = rootMap.get("Regions");
            if (!(rawRegions instanceof Map<?, ?> regions)) throw new IOException("Missing Regions compound");
            List<Region> result = new ArrayList<>();
            for (Map.Entry<?, ?> entry : regions.entrySet()) {
                if (!(entry.getKey() instanceof String name) || !(entry.getValue() instanceof Map<?, ?> region)) continue;
                int[] position = intVector(region.get("Position"));
                int[] size = intVector(region.get("Size"));
                if (position == null || size == null) continue;
                int sx = Math.abs(size[0]), sy = Math.abs(size[1]), sz = Math.abs(size[2]);
                long volume = (long) sx * sy * sz;
                if (volume > MAX_COLLECTION) throw new IOException("Region too large: " + name + " (" + volume + " blocks)");
                List<String> palette = readPalette(region.get("BlockStatePalette"));
                long[] packed = region.get("BlockStates") instanceof long[] values ? values : new long[0];
                int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, palette.size() - 1)));
                int perLong = 64 / bits;
                long expected = (volume + perLong - 1) / perLong;
                if (packed.length < expected) throw new IOException("Truncated BlockStates in region " + name);
                result.add(new Region(name, position[0], position[1], position[2], sx, sy, sz,
                        palette, packed, bits, perLong));
            }
            if (result.isEmpty()) throw new IOException("No valid schematic regions found");
            return new Schematic(result);
        }
    }

    private static Object child(Object value, String key) {
        return value instanceof Map<?, ?> map ? map.get(key) : null;
    }

    private static Object readPayload(DataInputStream in, int type, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT nesting is too deep");
        switch (type) {
            case 1: return in.readByte();
            case 2: return in.readShort();
            case 3: return in.readInt();
            case 4: return in.readLong();
            case 5: return in.readFloat();
            case 6: return in.readDouble();
            case 7: {
                int length = checkedLength(in.readInt());
                byte[] values = new byte[length];
                in.readFully(values);
                return values;
            }
            case 8: return readUtf(in);
            case 9: {
                int elementType = in.readUnsignedByte();
                int length = checkedLength(in.readInt());
                List<Object> list = new ArrayList<>(Math.min(length, 1_000_000));
                for (int i = 0; i < length; i++) list.add(readPayload(in, elementType, depth + 1));
                return list;
            }
            case 10: {
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    int childType = in.readUnsignedByte();
                    if (childType == 0) break;
                    String name = readUtf(in);
                    map.put(name, readPayload(in, childType, depth + 1));
                }
                return map;
            }
            case 11: {
                int length = checkedLength(in.readInt());
                int[] values = new int[length];
                for (int i = 0; i < length; i++) values[i] = in.readInt();
                return values;
            }
            case 12: {
                int length = checkedLength(in.readInt());
                long[] values = new long[length];
                for (int i = 0; i < length; i++) values[i] = in.readLong();
                return values;
            }
            default: throw new IOException("Unknown NBT tag type: " + type);
        }
    }

    private static int checkedLength(int length) throws IOException {
        if (length < 0 || length > MAX_COLLECTION) throw new IOException("Invalid NBT collection length: " + length);
        return length;
    }

    private static String readUtf(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static int[] intVector(Object value) {
        if (!(value instanceof Map<?, ?> map)) return null;
        Object x = map.get("x"), y = map.get("y"), z = map.get("z");
        if (!(x instanceof Number) || !(y instanceof Number) || !(z instanceof Number)) return null;
        return new int[] { ((Number) x).intValue(), ((Number) y).intValue(), ((Number) z).intValue() };
    }

    private static List<String> readPalette(Object value) throws IOException {
        if (!(value instanceof List<?> list) || list.isEmpty()) throw new IOException("Missing BlockStatePalette");
        List<String> palette = new ArrayList<>(list.size());
        for (Object element : list) {
            if (!(element instanceof Map<?, ?> block)) throw new IOException("Invalid palette entry");
            Object name = block.get("Name");
            if (!(name instanceof String)) throw new IOException("Palette entry has no block Name");
            palette.add((String) name);
        }
        return Collections.unmodifiableList(palette);
    }

    public static final class Schematic {
        private final List<Region> regions;
        private Schematic(List<Region> regions) { this.regions = Collections.unmodifiableList(regions); }
        public List<Region> regions() { return regions; }
        public long blockCount() {
            long count = 0;
            for (Region region : regions) count += (long) region.sizeX * region.sizeY * region.sizeZ;
            return count;
        }
    }

    public static final class Region {
        public final String name;
        public final int x, y, z, sizeX, sizeY, sizeZ;
        private final List<String> palette;
        private final long[] states;
        private final int bits, valuesPerLong;

        private Region(String name, int x, int y, int z, int sizeX, int sizeY, int sizeZ,
                       List<String> palette, long[] states, int bits, int valuesPerLong) {
            this.name = name; this.x = x; this.y = y; this.z = z;
            this.sizeX = sizeX; this.sizeY = sizeY; this.sizeZ = sizeZ;
            this.palette = palette; this.states = states; this.bits = bits; this.valuesPerLong = valuesPerLong;
        }
        public List<String> palette() { return palette; }
        public int blockStateIndex(int localX, int localY, int localZ) {
            if (localX < 0 || localY < 0 || localZ < 0 ||
                    localX >= sizeX || localY >= sizeY || localZ >= sizeZ) return -1;
            long index = (long) localX + (long) localZ * sizeX + (long) localY * sizeX * sizeZ;
            int longIndex = (int) (index / valuesPerLong);
            int offset = (int) ((index % valuesPerLong) * bits);
            if (longIndex >= states.length) return -1;
            return (int) ((states[longIndex] >>> offset) & ((1L << bits) - 1L));
        }
        public String blockName(int localX, int localY, int localZ) {
            int index = blockStateIndex(localX, localY, localZ);
            return index >= 0 && index < palette.size() ? palette.get(index) : null;
        }
    }
}
