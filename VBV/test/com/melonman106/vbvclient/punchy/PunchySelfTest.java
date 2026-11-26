package com.melonman106.vbvclient.punchy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Plain-JVM checks for the game-free Punchy core. Run with VBV/test/run-punchy-core-tests.sh.
 * {@code -Dvbv.punchy=<dir>} (the root of an unzipped Punchy resource pack, e.g. HyperPunchy) adds checks against
 * the real files; without it only the synthetic fixtures below run.
 */
public final class PunchySelfTest {
    private PunchySelfTest() {}

    static int checks;
    static int failures;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    static boolean near(float a, float b, float eps) {
        return Math.abs(a - b) <= eps;
    }

    public static void main(String[] args) throws IOException {
        ClipTests.run();
        RulesTests.run();
        RigTests.run();
        ControllerTests.run();
        String dir = System.getProperty("vbv.punchy", "");
        if (!dir.isEmpty() && Files.isDirectory(Path.of(dir))) RealPackTests.run(Path.of(dir));
        else System.out.println("(no -Dvbv.punchy pack directory: real-pack checks skipped)");
        System.out.println(checks + " checks, " + failures + " failed");
        if (failures > 0) System.exit(1);
    }

    // ------------------------------------------------------------------------------------------------ helpers

    static PunchyLibrary library(String... fileJson) {
        PunchyLibrary lib = new PunchyLibrary();
        for (int i = 0; i < fileJson.length; i++) lib.add("f" + i + ".json", fileJson[i]);
        return lib;
    }

    static String anims(String body) {
        return "{\"format_version\":\"1.8.0\",\"animations\":{" + body + "}}";
    }

    /** One clip: {@code name} of {@code length} seconds moving right_arm's position x from 0 to {@code dx}. */
    static String clip(String name, String loop, float length, float dx) {
        return "\"" + name + "\":{\"loop\":" + loop + ",\"animation_length\":" + length
            + ",\"bones\":{\"right_arm\":{\"position\":{\"0.0\":[0,0,0],\"" + length + "\":[" + dx + ",0,0]}}}}";
    }

    static float posX(PunchyController c) {
        BonePose p = c.poses().get("right_arm");
        return p == null ? 0.0F : p.pos[0];
    }

    // -------------------------------------------------------------------------------------------------- clips

