package com.solacemod.solace.entity;

import com.solacemod.solace.config.SolaceConfig;
import com.solacemod.solace.story.Scheduler;
import com.solacemod.solace.story.SolaceBrain;
import com.solacemod.solace.story.StoryData;
import com.solacemod.solace.util.Sounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;

/**
 * Solace in your fights. It doesn't fight like a pet. It stands back and watches you,
 * cheering you on. Once in a while it flickers next to a mob, kills it in one touch, and flickers back.
 * If you're about to die it almost always steps in, but saving you adds to your debt.
 */
public final class SolaceCombat {
    private final SolaceEntity solace;
    private int fightTicks;
    private long lastCheer;
    private int tapCooldown;

    SolaceCombat(SolaceEntity solace) {
        this.solace = solace;
    }

    /** Is the owner in a fight right now? */
    public boolean ownerFighting() {
        return fightTicks > 0;
    }

    void tick() {
        if (tapCooldown > 0) tapCooldown--;
        ServerPlayer owner = solace.getServerOwner();
        if (owner == null || owner.level() != solace.level() || !SolaceConfig.ALLOW_COMBAT.get()) {
            fightTicks = 0;
            return;
        }
        if (solace.tickCount % 10 != 0) return;
        List<Mob> threats = threats(owner);
        boolean recentlyHit = owner.tickCount - owner.getLastHurtByMobTimestamp() < 100 || owner.tickCount - owner.getLastHurtMobTimestamp() < 100;
        fightTicks = !threats.isEmpty() && (recentlyHit || threats.stream().anyMatch(m -> m.getTarget() == owner)) ? 100 : Math.max(0, fightTicks - 10);
        if (fightTicks <= 0 || threats.isEmpty()) return;

        // Watch the fight: face the closest threat.
        Mob closest = threats.get(0);
        solace.getLookControl().setLookAt(closest, 30F, 30F);

        // Cheer now and then (the AI decides what to say).
        long now = solace.level().getGameTime();
        if (now - lastCheer > 20 * 25) {
            lastCheer = now;
            SolaceBrain.event(owner, "The player is fighting " + threats.size() + " " + closest.getType().getDescription().getString().toLowerCase()
                    + (threats.size() > 1 ? "s" : "") + " (health " + Math.round(owner.getHealth()) + "/20). You're watching. Cheer them on, one short line.", false);
        }

        if (tapCooldown > 0) return;
        float health = owner.getHealth();
        double chance = 0.04;                     // ~ once every 25 seconds of fighting
        if (solace.isGuarding()) chance += 0.08;  // told to protect: more often
        if (health <= 8) chance += 0.15;          // in danger
        boolean saving = health <= 4;             // about to die: almost always steps in
        if (saving) chance = 0.85;
        if (solace.getRandom().nextFloat() < chance) {
            Mob target = threats.stream().filter(m -> m.getTarget() == owner).findFirst().orElse(closest);
            oneTap(target);
            tapCooldown = saving ? 20 : 60;
            if (saving && owner.getServer() != null) {
                var story = StoryData.get(owner.getServer()).get(owner.getUUID());
                story.debt += 8; // it saved you, and it'll remember
                story.addEvent("you saved the player from dying");
            }
        }
    }

    /** Hostile mobs near the owner, closest first. */
    private List<Mob> threats(ServerPlayer owner) {
        return solace.level().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(14),
                        m -> m.isAlive() && m instanceof Enemy && !(m instanceof SolaceEntity) && !(m instanceof WatcherEntity))
                .stream().sorted(Comparator.comparingDouble(owner::distanceToSqr)).toList();
    }

    /** Flicker to the target, kill it with one touch, flicker back. Bosses only take a heavy hit. */
    public void oneTap(LivingEntity target) {
        if (!(solace.level() instanceof ServerLevel level) || !target.isAlive()) return;
        // For the kill, the mask slips: he flares into his negative side.
        if (solace.getCorruption() >= 2) solace.goNegative(30);
        Vec3 home = solace.position();
        float homeYaw = solace.getYRot();
        Vec3 dir = target.position().subtract(home);
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(1, 0, 0);
        Vec3 spot = target.position().subtract(flat.normalize().scale(1.1));
        float yaw = (float) (Math.atan2(flat.z, flat.x) * (180 / Math.PI)) - 90F;

        level.sendParticles(ParticleTypes.ASH, home.x, home.y + 1, home.z, 20, 0.3, 0.8, 0.3, 0.01);
        solace.getNavigation().stop();
        solace.moveTo(spot.x, target.getY(), spot.z, yaw, 0);
        solace.swing(InteractionHand.MAIN_HAND);
        float damage = target.getMaxHealth() > 100 ? 20F : 1000F;
        target.hurt(solace.damageSources().mobAttack(solace), damage);
        level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 1, target.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        Sounds.playAt(solace, "entity.player.attack.crit", 1.0F, 0.6F);
        Sounds.playAt(solace, "block.amethyst_block.break", 0.6F, 0.5F);
        // ...and it's back where it was, as if it never moved.
        Scheduler.later(6, () -> {
            if (solace.isRemoved()) return;
            level.sendParticles(ParticleTypes.ASH, solace.getX(), solace.getY() + 1, solace.getZ(), 15, 0.3, 0.8, 0.3, 0.01);
            solace.moveTo(home.x, home.y, home.z, homeYaw, 0);
        });
    }

    @Nullable
    public static Mob nearestThreat(SolaceEntity solace, LivingEntity around, double radius) {
        return around.level().getEntitiesOfClass(Mob.class, around.getBoundingBox().inflate(radius), m -> m.isAlive() && m instanceof Enemy)
                .stream().min(Comparator.comparingDouble(around::distanceToSqr)).orElse(null);
    }
}
