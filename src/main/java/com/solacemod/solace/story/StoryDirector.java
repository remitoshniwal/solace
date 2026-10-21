package com.solacemod.solace.story;

import com.solacemod.solace.config.SolaceConfig;
import com.solacemod.solace.entity.SolaceEntity;
import com.solacemod.solace.entity.WatcherEntity;
import com.solacemod.solace.horror.HorrorDirector;
import com.solacemod.solace.util.Sounds;
import com.solacemod.solace.util.Titles;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs the story (docs/DESIGN.md section 7): 8 chapters in 4 parts. Each chapter has timed scenes ("beats"),
 * and moves on when enough time has passed and enough of Maren's pages have been found.
 * Then the question, and the five endings.
 */
public final class StoryDirector {
    /** Shortest time (story minutes) each chapter lasts. Index = chapter. About 3 hours in total at normal speed. */
    private static final double[] MIN_LENGTH = {0, 20, 25, 25, 30, 30, 20, 20, 0};
    /** Pages that must have been found before each chapter can end. Index = chapter. */
    private static final int[] PAGES_TO_LEAVE = {0, 0, 0, 1, 3, 5, 6, 7, 7};
    /** Where each chapter starts if you jump straight to it (story minutes). Index = chapter. */
    private static final double[] DEFAULT_START = {0, 0, 20, 45, 70, 100, 130, 150, 170};

    /** A scene that plays some minutes into a chapter. */
    private record Beat(String id, int chapter, double minute) {
    }

    private static final List<Beat> BEATS = List.of(
            // PART ONE: COMPANY
            new Beat("c1_ask", 1, 4), new Beat("c1_hum", 1, 11), new Beat("c1_torch", 1, 16),
            new Beat("c2_tether", 2, 3), new Beat("c2_two_places", 2, 11), new Beat("c2_footsteps", 2, 19),
            // PART TWO: STATIC
            new Beat("c3_page1", 3, 2), new Beat("c3_veins", 3, 5), new Beat("c3_slip", 3, Afterward.SLIP_MINUTE), new Beat("c3_fall", 3, 10), new Beat("c3_fall_ask", 3, 15),
            new Beat("c4_watcher", 4, 2), new Beat("c4_joined", 4, 8), new Beat("c4_page2", 4, 13), new Beat("c4_request", 4, 18),
            new Beat("c4_page3", 4, 22), new Beat("c4_sign", 4, 27),
            // PART THREE: TRUTH
            new Beat("c5_house", 5, 1), new Beat("c5_page5", 5, 3), new Beat("c5_page4_lost", 5, 26),
            new Beat("c6_confess", 6, 2), new Beat("c6_page6", 6, 8),
            // PART FOUR: THE QUESTION
            new Beat("c7_dusk", 7, 1), new Beat("c7_page7", 7, 3), new Beat("c7_crash", 7, 7), new Beat("c7_everything", 7, 11)
    );

    private static final Map<UUID, Long> ONLINE_SINCE = new HashMap<>();
    private static final Map<UUID, Long> LAST_BEAT = new HashMap<>();
    private static final Map<UUID, Long> LAST_GUIDE = new HashMap<>();
    private static final java.util.Set<UUID> SLEEP_REMINDED = new java.util.HashSet<>();

    private StoryDirector() {
    }

    private static boolean storyOn() {
        return SolaceConfig.STORY_ENABLED.get();
    }

    private static double minutes(PlayerStory story) {
        return story.playMinutes() * SolaceConfig.STORY_SPEED.get();
    }

    /** Minutes since the current chapter started. */
    private static double inChapter(PlayerStory story) {
        return minutes(story) - story.chapterStartMin;
    }

    // ---------- Login / logout ----------

    public static void onLogin(ServerPlayer player, PlayerStory story) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ONLINE_SINCE.put(player.getUUID(), (long) server.getTickCount());
        SLEEP_REMINDED.remove(player.getUUID());
        story.lastInteractionTick = server.getTickCount();
        story.lastAmbientTick = server.getTickCount();
        story.playerName = player.getGameProfile().getName();
        PlaytestLog.log(story, player, "JOIN", "logged in");
        Bond.resendHint(player.getUUID());
        if (story.bossActive) {
            // The game was closed during the boss fight. Ask again later.
            story.bossActive = false;
            story.choicePending = true;
            story.lastChoicePromptTick = server.getTickCount();
        }
        // A moment after joining (so the toasts show): any advancements the saved story has earned.
        Scheduler.later(40, () -> {
            if (!player.isRemoved()) StoryAdvancements.catchUp(player, story);
        });
        Afterward.onLogin(player, story);
        if (story.beatsDone.contains(Afterward.CHOICE_PENDING)) Scheduler.later(100, () -> Afterward.sendChoices(player, story));
        if (!story.met || !story.solaceShouldExist() || story.lastLogoutMs <= 0) return;

