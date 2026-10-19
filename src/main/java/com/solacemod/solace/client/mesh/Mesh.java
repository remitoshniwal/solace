package com.solacemod.solace.client.mesh;

import java.util.Arrays;

/**
 * A rounded 3D shape made of small four-cornered faces (spheres, tubes, curved surfaces...), with a colour per corner.
 * Built once (or every frame for things that bend), then drawn through any {@link MeshSink}.
 */
public final class Mesh {
    // Per corner: x, y, z, nx, ny, nz, u, v. Colours separately.
    float[] data = new float[8 * 4 * 64];
    int[] colors = new int[4 * 64];
    int quads;

    public int size() {
        return quads;
    }

    public void clear() {
        quads = 0;
    }

    /** Adds one face. Corners in order around the face. */
    public void quad(float[] p, float[] n, float[] uv, int[] c) {
        ensure(quads + 1);
        int base = quads * 32;
        for (int i = 0; i < 4; i++) {
            int o = base + i * 8;
            data[o] = p[i * 3];
            data[o + 1] = p[i * 3 + 1];
            data[o + 2] = p[i * 3 + 2];
            data[o + 3] = n[i * 3];
            data[o + 4] = n[i * 3 + 1];
            data[o + 5] = n[i * 3 + 2];
            data[o + 6] = uv[i * 2];
            data[o + 7] = uv[i * 2 + 1];
            colors[quads * 4 + i] = c[i];
        }
        quads++;
    }

    public void add(Mesh other) {
        ensure(quads + other.quads);
        System.arraycopy(other.data, 0, data, quads * 32, other.quads * 32);
        System.arraycopy(other.colors, 0, colors, quads * 4, other.quads * 4);
        quads += other.quads;
    }

    private void ensure(int n) {
        if (n * 32 > data.length) {
            data = Arrays.copyOf(data, Math.max(n * 32, data.length * 2));
            colors = Arrays.copyOf(colors, Math.max(n * 4, colors.length * 2));
        }
    }

    private final float[] tp = new float[12], tn = new float[12], tuv = new float[8];
    private final int[] tc = new int[4];

    /**
     * Draws every face, moved by {@code m}.
     *
     * @param tint multiplies every colour (0xFFFFFFFF = unchanged)
     * @param recolor if not null, replaces each colour (used for the negative form)
     */
    public void emit(Mat4 m, MeshSink sink, int tint, java.util.function.IntUnaryOperator recolor) {
        for (int q = 0; q < quads; q++) {
            int base = q * 32;
            for (int i = 0; i < 4; i++) {
                int o = base + i * 8;
                m.point(data[o], data[o + 1], data[o + 2], tp, i * 3);
                m.normal(data[o + 3], data[o + 4], data[o + 5], tn, i * 3);
                tuv[i * 2] = data[o + 6];
                tuv[i * 2 + 1] = data[o + 7];
                int c = colors[q * 4 + i];
                if (recolor != null) c = recolor.applyAsInt(c);
                tc[i] = tint == 0xFFFFFFFF ? c : multiply(c, tint);
            }
            sink.quad(tp, tn, tuv, tc);
        }
    }

    static int multiply(int a, int b) {
        int aa = ((a >>> 24) * (b >>> 24)) / 255;
        int r = (((a >> 16) & 255) * ((b >> 16) & 255)) / 255;
        int g = (((a >> 8) & 255) * ((b >> 8) & 255)) / 255;
        int bb = ((a & 255) * (b & 255)) / 255;
        return aa << 24 | r << 16 | g << 8 | bb;
    }
}