    static final class ClipTests {
        static void run() {
            PunchyLibrary lib = library(anims(
                "\"a\":{\"loop\":\"play_once\",\"animation_length\":1.0,\"bones\":{"
                    + "\"right_arm\":{\"rotation\":{\"0.0\":[0,0,0],\"0.5\":[10,20,30],\"1.0\":[0,0,0]},"
                    + "\"position\":[1,2,3],\"scale\":{\"0.0\":[1,1,1],\"1.0\":[2,2,2]}}}},"
                    + "\"loopy\":{\"loop\":true,\"animation_length\":2.0,\"bones\":{\"right_arm\":{\"position\":{\"0\":[0,0,0],\"2\":[4,0,0]}}}},"
                    + "\"held\":{\"loop\":\"hold_on_last_frame\",\"animation_length\":1.0,\"bones\":{\"right_arm\":{\"position\":{\"0\":[0,0,0],\"1\":[8,0,0]}}}},"
                    + "\"nolen\":{\"bones\":{\"right_arm\":{\"position\":{\"0\":[0,0,0],\"0.75\":[1,0,0]}}}},"
                    + "\"junk\":{\"animation_length\":1,\"bones\":{\"right_arm\":{\"rotation\":{\"0\":[\"math.sin(q.life_time)\",0,0],\"0.5\":[1,2,3]},"
                    + "\"position\":{\"0\":[0,0]}},\"empty\":{}}},"
                    + "\"meta\":{\"animation_length\":1.0,\"timeline\":{\"0.0\":\"anim_speed_1.5;dual_handed;\",\"0.5\":\"hide_item\"},"
                    + "\"sound_effects\":{\"0.2\":{\"effect\":\"attack_1\"},\"0.1\":{\"effect\":\"first\"}},\"bones\":{}}"));
            PunchyClip a = lib.get("a");
            BonePose p = new BonePose();
            check(a != null && a.loop == PunchyClip.Loop.ONCE && near(a.length, 1.0F, 1e-6F), "play_once clip parsed with its length");
            a.sample("right_arm", 0.25F, p);
            check(near(p.rot[0], 5F, 1e-4F) && near(p.rot[1], 10F, 1e-4F) && near(p.rot[2], 15F, 1e-4F), "rotation interpolates linearly");
            a.sample("right_arm", 0.5F, p);
            check(near(p.rot[1], 20F, 1e-4F), "keyframe value hit exactly");
            check(near(p.pos[0], 1F, 1e-6F) && near(p.pos[2], 3F, 1e-6F), "a bare [x,y,z] channel is a constant");
            a.sample("right_arm", 0.75F, p);
            check(near(p.rot[1], 10F, 1e-4F), "second half interpolates back");
            check(near(p.scale[0], 1.75F, 1e-4F), "scale interpolates");
            a.sample("right_arm", 5F, p);
            check(near(p.rot[0], 0F, 1e-6F) && near(p.scale[1], 2F, 1e-6F), "play_once clamps to its last frame");
            check(a.finished(1.0F) && !a.finished(0.9F), "play_once finishes at its length");
            check(!a.sample("left_arm", 0F, p), "sampling an unanimated bone reports false");
            check(a.animates("right_arm") && !a.animates("left_arm"), "animates()");

            PunchyClip loopy = lib.get("loopy");
            loopy.sample("right_arm", 3.0F, p);
            check(loopy.loop == PunchyClip.Loop.LOOP && near(p.pos[0], 2F, 1e-4F), "looping clip wraps (3s of 2s -> 1s)");
            check(!loopy.finished(100F), "looping clips never finish");
            check(near(loopy.wrap(-0.5F), 1.5F, 1e-5F), "negative time wraps forward");

            PunchyClip held = lib.get("held");
            held.sample("right_arm", 9F, p);
            check(held.loop == PunchyClip.Loop.HOLD && near(p.pos[0], 8F, 1e-5F), "hold_on_last_frame stays on the last frame");

            PunchyClip nolen = lib.get("nolen");
            check(near(nolen.length, 0.75F, 1e-6F), "missing animation_length falls back to the last keyframe");

            PunchyClip junk = lib.get("junk");
            p.reset();
            junk.sample("right_arm", 0.25F, p);
            check(junk != null && !junk.animates("empty"), "a bone with no usable channel is dropped");
            // The Molang frame at t=0 is skipped, leaving the single keyframe at 0.5 as a constant.
            check(near(p.rot[0], 1F, 1e-6F) && near(p.rot[2], 3F, 1e-6F), "a keyframe that is not three numbers is skipped, not fatal");
            check(near(p.pos[0], 0F, 1e-6F), "a two-number position is ignored");

            PunchyClip meta = lib.get("meta");
            check(near(meta.speed, 1.5F, 1e-6F), "anim_speed_X sets the speed");
            check(meta.hidesItem, "hide_item flag");
            check(meta.events.size() == 2 && meta.events.get(0).commands().contains("dual_handed"), "timeline commands are split on ';'");
            check(meta.sounds.size() == 2 && meta.sounds.get(0).effect().equals("first"), "sounds are sorted by time");

            PunchyLibrary bad = new PunchyLibrary();
            check(bad.add("x.json", "not json") == 0 && bad.problems().containsKey("x.json"), "bad JSON is reported, not thrown");
            check(bad.add("y.json", "{\"nope\":1}") == 0 && bad.problems().containsKey("y.json"), "no animations object is reported");
            check(bad.add("z.json", "[1,2]") == 0, "wrong root type is survived");

            PunchyLibrary pair = library(anims(clip("use_bow_left", "true", 1, 1) + "," + clip("use_bow_right", "true", 1, 2) + "," + clip("plain", "true", 1, 3)));
            check(pair.find("use_bow", true).name.equals("use_bow_right"), "rule name picks the _right clip for the right hand");
            check(pair.find("use_bow", false).name.equals("use_bow_left"), "rule name picks the _left clip for the left hand");
            check(pair.find("plain", true).name.equals("plain"), "an unsided name falls back to the plain clip");
            check(pair.find("missing", true) == null && pair.find(null, true) == null, "unknown names give null");
            pair.add("later.json", anims(clip("plain", "true", 1, 9)));
            check(near(pair.get("plain").length, 1F, 1e-6F) && pair.size() == 3, "a later file replaces a clip of the same name");
        }
    }

    // -------------------------------------------------------------------------------------------------- rules

    static final String COMPAT_A = """
        {"customGroups":[{"name":"THROW_GROUP","items":["minecraft:snowball","minecraft:egg"]}],
         "default_animations":[{"type":"hand_in","name":"hand_in_default"},{"type":"attack","name":"punch_default"},
                               {"type":"drinking","start":"d_start","looping":"d_loop","end":"d_end"}],
         "emptyHand":{"customAnimation":[{"type":"attack","var_1":"punch_1","var_2":"punch_2"}]},
         "item":{
           "SWORD":{"disable_mining_animation":true,"customAnimation":[{"type":"attack","var_1":"sw_1","var_2":"sw_2","var_3":"sw_3","critical_hit":"sw_crit"},{"type":"hand_in","name":"sword_in"}]},
           "PICKAXE":{"customAnimation":[{"type":"mining_v2","start":"m_start","looping":"m_loop","end":"m_end"}]},
           "THROW_GROUP":{"customAnimation":[{"type":"useItem","name":"throw"}]}},
         "itemSpecific":{
           "minecraft:diamond_sword":{"customAnimation":[{"type":"attack","var_1":"dia_1"}]},
           "minecraft:bow":{"customAnimation":[{"type":"use_bow","name":"bow_draw","variations":[{"state":"crouching","name":"bow_draw_down"}]},{"type":"use_bow_shot","name":"bow_shot"}]}}}
        """;