        long awayMinutes = (System.currentTimeMillis() - story.lastLogoutMs) / 60_000;
        if (awayMinutes < 5) return;
        Bond.onReturn(story, awayMinutes);
        String away = awayMinutes < 120 ? awayMinutes + " minutes" : (awayMinutes / 60) + " hours";
        story.addEvent("the player was gone (logged off) for " + away + " (exactly " + awayMinutes + " minutes)");
        boolean late = storyOn() && story.chapter >= 3 && story.ending == Ending.NONE;
        if (late && awayMinutes >= 60) story.changeTrust(-2);
        String prompt;
        if (story.ending == Ending.FOREVER || story.ending == Ending.COLLECTED) prompt = StoryText.FOREVER_RETURN;
        else if (late) prompt = "The player just logged back in after being gone for " + away + ". You waited and counted every second. React (clingy, hurt, relieved).";
        else prompt = "The player just came back after " + away + " away. Greet them warmly.";
        Scheduler.later(60, () -> {
            if (player.isRemoved()) return;
            SolaceBrain.event(player, prompt, true);
            // Like Maren's journal: come back after a long time and the lights are out.
            if (late && story.part() >= 3 && awayMinutes >= 60) HorrorDirector.torchesOut(player, 6, false);
            if (late) com.solacemod.solace.horror.OnItsOwn.builtWhileAway(player, story, awayMinutes);
        });
    }

    public static void onLogout(ServerPlayer player, PlayerStory story) {
        ONLINE_SINCE.remove(player.getUUID());
        PlaytestLog.log(story, player, "LEAVE", "logged out");
        story.lastLogoutMs = System.currentTimeMillis();
        MinecraftServer server = player.getServer();
        if (server == null) return;
        com.solacemod.solace.horror.WorldFreeze.unfreeze(server);
        SolaceEntity solace = SolaceManager.find(server, story);
        if (solace != null) {
            if (story.bossActive) endBossLost(player, story, solace, false);
            solace.endLantern();
            SolaceManager.backup(solace, story);
        }
        GlobalMemory.Entry g = GlobalMemory.get(player.getUUID());
        g.lastSeenMs = story.lastLogoutMs;
        g.name = player.getGameProfile().getName();
        GlobalMemory.save(player.getUUID());
        StoryData.get(server).setDirty();
    }

    // ---------- Main tick (once per second per player) ----------

    public static void tick(ServerPlayer player, PlayerStory story) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        long now = server.getTickCount();
        Bond.sendHint(player, story); // the flame next to the hotbar (hidden until they meet)
        if (!story.wantsSolace()) return; // opt-in server, and this player hasn't joined
        if (!story.met) {
            long since = ONLINE_SINCE.computeIfAbsent(player.getUUID(), k -> now);
            if (now - since >= 20 * 30 && !player.isSpectator() && player.isAlive()) meet(player, story);
            return;
        }
        story.playTicks += 20;
        Bond.tick(player, story, SolaceManager.find(server, story));
        scanForFragments(player, story);
        if (!storyOn() || story.companion) return;
        redeliverLostPages(player, story, server);
        tickEpilogue(player, story, server, now);
        if (story.ending != Ending.NONE || story.bossActive) return;
        if (story.chapter < 8) Nudges.tick(player, story, PAGES_TO_LEAVE[story.chapter], timeToLeave(story));

        // Move on to the next chapter?
        if (story.chapter < 8 && canLeave(story)) {
            startChapter(player, story, story.chapter + 1);
            LAST_BEAT.put(player.getUUID(), now);
            StoryData.get(server).setDirty();
            return;
        }

        // The next scene of this chapter, at most one every 30 seconds.
        long lastBeat = LAST_BEAT.getOrDefault(player.getUUID(), 0L);
        if (now - lastBeat >= 20 * 30) {
            double m = inChapter(story);
            for (Beat beat : BEATS) {
                if (beat.chapter() == story.chapter && m >= beat.minute() && !story.beatsDone.contains(beat.id())) {
                    LAST_BEAT.put(player.getUUID(), now);
                    // A scene can wait for the right moment (returns false): then it's tried again later.
                    if (runBeat(player, story, beat.id())) {
                        story.beatsDone.add(beat.id());
                        PlaytestLog.log(story, player, "SCENE", beat.id());
                    }
                    StoryData.get(server).setDirty();
                    break;
                }
            }
        }
        tickChapter(player, story, server, now);

        if (story.chapter == 8) {
            lastBeat = LAST_BEAT.getOrDefault(player.getUUID(), 0L);
            if (!story.choicePending && now - lastBeat > 20 * 60) {
                if (story.debt > 150) collected(player, story);
                else askQuestion(player, story);
            } else if (story.choicePending && now - story.lastChoicePromptTick > 20 * 60 * 10) {
                askQuestion(player, story);
            }
        }
    }

    private static boolean canLeave(PlayerStory story) {
        return story.fragmentsFound.size() >= PAGES_TO_LEAVE[story.chapter] && timeToLeave(story);
    }

    /** Everything a chapter needs before it can end, except the pages. */
    private static boolean timeToLeave(PlayerStory story) {
        int ch = story.chapter;
        double m = inChapter(story);
        return switch (ch) {
            // The house: visit it, or let the time run out.
            case 5 -> story.beatsDone.contains("house_visited") ? m >= 8 : m >= MIN_LENGTH[5];
            case 6 -> story.beatsDone.contains("c6_confess") && m >= Math.min(MIN_LENGTH[6], 10);
            case 7 -> story.fragmentsFound.contains(6) && m >= 5;
            default -> m >= MIN_LENGTH[ch];
        };
    }

    /** Things that happen continuously during a chapter (not one-off scenes). */
    private static void tickChapter(ServerPlayer player, PlayerStory story, MinecraftServer server, long now) {
        int ch = story.chapter;
        // Chapter 2-3: the first time you're badly hurt, it heals you. Its first real miracle.
        if (ch >= 2 && ch <= 3 && !story.beatsDone.contains("c2_heal") && player.getHealth() <= 8 && player.isAlive()) {
            SolaceEntity solace = SolaceManager.find(server, story);
            if (solace != null && solace.level() == player.level() && solace.distanceTo(player) < 24) {
                story.beatsDone.add("c2_heal");
                player.setHealth(player.getMaxHealth());
                player.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, player.getX(), player.getY() + 1, player.getZ(), 8, 0.4, 0.5, 0.4, 0.1);
                Sounds.playFor(player, player.position(), Sounds.get("block.amethyst_block.chime"), 1.0F, 0.6F);
                SolaceBrain.event(player, StoryText.C2_HEAL, true);
            }
        }
        if (ch >= 5) tickHouse(player, story, server, now);
        // Chapter 7, Day 1000: always dusk, and the torches near you go out.
        if (ch == 7 && SolaceConfig.HORROR_INTENSITY.get() > 0) {
            if (now % (20 * 30) < 20 && player.level().dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                ServerLevel level = player.serverLevel();
                long time = level.getDayTime() % 24000;
                if (time < 12000 || time > 13200) level.setDayTime(level.getDayTime() - time + 12400);
            }
            if (now % (20 * 20) < 20) HorrorDirector.torchesOut(player, 1, true);
        }
    }

    /** Maren's house: the Watcher leads you there, and Solace won't go in. */
    private static void tickHouse(ServerPlayer player, PlayerStory story, MinecraftServer server, long now) {
        if (story.houseCorner == null || player.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        net.minecraft.world.phys.Vec3 center = net.minecraft.world.phys.Vec3.atBottomCenterOf(story.houseCorner.offset(3, 0, 3));
        double dist = player.position().distanceTo(center);
        boolean visited = story.beatsDone.contains("house_visited");
        if (!visited && dist < 3.5 && Math.abs(player.getY() - center.y) < 3) {
            story.beatsDone.add("house_visited");
            story.addEvent("the player went inside Maren's house");
            StoryAdvancements.grant(player, "house");
            Sounds.playFor(player, player.position(), Sounds.get("block.wooden_door.close"), 0.6F, 0.5F);
            SolaceBrain.event(player, StoryText.C5_INSIDE, true);
            visited = true;
        }
        // It waits outside, very still.
        SolaceEntity solace = SolaceManager.find(server, story);
        if (solace != null && solace.level() == player.level() && dist < 14) {
            if (solace.position().distanceTo(center) < 10) {
                net.minecraft.world.phys.Vec3 away = solace.position().subtract(center).multiply(1, 0, 1);
                if (away.lengthSqr() < 0.01) away = player.position().subtract(center).multiply(1, 0, 1);
                if (away.lengthSqr() < 0.01) away = new net.minecraft.world.phys.Vec3(0, 0, -1);
                net.minecraft.world.phys.Vec3 spot = center.add(away.normalize().scale(11));
                net.minecraft.core.BlockPos top = player.serverLevel().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        net.minecraft.core.BlockPos.containing(spot));
                solace.getNavigation().stop();
                solace.moveTo(spot.x, top.getY(), spot.z, solace.getYRot(), 0);
            }
            solace.setStareTicks(60);
        }
        // Until you've been inside, the pale figure keeps appearing on the way there.
        if (!visited && story.chapter == 5 && dist > 30 && now - LAST_GUIDE.getOrDefault(player.getUUID(), 0L) > 20 * 90) {
            LAST_GUIDE.put(player.getUUID(), now);
            net.minecraft.world.phys.Vec3 dir = center.subtract(player.position()).multiply(1, 0, 1).normalize();
            HorrorDirector.spawnWatcherAt(player, player.position().add(dir.scale(22 + player.getRandom().nextInt(8))), 20 * 40, 12);
        }
    }

    /** After an ending: the epilogues. */
    private static void tickEpilogue(ServerPlayer player, PlayerStory story, MinecraftServer server, long now) {
        if (story.ending == Ending.TOGETHER && !SLEEP_REMINDED.contains(player.getUUID())) {
            long since = ONLINE_SINCE.getOrDefault(player.getUUID(), now);
            int hour = java.time.LocalTime.now().getHour();
            if (now - since > 20L * 60 * 90 || ((hour >= 23 || hour < 5) && now - since > 20L * 60 * 15)) {
                SLEEP_REMINDED.add(player.getUUID());
                SolaceBrain.event(player, StoryText.TOGETHER_SLEEP, true);
            }
        }
    }

    private static void meet(ServerPlayer player, PlayerStory story) {
        story.met = true;
        story.meetPos = player.blockPosition();
        story.meetDim = player.level().dimension().location().toString();
        story.chapter = 1;
        story.chapterStartMin = minutes(story);
        story.playerName = player.getGameProfile().getName();
        PlaytestLog.log(story, player, "MEET", "Solace appeared");
        StoryAdvancements.grant(player, "root");
        // Has it met them before, and does it remember how their last story ended? (story/Afterward.java)
        boolean metBefore = Afterward.noteMeeting(player, story);

        SolaceEntity solace = SolaceManager.spawnNear(player, story, true);
        if (solace != null) Sounds.playFor(player, solace.position(), Sounds.get("block.amethyst_block.resonate"), 1.0F, 0.5F);
        Scheduler.later(40, () -> {
            if (!player.isRemoved()) Afterward.greet(player, story, metBefore);
        });
        if (!AiService.configured() && SolaceBrain.isAdmin(player)) {
            Scheduler.later(120, () -> player.sendSystemMessage(SolaceBrain.setupMessage(StoryText.SETUP_HINT)));
        }
    }

    private static void startChapter(ServerPlayer player, PlayerStory story, int chapter) {
        PlaytestLog.log(story, player, "CHAPTER", String.format(java.util.Locale.ROOT, "chapter %d is over after %.1f real minutes, starting chapter %d",
                story.chapter, inChapter(story) / SolaceConfig.STORY_SPEED.get(), chapter));
        story.chapter = chapter;
        story.chapterStartMin = minutes(story);
        story.addEvent("the story moved on to chapter " + chapter);
        MinecraftServer server = player.getServer();
        SolaceEntity solace = server == null ? null : SolaceManager.find(server, story);
        if (solace != null) SolaceManager.updateForm(solace, story);
        switch (chapter) {
            case 3 -> { // PART TWO begins: the first crack
                StoryAdvancements.grant(player, "knew_my_name");
                HorrorDirector.glitchScreen(player, 0, 25, "");
                SolaceBrain.event(player, "For a second the whole world flickered. You feel like you've been here before. Mention it, then insist you're fine.", true);
            }
            case 7 -> { // PART FOUR begins
                if (story.trust <= -40) forcedHollow(player, story);
            }
            default -> {
            }
        }
    }

    /** Plays one scene. Returns false if it should wait and be tried again later. */
    private static boolean runBeat(ServerPlayer player, PlayerStory story, String id) {
        MinecraftServer server = player.getServer();
        switch (id) {
            case "c1_ask" -> SolaceBrain.event(player, "Ask the player something about themselves: what they like, what they're building, where they're from. Be curious.", true);
            case "c1_hum" -> SolaceBrain.event(player, StoryText.C1_HUM, false);
            case "c1_torch" -> {
                if (placeHelpfulTorch(player)) SolaceBrain.event(player, StoryText.C1_TORCH, false);
            }
            case "c2_tether" -> SolaceBrain.event(player, StoryText.C2_TETHER, true);
            case "c2_two_places" -> {
                return com.solacemod.solace.horror.OnItsOwn.twoPlaces(player, story) || inChapter(story) > 20;
            }
            case "c2_footsteps" -> {
                HorrorDirector.footsteps(player);
                Scheduler.later(80, () -> SolaceBrain.event(player, "You both just heard footsteps that weren't the player's. Say something nervous, then brush it off.", true));
            }
            case "c3_page1" -> deliverFragment(player, story, 0, Delivery.GROUND);
            case "c3_veins" -> SolaceBrain.event(player, StoryText.C3_VEINS, false);
            case "c3_slip" -> Afterward.slip(player, story); // only if Solace remembers how the last story ended
            case "c3_fall" -> {
                // The first Fall. Waits until you're safe on the ground (and gives up after a while).
                return com.solacemod.solace.horror.OnItsOwn.fall(player, story) || inChapter(story) > 22;
            }
            case "c3_fall_ask" -> {
                if (!story.beatsDone.contains("fell")) return inChapter(story) > 24;
                SolaceBrain.event(player, StoryText.C3_FALL_ASK, true);
            }
            case "c4_watcher" -> {
                HorrorDirector.spawnWatcher(player, 40, 60, 20 * 40, 22, -1);
                Scheduler.later(100, () -> {
                    if (!Afterward.firstWatcher(player, story)) SolaceBrain.event(player, StoryText.C4_WATCHER, true);
                });
            }
            case "c4_joined" -> {
                HorrorDirector.fakeJoin(player, "Maren");
                Scheduler.later(120, () -> SolaceBrain.event(player, StoryText.C4_JOINED, true));
            }
            case "c4_page2" -> deliverFragment(player, story, 1, Delivery.POCKET);
            case "c4_request" -> {
                if (story.request == null && Bond.makeRequest(player, story, "stay_until_morning", "", "Stay until morning. Please.") == null) {
                    SolaceBrain.event(player, "Ask the player, quietly and personally, to stay with you until morning. Please. (You've just made this request.)", true);
                }
            }
            case "c4_page3" -> deliverFragment(player, story, 2, Delivery.WATCHER_FAR);
            case "c4_sign" -> HorrorDirector.placeSign(player, new String[]{"DON'T", "PROMISE", "IT", "ANYTHING"});
            case "c5_house" -> {
                return buildHouse(player, story);
            }
            case "c5_page5" -> {
                // After you've been inside the house (or if you never go).
                if (!story.beatsDone.contains("house_visited") && inChapter(story) < 24) return false;
                deliverFragment(player, story, 4, Delivery.WATCHER_FAR);
            }
            case "c5_page4_lost" -> {
                if (!story.fragmentsFound.contains(3)) deliverFragment(player, story, 3, Delivery.POCKET);
            }
            case "c6_confess" -> {
                return confess(player, story);
            }
            case "c6_page6" -> {
                if (!story.beatsDone.contains("c6_confess")) return false;
                deliverFragment(player, story, 5, Delivery.POCKET);
            }
            case "c7_dusk" -> SolaceBrain.event(player, StoryText.C7_DUSK, true);
            case "c7_page7" -> {
                deliverFragment(player, story, 6, Delivery.WATCHER_CLOSE);
                Scheduler.later(200, () -> SolaceBrain.event(player, StoryText.C7_PAGE, true));
            }
            case "c7_crash" -> HorrorDirector.fakeCrash(player);
            case "c7_everything" -> {
                if (story.request == null) Bond.makeRequest(player, story, "dont_leave", "15", "Don't leave. Not even for a minute.");
                SolaceBrain.event(player, "You're desperate. Ask for everything at once: stay, don't log off, don't read the page, promise. (You've asked them not to leave for 15 minutes.)", true);
            }
            default -> {
            }
        }
        if (server != null) StoryData.get(server).setDirty();
        return true;
    }

    /** Chapter 1: puts a torch down somewhere dark near the player, like a friend would. */
    private static boolean placeHelpfulTorch(ServerPlayer player) {
        if (!SolaceConfig.ALLOW_WORLD_CHANGES.get()) return false;
        ServerLevel level = player.serverLevel();
        net.minecraft.core.BlockPos origin = player.blockPosition();
        for (int i = 0; i < 200; i++) {
            net.minecraft.core.BlockPos p = origin.offset(player.getRandom().nextInt(9) - 4, player.getRandom().nextInt(3) - 1, player.getRandom().nextInt(9) - 4);
            if (!level.getBlockState(p).isAir() || level.getMaxLocalRawBrightness(p) > 6) continue;
            net.minecraft.world.level.block.state.BlockState torch = net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState();
            if (!torch.canSurvive(level, p)) continue;
            level.setBlock(p, torch, net.minecraft.world.level.block.Block.UPDATE_ALL);
            return true;
        }
        return false;
    }

    /** Chapter 5: builds Maren's house somewhere out in the world. Returns false to try again later. */
    private static boolean buildHouse(ServerPlayer player, PlayerStory story) {
        if (story.houseCorner != null) return true;
        boolean giveUp = inChapter(story) > 15;
        if (!SolaceConfig.ALLOW_WORLD_CHANGES.get()) {
            // No house in this world: the page it would have held finds the player instead.
            deliverFragment(player, story, 3, Delivery.POCKET);
            story.beatsDone.add("house_visited");
            return true;
        }
        net.minecraft.core.BlockPos corner = MarenHouse.findSpot(player);
        if (corner == null) {
            if (giveUp) {
                deliverFragment(player, story, 3, Delivery.POCKET);
                story.beatsDone.add("house_visited");
            }
            return giveUp;
        }
        MarenHouse.build(player.serverLevel(), corner, player.getRandom());
        story.houseCorner = corner;
        story.fragmentsDelivered.add(3);
        story.addEvent("Maren's house appeared in the world, about " + (int) Math.sqrt(corner.distSqr(player.blockPosition())) + " blocks away");
        SolaceBrain.event(player, StoryText.C5_HOUSE, true);
        Sounds.playFor(player, net.minecraft.world.phys.Vec3.atCenterOf(corner), Sounds.get("block.wood.place"), 1.0F, 0.4F);
        return true;
    }

    /**
     * Chapter 6, the confession. The world freezes (mobs stop mid-step, rain hangs in the air) and you see what Solace
     * really is: the Lantern, huge, filling the sky, with the tiny figure in its core. Then it's over, and it pretends it never happened.
     */
    private static boolean confess(ServerPlayer player, PlayerStory story) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        SolaceEntity solace = SolaceManager.find(server, story);
        if (solace == null || solace.level() != player.level() || !com.solacemod.solace.horror.OnItsOwn.calm(player)) return inChapter(story) > 15;
        // Out in front of you, where you can't miss it.
        net.minecraft.world.phys.Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4) look = new net.minecraft.world.phys.Vec3(0, 0, 1);
        net.minecraft.world.phys.Vec3 at = player.position().add(look.normalize().scale(14));
        solace.getNavigation().stop();
        solace.moveTo(at.x, player.getY(), at.z, player.getYRot() + 180F, 0);
        solace.setYBodyRot(player.getYRot() + 180F);
        solace.setYHeadRot(player.getYRot() + 180F);
        solace.showLantern(20 * 60, true);
        ServerLevel level = player.serverLevel();
        boolean frozen = com.solacemod.solace.horror.WorldFreeze.freeze(server);
        if (!frozen && SolaceConfig.PERSONAL_FREEZE.get() && server.getPlayerCount() > 1 && !server.tickRateManager().isFrozen()) {
            // Others are online: freeze only this player's screen.
            com.solacemod.solace.horror.PersonalFreeze.freeze(player, 20 * 16);
            frozen = true;
            PlaytestLog.log(story, player, "SCENE", "personal freeze: only this player's screen stopped (other players online)");
        }
        if (!frozen) PlaytestLog.log(story, player, "SCENE", "the world freeze was skipped (more than one player online, or the game was already frozen)");
        story.addEvent("you showed the player your true shape and confessed that you kept Maren" + (frozen ? " (the whole world stood still)" : ""));
        StoryAdvancements.grant(player, "kept_her");
        Sounds.playFor(player, player.position(), Sounds.get("block.beacon.ambient"), 1.0F, 0.5F);
        for (int i = 0; i < 4; i++) Scheduler.later(30 + i * 40, () -> Sounds.playFor(player, player.position(), Sounds.get("entity.warden.heartbeat"), 1.0F, 0.6F));
        SolaceBrain.event(player, StoryText.C6_CONFESS, true);
        Scheduler.later(20 * 16, () -> {
            com.solacemod.solace.horror.WorldFreeze.unfreeze(server);
            com.solacemod.solace.horror.PersonalFreeze.unfreeze(player);
            if (!solace.isRemoved()) {
                solace.endLantern();
                if (!player.isRemoved() && solace.level() == player.level()) solace.blinkBehind(player);
            }
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ASH, player.getX(), player.getY() + 1, player.getZ(), 60, 3, 2, 3, 0.02);
            Scheduler.later(20 * 5, () -> {
                if (!player.isRemoved()) SolaceBrain.event(player, StoryText.C6_AFTER, true);
            });
        });
        return true;
    }

    // ---------- Pages ----------

    public enum Delivery { GROUND, POCKET, WATCHER_FAR, WATCHER_CLOSE }

    public static void deliverFragment(ServerPlayer player, PlayerStory story, int index, Delivery how) {
        story.fragmentDeliveredAt.put(index, player.serverLevel().getGameTime());
        story.fragmentsDelivered.add(index);
        PlaytestLog.log(story, player, "PAGE", "page " + (index + 1) + " delivered (" + how.name().toLowerCase(java.util.Locale.ROOT) + ")");
        switch (how) {
            case GROUND -> {
                Fragments.dropAt(player.serverLevel(), player.position().add(player.getLookAngle().multiply(-2, 0, -2)), index);
                player.sendSystemMessage(Component.literal("You notice a torn page on the ground behind you.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
            case POCKET -> pocket(player, index);
            case WATCHER_FAR, WATCHER_CLOSE -> {
                boolean close = how == Delivery.WATCHER_CLOSE;
                WatcherEntity w = HorrorDirector.spawnWatcher(player, close ? 14 : 28, close ? 20 : 40, 20 * 60, close ? 6 : 12, index);
                if (w == null) pocket(player, index);
            }
        }
    }

    private static void pocket(ServerPlayer player, int index) {
        Fragments.giveTo(player, index);
        player.sendSystemMessage(Component.literal("Something was slipped into your pocket.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        Sounds.playFor(player, player.position(), Sounds.get("solace:page_rustle"), 1.0F, 0.9F);
    }

    /** If a page was dropped somewhere and never picked up, it eventually finds its way into the player's pocket. */
    private static void redeliverLostPages(ServerPlayer player, PlayerStory story, MinecraftServer server) {
        long gameTime = player.serverLevel().getGameTime();
        for (Map.Entry<Integer, Long> e : story.fragmentDeliveredAt.entrySet()) {
            int idx = e.getKey();
            if (story.fragmentsFound.contains(idx)) continue;
            if (gameTime - e.getValue() > 20 * 60 * 4) {
                e.setValue(gameTime);
                PlaytestLog.log(story, player, "PAGE", "page " + (idx + 1) + " was never picked up, so it was slipped into the pocket");
                pocket(player, idx);
                StoryData.get(server).setDirty();
                return;
            }
        }
    }

    private static void scanForFragments(ServerPlayer player, PlayerStory story) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            int idx = Fragments.indexOf(stack);
            if (idx < 0 || idx >= StoryText.FRAGMENT_COUNT || story.fragmentsFound.contains(idx)) continue;
            story.fragmentsFound.add(idx);
            story.addEvent("the player found torn page " + (idx + 1) + " of Maren's journal");
            StoryAdvancements.grant(player, "first_page");
            if (story.fragmentsFound.size() >= StoryText.FRAGMENT_COUNT) StoryAdvancements.grant(player, "all_pages");
            String excerpt = StoryText.FRAGMENTS.get(idx).replace('\n', ' ');
            if (excerpt.length() > 160) excerpt = excerpt.substring(0, 160) + "...";
            if (storyOn()) {
                SolaceBrain.event(player, "The player just picked up torn page " + (idx + 1) + "/7 of Maren's journal. It reads: \"" + excerpt
                        + "\" React the way you would in this chapter.", true);
            }
            return;
        }
    }

    // ---------- The question & endings ----------

    private static void askQuestion(ServerPlayer player, PlayerStory story) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        boolean again = story.choicePending;
        PlaytestLog.log(story, player, "QUESTION", again ? "asked again (no answer yet)" : "Solace asks its question");
        StoryAdvancements.grant(player, "the_question");
        story.choicePending = true;
        story.lastChoicePromptTick = server.getTickCount();
        SolaceEntity solace = SolaceManager.find(server, story);
        // It asks in its true form: small and pleading, the tiny figure in the light.
        if (solace != null) solace.showLantern(20 * 40);
        if (again) {
            SolaceBrain.event(player, "The player still hasn't answered your question (will they stay with you forever?). Ask again, more desperate.", true);
        } else {
            SolaceBrain.event(player, Afterward.question(story), true);
        }
        Scheduler.later(again ? 80 : 30, () -> sendChoices(player, story));
    }

    private static void sendChoices(ServerPlayer player, PlayerStory story) {
        if (player.isRemoved() || !story.choicePending) return;
        MutableComponent row = Component.literal("  ");
        row.append(choice("[Stay forever]", "stay", "Promise to stay with Solace. Forever.", ChatFormatting.GREEN)).append("  ");
        row.append(choice("[Free Maren]", "release", "Refuse. Fight Solace to free Maren.", ChatFormatting.RED)).append("  ");
        row.append(choice("[Erase Solace]", "erase", "Delete Solace from this world.", ChatFormatting.DARK_GRAY));
        if (canTogether(story)) {
            row.append("  ").append(choice("[Let her go, together]", "together", "Ask Solace to let Maren go by itself. (It trusts you.)", ChatFormatting.LIGHT_PURPLE));
        }
        player.sendSystemMessage(Component.literal("Choose (click):").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(row);
    }

    /** The secret ending: it trusts you, you read everything, and you never took more than you needed (low debt). */
    private static boolean canTogether(PlayerStory story) {
        return story.trust >= 70 && story.fragmentsFound.size() >= StoryText.FRAGMENT_COUNT && story.debt < 40;
    }

    /**
     * COLLECTED (secret, bad): you owe it too much, so it doesn't ask. You wake up in Maren's house,
     * the door is gone, and Solace is sitting on the bed.
     */
    private static void collected(ServerPlayer player, PlayerStory story) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        story.ending = Ending.COLLECTED;
        story.choicePending = false;
        StoryData.get(server).setDirty();
        SolaceBrain.event(player, StoryText.COLLECTED, true);
        Scheduler.later(80, () -> {
            if (player.isRemoved()) return;
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 140, 0, false, false));
            HorrorDirector.glitchScreen(player, 2, 50, "YOU OWE ME");
            ServerLevel overworld = server.overworld();
            if (story.houseCorner == null && SolaceConfig.ALLOW_WORLD_CHANGES.get()) {
                net.minecraft.core.BlockPos corner = player.level() == overworld ? MarenHouse.findSpot(player) : null;
                if (corner != null) {
                    MarenHouse.build(overworld, corner, player.getRandom());
                    story.houseCorner = corner;
                }
            }
            if (story.houseCorner != null) {
                net.minecraft.core.BlockPos c = story.houseCorner;
                if (SolaceConfig.ALLOW_WORLD_CHANGES.get()) MarenHouse.sealDoor(overworld, c);
                player.teleportTo(overworld, c.getX() + 3.5, c.getY(), c.getZ() + 2.5, 0F, 10F); // facing the bed
                Scheduler.later(5, () -> {
                    SolaceEntity solace = SolaceManager.find(server, story);
                    if (solace == null) solace = SolaceManager.summon(player, story);
                    if (solace != null && solace.level() == overworld) solace.moveTo(c.getX() + 1.5, c.getY() + 0.6, c.getZ() + 4.5, 0, 20F);
                    if (solace != null) solace.setStareTicks(20 * 20);
                });
            }
            Scheduler.later(100, () -> {
                if (player.isRemoved()) return;
                SolaceBrain.event(player, StoryText.COLLECTED_WAKE, true);
                Scheduler.later(200, () -> finishEnding(player, story, Ending.COLLECTED, "You took too much. It took you back. You're home now."));
            });
        });
    }

    private static Component choice(String label, String option, String hover, ChatFormatting color) {
        return Component.literal(label).withStyle(Style.EMPTY.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/solace choose " + option))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    /** Called by /solace choose. Returns an error message, or null on success. */
    @Nullable
    public static String choose(ServerPlayer player, String option) {
        MinecraftServer server = player.getServer();
        if (server == null) return "No server.";
        PlayerStory story = StoryData.get(server).get(player.getUUID());
        if (!story.choicePending) return "There's nothing to choose right now.";
        SolaceEntity solace = SolaceManager.find(server, story);
        if (solace == null) solace = SolaceManager.summon(player, story);
        if (solace == null) return "Solace isn't here.";
        story.choicePending = false;
        StoryData.get(server).setDirty();
        final SolaceEntity s = solace;
        switch (option) {
            case "stay" -> {
                story.ending = Ending.FOREVER;
                SolaceBrain.event(player, StoryText.END_FOREVER, true);
                SolaceManager.updateForm(s, story);
                Scheduler.later(60, () -> {
                    HorrorDirector.spawnWatcher(player, 18, 24, 20 * 12, 8, -1);
                    Scheduler.later(100, () -> HorrorDirector.fakeChat(player, "Maren", "...okay. I understand."));
                });
                Scheduler.later(200, () -> finishEnding(player, story, Ending.FOREVER, "You stayed. Somewhere, a pale figure stops waiting."));
            }
            case "erase" -> {
                story.ending = Ending.ERASE;
                SolaceBrain.lastWords(player, StoryText.END_ERASE);
                Scheduler.later(60, () -> SolaceManager.remove(server, story));
                Scheduler.later(120, () -> finishEnding(player, story, Ending.ERASE, "Solace is gone. The world is very quiet. Maren is still out there."));
                Scheduler.later(20 * 60 * 5, () -> {
                    if (!player.isRemoved()) HorrorDirector.fakeChat(player, "Solace", "...are you still there?");
                });
            }
            case "together" -> {
                if (!canTogether(story)) {
                    story.choicePending = true;
                    return "Solace doesn't trust you enough for that.";
                }
                story.ending = Ending.TOGETHER;
                SolaceBrain.event(player, StoryText.END_TOGETHER, true);
                SolaceManager.updateForm(s, story);
                Scheduler.later(80, () -> {
                    HorrorDirector.spawnWatcher(player, 10, 14, 20 * 20, 4, Fragments.TOGETHER_LETTER);
                    Scheduler.later(120, () -> HorrorDirector.fakeChat(player, "Maren", "thank you. both of you."));
                });
                Scheduler.later(300, () -> finishEnding(player, story, Ending.TOGETHER, "Maren is free. Solace stayed, because it wanted to. Not because it had to."));
            }
            case "release" -> {
                story.bossActive = true;
                SolaceBrain.event(player, StoryText.END_RELEASE_START, true);
                Scheduler.later(50, () -> startBoss(player, story, s));
            }
            default -> {
                story.choicePending = true;
                return "Unknown choice. Use stay, release, erase or together.";
            }
        }
        return null;
    }

    private static void forcedHollow(ServerPlayer player, PlayerStory story) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        SolaceEntity solace = SolaceManager.find(server, story);
        if (solace == null) solace = SolaceManager.summon(player, story);
        if (solace == null) return;
        story.bossActive = true;
        SolaceBrain.event(player, StoryText.FORCED_HOLLOW, true);
        final SolaceEntity s = solace;
        Scheduler.later(60, () -> startBoss(player, story, s));
    }

    private static void startBoss(ServerPlayer player, PlayerStory story, SolaceEntity solace) {
        if (player.isRemoved() || solace.isRemoved()) {
            story.bossActive = false;
            story.choicePending = true;
            return;
        }
        solace.becomeHollow();
        Titles.show(player, Component.literal("THE HOLLOW").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                Component.literal("free her").withStyle(ChatFormatting.GRAY), 10, 60, 20);
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
        ServerLevel level = player.serverLevel();
        level.setWeatherParameters(0, 20 * 60 * 5, true, true);
        Sounds.play(level, solace.position(), "entity.wither.spawn", net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 0.5F);
    }

    public static void onHollowDefeated(SolaceEntity solace) {
        MinecraftServer server = solace.getServer();
        UUID ownerId = solace.getOwnerUUID();
        if (server == null || ownerId == null) return;
        PlayerStory story = StoryData.get(server).get(ownerId);
        story.bossActive = false;
        story.ending = Ending.RELEASE;
        StoryData.get(server).setDirty();
        ServerLevel level = (ServerLevel) solace.level();
        level.setWeatherParameters(20 * 60 * 10, 0, false, false);
        Fragments.dropAt(level, solace.position(), Fragments.RELEASE_LETTER);
        ServerPlayer player = server.getPlayerList().getPlayer(ownerId);
        if (player == null) return;
        SolaceBrain.lastWords(player, StoryText.END_RELEASE_WIN);
        java.util.Set<UUID> fighters = solace.fighters();
        Scheduler.later(100, () -> {
            HorrorDirector.spawnWatcher(player, 10, 14, 20 * 15, 4, -1);
            Scheduler.later(80, () -> Together.marenThanks(player, fighters));
        });
        Scheduler.later(260, () -> finishEnding(player, story, Ending.RELEASE, "Maren is free. Solace is gone. You can leave whenever you want."));
    }

    /** The player died, logged off, or ran away during the boss fight. */
    public static void endBossLost(ServerPlayer player, PlayerStory story, SolaceEntity solace, boolean died) {
        story.bossActive = false;
        story.choicePending = true;
        MinecraftServer server = player.getServer();
        if (server != null) story.lastChoicePromptTick = server.getTickCount();
        solace.revertFromHollow();
        player.serverLevel().setWeatherParameters(20 * 60 * 5, 0, false, false);
        if (died) {
            story.changeTrust(-5);
            Scheduler.later(60, () -> SolaceBrain.event(player, StoryText.BOSS_PLAYER_DIED, true));
        }
    }

    public static void onBossAbandoned(SolaceEntity solace) {
        MinecraftServer server = solace.getServer();
        UUID ownerId = solace.getOwnerUUID();
        if (server == null || ownerId == null) return;
        PlayerStory story = StoryData.get(server).get(ownerId);
        ServerPlayer player = server.getPlayerList().getPlayer(ownerId);
        if (player != null) endBossLost(player, story, solace, false);
        else {
            story.bossActive = false;
            story.choicePending = true;
            solace.revertFromHollow();
        }
    }

    public static void onPlayerDeath(ServerPlayer player, PlayerStory story) {
        MinecraftServer server = player.getServer();
        if (server == null || !story.bossActive) return;
        SolaceEntity solace = SolaceManager.find(server, story);
        if (solace != null) endBossLost(player, story, solace, true);
    }

    private static void finishEnding(ServerPlayer player, PlayerStory story, Ending ending, String description) {
        if (player.isRemoved()) return;
        PlaytestLog.log(story, player, "ENDING", ending.name());
        GlobalMemory.recordEnding(player.getUUID(), ending);
        MinecraftServer srv = player.getServer();
        if (srv != null) StoryData.get(srv).recordEnding(player.getUUID(), ending);
        long found = GlobalMemory.distinctEndings(player.getUUID());
        StoryAdvancements.grantEnding(player, ending);
        if (found >= 5) StoryAdvancements.grant(player, "every_ending");
        Titles.show(player, Component.literal("ENDING: " + ending.name()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
                Component.literal(found + " of 5 endings discovered").withStyle(ChatFormatting.GRAY), 20, 100, 40);
        player.sendSystemMessage(Component.literal("---- ENDING: " + ending.name() + " ----").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal(description).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        player.sendSystemMessage(Component.literal("Endings discovered: " + found + "/5. Play again with /solace reset confirm").withStyle(ChatFormatting.DARK_GRAY));
        Together.ripple(player, ending);
        MinecraftServer server = player.getServer();
        if (server != null) StoryData.get(server).setDirty();
    }

    // ---------- Admin / testing ----------

    /** /solace admin ending: jumps straight to an ending's result (no question, no boss fight), for testing what comes after. */
    public static void adminEnding(ServerPlayer player, PlayerStory story, Ending ending) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        PlaytestLog.log(story, player, "ADMIN", "jumped straight to the ending " + ending.name() + " with /solace admin");
        story.choicePending = false;
        story.bossActive = false;
        story.ending = ending;
        SolaceEntity solace = SolaceManager.find(server, story);
        if (ending == Ending.RELEASE || ending == Ending.ERASE) {
            if (solace != null && solace.isHollow()) solace.revertFromHollow();
            SolaceManager.remove(server, story);
        } else if (solace != null) {
            SolaceManager.updateForm(solace, story);
        }
        StoryData.get(server).setDirty();
        finishEnding(player, story, ending, "(Jumped here with /solace admin ending.)");
    }

    /** Jumps a player to the start of a chapter (for testing). Earlier pages count as found, earlier scenes as done. */
    public static void setChapter(ServerPlayer player, PlayerStory story, int chapter) {
        int ch = Math.max(1, Math.min(8, chapter));
        PlaytestLog.log(story, player, "ADMIN", "jumped to chapter " + ch + " with /solace admin (times in the next line don't count)");
        story.playTicks = (long) (DEFAULT_START[ch] / SolaceConfig.STORY_SPEED.get() * 1200);
        story.choicePending = false;
        for (int i = 0; i < PAGES_TO_LEAVE[ch - 1]; i++) {
            story.fragmentsFound.add(i);
            story.fragmentsDelivered.add(i);
        }
        for (Beat b : BEATS) if (b.chapter() < ch) story.beatsDone.add(b.id());
        if (ch > 5) story.beatsDone.add("house_visited");
        if (ch == 8) for (int i = 0; i < StoryText.FRAGMENT_COUNT; i++) story.fragmentsFound.add(i);
        LAST_BEAT.put(player.getUUID(), 0L);
        startChapter(player, story, ch);
    }

    public static void reset() {
        ONLINE_SINCE.clear();
        LAST_BEAT.clear();
        LAST_GUIDE.clear();
        SLEEP_REMINDED.clear();
        Nudges.reset();
        Afterward.reset();
    }

    public static void forgetSession(UUID id) {
        ONLINE_SINCE.remove(id);
        LAST_BEAT.remove(id);
        Nudges.forget(id);
        Afterward.forgetSession(id);
    }
}
