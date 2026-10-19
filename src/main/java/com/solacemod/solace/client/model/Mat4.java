package com.solacemod.solace.client.model;

/** A small 4x4 matrix for moving and turning model parts. No Minecraft code, so the preview tool can use it too. */
public final class Mat4 {
    final float[] m = new float[16]; // row-major

    public Mat4() {
        identity();
    }

    public Mat4 identity() {
        java.util.Arrays.fill(m, 0);
        m[0] = m[5] = m[10] = m[15] = 1;
        return this;
    }

    public Mat4 copy() {
        Mat4 c = new Mat4();
        System.arraycopy(m, 0, c.m, 0, 16);
        return c;
    }

    /** this = this * other */
    public Mat4 mul(Mat4 o) {
        float[] r = new float[16];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                float s = 0;
                for (int k = 0; k < 4; k++) s += m[i * 4 + k] * o.m[k * 4 + j];
                r[i * 4 + j] = s;
            }
        }
        System.arraycopy(r, 0, m, 0, 16);
        return this;
    }

    public Mat4 translate(float x, float y, float z) {
        Mat4 t = new Mat4();
        t.m[3] = x;
        t.m[7] = y;
        t.m[11] = z;
        return mul(t);
    }

    public Mat4 scale(float x, float y, float z) {
        Mat4 t = new Mat4();
        t.m[0] = x;
        t.m[5] = y;
        t.m[10] = z;
        return mul(t);
    }

    public Mat4 rotateX(float a) {
        Mat4 t = new Mat4();
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        t.m[5] = c;
        t.m[6] = -s;
        t.m[9] = s;
        t.m[10] = c;
        return mul(t);
    }

    public Mat4 rotateY(float a) {
        Mat4 t = new Mat4();
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        t.m[0] = c;
        t.m[2] = s;
        t.m[8] = -s;
        t.m[10] = c;
        return mul(t);
    }

    public Mat4 rotateZ(float a) {
        Mat4 t = new Mat4();
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        t.m[0] = c;
        t.m[1] = -s;
        t.m[4] = s;
        t.m[5] = c;
        return mul(t);
    }

    public void point(float x, float y, float z, float[] out, int o) {
        out[o] = m[0] * x + m[1] * y + m[2] * z + m[3];
        out[o + 1] = m[4] * x + m[5] * y + m[6] * z + m[7];
        out[o + 2] = m[8] * x + m[9] * y + m[10] * z + m[11];
    }

    public void normal(float x, float y, float z, float[] out, int o) {
        float nx = m[0] * x + m[1] * y + m[2] * z;
        float ny = m[4] * x + m[5] * y + m[6] * z;
        float nz = m[8] * x + m[9] * y + m[10] * z;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-6f) len = 1;
        out[o] = nx / len;
        out[o + 1] = ny / len;
        out[o + 2] = nz / len;
    }
}
