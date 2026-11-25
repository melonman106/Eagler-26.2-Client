package com.melonman106.vbvclient.punchy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One animation from a Blockbench/Bedrock {@code .animation.json} (the format Punchy resource packs use).
 *
 * <p>Only what the packs actually contain is read: per-bone {@code rotation}/{@code position}/{@code scale}
 * keyframes (plain numbers, linearly interpolated), {@code loop}, {@code animation_length}, the {@code timeline}
 * command strings and {@code sound_effects}. Molang strings, Catmull-Rom keyframes and the other Bedrock features
 * are not used by these packs and are ignored (a keyframe that is not three numbers is skipped).
 *
 * <p>This class uses no game classes, so the plain-JVM tests exercise it directly.
 */
public final class PunchyClip {
    public enum Loop { ONCE, LOOP, HOLD }

    /** A timeline entry: the commands that fire when playback passes {@code time}. */
    public record Event(float time, List<String> commands) {}

    /** A sound that fires when playback passes {@code time}. {@code effect} is a key of the pack's sounds.json. */
    public record Sound(float time, String effect) {}

    /** Keyframes of one channel. {@code times} is ascending; {@code values[i]} is an {x, y, z} triple. */
    static final class Channel {
        final float[] times;
        final float[][] values;

        Channel(float[] times, float[][] values) {
            this.times = times;
            this.values = values;
        }

        void sample(float t, float[] out) {
            int n = times.length;
            if (n == 1 || t <= times[0]) {
                System.arraycopy(values[0], 0, out, 0, 3);
                return;
            }
            if (t >= times[n - 1]) {
                System.arraycopy(values[n - 1], 0, out, 0, 3);
                return;
            }
            // Binary search for the keyframe pair around t.
            int lo = 0;
            int hi = n - 1;
            while (hi - lo > 1) {
                int mid = (lo + hi) >>> 1;
                if (times[mid] <= t) lo = mid; else hi = mid;
            }
            float span = times[hi] - times[lo];
            float f = span <= 1.0E-6F ? 1.0F : (t - times[lo]) / span;
            float[] a = values[lo];
            float[] b = values[hi];
            out[0] = a[0] + (b[0] - a[0]) * f;
            out[1] = a[1] + (b[1] - a[1]) * f;
            out[2] = a[2] + (b[2] - a[2]) * f;
        }
    }

    /** The three channels of one bone; any may be {@code null} (no animation of that channel). */
    static final class Track {
        Channel rotation;
        Channel position;
        Channel scale;
    }

    public final String name;
    public final float length;
    public final Loop loop;
    /** Bone name -> its channels. */
    final Map<String, Track> tracks;
    public final List<Event> events;
    public final List<Sound> sounds;
    /** {@code anim_speed_X} from the timeline (playback speed multiplier), 1 when absent. */
    public final float speed;
    /** True when a timeline command asks for the held item to be hidden while this clip plays. */
    public final boolean hidesItem;

    private PunchyClip(String name, float length, Loop loop, Map<String, Track> tracks, List<Event> events,
                       List<Sound> sounds, float speed, boolean hidesItem) {
        this.name = name;
        this.length = length;
        this.loop = loop;
        this.tracks = tracks;
        this.events = events;
        this.sounds = sounds;
        this.speed = speed;
        this.hidesItem = hidesItem;
    }

    /** Names of the bones this clip animates. */
    public java.util.Set<String> bones() {
        return java.util.Collections.unmodifiableSet(tracks.keySet());
    }

    public boolean animates(String bone) {
        return tracks.containsKey(bone);
    }

    /**
     * Wraps a playback time into the clip: looping clips repeat, the others stop on their last frame
     * ({@code play_once} clips are expected to be removed by the player once {@link #finished} is true).
     */
    public float wrap(float time) {
        if (length <= 0.0F) return 0.0F;
        if (loop == Loop.LOOP) {
            float t = time % length;
            return t < 0.0F ? t + length : t;
        }
        return Math.max(0.0F, Math.min(length, time));
    }

    public boolean finished(float time) {
        return loop != Loop.LOOP && time >= length;
    }

    /**
     * Samples one bone at {@code time} (seconds, wrapped for looping clips) and writes the result into {@code pose}.
     * Returns false (leaving {@code pose} untouched) when the clip does not animate this bone.
     */
    public boolean sample(String bone, float time, BonePose pose) {
        Track track = tracks.get(bone);
        if (track == null) return false;
        float t = wrap(time);
        if (track.rotation != null) track.rotation.sample(t, pose.rot);
        if (track.position != null) track.position.sample(t, pose.pos);
        if (track.scale != null) track.scale.sample(t, pose.scale);
        return true;
    }

    // ------------------------------------------------------------------------------------------------ parsing

