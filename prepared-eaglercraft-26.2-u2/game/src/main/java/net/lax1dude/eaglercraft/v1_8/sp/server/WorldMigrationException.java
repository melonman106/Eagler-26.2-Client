package net.lax1dude.eaglercraft.v1_8.sp.server;

import java.io.IOException;

/** An existing save needs conversion; it must never be treated as missing terrain. */
public final class WorldMigrationException extends IOException {
    public WorldMigrationException(String message) {
        super(message);
    }

    public static WorldMigrationException find(Throwable failure) {
        // Future and crash-report wrappers can be nested. Bound malformed cause chains.
        for (int depth = 0; failure != null && depth < 32; depth++) {
            if (failure instanceof WorldMigrationException migration) {
                return migration;
            }
            Throwable next = failure.getCause();
            if (next == failure) {
                break;
            }
            failure = next;
        }
        return null;
    }
}
