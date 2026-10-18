package com.solacemod.solace.story;

import com.google.gson.JsonObject;
import com.solacemod.solace.config.SolaceConfig;
import com.solacemod.solace.entity.SolaceEntity;
import com.solacemod.solace.entity.task.BlockMatcher;
import com.solacemod.solace.horror.HorrorDirector;
import com.solacemod.solace.util.Sounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Solace isn't a player. It's woven into the world, and it can bend it: heal you, change the weather,
 * strike monsters down, find anything, conjure items, carry you home...
 * Every power has a cooldown so Solace feels like a presence, not a cheat menu. All can be turned off in the config.
 */
public final class Powers {
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();

    /** Power name -> cooldown in seconds (short: Tether is the real limit; this just stops spamming). */
    private static final Map<String, Integer> COOLDOWN_SECONDS = Map.ofEntries(
            Map.entry("heal", 30), Map.entry("feed", 20), Map.entry("bless", 10), Map.entry("weather", 30), Map.entry("time", 60),
            Map.entry("smite", 10), Map.entry("banish", 20), Map.entry("reveal", 15), Map.entry("find", 5), Map.entry("conjure", 5),
            Map.entry("carry", 30), Map.entry("darken", 60), Map.entry("lights_out", 120), Map.entry("whisper", 20)
    );

    /** Power name -> Tether cost (docs/DESIGN.md section 4). Conjuring costs the item's value instead. */
    public static final Map<String, Double> COSTS = Map.ofEntries(
            Map.entry("find", 5.0), Map.entry("reveal", 5.0), Map.entry("bless", 8.0), Map.entry("feed", 10.0), Map.entry("weather", 10.0),
            Map.entry("smite", 12.0), Map.entry("banish", 12.0), Map.entry("heal", 15.0), Map.entry("carry", 15.0), Map.entry("time", 20.0)
    );

    private static final Map<String, String> EFFECTS = Map.ofEntries(
            Map.entry("night_vision", "night_vision"), Map.entry("speed", "speed"), Map.entry("strength", "strength"),
            Map.entry("resistance", "resistance"), Map.entry("fire_resistance", "fire_resistance"), Map.entry("water_breathing", "water_breathing"),
            Map.entry("regeneration", "regeneration"), Map.entry("haste", "haste"), Map.entry("jump_boost", "jump_boost"),
            Map.entry("slow_falling", "slow_falling"), Map.entry("invisibility", "invisibility"), Map.entry("luck", "luck"),
            Map.entry("absorption", "absorption"), Map.entry("conduit_power", "conduit_power"), Map.entry("dolphins_grace", "dolphins_grace")
    );

    /** Things Solace will never create (game-breaking or admin-only). */
    private static final Set<String> FORBIDDEN = Set.of("command_block", "chain_command_block", "repeating_command_block", "command_block_minecart",
            "structure_block", "structure_void", "jigsaw", "barrier", "light", "debug_stick", "bedrock", "spawner", "trial_spawner", "vault",
            "end_portal_frame", "reinforced_deepslate", "knowledge_book", "petrified_oak_slab", "test_block", "test_instance_block");

    /** "find" targets that are structures, mapped to vanilla structure tags. */
    private static final Map<String, String> STRUCTURES = Map.ofEntries(
            Map.entry("village", "village"), Map.entry("mineshaft", "mineshaft"), Map.entry("shipwreck", "shipwreck"),
            Map.entry("ruined_portal", "ruined_portal"), Map.entry("portal", "ruined_portal"), Map.entry("ocean_ruin", "ocean_ruin"),
            Map.entry("stronghold", "eye_of_ender_located"), Map.entry("end_portal", "eye_of_ender_located"),
            Map.entry("mansion", "on_woodland_explorer_maps"), Map.entry("woodland_mansion", "on_woodland_explorer_maps"),
            Map.entry("monument", "on_ocean_explorer_maps"), Map.entry("ocean_monument", "on_ocean_explorer_maps"),
            Map.entry("treasure", "on_treasure_maps"), Map.entry("buried_treasure", "on_treasure_maps"),
            Map.entry("trial_chamber", "on_trial_chambers_maps"), Map.entry("trial_chambers", "on_trial_chambers_maps")
    );

    private Powers() {
    }

    public static boolean isPower(String type) {
        return COOLDOWN_SECONDS.containsKey(type) || type.equals("teleport_player");
    }

