package com.solacemod.solace.entity.task;

import com.solacemod.solace.entity.SolaceEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;

/** Picks up dropped items lying around. */
public final class CollectTask implements SolaceTask {
    private static final int TIMEOUT = 20 * 60;
    private int age;
    private int collected;
    private ItemEntity target;

    @Override
    public boolean tick(SolaceEntity solace) {
        if (++age > TIMEOUT) return finish(solace);
        if (target == null || !target.isAlive()) {
            List<ItemEntity> items = solace.level().getEntitiesOfClass(ItemEntity.class, solace.getBoundingBox().inflate(16, 6, 16),
                    e -> e.isAlive() && !e.hasPickUpDelay());
            target = items.stream().min(Comparator.comparingDouble(solace::distanceToSqr)).orElse(null);
            if (target == null) return finish(solace);
            solace.getNavigation().moveTo(target, 1.2);
        }
        if (solace.distanceToSqr(target) < 2.0 * 2.0) {
            ItemStack stack = target.getItem().copy();
            ItemStack left = solace.inventory.addItem(stack);
            int taken = stack.getCount() - left.getCount();
            if (taken > 0) {
                solace.take(target, taken);
                collected += taken;
            }
            if (left.isEmpty()) target.discard();
            else {
                target.setItem(left);
                TaskUtil.report(solace, "My pockets are full!");
                return true;
            }
            target = null;
        } else if (solace.getNavigation().isDone()) {
            solace.getNavigation().moveTo(target, 1.2);
        }
        return false;
    }

    private boolean finish(SolaceEntity solace) {
        TaskUtil.report(solace, collected == 0 ? "There's nothing to pick up." : "Picked up " + collected + " items.");
        return true;
    }

    @Override
    public String describe() {
        return "picking up items";
    }
}
