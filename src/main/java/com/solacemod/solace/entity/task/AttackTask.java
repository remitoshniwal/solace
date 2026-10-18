package com.solacemod.solace.entity.task;

import com.solacemod.solace.entity.SolaceEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.function.Predicate;

/** Hunts the nearest mob of a type (or any hostile mob), one-tapping them one by one until enough are gone. */
public final class AttackTask implements SolaceTask {
    private final Predicate<LivingEntity> filter;
    private final String name;
    private final int wanted;
    private LivingEntity current;
    private int defeated;
    private int idle;
    private int wait = 20;

    public AttackTask(@Nullable EntityType<?> type, String name, int count) {
        this.filter = type == null ? e -> e instanceof Enemy : e -> e.getType() == type;
        this.name = name;
        this.wanted = Math.max(1, count);
    }

    @Override
    public boolean tick(SolaceEntity solace) {
        if (current != null && !current.isAlive()) {
            defeated++;
            current = null;
            if (defeated >= wanted) {
                TaskUtil.report(solace, "Got it. " + defeated + " " + name + " down.");
                return true;
            }
        }
        if (current == null) {
            current = solace.level().getEntitiesOfClass(LivingEntity.class, solace.getBoundingBox().inflate(20, 8, 20),
                            e -> e.isAlive() && !(e instanceof Player) && !(e instanceof SolaceEntity) && filter.test(e))
                    .stream().min(Comparator.comparingDouble(solace::distanceToSqr)).orElse(null);
            if (current == null) {
                if (++idle > 40) {
                    TaskUtil.report(solace, defeated == 0 ? "I don't see any " + name + " around." : "No more " + name + " nearby.");
                    return true;
                }
                return false;
            }
            idle = 0;
        }
        // Solace doesn't brawl. It flickers to each one and ends it in a single touch.
        if (++wait >= 25) {
            wait = 0;
            solace.combat.oneTap(current);
        }
        return false;
    }

    @Override
    public boolean isCombat() {
        return true;
    }

    @Override
    public boolean pausesForCombat() {
        return false;
    }

    @Override
    public String describe() {
        return "hunting " + name + " (" + defeated + "/" + wanted + ")";
    }
}