    /** Uses a power. Returns a short result for the AI to tell the player about (or null if there's nothing to say). */
    @Nullable
    public static String use(ServerPlayer owner, SolaceEntity solace, PlayerStory story, JsonObject a, String type) {
        if (type.equals("teleport_player")) type = "carry";
        boolean horror = type.equals("darken") || type.equals("lights_out") || type.equals("whisper");
        if (horror) {
            if (!SolaceConfig.STORY_ENABLED.get() || story.part() < 2 || SolaceConfig.HORROR_INTENSITY.get() <= 0) return null;
        } else if (!SolaceConfig.ALLOW_POWERS.get()) {
            return "your powers are switched off in this world";
        }
        long now = owner.serverLevel().getGameTime();
        String key = owner.getUUID() + ":" + type;
        Long ready = COOLDOWNS.get(key);
        if (ready != null && now < ready) return type + " isn't ready yet (" + (ready - now) / 20 + "s)";

        double cost = COSTS.getOrDefault(type, 0.0);
        if (cost > 0 && story.tether < cost) {
            return "can't: you're too weak right now (your strength is " + (int) story.tether + ", " + type + " needs " + (int) cost
                    + "). You grow stronger the longer they stay with you";
        }

        String result = switch (type) {
            case "heal" -> heal(owner);
            case "feed" -> feed(owner);
            case "bless" -> bless(owner, str(a, "effect"), num(a, "seconds", 60));
            case "weather" -> weather(owner, str(a, "weather"));
            case "time" -> time(owner, str(a, "time"));
            case "smite" -> smite(owner, solace);
            case "banish" -> banish(owner);
            case "reveal" -> reveal(owner);
            case "find" -> find(owner, str(a, "what"));
            case "conjure" -> conjure(owner, story, str(a, "item"), num(a, "count", 1));
            case "carry" -> carry(owner, solace, story, str(a, "name"));
            case "darken" -> {
                owner.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false));
                Sounds.playFor(owner, owner.position(), Sounds.get("entity.warden.heartbeat"), 1.0F, 0.6F);
                yield null;
            }
            case "lights_out" -> {
                HorrorDirector.torchesOut(owner, 4, true);
                yield null;
            }
            case "whisper" -> {
                Sounds.playFor(owner, owner.position().add(owner.getLookAngle().scale(-1.5)), Sounds.get("ambient.soul_sand_valley.mood"), 1.0F, 0.6F);
                yield null;
            }
            default -> null;
        };
        if (result == null) return null;
        boolean failed = result.startsWith("can't");
        // Only start the cooldown if the power actually did something.
        if (!failed) {
            COOLDOWNS.put(key, now + 20L * COOLDOWN_SECONDS.getOrDefault(type, 30));
            if (cost > 0) Bond.spend(story, cost); // conjure pays for itself inside conjure()
            // Big powers show what it really is, but only once the story has revealed it (chapter 6).
            if (solace != null && cost >= 15 && story.chapter >= 6 && SolaceConfig.STORY_ENABLED.get()) solace.showLantern(50);
        }
        // Solace already announced what it was doing. Only follow up when there's news: what "find" found, or a failure.
        if (failed || type.equals("find")) return result;
        story.addEvent("you used your power: " + result);
        return null;
    }

    // ---------- Individual powers ----------

    private static String heal(ServerPlayer p) {
        p.setHealth(p.getMaxHealth());
        p.removeEffect(MobEffects.POISON);
        p.removeEffect(MobEffects.WITHER);
        p.clearFire();
        p.getFoodData().eat(20, 1.0F);
        aura(p, ParticleTypes.HEART, 8);
        Sounds.playFor(p, p.position(), Sounds.get("block.amethyst_block.chime"), 1.0F, 0.8F);
        return "healed them completely";
    }

    private static String feed(ServerPlayer p) {
        p.getFoodData().eat(20, 1.0F);
        aura(p, ParticleTypes.HAPPY_VILLAGER, 10);
        return "filled their hunger";
    }

    private static String bless(ServerPlayer p, String name, int seconds) {
        String effectName = EFFECTS.get(name.toLowerCase(Locale.ROOT).replace(' ', '_'));
        if (effectName == null) return "can't: you only know these blessings: " + String.join(", ", EFFECTS.keySet());
        Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT
                .getHolder(ResourceKey.create(Registries.MOB_EFFECT, ResourceLocation.withDefaultNamespace(effectName))).orElse(null);
        if (effect == null) return "can't: that blessing doesn't exist";
        int ticks = Math.max(10, Math.min(180, seconds)) * 20;
        p.addEffect(new MobEffectInstance(effect, ticks, 0, false, true));
        aura(p, ParticleTypes.ENCHANT, 30);
        return "gave them " + name.replace('_', ' ') + " for " + ticks / 20 + " seconds";
    }

    private static String weather(ServerPlayer p, String kind) {
        ServerLevel level = p.serverLevel();
        if (level.dimension() != Level.OVERWORLD) return "can't: there's no weather here";
        switch (kind.toLowerCase(Locale.ROOT)) {
            case "rain" -> level.setWeatherParameters(0, 20 * 60 * 5, true, false);
            case "thunder", "storm" -> level.setWeatherParameters(0, 20 * 60 * 5, true, true);
            default -> level.setWeatherParameters(20 * 60 * 10, 0, false, false);
        }
        return "changed the weather to " + (kind.isEmpty() ? "clear" : kind);
    }

    private static String time(ServerPlayer p, String when) {
        ServerLevel level = p.serverLevel();
        if (level.dimension() != Level.OVERWORLD) return "can't: time doesn't move here";
        long day = level.getDayTime() / 24000 * 24000;
        long target = switch (when.toLowerCase(Locale.ROOT)) {
            case "night" -> 13000;
            case "midnight" -> 18000;
            case "noon" -> 6000;
            case "sunset", "dusk" -> 12000;
            default -> 1000; // day / morning
        };
        long next = day + target;
        if (next < level.getDayTime()) next += 24000;
        level.setDayTime(next);
        return "made it " + (when.isEmpty() ? "day" : when);
    }

    private static String smite(ServerPlayer p, SolaceEntity solace) {
        ServerLevel level = p.serverLevel();
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24),
                e -> e instanceof Enemy && e.isAlive() && !(e instanceof Player));
        if (targets.isEmpty()) return "can't: there's nothing hostile near them to strike";
        int n = 0;
        for (LivingEntity t : targets) {
            if (n++ >= 5) break;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(t.position());
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            t.hurt(solace.damageSources().magic(), 1000.0F);
        }
        return "struck down " + Math.min(n, 5) + " monsters with lightning";
    }

    private static String banish(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(20),
                e -> e instanceof Enemy && e.isAlive() && !(e instanceof Player));
        if (targets.isEmpty()) return "can't: there's nothing to banish";
        for (LivingEntity t : targets) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, t.getX(), t.getY() + 1, t.getZ(), 15, 0.3, 0.5, 0.3, 0.02);
            t.discard();
        }
        Sounds.playFor(p, p.position(), Sounds.get("entity.illusioner.mirror_move"), 1.0F, 0.5F);
        return "made " + targets.size() + " monsters vanish";
    }

    private static String reveal(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        List<LivingEntity> all = level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(48), e -> e != p && e.isAlive());
        for (LivingEntity e : all) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 30, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 60, 0, false, false));
        return "revealed " + all.size() + " living things nearby (they glow through walls) and let them see in the dark";
    }

    private static String find(ServerPlayer p, String what) {
        String w = what.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        if (w.isEmpty()) return "can't: find what?";
        ServerLevel level = p.serverLevel();
        String tagPath = STRUCTURES.get(w.endsWith("s") && STRUCTURES.containsKey(w.substring(0, w.length() - 1)) ? w.substring(0, w.length() - 1) : w);
        if (tagPath != null) {
            TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, ResourceLocation.withDefaultNamespace(tagPath));
            BlockPos found = level.findNearestMapStructure(tag, p.blockPosition(), 64, false);
            if (found == null) return "can't: there's no " + what + " anywhere near in this dimension";
            return "the nearest " + what + " is " + describe(p, found, false);
        }
        BlockMatcher matcher = BlockMatcher.parse(w);
        if (matcher == null) return "can't: you don't know what '" + what + "' is";
        BlockPos origin = p.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = -32; dy <= 32; dy++) {
            for (int dx = -40; dx <= 40; dx++) {
                for (int dz = -40; dz <= 40; dz++) {
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d >= bestDist) continue;
                    m.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (matcher.test(level.getBlockState(m))) {
                        best = m.immutable();
                        bestDist = d;
                    }
                }
            }
        }
        if (best == null) return "can't: there's no " + matcher.displayName() + " within 40 blocks";
        return "the nearest " + matcher.displayName() + " is " + describe(p, best, true);
    }

    private static String conjure(ServerPlayer p, PlayerStory story, String name, int count) {
        if (!SolaceConfig.ALLOW_CONJURING.get()) return "can't: conjuring is switched off in this world";
        Item item = ActionExecutor.item(name);
        if (item == null) return "can't: you don't know what '" + name + "' is";
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (FORBIDDEN.contains(id) || id.endsWith("_spawn_egg")) return "can't: some things shouldn't exist in anyone's hands";
        if (story.trust <= -30) return "can't: you don't want to give them anything (they've hurt you too much)";
        // No hard cap on what Solace can make. It just costs strength: torches are nearly free, netherite takes a huge bond.
        double unit = ItemValue.of(item);
        int wanted = Math.max(1, Math.min(count, Math.min(64, new ItemStack(item).getMaxStackSize())));
        int amount = (int) Math.min(wanted, Math.floor(story.tether / unit));
        if (amount < 1) {
            return "can't: making even one " + new ItemStack(item).getHoverName().getString() + " needs " + Math.round(unit)
                    + " strength and you only have " + (int) story.tether + " (max " + (int) Bond.maxTether(story) + " right now)";
        }
        Bond.spend(story, unit * amount);
        ItemStack stack = new ItemStack(item, amount);
        if (!p.getInventory().add(stack)) p.drop(stack, false);
        aura(p, ParticleTypes.END_ROD, 20);
        Sounds.playFor(p, p.position(), Sounds.get("block.respawn_anchor.charge"), 0.7F, 1.4F);
        String made = "conjured " + amount + " " + new ItemStack(item).getHoverName().getString() + " out of nothing into their hands";
        return amount < wanted ? made + " (you were only strong enough for " + amount + ")" : made;
    }

    private static String carry(ServerPlayer p, SolaceEntity solace, PlayerStory story, String name) {
        Vec3 dest;
        String where;
        if (name.isEmpty() || name.equalsIgnoreCase("solace") || name.equalsIgnoreCase("me") || name.equalsIgnoreCase("you")) {
            if (solace.level() != p.level()) return "can't: you're in another world";
            dest = solace.position();
            where = "to you";
        } else {
            String mark = story.marks.get(name.toLowerCase(Locale.ROOT).trim());
            if (mark == null) return "can't: you don't remember a place called '" + name + "'";
            String[] parts = mark.split("\\|");
            if (parts.length != 4 || !parts[0].equals(p.level().dimension().location().toString())) return "can't: that place is in another dimension";
            dest = new Vec3(Integer.parseInt(parts[1]) + 0.5, Integer.parseInt(parts[2]), Integer.parseInt(parts[3]) + 0.5);
            where = "to " + name;
        }
        ServerLevel level = p.serverLevel();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 40, 0.3, 0.8, 0.3, 0.05);
        p.teleportTo(dest.x, dest.y, dest.z);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, dest.x, dest.y + 1, dest.z, 40, 0.3, 0.8, 0.3, 0.05);
        Sounds.play(level, dest, "entity.enderman.teleport", SoundSource.PLAYERS, 1.0F, 0.6F);
        if (solace.level() == p.level()) solace.blinkBehind(p);
        return "carried them " + where;
    }

    // ---------- Helpers ----------

    /** "23 blocks away, 14 below, toward the north-east (x 120, y 12, z -40)". */
    static String describe(ServerPlayer p, BlockPos target, boolean withHeight) {
        int dx = target.getX() - p.getBlockX();
        int dz = target.getZ() - p.getBlockZ();
        int dy = target.getY() - p.getBlockY();
        int dist = (int) Math.round(Math.sqrt(dx * dx + dz * dz + (withHeight ? dy * dy : 0)));
        StringBuilder sb = new StringBuilder().append(dist).append(" blocks away");
        if (withHeight && Math.abs(dy) > 2) sb.append(", ").append(Math.abs(dy)).append(dy < 0 ? " below" : " above");
        sb.append(", toward the ").append(compass(dx, dz));
        sb.append(" (x ").append(target.getX()).append(withHeight ? ", y " + target.getY() : "").append(", z ").append(target.getZ()).append(')');
        return sb.toString();
    }

    static String compass(int dx, int dz) {
        if (dx == 0 && dz == 0) return "right here";
        double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, 90 = east
        String[] dirs = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return dirs[(int) Math.round(((angle + 360) % 360) / 45.0) % 8];
    }

    private static void aura(ServerPlayer p, net.minecraft.core.particles.SimpleParticleType type, int count) {
        p.serverLevel().sendParticles(type, p.getX(), p.getY() + 1, p.getZ(), count, 0.5, 0.7, 0.5, 0.05);
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

    public static void forget(UUID player) {
        COOLDOWNS.keySet().removeIf(k -> k.startsWith(player.toString()));
    }
}
