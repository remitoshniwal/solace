package com.solacemod.solace.story;

import com.solacemod.solace.config.SolaceConfig;
import com.solacemod.solace.entity.Presence;
import com.solacemod.solace.entity.SolaceEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Players together (multiplayer): Solace notices who you spend time with (jealousy), other players see what it does
 * behind your back (witnesses), two Solaces meet, and endings ripple out to the other players' Solaces.
 * None of this runs in single player.
 *
 * Every "how often" number is at the top of this file. They're guesses until a playtest with a friend.
 */
public final class Together {
    // ---------- Numbers to tweak after a playtest ----------
    /** How close (blocks) another player must be to count as "spending time with them". */
    static final int NEAR_BLOCKS = 16;
    /** Seconds near someone (from chapter 3) before Solace says something about it. */
    static final int JEALOUSY_AFTER_SECONDS = 5 * 60;
    /** Shortest gap between two jealous remarks, in ticks (20 minutes). */
    static final long JEALOUSY_GAP_TICKS = 20 * 60 * 20;
    /** Minutes together before the other player is mentioned in Solace's prompt. */
    static final int PROMPT_AFTER_MINUTES = 2;
    /** How far away (blocks) someone can witness another player's Solace. */
    static final int WITNESS_RANGE = 32;
    /** Shortest gap between two witness lines for the same witness and owner, in ticks (3 minutes). */
    static final long WITNESS_GAP_TICKS = 3 * 60 * 20;
    /** Shortest gap between two AI comments from the witness's own Solace, in ticks (15 minutes). */
    static final long WITNESS_COMMENT_GAP_TICKS = 15 * 60 * 20;
    /** How close (blocks) another player must be to your Solace for it to do something only they can see. */
    static final int FOR_SOMEONE_ELSE_RANGE = 24;
    /** How close (blocks) two Solaces must be to meet. */
    static final int MEET_RANGE = 8;
    /** Chance per second, while two calm Solaces are close, that they stop and meet (so it doesn't happen the instant they're near). */
    static final float MEET_CHANCE = 0.1F;
    /** Shortest gap before the same two Solaces meet again, in ticks (20 minutes). */
    static final long MEET_GAP_TICKS = 20 * 60 * 20;
    /** How long after someone else's ending (in ticks) the other Solaces feel it: 1 to 3 minutes. */
    static final int RIPPLE_MIN_TICKS = 20 * 60, RIPPLE_EXTRA_TICKS = 20 * 120;
    /** Before this chapter a ripple is silent, so nobody learns the story early. */
    static final int RIPPLE_SPEAKS_FROM_CHAPTER = 4;
    /** Extra boss health for each friend who joins the fight, and how many friends count (300 alone, 750 with 3). */
    public static final double HOLLOW_HEALTH_PER_FRIEND = 150;
    public static final int HOLLOW_MAX_FRIENDS = 3;
    /** Other Solaces this close (blocks) to the Hollow stop and watch it. */
    static final int HOLLOW_ONLOOKER_RANGE = 32;

    private Together() {
    }

    /** True when the story runs and this player has a Solace of their own. */
    static boolean hasSolace(PlayerStory story) {
        return SolaceConfig.STORY_ENABLED.get() && story.met && story.solaceShouldExist();
    }

    static boolean aiComments() {
        return SolaceConfig.MULTIPLAYER_AI_COMMENTS.get();
    }

