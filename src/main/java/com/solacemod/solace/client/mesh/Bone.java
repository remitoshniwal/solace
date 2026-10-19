package com.solacemod.solace.client.mesh;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * One moving part of a model (an upper arm, a finger...). It has a pivot (the joint it turns around), a rotation,
 * a shape, a slightly bigger copy of the shape (the glowing shell in the negative form), and child parts that move with it.
 */
public final class Bone {
    public final String name;
    public float px, py, pz;
    public float xRot, yRot, zRot;
    public float scale = 1;
    public Mesh mesh = new Mesh();
    public Mesh shell = new Mesh();
    /** Which body area this part belongs to (for the negative flashes), or 0 to inherit from its parent. */
    public int part;
    public boolean visible = true;
    public final List<Bone> children = new ArrayList<>();

    public Bone(String name, float px, float py, float pz) {
        this.name = name;
        this.px = px;
        this.py = py;
        this.pz = pz;
    }

    public Bone add(Bone child) {
        children.add(child);
        return child;
    }

    /** Draws this part and its children. Only parts whose area is in {@code mask} are drawn. */
    public void draw(Mat4 parent, MeshSink sink, int mask, boolean useShell, int tint, IntUnaryOperator recolor, int inherited) {
        if (!visible) return;
        int area = part != 0 ? part : inherited;
        Mat4 m = parent.copy().translate(px, py, pz).rotateZ(zRot).rotateY(yRot).rotateX(xRot);
        if (scale != 1) m.scale(scale, scale, scale);
        if ((area & mask) != 0) (useShell ? shell : mesh).emit(m, sink, tint, recolor);
        for (Bone c : children) c.draw(m, sink, mask, useShell, tint, recolor, area);
    }
}
