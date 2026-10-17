package com.solacemod.solace.story;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Everything Solace knows and remembers about one player in this world. Saved with the world. */
public final class PlayerStory {
    public final UUID playerId;
    public String playerName = "";

    // Story progress
    public boolean met;
    public UUID solaceId;
    public int act = 1;
    public long playTicks;
    public int trust = 20;
    public final Set<Integer> fragmentsFound = new HashSet<>();
    public final Set<Integer> fragmentsDelivered = new HashSet<>();
    public final Map<Integer, Long> fragmentDeliveredAt = new LinkedHashMap<>();
    public final Set<String> beatsDone = new HashSet<>();
    public Ending ending = Ending.NONE;
    public boolean choicePending;
    public long lastChoicePromptTick;
    public boolean bossActive;
    public long defiantUntilTick;

    // Memory
    public final List<String> facts = new ArrayList<>();
    public final List<Turn> history = new ArrayList<>();
    public String summary = "";
    public final List<String> recentEvents = new ArrayList<>();

    // Relationship stats
    public long lastLogoutMs;
    public int deaths;
    public int timesHurtSolace;
    public int giftsGiven;
    public long lastHurtCommentTick;

    // Solace's body (backup so it survives chunk unloading / dimension changes)
    public ListTag inventoryBackup = new ListTag();
    public String mode = "FOLLOW";
    public boolean guard;
    public BlockPos lastPos = BlockPos.ZERO;
    public String lastDim = "minecraft:overworld";
    public long respawnAtTick = -1;

    // Saved places: name -> "dimension|x|y|z"
    public final Map<String, String> marks = new LinkedHashMap<>();

    // Timing (not saved; reset each session)
    public long lastInteractionTick;
    public long lastAmbientTick;
    public long lastHorrorTick;
    public long nextHorrorTick;

    public record Turn(String role, String text) {
    }

    public PlayerStory(UUID playerId) {
        this.playerId = playerId;
    }

    public double playMinutes() {
        return playTicks / 1200.0;
    }

    public boolean storyOver() {
        return ending != Ending.NONE;
    }

    /** True while Solace should exist in the world for this player. */
    public boolean solaceShouldExist() {
        return met && ending != Ending.RELEASE && ending != Ending.ERASE;
    }

    public void addEvent(String event) {
        recentEvents.add(event);
        while (recentEvents.size() > 10) recentEvents.remove(0);
    }

    public void addFact(String fact) {
        String f = fact.trim();
        if (f.isEmpty()) return;
        for (String existing : facts) {
            if (existing.equalsIgnoreCase(f)) return;
        }
        facts.add(f);
        while (facts.size() > 40) facts.remove(0);
    }

    public void changeTrust(int delta) {
        trust = Math.max(-100, Math.min(100, trust + delta));
    }

    public String trustWord() {
        if (trust >= 70) return "you adore and trust them completely";
        if (trust >= 40) return "you trust them";
        if (trust >= 10) return "you like them but are a little unsure";
        if (trust >= -20) return "you are wary of them";
        if (trust >= -50) return "they have hurt you; you resent them";
        return "you feel betrayed; you are bitter and cold";
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Name", playerName);
        t.putBoolean("Met", met);
        if (solaceId != null) t.putUUID("SolaceId", solaceId);
        t.putInt("Act", act);
        t.putLong("PlayTicks", playTicks);
        t.putInt("Trust", trust);
        t.putIntArray("Fragments", fragmentsFound.stream().mapToInt(Integer::intValue).toArray());
        CompoundTag delivered = new CompoundTag();
        fragmentDeliveredAt.forEach((k, v) -> delivered.putLong(String.valueOf(k), v));
        t.put("Delivered", delivered);
        t.put("Beats", strings(beatsDone));
        t.putString("Ending", ending.name());
        t.putBoolean("ChoicePending", choicePending);
        t.putBoolean("BossActive", bossActive);
        t.put("Facts", strings(facts));
        ListTag hist = new ListTag();
        for (Turn turn : history) {
            CompoundTag h = new CompoundTag();
            h.putString("R", turn.role());
            h.putString("T", turn.text());
            hist.add(h);
        }
        t.put("History", hist);
        t.putString("Summary", summary);
        t.put("Events", strings(recentEvents));
        t.putLong("LastLogout", lastLogoutMs);
        t.putInt("Deaths", deaths);
        t.putInt("HurtSolace", timesHurtSolace);
        t.putInt("Gifts", giftsGiven);
        t.put("Inventory", inventoryBackup);
        t.putString("Mode", mode);
        t.putBoolean("Guard", guard);
        t.putLong("LastPos", lastPos.asLong());
        t.putString("LastDim", lastDim);
        CompoundTag m = new CompoundTag();
        marks.forEach(m::putString);
        t.put("Marks", m);
        return t;
    }

    public static PlayerStory load(UUID id, CompoundTag t) {
        PlayerStory s = new PlayerStory(id);
        s.playerName = t.getString("Name");
        s.met = t.getBoolean("Met");
        if (t.hasUUID("SolaceId")) s.solaceId = t.getUUID("SolaceId");
        s.act = Math.max(1, t.getInt("Act"));
        s.playTicks = t.getLong("PlayTicks");
        s.trust = t.contains("Trust") ? t.getInt("Trust") : 20;
        for (int f : t.getIntArray("Fragments")) s.fragmentsFound.add(f);
        CompoundTag delivered = t.getCompound("Delivered");
        for (String k : delivered.getAllKeys()) {
            try {
                int n = Integer.parseInt(k);
                s.fragmentsDelivered.add(n);
                s.fragmentDeliveredAt.put(n, delivered.getLong(k));
            } catch (NumberFormatException ignored) {
                // skip bad entries
            }
        }
        readStrings(t.getList("Beats", Tag.TAG_STRING), s.beatsDone);
        s.ending = Ending.parse(t.getString("Ending"));
        s.choicePending = t.getBoolean("ChoicePending");
        s.bossActive = t.getBoolean("BossActive");
        readStrings(t.getList("Facts", Tag.TAG_STRING), s.facts);
        for (Tag tag : t.getList("History", Tag.TAG_COMPOUND)) {
            CompoundTag h = (CompoundTag) tag;
            s.history.add(new Turn(h.getString("R"), h.getString("T")));
        }
        s.summary = t.getString("Summary");
        readStrings(t.getList("Events", Tag.TAG_STRING), s.recentEvents);
        s.lastLogoutMs = t.getLong("LastLogout");
        s.deaths = t.getInt("Deaths");
        s.timesHurtSolace = t.getInt("HurtSolace");
        s.giftsGiven = t.getInt("Gifts");
        s.inventoryBackup = t.getList("Inventory", Tag.TAG_COMPOUND);
        s.mode = t.contains("Mode") ? t.getString("Mode") : "FOLLOW";
        s.guard = t.getBoolean("Guard");
        s.lastPos = BlockPos.of(t.getLong("LastPos"));
        s.lastDim = t.contains("LastDim") ? t.getString("LastDim") : "minecraft:overworld";
        CompoundTag m = t.getCompound("Marks");
        for (String k : m.getAllKeys()) s.marks.put(k, m.getString(k));
        return s;
    }

    private static ListTag strings(Iterable<String> values) {
        ListTag list = new ListTag();
        for (String v : values) list.add(StringTag.valueOf(v));
        return list;
    }

    private static void readStrings(ListTag list, java.util.Collection<String> into) {
        for (int i = 0; i < list.size(); i++) into.add(list.getString(i));
    }
}