    static final class RulesTests {
        static void run() {
            PunchyRules rules = new PunchyRules();
            rules.add("a.json", COMPAT_A);
            PunchyRules.Resolved hand = rules.resolve(null, List.of("EMPTY_HAND"));
            check("punch_1".equals(hand.first("attack").str("var_1")), "empty hand uses the emptyHand entry over the defaults");
            check("hand_in_default".equals(hand.first("hand_in").str("name")), "defaults still supply rules the entry does not mention");
            PunchyRules.Resolved sword = rules.resolve("minecraft:iron_sword", List.of("SWORD"));
            check("sw_1".equals(sword.first("attack").str("var_1")) && "sword_in".equals(sword.first("hand_in").str("name")), "category overrides defaults by type");
            check(sword.disableMining, "disable_mining_animation is carried");
            check(sword.first("drinking") != null, "a default type nothing overrides survives");
            PunchyRules.Resolved diamond = rules.resolve("minecraft:diamond_sword", List.of("SWORD"));
            check("dia_1".equals(diamond.first("attack").str("var_1")) && diamond.first("attack").str("var_2") == null,
                "item-specific entry replaces the category's rule of that type wholesale");
            check("sword_in".equals(diamond.first("hand_in").str("name")), "…but only for the types it defines");
            PunchyRules.Resolved snow = rules.resolve("minecraft:snowball", List.of());
            check("throw".equals(snow.first("useItem").str("name")), "customGroups make the group name a category for member items");
            check(rules.resolve("minecraft:stone", List.of()).first("useItem") == null, "non-members get no group rules");
            PunchyRules.Resolved bow = rules.resolve("minecraft:bow", List.of("BOW"));
            check("bow_draw_down".equals(bow.first("use_bow").variation("crouching")) && bow.first("use_bow").variation("flying") == null, "variations by state");
            check(rules.resolve("minecraft:unknown", List.of("NOPE")).first("attack") != null, "unknown categories fall back to defaults");
            check(sword.all("nothing").isEmpty() && sword.first("nothing") == null, "missing types are empty");

            rules.add("b.json", "{\"item\":{\"SWORD\":{\"customAnimation\":[{\"type\":\"attack\",\"var_1\":\"override\"}]}}}");
            check("override".equals(rules.resolve("minecraft:iron_sword", List.of("SWORD")).first("attack").str("var_1")), "a later file overrides an earlier entry");
            rules.add("c.json", "oops");
            check(rules.problems().containsKey("c.json"), "bad compat JSON is reported");
            check(new PunchyRules().isEmpty() && !rules.isEmpty() && rules.entryCount() >= 4, "isEmpty / entryCount");
        }
    }

    // ----------------------------------------------------------------------------------------------------- rig