    /** Parses one entry of the file's {@code animations} object. Never throws for odd data; it skips what it cannot read. */
    public static PunchyClip parse(String name, JsonObject anim) {
        float length = number(anim.get("animation_length"), 0.0F);
        Loop loop = Loop.ONCE;
        JsonElement loopElement = anim.get("loop");
        if (loopElement != null && loopElement.isJsonPrimitive()) {
            String text = loopElement.getAsString();
            if ("true".equalsIgnoreCase(text)) loop = Loop.LOOP;
            else if ("hold_on_last_frame".equalsIgnoreCase(text)) loop = Loop.HOLD;
        }

        Map<String, Track> tracks = new LinkedHashMap<>();
        float lastKey = 0.0F;
        JsonElement bonesElement = anim.get("bones");
        if (bonesElement != null && bonesElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> bone : bonesElement.getAsJsonObject().entrySet()) {
                if (!bone.getValue().isJsonObject()) continue;
                JsonObject channels = bone.getValue().getAsJsonObject();
                Track track = new Track();
                track.rotation = channel(channels.get("rotation"));
                track.position = channel(channels.get("position"));
                track.scale = channel(channels.get("scale"));
                if (track.rotation == null && track.position == null && track.scale == null) continue;
                for (Channel c : new Channel[]{track.rotation, track.position, track.scale}) {
                    if (c != null) lastKey = Math.max(lastKey, c.times[c.times.length - 1]);
                }
                tracks.put(bone.getKey(), track);
            }
        }
        if (length <= 0.0F) length = lastKey;

        List<Event> events = new ArrayList<>();
        float speed = 1.0F;
        boolean hides = false;
        JsonElement timeline = anim.get("timeline");
        if (timeline != null && timeline.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : timeline.getAsJsonObject().entrySet()) {
                Float time = parseTime(entry.getKey());
                if (time == null) continue;
                List<String> commands = new ArrayList<>();
                JsonElement value = entry.getValue();
                if (value.isJsonArray()) {
                    for (JsonElement e : value.getAsJsonArray()) addCommands(commands, e);
                } else {
                    addCommands(commands, value);
                }
                for (String command : commands) {
                    if (command.startsWith("anim_speed_")) {
                        Float v = parseFloat(command.substring("anim_speed_".length()));
                        if (v != null && v > 0.05F && v < 20.0F) speed = v;
                    } else if (command.equals("hide_item")) {
                        hides = true;
                    }
                }
                if (!commands.isEmpty()) events.add(new Event(time, List.copyOf(commands)));
            }
            events.sort((a, b) -> Float.compare(a.time(), b.time()));
        }

        List<Sound> sounds = new ArrayList<>();
        JsonElement soundElement = anim.get("sound_effects");
        if (soundElement != null && soundElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : soundElement.getAsJsonObject().entrySet()) {
                Float time = parseTime(entry.getKey());
                if (time == null) continue;
                JsonElement value = entry.getValue();
                if (value.isJsonObject()) {
                    JsonElement effect = value.getAsJsonObject().get("effect");
                    if (effect != null && effect.isJsonPrimitive()) sounds.add(new Sound(time, effect.getAsString()));
                } else if (value.isJsonArray()) {
                    for (JsonElement e : value.getAsJsonArray()) {
                        if (e.isJsonObject() && e.getAsJsonObject().has("effect")) {
                            sounds.add(new Sound(time, e.getAsJsonObject().get("effect").getAsString()));
                        }
                    }
                }
            }
            sounds.sort((a, b) -> Float.compare(a.time(), b.time()));
        }
        return new PunchyClip(name, length, loop, tracks, List.copyOf(events), List.copyOf(sounds), speed, hides);
    }

    private static void addCommands(List<String> out, JsonElement element) {
        if (element == null || !element.isJsonPrimitive()) return;
        for (String part : element.getAsString().split(";")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
    }

    private static Channel channel(JsonElement element) {
        if (element == null) return null;
        if (element.isJsonArray()) {
            // A constant: "rotation": [x, y, z]
            float[] v = triple(element);
            return v == null ? null : new Channel(new float[]{0.0F}, new float[][]{v});
        }
        if (!element.isJsonObject()) return null;
        List<float[]> keys = new ArrayList<>(); // {time, x, y, z}
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            Float time = parseTime(entry.getKey());
            if (time == null) continue;
            float[] v = triple(entry.getValue());
            if (v == null) continue;
            keys.add(new float[]{time, v[0], v[1], v[2]});
        }
        if (keys.isEmpty()) return null;
        keys.sort((a, b) -> Float.compare(a[0], b[0]));
        float[] times = new float[keys.size()];
        float[][] values = new float[keys.size()][];
        for (int i = 0; i < times.length; i++) {
            float[] k = keys.get(i);
            times[i] = k[0];
            values[i] = new float[]{k[1], k[2], k[3]};
        }
        return new Channel(times, values);
    }

    /** {@code [x, y, z]} of plain numbers; anything else (Molang strings, Bedrock {@code {"post": ...}} frames) is skipped. */
    private static float[] triple(JsonElement element) {
        if (element == null || !element.isJsonArray()) return null;
        JsonArray array = element.getAsJsonArray();
        if (array.size() != 3) return null;
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) {
            JsonElement e = array.get(i);
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return null;
            out[i] = e.getAsFloat();
        }
        return out;
    }

    private static float number(JsonElement element, float fallback) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return fallback;
        return element.getAsFloat();
    }

    private static Float parseTime(String text) {
        Float v = parseFloat(text);
        return v == null || v < 0.0F ? null : v;
    }

    private static Float parseFloat(String text) {
        try {
            float v = Float.parseFloat(text.trim());
            return Float.isNaN(v) || Float.isInfinite(v) ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "PunchyClip{" + name + ", " + length + "s, " + loop + ", bones=" + Arrays.toString(tracks.keySet().toArray()) + "}";
    }
}