    /** A short line, word for word, in grey italics, for one player only. */
    static void line(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    /** Runs every second for each online player. */
    public static void tick(ServerPlayer player, PlayerStory story) {
        if (!hasSolace(story)) return;
        tickJealousy(player, story);
        tickWitnesses(player, story);
    }

    /** Runs every second, once for the whole server (things between two players). */
    public static void tickServer(MinecraftServer server) {
        if (server.getPlayerCount() < 2 || !SolaceConfig.STORY_ENABLED.get()) return;
        if (SolaceConfig.SOLACES_MEET.get()) tickMeetings(server);
        tickOnlookers(server);
    }

    /** Forgets everything that only lasts while the server runs. */
    public static void reset() {
        SIGHTS.clear();
        LAST_WITNESSED.clear();
        LAST_MET.clear();
    }

    // ---------- Jealousy ----------

    private static void tickJealousy(ServerPlayer player, PlayerStory story) {
        ServerPlayer near = nearestPlayer(player, NEAR_BLOCKS);
        if (near == null) {
            story.nearStreak = Math.max(0, story.nearStreak - 1);
            return;
        }
        story.secondsNear.merge(near.getUUID(), 1, Integer::sum);
        story.nearStreak++;
        if (!SolaceConfig.JEALOUSY.get() || story.chapter < 3 || story.storyOver() || story.bossActive) return;
        MinecraftServer server = player.getServer();
        long now = server.getTickCount();
        if (story.nearStreak < JEALOUSY_AFTER_SECONDS || now - story.lastJealousyTick < JEALOUSY_GAP_TICKS) return;
        story.nearStreak = 0;
        story.lastJealousyTick = now;
        jealous(player, story, near, name(near), story.secondsNear.getOrDefault(near.getUUID(), 0) / 60);
    }

    /** Solace stares at the other player and says something about them (also used by /solace admin together). */
    static void jealous(ServerPlayer player, PlayerStory story, @Nullable Entity other, String otherName, int minutes) {
        SolaceEntity solace = SolaceManager.find(player.getServer(), story);
        if (solace != null && other != null && solace.level() == other.level() && !solace.isHollow()) solace.stareAt(other, 20 * 8);
        PlaytestLog.log(story, player, "JEALOUSY", "with " + otherName + " (" + minutes + " minutes together)");
        if (aiComments()) SolaceBrain.event(player, String.format(StoryText.JEALOUSY_EVENT, otherName), false);
        else line(player, String.format(StoryText.JEALOUSY_LINE, otherName));
    }

    /** The line about who the player spends most time with, for Solace's prompt, or null. */
    @Nullable
    static String jealousyPrompt(ServerPlayer player, PlayerStory story) {
        if (!SolaceConfig.JEALOUSY.get() || !SolaceConfig.STORY_ENABLED.get() || story.secondsNear.isEmpty()) return null;
        UUID best = null;
        int most = 0;
        for (Map.Entry<UUID, Integer> e : story.secondsNear.entrySet()) {
            if (e.getValue() > most) {
                most = e.getValue();
                best = e.getKey();
            }
        }
        if (best == null || most < PROMPT_AFTER_MINUTES * 60) return null;
        String name = nameOf(player.getServer(), best);
        return name == null ? null : StoryText.jealousy(story.chapter, story.ending, name, most / 60);
    }

    // ---------- Witnesses ----------

    /** Something strange another player could catch: a Solace (or its copy) doing something its owner can't see. */
    private record Sight(Entity thing, UUID owner, String kind, long until) {
    }

    private static final List<Sight> SIGHTS = new ArrayList<>();
    /** "witness|owner" -> tick of the last witness line between them. */
    private static final Map<String, Long> LAST_WITNESSED = new HashMap<>();

    /** For the next few seconds, other players who look at {@code thing} witness it (a blink, a copy in two places...). */
    public static void showWitnesses(Entity thing, UUID owner, String kind, int ticks) {
        MinecraftServer server = thing.getServer();
        if (server == null || !SolaceConfig.WITNESSES.get()) return;
        SIGHTS.add(new Sight(thing, owner, kind, server.getTickCount() + ticks));
    }

    private static void tickWitnesses(ServerPlayer owner, PlayerStory story) {
        MinecraftServer server = owner.getServer();
        if (!SolaceConfig.WITNESSES.get() || server.getPlayerCount() < 2) {
            SIGHTS.clear();
            return;
        }
        long now = server.getTickCount();
        // What it's doing right now: one of its wrong movements, or standing over your bed.
        SolaceEntity solace = SolaceManager.find(server, story);
        if (solace != null && !solace.isHollow() && !solace.isShowingLantern() && solace.level() == owner.level()) {
            int move = solace.getPresence();
            if (move != Presence.WALK && move < Presence.NAMES.length) checkSight(owner, solace, Presence.NAMES[move], now);
            else if (owner.isSleeping() && solace.getStareTicks() > 0) checkSight(owner, solace, "sleep", now);
        }
        // Short moments registered by showWitnesses.
        SIGHTS.removeIf(s -> s.until() < now || s.thing().isRemoved());
        for (Sight s : List.copyOf(SIGHTS)) {
            if (s.owner().equals(owner.getUUID()) && s.thing().level() == owner.level()) checkSight(owner, s.thing(), s.kind(), now);
        }
    }

    /** If the owner isn't looking at it but someone else is, that someone is a witness. */
    private static void checkSight(ServerPlayer owner, Entity thing, String kind, long now) {
        if (looksAt(owner, thing)) return;
        MinecraftServer server = owner.getServer();
        for (ServerPlayer w : owner.serverLevel().players()) {
            if (w == owner || w.isSpectator() || !w.isAlive() || w.distanceTo(thing) > WITNESS_RANGE) continue;
            PlayerStory ws = StoryData.get(server).getIfPresent(w.getUUID());
            if (ws == null || !hasSolace(ws) || !looksAt(w, thing)) continue;
            String key = w.getUUID() + "|" + owner.getUUID();
            if (now - LAST_WITNESSED.getOrDefault(key, -1_000_000L) < WITNESS_GAP_TICKS) continue;
            LAST_WITNESSED.put(key, now);
            witnessed(w, ws, name(owner), StoryData.get(server).get(owner.getUUID()), kind, now);
        }
    }

    /** The witness is told what they saw, and the owner's Solace knows it was seen. {@code os} is null for a test stand-in. */
    private static void witnessed(ServerPlayer witness, PlayerStory ws, String ownerName, @Nullable PlayerStory os, String kind, long now) {
        line(witness, String.format(StoryText.WITNESS_LINES.getOrDefault(kind, StoryText.WITNESS_LINE_OTHER), ownerName));
        if (os != null) os.addEvent(String.format(StoryText.WITNESS_OWNER_EVENT, name(witness)) + " (" + kind.replace('_', ' ') + ")");
        PlaytestLog.log(ws, witness, "WITNESS", "saw " + ownerName + "'s Solace: " + kind);
        if (aiComments() && now - ws.lastWitnessCommentTick >= WITNESS_COMMENT_GAP_TICKS) {
            ws.lastWitnessCommentTick = now;
            SolaceBrain.event(witness, StoryText.witnessComment(ws.chapter, ws.ending, ownerName), false);
        }
    }

    /** A rare scare: your Solace turns and stares at another player, only while you're not looking. True if it happened. */
    public static boolean forSomeoneElse(ServerPlayer owner, PlayerStory story) {
        MinecraftServer server = owner.getServer();
        SolaceEntity solace = server == null ? null : SolaceManager.find(server, story);
        if (solace == null || solace.isHollow() || solace.level() != owner.level() || solace.getTask() != null || looksAt(owner, solace)) return false;
        ServerPlayer other = null;
        for (ServerPlayer o : owner.serverLevel().players()) {
            if (o == owner || o.isSpectator() || o.distanceTo(solace) > FOR_SOMEONE_ELSE_RANGE) continue;
            PlayerStory os = StoryData.get(server).getIfPresent(o.getUUID());
            if (os != null && hasSolace(os)) other = o;
        }
        if (other == null) return false;
        solace.stareAt(other, 20 * 6);
        showWitnesses(solace, owner.getUUID(), "for_you", 20 * 6);
        return true;
    }

    /** Is there another player (with a Solace) close enough to your Solace to see it? */
    public static boolean someoneElseNear(ServerPlayer owner, PlayerStory story) {
        MinecraftServer server = owner.getServer();
        if (server == null || !SolaceConfig.WITNESSES.get() || server.getPlayerCount() < 2) return false;
        SolaceEntity solace = SolaceManager.find(server, story);
        if (solace == null || solace.level() != owner.level()) return false;
        for (ServerPlayer o : owner.serverLevel().players()) {
            if (o == owner || o.isSpectator() || o.distanceTo(solace) > FOR_SOMEONE_ELSE_RANGE) continue;
            PlayerStory os = StoryData.get(server).getIfPresent(o.getUUID());
            if (os != null && hasSolace(os)) return true;
        }
        return false;
    }

    /** Is this player looking right at it, with nothing in the way? */
    public static boolean looksAt(ServerPlayer p, Entity thing) {
        Vec3 view = p.getViewVector(1.0F).normalize();
        Vec3 to = thing.getEyePosition().subtract(p.getEyePosition()).normalize();
        return view.dot(to) > 0.96 && p.hasLineOfSight(thing);
    }

    // ---------- Two Solaces meet ----------

    /** "solaceOwnerA|solaceOwnerB" (sorted) -> tick they last met. */
    private static final Map<String, Long> LAST_MET = new HashMap<>();

    private record Companion(ServerPlayer owner, PlayerStory story, SolaceEntity solace) {
    }

    private static void tickMeetings(MinecraftServer server) {
        List<Companion> calm = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PlayerStory story = StoryData.get(server).getIfPresent(p.getUUID());
            if (story == null || !hasSolace(story) || story.bossActive) continue;
            SolaceEntity solace = SolaceManager.find(server, story);
            if (solace != null && calm(solace)) calm.add(new Companion(p, story, solace));
        }
        long now = server.getTickCount();
        for (int i = 0; i < calm.size(); i++) {
            for (int j = i + 1; j < calm.size(); j++) {
                Companion a = calm.get(i), b = calm.get(j);
                if (a.solace().level() != b.solace().level() || a.solace().distanceTo(b.solace()) > MEET_RANGE) continue;
                if (now - LAST_MET.getOrDefault(pairKey(a, b), -1_000_000L) < MEET_GAP_TICKS) continue;
                if (a.solace().getRandom().nextFloat() >= MEET_CHANCE) continue;
                meet(a.owner(), a.story(), b.owner(), b.story());
                return; // one meeting at a time is plenty
            }
        }
    }

