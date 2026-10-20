package com.solacemod.solace.client;

import com.solacemod.solace.Solace;
import com.solacemod.solace.config.ClientConfig;
import com.solacemod.solace.entity.SolaceEntity;
import com.solacemod.solace.util.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Solace's sounds that are worked out on the player's own computer:
 * - the low hum near Solace and the Lantern's drone (loops that follow it and fade in and out),
 * - a soft flicker sound whenever the negative form flickers (the flicker itself is worked out here too, in SolaceRenderer),
 * - a tiny flutter when the bond flame changes,
 * - and the "Solace sounds" volume slider, which turns down every solace: sound, even ones the server sends.
 */
@EventBusSubscriber(modid = Solace.MODID, value = Dist.CLIENT)
public final class SolaceSounds {
    /** The hum is heard within this many blocks. */
    private static final double HUM_RANGE = 12;
    /** The drone carries further: the Lantern is big. */
    private static final double DRONE_RANGE = 24;
    /** At most one flicker sound every this many ticks (1.5 seconds), even when it flickers all the time. */
    private static final int FLICKER_GAP = 30;
    /** At most one bond flame sound every this many ticks. */
    private static final int BOND_GAP = 60;

    private static final RandomSource RANDOM = RandomSource.create();
    /** The hum or drone playing for each Solace (by entity id). */
    private static final Map<Integer, Loop> HUMS = new HashMap<>();
    private static final Map<Integer, Loop> DRONES = new HashMap<>();
    /** Was each Solace flickering last tick, and when did it last make a flicker sound. */
    private static final Map<Integer, Boolean> WAS_FLICKERING = new HashMap<>();
    private static final Map<Integer, Integer> LAST_FLICKER = new HashMap<>();
    private static int ticks;
    private static int lastBondSound = -BOND_GAP;

    private SolaceSounds() {
    }

    /** The volume slider, from 0 to 1. */
    static float volume() {
        return ClientConfig.SOLACE_SOUNDS.get() / 100F;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        if (mc.level == null || mc.player == null) {
            HUMS.clear();
            DRONES.clear();
            WAS_FLICKERING.clear();
            LAST_FLICKER.clear();
            return;
        }
        if (mc.isPaused()) return;
        // Forget loops that ended (or that the game stopped, like when the sound settings reload), so they can start again.
        HUMS.values().removeIf(loop -> !mc.getSoundManager().isActive(loop));
        DRONES.values().removeIf(loop -> !mc.getSoundManager().isActive(loop));
        for (SolaceEntity solace : mc.level.getEntitiesOfClass(SolaceEntity.class, mc.player.getBoundingBox().inflate(DRONE_RANGE + 8))) {
            int id = solace.getId();
            if (solace.isLanternForm()) {
                if (!DRONES.containsKey(id)) start(DRONES, id, new Loop(solace, ModSounds.LANTERN_DRONE.get(), false));
            } else if (ClientConfig.HUM_NEAR_SOLACE.get() && mc.player.distanceTo(solace) < HUM_RANGE + 4) {
                SoundEvent want = humFor(solace);
                Loop hum = HUMS.get(id);
                if (hum == null || hum.event != want) {
                    if (hum != null) hum.fadeOut(); // the old hum fades while the new one fades in
                    start(HUMS, id, new Loop(solace, want, true));
                }
            }
            flicker(mc, solace);
        }
    }

    private static void start(Map<Integer, Loop> map, int id, Loop loop) {
        map.put(id, loop);
        Minecraft.getInstance().getSoundManager().play(loop);
    }

    /** The plain hum, or the out-of-tune one once it has started to change (the uneasy form). */
    static SoundEvent humFor(SolaceEntity solace) {
        return solace.getForm() == SolaceEntity.FORM_UNEASY ? ModSounds.HUM_UNEASY.get() : ModSounds.HUM.get();
    }

    /** One soft flicker sound when the negative form starts flickering (not when it is fully negative: that has its own sound). */
    private static void flicker(Minecraft mc, SolaceEntity solace) {
        int id = solace.getId();
        boolean now = !solace.isNegative() && !solace.isLanternForm() && SolaceRenderer.negativeMask(solace, solace.tickCount) != 0;
        boolean before = WAS_FLICKERING.getOrDefault(id, false);
        WAS_FLICKERING.put(id, now);
        if (!now || before || ticks - LAST_FLICKER.getOrDefault(id, -FLICKER_GAP) < FLICKER_GAP) return;
        LAST_FLICKER.put(id, ticks);
        mc.getSoundManager().play(new SimpleSoundInstance(ModSounds.FLICKER.get(), SoundSource.HOSTILE, 0.7F,
                0.9F + RANDOM.nextFloat() * 0.2F, RANDOM, solace.getX(), solace.getY() + 1.2, solace.getZ()));
    }

