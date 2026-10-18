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
 * Runs the story: when Solace first appears, when each act starts, the timed story "beats" (pages, the Watcher,
 * fake messages...), the final question, and the four endings.
 */
public final class StoryDirector {
    /** Act thresholds in minutes of play (divided by story.storySpeed). */
    public static final double ACT2_MIN = 40, ACT3_MIN = 100, ACT4_MIN = 160;

    private record Beat(String id, int act, double minute) {
    }

    private static final List<Beat> BEATS = List.of(
            new Beat("ask_name", 1, 4), new Beat("first_footsteps", 1, 25),
            new Beat("frag1", 2, 44), new Beat("watcher1", 2, 52), new Beat("frag2", 2, 60), new Beat("fake_join", 2, 66), new Beat("frag3", 2, 78),
            new Beat("sign1", 3, 104), new Beat("frag4", 3, 110), new Beat("fake_crash", 3, 118), new Beat("frag5", 3, 126),
            new Beat("defiant", 3, 134), new Beat("frag6", 3, 146)
    );

    private static final Map<UUID, Long> ONLINE_SINCE = new HashMap<>();
    private static final Map<UUID, Long> LAST_BEAT = new HashMap<>();

    private StoryDirector() {
    }

    private static boolean storyOn() {
        return SolaceConfig.STORY_ENABLED.get();
    }

    private static double minutes(PlayerStory story) {
        return story.playMinutes() * SolaceConfig.STORY_SPEED.get();
    }

    // ---------- Login / logout ----------

    public static void onLogin(ServerPlayer player, PlayerStory story) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ONLINE_SINCE.put(player.getUUID(), (long) server.getTickCount());
        story.lastInteractionTick = server.getTickCount();
        story.lastAmbientTick = server.getTickCount();
        story.playerName = player.getGameProfile().getName();
        if (story.bossActive) {
            // The game was closed during the boss fight. Ask again later.
            story.bossActive = false;
            story.choicePending = true;
            story.lastChoicePromptTick = server.getTickCount();
        }
        if (!story.met || !story.solaceShouldExist() || story.lastLogoutMs <= 0) return;

