package com.solacemod.solace.story;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.solacemod.solace.Solace;
import com.solacemod.solace.config.SolaceConfig;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What Solace remembers about you across ALL your worlds. Stored in &lt;game folder&gt;/solace/memory/&lt;your-uuid&gt;.json.
 * Delete that file (or use /solace forget confirm) to make Solace forget you everywhere.
 */
public final class GlobalMemory {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, Entry> CACHE = new HashMap<>();

    public static final class Entry {
        public String name = "";
        public List<String> facts = new ArrayList<>();
        public int worldsMet;
        public List<String> endings = new ArrayList<>();
        public long lastSeenMs;
    }

    private GlobalMemory() {
    }

    public static boolean enabled() {
        return SolaceConfig.CROSS_WORLD_MEMORY.get();
    }

    private static Path file(UUID id) {
        return FMLPaths.GAMEDIR.get().resolve("solace").resolve("memory").resolve(id + ".json");
    }

    public static Entry get(UUID id) {
        return CACHE.computeIfAbsent(id, key -> {
            Path f = file(key);
            if (Files.exists(f)) {
                try {
                    Entry e = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Entry.class);
                    if (e != null) {
                        if (e.facts == null) e.facts = new ArrayList<>();
                        if (e.endings == null) e.endings = new ArrayList<>();
                        return e;
                    }
                } catch (IOException | RuntimeException ex) {
                    Solace.LOGGER.warn("Could not read Solace memory file {}", f, ex);
                }
            }
            return new Entry();
        });
    }

    public static void save(UUID id) {
        if (!enabled()) return;
        Entry e = CACHE.get(id);
        if (e == null) return;
        Path f = file(id);
        try {
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(e), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            Solace.LOGGER.warn("Could not save Solace memory file {}", f, ex);
        }
    }

    public static void addFact(UUID id, String fact) {
        if (!enabled()) return;
        Entry e = get(id);
        for (String f : e.facts) if (f.equalsIgnoreCase(fact)) return;
        e.facts.add(fact);
        while (e.facts.size() > 20) e.facts.remove(0);
        save(id);
    }

    public static void recordEnding(UUID id, Ending ending) {
        if (!enabled()) return;
        Entry e = get(id);
        e.endings.add(ending.name());
        save(id);
    }

    public static long distinctEndings(UUID id) {
        return get(id).endings.stream().distinct().count();
    }

    public static void forget(UUID id) {
        CACHE.put(id, new Entry());
        try {
            Files.deleteIfExists(file(id));
        } catch (IOException ex) {
            Solace.LOGGER.warn("Could not delete Solace memory file", ex);
        }
    }
}