    static final class RigTests {
        static void run() {
            PunchyRig rig = PunchyRig.defaultRig();
            check(rig.usable() && rig.has("camera") && rig.boneCount() == 9, "default rig has the bones first-person needs");
            float[][] rest = rig.solve(Map.of());
            float[] pr = rig.pivotJava(PunchyRig.RIGHT_ARM);
            float[] shoulder = rig.toCamera(rest, PunchyRig.RIGHT_ARM, pr[0], pr[1], pr[2]);
            check(near(shoulder[0], 0.6875F, 0.01F) && near(shoulder[1], -0.7125F, 0.01F) && near(shoulder[2], -0.4375F, 0.01F),
                "right shoulder sits right, below and ahead of the eye");
            float[] hand = rig.toCamera(rest, PunchyRig.RIGHT_ARM, pr[0] - 1, pr[1] + 10, pr[2]);
            check(hand[0] > 0.5F && hand[1] < -0.4F && hand[2] < -0.9F, "at rest the right hand is forward, right and low: " + java.util.Arrays.toString(hand));
            check(hand[2] < shoulder[2] - 0.5F, "the rest rotation points the arm forward");

            float[] pl = rig.pivotJava(PunchyRig.LEFT_ARM);
            float[] leftHand = rig.toCamera(rest, PunchyRig.LEFT_ARM, pl[0] + 1, pl[1] + 10, pl[2]);
            check(near(leftHand[0], -hand[0], 0.02F) && near(leftHand[1], hand[1], 0.02F) && near(leftHand[2], hand[2], 0.02F),
                "the left arm is the mirror image of the right at rest");

            // Item axis: the pack's base/top markers lie along the grip's local -Z; at rest that must point up.
            float[] g = rig.pivotJava(PunchyRig.GRIP_RIGHT);
            float[] a = rig.toCamera(rest, PunchyRig.GRIP_RIGHT, g[0], g[1], g[2]);
            float[] b = rig.toCamera(rest, PunchyRig.GRIP_RIGHT, g[0], g[1], g[2] - 10);
            check(b[1] - a[1] > 0.5F, "the held item points up at rest");

            // Animation conventions (Blockbench numbers: position Y up, X toward the player's left, Z back).
            BonePose down = new BonePose();
            down.pos[1] = -10.0F;
            float[] h1 = rig.toCamera(rig.solve(Map.of(PunchyRig.RIGHT_ARM, down)), PunchyRig.RIGHT_ARM, pr[0] - 1, pr[1] + 10, pr[2]);
            check(near(h1[1] - hand[1], -10F / 16F, 1e-3F) && near(h1[0], hand[0], 1e-3F), "position y -10 moves the arm 10 px down");
            BonePose fwd = new BonePose();
            fwd.pos[2] = -8.0F;
            float[] h2 = rig.toCamera(rig.solve(Map.of(PunchyRig.RIGHT_ARM, fwd)), PunchyRig.RIGHT_ARM, pr[0] - 1, pr[1] + 10, pr[2]);
            check(near(h2[2] - hand[2], -0.5F, 1e-3F), "position z -8 moves the arm 8 px forward");
            BonePose inward = new BonePose();
            inward.pos[0] = 8.0F;
            float[] h3 = rig.toCamera(rig.solve(Map.of(PunchyRig.RIGHT_ARM, inward)), PunchyRig.RIGHT_ARM, pr[0] - 1, pr[1] + 10, pr[2]);
            check(near(h3[0] - hand[0], -0.5F, 1e-3F), "position x +8 moves the right arm toward the centre");
            BonePose roll = new BonePose();
            roll.rot[2] = -30.0F;
            float[][] rolled = rig.solve(Map.of(PunchyRig.RIGHT_ARM, roll));
            float[] tipRolled = rig.toCamera(rolled, PunchyRig.GRIP_RIGHT, g[0], g[1], g[2] - 10);
            float[] gripRolled = rig.toCamera(rolled, PunchyRig.GRIP_RIGHT, g[0], g[1], g[2]);
            check(tipRolled[0] - gripRolled[0] > b[0] - a[0] + 0.1F, "a negative Z rotation leans the item outward (right)");
            BonePose swing = new BonePose();
            swing.rot[1] = 45.0F;
            float[][] swung = rig.solve(Map.of(PunchyRig.RIGHT_ARM, swing));
            float[] handSwung = rig.toCamera(swung, PunchyRig.RIGHT_ARM, pr[0] - 1, pr[1] + 10, pr[2]);
            check(handSwung[0] > hand[0] + 0.1F, "a positive Y rotation swings the right arm outward (wind-up side)");

            // The attachment matrix maps arm-local blocks to the same place toCamera does.
            float[] m = rig.attachment(rolled, PunchyRig.RIGHT_ARM, true);
            float[] viaMatrix = Mat4.apply(m, -1F / 16F, 10F / 16F, 0F);
            float[] viaPoint = rig.toCamera(rolled, PunchyRig.RIGHT_ARM, pr[0] - 1, pr[1] + 10, pr[2]);
            check(near(viaMatrix[0], viaPoint[0], 1e-4F) && near(viaMatrix[1], viaPoint[1], 1e-4F) && near(viaMatrix[2], viaPoint[2], 1e-4F),
                "attachment() agrees with toCamera()");
            check(Mat4.determinant3(m) > 0.0F, "the attachment is a proper rotation (no mirroring, so faces are not culled)");
            check(Mat4.determinant3(rig.attachment(rolled, PunchyRig.GRIP_LEFT, false)) > 0.0F, "same for the left grip");
            check(near(Mat4.determinant3(Mat4.rotationZYX(10, 20, 30)), 1.0F, 1e-5F), "Mat4 rotations are proper");

            // File order must not matter, and unusable files are refused.
            String geo = """
                {"format_version":"1.12.0","minecraft:geometry":[{"description":{"identifier":"geometry.player"},"bones":[
                 {"name":"itemgrip_right","parent":"item_right","pivot":[-12,8.5,-6]},
                 {"name":"item_right","parent":"right_arm","pivot":[-12,8.5,-6]},
                 {"name":"right_arm","parent":"body","pivot":[-11,16.6,-7],"rotation":[-100.03983,-6.46188,-8.56324],"cubes":[{"origin":[-14,6.6,-9],"size":[4,12,4]}]},
                 {"name":"left_arm","parent":"body","pivot":[11,16.6,-7],"rotation":[-100.0398,6.4619,8.5632]},
                 {"name":"item_left","parent":"left_arm","pivot":[12,8.5,-6]},
                 {"name":"itemgrip_left","parent":"item_left","pivot":[12,8.5,-6]},
                 {"name":"body","parent":"camera","pivot":[0,12,0]},
                 {"name":"camera","parent":"camera_animation","pivot":[0,28,0]},
                 {"name":"camera_animation","pivot":[0,28,0]}]}]}
                """;
            PunchyRig parsed = PunchyRig.parse(geo);
            check(parsed != null && parsed.usable(), "a geometry file lists bones in any order");
            float[] ph = parsed.toCamera(parsed.solve(Map.of()), PunchyRig.RIGHT_ARM, pr[0] - 1, pr[1] + 10, pr[2]);
            check(near(ph[0], hand[0], 1e-3F) && near(ph[1], hand[1], 1e-3F) && near(ph[2], hand[2], 1e-3F), "parsed geometry matches the built-in rig");
            check(PunchyRig.parse("{}") == null && PunchyRig.parse("garbage") == null, "unreadable geometry gives null");
            check(PunchyRig.parse("{\"minecraft:geometry\":[{\"bones\":[{\"name\":\"body\",\"pivot\":[0,0,0]}]}]}") == null, "a rig without arms is refused");
        }
    }

