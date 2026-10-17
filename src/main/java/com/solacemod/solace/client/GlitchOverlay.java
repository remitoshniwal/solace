package com.solacemod.solace.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.RandomSource;

/**
 * Brief screen static / red pulse / flash message. Kept soft on purpose (no bright strobing).
 * Players can turn it off with comfort.glitchEffects in config/solace-client.toml.
 */
public final class GlitchOverlay {
    private static final RandomSource RANDOM = RandomSource.create();
    private static int kind;
    private static int ticksLeft;
    private static int total;
    private static String message = "";

    private GlitchOverlay() {
    }

    public static void start(int k, int ticks, String msg) {
        kind = k;
        ticksLeft = Math.min(ticks, 100);
        total = ticksLeft;
        message = msg == null ? "" : msg;
    }

    public static void tick() {
        if (ticksLeft > 0) ticksLeft--;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        if (ticksLeft <= 0) return;
        int w = g.guiWidth();
        int h = g.guiHeight();
        float strength = Math.min(1F, ticksLeft / (float) Math.max(1, total) * 1.5F);
        switch (kind) {
            case 1 -> { // red pulse at the edges
                int a = (int) (90 * strength);
                int color = (a << 24) | 0x5A0000;
                g.fill(0, 0, w, h / 8, color);
                g.fill(0, h - h / 8, w, h, color);
                g.fill(0, 0, w / 10, h, color);
                g.fill(w - w / 10, 0, w, h, color);
            }
            case 2 -> { // dark screen with a message
                int a = (int) (160 * strength);
                g.fill(0, 0, w, h, a << 24);
                if (!message.isEmpty() && RANDOM.nextFloat() < 0.85F) {
                    var font = Minecraft.getInstance().font;
                    int x = w / 2 + RANDOM.nextInt(3) - 1;
                    g.drawCenteredString(font, message, x, h / 2 - 4, 0xFFB0B0B0);
                }
            }
            default -> { // static bars
                int bars = 6 + RANDOM.nextInt(10);
                for (int i = 0; i < bars; i++) {
                    int y = RANDOM.nextInt(h);
                    int bh = 1 + RANDOM.nextInt(6);
                    int a = (int) ((40 + RANDOM.nextInt(70)) * strength);
                    int grey = 40 + RANDOM.nextInt(120);
                    g.fill(0, y, w, y + bh, (a << 24) | (grey << 16) | (grey << 8) | grey);
                }
                for (int i = 0; i < 200; i++) {
                    int x = RANDOM.nextInt(w);
                    int y = RANDOM.nextInt(h);
                    int grey = RANDOM.nextInt(255);
                    g.fill(x, y, x + 1, y + 1, ((int) (120 * strength) << 24) | (grey << 16) | (grey << 8) | grey);
                }
            }
        }
    }
}
