package com.solacemod.solace.story;

import com.solacemod.solace.ai.BrainPool;
import com.solacemod.solace.config.SolaceConfig;
import com.solacemod.solace.util.Sounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Small hints when a player seems stuck: a page lying on the ground that they walked past, or Maren's house that
 * they never went to. Every hint writes a NUDGE line to the playtest log.
 *
 * ALL THE TIMINGS BELOW ARE GUESSES. Nobody has played the story yet. After the playtest, check in the log whether
 * the hints came too early (annoying) or too late (the player had already given up), and change the numbers here.
 * They are real seconds, not story minutes: looking for a page doesn't get quicker at a higher storySpeed.
 */
public final class Nudges {
    /** A page on the ground nobody has picked up: first rustle after this many seconds. (The page goes into the pocket by itself after 4 minutes.) */
    private static final int PAGE_FIRST_SECONDS = 60;
    /** ...and a second, louder rustle after this many seconds. */
    private static final int PAGE_SECOND_SECONDS = 150;
    /** Pages farther away than this get no rustle (the 4 minute pocket still catches them). */
    private static final int PAGE_HEARING_BLOCKS = 48;
    /** Maren's house exists but the player hasn't gone in: Solace warns them away from it after this many seconds... */
    private static final int HOUSE_FIRST_SECONDS = 5 * 60;
    /** ...and again after this many. */
    private static final int HOUSE_SECOND_SECONDS = 15 * 60;
    /** A chapter is over by time but still waiting for pages: write STUCK in the playtest log after this many seconds... */
    private static final int STUCK_LOG_SECONDS = 5 * 60;
    /** ...and slip the missing page into the pocket after this many. This should never happen. If it does, the log says where. */
    private static final int STUCK_RESCUE_SECONDS = 10 * 60;

    /** When (server tick) each thing was first noticed. Not saved: a new session starts the clocks again. */
    private static final Map<String, Long> SINCE = new HashMap<>();
    /** How many hints each thing has had already. */
    private static final Map<String, Integer> GIVEN = new HashMap<>();

    private Nudges() {
    }

