package com.solacemod.solace.story;

import com.solacemod.solace.config.SolaceConfig;
import com.solacemod.solace.entity.SolaceEntity;
import com.solacemod.solace.story.StoryText.Tone;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Solace notices what you build (1.13.0): a house, a farm, a tower, a wall, a mine, or "something".
 * It only ever looks: nothing here places, breaks or changes a block. It counts the blocks each player places (and breaks
 * underground) close together. Once enough pile up and the player pauses, it decides what the build is, stops and looks at it,
 * remembers it, and talks about it the next time it would talk on its own anyway (ambient chatter), so it costs no extra AI
 * requests. Written lines when no AI answers. A "something" that later gets a bed or crops can be noticed again as a house or farm.
 * All the writing is in StoryText. The numbers below are guesses until the playtest.
 */
public final class Builds {
    /** Blocks placed close together before Solace notices a build. */
    public static final int BUILD_BLOCKS = 40;
    /** Stone broken underground close together before Solace notices a mine. */
    public static final int MINE_BLOCKS = 64;
    /** Seconds without placing (or digging) there before Solace decides what the build is: a house isn't a house until the door is in. */
    public static final int PAUSE_SECONDS = 30;
    /** More blocks needed on a "something" before Solace looks at it again (it may have become a house or a farm). */
    private static final int MORE_BLOCKS = 10;
    /** Blocks from where a pile started that still count as the same build. */
    public static final int PILE_RADIUS = 12;
    /** A build of the same kind this close to one already noticed isn't noticed again. */
    public static final int SAME_PLACE = 24;
    /** Builds remembered per player (the oldest is forgotten first). */
    public static final int REMEMBERED = 12;
    /** Piles being counted at once per player (the oldest is dropped first). */
    private static final int PILES = 8;
    /** Minutes a noticed build waits for Solace's next turn to talk on its own. After that it's only remembered. */
    public static final int TALK_WITHIN_MINUTES = 15;
    /** Seconds Solace stops and looks at a build it just noticed. */
    public static final int LOOK_SECONDS = 6;
    /** Builds kept in Solace's long-term memory (its facts). Few, so they never crowd out what it knows about the player. */
    private static final int BUILD_FACTS = 3;
    /** Crops planted before a build counts as a farm. */
    private static final int FARM_CROPS = 8;

    private static final String FACT_START = "the player built ";

    public static final List<String> KINDS = List.of("house", "farm", "tower", "wall", "mine", "something");

    /** Blocks one player is placing (or digging) close together, not noticed yet. Kept in memory only. */
    private static final class Pile {
        final String dim;
        final BlockPos start;
        int minX, minY, minZ, maxX, maxY, maxZ;
        int placed, dug, crops;
        @Nullable BlockPos shelter; // a bed or door that was placed, to check for a roof
        long lastTick; // server tick of the last block placed or dug here
        @Nullable String noticedAs; // what Solace decided it is, or null
        int placedAtNotice;

        Pile(String dim, BlockPos at) {
            this.dim = dim;
            this.start = at;
            minX = maxX = at.getX();
            minY = maxY = at.getY();
            minZ = maxZ = at.getZ();
        }

        boolean holds(String d, BlockPos p) {
            return dim.equals(d) && Math.abs(p.getX() - start.getX()) <= PILE_RADIUS && Math.abs(p.getZ() - start.getZ()) <= PILE_RADIUS
                    && Math.abs(p.getY() - start.getY()) <= 32;
        }

