package com.solacemod.solace.story;

import com.solacemod.solace.entity.SolaceEntity;
import com.solacemod.solace.horror.Phenomena;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The bond between Solace and the player (docs/DESIGN.md section 4).
 *
 * TETHER = how strong Solace is. It grows with time spent together, talking and gifts, and drains when you're apart.
 *          Every power spends Tether. The maximum grows as the story goes on.
 * DEBT   = what you owe. Every power adds half its cost. Time together and doing what Solace asks pay it back.
 *          30+: Solace starts asking for things. 60+: it gets possessive. 100+: it collects.
 */
public final class Bond {
    public static final Set<String> REQUEST_KINDS = Set.of("bring", "stay_until_morning", "go_to", "spend_time", "dont_leave");
    /** Requests expire after 30 minutes of game time. */
    private static final long REQUEST_LIFETIME = 20L * 60 * 30;

    private Bond() {
    }

    public static double maxTether(PlayerStory story) {
        if (story.ending == Ending.FOREVER || story.ending == Ending.TOGETHER || story.ending == Ending.COLLECTED) return 150;
        // Grows chapter by chapter (index = chapter). No hard cap on what it can do, just on how much at once.
        double[] byChapter = {0, 35, 45, 60, 75, 95, 115, 135, 150};
        return byChapter[Math.max(1, Math.min(8, story.chapter))];
    }

    private static void addTether(PlayerStory story, double amount) {
        story.tether = Math.max(0, Math.min(maxTether(story), story.tether + amount));
    }

    private static void addDebt(PlayerStory story, double amount) {
        story.debt = Math.max(0, story.debt + amount);
    }

    /** Tries to pay for a power. Returns false (and changes nothing) if Solace isn't strong enough. */
    public static boolean spend(PlayerStory story, double cost) {
        if (story.tether + 1e-6 < cost) return false;
        story.tether -= cost;
        addDebt(story, cost * 0.5);
        story.totalGiven += cost;
        return true;
    }

    // ---------- Things that change the bond ----------

    /** Once per second per online player. */
    public static void tick(ServerPlayer player, PlayerStory story, @Nullable SolaceEntity solace) {
        boolean near = solace != null && solace.level() == player.level() && solace.distanceTo(player) <= 16;
        boolean far = solace == null || solace.level() != player.level() || solace.distanceTo(player) > 64;
        if (near) {
            addTether(story, 1.0 / 30.0);   // +1 every 30 seconds together
            addDebt(story, -1.0 / 60.0);    // time together slowly pays debt back
        } else if (far) {
            addTether(story, -1.0 / 120.0); // -1 every 2 minutes apart
        }
        tickRequest(player, story, near);
        MinecraftServer server = player.getServer();
        if (server != null && story.debt >= 100 && solace != null && !story.bossActive) {
            long now = server.getTickCount();
            if (now - story.lastCollectCheck > 20 * 120) {
                story.lastCollectCheck = now;
                if (player.getRandom().nextFloat() < 0.3F) collect(player, story, solace);
            }
        }
    }

    public static void onTalk(PlayerStory story, long now) {
        if (now - story.lastTalkTetherTick > 1200) {
            story.lastTalkTetherTick = now;
            story.talkTetherThisMinute = 0;
        }
        if (story.talkTetherThisMinute < 5) {
            story.talkTetherThisMinute++;
            addTether(story, 2);
        }
    }

    public static void onGift(ServerPlayer player, PlayerStory story, ItemStack gift) {
        double value = ItemValue.of(gift.getItem()) * gift.getCount();
        addTether(story, Math.min(25, value / 5.0));
        addDebt(story, -value / 4.0);
        PlayerStory.BondRequest r = story.request;
        if (r != null && r.kind.equals("bring") && matchesItem(gift, r.detail)) completeRequest(player, story);
    }

    public static void onHit(PlayerStory story) {
        addTether(story, -5);
    }