    private static boolean calm(SolaceEntity s) {
        return !s.isHollow() && !s.isLanternForm() && !s.isNegative() && s.getTask() == null && s.getPresence() == Presence.WALK
                && s.getStareTicks() <= 0 && !s.combat.ownerFighting() && s.getTarget() == null;
    }

    private static String pairKey(Companion a, Companion b) {
        String x = a.owner().getUUID().toString(), y = b.owner().getUUID().toString();
        return x.compareTo(y) < 0 ? x + "|" + y : y + "|" + x;
    }

    /** Two Solaces stop and face each other in silence. True if it happened (also used by /solace admin together). */
    public static boolean meet(ServerPlayer ownerA, PlayerStory a, ServerPlayer ownerB, PlayerStory b) {
        MinecraftServer server = ownerA.getServer();
        SolaceEntity sa = SolaceManager.find(server, a), sb = SolaceManager.find(server, b);
        if (sa == null || sb == null || sa.level() != sb.level()) return false;
        LAST_MET.put(pairKey(new Companion(ownerA, a, sa), new Companion(ownerB, b, sb)), (long) server.getTickCount());
        int ticks = 20 * (5 + sa.getRandom().nextInt(6));
        sa.stareAt(sb, ticks);
        sb.stareAt(sa, ticks);
        // Later in both stories, they turn negative together, at the same instant.
        if (Math.min(a.chapter, b.chapter) >= 3 && a.ending == Ending.NONE && b.ending == Ending.NONE) {
            Scheduler.later(ticks / 2, () -> {
                if (sa.isRemoved() || sb.isRemoved()) return;
                sa.goNegative(30);
                sb.goNegative(30);
            });
        }
        a.addEvent(String.format(StoryText.MEET_EVENT, name(ownerB)));
        b.addEvent(String.format(StoryText.MEET_EVENT, name(ownerA)));
        PlaytestLog.log(a, ownerA, "MEET", "Solace met " + name(ownerB) + "'s Solace");
        PlaytestLog.log(b, ownerB, "MEET", "Solace met " + name(ownerA) + "'s Solace");
        return true;
    }

