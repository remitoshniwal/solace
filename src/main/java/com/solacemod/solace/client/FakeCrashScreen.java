package com.solacemod.solace.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/** A harmless fake "crash" screen. It closes by itself after a few seconds, or press Esc. */
public final class FakeCrashScreen extends Screen {
    @Nullable
    private final Screen previous;
    private final String line;
    private int age;

    public FakeCrashScreen(@Nullable Screen previous, String line) {
        super(Component.literal("Crash"));
        this.previous = previous;
        this.line = line;
    }

    @Override
    public void tick() {
        if (++age > 20 * 7) onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int y = height / 3;
        g.drawCenteredString(font, "Minecraft has stopped responding", width / 2, y, 0xFFFFFFFF);
        g.drawCenteredString(font, "The game encountered a problem and must close.", width / 2, y + 20, 0xFFAAAAAA);
        g.drawCenteredString(font, line, width / 2, y + 44, 0xFF777777);
        if (age > 20 * 4) {
            boolean flicker = (age / 3) % 2 == 0;
            g.drawCenteredString(font, flicker ? "just kidding." : "just k§kidd§ring.", width / 2, y + 80, 0xFF8B0000);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(previous);
    }
}
