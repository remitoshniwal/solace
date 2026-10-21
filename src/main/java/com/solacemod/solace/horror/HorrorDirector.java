package com.solacemod.solace.horror;

import com.solacemod.solace.config.SolaceConfig;
import com.solacemod.solace.entity.ModEntities;
import com.solacemod.solace.entity.SolaceEntity;
import com.solacemod.solace.entity.WatcherEntity;
import com.solacemod.solace.network.ModNetwork;
import com.solacemod.solace.story.Ending;
import com.solacemod.solace.story.Fragments;
import com.solacemod.solace.story.PlayerStory;
import com.solacemod.solace.story.Scheduler;
import com.solacemod.solace.story.SolaceBrain;
import com.solacemod.solace.story.SolaceManager;
import com.solacemod.solace.story.StoryData;
import com.solacemod.solace.util.Sounds;
import com.solacemod.solace.util.Titles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The scary stuff. Picks a random unsettling event every few minutes, more often as the story goes on.
 * Every kind of event can be turned off in the config, and none of them break your builds.
 */
public final class HorrorDirector {
    private static final RandomSource RANDOM = RandomSource.create();

    private enum Kind {
        FOOTSTEPS, CAVE, WHISPER, WATCHER, DOOR, FAKE_CHAT, GLITCH, STARE, MINING, TORCHES, DARKNESS, TITLE, RENAME, SIGN, CORRUPT, FAKE_CRASH,
        // Things Solace does on its own (see OnItsOwn)
        FALL, TWO_PLACES, GIFT, REARRANGE, ECHO_STEPS, TIME_SLIP, MIRROR,
        // Multiplayer: it does something only another player can see (see Together)
        FOR_SOMEONE_ELSE
    }

    private static final List<String> TITLES = List.of("it remembers", "don't promise", "she's still here", "turn around", "you can log off. can't you?", "it was never static");
    private static final List<String> FAKE_LINES = List.of("help", "it's listening", "don't trust it", "is anyone there", "i can see you", "LOG OFF");
    private static final List<String[]> SIGNS = List.of(new String[]{"IT LIES"}, new String[]{"DON'T", "PROMISE"}, new String[]{"she was", "here first"},
            new String[]{"turn", "around"}, new String[]{"day 1000"});
    private static final List<String> RENAMES = List.of("it remembers", "Maren's", "don't", "still here", "mine");

    private HorrorDirector() {
    }

    private static double intensity() {
        return SolaceConfig.HORROR_INTENSITY.get();
    }

    /** Runs once per second per player. */
    public static void tick(ServerPlayer player, PlayerStory story) {
        if (!SolaceConfig.STORY_ENABLED.get() || intensity() <= 0 || !story.met || story.bossActive) return;
        MinecraftServer server = player.getServer();
        if (server == null || player.isSpectator() || !player.isAlive()) return;
        boolean erased = story.ending == Ending.ERASE;
        boolean released = story.ending == Ending.RELEASE;
        if ((story.ending != Ending.NONE && !erased && !released) || story.companion) return;
        if (story.chapter < 2 && !erased) return;

        long now = server.getTickCount();
        if (story.nextHorrorTick == 0) {
            story.nextHorrorTick = now + scheduleGap(story);
            return;
        }
        if (now < story.nextHorrorTick) return;
        story.nextHorrorTick = now + scheduleGap(story);
        story.lastHorrorTick = now;
        if (erased || released) {
            // After the ending: a few last echoes, then the world goes quiet for good.
            com.solacemod.solace.story.Afterward.afterScare(player, story);
            return;
        }
        Kind kind = pick(player, story);
        if (kind != null) run(player, story, kind);
    }

    private static long scheduleGap(PlayerStory story) {
        double minutes = switch (story.ending == Ending.ERASE || story.ending == Ending.RELEASE ? 0 : story.part()) {
            case 0 -> 15 + RANDOM.nextDouble() * 10;
            case 1 -> 6 + RANDOM.nextDouble() * 4;
            case 2 -> 4 + RANDOM.nextDouble() * 3;
            case 3 -> 2.5 + RANDOM.nextDouble() * 2;
            default -> 1.5 + RANDOM.nextDouble() * 1.5;
        };
        // The more the player owes Solace, the more often strange things happen.
        double debtFactor = 1.0 + Math.min(2.0, story.debt / 60.0);
        return (long) (minutes * 1200 / Math.max(0.1, intensity()) / debtFactor);
    }

