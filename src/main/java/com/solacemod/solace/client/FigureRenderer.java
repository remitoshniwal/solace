package com.solacemod.solace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solacemod.solace.Solace;
import com.solacemod.solace.client.mesh.Figure;
import com.solacemod.solace.client.mesh.Mat4;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws Solace's everyday body (client/mesh/Figure): the normal parts, the creeping veins, the glowing eyes, and any
 * parts that are showing their negative side (inverted, lit, with flowing light around them; no eyes there).
 * Shared by Solace itself, its copy in the distance, and the tiny figure inside the Lantern.
 */
final class FigureRenderer {
    /** A plain, faintly textured white: the shapes carry their own colours. */
    static final ResourceLocation CLOTH = Solace.id("textures/entity/solace_cloth.png");
    private static final ResourceLocation AURA = Solace.id("textures/entity/solace_aura.png");
    private static final ResourceLocation[] VEINS = {
            Solace.id("textures/entity/solace_veins_1.png"), Solace.id("textures/entity/solace_veins_2.png"),
            Solace.id("textures/entity/solace_veins_3.png"), Solace.id("textures/entity/solace_veins_4.png")};

    private FigureRenderer() {
    }

    /**
     * @param negativeMask which body areas are negative right now (Figure.BODY, ...); 0 = none
     * @param veinStage    0 = no veins, 1..4 = how far they've spread
     * @param eyeLevel     brightness of the eyes (0 = closed)
     * @param uneasy       later chapters: paler hands, tie pulled loose
     */
    static void draw(Figure fig, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay, int negativeMask, int veinStage,
                     float eyeLevel, boolean uneasy, float t) {
        Mat4 m = new Mat4();
        int normalMask = Figure.ALL & ~negativeMask;
        fig.eyes.visible = fig.eyeHalo.visible = false;
        if (normalMask != 0) {
            fig.root.draw(m, new MeshDraw(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(CLOTH)), light, overlay),
                    normalMask, false, 0xFFFFFFFF, uneasy ? FigureRenderer::uneasy : null, 0);
            if (veinStage > 0) {
                fig.root.draw(m, new MeshDraw(poseStack, buffer.getBuffer(RenderType.entityTranslucent(VEINS[veinStage - 1])), light, overlay),
                        normalMask, false, 0xFFFFFFFF, null, 0);
            }
        }
        // The eyes, only while the body is in its normal form.
        if ((negativeMask & Figure.BODY) == 0 && eyeLevel > 0) {
            int full = LightTexture.FULL_BRIGHT;
            MeshDraw glow = new MeshDraw(poseStack, buffer.getBuffer(RenderType.eyes(CLOTH)), full, OverlayTexture.NO_OVERLAY);
            fig.drawEyes(m, glow, grey(eyeLevel), false);
            fig.drawEyes(m, glow, grey(eyeLevel * 0.25F), true);
        }
        if (negativeMask != 0) {
            int full = LightTexture.FULL_BRIGHT;
            fig.root.draw(m, new MeshDraw(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(CLOTH)), full, overlay),
                    negativeMask, false, 0xFFFFFFFF, FigureRenderer::negative, 0);
            fig.root.draw(m, new MeshDraw(poseStack, buffer.getBuffer(RenderType.energySwirl(AURA, (t * 0.01F) % 1F, (t * 0.025F) % 1F)), full,
                    OverlayTexture.NO_OVERLAY), negativeMask, true, grey(0.55F), c -> 0xFFFFFFFF, 0);
        }
    }

    /** The photo negative of a colour: brightness flipped, black and white with a faint cold tint. */
    static int negative(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        int v = 255 - (int) (0.3 * r + 0.59 * g + 0.11 * b);
        return (c & 0xFF000000) | Math.max(0, v - 6) << 16 | Math.max(0, v - 4) << 8 | Math.min(255, v + 4);
    }

    private static int uneasy(int c) {
        if (c == Figure.SKIN) return 0xFFCFC6C2;   // paler hands
        if (c == Figure.TIE) return Figure.SHIRT;  // tie pulled loose
        return c;
    }

    static int grey(float level) {
        int v = Math.max(0, Math.min(255, (int) (level * 255)));
        return 0xFF000000 | v << 16 | v << 8 | v;
    }
}
