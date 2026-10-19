package com.solacemod.solace.client.mesh;

/**
 * Solace's everyday body, designed in 3D: a tall headless figure in a long tailored coat. Rounded chest and padded
 * shoulders, a coat that flares from the waist and sways, tapered arms and legs, long-fingered hands, rounded shoes.
 * The neck ends in a flat, dark cut; two eyes float just above it.
 * Units are blocks, feet at y = 0, facing +z. No Minecraft code here (the preview tool draws it too).
 */
public final class Figure {
    /** Body areas, for showing some parts in the negative form and others normally. */
    public static final int BODY = 1, RIGHT_ARM = 2, LEFT_ARM = 4, LEGS = 8, ALL = 15;

    // Colours (ARGB). Change these to recolour him.
    public static final int COAT = 0xFF262733, COAT_DARK = 0xFF16171E, SHIRT = 0xFFE9E9E5, TIE = 0xFF0F0E13, SKIN = 0xFFE2D8D0,
            TROUSERS = 0xFF1D1E27, SHOE = 0xFF0B0B0D, VOID = 0xFF020203, BUTTON = 0xFF050506, EYE = 0xFFE4FBFF;

    static final float HIP = 0.92f, NECK = 1.62f, EYES = 1.71f;
    /** The torso's outline from the waist up: {height, radius}. Wider than deep (see SX/SZ). */
    static final float[][] TORSO = {{0.88f, 0.18f}, {0.96f, 0.175f}, {1.05f, 0.165f}, {1.15f, 0.17f}, {1.27f, 0.205f}, {1.38f, 0.232f},
            {1.47f, 0.238f}, {1.53f, 0.215f}, {1.575f, 0.16f}, {1.605f, 0.11f}, {1.62f, 0.092f}};
    static final float SX = 1.0f, SZ = 0.64f;

    public final Bone root = new Bone("root", 0, 0, 0);
    public final Bone pelvis, torso, coat, eyes, eyeHalo;
    public final Bone rightArm, rightForearm, rightHand, leftArm, leftForearm, leftHand;
    public final Bone rightThigh, rightShin, leftThigh, leftShin;
    public final Bone[] fingers = new Bone[10];
    private final float grow;
    private float coatSway = -1;

    public Figure(float shellGrow) {
        this.grow = shellGrow;
        pelvis = root.add(new Bone("pelvis", 0, HIP, 0));
        pelvis.part = BODY;
        torso = pelvis.add(new Bone("torso", 0, 0, 0));
        coat = pelvis.add(new Bone("coat", 0, 0, 0));
        eyes = torso.add(new Bone("eyes", 0, EYES - HIP, 0.0f));
        eyeHalo = torso.add(new Bone("eye_halo", 0, EYES - HIP, 0.0f));
        build(torso, g -> torsoMesh(g));
        rebuildCoat(0);
        build(eyes, g -> eyeMesh(0.0f));
        build(eyeHalo, g -> eyeMesh(0.028f));

        rightArm = arm(-1);
        rightForearm = rightArm.children.get(0);
        rightHand = rightForearm.children.get(0);
        leftArm = arm(1);
        leftForearm = leftArm.children.get(0);
        leftHand = leftForearm.children.get(0);
        for (int i = 0; i < 5; i++) {
            fingers[i] = rightHand.children.get(i);
            fingers[5 + i] = leftHand.children.get(i);
        }
        rightThigh = leg(-1);
        rightShin = rightThigh.children.get(0);
        leftThigh = leg(1);
        leftShin = leftThigh.children.get(0);
    }

    private interface MeshMaker {
        Mesh make(float grow);
    }

    /** Builds a part's shape, and its slightly bigger "shell" copy for the negative form's glow. */
    private void build(Bone b, MeshMaker maker) {
        b.mesh = maker.make(0);
        b.shell = maker.make(grow);
    }

    // ---------- Torso: chest, shoulders, shirt and tie, the flat dark neck ----------

