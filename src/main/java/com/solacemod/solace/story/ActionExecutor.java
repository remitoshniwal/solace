package com.solacemod.solace.story;

import com.google.gson.JsonObject;
import com.solacemod.solace.config.SolaceConfig;
import com.solacemod.solace.entity.SolaceEntity;
import com.solacemod.solace.entity.SolaceGoals;
import com.solacemod.solace.entity.task.AttackTask;
import com.solacemod.solace.entity.task.BlockMatcher;
import com.solacemod.solace.entity.task.BuildTask;
import com.solacemod.solace.entity.task.CollectTask;
import com.solacemod.solace.entity.task.CraftHelper;
import com.solacemod.solace.entity.task.GoToTask;
import com.solacemod.solace.entity.task.MineTask;
import com.solacemod.solace.entity.task.TorchTask;
import com.solacemod.solace.horror.HorrorDirector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Carries out the actions the AI (or the keyword parser) asked for. Everything is checked first:
 * unknown items, disabled abilities, too-large numbers and far-away coordinates are all rejected safely.
 */
public final class ActionExecutor {
    private ActionExecutor() {
    }

    /** Runs the actions and returns short messages Solace should say about them (e.g. "I don't have any diamonds."). */
    public static List<String> execute(ServerPlayer owner, SolaceEntity solace, PlayerStory story, List<JsonObject> actions) {
        List<String> feedback = new ArrayList<>();
        for (JsonObject a : actions) {
            try {
                String msg = run(owner, solace, story, a);
                if (msg != null && !msg.isEmpty()) feedback.add(msg);
            } catch (RuntimeException e) {
                com.solacemod.solace.Solace.LOGGER.debug("Solace action failed: {}", a, e);
            }
        }
        return feedback;
    }

