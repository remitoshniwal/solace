package com.solacemod.solace.client.mesh;

/**
 * Solace's true form, designed in 3D: one smooth, see-through, glowing body shaped like a lava lamp, that ripples
 * like liquid. Inside, black ferrofluid blobs bristling with spikes rise and sink, glowing wax drifts, and a bright
 * core shines in the middle. A few smooth tendrils trail beneath it and two reach out from the sides.
 * Units are blocks, the bottom of the body at y = 0, facing +z. Everything here moves, so it's rebuilt each frame.
 */
public final class LanternShape {
    public static final int GLASS = 0x5A38C8D8, GLASS_HOLLOW = 0xD0101418, TENDRIL = 0x7044D8E6, TENDRIL_HOLLOW = 0xC0181C22,
            FERRO = 0xFF050608, FERRO_SHEEN = 0xFF1C5A66, WAX = 0xFF7FF4FF, WAX_HOLLOW = 0xFFB01010, CORE = 0xFFE8FFFF, CORE_HOLLOW = 0xFFFF5040;

    /** The body's outline from bottom to top: {height, radius}. Like a lava lamp, widest a little below the middle. */
    static final float[][] BODY = {{0.0f, 0.02f}, {0.06f, 0.16f}, {0.18f, 0.27f}, {0.36f, 0.33f}, {0.56f, 0.34f}, {0.78f, 0.31f},
            {1.0f, 0.255f}, {1.2f, 0.19f}, {1.36f, 0.12f}, {1.46f, 0.05f}, {1.5f, 0.0f}};
    public static final float HEIGHT = 1.5f;
    public static final float CORE_Y = 0.62f;

    public final Mesh glass = new Mesh(), aura = new Mesh(), tendrils = new Mesh(), ferro = new Mesh(), wax = new Mesh(), core = new Mesh(), coreGlow = new Mesh();

