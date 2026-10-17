package com.solacemod.solace.util;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Looks up vanilla sounds by name (like "ambient.cave") so we don't depend on how Minecraft stores each one. */
public final class Sounds {
    private Sounds() {
    }

    public static SoundEvent get(String id) {
        ResourceLocation rl = ResourceLocation.withDefaultNamespace(id);
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(rl);
        return event != null ? event : SoundEvent.createVariableRangeEvent(rl);
    }

    public static Holder<SoundEvent> holder(String id) {
        return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(get(id));
    }

    /** Plays a sound everyone nearby can hear. */
    public static void play(Level level, Vec3 pos, String id, SoundSource source, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, get(id), source, volume, pitch);
    }

    public static void playAt(Entity entity, String id, float volume, float pitch) {
        play(entity.level(), entity.position(), id, SoundSource.NEUTRAL, volume, pitch);
    }

    /** Plays a sound only this one player can hear, at a position (for horror tricks). */
    public static void playFor(ServerPlayer player, Vec3 pos, SoundEvent event, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(event), SoundSource.AMBIENT,
                pos.x, pos.y, pos.z, volume, pitch, player.getRandom().nextLong()));
    }

    /** Plays a sound only this one player can hear, at a position (for horror tricks). */
    public static void playFor(ServerPlayer player, Vec3 pos, String id, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(holder(id), SoundSource.AMBIENT, pos.x, pos.y, pos.z, volume, pitch,
                player.getRandom().nextLong()));
    }
}
