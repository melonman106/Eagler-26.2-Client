package com.melonman106.vbvclient.punchy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Decides which animation clip one hand plays, advances it and blends between clips. Game-free: the renderer
 * fills in an {@link Input} each frame and reads {@link #poses()} back.
 *
 * <p>What it understands (rule types from the compat files): {@code hand_in} (equipping), {@code attack}
 * (variants {@code var_1..var_3}, cycled per swing; {@code name} when there is only one), {@code mining} and
 * {@code mining_v2} (looping while a block is being broken), {@code useItem} (a right click), {@code drinking}
 * (start/loop/end while eating or drinking), {@code use_bow} / {@code use_bow_shot}, {@code use_crossbow},
 * {@code use_shield} and {@code spear_charge}. Anything else in the pack ({@code interaction}, {@code inspect},
 * fishing rods, ...) is left to the vanilla pose for now.
 *
 * <p>While no clip is active the pose map is empty, which the rig reads as its rest pose.
 */
public final class PunchyController {
    /** Seconds spent blending from the previous pose into a newly started clip. */
    public static final float BLEND = 0.10F;
    /** A swing within this many seconds of the previous one continues the attack combo; later swings restart it. */
    public static final float COMBO_WINDOW = 1.2F;

    /** What the game tells the controller about this hand, once per frame. */
    public static final class Input {
        /** {@code minecraft:iron_sword}; null for an empty hand. */
        public String itemId;
        /** Categories of the held item, least specific first ({@code SWORD}); {@code EMPTY_HAND} when empty. */
        public List<String> categories = List.of("EMPTY_HAND");
        /** Attack key went down this frame. */
        public boolean attackPressed;
        /** Use key went down this frame. */
        public boolean usePressed;
        /** A block is being broken right now. */
        public boolean mining;
        /** This hand's item is being used (bow drawn, shield raised, eating...). */
        public boolean using;
        /** What the use is: BOW, CROSSBOW, BLOCK, EAT, DRINK, SPEAR or OTHER. */
        public String useKind = "OTHER";
        public boolean crouching;
        public boolean criticalHit;
    }

    private enum Phase { NONE, START, LOOP, END, HOLD }

    private final PunchyLibrary library;
    private final PunchyRules rules;
    private final boolean rightHand;

    private boolean started;
    private String itemId;
    private PunchyRules.Resolved resolved;

    // The one-shot layer (equip, attack, right click, bow release). Overrides the base layer while playing.
    private PunchyClip shot;
    private float shotTime;
    // The base layer (mining / using): a clip, how far it has played, and where it is in start-loop-end.
    private PunchyClip base;
    private float baseTime;
    private Phase phase = Phase.NONE;
    private PunchyRules.Rule baseRule;

    private int combo;
    private float sinceLastSwing = 99.0F;
    private float usedSeconds;
    private boolean wasUsing;
    private String lastUseKind = "OTHER";
    private boolean wasMining;

    private final Map<String, BonePose> out = new HashMap<>();
    private final Map<String, BonePose> from = new HashMap<>();
    private float blend = 1.0F;
    private PunchyClip lastActive;
    private boolean hideItem;
    private final List<String> sounds = new ArrayList<>();

    public PunchyController(PunchyLibrary library, PunchyRules rules, boolean rightHand) {
        this.library = library;
        this.rules = rules;
        this.rightHand = rightHand;
    }

    /** The current pose of every bone the active clip animates (read-only use; empty = rest pose). */
    public Map<String, BonePose> poses() {
        return out;
    }

    /** True when the playing clip asks for the held item to be hidden. */
    public boolean itemHidden() {
        return hideItem;
    }

    public String activeClip() {
        PunchyClip c = active();
        return c == null ? null : c.name;
    }

    /** Sound effect keys that fired since the last call (keys of the pack's sounds.json). */
    public List<String> drainSounds() {
        if (sounds.isEmpty()) return List.of();
        List<String> copy = new ArrayList<>(sounds);
        sounds.clear();
        return copy;
    }

    /** Drops everything that is playing (world change, resource reload). */
    public void reset() {
        started = false;
        shot = null;
        base = null;
        phase = Phase.NONE;
        out.clear();
        from.clear();
        lastActive = null;
        hideItem = false;
        sounds.clear();
        combo = 0;
        usedSeconds = 0.0F;
        wasUsing = false;
        wasMining = false;
    }

    private PunchyClip active() {
        return shot != null ? shot : base;
    }

    public void update(Input in, float dt) {
        if (dt < 0.0F) dt = 0.0F;
        if (dt > 0.25F) dt = 0.25F;
        sinceLastSwing += dt;

        boolean changed = !started || !Objects.equals(in.itemId, itemId);
        if (changed) {
            boolean first = !started;
            started = true;
            itemId = in.itemId;
            resolved = rules.resolve(itemId, in.categories);
            base = null;
            phase = Phase.NONE;
            shot = null;
            wasMining = false;
            wasUsing = false;
            if (!first) playShot(clipFor(resolved.first("hand_in"), "name", "var_1"));
        }

        // Mining: a looping base layer while a block is being broken (start -> loop, then end).
        boolean mineable = !resolved.disableMining && (resolved.first("mining_v2") != null || resolved.first("mining") != null);
        boolean mining = mineable && in.mining && !in.using;
        if (mining && !wasMining) startMining();
        else if (!mining && wasMining) stopMining();
        wasMining = mining;

        // Swings. A click while mining is the mining animation; a click that is not mining is an attack.
        if (in.attackPressed && !mining && !in.using) attack(in.criticalHit);
        if (in.usePressed && !in.using) {
            PunchyRules.Rule use = resolved.first("useItem");
            if (use != null) playShot(clipFor(use, "name"));
        }

        // Using: bow draw, shield, eating...
        if (in.using && !wasUsing) startUsing(in);
        else if (!in.using && wasUsing) stopUsing();
        if (in.using) usedSeconds += dt; else usedSeconds = 0.0F;
        if (in.using && phase == Phase.HOLD && baseRule != null && baseRule.type.equals("spear_charge")) {
            PunchyClip stage = library.find(usedSeconds > 1.2F ? baseRule.str("stage_3") : usedSeconds > 0.6F ? baseRule.str("stage_2") : baseRule.str("stage_1"), rightHand);
            if (stage != null && stage != base) {
                base = stage;
                baseTime = 0.0F;
            }
        }
        wasUsing = in.using;
        if (in.using) lastUseKind = in.useKind;

        advance(dt);
        compose(dt);
    }

    // ---------------------------------------------------------------------------------------------- triggers

    private void attack(boolean critical) {
        PunchyRules.Rule rule = resolved.first("attack");
        if (rule == null) return;
        String name = null;
        if (critical) name = rule.str("critical_hit");
        if (name == null) {
            List<String> variants = new ArrayList<>();
            for (String key : new String[]{"var_1", "var_2", "var_3"}) {
                String v = rule.str(key);
                if (v != null) variants.add(v);
            }
            if (variants.isEmpty() && rule.str("name") != null) variants.add(rule.str("name"));
            if (variants.isEmpty()) return;
            if (sinceLastSwing > COMBO_WINDOW) combo = 0;
            name = variants.get(combo % variants.size());
            combo++;
        }
        sinceLastSwing = 0.0F;
        playShot(library.find(name, rightHand));
    }

    private void startMining() {
        PunchyRules.Rule v2 = resolved.first("mining_v2");
        if (v2 != null) {
            PunchyClip start = library.find(v2.str("start"), rightHand);
            baseRule = v2;
            if (start != null) {
                beginBase(start, Phase.START);
            } else {
                PunchyClip loop = library.find(v2.str("looping"), rightHand);
                if (loop != null) beginBase(loop, Phase.LOOP);
            }
            return;
        }
        PunchyRules.Rule m = resolved.first("mining");
        if (m != null) {
            PunchyClip loop = library.find(m.str("name"), rightHand);
            baseRule = m;
            if (loop != null) beginBase(loop, Phase.LOOP);
        }
    }

    private void stopMining() {
        if (baseRule == null || (!baseRule.type.equals("mining") && !baseRule.type.equals("mining_v2"))) return;
        if (baseRule.type.equals("mining_v2")) {
            PunchyClip end = library.find(baseRule.str("end"), rightHand);
            if (end != null) {
                beginBase(end, Phase.END);
                return;
            }
        } else if (!baseRule.bool("stop_immediate") && base != null && base.loop == PunchyClip.Loop.LOOP) {
            // Let the loop run to the end of its cycle instead of cutting it off.
            phase = Phase.END;
            return;
        }
        endBase();
    }

    private void startUsing(Input in) {
        PunchyRules.Rule rule = null;
        PunchyClip clip = null;
        switch (in.useKind) {
            case "BOW" -> {
                rule = resolved.first("use_bow");
                if (rule != null) {
                    String name = in.crouching ? rule.variation("crouching") : null;
                    clip = library.find(name != null ? name : rule.str("name"), rightHand);
                }
            }
            case "CROSSBOW" -> {
                rule = resolved.first("use_crossbow");
                if (rule != null) clip = library.find(rule.str("name"), rightHand);
            }
            case "BLOCK" -> {
                rule = resolved.first("use_shield");
                if (rule != null) clip = library.find(rule.str("name"), rightHand);
            }
            case "SPEAR" -> {
                rule = resolved.first("spear_charge");
                if (rule != null) clip = library.find(rule.str("stage_1"), rightHand);
            }
            case "EAT", "DRINK" -> {
                rule = resolved.first("drinking");
                if (rule != null) {
                    PunchyClip start = library.find(rule.str("start"), rightHand);
                    baseRule = rule;
                    if (start != null) {
                        beginBase(start, Phase.START);
                    } else {
                        PunchyClip loop = library.find(rule.str("looping"), rightHand);
                        if (loop != null) beginBase(loop, Phase.LOOP);
                    }
                    return;
                }
                rule = resolved.first("useItem");
                if (rule != null) {
                    clip = library.find(rule.str("name"), rightHand);
                    if (clip != null) {
                        baseRule = rule;
                        beginBase(clip, clip.loop == PunchyClip.Loop.LOOP ? Phase.LOOP : Phase.HOLD);
                        return;
                    }
                }
            }
            default -> { }
        }
        if (clip != null) {
            baseRule = rule;
            beginBase(clip, Phase.HOLD);
        }
    }

    private void stopUsing() {
        if (baseRule == null) return;
        String type = baseRule.type;
        if (type.equals("drinking")) {
            PunchyClip end = library.find(baseRule.str("end"), rightHand);
            if (end != null) {
                beginBase(end, Phase.END);
                return;
            }
        } else if (type.equals("use_bow") && usedSeconds > 0.15F) {
            PunchyRules.Rule shotRule = resolved.first("use_bow_shot");
            if (shotRule != null) {
                String name = shotRule.str("name");
                PunchyClip clip = library.find(name, rightHand);
                if (clip != null) {
                    base = null;
                    phase = Phase.NONE;
                    playShot(clip);
                    return;
                }
            }
        }
        if (type.equals("use_bow") || type.equals("use_crossbow") || type.equals("use_shield")
            || type.equals("spear_charge") || type.equals("drinking") || type.equals("useItem")) {
            endBase();
        }
    }

    private PunchyClip clipFor(PunchyRules.Rule rule, String... keys) {
        if (rule == null) return null;
        return library.find(rule.firstOf(keys), rightHand);
    }

    // ------------------------------------------------------------------------------------------------ playback

    private void playShot(PunchyClip clip) {
        if (clip == null) return;
        snapshot();
        shot = clip;
        shotTime = 0.0F;
        fireSounds(clip, -1.0F, 0.0F);
    }

    private void beginBase(PunchyClip clip, Phase p) {
        snapshot();
        base = clip;
        baseTime = 0.0F;
        phase = p;
        fireSounds(clip, -1.0F, 0.0F);
    }

    private void endBase() {
        if (base == null) return;
        snapshot();
        base = null;
        phase = Phase.NONE;
        baseRule = null;
    }

    /** Remembers what is on screen now so the next clip blends in from it instead of popping. */
    private void snapshot() {
        from.clear();
        for (Map.Entry<String, BonePose> e : out.entrySet()) from.put(e.getKey(), new BonePose().set(e.getValue()));
        blend = 0.0F;
    }

    private void advance(float dt) {
        if (shot != null) {
            float step = dt * shot.speed;
            fireSounds(shot, shotTime, shotTime + step);
            shotTime += step;
            if (shot.finished(shotTime)) shot = null;
        }
        if (base != null) {
            float step = dt * base.speed;
            fireSounds(base, baseTime, baseTime + step);
            baseTime += step;
            if (base.loop == PunchyClip.Loop.LOOP) {
                if (phase == Phase.END && baseTime >= base.length) endBase();
            } else if (base.finished(baseTime)) {
                nextPhase();
            }
        }
    }

    /** A non-looping base clip reached its end: start -> loop, end -> nothing, hold -> stay on the last frame. */
    private void nextPhase() {
        switch (phase) {
            case START -> {
                PunchyClip loop = baseRule == null ? null : library.find(baseRule.str("looping"), rightHand);
                if (loop != null) {
                    base = loop;
                    baseTime = 0.0F;
                    phase = Phase.LOOP;
                } else {
                    endBase();
                }
            }
            case END -> endBase();
            case HOLD, LOOP, NONE -> baseTime = base.length;
        }
    }

    private void fireSounds(PunchyClip clip, float previous, float now) {
        for (PunchyClip.Sound s : clip.sounds) {
            if (s.time() > previous && s.time() <= now) sounds.add(s.effect());
        }
    }

    private void compose(float dt) {
        PunchyClip clip = active();
        hideItem = clip != null && clip.hidesItem;
        if (clip != lastActive) {
            // Moving between clips of the same layer (start -> loop) is seamless; blending only matters on a snapshot.
            lastActive = clip;
        }
        if (blend < 1.0F) blend = Math.min(1.0F, blend + dt / BLEND);

        Map<String, BonePose> target = new HashMap<>();
        if (clip != null) {
            float t = shot != null ? shotTime : baseTime;
            for (String bone : clip.bones()) {
                BonePose p = new BonePose();
                clip.sample(bone, t, p);
                target.put(bone, p);
            }
        }
        out.clear();
        if (blend >= 1.0F || from.isEmpty()) {
            out.putAll(target);
            if (blend >= 1.0F) from.clear();
            return;
        }
        // Smooth-step so the start and end of the blend are gentle.
        float f = blend * blend * (3.0F - 2.0F * blend);
        java.util.Set<String> bones = new java.util.HashSet<>(target.keySet());
        bones.addAll(from.keySet());
        for (String bone : bones) {
            BonePose a = from.containsKey(bone) ? new BonePose().set(from.get(bone)) : new BonePose();
            BonePose b = target.containsKey(bone) ? target.get(bone) : new BonePose();
            out.put(bone, a.lerpTo(b, f));
        }
    }
}