    // ---------- Endings that ripple ----------

    /** Someone finished the story: every other player's Solace feels it once (offline players when they're back). */
    public static void ripple(ServerPlayer finisher, Ending ending) {
        MinecraftServer server = finisher.getServer();
        if (server == null || !SolaceConfig.ENDINGS_RIPPLE.get() || !SolaceConfig.STORY_ENABLED.get()) return;
        for (PlayerStory other : StoryData.get(server).all().values()) {
            if (other.playerId.equals(finisher.getUUID()) || !hasSolace(other) || other.storyOver()) continue;
            other.pendingRipple = ending;
            ServerPlayer p = server.getPlayerList().getPlayer(other.playerId);
            if (p != null) Scheduler.later(RIPPLE_MIN_TICKS + finisher.getRandom().nextInt(RIPPLE_EXTRA_TICKS), () -> feelRipple(p, other));
        }
        StoryData.get(server).setDirty();
    }

    /** A player came back: if someone finished while they were away, their Solace feels it a minute later. */
    public static void onLogin(ServerPlayer player, PlayerStory story) {
        if (story.pendingRipple != null) Scheduler.later(RIPPLE_MIN_TICKS, () -> feelRipple(player, story));
    }

    /** The Solace goes still, and (later in the story) says something. Also used by /solace admin together. */
    public static void feelRipple(ServerPlayer player, PlayerStory story) {
        Ending ending = story.pendingRipple;
        if (ending == null || player.isRemoved() || story.bossActive) return; // still pending: tried again on the next login
        story.pendingRipple = null;
        if (!hasSolace(story) || story.storyOver()) return;
        SolaceEntity solace = SolaceManager.find(player.getServer(), story);
        if (solace != null && !solace.isHollow() && solace.level() == player.level()) {
            solace.getNavigation().stop();
            solace.stareAt(solace, 20 * 12); // looking at nothing: it just stops, facing wherever it was facing
        }
        PlaytestLog.log(story, player, "RIPPLE", "felt another player's ending: " + ending.name());
        if (story.chapter < RIPPLE_SPEAKS_FROM_CHAPTER) {
            line(player, StoryText.RIPPLE_STILL_LINE);
            return;
        }
        if (aiComments()) SolaceBrain.event(player, rippleHint(ending), false);
        else line(player, rippleLine(ending));
        story.addEvent("you felt another companion's story end (" + ending.name().toLowerCase() + ")");
    }

