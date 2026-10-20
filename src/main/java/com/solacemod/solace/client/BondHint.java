package com.solacemod.solace.client;

import com.solacemod.solace.Solace;
import com.solacemod.solace.config.ClientConfig;
import com.solacemod.solace.network.ModNetwork;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * The little flame in the top left corner: how strong Solace is right now, and how much you owe it. No numbers.
 * The flame fills up from the bottom with Solace's strength (Tether). Black cracks spread through it as your debt grows.
 * While Solace is waiting for something it asked you for, the flame flickers.
 * Players can turn it off in Solace setup (Story page) or with display.bondHint in config/solace-client.toml.
 */
@EventBusSubscriber(modid = Solace.MODID, value = Dist.CLIENT)
public final class BondHint {
    /**
     * The flame, one character per pixel. o = outline, c = edge (pale blue), m = middle, w = white-hot core, . = nothing.
     * Rows 1 to 11 are the inside, which fills up from the bottom.
     */
    private static final String[] SHAPE = {
            "......o......",
            "......oo.....",
            ".....oco.....",
            ".....ocmo....",
            "....ocmmo..o.",
            "....ocmmco.oo",
            "..o.ocmwmcoco",
            "..oo.ocwmmco.",
            ".ocoocmwwmco.",
            ".ocmcmwwwmco.",
            "ocmmmwwwwmmco",
            "ocmmwwwwwwmco",
            "ocmmwwwwwwmco",
            ".ocmmwwwwmco.",
            "..ocmmmmmco..",
            "...ooooooo...",
    };
    private static final int INSIDE_TOP = 1;
    private static final int INSIDE_ROWS = 14;
    /** Where the debt cracks appear, in order (x, y), as one crack that keeps growing. Debt 30+ shows the first 4, 60+ the first 8, 100+ all. */
    private static final int[][] CRACKS = {{5, 13}, {6, 12}, {6, 11}, {7, 10}, {7, 9}, {8, 8}, {4, 12}, {3, 11},
            {8, 7}, {9, 12}, {10, 11}, {5, 9}, {7, 6}};
    private static final int[] CRACKS_PER_BAND = {0, 4, 8, CRACKS.length};

    private static final int OUTLINE = 0xE0101014;
    private static final int OUTLINE_GHOST = 0x8C5A5A64;
    private static final int EDGE = 0xFF9FC3FF;
    private static final int MIDDLE = 0xFFD6E6FF;
    private static final int CORE = 0xFFFFFFFF;
    private static final int EMPTY = 0x50000000;
    private static final int CRACK = 0xFF000000;
    private static final int MARGIN = 6;

    private static boolean show;
    private static int targetPercent;
    private static int debtBand;
    private static boolean request;
    /** What's drawn right now, sliding toward targetPercent so changes look smooth. */
    private static float shownPercent = -1;
    private static long lastFrameNanos;

    private BondHint() {
    }

    public static void update(ModNetwork.BondPayload payload) {
        show = payload.show();
        targetPercent = Math.max(0, Math.min(100, payload.tetherPercent()));
        debtBand = Math.max(0, Math.min(3, payload.debtBand()));
        request = payload.request();
        if (shownPercent < 0) shownPercent = targetPercent;
    }

    /** Leaving a world or server: forget everything, so the next world starts hidden. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        show = false;
        shownPercent = -1;
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!show || mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
        if (!ClientConfig.BOND_HINT.get()) return;
        float time = mc.player.tickCount + delta.getGameTimeDeltaPartialTick(false);
        // Slide toward the real value over about a second, however fast the game is drawing.
        long nanos = System.nanoTime();
        float seconds = lastFrameNanos == 0 ? 0 : Math.min(0.5F, (nanos - lastFrameNanos) / 1.0E9F);
        lastFrameNanos = nanos;
        shownPercent += (targetPercent - shownPercent) * Math.min(1F, seconds * 3F);
        // How many inside rows are lit, counted from the bottom. Even a little strength lights one row.
        int lit = shownPercent < 0.5F ? 0 : Math.max(1, Math.round(shownPercent / 100F * INSIDE_ROWS));
        int firstLit = INSIDE_TOP + INSIDE_ROWS - lit;
        // While Solace waits for something it asked for, the top of the flame keeps dropping out.
        if (request && lit > 1 && ((int) (time / 3) % 5 == 0)) firstLit++;
        // The tip sways a little.
        int sway = Math.round((float) Math.sin(time * 0.07F) * 0.8F);

        for (int y = 0; y < SHAPE.length; y++) {
            String row = SHAPE[y];
            int shift = y <= 2 ? sway : 0;
            boolean rowLit = y >= firstLit;
            for (int x = 0; x < row.length(); x++) {
                char c = row.charAt(x);
                if (c == '.') continue;
                int color;
                if (c == 'o') {
                    // The outline is solid next to the lit part, faint around the empty part (so you can see how big it can get).
                    color = y >= firstLit - 1 ? OUTLINE : OUTLINE_GHOST;
                } else if (!rowLit) {
                    color = EMPTY;
                } else {
                    color = c == 'w' ? CORE : c == 'm' ? MIDDLE : EDGE;
                }
                pixel(g, x + shift, y, color);
            }
        }
        // Debt: black cracks through the flame. At 100+ they slowly pulse.
        int cracks = CRACKS_PER_BAND[debtBand];
        boolean pulse = debtBand == 3 && Math.sin(time * 0.15F) > 0.3;
        for (int i = 0; i < cracks; i++) {
            int x = CRACKS[i][0];
            int y = CRACKS[i][1];
            pixel(g, x + (y <= 2 ? sway : 0), y, pulse ? 0xFF2A0A0A : CRACK);
        }
    }

    private static void pixel(GuiGraphics g, int x, int y, int color) {
        g.fill(MARGIN + x, MARGIN + y, MARGIN + x + 1, MARGIN + y + 1, color);
    }
}
