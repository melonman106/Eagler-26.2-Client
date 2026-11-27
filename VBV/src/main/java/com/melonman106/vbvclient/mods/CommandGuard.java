package com.melonman106.vbvclient.mods;

import java.util.Locale;
import java.util.Set;

/**
 * The checks a mod's command text must pass before it may run. Commands are the one strong tool a mod has, so the
 * rules are fixed here and applied twice: when the mod is read (a refused command is dropped with a message) and again
 * when it is about to run.
 *
 * <p>No game classes are used, so this runs on a plain JVM.</p>
 */
public final class CommandGuard {
    private CommandGuard() {}

    public static final int MAX_LENGTH = 512;
    /** The highest permission level a mod's commands may run at ("like a data pack's functions"). */
    public static final int MAX_LEVEL = 2;
    /** Commands that are refused whatever the permission level, wherever they appear in a command chain. */
    public static final Set<String> DENIED = Set.of("op", "deop", "stop", "ban", "ban-ip", "banlist", "pardon",
        "pardon-ip", "whitelist", "save-all", "save-on", "save-off", "setidletimeout", "publish", "debug", "perf", "jfr",
        "transfer", "reload", "kick");

    /** Why the command may not run, or {@code null} when it may. */
    public static String check(String command) {
        if (command == null) return "the command is missing";
        String text = command.trim();
        if (text.startsWith("/")) text = text.substring(1).trim();
        if (text.isEmpty()) return "the command is empty";
        if (text.length() > MAX_LENGTH) return "the command is longer than " + MAX_LENGTH + " characters";
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < 32) return "the command contains a control character";
        }
        String[] words = text.split(" +");
        String first = commandWord(words[0]);
        if (DENIED.contains(first)) return "\"" + first + "\" is not allowed in mods";
        // A command can run another one: "execute as @a run op @s", also "return run ...". Check every command that follows
        // a "run". (A word "run" used as an ordinary argument is checked too; refusing it is harmless.)
        for (int i = 1; i + 1 < words.length; i++) {
            if (!words[i].equals("run")) continue;
            String inner = commandWord(words[i + 1]);
            if (DENIED.contains(inner)) return "\"" + inner + "\" is not allowed in mods (found after \"run\")";
        }
        return null;
    }

    /** The mod's permission level clamped into 0..{@link #MAX_LEVEL}. */
    public static int clampLevel(int level) {
        return Math.max(0, Math.min(MAX_LEVEL, level));
    }

    private static String commandWord(String word) {
        String lower = word.toLowerCase(Locale.ROOT);
        if (lower.startsWith("/")) lower = lower.substring(1);
        if (lower.startsWith("minecraft:")) lower = lower.substring("minecraft:".length());
        return lower;
    }
}
