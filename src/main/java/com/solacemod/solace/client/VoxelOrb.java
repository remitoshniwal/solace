package com.solacemod.solace.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The Lantern's body as chunky 3D voxels (little cubes, 1/8 of a block each): a ragged ball of plasma with a solid
 * white-hot middle inside glowing layers of blue, deeper towards the skin, and arcs of lightning poking out. Built once as a
 * loop of frames (each a slightly different shape) so it churns like a flipbook, but in 3D.
 * Pure Java: lists of faces the renderer sends to Minecraft.
 */
final class VoxelOrb {
    /** Grid size in voxels, and voxel size in blocks. */
    static final int N = 13;
    static final float VOX = 1 / 8F;
    static final int FRAMES = 10;

    /** One square face: 4 corners (x, y, z in blocks, centred on the ball), its colour, and which way it faces. */
    record Face(float[] pos, int argb, float nx, float ny, float nz) {
    }

    /** The parts of one frame: the solid white-hot inside, the glowing blue layers round it, and the inside-out outline. */
    record Frame(List<Face> core, List<Face> shell, List<Face> outline) {
    }

    final Frame[] frames = new Frame[FRAMES];

    /**
     * @param palette colours from the hottest middle to the outer skin (5 of them)
     * @param outline colour of the outline
     */
    VoxelOrb(int[] palette, int outline, long seed) {
        for (int f = 0; f < FRAMES; f++) frames[f] = build(f, palette, outline, seed);
    }

