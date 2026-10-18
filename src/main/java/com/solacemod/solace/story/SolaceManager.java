package com.solacemod.solace.story;

import com.solacemod.solace.entity.ModEntities;
import com.solacemod.solace.entity.SolaceEntity;
import com.solacemod.solace.entity.SolaceGoals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Makes sure each player's Solace exists, follows them between dimensions, survives chunk unloading,
 * and "comes back" after it's killed.
 */
public final class SolaceManager {
    private SolaceManager() {
    }

    @Nullable
    public static SolaceEntity find(MinecraftServer server, PlayerStory story) {
        if (story.solaceId == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(story.solaceId);
            if (e instanceof SolaceEntity s && s.isAlive()) return s;
        }
        return null;
    }

    /** Is this entity the one, real Solace for its owner? (Stale copies remove themselves.) */
    public static boolean isCurrent(SolaceEntity entity) {
        MinecraftServer server = entity.getServer();
        UUID owner = entity.getOwnerUUID();
        if (server == null || owner == null) return false;
        PlayerStory story = StoryData.get(server).getIfPresent(owner);
        return story != null && story.solaceShouldExist() && entity.getUUID().equals(story.solaceId) && story.respawnAtTick < 0;
    }

    /** Creates Solace near the player (behind them if {@code behind}), restoring its pockets from the backup. */
    public static SolaceEntity spawnNear(ServerPlayer player, PlayerStory story, boolean behind) {
        ServerLevel level = player.serverLevel();
        SolaceEntity solace = ModEntities.SOLACE.get().create(level);
        if (solace == null) return null;
        solace.setOwner(player.getUUID());
        solace.setCustomName(Component.literal("Solace"));
        solace.inventory.fromTag(story.inventoryBackup, level.registryAccess());
        try {
            solace.setMode(SolaceEntity.Mode.valueOf(story.mode));
        } catch (IllegalArgumentException e) {
            solace.setMode(SolaceEntity.Mode.FOLLOW);
        }
        if (solace.getMode() == SolaceEntity.Mode.STAY) solace.setMode(SolaceEntity.Mode.FOLLOW);
        solace.setGuard(story.guard);
        updateForm(solace, story);
        Vec3 pos = spawnSpot(level, player, behind ? 6 : 3);
        solace.moveTo(pos.x, pos.y, pos.z, player.getYRot() + 180F, 0F);
        story.solaceId = solace.getUUID();
        story.respawnAtTick = -1;
        level.addFreshEntity(solace);
        return solace;
    }