    private static String rippleHint(Ending ending) {
        return switch (ending) {
            case FOREVER, COLLECTED -> StoryText.RIPPLE_KEPT;
            case TOGETHER -> StoryText.RIPPLE_LET_GO;
            default -> StoryText.RIPPLE_GONE;
        };
    }

    private static String rippleLine(Ending ending) {
        return switch (ending) {
            case FOREVER, COLLECTED -> StoryText.RIPPLE_KEPT_LINE;
            case TOGETHER -> StoryText.RIPPLE_LET_GO_LINE;
            default -> StoryText.RIPPLE_GONE_LINE;
        };
    }

    // ---------- The Hollow with friends ----------

    /** A friend hit someone's Hollow. The first one makes it furious (one AI line for the owner). */
    public static void onHollowFriend(SolaceEntity hollow, @Nullable ServerPlayer owner, ServerPlayer friend, int friends) {
        MinecraftServer server = friend.getServer();
        PlayerStory fs = StoryData.get(server).getIfPresent(friend.getUUID());
        if (owner == null) return;
        PlayerStory os = StoryData.get(server).get(owner.getUUID());
        PlaytestLog.log(os, owner, "HOLLOW", name(friend) + " joined the fight (" + friends + " friend" + (friends == 1 ? "" : "s") + ", boss health " + Math.round(hollow.getMaxHealth()) + ")");
        if (fs != null) PlaytestLog.log(fs, friend, "HOLLOW", "joined " + name(owner) + "'s fight");
        if (friends == 1 && aiComments()) SolaceBrain.event(owner, String.format(StoryText.BOSS_FRIEND_JOINED, name(friend)), true);
    }

    /** Phase two with friends: it turns on them and says, word for word, that it only wants its owner. */
    public static void hollowNotThem(SolaceEntity hollow, ServerPlayer owner, List<ServerPlayer> friends) {
        PlayerStory os = StoryData.get(owner.getServer()).get(owner.getUUID());
        SolaceBrain.display(owner, hollow, os, StoryText.BOSS_NOT_THEM, "angry");
        if (SolaceConfig.REPLIES_VISIBLE_TO_ALL.get()) return; // everyone saw it already
        Component said = Component.literal("<").append(Component.literal("The Hollow").withStyle(ChatFormatting.DARK_RED))
                .append(Component.literal("> " + StoryText.BOSS_NOT_THEM).withStyle(ChatFormatting.WHITE));
        for (ServerPlayer f : friends) f.sendSystemMessage(said);
    }

