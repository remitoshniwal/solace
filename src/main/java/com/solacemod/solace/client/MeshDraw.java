package com.solacemod.solace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solacemod.solace.client.mesh.MeshSink;

/** Sends the rounded 3D shapes (client/mesh) to Minecraft's renderer. */
final class MeshDraw implements MeshSink {
    private final PoseStack.Pose pose;
    private final VertexConsumer out;
    private final int light, overlay;

    MeshDraw(PoseStack poseStack, VertexConsumer out, int light, int overlay) {
        this.pose = poseStack.last();
        this.out = out;
        this.light = light;
        this.overlay = overlay;
    }

    @Override
    public void quad(float[] pos, float[] nrm, float[] uv, int[] argb) {
        for (int i = 0; i < 4; i++) {
            out.addVertex(pose, pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2])
                    .setColor(argb[i])
                    .setUv(uv[i * 2], uv[i * 2 + 1])
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(pose, nrm[i * 3], nrm[i * 3 + 1], nrm[i * 3 + 2]);
        }
    }
}