    private static Vec3 spawnSpot(ServerLevel level, ServerPlayer player, int distance) {
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4) look = new Vec3(0, 0, 1);
        look = look.normalize();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = Math.PI + (attempt - 4) * 0.3;
            Vec3 dir = new Vec3(look.x * Math.cos(angle) - look.z * Math.sin(angle), 0, look.x * Math.sin(angle) + look.z * Math.cos(angle));
            Vec3 p = player.position().add(dir.scale(distance));
            BlockPos bp = BlockPos.containing(p);
            for (int dy = 2; dy >= -3; dy--) {
                BlockPos test = bp.above(dy);
                if (!level.getBlockState(test.below()).getCollisionShape(level, test.below()).isEmpty()
                        && level.getBlockState(test).getCollisionShape(level, test).isEmpty()
                        && level.getBlockState(test.above()).getCollisionShape(level, test.above()).isEmpty()) {
                    return new Vec3(test.getX() + 0.5, test.getY(), test.getZ() + 0.5);
                }
            }
        }
        if (level.canSeeSky(player.blockPosition())) {
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(2, 0, 2));
            return Vec3.atBottomCenterOf(top);
        }
        return player.position();
    }

    /** How many lumps move under its skin in each chapter (index = chapter). */
    private static final int[] LUMPS = {0, 1, 2, 4, 6, 9, 12, 16, 20};

    static void updateForm(SolaceEntity solace, PlayerStory story) {
        if (solace.isHollow()) return;
        boolean storyOn = com.solacemod.solace.config.SolaceConfig.STORY_ENABLED.get();
        int lumps = !storyOn ? 0 : story.ending == Ending.NONE ? LUMPS[Math.min(8, story.chapter())] : story.ending == Ending.FOREVER ? 20 : 0;
        solace.setLumps(lumps);
        if (solace.isShowingLantern()) return;
        boolean uneasy = storyOn && story.ending == Ending.NONE && story.act >= 3;
        solace.setForm(uneasy || story.ending == Ending.FOREVER ? SolaceEntity.FORM_UNEASY : SolaceEntity.FORM_NORMAL);
    }

    /** Runs every 2 seconds per online player. */
    public static void tick(ServerPlayer player, PlayerStory story) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        SolaceEntity solace = find(server, story);
        if (!story.solaceShouldExist()) {
            if (solace != null && !solace.isHollow()) remove(server, story);
            return;
        }
        story.respawnAtTick = -1; // (older saves) Solace can't die anymore, so there's nothing to wait for
        if (solace == null) {
            // Not loaded anywhere. In stay mode, wait for the player to come back; otherwise catch up.
            if (!"STAY".equals(story.mode) && !player.isSpectator()) spawnNear(player, story, true);
            return;
        }
        backup(solace, story);
        updateForm(solace, story);
        if (solace.getMode() == SolaceEntity.Mode.STAY || solace.isHollow()) return;
        if (solace.level() != player.level()) {
            bringTo(solace, player, story);
        } else if (solace.getMode() == SolaceEntity.Mode.FOLLOW && solace.distanceTo(player) > 48) {
            SolaceGoals.teleportNear(solace, player);
        }
    }

    public static void backup(SolaceEntity solace, PlayerStory story) {
        story.inventoryBackup = solace.inventory.createTag(solace.registryAccess());
        story.mode = solace.getMode().name();
        story.guard = solace.isGuarding();
        story.lastPos = solace.blockPosition();
        story.lastDim = solace.level().dimension().location().toString();
    }

    /** Moves Solace to the player, even across dimensions. */
    public static SolaceEntity bringTo(SolaceEntity solace, ServerPlayer player, PlayerStory story) {
        if (solace.level() == player.level()) {
            if (!SolaceGoals.teleportNear(solace, player)) solace.teleportTo(player.getX(), player.getY(), player.getZ());
            return solace;
        }
        // Different dimension: copy Solace into the player's dimension.
        CompoundTag data = new CompoundTag();
        solace.saveWithoutId(data);
        solace.discard();
        ServerLevel level = player.serverLevel();
        SolaceEntity copy = ModEntities.SOLACE.get().create(level);
        if (copy == null) return null;
        copy.load(data);
        Vec3 pos = spawnSpot(level, player, 3);
        copy.moveTo(pos.x, pos.y, pos.z, player.getYRot() + 180F, 0F);
        level.addFreshEntity(copy);
        story.solaceId = copy.getUUID();
        return copy;
    }

    /** Summons Solace to the player from anywhere (used by /solace come). */
    public static SolaceEntity summon(ServerPlayer player, PlayerStory story) {
        MinecraftServer server = player.getServer();
        SolaceEntity solace = server == null ? null : find(server, story);
        if (solace == null) return spawnNear(player, story, false);
        solace.setMode(SolaceEntity.Mode.FOLLOW);
        return bringTo(solace, player, story);
    }

    /** Removes Solace for good (used by endings and /solace reset). */
    public static void remove(MinecraftServer server, PlayerStory story) {
        SolaceEntity solace = find(server, story);
        if (solace != null) {
            ServerLevel level = (ServerLevel) solace.level();
            level.sendParticles(ParticleTypes.ASH, solace.getX(), solace.getY() + 1, solace.getZ(), 80, 0.4, 0.9, 0.4, 0.02);
            solace.discard();
        }
    }
}