    // ------------------------------------------------------------------------------------------- controller

    static final class ControllerTests {
        static final float DT = 0.05F;

        static PunchyLibrary lib() {
            return library(anims(String.join(",",
                clip("hand_in_default", "false", 0.4F, 1F), clip("sword_in", "false", 0.4F, 2F),
                clip("punch_1", "false", 0.5F, 11F), clip("punch_2", "false", 0.5F, 12F), clip("punch_default", "false", 0.5F, 10F),
                clip("sw_1", "false", 0.5F, 21F), clip("sw_2", "false", 0.5F, 22F), clip("sw_3", "false", 0.5F, 23F), clip("sw_crit", "false", 0.5F, 29F),
                clip("m_start", "false", 0.3F, 31F), clip("m_loop", "true", 0.4F, 32F), clip("m_end", "false", 0.3F, 33F),
                clip("bow_draw", "hold_on_last_frame", 0.5F, 41F), clip("bow_draw_down", "hold_on_last_frame", 0.5F, 42F), clip("bow_shot", "false", 0.3F, 43F),
                clip("d_start", "false", 0.2F, 51F), clip("d_loop", "true", 0.3F, 52F), clip("d_end", "false", 0.2F, 53F),
                "\"fast\":{\"loop\":false,\"animation_length\":0.5,\"timeline\":{\"0\":\"anim_speed_2\"},\"bones\":{\"right_arm\":{\"position\":{\"0\":[0,0,0],\"0.5\":[5,0,0]}}}}",
                "\"snd\":{\"loop\":false,\"animation_length\":1.0,\"sound_effects\":{\"0.0\":{\"effect\":\"start\"},\"0.5\":{\"effect\":\"mid\"}},\"timeline\":{\"0\":\"hide_item\"},\"bones\":{\"right_arm\":{\"position\":{\"0\":[0,0,0],\"1\":[1,0,0]}}}}")));
        }

        static PunchyController make(PunchyLibrary lib) {
            PunchyRules rules = new PunchyRules();
            rules.add("a.json", COMPAT_A);
            return new PunchyController(lib, rules, true);
        }

        static PunchyController.Input input(String item, String... cats) {
            PunchyController.Input in = new PunchyController.Input();
            in.itemId = item;
            in.categories = cats.length == 0 ? List.of("EMPTY_HAND") : List.of(cats);
            return in;
        }

        /** Runs {@code seconds} of frames with the given input (edges are cleared after the first frame). */
        static void run(PunchyController c, PunchyController.Input in, float seconds) {
            boolean attack = in.attackPressed;
            boolean use = in.usePressed;
            for (float t = 0; t < seconds - 1e-4F; t += DT) {
                c.update(in, DT);
                in.attackPressed = false;
                in.usePressed = false;
            }
            in.attackPressed = attack && false;
            in.usePressed = use && false;
        }

