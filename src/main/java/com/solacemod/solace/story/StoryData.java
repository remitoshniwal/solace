package com.solacemod.solace.story;

import com.solacemod.solace.ai.RequestBudget;
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
        return data;
    }
}