    @Nullable
    private static String run(ServerPlayer owner, SolaceEntity solace, PlayerStory story, JsonObject a) {
        String type = str(a, "type").toLowerCase(Locale.ROOT);
        switch (type) {
            case "follow" -> {
                solace.setMode(SolaceEntity.Mode.FOLLOW);
                return null;
            }
            case "stay", "wait" -> {
                solace.setTask(null);
                solace.setMode(SolaceEntity.Mode.STAY);
                return null;
            }
            case "wander", "explore" -> {
                solace.setMode(SolaceEntity.Mode.WANDER);
                return null;
            }
            case "come" -> {
                solace.setTask(null);
                solace.setMode(SolaceEntity.Mode.FOLLOW);
                if (solace.distanceTo(owner) > 16) SolaceGoals.teleportNear(solace, owner);
                else solace.getNavigation().moveTo(owner, 1.3);
                return null;
            }
            case "stop" -> {
                solace.setTask(null);
                solace.setTarget(null);
                return null;
            }
            case "guard", "protect" -> {
                if (!SolaceConfig.ALLOW_COMBAT.get()) return "I'm not allowed to fight.";
                boolean on = !a.has("on") || a.get("on").getAsBoolean();
                solace.setGuard(on);
                return null;
            }
            case "attack", "hunt", "kill" -> {
                if (!SolaceConfig.ALLOW_COMBAT.get()) return "I'm not allowed to fight.";
                String target = str(a, "target").toLowerCase(Locale.ROOT).replace(' ', '_');
                int count = clamp(num(a, "count", 1), 1, 20);
                if (target.isEmpty() || target.matches("hostile|monster|monsters|mob|mobs|enemy|enemies|any")) {
                    solace.setTask(new AttackTask(null, "monsters", count));
                    return null;
                }
                EntityType<?> et = entityType(target);
                if (et == null) return "I don't know what a " + target.replace('_', ' ') + " is.";
                if (et == EntityType.PLAYER) return "I won't hurt people.";
                solace.setTask(new AttackTask(et, et.getDescription().getString().toLowerCase(Locale.ROOT), count));
                return null;
            }
            case "mine", "dig", "gather", "chop" -> {
                if (!SolaceConfig.ALLOW_MINING.get()) return "I'm not allowed to mine here.";
                BlockMatcher matcher = BlockMatcher.parse(str(a, "block"));
                if (matcher == null) return "I don't know which block you mean.";
                int count = clamp(num(a, "count", 8), 1, SolaceConfig.MAX_MINE_COUNT.get());
                solace.setTask(new MineTask(matcher, count));
                return null;
            }
            case "collect", "pickup", "pick_up" -> {
                solace.setTask(new CollectTask());
                return null;
            }
            case "give" -> {
                return give(owner, solace, str(a, "item"), clamp(num(a, "count", 64), 1, 64 * 27));
            }
            case "craft" -> {
                if (!SolaceConfig.ALLOW_CRAFTING.get()) return "I'm not allowed to craft.";
                Item item = item(str(a, "item"));
                if (item == null) return "I don't know how to make that.";
                int wanted = clamp(num(a, "count", 1), 1, 64);
                int made = CraftHelper.craft(solace, item, wanted);
                String name = new ItemStack(item).getHoverName().getString();
                return made == 0 ? "I don't have what I need to make " + name + "." : "Made " + made + " " + name + ".";
            }
            case "build" -> {
                if (!SolaceConfig.ALLOW_BUILDING.get()) return "I'm not allowed to build.";
                Block preferred = null;
                Item bi = item(str(a, "block"));
                if (bi instanceof BlockItem blockItem) preferred = blockItem.getBlock();
                BuildTask task = BuildTask.create(str(a, "structure"), owner.blockPosition(), owner.getDirection(), preferred);
                if (task == null) return "I only know how to build a " + String.join(", ", BuildTask.STRUCTURES) + ".";
                solace.setTask(task);
                return null;
            }
            case "torches", "torch", "light" -> {
                if (!SolaceConfig.ALLOW_BUILDING.get()) return "I'm not allowed to place blocks.";
                solace.setTask(new TorchTask());
                return null;
            }
            case "mark", "remember_place" -> {
                String name = cleanName(str(a, "name"));
                if (name.isEmpty()) name = "home";
                if (story.marks.size() >= 20 && !story.marks.containsKey(name)) return "I can't remember any more places.";
                BlockPos p = owner.blockPosition();
                story.marks.put(name, owner.level().dimension().location() + "|" + p.getX() + "|" + p.getY() + "|" + p.getZ());
                return "I'll remember this place as \"" + name + "\".";
            }
            case "goto", "go", "go_to" -> {
                return goTo(owner, solace, story, a);
            }
            case "equip" -> {
                solace.equipBestWeapon();
                return null;
            }
            case "stare" -> {
                if (story.act < 2) return null;
                solace.setStareTicks(20 * 8);
                return null;
            }
            case "glitch" -> {
                if (story.act < 2) return null;
                HorrorDirector.glitchScreen(owner, 0, 30, "");
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    private static String goTo(ServerPlayer owner, SolaceEntity solace, PlayerStory story, JsonObject a) {
        if (a.has("x") && a.has("z")) {
            int x = num(a, "x", 0);
            int z = num(a, "z", 0);
            int y = a.has("y") ? num(a, "y", owner.getBlockY()) : owner.getBlockY();
            BlockPos target = new BlockPos(x, y, z);
            if (target.distSqr(solace.blockPosition()) > 128 * 128) return "That's too far for me to walk.";
            solace.setMode(SolaceEntity.Mode.STAY);
            solace.setTask(new GoToTask(target, x + " " + y + " " + z));
            return null;
        }
        String name = cleanName(str(a, "name"));
        String mark = story.marks.get(name);
        if (mark == null) return "I don't remember a place called \"" + name + "\".";
        String[] parts = mark.split("\\|");
        if (parts.length != 4 || !parts[0].equals(owner.level().dimension().location().toString())) return "That place is in another world.";
        BlockPos target = new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        if (target.distSqr(solace.blockPosition()) > 160 * 160) return name + " is too far away for me to walk.";
        solace.setMode(SolaceEntity.Mode.STAY);
        solace.setTask(new GoToTask(target, name));
        return null;
    }

    private static String give(ServerPlayer owner, SolaceEntity solace, String itemName, int count) {
        String wanted = itemName.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        boolean all = wanted.isEmpty() || wanted.equals("all") || wanted.equals("everything");
        Item item = all ? null : item(wanted);
        int given = 0;
        for (int i = 0; i < solace.inventory.getContainerSize() && given < count; i++) {
            ItemStack s = solace.inventory.getItem(i);
            if (s.isEmpty()) continue;
            boolean matches = all || (item != null ? s.is(item) : BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().contains(wanted));
            if (!matches) continue;
            int take = Math.min(s.getCount(), count - given);
            ItemStack out = s.split(take);
            given += take;
            if (!owner.getInventory().add(out)) owner.drop(out, false);
        }
        if (given == 0) return all ? "My pockets are empty." : "I don't have any " + wanted.replace('_', ' ') + ".";
        com.solacemod.solace.util.Sounds.playAt(owner, "entity.item.pickup", 0.5F, 1.0F);
        return null;
    }

    @Nullable
    static Item item(String name) {
        if (name == null || name.isBlank()) return null;
        String key = name.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        ResourceLocation rl = ResourceLocation.tryParse(key);
        if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) return BuiltInRegistries.ITEM.get(rl);
        // Plurals: "torches" -> "torch", "planks" stays, "sticks" -> "stick"
        for (String suffix : new String[]{"es", "s"}) {
            if (!key.endsWith(suffix)) continue;
            rl = ResourceLocation.tryParse(key.substring(0, key.length() - suffix.length()));
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) return BuiltInRegistries.ITEM.get(rl);
        }
        return null;
    }

    @Nullable
    private static EntityType<?> entityType(String name) {
        String key = name.endsWith("s") && !name.endsWith("ss") ? name.substring(0, name.length() - 1) : name;
        for (String k : new String[]{name, key}) {
            ResourceLocation rl = ResourceLocation.tryParse(k);
            if (rl != null && BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) return BuiltInRegistries.ENTITY_TYPE.get(rl);
        }
        return null;
    }

    private static String cleanName(String s) {
        String out = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_ ]", "").trim();
        return out.length() > 24 ? out.substring(0, 24) : out;
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static int num(JsonObject o, String key, int def) {
        try {
            return o.has(key) && o.get(key).isJsonPrimitive() ? (int) Math.round(o.get(key).getAsDouble()) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
