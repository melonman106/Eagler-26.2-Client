package net.lax1dude.eaglercraft.v1_8.teavmcompat.dfu;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounds completed DFU rewrites; the separate pending-rewrite cache is untouched. */
public final class DfuRewriteCache {
    private static final int LIMIT = 4096;

    private DfuRewriteCache() {
    }

    public static Map<Object, Object> create() {
        return Collections.synchronizedMap(new LinkedHashMap<Object, Object>(LIMIT, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
                return size() > LIMIT;
            }
        });
    }
}
