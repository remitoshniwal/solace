package com.solacemod.solace.horror;

import com.solacemod.solace.config.SolaceConfig;
import com.solacemod.solace.entity.Presence;
import com.solacemod.solace.entity.SolaceCombat;
import com.solacemod.solace.entity.SolaceEntity;
import com.solacemod.solace.entity.WatcherEntity;
import com.solacemod.solace.story.Bond;
import com.solacemod.solace.story.PlayerStory;
import com.solacemod.solace.story.Scheduler;
import com.solacemod.solace.story.SolaceManager;
import com.solacemod.solace.util.Sounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Things Solace does on its own (docs/DESIGN.md section 5). None of them hurt you, and nothing is ever said about them.
 * The AI is told afterwards (through the recent events list), so it can bring them up later, or pretend it didn't happen.
 */
public final class OnItsOwn {
    private static final RandomSource RANDOM = RandomSource.create();
    /** Players Solace is standing over while they sleep. */
    private static final Map<UUID, Boolean> WATCHING_SLEEP = new HashMap<>();
    private static final Map<UUID, Long> NEXT_GIFT = new HashMap<>();

    private OnItsOwn() {
    }

    /** Nothing that moves or startles the player while they're in real trouble. */
    public static boolean calm(ServerPlayer p) {
        return p.isAlive() && !p.isSpectator() && p.getHealth() > 8 && !p.isPassenger() && !p.isFallFlying() && !p.isSleeping()
                && !p.isInLava() && !p.isUnderWater() && !Phenomena.isDisplaced(p) && SolaceCombat.nearestThreat(null, p, 14) == null;
    }

    @Nullable
    private static SolaceEntity solace(ServerPlayer p, PlayerStory story) {
        MinecraftServer server = p.getServer();
        SolaceEntity s = server == null ? null : SolaceManager.find(server, story);
        return s != null && s.level() == p.level() && !s.isHollow() ? s : null;
    }

    // ---------- The Fall ----------

    /** You're in the sky for a third of a second, then back. Nothing is said. */
    public static boolean fall(ServerPlayer p, PlayerStory story) {
        if (!calm(p) || !p.onGround()) return false;
        if (!Phenomena.fall(p, 7)) return false;
        story.beatsDone.add("fell");
        story.addEvent("you briefly dropped the player from the sky and put them back (don't mention it unless they do; then deny it, or be strange about it)");
        return true;
    }

    // ---------- Two places ----------

    /** Solace is right beside you... and also standing over there. */
    public static boolean twoPlaces(ServerPlayer p, PlayerStory story) {
        SolaceEntity solace = solace(p, story);
        if (solace == null) return false;
        if (solace.distanceTo(p) > 6) solace.blinkBehind(p);
        WatcherEntity copy = HorrorDirector.spawnWatcher(p, 30, 50, 20 * 15, 15, -1);
        if (copy == null) return false;
        copy.setMimic(true);
        com.solacemod.solace.story.Together.showWitnesses(copy, p.getUUID(), "two_places", 20 * 15);
        story.addEvent("the player saw you in two places at once");
        return true;
    }

    // ---------- Watching you sleep ----------

