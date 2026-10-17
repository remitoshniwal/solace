package com.solacemod.solace.entity.task;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Map;

/**
 * Understands what block the player (or the AI) means: "iron", "iron_ore", "logs", "#minecraft:logs", "oak log"...
 * Ores include their deepslate versions automatically.
 */
public final class BlockMatcher {
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("wood", "#minecraft:logs"), Map.entry("log", "#minecraft:logs"), Map.entry("logs", "#minecraft:logs"),
            Map.entry("tree", "#minecraft:logs"), Map.entry("trees", "#minecraft:logs"),
            Map.entry("stone", "#minecraft:base_stone_overworld"), Map.entry("rock", "#minecraft:base_stone_overworld"),
            Map.entry("cobblestone", "#minecraft:base_stone_overworld"), Map.entry("cobble", "#minecraft:base_stone_overworld"),
            Map.entry("dirt", "#minecraft:dirt"), Map.entry("sand", "#minecraft:sand"), Map.entry("leaves", "#minecraft:leaves"),
            Map.entry("coal", "#minecraft:coal_ores"), Map.entry("iron", "#minecraft:iron_ores"), Map.entry("gold", "#minecraft:gold_ores"),
            Map.entry("diamond", "#minecraft:diamond_ores"), Map.entry("diamonds", "#minecraft:diamond_ores"),
            Map.entry("copper", "#minecraft:copper_ores"), Map.entry("redstone", "#minecraft:redstone_ores"),
            Map.entry("lapis", "#minecraft:lapis_ores"), Map.entry("emerald", "#minecraft:emerald_ores"),
            Map.entry("emeralds", "#minecraft:emerald_ores"), Map.entry("ore", "#c:ores"), Map.entry("ores", "#c:ores"),
            Map.entry("wool", "#minecraft:wool"), Map.entry("flowers", "#minecraft:small_flowers"), Map.entry("flower", "#minecraft:small_flowers")
    );

    private final String displayName;
    @Nullable
    private final TagKey<Block> tag;
    @Nullable
    private final Block block;

    private BlockMatcher(String displayName, @Nullable TagKey<Block> tag, @Nullable Block block) {
        this.displayName = displayName;
        this.tag = tag;
        this.block = block;
    }

    public String displayName() {
        return displayName;
    }

    public boolean test(BlockState state) {
        if (state.isAir() || state.hasBlockEntity()) return false;
        if (tag != null) return state.is(tag);
        return block != null && state.is(block);
    }

    /** Returns null if we can't figure out which block was meant. */
    @Nullable
    public static BlockMatcher parse(String input) {
        if (input == null || input.isBlank()) return null;
        String key = input.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        String display = key.replace("minecraft:", "").replace('_', ' ').replace("#", "");
        key = ALIASES.getOrDefault(key, key);

        if (key.startsWith("#")) {
            ResourceLocation rl = ResourceLocation.tryParse(key.substring(1));
            if (rl == null) return null;
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, rl);
            return BuiltInRegistries.BLOCK.getTag(tag).isPresent() ? new BlockMatcher(display, tag, null) : null;
        }
        ResourceLocation rl = ResourceLocation.tryParse(key);
        if (rl == null) return null;
        // "iron_ore" -> also matches deepslate_iron_ore via the vanilla ore tags.
        if (rl.getNamespace().equals("minecraft") && rl.getPath().endsWith("_ore")) {
            String base = rl.getPath().replace("deepslate_", "").replace("nether_", "");
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace(base + "s"));
            if (BuiltInRegistries.BLOCK.getTag(tag).isPresent()) return new BlockMatcher(display, tag, null);
        }
        if (BuiltInRegistries.BLOCK.containsKey(rl)) return new BlockMatcher(display, null, BuiltInRegistries.BLOCK.get(rl));
        // Try singular: "oak_logs" -> "oak_log"
        if (key.endsWith("s")) return parse(key.substring(0, key.length() - 1));
        return null;
    }
}
