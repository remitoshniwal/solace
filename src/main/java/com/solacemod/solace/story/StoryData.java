package com.solacemod.solace.story;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Saves every player's {@link PlayerStory} inside the world folder (data/solace_story.dat). */
public final class StoryData extends SavedData {
    private static final String NAME = "solace_story";
    private final Map<UUID, PlayerStory> stories = new HashMap<>();

    // Daily AI request counter, so the cost limit survives restarts.
    public String requestDay = "";
    public int requestsToday;

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

    public Map<UUID, PlayerStory> all() {
        return stories;
    }

    /** Counts one AI request. Returns false if today's limit is already used up. */
    public boolean tryUseRequest(int dailyLimit) {
        String today = LocalDate.now().toString();
        if (!today.equals(requestDay)) {
            requestDay = today;
            requestsToday = 0;
        }
        if (dailyLimit > 0 && requestsToday >= dailyLimit) return false;
        requestsToday++;
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag players = new CompoundTag();
        stories.forEach((id, story) -> players.put(id.toString(), story.save()));
        tag.put("Players", players);
        tag.putString("RequestDay", requestDay);
        tag.putInt("RequestsToday", requestsToday);
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
        data.requestDay = tag.getString("RequestDay");
        data.requestsToday = tag.getInt("RequestsToday");
        return data;
    }
}