    /** The bond flame changed (stronger, weaker, more debt, or a request). Called by BondHint. */
    static void bondShift() {
        if (ticks - lastBondSound < BOND_GAP || !ClientConfig.BOND_HINT.get()) return;
        lastBondSound = ticks;
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(ModSounds.BOND_SHIFT.get().getLocation(),
                SoundSource.PLAYERS, 0.5F, 1.0F, RANDOM, false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }

    /** The volume slider: every solace: sound is made quieter just before it plays. Our loops read the slider themselves. */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || sound instanceof TickableSoundInstance || !sound.getLocation().getNamespace().equals(Solace.MODID)) return;
        float v = volume();
        if (v <= 0) event.setSound(null);
        else if (v < 1) event.setSound(new Quieter(sound, v));
    }

    /**
     * A hum or drone that follows one Solace. It fades in when it starts and fades out when it's no longer wanted
     * (Solace changed form, walked away, was removed, or the hum was turned off), then stops itself.
     */
    private static final class Loop extends AbstractTickableSoundInstance {
        private final SolaceEntity solace;
        private final SoundEvent event;
        private final boolean hum;
        private boolean fading;
        /** 0 = silent, 1 = full: slides up and down so the sound never starts or stops suddenly (about 2 seconds). */
        private float fade;

        Loop(SolaceEntity solace, SoundEvent event, boolean hum) {
            super(event, SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
            this.solace = solace;
            this.event = event;
            this.hum = hum;
            this.looping = true;
            this.delay = 0;
            this.volume = 0;
            this.attenuation = Attenuation.LINEAR;
            follow();
        }

        void fadeOut() {
            fading = true;
        }

        @Override
        public boolean canStartSilent() {
            return true; // it starts at volume 0 and fades in
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (!fading && (solace.isRemoved() || mc.player == null || solace.level() != mc.player.level() || !wanted())) fading = true;
            fade += fading ? -0.025F : 0.025F;
            if (fade <= 0 && fading) {
                stop();
                return;
            }
            fade = Math.min(1, Math.max(0, fade));
            follow();
            // The hum gets a little lower and louder each chapter. The Vast form and the Hollow drone deeper.
            int chapter = Math.max(0, Math.min(8, solace.getCorruption()));
            float loud = hum ? 0.35F + chapter * 0.05F : 0.8F;
            pitch = hum ? 1.0F - chapter * 0.02F
                    : solace.getForm() == SolaceEntity.FORM_HOLLOW ? 0.7F : solace.getForm() == SolaceEntity.FORM_VAST ? 0.8F : 1.0F;
            volume = loud * fade * volume();
        }

        private boolean wanted() {
            if (!hum) return solace.isLanternForm();
            Minecraft mc = Minecraft.getInstance();
            return !solace.isLanternForm() && ClientConfig.HUM_NEAR_SOLACE.get() && humFor(solace) == event
                    && mc.player != null && mc.player.distanceTo(solace) < HUM_RANGE + 4;
        }

        private void follow() {
            x = solace.getX();
            y = solace.getY() + (solace.isLanternForm() ? 2.0 : 1.2);
            z = solace.getZ();
        }
    }

    /**
     * Wraps a sound to make it quieter, for the volume slider. Everything else is passed straight through.
     * (Minecraft treats a volume above 1 as "carries further", not louder, so that part is dropped first.)
     */
    private record Quieter(SoundInstance inner, float factor) implements SoundInstance {
        @Override
        public ResourceLocation getLocation() {
            return inner.getLocation();
        }

        @Nullable
        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            return inner.resolve(manager);
        }

        @Override
        public Sound getSound() {
            return inner.getSound();
        }

        @Override
        public SoundSource getSource() {
            return inner.getSource();
        }

        @Override
        public boolean isLooping() {
            return inner.isLooping();
        }

        @Override
        public boolean isRelative() {
            return inner.isRelative();
        }

        @Override
        public int getDelay() {
            return inner.getDelay();
        }

        @Override
        public float getVolume() {
            return Math.min(1F, inner.getVolume()) * factor;
        }

        @Override
        public float getPitch() {
            return inner.getPitch();
        }

        @Override
        public double getX() {
            return inner.getX();
        }

        @Override
        public double getY() {
            return inner.getY();
        }

        @Override
        public double getZ() {
            return inner.getZ();
        }

        @Override
        public Attenuation getAttenuation() {
            return inner.getAttenuation();
        }

        @Override
        public boolean canStartSilent() {
            return inner.canStartSilent();
        }

        @Override
        public boolean canPlaySound() {
            return inner.canPlaySound();
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
            return inner.getStream(buffers, sound, looping);
        }
    }
}