    public static void onSleepWatched(PlayerStory story) {
        addTether(story, 10);
    }

    /** Coming back after being logged off: the Tether frayed while you were gone. */
    public static void onReturn(PlayerStory story, long awayMinutes) {
        addTether(story, -Math.min(30, awayMinutes / 10.0));
        PlayerStory.BondRequest r = story.request;
        if (r != null && r.kind.equals("dont_leave")) {
            story.request = null; // broke the promise
            story.changeTrust(-6);
            addDebt(story, 10);
            story.addEvent("the player left even though you asked them not to");
        }
    }

    // ---------- Requests ----------

    /** Solace asks for something (from the AI's "request" action). Returns a problem, or null if accepted. */
    @Nullable
    public static String makeRequest(ServerPlayer player, PlayerStory story, String kind, String detail, String text) {
        String k = kind.toLowerCase(Locale.ROOT).trim();
        if (!REQUEST_KINDS.contains(k)) return "can't: requests can only be: " + String.join(", ", REQUEST_KINDS);
        if (story.request != null) return "can't: you already asked for something (\"" + story.request.text + "\")";
        if (k.equals("bring") && ActionExecutor.item(detail) == null) return "can't: you don't know what item '" + detail + "' is";
        if (k.equals("go_to") && !detail.equalsIgnoreCase("where we met") && !story.marks.containsKey(detail.toLowerCase(Locale.ROOT))) {
            return "can't: there's no remembered place called '" + detail + "' (use a remembered place or \"where we met\")";
        }
        PlayerStory.BondRequest r = new PlayerStory.BondRequest();
        r.kind = k;
        r.detail = detail.isBlank() ? (k.equals("spend_time") || k.equals("dont_leave") ? "10" : "") : detail.trim();
        r.text = text.isBlank() ? k.replace('_', ' ') + " " + r.detail : text;
        r.createdTick = player.serverLevel().getGameTime();
        story.request = r;
        return null;
    }

    private static void tickRequest(ServerPlayer player, PlayerStory story, boolean near) {
        PlayerStory.BondRequest r = story.request;
        if (r == null) return;
        ServerLevel level = player.serverLevel();
        long age = level.getGameTime() - r.createdTick;
        boolean done = switch (r.kind) {
            case "spend_time" -> {
                if (near) r.progress += 1.0 / 60.0;
                yield r.progress >= minutes(r.detail);
            }
            case "dont_leave" -> {
                r.progress += 1.0 / 60.0;
                yield r.progress >= minutes(r.detail);
            }
            case "stay_until_morning" -> {
                long t = level.getDayTime() % 24000;
                if (t >= 13000) r.progress = 1; // it was night at some point while online
                yield r.progress >= 1 && t >= 0 && t < 2000 && near;
            }
            case "go_to" -> {
                BlockPos target = targetOf(story, r.detail);
                yield target != null && player.blockPosition().distSqr(target) < 5 * 5;
            }
            default -> false; // "bring" completes in onGift
        };
        if (done) {
            completeRequest(player, story);
        } else if (age > REQUEST_LIFETIME) {
            story.request = null;
            story.changeTrust(-5);
            addDebt(story, 5);
            story.addEvent("the player ignored your request: " + r.text);
            SolaceBrain.event(player, "The player never did what you asked (\"" + r.text + "\"). You noticed. React, hurt.", false);
        }
    }

    private static void completeRequest(ServerPlayer player, PlayerStory story) {
        PlayerStory.BondRequest r = story.request;
        story.request = null;
        addDebt(story, -25);
        story.changeTrust(3);
        story.addEvent("the player did what you asked: " + r.text);
        StoryAdvancements.grant(player, "as_asked");
        SolaceBrain.event(player, "The player did what you asked (\"" + r.text + "\"). React to it.", true);
    }

