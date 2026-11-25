package com.melonman106.vbvclient.punchy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Developer tool (not part of the checks): draws first-person frames of real animations as an SVG so the rig
 * conventions can be eyeballed without the game.
 *
 * <pre>java PunchyPreview &lt;packDir&gt; &lt;out.svg&gt; [geo=player.geo.json] clip[:t1,t2,...] ...   ("rest" = no clip)</pre>
 *
 * Each cell is a 16:9 view with a 70 degree vertical FOV. The arm is the vanilla arm box (4x12x4 px around its
 * shoulder pivot); the green line is the item axis (the grip's local -Z, where the pack's base/top markers lie).
 */
public final class PunchyPreview {
    private static final int CW = 480;
    private static final int CH = 270;
    private static final float FOCAL = (CH / 2.0F) / (float) Math.tan(Math.toRadians(35.0));

    public static void main(String[] args) throws Exception {
        Path pack = Path.of(args[0]);
        PunchyLibrary lib = new PunchyLibrary();
        try (var s = Files.walk(pack.resolve("assets/minecraft/punchy/animations"))) {
            for (Path p : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".animation.json"))::iterator) {
                lib.add(p.getFileName().toString(), Files.readString(p));
            }
        }
        PunchyRig rig = PunchyRig.defaultRig();
        List<String[]> frames = new ArrayList<>(); // {label, clip, time}
        for (int i = 2; i < args.length; i++) {
            if (args[i].startsWith("geo=")) {
                rig = PunchyRig.parse(Files.readString(Path.of(args[i].substring(4))));
                continue;
            }
            String clip = args[i];
            String[] times = {"0"};
            int c = args[i].indexOf(':');
            if (c >= 0) {
                clip = args[i].substring(0, c);
                times = args[i].substring(c + 1).split(",");
            }
            for (String t : times) frames.add(new String[]{clip + " @" + t, clip, t});
        }
        int cols = Math.min(3, frames.size());
        int rows = (frames.size() + cols - 1) / cols;
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns='http://www.w3.org/2000/svg' width='").append(cols * CW).append("' height='")
            .append(rows * CH).append("' font-family='monospace' font-size='12'>\n");
        svg.append("<rect width='100%' height='100%' fill='#1b1f2a'/>\n");
        for (int i = 0; i < frames.size(); i++) {
            String[] f = frames.get(i);
            svg.append("<g transform='translate(").append((i % cols) * CW).append(',').append((i / cols) * CH).append(")'>\n");
            svg.append("<clipPath id='c").append(i).append("'><rect x='2' y='2' width='").append(CW - 4)
                .append("' height='").append(CH - 4).append("'/></clipPath>\n");
            svg.append("<rect x='2' y='2' width='").append(CW - 4).append("' height='").append(CH - 4)
                .append("' fill='#232a3a' stroke='#3a4560'/>\n<g clip-path='url(#c").append(i).append(")'>\n");
            svg.append("<path d='M").append(CW / 2 - 8).append(' ').append(CH / 2).append("h16M").append(CW / 2).append(' ')
                .append(CH / 2 - 8).append("v16' stroke='#667'/>\n");
            PunchyClip clip = f[1].equals("rest") ? null : lib.get(f[1]);
            float t = Float.parseFloat(f[2]);
            Map<String, BonePose> poses = new HashMap<>();
            if (clip != null) {
                for (String bone : clip.bones()) {
                    BonePose bp = new BonePose();
                    clip.sample(bone, t, bp);
                    poses.put(bone, bp);
                }
            } else if (!f[1].equals("rest")) {
                svg.append("<text x='10' y='40' fill='#f66'>missing clip</text>\n");
            }
            float[][] world = rig.solve(poses);
            draw(svg, rig, world, PunchyRig.RIGHT_ARM, PunchyRig.GRIP_RIGHT, "#6aa0ff");
            draw(svg, rig, world, PunchyRig.LEFT_ARM, PunchyRig.GRIP_LEFT, "#ffb04a");
            svg.append("</g>\n<text x='10' y='20' fill='#cfd6ee'>").append(f[0]).append("</text>\n</g>\n");
            if (f[1].equals("rest") || i == 0) {
                float[] shoulder = rig.toCamera(world, PunchyRig.RIGHT_ARM, rig.pivotJava(PunchyRig.RIGHT_ARM)[0],
                    rig.pivotJava(PunchyRig.RIGHT_ARM)[1], rig.pivotJava(PunchyRig.RIGHT_ARM)[2]);
                float[] p = rig.pivotJava(PunchyRig.RIGHT_ARM);
                float[] hand = rig.toCamera(world, PunchyRig.RIGHT_ARM, p[0] - 1, p[1] + 10, p[2]);
                System.out.printf("%s: right shoulder (x=%.2f y=%.2f z=%.2f)  hand centre (x=%.2f y=%.2f z=%.2f)%n", f[0],
                    shoulder[0], shoulder[1], shoulder[2], hand[0], hand[1], hand[2]);
            }
        }
        svg.append("</svg>\n");
        Files.writeString(Path.of(args[1]), svg.toString());
    }

    private static void draw(StringBuilder svg, PunchyRig rig, float[][] world, String arm, String grip, String color) {
        float[] pivot = rig.pivotJava(arm);
        float[][] corners = new float[8][];
        int n = 0;
        for (int xi = 0; xi < 2; xi++) for (int yi = 0; yi < 2; yi++) for (int zi = 0; zi < 2; zi++) {
            corners[n++] = rig.toCamera(world, arm, pivot[0] + (xi == 0 ? -3 : 1), pivot[1] + (yi == 0 ? -2 : 10),
                pivot[2] + (zi == 0 ? -2 : 2));
        }
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) line(svg, corners[e[0]], corners[e[1]], color, 2.0F);
        float[] gp = rig.pivotJava(grip);
        float[] a = rig.toCamera(world, grip, gp[0], gp[1], gp[2]);
        float[] b = rig.toCamera(world, grip, gp[0], gp[1], gp[2] - 14.0F);
        line(svg, a, b, "#7be07b", 3.0F);
        float[] s = project(a);
        if (s != null) svg.append("<circle cx='").append(s[0]).append("' cy='").append(s[1]).append("' r='3' fill='#fff'/>\n");
    }

    private static float[] project(float[] p) {
        float depth = -p[2];
        if (depth < 0.05F) return null; // behind / too close to the eye
        return new float[]{CW / 2.0F + p[0] / depth * FOCAL, CH / 2.0F - p[1] / depth * FOCAL};
    }

    private static void line(StringBuilder svg, float[] a, float[] b, String color, float w) {
        float[] pa = project(a);
        float[] pb = project(b);
        if (pa == null || pb == null) return;
        svg.append("<line x1='").append(pa[0]).append("' y1='").append(pa[1]).append("' x2='").append(pb[0])
            .append("' y2='").append(pb[1]).append("' stroke='").append(color).append("' stroke-width='").append(w)
            .append("' stroke-linecap='round'/>\n");
    }
}