        void grow(BlockPos p) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX());
            maxY = Math.max(maxY, p.getY());
            maxZ = Math.max(maxZ, p.getZ());
        }

        BlockPos middle() {
            return new BlockPos((minX + maxX) / 2, minY, (minZ + maxZ) / 2);
        }
    }

    private static final Map<UUID, List<Pile>> PILES_BY_PLAYER = new HashMap<>();

    private Builds() {
    }

    public static void reset() {
        PILES_BY_PLAYER.clear();
    }

    public static void forget(UUID id) {
        PILES_BY_PLAYER.remove(id);
    }

    private static boolean on(PlayerStory story) {
        return SolaceConfig.NOTICE_BUILDS.get() && story.met && story.solaceShouldExist();
    }

    // ---------- Counting ----------

    /** A player placed a block. */
    public static void onPlaced(ServerPlayer player, PlayerStory story, BlockPos pos, BlockState state) {
        if (!on(story)) return;
        Pile pile = pileAt(player, pos);
        pile.placed++;
        pile.grow(pos);
        pile.lastTick = tickNow(player);
        Block block = state.getBlock();
        if (block instanceof BushBlock && block instanceof BonemealableBlock) pile.crops++; // wheat, carrots, potatoes, beetroot, stems, saplings...
        if (block instanceof BedBlock || block instanceof DoorBlock) pile.shelter = pos;
    }

    /** A player broke a block. Only stone deep in the ground counts (digging a mine). */
    public static void onBroken(ServerPlayer player, PlayerStory story, BlockPos pos, BlockState state) {
        if (!on(story)) return;
        Level level = player.level();
        boolean stone = state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.STONE_ORE_REPLACEABLES)
                || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
        if (!stone || level.canSeeSky(pos.above())) return;
        Pile pile = pileAt(player, pos);
        pile.dug++;
        pile.grow(pos);
        pile.lastTick = tickNow(player);
    }

    private static long tickNow(ServerPlayer player) {
        return player.getServer() == null ? 0 : player.getServer().getTickCount();
    }

    /** Once a second: piles the player has paused on get looked at. */
    public static void tick(ServerPlayer player, PlayerStory story) {
        List<Pile> piles = PILES_BY_PLAYER.get(player.getUUID());
        if (piles == null || !on(story)) return;
        long now = tickNow(player);
        for (Pile p : piles) {
            if (now - p.lastTick < PAUSE_SECONDS * 20L) continue;
            String kind = null;
            if (p.noticedAs == null) {
                if (p.dug >= MINE_BLOCKS && p.dug > p.placed) kind = "mine";
                else if (p.placed >= BUILD_BLOCKS) kind = kind(player.serverLevel(), p);
            } else if (p.noticedAs.equals("something") && p.placed - p.placedAtNotice >= MORE_BLOCKS) {
                String again = kind(player.serverLevel(), p);
                p.placedAtNotice = p.placed; // look again only after more blocks
                if (!again.equals("something")) kind = again;
            }
            if (kind == null) continue;
            p.noticedAs = kind;
            p.placedAtNotice = p.placed;
            notice(player, story, kind, p.middle(), kind.equals("mine") ? p.dug : p.placed);
            return; // one at a time
        }
    }

    private static Pile pileAt(ServerPlayer player, BlockPos pos) {
        String dim = player.level().dimension().location().toString();
        List<Pile> piles = PILES_BY_PLAYER.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        for (Pile p : piles) if (p.holds(dim, pos)) return p;
        Pile pile = new Pile(dim, pos.immutable());
        piles.add(pile);
        while (piles.size() > PILES) piles.remove(0);
        return pile;
    }

    /** What the build looks like. Kept to a few kinds on purpose: a wrong guess spoils the mood more than a vague one. */
    private static String kind(ServerLevel level, Pile p) {
        int height = p.maxY - p.minY + 1;
        int wide = Math.max(p.maxX - p.minX, p.maxZ - p.minZ) + 1;
        int thin = Math.min(p.maxX - p.minX, p.maxZ - p.minZ) + 1;
        if (p.crops >= FARM_CROPS) return "farm";
        if (p.shelter != null && roofed(level, p.shelter)) return "house";
        if (height >= 10 && height >= wide * 2.5) return "tower";
        if (thin <= 2 && wide >= 12 && height >= 2 && height <= 8) return "wall";
        return "something";
    }

    /** Something solid over the bed or door, or right next to it: a roof. */
    private static boolean roofed(ServerLevel level, BlockPos at) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 2; dy <= 7; dy++) {
                    if (level.getBlockState(at.offset(dx, dy, dz)).isSolidRender(level, at.offset(dx, dy, dz))) return true;
                }
            }
        }
        return false;
    }

    // ---------- Noticing ----------

    /** Solace noticed a build: it looks, remembers, and has something to say at its next turn to talk. */
    static void notice(ServerPlayer player, PlayerStory story, String kind, BlockPos at, int blocks) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        String dim = player.level().dimension().location().toString();
        if (nearBuild(story, dim, at, SAME_PLACE, kind)) {
            PlaytestLog.log(story, player, "BUILD", kind + " at " + at.toShortString() + " (" + blocks + " blocks): already noticed one here, nothing said");
            return;
        }
        story.builds.add(kind + "|" + dim + "|" + at.getX() + "|" + at.getY() + "|" + at.getZ());
        while (story.builds.size() > REMEMBERED) story.builds.remove(0);
        if (!kind.equals("something")) {
            List<String> old = story.facts.stream().filter(f -> f.startsWith(FACT_START)).toList();
            for (int i = 0; i <= old.size() - BUILD_FACTS; i++) story.facts.remove(old.get(i));
            story.addFact(FACT_START + StoryText.buildName(kind) + " near x " + at.getX() + ", z " + at.getZ());
        }
        story.addEvent("you watched the player build " + StoryText.buildName(kind) + " (" + blocks + " blocks)");
        story.pendingBuild = kind;
        story.pendingBuildBlocks = blocks;
        story.pendingBuildTick = server.getTickCount();
        StoryData.get(server).setDirty();
        PlaytestLog.log(story, player, "BUILD", kind + " at " + at.toShortString() + " (" + blocks + " blocks, " + story.tone().name().toLowerCase(Locale.ROOT) + ")");

        SolaceEntity solace = SolaceManager.find(server, story);
        if (solace != null && !story.bossActive && solace.getTarget() == null && solace.level() == player.level() && !solace.isHollow() && solace.distanceTo(player) < 48) {
            solace.stareAtSpot(Vec3.atCenterOf(at).add(0, 1, 0), LOOK_SECONDS * 20);
        }
        // With talking on its own switched off, the written line comes straight after the look (no AI).
        if (!SolaceConfig.AMBIENT_CHATTER.get()) Scheduler.later(LOOK_SECONDS * 20, () -> {
            if (!player.isRemoved() && story.pendingBuild != null) speak(player, story);
        });
    }

    /** The build Solace still wants to talk about, or null (none, or it waited too long). */
    @Nullable
    public static String pending(PlayerStory story, long now) {
        if (story.pendingBuild == null) return null;
        if (now - story.pendingBuildTick > TALK_WITHIN_MINUTES * 1200L) {
            story.pendingBuild = null;
            return null;
        }
        return story.pendingBuild;
    }

    /** Solace's turn to talk on its own: it talks about the build (background request, written line if no AI answers). */
    public static void speak(ServerPlayer player, PlayerStory story) {
        String kind = story.pendingBuild;
        if (kind == null) return;
        story.pendingBuild = null;
        Tone tone = story.tone();
        SolaceBrain.comment(player, StoryText.build(kind, tone, story.pendingBuildBlocks), StoryText.buildLine(kind, tone));
    }

    /** True if Solace has noticed a build (of this kind, or any kind if null) within this many blocks. */
    public static boolean nearBuild(PlayerStory story, String dim, BlockPos at, int blocks, @Nullable String kind) {
        for (String b : story.builds) {
            String[] f = b.split("\\|");
            if (f.length != 5 || !f[1].equals(dim) || (kind != null && !f[0].equals(kind))) continue;
            try {
                int x = Integer.parseInt(f[2]), y = Integer.parseInt(f[3]), z = Integer.parseInt(f[4]);
                if (Math.abs(x - at.getX()) <= blocks && Math.abs(z - at.getZ()) <= blocks && Math.abs(y - at.getY()) <= blocks) return true;
            } catch (NumberFormatException ignored) {
                // a broken entry: skip it
            }
        }
        return false;
    }

    // ---------- Testing (/solace admin build) ----------

    public static final List<String> ADMIN_OPTIONS = List.of("list", "forget", "record", "say", "house", "farm", "tower", "wall", "mine", "something");

    /** Runs one test action. Returns what happened. */
    public static String admin(ServerPlayer player, PlayerStory story, String what, String arg) {
        String name = player.getGameProfile().getName();
        switch (what) {
            case "list" -> {
                return name + "'s noticed builds: " + (story.builds.isEmpty() ? "none" : String.join(", ", story.builds))
                        + (story.pendingBuild != null ? ". Waiting to talk about: " + story.pendingBuild : "");
            }
            case "forget" -> {
                story.builds.clear();
                story.pendingBuild = null;
                forget(player.getUUID());
                return "Forgot " + name + "'s builds.";
            }
            case "record" -> {
                // "x1 y1 z1 x2 y2 z2": every block in that box counts as placed by the player, one by one, as if they had built it.
                String[] n = arg.trim().split("\\s+");
                if (n.length != 6) throw new IllegalStateException("Give two corners: record x1 y1 z1 x2 y2 z2");
                int[] c = new int[6];
                try {
                    for (int i = 0; i < 6; i++) c[i] = Integer.parseInt(n[i]);
                } catch (NumberFormatException e) {
                    throw new IllegalStateException("Corners must be whole numbers.");
                }
                ServerLevel level = player.serverLevel();
                int count = 0;
                for (BlockPos p : BlockPos.betweenClosed(Math.min(c[0], c[3]), Math.min(c[1], c[4]), Math.min(c[2], c[5]),
                        Math.max(c[0], c[3]), Math.max(c[1], c[4]), Math.max(c[2], c[5]))) {
                    BlockState state = level.getBlockState(p);
                    if (state.isAir()) continue;
                    if (++count > 4096) throw new IllegalStateException("At most 4096 blocks.");
                    onPlaced(player, story, p.immutable(), state);
                }
                // As if they had paused a while ago: Solace looks at it within a second.
                List<Pile> piles = PILES_BY_PLAYER.get(player.getUUID());
                if (piles != null) for (Pile pile : piles) pile.lastTick -= PAUSE_SECONDS * 20L;
                return "Counted " + count + " blocks as placed by " + name + ".";
            }
            case "say" -> {
                String kind = story.pendingBuild;
                if (kind == null) throw new IllegalStateException("Nothing noticed to talk about.");
                speak(player, story);
                return "Solace talks about " + StoryText.buildName(kind) + ".";
            }
            default -> {
                if (!KINDS.contains(what)) throw new IllegalStateException("Options: " + String.join(" ", ADMIN_OPTIONS));
                // Pretends a build of this kind was just finished where the player stands (then "say", or wait for Solace's turn).
                notice(player, story, what, player.blockPosition(), BUILD_BLOCKS);
                return "Solace noticed a " + what + ".";
            }
        }
    }
}
