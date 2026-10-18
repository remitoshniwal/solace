package com.solacemod.solace.story;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * How "expensive" an item is for Solace to conjure (in Tether). There's no hard cap on what it can make:
 * netherite is possible, it just costs a huge amount of Tether, which only exists late in the story with a strong bond.
 */
public final class ItemValue {
    /** Exact values for special items (per single item). */
    private static final Map<String, Double> EXACT = Map.ofEntries(
            Map.entry("netherite_ingot", 80.0), Map.entry("netherite_scrap", 20.0), Map.entry("ancient_debris", 22.0),
            Map.entry("netherite_block", 720.0), Map.entry("netherite_upgrade_smithing_template", 60.0),
            Map.entry("diamond", 25.0), Map.entry("diamond_block", 225.0), Map.entry("diamond_ore", 25.0), Map.entry("deepslate_diamond_ore", 25.0),
            Map.entry("emerald", 8.0), Map.entry("emerald_block", 72.0), Map.entry("gold_ingot", 5.0), Map.entry("gold_block", 45.0),
            Map.entry("iron_ingot", 6.0), Map.entry("iron_block", 54.0), Map.entry("copper_ingot", 1.5), Map.entry("raw_iron", 5.0), Map.entry("raw_gold", 4.0),
            Map.entry("redstone", 1.5), Map.entry("lapis_lazuli", 1.5), Map.entry("coal", 1.0), Map.entry("quartz", 1.5), Map.entry("amethyst_shard", 2.0),
            Map.entry("elytra", 120.0), Map.entry("totem_of_undying", 100.0), Map.entry("enchanted_golden_apple", 140.0), Map.entry("golden_apple", 20.0),
            Map.entry("nether_star", 150.0), Map.entry("beacon", 180.0), Map.entry("trident", 100.0), Map.entry("heart_of_the_sea", 60.0),
            Map.entry("mace", 120.0), Map.entry("heavy_core", 90.0), Map.entry("dragon_egg", 300.0), Map.entry("dragon_head", 60.0),
            Map.entry("dragon_breath", 15.0), Map.entry("end_crystal", 40.0), Map.entry("conduit", 80.0), Map.entry("wither_skeleton_skull", 40.0),
            Map.entry("shulker_shell", 20.0), Map.entry("shulker_box", 45.0), Map.entry("ender_pearl", 6.0), Map.entry("ender_eye", 10.0),
            Map.entry("blaze_rod", 6.0), Map.entry("ghast_tear", 10.0), Map.entry("experience_bottle", 5.0), Map.entry("enchanted_book", 30.0),
            Map.entry("name_tag", 10.0), Map.entry("saddle", 10.0), Map.entry("trial_key", 15.0), Map.entry("ominous_trial_key", 40.0),
            Map.entry("echo_shard", 20.0), Map.entry("recovery_compass", 60.0), Map.entry("sniffer_egg", 40.0), Map.entry("music_disc_pigstep", 40.0),
            Map.entry("bread", 1.0), Map.entry("cooked_beef", 1.5), Map.entry("cooked_porkchop", 1.5), Map.entry("golden_carrot", 4.0),
            Map.entry("torch", 0.3), Map.entry("stick", 0.1), Map.entry("arrow", 0.5), Map.entry("book", 2.0), Map.entry("bookshelf", 7.0)
    );

    /** Material -> value of one unit of it, used for tools and armor. */
    private static final Map<String, Double> MATERIALS = Map.of(
            "netherite_", 80.0, "diamond_", 22.0, "golden_", 5.0, "iron_", 6.0, "chainmail_", 4.0, "copper_", 1.5,
            "stone_", 0.3, "wooden_", 0.3, "leather_", 1.0, "turtle_", 12.0);

    /** How many material units each tool/armor piece takes. */
    private static final Map<String, Integer> PIECES = Map.ofEntries(
            Map.entry("_helmet", 5), Map.entry("_chestplate", 8), Map.entry("_leggings", 7), Map.entry("_boots", 4),
            Map.entry("_sword", 2), Map.entry("_pickaxe", 3), Map.entry("_axe", 3), Map.entry("_shovel", 1), Map.entry("_hoe", 2),
            Map.entry("_horse_armor", 6), Map.entry("_shell", 5));

    private ItemValue() {
    }

    /** Tether cost of ONE of this item. */
    public static double of(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        Double exact = EXACT.get(id);
        if (exact != null) return exact;
        if (id.endsWith("_shulker_box")) return 45.0;
        if (id.startsWith("music_disc_")) return 15.0;
        for (Map.Entry<String, Double> mat : MATERIALS.entrySet()) {
            if (!id.startsWith(mat.getKey())) continue;
            for (Map.Entry<String, Integer> piece : PIECES.entrySet()) {
                if (id.endsWith(piece.getKey())) {
                    double v = mat.getValue() * piece.getValue();
                    // Netherite gear is diamond gear + an ingot.
                    if (mat.getKey().equals("netherite_")) v = 22.0 * piece.getValue() + 80.0;
                    return Math.max(1.0, v);
                }
            }
        }
        // Everything else: by rarity. Plain blocks and basic items are cheap.
        return switch (new ItemStack(item).getRarity()) {
            case EPIC -> 80.0;
            case RARE -> 30.0;
            case UNCOMMON -> 10.0;
            default -> 1.0;
        };
    }
}