    private static Frame build(int frame, int[] palette, int outlineColor, long seed) {
        double ang = 2 * Math.PI * frame / FRAMES;
        double ox = Math.cos(ang) * 1.6, oz = Math.sin(ang) * 1.6;
        int[][][] level = new int[N][N][N]; // -1 empty, 0 hottest .. 4 skin
        boolean[][][] arc = new boolean[N][N][N];
        float c = (N - 1) / 2F, radius = N / 2F - 0.6F;
        for (int x = 0; x < N; x++) for (int y = 0; y < N; y++) for (int z = 0; z < N; z++) {
            float dx = x - c, dy = y - c, dz = z - c;
            double r = Math.sqrt(dx * dx + dy * dy + dz * dz) / radius;
            double n = fbm(dx * 0.32 + ox, dy * 0.32, dz * 0.32 + oz, seed);
            double edge = r + 0.55 * (n - 0.5);
            if (edge > 0.98) {
                level[x][y][z] = -1;
                continue;
            }
            double heat = r + 0.35 * (fbm(dx * 0.5 - oz, dy * 0.5 + 3, dz * 0.5 + ox, seed + 7) - 0.5);
            level[x][y][z] = heat < 0.3 ? 0 : heat < 0.45 ? 1 : heat < 0.6 ? 2 : edge > 0.84 ? 4 : 3;
        }
        // Two arcs of lightning crackling out through the skin.
        Random rnd = new Random(seed * 31 + frame);
        for (int a = 0; a < 2; a++) {
            double px = c, py = c, pz = c;
            double vx = rnd.nextGaussian(), vy = rnd.nextGaussian(), vz = rnd.nextGaussian();
            for (int step = 0; step < N; step++) {
                double l = Math.sqrt(vx * vx + vy * vy + vz * vz) + 1e-6;
                px += vx / l;
                py += vy / l;
                pz += vz / l;
                vx += rnd.nextGaussian() * 0.6;
                vy += rnd.nextGaussian() * 0.6;
                vz += rnd.nextGaussian() * 0.6;
                int ix = (int) Math.round(px), iy = (int) Math.round(py), iz = (int) Math.round(pz);
                if (ix < 0 || iy < 0 || iz < 0 || ix >= N || iy >= N || iz >= N) break;
                if (level[ix][iy][iz] < 0) arc[ix][iy][iz] = true;
                level[ix][iy][iz] = Math.min(level[ix][iy][iz] < 0 ? 9 : level[ix][iy][iz], 1);
            }
        }
        List<Face> core = new ArrayList<>(), shell = new ArrayList<>(), outer = new ArrayList<>();
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int x = 0; x < N; x++) for (int y = 0; y < N; y++) for (int z = 0; z < N; z++) {
            int lv = level[x][y][z];
            if (lv < 0) continue;
            boolean solid = lv <= 1;
            for (int[] d : dirs) {
                int nl = get(level, x + d[0], y + d[1], z + d[2]);
                // The hot middle is solid; the blue layers round it are see-through, each showing its outer side.
                boolean show = solid ? nl < 0 || nl > 1 : nl < 0 || nl > lv;
                if (!show) continue;
                // For the glowing layers the alpha says how strongly they glow.
                int alpha = solid ? 0xFF000000 : lv == 2 ? 0x70000000 : lv == 3 ? 0x80000000 : 0xB0000000;
                Face face = face(x - c, y - c, z - c, d, palette[Math.min(lv, 4)] & 0xFFFFFF | alpha);
                if (solid) core.add(face);
                else shell.add(face);
                if (nl < 0 && !arc[x][y][z]) outer.add(face); // (the thin lightning arcs get no outline)
            }
        }
        return new Frame(core, shell, outline(outer, outlineColor));
    }

    private static int get(int[][][] level, int x, int y, int z) {
        return x < 0 || y < 0 || z < 0 || x >= N || y >= N || z >= N ? -1 : level[x][y][z];
    }

    /** One face of the voxel at (x, y, z) (in voxels from the centre), facing d. */
    private static Face face(float x, float y, float z, int[] d, int argb) {
        float h = 0.5F;
        float[][] corners = new float[4][];
        int axis = d[0] != 0 ? 0 : d[1] != 0 ? 1 : 2, s = d[axis];
        int u = (axis + 1) % 3, v = (axis + 2) % 3;
        float[][] uv = {{-h, -h}, {h, -h}, {h, h}, {-h, h}};
        float[] centre = {x, y, z};
        for (int k = 0; k < 4; k++) {
            float[] p = centre.clone();
            p[axis] += s * h;
            p[u] += uv[k][0];
            p[v] += uv[k][1] * s; // keeps the corners going round the outside the right way
            corners[k] = p;
        }
        float[] pos = new float[12];
        for (int k = 0; k < 4; k++) for (int a = 0; a < 3; a++) pos[k * 3 + a] = corners[k][a] * VOX;
        return new Face(pos, argb, d[0], d[1], d[2]);
    }

    /**
     * The outline: the outer skin puffed out a little and turned inside out, so with back faces hidden only a dark
     * rim shows round the edges (the same trick as Solace's body).
     */
    private static List<Face> outline(List<Face> outer, int argb) {
        Map<Long, float[]> normals = new HashMap<>();
        for (Face f : outer) for (int k = 0; k < 4; k++) {
            float[] n = normals.computeIfAbsent(key(f.pos, k), x -> new float[3]);
            n[0] += f.nx;
            n[1] += f.ny;
            n[2] += f.nz;
        }
        float width = 0.045F;
        List<Face> out = new ArrayList<>();
        for (Face f : outer) {
            float[] pos = new float[12];
            for (int k = 0; k < 4; k++) {
                int src = 3 - k; // reversed order: the face now points inwards
                float[] sn = normals.get(key(f.pos, src));
                float sl = (float) Math.sqrt(sn[0] * sn[0] + sn[1] * sn[1] + sn[2] * sn[2]) + 1e-6F;
                for (int a = 0; a < 3; a++) pos[k * 3 + a] = f.pos[src * 3 + a] + sn[a] / sl * width;
            }
            out.add(new Face(pos, argb, -f.nx, -f.ny, -f.nz));
        }
        return out;
    }

    private static long key(float[] pos, int k) {
        long x = Math.round(pos[k * 3] * 64), y = Math.round(pos[k * 3 + 1] * 64), z = Math.round(pos[k * 3 + 2] * 64);
        return (x & 0xFFFFF) << 40 | (y & 0xFFFFF) << 20 | (z & 0xFFFFF);
    }

    // ---- smooth 3D noise ----

    private static double fbm(double x, double y, double z, long seed) {
        double sum = 0, amp = 1, total = 0;
        for (int o = 0; o < 3; o++) {
            sum += noise(x, y, z, seed + o * 101) * amp;
            total += amp;
            amp *= 0.5;
            x *= 2;
            y *= 2;
            z *= 2;
        }
        return sum / total;
    }

    private static double noise(double x, double y, double z, long seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        double fx = smooth(x - x0), fy = smooth(y - y0), fz = smooth(z - z0);
        double r = 0;
        for (int i = 0; i < 8; i++) {
            int dx = i & 1, dy = (i >> 1) & 1, dz = (i >> 2) & 1;
            double w = (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy) * (dz == 1 ? fz : 1 - fz);
            r += w * hash(x0 + dx, y0 + dy, z0 + dz, seed);
        }
        return r;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double hash(int x, int y, int z, long seed) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0x632BE59BD9B4E019L + y * 0x85157AF5L + z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }
}