        long awayMinutes = (System.currentTimeMillis() - story.lastLogoutMs) / 60_000;
        if (awayMinutes < 5) return;
        String away = awayMinutes < 120 ? awayMinutes + " minutes" : (awayMinutes / 60) + " hours";
        story.addEvent("the player was gone (logged off) for " + away);
        boolean late = storyOn() && story.act >= 2 && story.ending == Ending.NONE;
        if (late && awayMinutes >= 60) story.changeTrust(-2);
        String prompt = late
                ? "The player just logged back in after being gone for " + away + ". You waited and counted every second. React (clingy, hurt, relieved)."
                : "The player just came back after " + away + " away. Greet them warmly.";
        Scheduler.later(60, () -> {
            if (player.isRemoved()) return;
            SolaceBrain.event(player, prompt, true);
            // Like Maren's journal: come back after a long time and the lights are out.
            if (late && story.act >= 3 && awayMinutes >= 60) HorrorDirector.torchesOut(player, 6, false);
        });
    }

    public static void onLogout(ServerPlayer player, PlayerStory story) {
        ONLINE_SINCE.remove(player.getUUID());
        story.lastLogoutMs = System.currentTimeMillis();
        MinecraftServer server = player.getServer();
        if (server == null) return;
        SolaceEntity solace = SolaceManager.find(server, story);
        if (solace != null) {
            if (story.bossActive) endBossLost(player, story, solace, false);
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
        if (!story.met) {
            long since = ONLINE_SINCE.computeIfAbsent(player.getUUID(), k -> now);
            if (now - since >= 20 * 30 && !player.isSpectator() && player.isAlive()) meet(player, story);
            return;
        }
        story.playTicks += 20;
        scanForFragments(player, story);
        if (!storyOn()) return;
        redeliverLostPages(player, story, server);
        if (story.ending != Ending.NONE || story.bossActive) return;

        double min = minutes(story);
        int nextAct = story.act;
        if (story.act == 1 && min >= ACT2_MIN) nextAct = 2;
        else if (story.act == 2 && min >= ACT3_MIN && story.fragmentsFound.size() >= 3) nextAct = 3;
        else if (story.act == 3 && min >= ACT4_MIN && story.fragmentsFound.size() >= 6) nextAct = 4;
        if (nextAct != story.act) {
            story.act = nextAct;
            LAST_BEAT.put(player.getUUID(), now);
            onActStart(player, story, nextAct);
            StoryData.get(server).setDirty();
            return;
        }

        long lastBeat = LAST_BEAT.getOrDefault(player.getUUID(), 0L);
        if (now - lastBeat >= 20 * 30) {
            for (Beat beat : BEATS) {
                if (beat.act() == story.act && min >= beat.minute() && !story.beatsDone.contains(beat.id())) {
                    story.beatsDone.add(beat.id());
                    LAST_BEAT.put(player.getUUID(), now);
                    runBeat(player, story, beat.id());
                    StoryData.get(server).setDirty();
                    break;
                }
            }
        }

        if (story.act == 4) {
            if (!story.choicePending && story.fragmentsFound.containsAll(List.of(0, 1, 2, 3, 4, 5, 6)) && now - lastBeat > 20 * 60) {
                askQuestion(player, story);
            } else if (story.choicePending && now - story.lastChoicePromptTick > 20 * 60 * 10) {
                askQuestion(player, story);
            }
        }
    }

    private static void meet(ServerPlayer player, PlayerStory story) {
        story.met = true;
        story.act = 1;
        story.playerName = player.getGameProfile().getName();
        GlobalMemory.Entry g = GlobalMemory.get(player.getUUID());
        g.worldsMet++;
        g.name = story.playerName;
        GlobalMemory.save(player.getUUID());
        boolean metBefore = GlobalMemory.enabled() && g.worldsMet > 1;

        SolaceEntity solace = SolaceManager.spawnNear(player, story, true);
        if (solace != null) Sounds.playFor(player, solace.position(), Sounds.get("block.amethyst_block.resonate"), 1.0F, 0.5F);
        String prompt = metBefore ? StoryText.MEET_AGAIN : StoryText.MEET;
        Scheduler.later(40, () -> {
            if (!player.isRemoved()) SolaceBrain.event(player, prompt, true);
        });
        if (!AiService.configured() && SolaceBrain.isAdmin(player)) {
            Scheduler.later(120, () -> player.sendSystemMessage(SolaceBrain.setupMessage(StoryText.SETUP_HINT)));
        }
    }

    private static void onActStart(ServerPlayer player, PlayerStory story, int act) {
        switch (act) {
            case 2 -> {
                HorrorDirector.glitchScreen(player, 0, 25, "");
                SolaceBrain.event(player, "For a second the whole world flickered. You feel like you've been here before. Mention it, then insist you're fine.", true);
            }
            case 3 -> SolaceBrain.event(player, "You've decided to tell the player part of the truth: there was someone here before them. Maren. She left. Say it.", true);
            case 4 -> {
                if (story.trust <= -40) {
                    forcedHollow(player, story);
                    return;
                }
                deliverFragment(player, story, 6, Delivery.WATCHER_CLOSE);
                SolaceBrain.event(player, "You can tell the player is about to learn everything about you and Maren. You feel cornered and frightened.", true);
            }
            default -> {
            }
        }
    }

    private static void runBeat(ServerPlayer player, PlayerStory story, String id) {
        switch (id) {
            case "ask_name" -> SolaceBrain.event(player, "Ask the player what you should call them, or ask about something they like. Be curious.", true);
            case "first_footsteps" -> {
                HorrorDirector.footsteps(player);
                Scheduler.later(80, () -> SolaceBrain.event(player, "You both just heard footsteps that weren't the player's. Say something nervous, then brush it off.", true));
            }
            case "frag1" -> deliverFragment(player, story, 0, Delivery.GROUND);
            case "watcher1" -> HorrorDirector.spawnWatcher(player, 40, 60, 20 * 40, 22, -1);
            case "frag2" -> deliverFragment(player, story, 1, Delivery.POCKET);
            case "fake_join" -> {
                HorrorDirector.fakeJoin(player, "Maren");
                Scheduler.later(120, () -> SolaceBrain.event(player, "A chat message just said 'Maren joined the game', then she left again. The player may ask. Deny knowing her.", true));
            }
            case "frag3" -> deliverFragment(player, story, 2, Delivery.WATCHER_FAR);
            case "sign1" -> HorrorDirector.placeSign(player, new String[]{"DON'T", "PROMISE", "IT", "ANYTHING"});
            case "frag4" -> deliverFragment(player, story, 3, Delivery.WATCHER_FAR);
            case "fake_crash" -> HorrorDirector.fakeCrash(player);
            case "frag5" -> deliverFragment(player, story, 4, Delivery.POCKET);
            case "defiant" -> {
                MinecraftServer server = player.getServer();
                if (server != null) story.defiantUntilTick = server.getTickCount() + 20 * 60 * 5;
                SolaceBrain.event(player, "You're hurt and upset, and for the next few minutes you won't do anything the player asks. Tell them.", true);
            }
            case "frag6" -> deliverFragment(player, story, 5, Delivery.WATCHER_FAR);
            default -> {
            }
        }
    }

    // ---------- Pages ----------

    public enum Delivery { GROUND, POCKET, WATCHER_FAR, WATCHER_CLOSE }

    public static void deliverFragment(ServerPlayer player, PlayerStory story, int index, Delivery how) {
        story.fragmentDeliveredAt.put(index, player.serverLevel().getGameTime());
        story.fragmentsDelivered.add(index);
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
        Sounds.playFor(player, player.position(), Sounds.get("item.book.page_turn"), 1.0F, 0.7F);
    }

    /** If a page was dropped somewhere and never picked up, it eventually finds its way into the player's pocket. */
    private static void redeliverLostPages(ServerPlayer player, PlayerStory story, MinecraftServer server) {
        long gameTime = player.serverLevel().getGameTime();
        for (Map.Entry<Integer, Long> e : story.fragmentDeliveredAt.entrySet()) {
            int idx = e.getKey();
            if (story.fragmentsFound.contains(idx)) continue;
            if (gameTime - e.getValue() > 20 * 60 * 4) {
                e.setValue(gameTime);
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
            String excerpt = StoryText.FRAGMENTS.get(idx).replace('\n', ' ');
            if (excerpt.length() > 160) excerpt = excerpt.substring(0, 160) + "...";
            if (storyOn()) {
                SolaceBrain.event(player, "The player just picked up torn page " + (idx + 1) + "/7 of Maren's journal. It reads: \"" + excerpt
                        + "\" React according to your current act.", true);
            }
            return;
        }
    }

    // ---------- The question & endings ----------

    private static void askQuestion(ServerPlayer player, PlayerStory story) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        boolean again = story.choicePending;
        story.choicePending = true;
        story.lastChoicePromptTick = server.getTickCount();
        SolaceEntity solace = SolaceManager.find(server, story);
        if (again) {
            SolaceBrain.event(player, "The player still hasn't answered your question (will they stay with you forever?). Ask again, more desperate.", true);
        } else {
            SolaceBrain.event(player, StoryText.QUESTION, true);
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

    private static boolean canTogether(PlayerStory story) {
        return story.trust >= 70 && story.fragmentsFound.size() >= StoryText.FRAGMENT_COUNT;
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
        Scheduler.later(100, () -> {
            HorrorDirector.spawnWatcher(player, 10, 14, 20 * 15, 4, -1);
            Scheduler.later(80, () -> HorrorDirector.fakeChat(player, "Maren", "thank you."));
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
        GlobalMemory.recordEnding(player.getUUID(), ending);
        long found = GlobalMemory.distinctEndings(player.getUUID());
        Titles.show(player, Component.literal("ENDING: " + ending.name()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
                Component.literal(found + " of 4 endings discovered").withStyle(ChatFormatting.GRAY), 20, 100, 40);
        player.sendSystemMessage(Component.literal("---- ENDING: " + ending.name() + " ----").withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal(description).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        player.sendSystemMessage(Component.literal("Endings discovered: " + found + "/4. Play again with /solace reset confirm").withStyle(ChatFormatting.DARK_GRAY));
        MinecraftServer server = player.getServer();
        if (server != null) StoryData.get(server).setDirty();
    }

    // ---------- Admin / testing ----------

    /** Jumps a player to the start of an act (for testing). */
    public static void setAct(ServerPlayer player, PlayerStory story, int act) {
        double[] start = {0, ACT2_MIN, ACT3_MIN, ACT4_MIN};
        int a = Math.max(1, Math.min(4, act));
        story.act = a;
        story.playTicks = (long) (start[a - 1] / SolaceConfig.STORY_SPEED.get() * 1200);
        story.choicePending = false;
        for (int i = 0; i < (a == 2 ? 0 : a == 3 ? 3 : a == 4 ? 6 : 0); i++) {
            story.fragmentsFound.add(i);
            story.fragmentsDelivered.add(i);
        }
        for (Beat b : BEATS) if (b.act() < a) story.beatsDone.add(b.id());
        LAST_BEAT.put(player.getUUID(), 0L);
        if (a > 1) onActStart(player, story, a);
    }

    public static void reset() {
        ONLINE_SINCE.clear();
        LAST_BEAT.clear();
    }

    public static void forgetSession(UUID id) {
        ONLINE_SINCE.remove(id);
        LAST_BEAT.remove(id);
    }
}