    private Mesh torsoMesh(float g) {
        Mesh m = new Mesh();
        float[][] prof = new float[TORSO.length][2];
        for (int i = 0; i < TORSO.length; i++) {
            prof[i][0] = TORSO[i][0] - HIP;
            prof[i][1] = TORSO[i][1] + g;
        }
        Shapes.lathe(m, 0, 0, prof, SX, SZ, 0, (float) (2 * Math.PI), 56, 6, (u, v) -> torsoColor(u, v), null);
        // The cut where a neck should go on: flat and dark, nothing inside.
        float top = NECK - HIP;
        Shapes.disc(m, 0, top - 0.002f, 0, 0.09f * SX + g, 0.09f * SZ + g, 20, true, Shapes.solid(g > 0 ? COAT : VOID));
        // Padded shoulders.
        for (int side : new int[]{-1, 1}) {
            Shapes.ellipsoid(m, side * 0.225f, 1.455f - HIP, 0, 0.088f + g, 0.068f + g, 0.1f + g, 16, 10, Shapes.solid(COAT));
        }
        if (g == 0) {
            // Two buttons where the coat closes.
            for (float y : new float[]{1.11f, 1.20f}) {
                float r = radiusAt(y) * SZ;
                Shapes.ellipsoid(m, 0, y - HIP, r + 0.004f, 0.011f, 0.011f, 0.006f, 8, 6, Shapes.solid(BUTTON));
            }
        }
        return m;
    }

    /** The coat everywhere, a white shirt in a V at the chest with a dark tie, darker lapels, and the shirt collar at the top. */
    private static int torsoColor(float u, float v) {
        float y = TORSO[0][0] + v * (TORSO[TORSO.length - 1][0] - TORSO[0][0]);
        double th = u * 2 * Math.PI;
        double front = Math.abs(th > Math.PI ? th - 2 * Math.PI : th); // 0 at the front, PI at the back
        if (y > 1.59f) return SHIRT; // the collar
        float vWidth = y < 1.25f ? -1 : (y - 1.25f) / (1.60f - 1.25f) * 0.78f;
        if (front < vWidth) return front < 0.07f && y < 1.575f ? TIE : SHIRT;
        if (front < vWidth + 0.14f) return COAT_DARK; // lapel
        if (front < 0.03f && y < 1.27f) return COAT_DARK; // where the coat closes
        return COAT;
    }

    static float radiusAt(float y) {
        for (int i = 0; i < TORSO.length - 1; i++) {
            if (y >= TORSO[i][0] && y <= TORSO[i + 1][0]) {
                float f = (y - TORSO[i][0]) / (TORSO[i + 1][0] - TORSO[i][0]);
                return TORSO[i][1] + (TORSO[i + 1][1] - TORSO[i][1]) * f;
            }
        }
        return TORSO[0][1];
    }

    private static Mesh eyeMesh(float extra) {
        Mesh m = new Mesh();
        for (int side : new int[]{-1, 1}) {
            Shapes.ellipsoid(m, side * 0.048f, 0, 0.03f, 0.021f + extra, 0.012f + extra * 0.7f, 0.012f + extra * 0.5f, 12, 8, Shapes.solid(EYE));
        }
        return m;
    }

    // ---------- The coat skirt: flares from the waist to the knees, open at the front, swings as he walks ----------

    /** Rebuilds the coat for how much it's swinging (0 = hanging still). Cheap enough to do every frame. */
    public void rebuildCoat(float sway) {
        float s = Math.round(sway * 40) / 40f; // only rebuild when it changed noticeably
        if (s == coatSway) return;
        coatSway = s;
        coat.mesh = coatMesh(0, s);
        coat.shell = coatMesh(grow, s);
    }

    private static Mesh coatMesh(float g, float sway) {
        Mesh m = new Mesh();
        float[][] prof = {{0.47f - HIP, 0.285f + g}, {0.60f - HIP, 0.26f + g}, {0.75f - HIP, 0.228f + g}, {0.90f - HIP, 0.196f + g}, {1.0f - HIP, 0.183f + g}};
        Shapes.lathe(m, 0, 0, prof, 1.02f, 0.70f, 0.30f, (float) (2 * Math.PI - 0.30f), 32, 3,
                (u, v) -> v < 0.06f ? COAT_DARK : COAT,
                (angle, y) -> {
                    // Lower down, the back of the coat kicks out as he walks.
                    float low = Math.max(0, (0.98f - HIP - y) / 0.48f);
                    float back = (float) (0.5 - 0.5 * Math.cos(angle));
                    return 1 + sway * low * (0.18f * back + 0.05f);
                });
        return m;
    }

