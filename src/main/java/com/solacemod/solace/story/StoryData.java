package com.solacemod.solace.story;

import com.solacemod.solace.ai.RequestBudget;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Saves every player's {@link PlayerStory} inside the world folder (data/solace_story.dat). */
public final class StoryData extends SavedData {
    private static final String NAME = "solace_story";
    private final Map<UUID, PlayerStory> stories = new HashMap<>();
    /** Players who have met Solace in this world at least once. Kept through /solace reset, so a reset isn't counted as a new world. */
    public final Set<UUID> metHere = new HashSet<>();
    /** The endings each player has reached in this world, oldest first. Kept through /solace reset. */
    public final Map<UUID, List<Ending>> endedHere = new HashMap<>();

    /** AI request counts (whole server and each player), so the cost limits survive restarts. */
    public final RequestBudget budget = new RequestBudget();

    public static StoryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StoryData::new, StoryData::load, null), NAME);
    }

    public PlayerStory get(UUID player) {
        return stories.computeIfAbsent(player, PlayerStory::new);
    }

    public PlayerStory getIfPresent(UUID player) {
        return stories.get(player);
    }

    public void reset(UUID player) {
        stories.remove(player);
        setDirty();
    }

    public void recordEnding(UUID player, Ending ending) {
        endedHere.computeIfAbsent(player, k -> new ArrayList<>()).add(ending);
        setDirty();
    }

    public Map<UUID, PlayerStory> all() {
        return stories;
    }

    /** Today's date as the budget counts it. */
    public static String today() {
        return LocalDate.now().toString();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag players = new CompoundTag();
        stories.forEach((id, story) -> players.put(id.toString(), story.save()));
        tag.put("Players", players);
        tag.putString("RequestDay", budget.day());
        tag.putInt("RequestsToday", budget.total());
        CompoundTag perPlayer = new CompoundTag();
        budget.perPlayer().forEach(perPlayer::putInt);
        tag.put("RequestsByPlayer", perPlayer);
        net.minecraft.nbt.ListTag met = new net.minecraft.nbt.ListTag();
        for (UUID id : metHere) met.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
        tag.put("MetHere", met);
        CompoundTag ended = new CompoundTag();
        endedHere.forEach((id, list) -> ended.putString(id.toString(), String.join(",", list.stream().map(Enum::name).toList())));
        tag.put("EndedHere", ended);
        return tag;
    }

    private static StoryData load(CompoundTag tag, HolderLookup.Provider registries) {
        StoryData data = new StoryData();
        CompoundTag players = tag.getCompound("Players");
        for (String key : players.getAllKeys()) {
            try {
                UUID id = UUID.fromString(key);
                data.stories.put(id, PlayerStory.load(id, players.getCompound(key)));
            } catch (IllegalArgumentException ignored) {
                // skip corrupted entries instead of crashing
            }
        }
        Map<String, Integer> perPlayer = new HashMap<>();
        CompoundTag byPlayer = tag.getCompound("RequestsByPlayer");
        for (String key : byPlayer.getAllKeys()) perPlayer.put(key, byPlayer.getInt(key));
        data.budget.restore(tag.getString("RequestDay"), tag.getInt("RequestsToday"), perPlayer);
        net.minecraft.nbt.ListTag met = tag.getList("MetHere", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < met.size(); i++) {
            try {
                data.metHere.add(UUID.fromString(met.getString(i)));
            } catch (IllegalArgumentException ignored) {
                // skip a broken entry
            }
        }
        // Worlds from before 1.10.0: everyone with a story has met Solace here.
        data.stories.forEach((id, story) -> {
            if (story.met) data.metHere.add(id);
        });
        CompoundTag ended = tag.getCompound("EndedHere");
        for (String key : ended.getAllKeys()) {
            try {
                List<Ending> list = new ArrayList<>();
                for (String name : ended.getString(key).split(",")) {
                    Ending e = Ending.parse(name);
                    if (e != Ending.NONE) list.add(e);
                }
                data.endedHere.put(UUID.fromString(key), list);
            } catch (IllegalArgumentException ignored) {
                // skip a broken entry
            }
        }
        // Worlds from before 1.10.0: the ending each saved story already has.
        data.stories.forEach((id, story) -> {
            if (story.ending != Ending.NONE && !data.endedHere.containsKey(id)) data.endedHere.put(id, new ArrayList<>(List.of(story.ending)));
        });
        return data;
    }
}