    /** The Hollow was beaten: Maren thanks everyone who fought, and the friends get "Not alone". */
    public static void marenThanks(ServerPlayer owner, java.util.Set<UUID> fighters) {
        MinecraftServer server = owner.getServer();
        if (server == null) return;
        if (fighters.isEmpty()) {
            com.solacemod.solace.horror.HorrorDirector.fakeChat(owner, "Maren", "thank you.");
            return;
        }
        com.solacemod.solace.horror.HorrorDirector.fakeChat(owner, "Maren", StoryText.MAREN_THANKS_ALL);
        for (UUID id : fighters) {
            ServerPlayer f = server.getPlayerList().getPlayer(id);
            if (f == null) continue;
            com.solacemod.solace.horror.HorrorDirector.fakeChat(f, "Maren", StoryText.MAREN_THANKS_ALL);
            StoryAdvancements.grant(f, "not_alone");
        }
    }

    /** While someone fights their Hollow, every other Solace nearby stops and watches. It doesn't help. */
    private static void tickOnlookers(MinecraftServer server) {
        List<SolaceEntity> hollows = new ArrayList<>();
        List<SolaceEntity> others = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PlayerStory story = StoryData.get(server).getIfPresent(p.getUUID());
            if (story == null || !story.met) continue;
            SolaceEntity s = SolaceManager.find(server, story);
            if (s == null) continue;
            if (s.isHollow()) hollows.add(s);
            else others.add(s);
        }
        for (SolaceEntity hollow : hollows) {
            for (SolaceEntity s : others) {
                if (s.level() == hollow.level() && s.distanceTo(hollow) <= HOLLOW_ONLOOKER_RANGE && s.getTask() == null && !s.isLanternForm()) {
                    s.stareAt(hollow, 40);
                }
            }
        }
    }

    // ---------- Testing (/solace admin together) ----------

    public static final java.util.List<String> ADMIN_OPTIONS = java.util.List.of("jealousy", "witness", "meet", "ripple", "hollow", "freeze");

    /**
     * Runs one multiplayer moment right now for {@code player}. With nobody else online, a stand-in plays the other player
     * (a made-up name, or a second Solace that belongs to nobody), so it can be tested alone. Returns what happened.
     */
    public static String admin(ServerPlayer player, PlayerStory story, String what, String arg) {
        MinecraftServer server = player.getServer();
        if (!story.met) throw new IllegalStateException(name(player) + " hasn't met Solace yet.");
        ServerPlayer other = nearestPlayer(player, 64);
        String otherName = other != null ? name(other) : "Friend";
        switch (what) {
            case "jealousy" -> {
                jealous(player, story, other, otherName, story.secondsNear.getOrDefault(other != null ? other.getUUID() : player.getUUID(), 0) / 60);
                return "Solace is jealous of " + otherName + (other == null ? " (a made-up player: nobody else is near)." : ".");
            }
            case "witness" -> {
                String kind = arg.isEmpty() ? "blink" : arg;
                if (!StoryText.WITNESS_LINES.containsKey(kind)) throw new IllegalStateException("Unknown kind. Options: " + String.join(" ", StoryText.WITNESS_LINES.keySet()));
                PlayerStory os = other == null ? null : StoryData.get(server).getIfPresent(other.getUUID());
                witnessed(player, story, otherName, os, kind, server.getTickCount());
                return name(player) + " witnessed " + otherName + "'s Solace (" + kind + ").";
            }
            case "meet" -> {
                if (other != null) {
                    PlayerStory os = StoryData.get(server).getIfPresent(other.getUUID());
                    if (os != null && meet(player, story, other, os)) return "The two Solaces meet.";
                }
                SolaceEntity mine = SolaceManager.find(server, story);
                if (mine == null || mine.level() != player.level()) throw new IllegalStateException("Solace isn't near " + name(player) + ".");
                SolaceEntity standIn = standIn(mine, story);
                if (standIn == null) throw new IllegalStateException("No room for a stand-in Solace.");
                int ticks = 20 * 8;
                mine.stareAt(standIn, ticks);
                standIn.stareAt(mine, ticks);
                if (story.chapter >= 3 && !story.storyOver()) Scheduler.later(ticks / 2, () -> {
                    if (!mine.isRemoved() && !standIn.isRemoved()) {
                        mine.goNegative(30);
                        standIn.goNegative(30);
                    }
                });
                PlaytestLog.log(story, player, "MEET", "Solace met a stand-in Solace (test)");
                return "Solace meets a stand-in Solace (it leaves in 12 seconds).";
            }
            case "ripple" -> {
                Ending ending;
                try {
                    ending = Ending.valueOf((arg.isEmpty() ? "release" : arg).toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw new IllegalStateException("Unknown ending. Options: release erase forever together collected");
                }
                if (ending == Ending.NONE) throw new IllegalStateException("Pick an ending.");
                story.pendingRipple = ending;
                feelRipple(player, story);
                return "Solace felt another player's ending (" + ending.name().toLowerCase() + ").";
            }
            case "hollow" -> {
                int friends;
                try {
                    friends = arg.isEmpty() ? 2 : Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    throw new IllegalStateException("Give a number of friends, 0 to " + HOLLOW_MAX_FRIENDS + ".");
                }
                SolaceEntity solace = SolaceManager.find(server, story);
                if (solace == null) throw new IllegalStateException("Solace isn't around.");
                if (!solace.isHollow()) {
                    story.bossActive = true;
                    solace.becomeHollow();
                }
                solace.pretendFriends(friends);
                PlaytestLog.log(story, player, "HOLLOW", "test: boss health as if " + friends + " friends joined (" + Math.round(solace.getMaxHealth()) + ")");
                return "The Hollow has " + Math.round(solace.getMaxHealth()) + " health, as if " + friends + " friends joined. End it with /solace reset confirm or by losing the fight.";
            }
            case "freeze" -> {
                com.solacemod.solace.horror.PersonalFreeze.freeze(player, 20 * 16);
                Scheduler.later(20 * 16, () -> com.solacemod.solace.horror.PersonalFreeze.unfreeze(player));
                PlaytestLog.log(story, player, "SCENE", "test: personal freeze");
                return "Only " + name(player) + "'s screen is frozen for 16 seconds. They can't be hurt meanwhile.";
            }
            default -> throw new IllegalStateException("Options: " + String.join(" ", ADMIN_OPTIONS));
        }
    }

    /** A second Solace that belongs to nobody, standing a few blocks in front of yours. Leaves on its own. */
    @Nullable
    private static SolaceEntity standIn(SolaceEntity mine, PlayerStory story) {
        net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) mine.level();
        SolaceEntity s = com.solacemod.solace.entity.ModEntities.SOLACE.get().create(level);
        if (s == null) return null;
        Vec3 dir = Vec3.directionFromRotation(0, mine.getYRot()).multiply(1, 0, 1);
        if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(0, 0, 1);
        Vec3 at = mine.position().add(dir.normalize().scale(4));
        s.setOwner(UUID.randomUUID());
        s.setCustomName(Component.literal("Solace"));
        s.setCorruption(Math.min(8, story.chapter));
        s.standInTicks = 20 * 12;
        s.moveTo(at.x, mine.getY(), at.z, mine.getYRot() + 180F, 0);
        level.addFreshEntity(s);
        return s;
    }

    // ---------- Helpers ----------

    @Nullable
    static ServerPlayer nearestPlayer(ServerPlayer player, double range) {
        ServerPlayer best = null;
        double bestDist = range * range;
        for (ServerPlayer o : player.serverLevel().players()) {
            if (o == player || o.isSpectator() || !o.isAlive()) continue;
            double d = o.distanceToSqr(player);
            if (d <= bestDist) {
                bestDist = d;
                best = o;
            }
        }
        return best;
    }

    /** A player's name, online or not (from their saved story), or null if unknown. */
    @Nullable
    static String nameOf(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) return name(online);
        PlayerStory s = StoryData.get(server).getIfPresent(id);
        return s == null || s.playerName.isEmpty() ? null : s.playerName;
    }
}
