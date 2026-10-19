package com.solacemod.solace.client.model;

/** Where finished quads go: the game's renderer, or the preview tool. Positions are in blocks, y up. */
public interface MeshSink {
    /**
     * One four-cornered face. Arrays hold 4 corners: pos and nrm have 12 floats (x,y,z per corner), uv has 8, argb has 4.
     */
    void quad(float[] pos, float[] nrm, float[] uv, int[] argb);
}