        static void run() {
            PunchyLibrary lib = lib();

            // Start-up: no hand_in on the very first frame, rest pose.
            PunchyController c = make(lib);
            PunchyController.Input hand = input(null);
            run(c, hand, 0.2F);
            check(c.activeClip() == null && c.poses().isEmpty(), "idle empty hand plays nothing");

            // Equipping a different item plays that item's hand_in.
            PunchyController.Input sword = input("minecraft:iron_sword", "SWORD");
            c.update(sword, DT);
            check("sword_in".equals(c.activeClip()), "changing item plays its hand_in");
            run(c, sword, 0.5F);
            check(c.activeClip() == null, "play_once hand_in ends and releases the pose");

            // Attack combo cycles var_1..var_3 and wraps.
            List<String> seen = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                sword.attackPressed = true;
                c.update(sword, DT);
                sword.attackPressed = false;
                seen.add(c.activeClip());
                run(c, sword, 0.6F);
            }
            check(seen.equals(List.of("sw_1", "sw_2", "sw_3", "sw_1")) || seen.equals(List.of("sw_1", "sw_2", "sw_3", "sw_1")),
                "attack variants cycle per swing: " + seen);
            run(c, sword, 2.0F);
            sword.attackPressed = true;
            c.update(sword, DT);
            sword.attackPressed = false;
            check("sw_1".equals(c.activeClip()), "the combo restarts after a pause");
            run(c, sword, 0.6F);
            sword.attackPressed = true;
            sword.criticalHit = true;
            c.update(sword, DT);
            sword.attackPressed = false;
            sword.criticalHit = false;
            check("sw_crit".equals(c.activeClip()), "a critical hit plays critical_hit");
            run(c, sword, 0.6F);

            // A sword never plays the mining loop even while breaking blocks (disable_mining_animation).
            sword.mining = true;
            sword.attackPressed = true;
            c.update(sword, DT);
            sword.attackPressed = false;
            check(c.activeClip() != null && c.activeClip().startsWith("sw_"), "swords attack instead of mining");
            sword.mining = false;
            run(c, sword, 0.7F);

            // Mining: start -> loop while held -> end on release; swings while mining do not attack.
            PunchyController m = make(lib);
            PunchyController.Input pick = input("minecraft:iron_pickaxe", "PICKAXE");
            run(m, pick, 0.1F);
            pick.mining = true;
            pick.attackPressed = true;
            m.update(pick, DT);
            pick.attackPressed = false;
            check("m_start".equals(m.activeClip()), "mining begins with the start clip (and the click is not an attack)");
            run(m, pick, 0.4F);
            check("m_loop".equals(m.activeClip()), "then loops");
            run(m, pick, 3.0F);
            check("m_loop".equals(m.activeClip()), "keeps looping while the block is being broken");
            pick.mining = false;
            m.update(pick, DT);
            check("m_end".equals(m.activeClip()), "releasing plays the end clip");
            run(m, pick, 0.5F);
            check(m.activeClip() == null, "and then goes back to rest");

            // Bow: hold on draw, variation when crouching, shot on release.
            PunchyController b = make(lib);
            PunchyController.Input bow = input("minecraft:bow", "BOW");
            run(b, bow, 0.1F);
            bow.using = true;
            bow.useKind = "BOW";
            b.update(bow, DT);
            check("bow_draw".equals(b.activeClip()), "drawing a bow plays use_bow");
            run(b, bow, 2.0F);
            check("bow_draw".equals(b.activeClip()) && near(posX(b), 41F, 0.01F), "the draw holds on its last frame");
            bow.using = false;
            b.update(bow, DT);
            check("bow_shot".equals(b.activeClip()), "releasing after a draw plays use_bow_shot");
            run(b, bow, 0.5F);
            check(b.activeClip() == null, "the shot ends");
            PunchyController b2 = make(lib);
            run(b2, bow, 0.1F);
            bow.using = true;
            bow.crouching = true;
            b2.update(bow, DT);
            check("bow_draw_down".equals(b2.activeClip()), "crouching picks the crouching variation");
            bow.using = false;
            bow.crouching = false;
            b2.update(bow, DT); // released within 0.15 s: no shot
            check(!"bow_shot".equals(b2.activeClip()), "a tap too short to draw plays no shot");

            // Pressing attack while the bow is drawn does not swing.
            PunchyController b3 = make(lib);
            run(b3, bow, 0.1F);
            bow.using = true;
            b3.update(bow, DT);
            bow.attackPressed = true;
            b3.update(bow, DT);
            bow.attackPressed = false;
            check("bow_draw".equals(b3.activeClip()), "attack presses are ignored while an item is in use");
            bow.using = false;

            // Eating/drinking: start -> loop -> end (from default_animations).
            PunchyController d = make(lib);
            PunchyController.Input food = input("minecraft:bread", "FOOD");
            run(d, food, 0.1F);
            food.using = true;
            food.useKind = "EAT";
            d.update(food, DT);
            check("d_start".equals(d.activeClip()), "eating starts with the drinking start clip");
            run(d, food, 0.3F);
            check("d_loop".equals(d.activeClip()), "then loops");
            food.using = false;
            d.update(food, DT);
            check("d_end".equals(d.activeClip()), "ends with the end clip");

            // Blending: a new clip starts from what was on screen, not from its first frame.
            PunchyController bl = make(lib);
            PunchyController.Input e = input(null);
            run(bl, e, 0.1F);
            e.attackPressed = true;
            bl.update(e, DT); // punch_1 begins; its pose is already ~1/10 of the way in
            float first = posX(bl);
            run(bl, e, 0.45F);
            check(first < 11F * 0.35F, "the first frames of a clip are blended in, not snapped (" + first + ")");

