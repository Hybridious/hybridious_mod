package dev.hybridious.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.item.map.MapState;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MapHashCache {
    private static final String CACHE_DIR = "meteor-client/hybridious_mod";
    private static final String NSFW_CACHE = "nsfw_maps.json";
    private static final String SFW_CACHE = "sfw_maps.json";

    private final Map<String, CacheEntry> nsfwCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry> sfwCache = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path cacheDir;
    private final Path nsfwPath;
    private final Path sfwPath;

    public static class CacheEntry {
        public double confidence;
        public String classification;
        public long timestamp;

        public CacheEntry(double confidence, String classification) {
            this.confidence = confidence;
            this.classification = classification;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public MapHashCache(String minecraftDir) {
        this.cacheDir = Paths.get(minecraftDir, CACHE_DIR);
        this.nsfwPath = cacheDir.resolve(NSFW_CACHE);
        this.sfwPath = cacheDir.resolve(SFW_CACHE);

        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            System.err.println("[MapFilter] Failed to create cache directory: " + e.getMessage());
        }

        loadCache();
    }

    public String hashMap(MapState mapState) {
        try {
            byte[] colors = extractMapColors(mapState);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(colors);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return null;
        }
    }

    public CacheEntry getCached(String hash) {
        if (hash == null) return null;
        CacheEntry nsfw = nsfwCache.get(hash);
        if (nsfw != null) return nsfw;
        return sfwCache.get(hash);
    }

    public Boolean isSafe(String hash) {
        if (hash == null) return null;
        if (nsfwCache.containsKey(hash)) return false;
        if (sfwCache.containsKey(hash)) return true;
        return null;
    }

    public void cache(String hash, boolean isSafe, double confidence, String classification) {
        if (hash == null) return;

        CacheEntry entry = new CacheEntry(confidence, classification);

        // Remove from opposite cache first
        if (isSafe) {
            nsfwCache.remove(hash);
            sfwCache.put(hash, entry);
            saveCache(sfwPath, sfwCache);
            saveCache(nsfwPath, nsfwCache);
        } else {
            sfwCache.remove(hash);
            nsfwCache.put(hash, entry);
            saveCache(nsfwPath, nsfwCache);
            saveCache(sfwPath, sfwCache);
        }
    }

    public void markSafe(String hash) {
        if (hash == null) return;

        CacheEntry entry = new CacheEntry(1.0, "SFW (Manual Override)");
        nsfwCache.remove(hash); // Remove from blacklist
        sfwCache.put(hash, entry);

        saveCache(sfwPath, sfwCache);
        saveCache(nsfwPath, nsfwCache);
    }

    public void markUnsafe(String hash) {
        if (hash == null) return;

        CacheEntry entry = new CacheEntry(1.0, "NSFW (Manual Blacklist)");
        sfwCache.remove(hash); // Remove from whitelist
        nsfwCache.put(hash, entry);

        saveCache(nsfwPath, nsfwCache);
        saveCache(sfwPath, sfwCache);
    }

    private void loadCache() {
        loadCacheFile(nsfwPath, nsfwCache);
        loadCacheFile(sfwPath, sfwCache);
        System.out.println("[MapFilter] Loaded cache: " + nsfwCache.size() + " NSFW, " + sfwCache.size() + " SFW");
    }

    private void loadCacheFile(Path path, Map<String, CacheEntry> cache) {
        if (!Files.exists(path)) return;

        try (Reader reader = new FileReader(path.toFile())) {
            Map<String, CacheEntry> loaded = gson.fromJson(reader,
                    new TypeToken<Map<String, CacheEntry>>(){}.getType());
            if (loaded != null) {
                cache.putAll(loaded);
            }
        } catch (Exception e) {
            System.err.println("[MapFilter] Failed to load " + path.getFileName() + ": " + e.getMessage());
        }
    }

    private void saveCache(Path path, Map<String, CacheEntry> cache) {
        try (Writer writer = new FileWriter(path.toFile())) {
            gson.toJson(cache, writer);
        } catch (Exception e) {
            System.err.println("[MapFilter] Failed to save " + path.getFileName() + ": " + e.getMessage());
        }
    }

    public void clear() {
        nsfwCache.clear();
        sfwCache.clear();
        try {
            Files.deleteIfExists(nsfwPath);
            Files.deleteIfExists(sfwPath);
        } catch (IOException e) {
            System.err.println("[MapFilter] Failed to delete cache files: " + e.getMessage());
        }
    }

    public CacheStats getStats() {
        return new CacheStats(nsfwCache.size() + sfwCache.size(), sfwCache.size(), nsfwCache.size());
    }

    public static class CacheStats {
        public final int total;
        public final int safe;
        public final int blocked;

        public CacheStats(int total, int safe, int blocked) {
            this.total = total;
            this.safe = safe;
            this.blocked = blocked;
        }
    }

    private byte[] extractMapColors(MapState mapState) throws Exception {
        try {
            Field colorsField = MapState.class.getField("colors");
            return (byte[]) colorsField.get(mapState);
        } catch (Exception e1) {
            try {
                Field colorsField = MapState.class.getDeclaredField("colors");
                colorsField.setAccessible(true);
                return (byte[]) colorsField.get(mapState);
            } catch (Exception e2) {
                try {
                    Field colorsField = MapState.class.getDeclaredField("field_122");
                    colorsField.setAccessible(true);
                    return (byte[]) colorsField.get(mapState);
                } catch (Exception e3) {
                    for (Field field : MapState.class.getDeclaredFields()) {
                        if (field.getType().equals(byte[].class)) {
                            field.setAccessible(true);
                            byte[] array = (byte[]) field.get(mapState);
                            if (array != null && array.length == 16384) {
                                return array;
                            }
                        }
                    }
                    throw e3;
                }
            }
        }
    }
}