    /**
     * Rebuilds everything for this moment.
     *
     * @param t         time in ticks (real time, so it moves even when the world is frozen)
     * @param agitation 1 = calm, higher = upset (faster blobs, longer spikes)
     * @param hollow    the boss: dark body, red light
     */
    public void update(float t, float agitation, boolean hollow) {
        float tt = t * agitation;
        glass.clear();
        aura.clear();
        tendrils.clear();
        ferro.clear();
        wax.clear();
        core.clear();
        coreGlow.clear();

        // The body ripples: slow waves running round and up it, like liquid held in a shape.
        Shapes.Wobble ripple = (a, y) -> 1 + 0.035f * (float) Math.sin(a * 3 + tt * 0.07f + y * 6) + 0.02f * (float) Math.sin(a * 5 - tt * 0.05f + y * 9);
        Shapes.lathe(glass, 0, 0, BODY, 1, 0.92f, 0, (float) (2 * Math.PI), 28, 3, Shapes.solid(hollow ? GLASS_HOLLOW : GLASS), ripple);
        float[][] outer = new float[BODY.length][2];
        for (int i = 0; i < BODY.length; i++) {
            outer[i][0] = BODY[i][0] * 1.03f - 0.02f;
            outer[i][1] = BODY[i][1] * 1.1f + 0.015f;
        }
        Shapes.lathe(aura, 0, 0, outer, 1, 0.92f, 0, (float) (2 * Math.PI), 28, 2, Shapes.solid(0xFFFFFFFF), ripple);

        // Ferrofluid: black blobs drifting up and down inside, each covered in spikes that stretch and shrink.
        for (int i = 0; i < 7; i++) {
            float sp = 0.016f + i * 0.0035f, ph = i * 1.9f;
            float y = 0.12f + (0.5f + 0.5f * (float) Math.sin(tt * sp + ph)) * 1.0f;
            float room = radiusAt(y) * 0.62f;
            float x = (float) Math.sin(tt * sp * 0.7f + ph * 2) * room, z = (float) Math.cos(tt * sp * 0.6f + ph) * room * 0.8f;
            float r = 0.045f + (i % 3) * 0.022f;
            float squash = 0.15f * (float) Math.sin(tt * sp * 2 + ph);
            Shapes.ellipsoid(ferro, x, y, z, r * (1 + squash), r * (1 - squash), r * (1 + squash), 12, 8,
                    (u, v) -> v > 0.7f && u < 0.3f ? FERRO_SHEEN : FERRO);
            int spikes = 14;
            for (int k = 0; k < spikes; k++) {
                // Spread the spikes evenly over the blob (golden-angle spiral).
                double fy = 1 - 2 * (k + 0.5) / spikes, fr = Math.sqrt(1 - fy * fy), fa = k * 2.39996;
                float dx = (float) (Math.cos(fa) * fr), dy = (float) fy, dz = (float) (Math.sin(fa) * fr);
                float len = r * (0.5f + 1.1f * Math.abs((float) Math.sin(tt * 0.08f + i * 1.3f + k * 0.7f)));
                Shapes.cone(ferro, x + dx * r * 0.8f, y + dy * r * 0.8f, z + dz * r * 0.8f, dx, dy, dz, r * 0.32f, len, 6, Shapes.solid(FERRO));
            }
        }

        // Lava-lamp wax: soft glowing blobs, slower and bigger, swelling as they rise.
        for (int i = 0; i < 5; i++) {
            float sp = 0.009f + i * 0.0025f, ph = i * 2.6f + 1;
            float y = 0.15f + (0.5f + 0.5f * (float) Math.sin(tt * sp + ph)) * 0.95f;
            float room = radiusAt(y) * 0.5f;
            float x = (float) Math.cos(tt * sp * 0.8f + ph) * room, z = (float) Math.sin(tt * sp * 0.5f + ph) * room * 0.8f;
            float swell = 0.07f + 0.025f * (float) Math.sin(tt * sp * 3 + ph);
            Shapes.ellipsoid(wax, x, y, z, swell * 1.1f, swell * (0.85f + 0.3f * (float) Math.cos(tt * sp * 2 + ph)), swell, 12, 8,
                    Shapes.solid(hollow ? WAX_HOLLOW : WAX));
        }

        // The core and its glow.
        float beat = 1 + 0.07f * (float) Math.sin(tt * (hollow ? 0.3f : 0.12f));
        Shapes.ellipsoid(core, 0, CORE_Y, 0, 0.085f * beat, 0.085f * beat, 0.085f * beat, 16, 10, Shapes.solid(hollow ? CORE_HOLLOW : CORE));
        Shapes.ellipsoid(coreGlow, 0, CORE_Y, 0, 0.19f * beat, 0.19f * beat, 0.19f * beat, 16, 10, Shapes.solid(hollow ? CORE_HOLLOW : CORE));

        // Tendrils: five trailing underneath like a jellyfish's, two reaching out from the sides like arms.
        int tc = hollow ? TENDRIL_HOLLOW : TENDRIL;
        for (int i = 0; i < 5; i++) {
            double a = i * 2 * Math.PI / 5 + 0.3;
            float bx = (float) Math.sin(a) * 0.17f, bz = (float) Math.cos(a) * 0.15f;
            tendril(bx, 0.07f, bz, (float) Math.sin(a) * 0.12f, -1, (float) Math.cos(a) * 0.12f, 0.62f, 0.042f, tt * 0.05f + i * 1.7f, tc);
        }
        for (int side : new int[]{-1, 1}) {
            tendril(side * 0.29f, 0.85f, 0, side * 0.6f, -0.8f, 0.1f, 0.55f, 0.048f, tt * 0.04f + side, tc);
        }
    }

    /** One smooth tendril: a tapering tube along a gently waving curve. */
    private void tendril(float x, float y, float z, float dx, float dy, float dz, float length, float r0, float phase, int color) {
        int n = 10;
        float[][] pts = new float[n][3];
        float[] radii = new float[n];
        float[] d = Shapes.norm(new float[]{dx, dy, dz});
        float px = x, py = y, pz = z;
        for (int k = 0; k < n; k++) {
            float f = (float) k / (n - 1);
            pts[k][0] = px;
            pts[k][1] = py;
            pts[k][2] = pz;
            radii[k] = r0 * (1 - f * 0.88f);
            float step = length / (n - 1);
            float wave = (float) Math.sin(phase + f * 3.2f) * 0.6f * f;
            float wave2 = (float) Math.cos(phase * 0.8f + f * 2.6f) * 0.5f * f;
            px += (d[0] + wave) * step;
            py += (d[1] + Math.abs(wave2) * 0.3f) * step;
            pz += (d[2] + wave2) * step;
        }
        Shapes.tube(tendrils, pts, radii, 8, Shapes.solid(color), true);
    }

    static float radiusAt(float y) {
        for (int i = 0; i < BODY.length - 1; i++) {
            if (y >= BODY[i][0] && y <= BODY[i + 1][0]) {
                float f = (y - BODY[i][0]) / (BODY[i + 1][0] - BODY[i][0]);
                return BODY[i][1] + (BODY[i + 1][1] - BODY[i][1]) * f;
            }
        }
        return 0.05f;
    }
}