    @Nullable
    private static Kind pick(@Nullable ServerPlayer player, PlayerStory story) {
        List<Kind> bag = new ArrayList<>();
        boolean world = SolaceConfig.ALLOW_WORLD_CHANGES.get();
        boolean chat = SolaceConfig.ALLOW_FAKE_MESSAGES.get();
        boolean screen = SolaceConfig.ALLOW_SCREEN_EFFECTS.get();
        int chapter = story.chapter;
        if (world) add(bag, Kind.GIFT, chapter <= 3 ? 2 : 1);
        add(bag, Kind.TWO_PLACES, 2);
        if (chapter >= 3) {
            add(bag, Kind.FALL, 1);
            add(bag, Kind.ECHO_STEPS, 2);
        }
        if (chapter >= 4) add(bag, Kind.REARRANGE, 2);
        if (chapter >= 3 && player != null && com.solacemod.solace.story.Together.someoneElseNear(player, story)) add(bag, Kind.FOR_SOMEONE_ELSE, 2);
        if (chapter >= 5) {
            add(bag, Kind.TIME_SLIP, 1);
            add(bag, Kind.MIRROR, 1);
        }
        // Chapter 2 is quiet: only small wrong things, nothing outright scary yet.
        if (chapter < 3) {
            add(bag, Kind.FOOTSTEPS, 1);
            add(bag, Kind.STARE, 1);
            return bag.get(RANDOM.nextInt(bag.size()));
        }
        add(bag, Kind.FOOTSTEPS, 3);
        add(bag, Kind.CAVE, 2);
        add(bag, Kind.WHISPER, 1);
        add(bag, Kind.WATCHER, story.part() >= 4 ? 3 : 2);
        add(bag, Kind.STARE, 2);
        add(bag, Kind.MINING, 1);
        if (world) add(bag, Kind.DOOR, 1);
        if (chat) add(bag, Kind.FAKE_CHAT, 1);
        if (screen) add(bag, Kind.GLITCH, 1);
        if (story.part() >= 3) {
            if (world) add(bag, Kind.TORCHES, 2);
            if (world) add(bag, Kind.SIGN, 1);
            add(bag, Kind.DARKNESS, story.part() >= 4 ? 2 : 1);
            add(bag, Kind.TITLE, 1);
            add(bag, Kind.RENAME, 1);
            add(bag, Kind.CORRUPT, 1);
            if (SolaceConfig.ALLOW_FAKE_CRASH.get() && RANDOM.nextFloat() < 0.15F) add(bag, Kind.FAKE_CRASH, 1);
        }
        return bag.isEmpty() ? null : bag.get(RANDOM.nextInt(bag.size()));
    }

    private static void add(List<Kind> bag, Kind kind, int weight) {
        for (int i = 0; i < weight; i++) bag.add(kind);
    }

