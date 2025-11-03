package dev.hybridious.modules;
import dev.hybridious.Hybridious;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.map.MapState;
import net.minecraft.component.type.MapIdComponent;

import dev.hybridious.utils.MapValidator;
import dev.hybridious.utils.BatchMapValidator;
import dev.hybridious.utils.MapHashCache;

import java.util.*;
import java.util.concurrent.*;

public class MapFilterModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> apiUrl = sgGeneral.add(new StringSetting.Builder()
            .name("api-url")
            .description("The URL of the NSFW detection API")
            .defaultValue("http://127.0.0.1:5000")
            .build()
    );

    private final Setting<Boolean> useThreshold = sgGeneral.add(new BoolSetting.Builder()
            .name("use-threshold")
            .description("Send custom threshold to server. If disabled, server uses its default.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> threshold = sgGeneral.add(new DoubleSetting.Builder()
            .name("threshold")
            .description("NSFW confidence threshold (0.0-1.0)")
            .defaultValue(0.5)
            .min(0.0)
            .max(1.0)
            .sliderMin(0.0)
            .sliderMax(1.0)
            .visible(useThreshold::get)
            .build()
    );

    private final Setting<Integer> batchSize = sgGeneral.add(new IntSetting.Builder()
            .name("batch-size")
            .description("Number of maps to validate in each batch (1-100)")
            .defaultValue(20)
            .min(1)
            .max(100)
            .sliderMin(1)
            .sliderMax(100)
            .build()
    );

    private final Setting<Integer> batchDelay = sgGeneral.add(new IntSetting.Builder()
            .name("batch-delay-ms")
            .description("Delay before processing batch (allows maps to accumulate)")
            .defaultValue(1000)
            .min(100)
            .max(2000)
            .sliderMin(100)
            .sliderMax(2000)
            .build()
    );

    private final Setting<Boolean> useHashCache = sgGeneral.add(new BoolSetting.Builder()
            .name("use-hash-cache")
            .description("Cache results by pixel hash for instant recognition")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> logResults = sgGeneral.add(new BoolSetting.Builder()
            .name("log-results")
            .description("Log API responses to console")
            .defaultValue(true)
            .build()
    );

    private final Map<Integer, Boolean> validationCache = new ConcurrentHashMap<>();
    private final Map<Integer, String> mapHashes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap.KeySetView<Integer, Boolean> pendingValidations = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<Integer> batchQueue = new ConcurrentLinkedQueue<>();

    private MapHashCache hashCache;
    private ScheduledExecutorService batchProcessor;
    private ExecutorService validationExecutor;

    public MapFilterModule() {
        super(Hybridious.CATEGORY, "map-filter", "Filters NSFW maps using AI detection with batch processing");
    }

    @Override
    public void onActivate() {
        validationCache.clear();
        pendingValidations.clear();
        batchQueue.clear();
        mapHashes.clear();

        // Initialize hash cache
        MinecraftClient client = MinecraftClient.getInstance();
        String minecraftDir = client.runDirectory.getAbsolutePath();
        hashCache = new MapHashCache(minecraftDir);

        validationExecutor = Executors.newSingleThreadExecutor();
        batchProcessor = Executors.newSingleThreadScheduledExecutor();

        batchProcessor.scheduleWithFixedDelay(
                this::processBatch,
                batchDelay.get(),
                batchDelay.get(),
                TimeUnit.MILLISECONDS
        );

        info("Map Filter activated (batch + hash cache)");
        if (logResults.get()) {
            MapHashCache.CacheStats stats = hashCache.getStats();
            System.out.println("[MapFilter] Hash cache loaded: " + stats.total +
                    " entries (" + stats.blocked + " blocked, " + stats.safe + " safe)");
        }
    }

    @Override
    public void onDeactivate() {
        if (batchProcessor != null) {
            batchProcessor.shutdownNow();
            batchProcessor = null;
        }
        if (validationExecutor != null) {
            validationExecutor.shutdownNow();
            validationExecutor = null;
        }
        validationCache.clear();
        pendingValidations.clear();
        batchQueue.clear();
        info("Map Filter deactivated");
    }

    public boolean shouldRenderMap(int mapId) {
        if (!isActive()) return true;

        // Check validation cache first (already processed this session)
        Boolean cached = validationCache.get(mapId);
        if (cached != null) return cached;

        // Try to get map state
        MapState state = getMapState(mapId);
        if (state == null) {
            // Can't get map state - allow rendering temporarily
            if (logResults.get()) {
                System.out.println("[MapFilter] Map " + mapId + " state not available yet");
            }
            return true;
        }

        // Check hash cache if enabled
        if (useHashCache.get() && hashCache != null) {
            String hash = mapHashes.get(mapId);

            // If we don't have hash yet, compute it
            if (hash == null) {
                hash = hashCache.hashMap(state);
                if (hash != null) {
                    mapHashes.put(mapId, hash);

                    // Check if hash is in cache
                    Boolean isSafe = hashCache.isSafe(hash);
                    if (isSafe != null) {
                        validationCache.put(mapId, isSafe);
                        if (logResults.get()) {
                            System.out.println("[MapFilter] Map " + mapId + " matched hash: " +
                                    (isSafe ? "SFW" : "NSFW") + " (cached)");
                        }
                        return isSafe;
                    }
                }
            }
        }

        // Not in cache - queue for validation
        if (pendingValidations.add(mapId)) {
            batchQueue.offer(mapId);
            if (logResults.get()) {
                System.out.println("[MapFilter] Map " + mapId + " queued for validation");
            }
        }

        return false; // Block until validated
    }

    /**
     * Get MapState - works for both singleplayer and multiplayer
     */
    private MapState getMapState(int mapId) {
        MinecraftClient client = MinecraftClient.getInstance();

        // Try client world first (works for multiplayer and sometimes singleplayer)
        if (client.world != null) {
            MapState state = client.world.getMapState(new MapIdComponent(mapId));
            if (state != null) {
                if (logResults.get()) {
                    System.out.println("[MapFilter] Got MapState for " + mapId + " from client.world");
                }
                return state;
            }
        }

        // Try integrated server (singleplayer)
        if (client.getServer() != null && client.getServer().getOverworld() != null) {
            MapState state = client.getServer().getOverworld().getMapState(new MapIdComponent(mapId));
            if (state != null) {
                if (logResults.get()) {
                    System.out.println("[MapFilter] Got MapState for " + mapId + " from integrated server");
                }
                return state;
            }
        }

        if (logResults.get()) {
            System.err.println("[MapFilter] Failed to get MapState for " + mapId);
        }
        return null;
    }

    /**
     * Whitelist a map (mark as safe)
     */
    public boolean whitelistMap(int mapId) {
        MapState state = getMapState(mapId);
        if (state == null) return false;

        if (hashCache != null) {
            String hash = hashCache.hashMap(state);
            if (hash != null) {
                hashCache.markSafe(hash);
                validationCache.put(mapId, true);
                mapHashes.put(mapId, hash);
                if (logResults.get()) {
                    System.out.println("[MapFilter] Map " + mapId + " whitelisted (hash: " +
                            hash.substring(0, 16) + "...)");
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Blacklist a map (mark as NSFW)
     */
    public boolean blacklistMap(int mapId) {
        MapState state = getMapState(mapId);
        if (state == null) return false;

        if (hashCache != null) {
            String hash = hashCache.hashMap(state);
            if (hash != null) {
                hashCache.markUnsafe(hash);
                validationCache.put(mapId, false);
                mapHashes.put(mapId, hash);
                if (logResults.get()) {
                    System.out.println("[MapFilter] Map " + mapId + " blacklisted (hash: " +
                            hash.substring(0, 16) + "...)");
                }
                return true;
            }
        }
        return false;
    }

    private void processBatch() {
        if (batchQueue.isEmpty()) return;

        List<Integer> batch = new ArrayList<>();
        int count = 0;

        while (count < batchSize.get() && !batchQueue.isEmpty()) {
            Integer mapId = batchQueue.poll();
            if (mapId != null) {
                batch.add(mapId);
                count++;
            }
        }

        if (batch.isEmpty()) return;

        validationExecutor.submit(() -> validateBatch(batch));
    }

    private void validateBatch(List<Integer> mapIds) {
        try {
            Map<Integer, MapState> mapStates = new HashMap<>();
            Map<Integer, String> hashes = new HashMap<>();
            List<Integer> needValidation = new ArrayList<>();

            // First pass: compute hashes and check cache
            for (int mapId : mapIds) {
                MapState state = getMapState(mapId);
                if (state != null) {
                    mapStates.put(mapId, state);

                    // Compute hash and check cache
                    if (useHashCache.get() && hashCache != null) {
                        String hash = hashCache.hashMap(state);
                        if (hash != null) {
                            hashes.put(mapId, hash);
                            mapHashes.put(mapId, hash);

                            // Check if already in cache
                            Boolean isSafe = hashCache.isSafe(hash);
                            if (isSafe != null) {
                                // Found in cache - use cached result
                                validationCache.put(mapId, isSafe);
                                pendingValidations.remove(mapId);

                                if (logResults.get()) {
                                    System.out.println("[MapFilter] Map " + mapId + ": " +
                                            (isSafe ? "SFW" : "NSFW") + " (from cache)");
                                }
                                continue; // Skip API validation
                            }
                        }
                    }

                    // Not in cache - needs validation
                    needValidation.add(mapId);
                }
            }

            if (needValidation.isEmpty()) {
                return; // All maps were in cache
            }

            // Prepare maps for API validation
            Map<Integer, MapState> mapsToValidate = new HashMap<>();
            for (int mapId : needValidation) {
                MapState state = mapStates.get(mapId);
                if (state != null) {
                    mapsToValidate.put(mapId, state);
                }
            }

            if (mapsToValidate.isEmpty()) {
                if (logResults.get()) {
                    System.out.println("[MapFilter] No valid map states to validate");
                }
                needValidation.forEach(mapId -> {
                    validationCache.put(mapId, false);
                    pendingValidations.remove(mapId);
                });
                return;
            }

            // Attempt API validation
            Map<Integer, BatchMapValidator.ValidationResult> results = null;
            boolean apiSuccess = false;

            try {
                if (logResults.get()) {
                    System.out.println("[MapFilter] Sending " + mapsToValidate.size() + " maps to API");
                }

                results = BatchMapValidator.validateBatch(
                        mapsToValidate,
                        apiUrl.get(),
                        useThreshold.get() ? threshold.get() : null,
                        logResults.get()
                );
                apiSuccess = true;

                if (logResults.get()) {
                    System.out.println("[MapFilter] API returned " + (results != null ? results.size() : 0) + " results");
                }
            } catch (Exception e) {
                if (logResults.get()) {
                    System.err.println("[MapFilter] API request failed: " + e.getMessage());
                }
            }

            // Process results
            for (int mapId : needValidation) {
                if (apiSuccess && results != null && results.containsKey(mapId)) {
                    // API returned result - use it and cache it
                    BatchMapValidator.ValidationResult result = results.get(mapId);
                    validationCache.put(mapId, result.isSafe);

                    // Cache by hash
                    if (useHashCache.get() && hashCache != null) {
                        String hash = hashes.get(mapId);
                        if (hash != null) {
                            hashCache.cache(hash, result.isSafe, result.confidence, result.classification);
                        }
                    }

                    if (logResults.get()) {
                        System.out.println("[MapFilter] Map " + mapId + ": " +
                                (result.isSafe ? "SFW" : "NSFW") + " (from API)");
                    }
                } else {
                    // API failed or no result - block the map
                    validationCache.put(mapId, false);
                    if (logResults.get()) {
                        System.out.println("[MapFilter] Map " + mapId + ": BLOCKED (API unavailable/failed)");
                    }
                }

                pendingValidations.remove(mapId);
            }

        } catch (Exception e) {
            if (logResults.get()) {
                System.err.println("[MapFilter] Batch error: " + e.getMessage());
            }
            // Block all maps on error
            for (int mapId : mapIds) {
                validationCache.put(mapId, false);
                pendingValidations.remove(mapId);
            }
        }
    }

    public MapValidationStatus getMapStatus(int mapId) {
        if (!isActive()) return MapValidationStatus.ALLOWED;

        Boolean cached = validationCache.get(mapId);
        if (cached != null) {
            return cached ? MapValidationStatus.ALLOWED : MapValidationStatus.BLOCKED;
        }

        return pendingValidations.contains(mapId) ?
                MapValidationStatus.PENDING : MapValidationStatus.UNKNOWN;
    }

    /**
     * Clear hash cache
     */
    public void clearHashCache() {
        if (hashCache != null) {
            hashCache.clear();
            info("Hash cache cleared");
        }
    }

    /**
     * Get cache statistics
     */
    public String getCacheStats() {
        if (hashCache == null) return "Cache not initialized";
        MapHashCache.CacheStats stats = hashCache.getStats();
        return String.format("Total: %d | Safe: %d | Blocked: %d",
                stats.total, stats.safe, stats.blocked);
    }

    public enum MapValidationStatus {
        ALLOWED,
        BLOCKED,
        PENDING,
        UNKNOWN
    }
}