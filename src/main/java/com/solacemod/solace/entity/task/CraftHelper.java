package com.solacemod.solace.entity.task;

import com.solacemod.solace.entity.SolaceEntity;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Crafts items from what's in Solace's pockets, using the game's real recipes.
 * If an ingredient is missing it tries to craft that first (for example logs -> planks -> sticks).
 */
public final class CraftHelper {
    private static final int MAX_DEPTH = 2;

    private CraftHelper() {
    }

    /** Crafts up to {@code count} of the item. Returns how many were made. */
    public static int craft(SolaceEntity solace, Item item, int count) {
        int made = 0;
        int guard = 0;
        while (made < count && guard++ < 64) {
            int got = craftOnce(solace, item, 0);
            if (got <= 0) break;
            made += got;
        }
        return made;
    }

    private static int craftOnce(SolaceEntity solace, Item item, int depth) {
        Level level = solace.level();
        SimpleContainer inv = solace.inventory;
        List<RecipeHolder<CraftingRecipe>> recipes = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack result = holder.value().getResultItem(level.registryAccess());
            if (!result.isEmpty() && result.is(item)) recipes.add(holder);
        }
        for (RecipeHolder<CraftingRecipe> holder : recipes) {
            List<Integer> slots = match(holder.value().getIngredients(), inv);
            if (slots != null) return consumeAndCraft(solace, holder.value(), slots);
        }
        if (depth >= MAX_DEPTH) return 0;
        // Missing something: try to make the missing ingredients, then try again.
        for (RecipeHolder<CraftingRecipe> holder : recipes) {
            boolean progressed = false;
            for (Ingredient ing : holder.value().getIngredients()) {
                if (ing.isEmpty() || hasAny(inv, ing)) continue;
                for (ItemStack option : ing.getItems()) {
                    if (option.is(item)) continue; // avoid loops
                    if (craftOnce(solace, option.getItem(), depth + 1) > 0) {
                        progressed = true;
                        break;
                    }
                }
            }
            if (progressed) {
                List<Integer> slots = match(holder.value().getIngredients(), inv);
                if (slots != null) return consumeAndCraft(solace, holder.value(), slots);
            }
        }
        return 0;
    }

    private static boolean hasAny(SimpleContainer inv, Ingredient ing) {
        for (int i = 0; i < inv.getContainerSize(); i++) if (ing.test(inv.getItem(i))) return true;
        return false;
    }

    /** Finds one pocket slot for each ingredient, or null if something is missing. */
    private static List<Integer> match(List<Ingredient> ingredients, SimpleContainer inv) {
        int[] used = new int[inv.getContainerSize()];
        List<Integer> slots = new ArrayList<>();
        for (Ingredient ing : ingredients) {
            if (ing.isEmpty()) continue;
            int found = -1;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && s.getCount() > used[i] && ing.test(s)) {
                    found = i;
                    break;
                }
            }
            if (found < 0) return null;
            used[found]++;
            slots.add(found);
        }
        return slots.isEmpty() ? null : slots;
    }

    private static int consumeAndCraft(SolaceEntity solace, CraftingRecipe recipe, List<Integer> slots) {
        SimpleContainer inv = solace.inventory;
        List<ItemStack> leftovers = new ArrayList<>();
        for (int slot : slots) {
            ItemStack s = inv.getItem(slot);
            ItemStack remainder = s.getCraftingRemainingItem();
            if (!remainder.isEmpty()) leftovers.add(remainder.copy());
            s.shrink(1);
        }
        ItemStack result = recipe.getResultItem(solace.level().registryAccess()).copy();
        int count = result.getCount();
        solace.storeOrDrop(result);
        leftovers.forEach(solace::storeOrDrop);
        return count;
    }
}