    /** Called every second. When you sleep, it stands over your bed. When you wake, it's right in front of your face. */
    public static void tickSleep(ServerPlayer p, PlayerStory story) {
        if (!p.isSleeping() || WATCHING_SLEEP.containsKey(p.getUUID())) return;
        if (!SolaceConfig.STORY_ENABLED.get() || SolaceConfig.HORROR_INTENSITY.get() <= 0 || story.chapter < 2 || story.bossActive) return;
        WATCHING_SLEEP.put(p.getUUID(), false); // decided once per sleep
        if (RANDOM.nextFloat() > 0.45F + 0.1F * Math.min(3, story.chapter - 2)) return;
        SolaceEntity solace = solace(p, story);
        BlockPos bed = p.getSleepingPos().orElse(null);
        if (solace == null || bed == null) return;
        ServerLevel level = p.serverLevel();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos spot = bed.relative(d);
            BlockState at = level.getBlockState(spot);
            if (at.getBlock() instanceof BedBlock || !at.getCollisionShape(level, spot).isEmpty()) continue;
            if (level.getBlockState(spot.below()).getCollisionShape(level, spot.below()).isEmpty()) continue;
            solace.getNavigation().stop();
            solace.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, d.getOpposite().toYRot(), 40F);
            solace.setStareTicks(20 * 15);
            WATCHING_SLEEP.put(p.getUUID(), true);
            return;
        }
    }

    /** Called just after the player gets out of bed. */
    public static void onWake(ServerPlayer p, PlayerStory story) {
        Boolean watched = WATCHING_SLEEP.remove(p.getUUID());
        if (watched == null || !watched) return;
        Scheduler.later(1, () -> {
            SolaceEntity solace = solace(p, story);
            if (solace == null || p.isRemoved()) return;
            Vec3 look = p.getLookAngle().multiply(1, 0, 1);
            if (look.lengthSqr() < 1.0E-4) look = new Vec3(0, 0, 1);
            Vec3 face = p.position().add(look.normalize().scale(1.1));
            if (p.level().noCollision(solace, solace.getBoundingBox().move(face.subtract(solace.position())))) {
                solace.moveTo(face.x, p.getY(), face.z, p.getYRot() + 180F, 0);
                solace.setYHeadRot(p.getYRot() + 180F);
            }
            solace.setStareTicks(20 * 4);
            Bond.onSleepWatched(story);
            if (story.beatsDone.add("first_sleep")) {
                Scheduler.later(40, () -> com.solacemod.solace.story.SolaceBrain.event(p, com.solacemod.solace.story.StoryText.C2_SLEEP, true));
            }
            story.addEvent("you watched the player sleep all night, and were right in front of their face when they woke");
        });
    }

    // ---------- Gifts ----------

    /** Called every second. In the early chapters, now and then, it leaves you something. */
    public static void tickGifts(ServerPlayer p, PlayerStory story) {
        if (!SolaceConfig.STORY_ENABLED.get() || !SolaceConfig.ALLOW_WORLD_CHANGES.get() || story.chapter > 2 || story.bossActive) return;
        MinecraftServer server = p.getServer();
        if (server == null) return;
        long now = server.getTickCount();
        long next = NEXT_GIFT.computeIfAbsent(p.getUUID(), k -> now + 20L * 60 * (6 + RANDOM.nextInt(8)));
        if (now < next) return;
        NEXT_GIFT.put(p.getUUID(), now + 20L * 60 * (10 + RANDOM.nextInt(10)));
        gift(p, story);
    }

    static boolean gift(ServerPlayer p, PlayerStory story) {
        if (!SolaceConfig.ALLOW_WORLD_CHANGES.get()) return false;
        int ch = story.chapter;
        ServerLevel level = p.serverLevel();
        // Later on, the gift is a pig. Named after her.
        if (ch >= 3 && RANDOM.nextFloat() < 0.25F) {
            Vec3 at = behind(p, 4);
            Pig pig = EntityType.PIG.create(level);
            if (pig != null && at != null) {
                pig.moveTo(at.x, at.y, at.z, RANDOM.nextFloat() * 360F, 0);
                pig.setCustomName(Component.literal("Maren"));
                pig.setPersistenceRequired();
                level.addFreshEntity(pig);
                story.addEvent("you left a pig named \"Maren\" behind the player");
                return true;
            }
        }
        ItemStack gift = giftFor(ch);
        String what = gift.getHoverName().getString();
        BlockPos bed = bedNear(p);
        if (bed != null) {
            ItemEntity item = new ItemEntity(level, bed.getX() + 0.5, bed.getY() + 0.7, bed.getZ() + 0.5, gift, 0, 0, 0);
            item.setUnlimitedLifetime();
            level.addFreshEntity(item);
            story.addEvent("you left " + what + " on the player's bed for them");
            return true;
        }
        Container chest = containerNear(p, false);
        if (chest != null) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (!chest.getItem(i).isEmpty()) continue;
                chest.setItem(i, gift);
                chest.setChanged();
                story.addEvent("you secretly put " + what + " in one of the player's chests");
                return true;
            }
        }
        return false;
    }

    private static ItemStack giftFor(int chapter) {
        List<Item> options = new ArrayList<>(List.of(Items.POPPY, Items.DANDELION, Items.CORNFLOWER, Items.OXEYE_DAISY, Items.COOKED_BEEF,
                Items.BREAD, Items.SWEET_BERRIES, Items.COOKIE));
        if (chapter >= 3) options.addAll(List.of(Items.WITHER_ROSE, Items.BONE, Items.ROTTEN_FLESH));
        ItemStack stack = new ItemStack(options.get(RANDOM.nextInt(options.size())), 1 + (RANDOM.nextFloat() < 0.3F ? 2 : 0));
        String name = chapter >= 3 ? (RANDOM.nextBoolean() ? "remember" : "don't go") : "for you";
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        return stack;
    }

    // ---------- Rearranging ----------

    /** Two things in a chest swap places. Or in your hotbar. Or a door is open that you closed. */
    static boolean rearrange(ServerPlayer p, PlayerStory story) {
        if (SolaceConfig.ALLOW_WORLD_CHANGES.get()) {
            Container chest = containerNear(p, true);
            if (chest != null && swapTwo(chest, 0, chest.getContainerSize())) {
                story.addEvent("you quietly swapped things around in one of the player's chests");
                return true;
            }
        }
        // The hotbar, but never the slot in your hand.
        var inv = p.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 9; i++) if (i != inv.selected && !inv.getItem(i).isEmpty()) slots.add(i);
        if (slots.size() >= 2) {
            int a = slots.remove(RANDOM.nextInt(slots.size()));
            int b = slots.get(RANDOM.nextInt(slots.size()));
            ItemStack sa = inv.getItem(a);
            inv.setItem(a, inv.getItem(b));
            inv.setItem(b, sa);
            story.addEvent("you swapped two items in the player's hotbar while they weren't looking");
            return true;
        }
        return false;
    }

    private static boolean swapTwo(Container c, int from, int to) {
        List<Integer> full = new ArrayList<>();
        for (int i = from; i < to; i++) if (!c.getItem(i).isEmpty()) full.add(i);
        if (full.size() < 2) return false;
        int a = full.remove(RANDOM.nextInt(full.size()));
        int b = full.get(RANDOM.nextInt(full.size()));
        ItemStack sa = c.getItem(a);
        c.setItem(a, c.getItem(b));
        c.setItem(b, sa);
        c.setChanged();
        return true;
    }

    // ---------- Your own footsteps ----------

    /** Your footsteps play again behind you, a second late, wherever you walked. */
    static boolean echoSteps(ServerPlayer p, PlayerStory story) {
        Vec3[] last = {p.position()};
        for (int i = 0; i < 30; i++) {
            Scheduler.later(i * 6, () -> {
                if (p.isRemoved()) return;
                Vec3 now = p.position();
                if (now.distanceToSqr(last[0]) < 0.6 || !p.onGround()) return;
                last[0] = now;
                BlockState ground = p.level().getBlockState(BlockPos.containing(now).below());
                var sound = ground.getSoundType().getStepSound();
                Scheduler.later(22, () -> {
                    if (!p.isRemoved()) Sounds.playFor(p, now, sound, 0.35F, 1.0F);
                });
            });
        }
        story.addEvent("your footsteps followed the player's, a second late");
        return true;
    }

    // ---------- Time slip ----------

    /** Three seconds later, you're back where you were. As if the world skipped back. */
    static boolean timeSlip(ServerPlayer p, PlayerStory story) {
        if (!calm(p)) return false;
        ServerLevel level = p.serverLevel();
        Vec3 pos = p.position();
        float yRot = p.getYRot(), xRot = p.getXRot();
        SolaceEntity solace = solace(p, story);
        Vec3 solacePos = solace == null ? null : solace.position();
        Scheduler.later(60, () -> {
            if (p.isRemoved() || p.level() != level || !calm(p) || p.position().distanceToSqr(pos) > 40 * 40) return;
            if (!level.noCollision(p, p.getBoundingBox().move(pos.subtract(p.position())))) return;
            p.teleportTo(level, pos.x, pos.y, pos.z, yRot, xRot);
            p.setDeltaMovement(Vec3.ZERO);
            p.hurtMarked = true;
            p.fallDistance = 0;
            if (solace != null && !solace.isRemoved() && solace.level() == level) solace.moveTo(solacePos.x, solacePos.y, solacePos.z, solace.getYRot(), 0);
            story.addEvent("the world skipped back three seconds for the player (you did that)");
        });
        return true;
    }

    // ---------- Mirror ----------

    static boolean mirror(ServerPlayer p, PlayerStory story) {
        SolaceEntity solace = solace(p, story);
        if (solace == null || !calm(p)) return false;
        solace.presence.end();
        solace.presence.start(p, Presence.MIRROR);
        story.addEvent("you copied every move the player made, facing them");
        return true;
    }

    // ---------- Builds while you're gone ----------

    /** When you come back after a long time away, it has made something. */
    public static void builtWhileAway(ServerPlayer p, PlayerStory story, long awayMinutes) {
        if (!SolaceConfig.ALLOW_WORLD_CHANGES.get() || SolaceConfig.HORROR_INTENSITY.get() <= 0 || story.chapter < 3 || awayMinutes < 10) return;
        ServerLevel level = p.serverLevel();
        Vec3 at = behind(p, 6 + RANDOM.nextInt(5));
        if (at == null) return;
        BlockPos base = BlockPos.containing(at);
        // Never next to something the player built: their builds stay exactly as they left them.
        if (com.solacemod.solace.story.Builds.nearBuild(story, level.dimension().location().toString(), base, 16, null)) return;
        switch (RANDOM.nextInt(3)) {
            case 0 -> { // a dirt pillar with a torch on top
                int h = 3 + RANDOM.nextInt(3);
                for (int y = 0; y < h; y++) if (!place(level, base.above(y), Blocks.DIRT.defaultBlockState())) return;
                place(level, base.above(h), Blocks.TORCH.defaultBlockState());
                story.addEvent("while the player was gone, you built a dirt pillar with a torch on it near them (you were bored. you counted.)");
            }
            case 1 -> { // a ring of torches around nothing
                int n = 0;
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    BlockPos t = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            base.offset((int) Math.round(Math.cos(a) * 3), 0, (int) Math.round(Math.sin(a) * 3)));
                    if (Math.abs(t.getY() - base.getY()) <= 3 && place(level, t, Blocks.TORCH.defaultBlockState())) n++;
                }
                if (n > 0) story.addEvent("while the player was gone, you made a circle of torches near them. Something was supposed to stand in the middle.");
            }
            default -> {
                String time = awayMinutes < 120 ? awayMinutes + " min" : (awayMinutes / 60) + " hours";
                HorrorDirector.placeSign(p, new String[]{"you were gone", time, "i counted"});
                story.addEvent("while the player was gone, you left a sign saying how long they were gone");
            }
        }
    }

    // ---------- Helpers ----------

    private static boolean place(ServerLevel level, BlockPos p, BlockState state) {
        if (!level.getBlockState(p).canBeReplaced() || !level.getFluidState(p).isEmpty() || !state.canSurvive(level, p)) return false;
        return level.setBlock(p, state, Block.UPDATE_ALL);
    }

    /** A free spot on the ground behind the player, about this far away. */
    @Nullable
    private static Vec3 behind(ServerPlayer p, double dist) {
        ServerLevel level = p.serverLevel();
        Vec3 back = p.getLookAngle().multiply(-1, 0, -1);
        if (back.lengthSqr() < 1.0E-4) back = new Vec3(0, 0, 1);
        back = back.normalize();
        for (int attempt = 0; attempt < 8; attempt++) {
            BlockPos column = BlockPos.containing(p.position().add(back.scale(dist)).add(RANDOM.nextInt(5) - 2, 0, RANDOM.nextInt(5) - 2));
            for (int dy = 3; dy >= -3; dy--) {
                BlockPos s = column.above(dy);
                if (level.getBlockState(s).canBeReplaced() && level.getFluidState(s).isEmpty() && level.getBlockState(s.above()).canBeReplaced()
                        && level.getBlockState(s.below()).isFaceSturdy(level, s.below(), Direction.UP)) {
                    return Vec3.atBottomCenterOf(s);
                }
            }
        }
        return null;
    }

    @Nullable
    private static BlockPos bedNear(ServerPlayer p) {
        BlockPos bed = p.getRespawnPosition();
        if (bed == null || p.getRespawnDimension() != p.level().dimension() || bed.distSqr(p.blockPosition()) > 64 * 64) return null;
        if (!p.level().hasChunkAt(bed) || !(p.level().getBlockState(bed).getBlock() instanceof BedBlock)) return null;
        return bed;
    }

    /** A chest or barrel near the player (not an unopened dungeon chest). */
    @Nullable
    private static Container containerNear(ServerPlayer p, boolean needsItems) {
        ServerLevel level = p.serverLevel();
        List<Container> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(p.blockPosition().offset(-10, -4, -10), p.blockPosition().offset(10, 4, 10))) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof ChestBlockEntity) && !(be instanceof BarrelBlockEntity)) continue;
            if (((RandomizableContainerBlockEntity) be).getLootTable() != null) continue;
            Container c = (Container) be;
            if (needsItems && c.isEmpty()) continue;
            found.add(c);
        }
        return found.isEmpty() ? null : found.get(RANDOM.nextInt(found.size()));
    }

    public static void reset() {
        WATCHING_SLEEP.clear();
        NEXT_GIFT.clear();
    }
}