            // Blending: when the mining loop is released, the end clip starts from the pose that was on screen.
            PunchyController bm = make(lib);
            PunchyController.Input pk = input("minecraft:iron_pickaxe", "PICKAXE");
            run(bm, pk, 0.1F);
            pk.mining = true;
            run(bm, pk, 0.6F); // start (0.3 s) + 0.3 s into the loop (24 px)
            float before = posX(bm);
            pk.mining = false;
            bm.update(pk, DT);
            float after = posX(bm);
            float target = 33F * (DT / 0.3F);
            check("m_end".equals(bm.activeClip()) && before > 20F && after > target + 0.3F * (before - target),
                "a clip started mid-motion blends from the old pose (before=" + before + " after=" + after + " raw=" + target + ")");

            // Frame gaps are clamped: a 100 s hitch must not skip a 0.5 s clip.
            PunchyController hitch = make(lib);
            PunchyController.Input hp = input(null);
            run(hitch, hp, 0.1F);
            hp.attackPressed = true;
            hitch.update(hp, DT);
            hp.attackPressed = false;
            hitch.update(hp, 100F);
            check(hitch.activeClip() != null, "a long frame gap advances at most a quarter second");

            // anim_speed_X speeds a clip up.
            PunchyRules fastRules = new PunchyRules();
            fastRules.add("f.json", "{\"emptyHand\":{\"customAnimation\":[{\"type\":\"attack\",\"name\":\"fast\"}]}}");
            PunchyController fc = new PunchyController(lib, fastRules, true);
            PunchyController.Input fi = input(null);
            run(fc, fi, 0.1F);
            fi.attackPressed = true;
            fc.update(fi, DT);
            fi.attackPressed = false;
            run(fc, fi, 0.3F);
            check(fc.activeClip() == null, "a clip with anim_speed_2 finishes in half the time");

            // Sounds fire once each; hide_item shows while the clip plays.
            PunchyLibrary sl = lib;
            PunchyRules rules = new PunchyRules();
            rules.add("s.json", "{\"emptyHand\":{\"customAnimation\":[{\"type\":\"attack\",\"name\":\"snd\"}]}}");
            PunchyController sc = new PunchyController(sl, rules, true);
            PunchyController.Input si = input(null);
            run(sc, si, 0.1F);
            si.attackPressed = true;
            sc.update(si, DT);
            List<String> got = new ArrayList<>(sc.drainSounds());
            check(got.equals(List.of("start")), "a sound at t=0 fires when the clip starts: " + got);
            check(sc.itemHidden(), "hide_item hides the held item");
            si.attackPressed = false;
            run(sc, si, 0.6F);
            got.addAll(sc.drainSounds());
            check(got.equals(List.of("start", "mid")), "a sound at t=0.5 fires once as playback passes it: " + got);
            run(sc, si, 1.0F);
            check(sc.drainSounds().isEmpty() && !sc.itemHidden(), "nothing fires after the clip ends");