    // ---------- Arms: upper arm, forearm with a white cuff, a long-fingered hand ----------

    private Bone arm(int side) {
        Bone upper = pelvis.add(new Bone(side < 0 ? "right_arm" : "left_arm", side * 0.275f, 1.452f - HIP, 0));
        upper.part = side < 0 ? RIGHT_ARM : LEFT_ARM;
        build(upper, g -> {
            Mesh m = new Mesh();
            Shapes.tube(m, new float[][]{{0, 0.02f, 0}, {0, -0.15f, 0}, {0, -0.30f, 0}}, new float[]{0.078f + g, 0.071f + g, 0.062f + g}, 16, Shapes.solid(COAT), true);
            return m;
        });
        Bone fore = upper.add(new Bone("forearm", 0, -0.30f, 0));
        build(fore, g -> {
            Mesh m = new Mesh();
            Shapes.tube(m, new float[][]{{0, 0, 0}, {0, -0.14f, 0}, {0, -0.255f, 0}}, new float[]{0.063f + g, 0.057f + g, 0.052f + g}, 16, Shapes.solid(COAT), true);
            Shapes.tube(m, new float[][]{{0, -0.245f, 0}, {0, -0.285f, 0}}, new float[]{0.056f + g, 0.054f + g}, 16, Shapes.solid(SHIRT), false);
            return m;
        });
        Bone hand = fore.add(new Bone("hand", 0, -0.29f, 0));
        build(hand, g -> {
            Mesh m = new Mesh();
            Shapes.ellipsoid(m, 0, -0.05f, 0.004f, 0.025f + g, 0.058f + g, 0.036f + g, 12, 10, Shapes.solid(SKIN));
            return m;
        });
        // Four long fingers and a thumb.
        for (int f = 0; f < 4; f++) {
            Bone finger = hand.add(new Bone("finger" + f, 0, -0.098f, -0.024f + f * 0.016f));
            build(finger, g -> {
                Mesh m = new Mesh();
                Shapes.tube(m, new float[][]{{0, 0, 0}, {0, -0.045f, 0}, {0, -0.09f, 0}}, new float[]{0.0098f + g, 0.0092f + g, 0.0078f + g}, 8, Shapes.solid(SKIN), true);
                return m;
            });
        }
        Bone thumb = hand.add(new Bone("thumb", side * -0.012f, -0.035f, 0.028f));
        thumb.zRot = side * -0.5f;
        thumb.xRot = -0.4f;
        build(thumb, g -> {
            Mesh m = new Mesh();
            Shapes.tube(m, new float[][]{{0, 0, 0}, {0, -0.035f, 0}, {0, -0.065f, 0}}, new float[]{0.009f + g, 0.0085f + g, 0.007f + g}, 8, Shapes.solid(SKIN), true);
            return m;
        });
        return upper;
    }

    // ---------- Legs: thigh, shin, a long rounded shoe ----------

    private Bone leg(int side) {
        Bone thigh = pelvis.add(new Bone(side < 0 ? "right_leg" : "left_leg", side * 0.105f, 0, 0));
        thigh.part = LEGS;
        build(thigh, g -> {
            Mesh m = new Mesh();
            Shapes.tube(m, new float[][]{{0, 0.03f, 0}, {0, -0.22f, 0}, {0, -0.44f, 0}}, new float[]{0.097f + g, 0.088f + g, 0.074f + g}, 16, Shapes.solid(TROUSERS), true);
            return m;
        });
        Bone shin = thigh.add(new Bone("shin", 0, -0.44f, 0));
        build(shin, g -> {
            Mesh m = new Mesh();
            Shapes.tube(m, new float[][]{{0, 0, 0}, {0, -0.2f, 0}, {0, -0.405f, 0}}, new float[]{0.073f + g, 0.066f + g, 0.058f + g}, 16, Shapes.solid(TROUSERS), true);
            Shapes.ellipsoid(m, 0, -0.445f, 0.045f, 0.064f + g, 0.042f + g, 0.135f + g, 14, 10, Shapes.solid(SHOE));
            return m;
        });
        return thigh;
    }

