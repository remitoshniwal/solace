package com.solacemod.solace.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A model made in Blockbench (.bbmodel, "Generic Model" format), read straight from the file and drawn by the mod.
 * No extra library needed. Supports groups (bones), cubes, and mesh faces (triangles and quads).
 * <p>
 * Group names carry meaning:
 * <ul>
 *   <li>a name starting with {@code n_} shows only in the normal form, {@code x_} only in the negative;</li>
 *   <li>{@code arm_r} and {@code arm_l} mark the arms (so one arm can flash negative on its own);</li>
 *   <li>a name containing "cloak" or "robe" flutters: its cloth ripples, more towards the hem.</li>
 * </ul>
 * The renderer moves bones by name (see {@link #bone}). Blockbench pixels are turned into blocks, and the model's
 * front (north in Blockbench) is turned to face the way the entity looks.
 */
public final class BbModel {
    public static final int BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 4, ALL = 7;
    private static final int SHARED = 0, NORMAL_ONLY = 1, NEGATIVE_ONLY = 2;

    public final Bone root = new Bone("", new float[3], new float[3], BODY, SHARED, false);
    private final Map<String, Bone> byName = new HashMap<>();
    private final Bone missing = new Bone("missing", new float[3], new float[3], BODY, SHARED, false);

    /** One drawable face: four corners (a triangle repeats its last corner), in Blockbench pixels. */
    static final class Face {
        final float[] pos = new float[12];
        final float[] uv = new float[8];
        /** For cloth: how far down each corner hangs (0 at the top, 1 at the hem). */
        float[] fall;
    }

    /** A group from Blockbench. Its extra rotation, offset and scale are set by the renderer each frame. */
    public static final class Bone {
        public final String name;
        final float[] pivot, baseRot;
        final int part, form;
        final boolean flutter;
        final List<Bone> children = new ArrayList<>();
        final List<Face> faces = new ArrayList<>();
        public float rx, ry, rz, ox, oy, oz, sx = 1, sy = 1, sz = 1;
        public boolean visible = true;

        Bone(String name, float[] pivot, float[] baseRot, int part, int form, boolean flutter) {
            this.name = name;
            this.pivot = pivot;
            this.baseRot = baseRot;
            this.part = part;
            this.form = form;
            this.flutter = flutter;
        }

        /** Rotation on top of the model's own, in degrees. */
        public Bone rot(float x, float y, float z) {
            rx = x;
            ry = y;
            rz = z;
            return this;
        }

        public Bone scale(float s) {
            sx = sy = sz = s;
            return this;
        }

        void reset() {
            rx = ry = rz = ox = oy = oz = 0;
            sx = sy = sz = 1;
            visible = true;
            for (Bone c : children) c.reset();
        }
    }

    /** The bone with this name, or a spare one (so a missing part never crashes the game). */
    public Bone bone(String name) {
        return byName.getOrDefault(name, missing);
    }

    /** Puts every bone back to its Blockbench pose. */
    public void reset() {
        root.reset();
    }

    // ------------------------------------------------------------------ loading

    public static BbModel load(InputStream in) {
        JsonObject j = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        BbModel model = new BbModel();
        float texW = 16, texH = 16;
        if (j.has("resolution")) {
            texW = j.getAsJsonObject("resolution").get("width").getAsFloat();
            texH = j.getAsJsonObject("resolution").get("height").getAsFloat();
        }
        Map<String, JsonObject> elements = new HashMap<>();
        for (JsonElement e : j.getAsJsonArray("elements")) elements.put(e.getAsJsonObject().get("uuid").getAsString(), e.getAsJsonObject());
        Map<String, JsonObject> groups = new HashMap<>();
        if (j.has("groups")) for (JsonElement g : j.getAsJsonArray("groups")) groups.put(g.getAsJsonObject().get("uuid").getAsString(), g.getAsJsonObject());
        for (JsonElement node : j.getAsJsonArray("outliner")) model.add(model.root, node, elements, groups, texW, texH);
        return model;
    }

    private void add(Bone parent, JsonElement node, Map<String, JsonObject> elements, Map<String, JsonObject> groups, float texW, float texH) {
        if (node.isJsonPrimitive()) {
            JsonObject e = elements.get(node.getAsString());
            if (e == null || (e.has("export") && !e.get("export").getAsBoolean())) return;
            String type = e.has("type") ? e.get("type").getAsString() : "cube";
            int before = parent.faces.size();
            if (type.equals("mesh")) addMesh(parent, e, texW, texH);
            else if (type.equals("cube")) addCube(parent, e, texW, texH);
            if (parent.flutter) markFall(parent.faces.subList(before, parent.faces.size()));
            return;
        }
        JsonObject o = node.getAsJsonObject();
        JsonObject info = groups.getOrDefault(o.get("uuid").getAsString(), o);
        String name = info.has("name") ? info.get("name").getAsString() : "group";
        int part = name.equals("arm_r") ? RIGHT_ARM : name.equals("arm_l") ? LEFT_ARM : parent.part;
        int form = name.startsWith("n_") ? NORMAL_ONLY : name.startsWith("x_") ? NEGATIVE_ONLY : parent.form;
        boolean flutter = parent.flutter || name.contains("cloak") || name.contains("robe");
        Bone bone = new Bone(name, vec(info, "origin"), vec(info, "rotation"), part, form, flutter);
        parent.children.add(bone);
        byName.putIfAbsent(name, bone);
        if (o.has("children")) for (JsonElement c : o.getAsJsonArray("children")) add(bone, c, elements, groups, texW, texH);
    }

    private static float[] vec(JsonObject o, String key) {
        float[] v = new float[3];
        if (o.has(key) && o.get(key).isJsonArray()) {
            JsonArray a = o.getAsJsonArray(key);
            for (int i = 0; i < 3 && i < a.size(); i++) v[i] = a.get(i).getAsFloat();
        }
        return v;
    }

    /** Blockbench turns elements Z, then Y, then X on the outside: the X turn happens first. */
    private static Mat4 rotation(float[] origin, float[] rot) {
        return new Mat4().translate(origin[0], origin[1], origin[2])
                .rotateZ(rot[2] * DEG).rotateY(rot[1] * DEG).rotateX(rot[0] * DEG)
                .translate(-origin[0], -origin[1], -origin[2]);
    }

    private static final float DEG = (float) (Math.PI / 180);

    private static void addCube(Bone bone, JsonObject e, float texW, float texH) {
        float[] from = vec(e, "from"), to = vec(e, "to"), origin = vec(e, "origin"), rot = vec(e, "rotation");
        float inflate = e.has("inflate") ? e.get("inflate").getAsFloat() : 0;
        float x1 = from[0] - inflate, y1 = from[1] - inflate, z1 = from[2] - inflate;
        float x2 = to[0] + inflate, y2 = to[1] + inflate, z2 = to[2] + inflate;
        Mat4 m = rotation(origin, rot);
        JsonObject faces = e.getAsJsonObject("faces");
        // Corners of each face in the order top-left, top-right, bottom-right, bottom-left of its texture.
        float[][] corners = {
                {x2, y2, z1, x1, y2, z1, x1, y1, z1, x2, y1, z1}, // north
                {x2, y2, z2, x2, y2, z1, x2, y1, z1, x2, y1, z2}, // east
                {x1, y2, z2, x2, y2, z2, x2, y1, z2, x1, y1, z2}, // south
                {x1, y2, z1, x1, y2, z2, x1, y1, z2, x1, y1, z1}, // west
                {x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2}, // up
                {x1, y1, z2, x2, y1, z2, x2, y1, z1, x1, y1, z1}, // down
        };
        String[] names = {"north", "east", "south", "west", "up", "down"};
        for (int f = 0; f < 6; f++) {
            if (faces == null || !faces.has(names[f])) continue;
            JsonObject face = faces.getAsJsonObject(names[f]);
            if (!face.has("texture") || face.get("texture").isJsonNull()) continue;
            JsonArray uv = face.getAsJsonArray("uv");
            float u1 = uv.get(0).getAsFloat() / texW, v1 = uv.get(1).getAsFloat() / texH;
            float u2 = uv.get(2).getAsFloat() / texW, v2 = uv.get(3).getAsFloat() / texH;
            Face out = new Face();
            for (int k = 0; k < 4; k++) m.point(corners[f][k * 3], corners[f][k * 3 + 1], corners[f][k * 3 + 2], out.pos, k * 3);
            float[] uvs = {u1, v1, u2, v1, u2, v2, u1, v2};
            System.arraycopy(uvs, 0, out.uv, 0, 8);
            bone.faces.add(out);
        }
    }

    private static void addMesh(Bone bone, JsonObject e, float texW, float texH) {
        Mat4 m = rotation(new float[3], vec(e, "rotation"));
        float[] origin = vec(e, "origin");
        JsonObject verts = e.getAsJsonObject("vertices");
        for (Map.Entry<String, JsonElement> fe : e.getAsJsonObject("faces").entrySet()) {
            JsonObject face = fe.getValue().getAsJsonObject();
            if (face.has("texture") && face.get("texture").isJsonNull()) continue;
            JsonArray ids = face.getAsJsonArray("vertices");
            int n = ids.size();
            if (n < 3 || n > 4) continue;
            JsonObject uv = face.getAsJsonObject("uv");
            float[][] p = new float[n][3];
            float[][] t = new float[n][2];
            for (int i = 0; i < n; i++) {
                String id = ids.get(i).getAsString();
                JsonArray v = verts.getAsJsonArray(id);
                float[] w = new float[3];
                m.point(v.get(0).getAsFloat(), v.get(1).getAsFloat(), v.get(2).getAsFloat(), w, 0);
                p[i] = new float[]{w[0] + origin[0], w[1] + origin[1], w[2] + origin[2]};
                JsonArray tv = uv.getAsJsonArray(id);
                t[i] = new float[]{tv.get(0).getAsFloat() / texW, tv.get(1).getAsFloat() / texH};
            }
            if (n == 4) sortLoop(p, t);
            Face out = new Face();
            for (int k = 0; k < 4; k++) {
                int i = Math.min(k, n - 1);
                System.arraycopy(p[i], 0, out.pos, k * 3, 3);
                out.uv[k * 2] = t[i][0];
                out.uv[k * 2 + 1] = t[i][1];
            }
            bone.faces.add(out);
        }
    }

    /** Blockbench doesn't store a quad's corners in order round the edge, so put them in order. */
    private static void sortLoop(float[][] p, float[][] t) {
        float[] c = new float[3];
        for (float[] q : p) for (int k = 0; k < 3; k++) c[k] += q[k] / 4;
        float[] a = sub(p[0], c), b = sub(p[1], c);
        float[] nrm = cross(a, b);
        if (len(nrm) < 1e-6f) nrm = cross(a, sub(p[2], c));
        float[] y = cross(nrm, a);
        double[] ang = new double[4];
        for (int i = 0; i < 4; i++) {
            float[] d = sub(p[i], c);
            ang[i] = Math.atan2(dot(d, y), dot(d, a));
        }
        Integer[] order = {0, 1, 2, 3};
        java.util.Arrays.sort(order, (i, k) -> Double.compare(ang[i], ang[k]));
        float[][] p2 = new float[4][], t2 = new float[4][];
        for (int i = 0; i < 4; i++) {
            p2[i] = p[order[i]];
            t2[i] = t[order[i]];
        }
        System.arraycopy(p2, 0, p, 0, 4);
        System.arraycopy(t2, 0, t, 0, 4);
    }

    private static void markFall(List<Face> faces) {
        float top = -1e9f, bottom = 1e9f;
        for (Face f : faces) for (int k = 0; k < 4; k++) {
            top = Math.max(top, f.pos[k * 3 + 1]);
            bottom = Math.min(bottom, f.pos[k * 3 + 1]);
        }
        float h = Math.max(0.01f, top - bottom);
        for (Face f : faces) {
            f.fall = new float[4];
            for (int k = 0; k < 4; k++) f.fall[k] = (top - f.pos[k * 3 + 1]) / h;
        }
    }

    // ------------------------------------------------------------------ drawing

    /** The starting matrix: Blockbench pixels to blocks, front turned round to face +z like the entity. */
    public static Mat4 base() {
        return new Mat4().rotateY((float) Math.PI).scale(1 / 16F, 1 / 16F, 1 / 16F);
    }

    /**
     * Draws the parts that belong to one pass.
     *
     * @param negativeMask which areas (BODY, RIGHT_ARM, LEFT_ARM) are in the negative right now
     * @param negativePass true to draw the negative areas (with negative-only parts), false for the normal areas
     * @param t            time in ticks, for the cloth ripple
     * @param push         how hard the cloth is pushed back (walking), 0..1
     * @param argb         colour to tint by
     */
    public void draw(Mat4 base, MeshSink out, int negativeMask, boolean negativePass, float t, float push, int argb) {
        draw(root, base, out, negativeMask, negativePass, t, push, new int[]{argb, argb, argb, argb});
    }

    private void draw(Bone b, Mat4 parent, MeshSink out, int mask, boolean negPass, float t, float push, int[] argb) {
        if (!b.visible) return;
        if (b.form == NORMAL_ONLY && negPass && (mask & b.part) != 0) return;
        if (b.form == NEGATIVE_ONLY && !negPass) return;
        if (b.form == NEGATIVE_ONLY && (mask & b.part) == 0) return;
        Mat4 m = parent.copy()
                .translate(b.ox, b.oy, b.oz)
                .translate(b.pivot[0], b.pivot[1], b.pivot[2])
                .rotateZ((b.baseRot[2] + b.rz) * DEG).rotateY((b.baseRot[1] + b.ry) * DEG).rotateX((b.baseRot[0] + b.rx) * DEG)
                .scale(b.sx, b.sy, b.sz)
                .translate(-b.pivot[0], -b.pivot[1], -b.pivot[2]);
        boolean mine = ((mask & b.part) != 0) == negPass;
        if (mine && !b.faces.isEmpty()) {
            float[] pos = new float[12], nrm = new float[12];
            for (Face f : b.faces) {
                for (int k = 0; k < 4; k++) {
                    float x = f.pos[k * 3], y = f.pos[k * 3 + 1], z = f.pos[k * 3 + 2];
                    if (f.fall != null) {
                        // Cloth: ripples travel round it, bigger towards the hem, and it trails back when walking.
                        float d = f.fall[k] * f.fall[k];
                        z += (float) Math.sin(t * 0.11F + x * 0.55F + y * 0.25F) * 0.55F * d + push * 3.5F * d;
                        x += (float) Math.cos(t * 0.08F + z * 0.6F + y * 0.2F) * 0.3F * d;
                    }
                    m.point(x, y, z, pos, k * 3);
                }
                // One normal for the face, from its diagonals.
                float ax = pos[6] - pos[0], ay = pos[7] - pos[1], az = pos[8] - pos[2];
                float bx = pos[9] - pos[3], by = pos[10] - pos[4], bz = pos[11] - pos[5];
                if (bx * bx + by * by + bz * bz < 1e-10f) { // a triangle: use two edges
                    bx = pos[3] - pos[0];
                    by = pos[4] - pos[1];
                    bz = pos[5] - pos[2];
                    float cx = ax, cy = ay, cz = az;
                    ax = bx; ay = by; az = bz;
                    bx = cx; by = cy; bz = cz;
                }
                float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
                float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (l < 1e-8f) continue;
                nx /= l;
                ny /= l;
                nz /= l;
                for (int k = 0; k < 4; k++) {
                    nrm[k * 3] = nx;
                    nrm[k * 3 + 1] = ny;
                    nrm[k * 3 + 2] = nz;
                }
                out.quad(pos, nrm, f.uv, argb);
            }
        }
        for (Bone c : b.children) draw(c, m, out, mask, negPass, t, push, argb);
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float len(float[] a) {
        return (float) Math.sqrt(dot(a, a));
    }
}
