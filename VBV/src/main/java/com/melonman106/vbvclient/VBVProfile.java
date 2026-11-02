package com.melonman106.vbvclient;

import net.lax1dude.eaglercraft.v1_8.profile.EaglerProfile;

/** Bridge to Eagler's persistent username, skin and cape profile. */
public final class VBVProfile {
    private VBVProfile() {}

    public static String name() {
        EaglerProfile.readIfNeeded();
        return EaglerProfile.getName();
    }

    public static void saveName(String name) {
        EaglerProfile.readIfNeeded();
        String safe = name == null ? "" : name.trim().replaceAll("[^A-Za-z0-9]", "_");
        if (safe.length() < 3) safe = (safe + "___").substring(0, 3);
        if (safe.length() > 16) safe = safe.substring(0, 16);
        EaglerProfile.setName(safe);
        EaglerProfile.save();
    }
}
