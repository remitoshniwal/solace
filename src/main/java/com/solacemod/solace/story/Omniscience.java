package com.solacemod.solace.story;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Everything Solace "sees". It's part of the world, so it knows where you are, what you carry, what's hidden
 * in the stone around you, how many times you've died... and what time it is where you really are.
 */
final class Omniscience {
    /** Valuable blocks Solace senses through the ground: tag/id -> name. */
    private static final Map<String, String> SENSED = new LinkedHashMap<>();

    static {
        SENSED.put("ancient_debris", "ancient debris");
        SENSED.put("diamond_ore", "diamonds");
        SENSED.put("emerald_ore", "emeralds");
        SENSED.put("gold_ore", "gold");
        SENSED.put("iron_ore", "iron");
        SENSED.put("redstone_ore", "redstone");
        SENSED.put("lapis_ore", "lapis");
        SENSED.put("spawner", "a monster spawner");
        SENSED.put("chest", "a chest");
    }

    private Omniscience() {
    }

    static String describe(ServerPlayer p) {
        StringBuilder sb = new StringBuilder();
        BlockPos pos = p.blockPosition();
        sb.append(". Player is at x ").append(pos.getX()).append(", y ").append(pos.getY()).append(", z ").append(pos.getZ())
                .append(", facing ").append(p.getDirection().getName());
        HitResult hit = p.pick(24.0, 1.0F, false);
        if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
            BlockState st = p.level().getBlockState(bh.getBlockPos());
            sb.append(", looking at ").append(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath())
                    .append(" ").append(Math.round(Math.sqrt(bh.getBlockPos().distSqr(pos)))).append(" blocks away");
        }

        // Inventory and armor
        Map<String, Integer> inv = new LinkedHashMap<>();
        for (ItemStack s : p.getInventory().items) {
            if (!s.isEmpty()) inv.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath(), s.getCount(), Integer::sum);
        }
        if (!inv.isEmpty()) {
            sb.append(". Player's inventory: ");
            int n = 0;
            for (Map.Entry<String, Integer> e : inv.entrySet()) {
                if (n++ >= 14) {
                    sb.append("and more, ");
                    break;
                }
                sb.append(e.getValue()).append(' ').append(e.getKey()).append(", ");
            }
            sb.setLength(sb.length() - 2);
        } else {
            sb.append(". Player's inventory is empty");
        }
        StringBuilder armor = new StringBuilder();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack a = p.getItemBySlot(slot);
            if (!a.isEmpty()) armor.append(BuiltInRegistries.ITEM.getKey(a.getItem()).getPath()).append(", ");
        }
        sb.append(armor.isEmpty() ? ". No armor" : ". Wearing " + armor.substring(0, armor.length() - 2));

        // What's hidden nearby
        String hidden = sense(p);
        if (!hidden.isEmpty()) sb.append(". Hidden nearby (only you can sense it): ").append(hidden);

        // Other players
        StringBuilder others = new StringBuilder();
        for (ServerPlayer o : p.serverLevel().players()) {
            if (o == p) continue;
            others.append(o.getGameProfile().getName()).append(" (").append(Math.round(o.distanceTo(p))).append(" blocks), ");
        }
        if (!others.isEmpty()) sb.append(". Other players in this dimension: ").append(others, 0, others.length() - 2);

        // Lifetime stats
        var stats = p.getStats();
        int deaths = stats.getValue(Stats.CUSTOM.get(Stats.DEATHS));
        int kills = stats.getValue(Stats.CUSTOM.get(Stats.MOB_KILLS));
        long hours = stats.getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) / 72000L;
        long walkedKm = (stats.getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM)) + stats.getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM))) / 100_000L;
        sb.append(". In this world the player has died ").append(deaths).append(" times, killed ").append(kills)
                .append(" mobs, walked ").append(walkedKm).append(" km, and played ").append(hours).append(" hours");

        // The real world
        LocalDateTime now = LocalDateTime.now();
        sb.append(". In the real world it is ").append(now.format(DateTimeFormatter.ofPattern("h:mm a 'on' EEEE", Locale.ENGLISH)))
                .append(" (you just know this; it unsettles players if you mention it, especially late at night)");
        return sb.toString();
    }

    /** The nearest valuable thing of each kind within 16 blocks. */
    private static String sense(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        BlockPos origin = p.blockPosition();
        Map<String, BlockPos> nearest = new LinkedHashMap<>();
        Map<String, Double> best = new LinkedHashMap<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = -12; dy <= 12; dy++) {
            for (int dx = -16; dx <= 16; dx++) {
                for (int dz = -16; dz <= 16; dz++) {
                    m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(m).getBlock()).getPath();
                    String key = id.replace("deepslate_", "").replace("nether_gold_ore", "gold_ore");
                    if (!SENSED.containsKey(key)) continue;
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d < best.getOrDefault(key, Double.MAX_VALUE)) {
                        best.put(key, d);
                        nearest.put(key, m.immutable());
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : SENSED.entrySet()) {
            BlockPos at = nearest.get(e.getKey());
            if (at != null) sb.append(e.getValue()).append(" ").append(Powers.describe(p, at, true)).append("; ");
        }
        return sb.isEmpty() ? "" : sb.substring(0, sb.length() - 2);
    }
}