            // reset() clears everything.
            sc.reset();
            check(sc.poses().isEmpty() && sc.activeClip() == null, "reset() clears the controller");
            // huge frame gaps are clamped
            c.update(sword, 100F);
            check(true, "a huge dt does not blow up");
        }
    }

    // --------------------------------------------------------------------------------------------- real pack

    static final class RealPackTests {
        static void run(Path pack) throws IOException {
            Path animations = pack.resolve("assets/minecraft/punchy/animations");
            Path compat = pack.resolve("assets/minecraft/punchy/compat");
            PunchyLibrary lib = new PunchyLibrary();
            int files = 0;
            try (Stream<Path> s = Files.walk(animations)) {
                for (Path p : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".animation.json")).sorted()::iterator) {
                    lib.add(p.getFileName().toString(), Files.readString(p));
                    files++;
                }
            }
            check(files > 5 && lib.size() > 100, "real pack: loaded " + lib.size() + " clips from " + files + " files");
            check(lib.problems().isEmpty(), "real pack: every animation file parsed: " + lib.problems());
            int bad = 0;
            for (PunchyClip c : lib.all().values()) {
                if (c.length <= 0.0F || c.bones().isEmpty()) bad++;
            }
            check(bad == 0, "real pack: every clip has a length and at least one bone (" + bad + " without)");

            PunchyRules rules = new PunchyRules();
            Path tuning = compat.resolve("default_tuning.json");
            if (Files.exists(tuning)) rules.add("default_tuning.json", Files.readString(tuning));
            try (Stream<Path> s = Files.walk(compat)) {
                for (Path p : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".json") && !x.equals(tuning)).sorted()::iterator) {
                    rules.add(p.getFileName().toString(), Files.readString(p));
                }
            }
            check(rules.problems().isEmpty() && !rules.isEmpty(), "real pack: compat files parsed: " + rules.problems());

            // Every animation name a rule asks for for the main actions must resolve to a clip for at least one hand.
            Map<String, List<String>> checks = new HashMap<>();
            checks.put("SWORD", List.of("attack"));
            checks.put("PICKAXE", List.of("attack", "mining_v2"));
            checks.put("EMPTY_HAND", List.of("attack", "hand_in"));
            checks.put("BOW", List.of("use_bow"));
            int missing = 0;
            int looked = 0;
            for (Map.Entry<String, List<String>> e : checks.entrySet()) {
                PunchyRules.Resolved r = rules.resolve(null, List.of(e.getKey()));
                for (String type : e.getValue()) {
                    PunchyRules.Rule rule = r.first(type);
                    if (rule == null) continue;
                    for (String key : new String[]{"name", "var_1", "var_2", "var_3", "start", "looping", "end"}) {
                        String name = rule.str(key);
                        if (name == null) continue;
                        looked++;
                        if (lib.find(name, true) == null) {
                            missing++;
                            System.out.println("  note: " + e.getKey() + "/" + type + "/" + key + " -> '" + name + "' has no clip");
                        }
                    }
                }
            }
            check(looked > 8, "real pack: looked up " + looked + " animation names for the main actions");
            check(missing == 0, "real pack: " + missing + " animation names the main rules ask for are missing");

            // Drive the controller with the real data: swing a sword, mine with a pickaxe, equip, bow.
            PunchyController c = new PunchyController(lib, rules, true);
            PunchyController.Input in = ControllerTests.input("minecraft:diamond_sword", "SWORD");
            ControllerTests.run(c, in, 0.2F);
            in.attackPressed = true;
            c.update(in, 0.016F);
            in.attackPressed = false;
            check(c.activeClip() != null && c.activeClip().contains("attack"), "real pack: a sword swing plays an attack clip (" + c.activeClip() + ")");
            for (int i = 0; i < 400; i++) c.update(in, 0.016F);
            check(c.activeClip() == null && c.poses().isEmpty(), "real pack: the swing ends and the pose clears");

            // Rest-pose sanity for the end of equipping/attacking: the hand ends up where it started.
            PunchyRig rig = PunchyRig.defaultRig();
            Path geo = pack.resolveSibling("jar").resolve("assets/punchy/geo/player.geo.json");
            float[][] rest = rig.solve(Map.of());
            float[] pr = rig.pivotJava(PunchyRig.RIGHT_ARM);
            float[] restHand = rig.toCamera(rest, PunchyRig.RIGHT_ARM, pr[0] - 1, pr[1] + 10, pr[2]);
            int offRest = 0;
            int total = 0;
            for (PunchyClip clip : lib.all().values()) {
                if (clip.loop != PunchyClip.Loop.ONCE || !clip.animates(PunchyRig.RIGHT_ARM) || clip.length < 0.2F) continue;
                if (!(clip.name.startsWith("attack") || clip.name.contains("hand_in"))) continue;
                total++;
                Map<String, BonePose> poses = new HashMap<>();
                for (String bone : clip.bones()) {
                    BonePose bp = new BonePose();
                    clip.sample(bone, clip.length, bp);
                    poses.put(bone, bp);
                }
                float[] h = rig.toCamera(rig.solve(poses), PunchyRig.RIGHT_ARM, pr[0] - 1, pr[1] + 10, pr[2]);
                float dx = h[0] - restHand[0];
                float dy = h[1] - restHand[1];
                float dz = h[2] - restHand[2];
                if (Math.sqrt(dx * dx + dy * dy + dz * dz) > 0.2) {
                    offRest++;
                    System.out.println("  note: " + clip.name + " ends " + String.format("%.2f", Math.sqrt(dx * dx + dy * dy + dz * dz)) + " blocks from rest");
                }
            }
            check(total > 10, "real pack: examined the last frame of " + total + " attack/hand_in clips");
            check(offRest * 5 <= total, "real pack: attack/hand_in clips end at the rest pose (" + offRest + " of " + total + " do not)");
            if (Files.exists(geo)) {
                PunchyRig real = PunchyRig.parse(Files.readString(geo));
                check(real != null && real.usable(), "real pack: the mod's player.geo.json parses");
                float[] rh = real.toCamera(real.solve(Map.of()), PunchyRig.RIGHT_ARM, pr[0] - 1, pr[1] + 10, pr[2]);
                check(near(rh[0], restHand[0], 0.01F) && near(rh[1], restHand[1], 0.01F) && near(rh[2], restHand[2], 0.01F),
                    "real pack: the built-in rig puts the hand where the real geometry does");
            }
        }
    }
}
