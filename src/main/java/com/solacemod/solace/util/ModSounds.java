package com.solacemod.solace.util;

import com.solacemod.solace.Solace;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

/**
 * Solace's own sounds. The .ogg files are in assets/solace/sounds/ (made by tools/make_sounds.py),
 * and assets/solace/sounds.json says which files belong to each name, their subtitle and how far they carry.
 * In code, play them like vanilla ones with their full name, for example Sounds.playAt(entity, "solace:negative_surge", 1, 1).
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Solace.MODID);
    /** Every name, for /solace admin sound. */
    public static final List<String> NAMES = new ArrayList<>();

    public static final DeferredHolder<SoundEvent, SoundEvent> HUM = register("hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUM_UNEASY = register("hum_uneasy");
    public static final DeferredHolder<SoundEvent, SoundEvent> LANTERN_DRONE = register("lantern_drone");
    public static final DeferredHolder<SoundEvent, SoundEvent> FLICKER = register("flicker");
    public static final DeferredHolder<SoundEvent, SoundEvent> NEGATIVE_SURGE = register("negative_surge");
    public static final DeferredHolder<SoundEvent, SoundEvent> NEGATIVE_FADE = register("negative_fade");
    public static final DeferredHolder<SoundEvent, SoundEvent> WATCHER_NEAR = register("watcher_near");
    /** Heard up to 48 blocks away: the Watcher is often far off when it's seen. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WATCHER_VANISH = register("watcher_vanish", 48);
    public static final DeferredHolder<SoundEvent, SoundEvent> BOND_SHIFT = register("bond_shift");
    public static final DeferredHolder<SoundEvent, SoundEvent> PAGE_RUSTLE = register("page_rustle");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        NAMES.add(name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Solace.id(name)));
    }

    /** A sound the server sends to everyone within this many blocks (match attenuation_distance in sounds.json). */
    private static DeferredHolder<SoundEvent, SoundEvent> register(String name, float range) {
        NAMES.add(name);
        return SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(Solace.id(name), range));
    }
}