    /** Draws just the eyes (or their soft glow), wherever the body has moved them. */
    public void drawEyes(Mat4 base, MeshSink sink, int tint, boolean halo) {
        Mat4 m = base.copy();
        for (Bone b : new Bone[]{root, pelvis, torso}) {
            m.translate(b.px, b.py, b.pz).rotateZ(b.zRot).rotateY(b.yRot).rotateX(b.xRot);
            if (b.scale != 1) m.scale(b.scale, b.scale, b.scale);
        }
        Bone e = halo ? eyeHalo : eyes;
        boolean was = e.visible;
        e.visible = true;
        e.draw(m, sink, ALL, false, tint, null, ALL);
        e.visible = was;
    }

    // ---------- Poses ----------

    /**
     * Poses the body for this frame.
     *
     * @param limbSwing    how far along the walk cycle
     * @param amount       how fast he's walking (0 = still)
     * @param age          time in ticks (for breathing and twitches)
     * @param lookYaw      where he's looking, in radians (the eyes turn)
     * @param attack       0..1 while swinging an arm (the one-tap)
     * @param seed         makes his twitches different from another Solace's
     */
    public void pose(float limbSwing, float amount, float age, float lookYaw, float lookPitch, float attack, int seed) {
        float walk = (float) Math.cos(limbSwing * 0.6662f);
        float walk2 = (float) Math.sin(limbSwing * 0.6662f);
        float breathe = (float) Math.sin(age * 0.06f);

        rightThigh.xRot = -walk * 0.7f * amount;
        leftThigh.xRot = walk * 0.7f * amount;
        rightShin.xRot = Math.max(0, walk2) * 0.9f * amount;
        leftShin.xRot = Math.max(0, -walk2) * 0.9f * amount;

        rightArm.xRot = walk * 0.32f * amount + breathe * 0.015f;
        leftArm.xRot = -walk * 0.32f * amount - breathe * 0.015f;
        rightArm.zRot = -0.06f;
        leftArm.zRot = 0.06f;
        rightForearm.xRot = -0.2f - Math.max(0, walk) * 0.25f * amount;
        leftForearm.xRot = -0.2f - Math.max(0, -walk) * 0.25f * amount;
        if (attack > 0) {
            float a = (float) Math.sin(attack * Math.PI);
            rightArm.xRot = -2.1f * a;
            rightForearm.xRot = -0.1f;
        }
        for (int i = 0; i < fingers.length; i++) {
            if (fingers[i].name.equals("thumb")) continue;
            boolean twitch = Math.floorMod((int) (age / 3) * 7919 + seed + i * 104729, 97) == 0;
            fingers[i].xRot = -0.22f - (twitch ? 0.9f : 0);
        }
        torso.py = breathe * 0.004f;
        eyes.py = eyeHalo.py = EYES - HIP + breathe * 0.006f;
        eyes.yRot = eyeHalo.yRot = lookYaw;
        eyes.xRot = eyeHalo.xRot = lookPitch;
        rebuildCoat(Math.min(1, amount * 1.2f));
    }

    /** Knees up, arms around them: the tiny figure curled inside the Lantern. */
    public void curl() {
        pelvis.xRot = 0.5f;
        rightThigh.xRot = leftThigh.xRot = -1.9f;
        rightShin.xRot = leftShin.xRot = 2.2f;
        rightArm.xRot = leftArm.xRot = -1.1f;
        rightForearm.xRot = leftForearm.xRot = -1.2f;
        rightArm.zRot = -0.25f;
        leftArm.zRot = 0.25f;
    }
}