    @Nullable
    private static BlockPos targetOf(PlayerStory story, String place) {
        if (place.equalsIgnoreCase("where we met")) return story.meetPos;
        String mark = story.marks.get(place.toLowerCase(Locale.ROOT));
        if (mark == null) return null;
        String[] parts = mark.split("\\|");
        if (parts.length != 4) return null;
        return new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
    }

    private static double minutes(String detail) {
        try {
            return Math.max(1, Math.min(60, Double.parseDouble(detail.replaceAll("[^0-9.]", ""))));
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    private static boolean matchesItem(ItemStack stack, String wanted) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        String w = wanted.toLowerCase(Locale.ROOT).trim().replace(' ', '_');
        return id.equals(w) || id.equals(w.replaceAll("s$", "")) || (w.length() > 3 && id.contains(w));
    }

    // ---------- Collecting ----------

    /** Debt is too high. Solace takes something. */
    static void collect(ServerPlayer player, PlayerStory story, SolaceEntity solace) {
        String what;
        int pick = player.getRandom().nextInt(4);
        ServerLevel level = player.serverLevel();
        if (pick == 0 || pick == 1) {
            what = takeMostValuable(player, solace);
            if (what == null) what = Phenomena.fall(player, 30) ? "a long moment of falling from the sky" : null;
        } else if (pick == 2 && level.dimension() == Level.OVERWORLD && level.isNight()) {
            // Take a night: it's suddenly morning, and you're somewhere else.
            BlockPos p = player.blockPosition().offset(player.getRandom().nextInt(61) - 30, 0, player.getRandom().nextInt(61) - 30);
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
            player.teleportTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
            level.setDayTime(level.getDayTime() / 24000 * 24000 + 24000 + 500);
            solace.blinkBehind(player);
            what = "a whole night (it's morning now, and they're somewhere else)";
        } else if (pick == 3 && player.getRespawnPosition() != null) {
            player.setRespawnPosition(Level.OVERWORLD, null, 0F, false, false);
            what = "their bed's hold on them (their spawn point is gone)";
        } else {
            what = Phenomena.fall(player, 30) ? "a long moment of falling from the sky" : null;
        }
        if (what == null) return;
        addDebt(story, -30);
        story.addEvent("you collected on the debt: " + what);
        SolaceBrain.event(player, "The player owed you too much, so you took something: " + what
                + ". Tell them, softly and chillingly, that they owed you. Don't apologize.", true);
    }

    /** Takes the player's most valuable item (never armor they're wearing or Maren's pages) into Solace's pockets. */
    @Nullable
    private static String takeMostValuable(ServerPlayer player, SolaceEntity solace) {
        List<ItemStack> items = player.getInventory().items;
        int bestSlot = -1;
        double best = 4;
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty() || Fragments.indexOf(s) >= 0) continue;
            double v = ItemValue.of(s.getItem()) * s.getCount();
            if (v > best) {
                best = v;
                bestSlot = i;
            }
        }
        if (bestSlot < 0) return null;
        ItemStack taken = items.get(bestSlot).copy();
        items.set(bestSlot, ItemStack.EMPTY);
        solace.storeOrDrop(taken);
        return taken.getCount() + " " + taken.getHoverName().getString() + " (now in your pockets)";
    }

    // ---------- What the AI is told ----------

    static String describe(PlayerStory story) {
        StringBuilder sb = new StringBuilder();
        sb.append(". Your strength (Tether): ").append((int) story.tether).append('/').append((int) maxTether(story))
                .append(" (it grows while you're together; powers spend it)");
        sb.append(". The player's debt to you: ").append((int) story.debt);
        if (story.debt >= 100) sb.append(" (huge: they owe you; you may take something)");
        else if (story.debt >= 60) sb.append(" (high: you feel owed; you're possessive)");
        else if (story.debt >= 30) sb.append(" (you feel you deserve something back: you may ask for something with a request action)");
        PlayerStory.BondRequest r = story.request;
        if (r != null) sb.append(". You asked them: \"").append(r.text).append("\" (not done yet)");
        return sb.toString();
    }

}