    /** Once per second per player, while the story is running and has no ending yet. */
    static void tick(ServerPlayer player, PlayerStory story, int pagesToLeave, boolean timeToLeave) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        long now = server.getTickCount();
        boolean hints = SolaceConfig.STORY_NUDGES.get();
        if (hints) {
            tickPages(player, story, now);
            tickHouse(player, story, now);
        }
        tickStuck(player, story, now, pagesToLeave, timeToLeave);
    }

    // ---------- A page on the ground ----------

    private static void tickPages(ServerPlayer player, PlayerStory story, long now) {
        for (int idx : story.fragmentDeliveredAt.keySet()) {
            if (story.fragmentsFound.contains(idx)) continue;
            String key = player.getUUID() + ":page" + idx;
            ItemEntity page = findPage(player, idx);
            if (page == null) {
                // Not on the ground near the player (still with the Watcher, too far away, or picked up).
                SINCE.remove(key);
                continue;
            }
            long since = SINCE.computeIfAbsent(key, k -> now);
            int given = GIVEN.getOrDefault(key, 0);
            long seconds = (now - since) / 20;
            if (given == 0 && seconds >= PAGE_FIRST_SECONDS) {
                rustle(player, story, page, idx, 2.5F, StoryText.NUDGE_PAGE_LINE);
                GIVEN.put(key, 1);
            } else if (given == 1 && seconds >= PAGE_SECOND_SECONDS) {
                rustle(player, story, page, idx, 3.5F, StoryText.NUDGE_PAGE_AGAIN_LINE);
                GIVEN.put(key, 2);
            }
        }
    }

    @Nullable
    private static ItemEntity findPage(ServerPlayer player, int idx) {
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(PAGE_HEARING_BLOCKS))) {
            if (Fragments.indexOf(item.getItem()) == idx) return item;
        }
        return null;
    }

    /** A page-turn sound from where the page is lying, so the player can hear which way to go. */
    private static void rustle(ServerPlayer player, PlayerStory story, ItemEntity page, int idx, float volume, String line) {
        Sounds.playFor(player, page.position(), Sounds.get("item.book.page_turn"), volume, 0.8F);
        player.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        PlaytestLog.log(story, player, "NUDGE", "page " + (idx + 1) + " is lying " + Math.round(page.distanceTo(player)) + " blocks away: a rustle");
    }

    // ---------- Maren's house ----------

    private static void tickHouse(ServerPlayer player, PlayerStory story, long now) {
        if (story.chapter != 5 || story.houseCorner == null || story.beatsDone.contains("house_visited")) return;
        if (player.level().dimension() != Level.OVERWORLD) return;
        String key = player.getUUID() + ":house";
        long since = SINCE.computeIfAbsent(key, k -> now);
        int given = GIVEN.getOrDefault(key, 0);
        long seconds = (now - since) / 20;
        if ((given == 0 && seconds >= HOUSE_FIRST_SECONDS) || (given == 1 && seconds >= HOUSE_SECOND_SECONDS)) {
            Vec3 center = Vec3.atBottomCenterOf(story.houseCorner.offset(3, 0, 3));
            int dist = (int) Math.round(player.position().distanceTo(center));
            if (dist < 12) return; // practically on the doorstep already
            GIVEN.put(key, given + 1);
            String dir = direction(player.position(), center);
            int roughly = Math.max(10, Math.round(dist / 10F) * 10);
            boolean ai = AiService.configured() && AiService.hasRoomFor(BrainPool.Kind.STORY);
            if (ai) {
                // The fear points the way: it tells them NOT to go there.
                SolaceBrain.event(player, String.format(Locale.ROOT, StoryText.NUDGE_HOUSE, dir, roughly), true);
            } else {
                player.sendSystemMessage(Component.literal(String.format(Locale.ROOT, StoryText.NUDGE_HOUSE_LINE, dir))
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
            PlaytestLog.log(story, player, "NUDGE", "house not visited after " + seconds / 60 + " minutes: it's " + dist + " blocks to the "
                    + dir + (ai ? " (Solace warned them away)" : " (grey line, no AI)"));
        }
    }

    /** "north", "south-east"... from one spot to another. In Minecraft, north is -Z and east is +X. */
    static String direction(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, 90 = east
        String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        int i = (int) Math.floorMod(Math.round(angle / 45.0), 8);
        return names[i];
    }

    // ---------- Safety net ----------

    private static void tickStuck(ServerPlayer player, PlayerStory story, long now, int pagesToLeave, boolean timeToLeave) {
        String key = player.getUUID() + ":stuck" + story.chapter;
        if (!timeToLeave || story.fragmentsFound.size() >= pagesToLeave) {
            SINCE.remove(key);
            GIVEN.remove(key);
            return;
        }
        long since = SINCE.computeIfAbsent(key, k -> now);
        int given = GIVEN.getOrDefault(key, 0);
        long seconds = (now - since) / 20;
        if (given == 0 && seconds >= STUCK_LOG_SECONDS) {
            GIVEN.put(key, 1);
            PlaytestLog.log(story, player, "STUCK", "chapter " + story.chapter + " is over by time but waiting for pages (" + story.fragmentsFound.size()
                    + " of " + pagesToLeave + " found) for " + seconds / 60 + " minutes");
        } else if (given == 1 && seconds >= STUCK_RESCUE_SECONDS) {
            GIVEN.put(key, 2);
            for (int i = 0; i < pagesToLeave; i++) {
                if (story.fragmentsFound.contains(i)) continue;
                PlaytestLog.log(story, player, "NUDGE", "stuck for " + seconds / 60 + " minutes: page " + (i + 1) + " slipped into the pocket");
                StoryDirector.deliverFragment(player, story, i, StoryDirector.Delivery.POCKET);
                break;
            }
            SINCE.remove(key);
            GIVEN.remove(key);
        }
    }

    public static void forget(UUID id) {
        String prefix = id + ":";
        SINCE.keySet().removeIf(k -> k.startsWith(prefix));
        GIVEN.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public static void reset() {
        SINCE.clear();
        GIVEN.clear();
    }
}
