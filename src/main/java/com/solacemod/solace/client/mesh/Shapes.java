package com.solacemod.solace.client.mesh;

/**
 * Rounded building blocks: ellipsoids, surfaces of revolution (like a shape turned on a lathe), tubes along a curve,
 * cones and discs. Each one adds faces to a {@link Mesh}. Units are blocks, y is up, and +z is the front.
 */
public final class Shapes {
    private Shapes() {
    }

    /** Colour for the face at (u, v): u goes around the shape (0..1), v along it (0..1). */
    public interface Paint {
        int at(float u, float v);
    }

    public static Paint solid(int argb) {
        return (u, v) -> argb;
    }

    /** A point on a surface, plus a point "inside" it (used to make the faces' normals point outward). */
    private interface Surface {
        void at(int i, int j, float[] p, float[] inside);
    }

    /** Builds an nu x nv grid of faces from a surface. closedU = the surface wraps all the way around. */
    private static void grid(Mesh mesh, int nu, int nv, boolean closedU, Surface s, Paint paint, float uRepeat, float vRepeat) {
        float[][] pts = new float[(nu + 1) * (nv + 1)][3];
        float[][] ins = new float[(nu + 1) * (nv + 1)][3];
        for (int j = 0; j <= nv; j++) {
            for (int i = 0; i <= nu; i++) s.at(i, j, pts[j * (nu + 1) + i], ins[j * (nu + 1) + i]);
        }
        float[][] nrm = new float[pts.length][3];
        for (int j = 0; j <= nv; j++) {
            for (int i = 0; i <= nu; i++) {
                int il = i > 0 ? i - 1 : (closedU ? nu - 1 : i), ir = i < nu ? i + 1 : (closedU ? 1 : i);
                int jd = Math.max(0, j - 1), ju = Math.min(nv, j + 1);
                float[] a = pts[j * (nu + 1) + ir], b = pts[j * (nu + 1) + il], c = pts[ju * (nu + 1) + i], d = pts[jd * (nu + 1) + i];
                float ux = a[0] - b[0], uy = a[1] - b[1], uz = a[2] - b[2];
                float vx = c[0] - d[0], vy = c[1] - d[1], vz = c[2] - d[2];
                float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                float[] p = pts[j * (nu + 1) + i], in = ins[j * (nu + 1) + i];
                float ox = p[0] - in[0], oy = p[1] - in[1], oz = p[2] - in[2];
                if (nx * nx + ny * ny + nz * nz < 1e-12f) {
                    nx = ox;
                    ny = oy;
                    nz = oz;
                }
                if (nx * ox + ny * oy + nz * oz < 0) {
                    nx = -nx;
                    ny = -ny;
                    nz = -nz;
                }
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len < 1e-9f) len = 1;
                nrm[j * (nu + 1) + i] = new float[]{nx / len, ny / len, nz / len};
            }
        }
        float[] p = new float[12], n = new float[12], uv = new float[8];
        int[] c = new int[4];
        int[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int j = 0; j < nv; j++) {
            for (int i = 0; i < nu; i++) {
                int color = paint.at((i + 0.5f) / nu, (j + 0.5f) / nv);
                if ((color >>> 24) == 0) continue; // fully transparent: leave a hole
                for (int k = 0; k < 4; k++) {
                    int ii = i + corners[k][0], jj = j + corners[k][1];
                    int idx = jj * (nu + 1) + ii;
                    System.arraycopy(pts[idx], 0, p, k * 3, 3);
                    System.arraycopy(nrm[idx], 0, n, k * 3, 3);
                    uv[k * 2] = (float) ii / nu * uRepeat;
                    uv[k * 2 + 1] = (float) jj / nv * vRepeat;
                    c[k] = color;
                }
                mesh.quad(p, n, uv, c);
            }
        }
    }

    /** An ellipsoid (a squashed or stretched sphere) centred at (cx, cy, cz). */
    public static void ellipsoid(Mesh mesh, float cx, float cy, float cz, float rx, float ry, float rz, int nu, int nv, Paint paint) {
        grid(mesh, nu, nv, true, (i, j, p, in) -> {
            double th = 2 * Math.PI * i / nu, ph = -Math.PI / 2 + Math.PI * j / nv;
            p[0] = cx + (float) (rx * Math.cos(ph) * Math.sin(th));
            p[1] = cy + (float) (ry * Math.sin(ph));
            p[2] = cz + (float) (rz * Math.cos(ph) * Math.cos(th));
            in[0] = cx;
            in[1] = cy;
            in[2] = cz;
        }, paint, 2, 1);
    }

    /**
     * A surface of revolution around a vertical axis at (cx, cz): the profile is a list of {y, radius} from bottom to top.
     * sx/sz squash it into an oval (e.g. a chest that's wider than it is deep). a0..a1 = which part of the circle
     * (in radians, 0 = front), so a coat can be left open at the front.
     *
     * @param wobble if not null, changes the radius at (angle, y) every frame: liquid-like movement
     */
    public static void lathe(Mesh mesh, float cx, float cz, float[][] profile, float sx, float sz, float a0, float a1, int nu, int subdiv,
                             Paint paint, Wobble wobble) {
        int nv = (profile.length - 1) * subdiv;
        boolean closed = Math.abs((a1 - a0) - 2 * Math.PI) < 1e-3;
        grid(mesh, nu, nv, closed, (i, j, p, in) -> {
            float t = (float) j / subdiv;
            int k = Math.min(profile.length - 2, (int) t);
            float f = t - k;
            float y = profile[k][0] + (profile[k + 1][0] - profile[k][0]) * f;
            float r = profile[k][1] + (profile[k + 1][1] - profile[k][1]) * f;
            double th = a0 + (a1 - a0) * i / nu;
            if (wobble != null) r *= wobble.at((float) th, y);
            p[0] = cx + (float) (Math.sin(th) * r * sx);
            p[1] = y;
            p[2] = cz + (float) (Math.cos(th) * r * sz);
            in[0] = cx;
            in[1] = y;
            in[2] = cz;
        }, paint, 3, Math.max(1, profile[profile.length - 1][0] - profile[0][0]) * 3);
    }

    public interface Wobble {
        float at(float angle, float y);
    }

    /**
     * A tube through a list of points, thicker or thinner at each point (radii), with rounded ends.
     * Good for arms, legs, fingers and tendrils.
     */
    public static void tube(Mesh mesh, float[][] pts, float[] radii, int nu, Paint paint, boolean roundEnds) {
        int n = pts.length;
        float[][] tan = new float[n][3], nor = new float[n][3], bin = new float[n][3];
        for (int i = 0; i < n; i++) {
            float[] a = pts[Math.max(0, i - 1)], b = pts[Math.min(n - 1, i + 1)];
            tan[i] = norm(new float[]{b[0] - a[0], b[1] - a[1], b[2] - a[2]});
        }
        // A first "sideways" direction, then carried along the tube so it doesn't twist.
        float[] ref = Math.abs(tan[0][1]) < 0.9f ? new float[]{0, 1, 0} : new float[]{1, 0, 0};
        nor[0] = norm(cross(cross(tan[0], ref), tan[0]));
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                float[] prev = nor[i - 1];
                float d = dot(prev, tan[i]);
                nor[i] = norm(new float[]{prev[0] - tan[i][0] * d, prev[1] - tan[i][1] * d, prev[2] - tan[i][2] * d});
            }
            bin[i] = cross(tan[i], nor[i]);
        }
        grid(mesh, nu, n - 1, true, (i, j, p, in) -> {
            double th = 2 * Math.PI * i / nu;
            float c = (float) Math.cos(th), s = (float) Math.sin(th), r = radii[j];
            for (int k = 0; k < 3; k++) {
                p[k] = pts[j][k] + r * (c * nor[j][k] + s * bin[j][k]);
                in[k] = pts[j][k];
            }
        }, paint, 1, n);
        if (roundEnds) {
            int seg = Math.max(6, nu / 2);
            if (radii[0] > 0.002f) ellipsoid(mesh, pts[0][0], pts[0][1], pts[0][2], radii[0], radii[0], radii[0], nu, seg, paint);
            if (radii[n - 1] > 0.002f) ellipsoid(mesh, pts[n - 1][0], pts[n - 1][1], pts[n - 1][2], radii[n - 1], radii[n - 1], radii[n - 1], nu, seg, paint);
        }
    }

    /** A pointed cone from base (x, y, z) along direction d. Used for ferrofluid spikes. */
    public static void cone(Mesh mesh, float x, float y, float z, float dx, float dy, float dz, float radius, float length, int nu, Paint paint) {
        float[] d = norm(new float[]{dx, dy, dz});
        float[][] pts = {{x, y, z}, {x + d[0] * length * 0.55f, y + d[1] * length * 0.55f, z + d[2] * length * 0.55f},
                {x + d[0] * length, y + d[1] * length, z + d[2] * length}};
        tube(mesh, pts, new float[]{radius, radius * 0.4f, 0}, nu, paint, false);
    }

    /** A flat oval facing up (or down) at height y. */
    public static void disc(Mesh mesh, float cx, float cy, float cz, float rx, float rz, int nu, boolean up, Paint paint) {
        float[] p = new float[12], n = new float[12], uv = new float[8];
        int[] c = new int[4];
        for (int i = 0; i < nu; i++) {
            double a = 2 * Math.PI * i / nu, b = 2 * Math.PI * (i + 1) / nu;
            float[][] corners = {{cx, cy, cz}, {cx + (float) Math.sin(a) * rx, cy, cz + (float) Math.cos(a) * rz},
                    {cx + (float) Math.sin(b) * rx, cy, cz + (float) Math.cos(b) * rz}, {cx, cy, cz}};
            int color = paint.at((i + 0.5f) / nu, 0.5f);
            for (int k = 0; k < 4; k++) {
                System.arraycopy(corners[k], 0, p, k * 3, 3);
                n[k * 3] = 0;
                n[k * 3 + 1] = up ? 1 : -1;
                n[k * 3 + 2] = 0;
                uv[k * 2] = 0.5f + (corners[k][0] - cx) * 4;
                uv[k * 2 + 1] = 0.5f + (corners[k][2] - cz) * 4;
                c[k] = color;
            }
            mesh.quad(p, n, uv, c);
        }
    }

    static float[] norm(float[] v) {
        float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (l < 1e-9f) return new float[]{0, 1, 0};
        return new float[]{v[0] / l, v[1] / l, v[2] / l};
    }

    static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
}
