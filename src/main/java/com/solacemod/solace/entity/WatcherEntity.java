package com.solacemod.solace.entity;

import com.solacemod.solace.story.Fragments;
import com.solacemod.solace.util.Sounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * The pale figure in the distance: Maren's echo. It never moves or attacks. It just watches,
 * and disappears when you look straight at it or get close. Sometimes it leaves something behind.
 * It is never saved to disk, so it can't pile up in your world.
 */
public class WatcherEntity extends PathfinderMob {
    private UUID watchedPlayer;
    private int maxLife = 20 * 30;
    private int vanishDistance = 18;
    private int lookedAtTicks;
    private int fragmentToLeave = -1;
    private Consumer<WatcherEntity> onVanish;

    public WatcherEntity(EntityType<? extends WatcherEntity> type, Level level) {
        super(type, level);
        setInvulnerable(true);
        setSilent(true);
        setNoAi(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    public void configure(ServerPlayer player, int lifeTicks, int vanishDistance, int fragmentIndex, Consumer<WatcherEntity> onVanish) {
        this.watchedPlayer = player.getUUID();
        this.maxLife = lifeTicks;
        this.vanishDistance = vanishDistance;
        this.fragmentToLeave = fragmentIndex;
        this.onVanish = onVanish;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        Player player = watchedPlayer == null ? null : level().getPlayerByUUID(watchedPlayer);
        if (player == null || player.level() != level()) {
            discard();
            return;
        }
        getLookControl().setLookAt(player, 180F, 180F);
        // Turn the whole body toward the player, slowly. Creepier than snapping.
        Vec3 d = player.position().subtract(position());
        float targetYaw = (float) (Math.atan2(d.z, d.x) * (180F / Math.PI)) - 90F;
        setYRot(targetYaw);
        setYBodyRot(targetYaw);
        setYHeadRot(targetYaw);

        boolean seen = isLookedAtBy(player);
        lookedAtTicks = seen ? lookedAtTicks + 1 : Math.max(0, lookedAtTicks - 1);
        if (tickCount > maxLife || distanceTo(player) < vanishDistance || (tickCount > 40 && lookedAtTicks > 25)) {
            vanish();
        }
    }

    private boolean isLookedAtBy(Player player) {
        Vec3 view = player.getViewVector(1.0F).normalize();
        Vec3 toMe = new Vec3(getX() - player.getX(), getEyeY() - player.getEyeY(), getZ() - player.getZ()).normalize();
        return view.dot(toMe) > 0.985 && player.hasLineOfSight(this);
    }

    public void vanish() {
        if (isRemoved()) return;
        ServerLevel level = (ServerLevel) level();
        level.sendParticles(ParticleTypes.ASH, getX(), getY() + 1, getZ(), 40, 0.3, 0.8, 0.3, 0.02);
        Sounds.play(level, position(), "particle.soul_escape", SoundSource.AMBIENT, 1.0F, 0.5F);
        if (fragmentToLeave >= 0) Fragments.dropAt(level, position(), fragmentToLeave);
        if (onVanish != null) onVanish.accept(this);
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && source.getEntity() instanceof Player) vanish();
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }
}