    /** Runs a specific event (also used by /solace admin horror). */
    public static boolean run(ServerPlayer player, PlayerStory story, String name) {
        try {
            run(player, story, Kind.valueOf(name.toUpperCase()));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String kinds() {
        StringBuilder sb = new StringBuilder();
        for (Kind k : Kind.values()) sb.append(k.name().toLowerCase()).append(' ');
        return sb.toString().trim();
    }

    private static void run(ServerPlayer player, PlayerStory story, Kind kind) {
        com.solacemod.solace.story.PlaytestLog.log(story, player, "SCARE", kind.name().toLowerCase(java.util.Locale.ROOT));
        ServerLevel level = player.serverLevel();
        switch (kind) {
            case FOOTSTEPS -> footsteps(player);
            case CAVE -> Sounds.playFor(player, around(player, 10), Sounds.get("ambient.cave"), 1.0F, 0.8F + RANDOM.nextFloat() * 0.3F);
            case WHISPER -> Sounds.playFor(player, around(player, 3), Sounds.get("ambient.soul_sand_valley.mood"), 1.0F, 0.5F);
            case MINING -> {
                Vec3 below = player.position().add(RANDOM.nextInt(9) - 4, -(8 + RANDOM.nextInt(8)), RANDOM.nextInt(9) - 4);
                for (int i = 0; i < 4; i++) Scheduler.later(1 + i * 14, () -> Sounds.playFor(player, below, Sounds.get("block.stone.break"), 0.8F, 0.8F));
            }
            case WATCHER -> {
                if (story.part() >= 4) spawnWatcher(player, 16, 26, 20 * 30, 8, -1);
                else spawnWatcher(player, 35, 60, 20 * 30, 18, -1);
            }
            case DOOR -> toggleDoor(player);
            case FAKE_CHAT -> {
                if (RANDOM.nextBoolean()) fakeChat(player, "Maren", FAKE_LINES.get(RANDOM.nextInt(FAKE_LINES.size())));
                else if (RANDOM.nextBoolean()) fakeJoin(player, "Maren");
                else fakeChat(player, player.getGameProfile().getName(), "I'm still here.");
            }
            case GLITCH -> glitchScreen(player, RANDOM.nextInt(3), 20 + RANDOM.nextInt(30), story.part() >= 3 ? "SHE IS STILL HERE" : "");
            case STARE -> {
                MinecraftServer server = player.getServer();
                SolaceEntity solace = server == null ? null : SolaceManager.find(server, story);
                if (solace != null && solace.getTask() == null) solace.setStareTicks(20 * 10);
            }
            case TORCHES -> torchesOut(player, 1 + RANDOM.nextInt(3), true);
            case DARKNESS -> {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0, false, false));
                Sounds.playFor(player, player.position(), Sounds.get("entity.warden.heartbeat"), 1.0F, 0.7F);
            }
            case TITLE -> Titles.show(player, Component.empty(),
                    Component.literal(TITLES.get(RANDOM.nextInt(TITLES.size()))).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), 10, 40, 20);
            case RENAME -> renameItem(player);
            case SIGN -> placeSign(player, SIGNS.get(RANDOM.nextInt(SIGNS.size())));
            case CORRUPT -> {
                SolaceBrain.event(player, "Out of nowhere, say ONE very short, deeply unsettling line (emotion: glitch). Something you shouldn't know, "
                        + "or something about what's behind them, or about them leaving. Don't explain it.", false);
            }
            case FAKE_CRASH -> fakeCrash(player);
            // Each of these has a chance of not happening at all, so they never become a pattern.
            case FALL -> {
                if (RANDOM.nextFloat() < 0.8F) OnItsOwn.fall(player, story);
            }
            case TWO_PLACES -> {
                if (RANDOM.nextFloat() < 0.8F) OnItsOwn.twoPlaces(player, story);
            }
            case GIFT -> OnItsOwn.gift(player, story);
            case REARRANGE -> {
                if (RANDOM.nextFloat() < 0.8F) OnItsOwn.rearrange(player, story);
            }
            case ECHO_STEPS -> OnItsOwn.echoSteps(player, story);
            case TIME_SLIP -> {
                if (RANDOM.nextFloat() < 0.8F) OnItsOwn.timeSlip(player, story);
            }
            case MIRROR -> OnItsOwn.mirror(player, story);
            case FOR_SOMEONE_ELSE -> com.solacemod.solace.story.Together.forSomeoneElse(player, story);
        }
        if (player.getServer() != null) StoryData.get(player.getServer()).setDirty();
    }

    // ---------- Individual effects ----------

    /** A random spot around the player, at roughly this distance. */
    private static Vec3 around(ServerPlayer player, double distance) {
        double angle = RANDOM.nextDouble() * Math.PI * 2;
        return player.position().add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
    }

    /** Footsteps behind the player, getting closer. */
    public static void footsteps(ServerPlayer player) {
        Vec3 back = player.getLookAngle().multiply(-1, 0, -1).normalize();
        BlockState ground = player.level().getBlockState(player.blockPosition().below());
        var step = ground.getSoundType().getStepSound();
        int steps = 4 + RANDOM.nextInt(3);
        for (int i = 0; i < steps; i++) {
            double dist = 6 - i * 0.8;
            Scheduler.later(1 + i * 9, () -> {
                if (!player.isRemoved()) Sounds.playFor(player, player.position().add(back.scale(Math.max(1.5, dist))), step, 0.6F, 1.0F);
            });
        }
    }

    /** Spawns the Watcher somewhere the player might glimpse it. Returns null if no spot was found. */
    @Nullable
    public static WatcherEntity spawnWatcher(ServerPlayer player, int minDist, int maxDist, int lifeTicks, int vanishDistance, int fragmentIndex) {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4) look = new Vec3(1, 0, 0);
        look = look.normalize();
        for (int attempt = 0; attempt < 16; attempt++) {
            // Somewhere off to the side of where the player is looking: seen from the corner of their eye.
            double angle = (RANDOM.nextBoolean() ? 1 : -1) * (0.5 + RANDOM.nextDouble() * 0.7);
            double dist = minDist + RANDOM.nextDouble() * (maxDist - minDist);
            double x = player.getX() + (look.x * Math.cos(angle) - look.z * Math.sin(angle)) * dist;
            double z = player.getZ() + (look.x * Math.sin(angle) + look.z * Math.cos(angle)) * dist;
            BlockPos column = BlockPos.containing(x, player.getY(), z);
            if (!level.hasChunkAt(column)) continue;
            BlockPos spot = findStandingSpot(level, column, player);
            if (spot == null) continue;
            WatcherEntity watcher = ModEntities.WATCHER.get().create(level);
            if (watcher == null) return null;
            watcher.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
            watcher.configure(player, lifeTicks, vanishDistance, fragmentIndex, null);
            level.addFreshEntity(watcher);
            // A faint ringing in your ears, only for you, as it appears somewhere out there.
            Sounds.playFor(player, player.position(), Sounds.get("solace:watcher_near"), 0.6F, 1.0F);
            return watcher;
        }
        return null;
    }

    /** Spawns the Watcher near a chosen point (used to lead the player somewhere). */
    @Nullable
    public static WatcherEntity spawnWatcherAt(ServerPlayer player, Vec3 near, int lifeTicks, int vanishDistance) {
        ServerLevel level = player.serverLevel();
        for (int attempt = 0; attempt < 10; attempt++) {
            BlockPos column = BlockPos.containing(near.x + RANDOM.nextInt(7) - 3, player.getY(), near.z + RANDOM.nextInt(7) - 3);
            if (!level.hasChunkAt(column)) continue;
            BlockPos spot = findStandingSpot(level, column, player);
            if (spot == null) continue;
            WatcherEntity watcher = ModEntities.WATCHER.get().create(level);
            if (watcher == null) return null;
            watcher.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
            watcher.configure(player, lifeTicks, vanishDistance, -1, null);
            level.addFreshEntity(watcher);
            return watcher;
        }
        return null;
    }

    @Nullable
    private static BlockPos findStandingSpot(ServerLevel level, BlockPos column, ServerPlayer player) {
        if (level.canSeeSky(player.blockPosition())) {
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (Math.abs(top.getY() - player.getBlockY()) < 24 && standable(level, top)) return top;
        }
        for (int dy = 6; dy >= -6; dy--) {
            BlockPos p = new BlockPos(column.getX(), player.getBlockY() + dy, column.getZ());
            if (standable(level, p)) return p;
        }
        return null;
    }

    private static boolean standable(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && level.getFluidState(p).isEmpty()
                && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP);
    }

    public static void fakeChat(ServerPlayer player, String name, String text) {
        if (!SolaceConfig.ALLOW_FAKE_MESSAGES.get()) return;
        player.sendSystemMessage(Component.literal("<" + name + "> " + text));
    }

    public static void fakeJoin(ServerPlayer player, String name) {
        if (!SolaceConfig.ALLOW_FAKE_MESSAGES.get()) return;
        player.sendSystemMessage(Component.translatable("multiplayer.player.joined", name).withStyle(ChatFormatting.YELLOW));
        Scheduler.later(60 + RANDOM.nextInt(60), () -> {
            if (!player.isRemoved()) player.sendSystemMessage(Component.translatable("multiplayer.player.left", name).withStyle(ChatFormatting.YELLOW));
        });
    }

    public static void glitchScreen(ServerPlayer player, int kind, int ticks, String message) {
        if (!SolaceConfig.ALLOW_SCREEN_EFFECTS.get()) return;
        PacketDistributor.sendToPlayer(player, new ModNetwork.GlitchPayload(kind, ticks, message));
        Sounds.playFor(player, player.position(), Sounds.get("block.beacon.deactivate"), 0.4F, 1.8F);
    }

    public static void fakeCrash(ServerPlayer player) {
        if (!SolaceConfig.ALLOW_FAKE_CRASH.get()) {
            glitchScreen(player, 0, 40, "");
            return;
        }
        PacketDistributor.sendToPlayer(player, new ModNetwork.FakeCrashPayload("java.lang.IllegalStateException: Player " + player.getGameProfile().getName() + " is not allowed to leave"));
    }

    /** Puts out torches the player isn't looking at. */
    public static void torchesOut(ServerPlayer player, int max, boolean onlyBehind) {
        if (!SolaceConfig.ALLOW_WORLD_CHANGES.get()) return;
        ServerLevel level = player.serverLevel();
        Vec3 view = player.getViewVector(1.0F);
        BlockPos origin = player.blockPosition();
        int removed = 0;
        for (int i = 0; i < 600 && removed < max; i++) {
            BlockPos p = origin.offset(RANDOM.nextInt(33) - 16, RANDOM.nextInt(9) - 4, RANDOM.nextInt(33) - 16);
            BlockState state = level.getBlockState(p);
            if (!state.is(Blocks.TORCH) && !state.is(Blocks.WALL_TORCH) && !state.is(Blocks.SOUL_TORCH) && !state.is(Blocks.SOUL_WALL_TORCH)) continue;
            Vec3 to = Vec3.atCenterOf(p).subtract(player.getEyePosition()).normalize();
            if (onlyBehind && view.dot(to) > 0.2) continue;
            level.removeBlock(p, false);
            Sounds.playFor(player, Vec3.atCenterOf(p), Sounds.get("block.candle.extinguish"), 1.0F, 0.8F);
            removed++;
        }
    }

    static void toggleDoor(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = player.blockPosition();
        for (int i = 0; i < 400; i++) {
            BlockPos p = origin.offset(RANDOM.nextInt(25) - 12, RANDOM.nextInt(7) - 3, RANDOM.nextInt(25) - 12);
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof DoorBlock door && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                    && !state.is(Blocks.IRON_DOOR)) {
                door.setOpen(null, level, state, p, !door.isOpen(state));
                return;
            }
        }
        // No doors around: just the sound of one.
        Sounds.playFor(player, around(player, 8), Sounds.get("block.wooden_door.open"), 1.0F, 0.9F);
    }

    private static void renameItem(ServerPlayer player) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && !s.has(DataComponents.CUSTOM_NAME) && Fragments.indexOf(s) < 0) candidates.add(i);
        }
        if (candidates.isEmpty()) return;
        ItemStack stack = player.getInventory().getItem(candidates.get(RANDOM.nextInt(candidates.size())));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(RENAMES.get(RANDOM.nextInt(RENAMES.size())))
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    /** Places a sign behind the player, facing them. Returns false if it couldn't (world changes off, or no room). */
    public static boolean placeSign(ServerPlayer player, String[] lines) {
        if (!SolaceConfig.ALLOW_WORLD_CHANGES.get()) return false;
        ServerLevel level = player.serverLevel();
        Vec3 back = player.getLookAngle().multiply(-1, 0, -1).normalize();
        for (int attempt = 0; attempt < 10; attempt++) {
            double dist = 4 + RANDOM.nextInt(4);
            BlockPos column = BlockPos.containing(player.position().add(back.scale(dist)).add(RANDOM.nextInt(3) - 1, 0, RANDOM.nextInt(3) - 1));
            for (int dy = 2; dy >= -2; dy--) {
                BlockPos p = column.above(dy);
                if (!level.getBlockState(p).canBeReplaced() || !level.getBlockState(p).getFluidState().isEmpty()) continue;
                if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) continue;
                Vec3 toPlayer = player.position().subtract(Vec3.atCenterOf(p));
                float yaw = (float) (Mth.atan2(toPlayer.z, toPlayer.x) * (180F / Math.PI)) - 90F;
                int rotation = Mth.floor(yaw * 16.0F / 360.0F + 0.5) & 15; // yaw already points from the sign toward the player
                BlockState sign = Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation);
                level.setBlock(p, sign, Block.UPDATE_ALL);
                BlockEntity be = level.getBlockEntity(p);
                if (be instanceof SignBlockEntity signBe) {
                    SignText text = new SignText();
                    for (int i = 0; i < Math.min(4, lines.length); i++) {
                        text = text.setMessage(i, Component.literal(lines[i]).withStyle(ChatFormatting.DARK_RED));
                    }
                    signBe.setText(text, true);
                    signBe.setWaxed(true);
                    signBe.setChanged();
                    level.sendBlockUpdated(p, sign, sign, Block.UPDATE_ALL);
                }
                return true;
            }
        }
        return false;
    }
}
