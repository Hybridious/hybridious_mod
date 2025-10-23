package dev.hybridious.modules;

import dev.hybridious.Hybridious;
import net.minecraft.util.math.BlockPos;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.util.math.Vec3d;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;


public class MinecartDetector extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgLogging = settings.createGroup("Logging");

    // General Settings
    private final Setting<Boolean> highlightIncorrectDirection = sgGeneral.add(new BoolSetting.Builder()
            .name("highlight-incorrect-direction")
            .description("Highlights hopper minecarts that are not facing south.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> detectEntityStacking = sgGeneral.add(new BoolSetting.Builder()
            .name("detect-entity-stacking")
            .description("Alerts when chest minecarts are entity stacked.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> excludeWaterMinecarts = sgGeneral.add(new BoolSetting.Builder()
            .name("exclude-water-minecarts")
            .description("Excludes minecarts that have water nearby from highlighting.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> playSoundAlert = sgGeneral.add(new BoolSetting.Builder()
            .name("play-sound-alert")
            .description("Plays a sound when stacked minecarts are detected.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> checkRadius = sgGeneral.add(new DoubleSetting.Builder()
            .name("check-radius")
            .description("The radius to check for entity stacking.")
            .defaultValue(0.5)
            .min(0.1)
            .sliderRange(0.1, 2)
            .build()
    );

    private final Setting<Integer> alertCooldown = sgGeneral.add(new IntSetting.Builder()
            .name("alert-cooldown")
            .description("Cooldown in ticks between alerts for the same stacked entities.")
            .defaultValue(100)
            .min(1)
            .sliderRange(1, 200)
            .build()
    );

    private final Setting<Integer> checkFrequency = sgGeneral.add(new IntSetting.Builder()
            .name("check-frequency")
            .description("How often to check for minecarts (in ticks). Higher values = less lag.")
            .defaultValue(20)
            .min(5)
            .sliderRange(5, 100)
            .build()
    );

    private final Setting<Double> detectionRange = sgGeneral.add(new DoubleSetting.Builder()
            .name("detection-range")
            .description("The maximum distance from the player to detect minecarts.")
            .defaultValue(128.0)
            .min(16.0)
            .sliderRange(16.0, 256.0)
            .build()
    );

    private final Setting<Boolean> streamingMode = sgGeneral.add(new BoolSetting.Builder()
            .name("streaming-mode")
            .description("Hides coordinates in chat messages but still logs them to file.")
            .defaultValue(false)
            .build()
    );

    // Logging Settings
    private final Setting<Boolean> logToFile = sgLogging.add(new BoolSetting.Builder()
            .name("log-to-file")
            .description("Logs detected minecarts to file.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> logStackedMinecarts = sgLogging.add(new BoolSetting.Builder()
            .name("log-stacked-minecarts")
            .description("Logs stacked minecarts to STACKED_MINECARTS.txt")
            .defaultValue(true)
            .visible(() -> logToFile.get())
            .build()
    );

    private final Setting<Boolean> logWrongDirectionMinecarts = sgLogging.add(new BoolSetting.Builder()
            .name("log-wrong-direction-minecarts")
            .description("Logs minecarts facing the wrong direction to WRONG_DIRECTION_MINECARTS.txt")
            .defaultValue(true)
            .visible(() -> logToFile.get())
            .build()
    );

    private final Setting<Boolean> notifyWrongDirection = sgLogging.add(new BoolSetting.Builder()
            .name("notify-wrong-direction")
            .description("Sends a chat message when a minecart facing the wrong direction is found.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> wrongDirectionCooldown = sgLogging.add(new IntSetting.Builder()
            .name("wrong-direction-cooldown")
            .description("Cooldown in ticks between alerts for wrong direction minecarts.")
            .defaultValue(100)
            .min(1)
            .sliderRange(1, 200)
            .visible(() -> notifyWrongDirection.get())
            .build()
    );

    // Render Settings
    public enum RenderMode {
        Line,
        Box,
        Tracer,
        Text
    }

    private final Setting<RenderMode> renderMode = sgRender.add(new EnumSetting.Builder<RenderMode>()
            .name("render-mode")
            .description("How incorrectly oriented minecarts are rendered.")
            .defaultValue(RenderMode.Box)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both)
            .visible(() -> renderMode.get() == RenderMode.Box)
            .build()
    );

    private final Setting<Double> tracerThickness = sgRender.add(new DoubleSetting.Builder()
            .name("tracer-thickness")
            .description("The thickness of tracer lines.")
            .defaultValue(1.5)
            .min(0.1)
            .max(5.0)
            .sliderRange(0.1, 5.0)
            .visible(() -> renderMode.get() == RenderMode.Tracer || renderMode.get() == RenderMode.Line)
            .build()
    );

    private final Setting<SettingColor> incorrectDirectionColor = sgRender.add(new ColorSetting.Builder()
            .name("incorrect-direction-color")
            .description("The color of hopper minecarts facing the incorrect direction.")
            .defaultValue(new SettingColor(255, 0, 0, 75))
            .build()
    );

    private final Setting<SettingColor> stackedEntityColor = sgRender.add(new ColorSetting.Builder()
            .name("stacked-entity-color")
            .description("The color of stacked chest minecarts.")
            .defaultValue(new SettingColor(255, 255, 0, 75))
            .build()
    );

    // Tracking variables - using more efficient data structures and caching
    private final Map<Integer, Long> alertCooldowns = new HashMap<>();
    private final Map<Integer, Long> wrongDirectionCooldowns = new HashMap<>();
    private final Set<Entity> badHopperMinecarts = new HashSet<>();
    private final Set<Entity> stackedMinecarts = new HashSet<>();

    // Track unique stacked minecart locations to avoid repeated alerts for the same location
    private final Map<String, Long> knownStackedLocations = new HashMap<>();
    private final Map<String, Long> knownWrongDirectionLocations = new HashMap<>();

    // Cache for checked entities to prevent redundant checks - limited size
    private final Map<Integer, Boolean> orientationCache = new LinkedHashMap<>(100, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Boolean> eldest) {
            return size() > 100; // Limit cache size
        }
    };

    private final Map<BlockPos, Boolean> waterCache = new LinkedHashMap<>(100, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockPos, Boolean> eldest) {
            return size() > 100; // Limit cache size
        }
    };

    // Files for logging minecarts
    private File stackedMinecartLogFile;
    private File wrongDirectionLogFile;

    // Yaw angle for correct direction - minecarts should be facing SOUTH (180 degrees)
    private final float CORRECT_YAW = 180.0f;
    private final float YAW_TOLERANCE = 1.0f; // Tolerance for angle comparison

    private int tickCounter = 0;
    private long lastCacheCleanTime = 0;

    public MinecartDetector() {
        super(Hybridious.CATEGORY, "Minecart Detector", "Detects and highlights special minecart configurations.");
    }

    @Override
    public void onActivate() {
        // Clear all caches when the module is activated
        clearAllCaches();

        // Initialize log files
        if (logToFile.get()) {
            initializeLogFiles();
        }
    }

    private void initializeLogFiles() {
        try {
            // Create hybridious_mod directory in the meteor-client folder
            File hybridModDir = meteordevelopment.meteorclient.MeteorClient.FOLDER.toPath().resolve("hybridious_mod").toFile();

            // Create the directory if it doesn't exist
            if (!hybridModDir.exists()) {
                hybridModDir.mkdirs();
            }

            if (logStackedMinecarts.get()) {
                stackedMinecartLogFile = new File(hybridModDir, "STACKED_MINECARTS.txt");
                // Only create file if it doesn't exist, don't write anything yet
                if (!stackedMinecartLogFile.exists()) {
                    stackedMinecartLogFile.createNewFile();
                }
            }

            if (logWrongDirectionMinecarts.get()) {
                wrongDirectionLogFile = new File(hybridModDir, "WRONG_DIRECTION_MINECARTS.txt");
                // Only create file if it doesn't exist, don't write anything yet
                if (!wrongDirectionLogFile.exists()) {
                    wrongDirectionLogFile.createNewFile();
                }
            }
        } catch (IOException e) {
            error("Failed to initialize log files: " + e.getMessage());
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        tickCounter++;

        // Only run checks at the specified frequency
        if (tickCounter % checkFrequency.get() != 0) return;

        // Clear caches periodically to prevent stale data
        if (System.currentTimeMillis() - lastCacheCleanTime > 10000) { // 10 seconds
            clearAllCaches();
            lastCacheCleanTime = System.currentTimeMillis();
        }

        // Skip processing if all features are disabled
        if (!highlightIncorrectDirection.get() && !detectEntityStacking.get()) return;

        // Clear entity sets for this tick
        badHopperMinecarts.clear();
        stackedMinecarts.clear();

        // Clean up expired cooldowns
        clearExpiredCooldowns();

        // Process entities - limited to nearby entities only
        int processedEntities = 0;
        List<ChestMinecartEntity> chestMinecarts = new ArrayList<>();
        Set<String> currentWrongDirectionLocations = new HashSet<>();

        // First pass - collect entities of interest with early filtering
        for (Entity entity : mc.world.getEntities()) {
            // Limit processing to configured detection range
            if (entity.distanceTo(mc.player) > detectionRange.get()) continue;

            // Limit total entities processed
            if (processedEntities++ > 50) break;

            // Process hopper minecarts
            if (highlightIncorrectDirection.get() && entity instanceof HopperMinecartEntity) {
                checkHopperMinecart((HopperMinecartEntity)entity, currentWrongDirectionLocations);
            }

            // Collect chest minecarts for stacking check
            if (detectEntityStacking.get() && entity instanceof ChestMinecartEntity) {
                chestMinecarts.add((ChestMinecartEntity)entity);
            }
        }

        // Clean up old wrong direction locations that we didn't see this time
        if (!currentWrongDirectionLocations.isEmpty() && logWrongDirectionMinecarts.get()) {
            knownWrongDirectionLocations.entrySet().removeIf(entry ->
                    !currentWrongDirectionLocations.contains(entry.getKey()) &&
                            System.currentTimeMillis() - entry.getValue() > 300000); // 5 minutes
        }

        // Second pass - check for entity stacking (much more efficient now)
        if (detectEntityStacking.get() && !chestMinecarts.isEmpty()) {
            checkForEntityStacking(chestMinecarts);
        }
    }

    private void checkHopperMinecart(HopperMinecartEntity entity, Set<String> currentWrongDirectionLocations) {
        // Skip if correctly oriented
        if (isCorrectlyOriented(entity)) return;

        // Skip if near water and we're excluding water minecarts
        if (excludeWaterMinecarts.get() && hasWaterNearby(entity.getBlockPos())) return;

        // Add to the set for rendering
        badHopperMinecarts.add(entity);

        // Handle wrong direction logging and notification
        if (logWrongDirectionMinecarts.get() || notifyWrongDirection.get()) {
            Vec3d pos = entity.getPos();
            String locationKey = String.format("%d,%d,%d",
                    (int)Math.round(pos.x),
                    (int)Math.round(pos.y),
                    (int)Math.round(pos.z));

            // Add to current session locations
            currentWrongDirectionLocations.add(locationKey);

            // Check if we've already alerted for this location
            boolean shouldNotify = !knownWrongDirectionLocations.containsKey(locationKey);

            // Also check entity-specific cooldown
            if (shouldNotify && notifyWrongDirection.get()) {
                int entityId = entity.getId();
                long now = System.currentTimeMillis();

                if (wrongDirectionCooldowns.containsKey(entityId)) {
                    long cooldownEnd = wrongDirectionCooldowns.get(entityId);
                    if (now < cooldownEnd) {
                        shouldNotify = false;
                    }
                }

                if (shouldNotify) {
                    // Add to cooldowns
                    long cooldownTime = wrongDirectionCooldown.get() * 50L; // convert ticks to milliseconds
                    wrongDirectionCooldowns.put(entityId, now + cooldownTime);
                }
            }

            if (shouldNotify) {
                // Log the new wrong direction minecart
                String logMessage = String.format(
                        "[%s] Wrong direction hopper minecart at X: %.2f, Y: %.2f, Z: %.2f, Yaw: %.1f in %s",
                        getCurrentTimeStamp(), pos.x, pos.y, pos.z, entity.getYaw(),
                        mc.world.getRegistryKey().getValue().toString()
                );

                // Send client-side notification
                if (notifyWrongDirection.get()) {
                    String chatMessage;
                    if (streamingMode.get()) {
                        chatMessage = "[MinecartDetector] Detected hopper minecart facing wrong direction [COORDINATES HIDDEN]";
                    } else {
                        chatMessage = String.format("[MinecartDetector] Detected hopper minecart facing wrong direction at X: %.1f, Y: %.1f, Z: %.1f, Yaw: %.1f",
                                pos.x, pos.y, pos.z, entity.getYaw());
                    }

                    ChatUtils.info(chatMessage);
                }

                // Log to file if enabled
                if (logWrongDirectionMinecarts.get() && wrongDirectionLogFile != null) {
                    writeToLogFile(wrongDirectionLogFile, logMessage + "\n", true);
                }

                // Remember we've seen this location
                knownWrongDirectionLocations.put(locationKey, System.currentTimeMillis());
            }
        }
    }

    private void checkForEntityStacking(List<ChestMinecartEntity> chestMinecarts) {
        int size = chestMinecarts.size();
        Set<String> currentSessionLocations = new HashSet<>();

        // More efficient nested loop - only check each pair once
        for (int i = 0; i < size; i++) {
            ChestMinecartEntity minecart1 = chestMinecarts.get(i);
            Vec3d pos1 = minecart1.getPos();

            for (int j = i + 1; j < size; j++) {
                ChestMinecartEntity minecart2 = chestMinecarts.get(j);

                // Fast early distance check using squared distance
                double distSq = minecart1.squaredDistanceTo(minecart2);
                double checkRadiusSq = checkRadius.get() * checkRadius.get();

                if (distSq <= checkRadiusSq) {
                    // Found stacked minecarts - add to the set for rendering
                    stackedMinecarts.add(minecart1);
                    stackedMinecarts.add(minecart2);

                    // Get position as string for location tracking - rounded to block position
                    Vec3d pos = minecart1.getPos();
                    String locationKey = String.format("%d,%d,%d",
                            (int)Math.round(pos.x),
                            (int)Math.round(pos.y),
                            (int)Math.round(pos.z));

                    // Add to current session locations
                    currentSessionLocations.add(locationKey);

                    // Check if we've already alerted for this location
                    if (!knownStackedLocations.containsKey(locationKey)) {
                        // Log the new stacked minecarts
                        String logMessage = String.format(
                                "[%s] Stacked chest minecarts at X: %.2f, Y: %.2f, Z: %.2f in %s",
                                getCurrentTimeStamp(), pos.x, pos.y, pos.z,
                                mc.world.getRegistryKey().getValue().toString()
                        );

                        // Send client-side notification
                        String chatMessage;
                        if (streamingMode.get()) {
                            chatMessage = "[MinecartDetector] Detected stacked chest minecarts [COORDINATES HIDDEN]";
                        } else {
                            chatMessage = String.format("[MinecartDetector] Detected stacked chest minecarts at X: %.1f, Y: %.1f, Z: %.1f",
                                    pos.x, pos.y, pos.z);
                        }

                        ChatUtils.info(chatMessage);

                        // Log to file if enabled
                        if (logStackedMinecarts.get() && stackedMinecartLogFile != null) {
                            writeToLogFile(stackedMinecartLogFile, logMessage + "\n", true);
                        }

                        // Play sound if enabled
                        if (playSoundAlert.get()) {
                            mc.player.playSound(net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                        }

                        // Remember we've seen this location
                        knownStackedLocations.put(locationKey, System.currentTimeMillis());
                    }
                }
            }
        }

        // Clean up old locations that we didn't see this time
        // This allows re-alerting if stacked minecarts reappear later
        if (!currentSessionLocations.isEmpty()) {
            knownStackedLocations.entrySet().removeIf(entry ->
                    !currentSessionLocations.contains(entry.getKey()) &&
                            System.currentTimeMillis() - entry.getValue() > 300000); // 5 minutes
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        // Skip rendering if disabled or nothing to render
        if (!highlightIncorrectDirection.get() && stackedMinecarts.isEmpty()) return;

        // Render incorrectly oriented hopper minecarts
        if (highlightIncorrectDirection.get()) {
            Color color = incorrectDirectionColor.get();
            for (Entity entity : badHopperMinecarts) {
                renderEntityHighlight(event, entity, color);
            }
        }

        // Render stacked chest minecarts
        if (!stackedMinecarts.isEmpty()) {
            Color color = stackedEntityColor.get();
            for (Entity entity : stackedMinecarts) {
                renderEntityHighlight(event, entity, color);
            }
        }
    }

    private void renderEntityHighlight(Render3DEvent event, Entity entity, Color color) {
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();

        RenderMode mode = renderMode.get();

        if (mode == RenderMode.Line) {
            // Simple vertical line
            event.renderer.line(
                    x, y, z,
                    x, y + 0.5, z,
                    color
            );
        }
        else if (mode == RenderMode.Box) {
            // Box around the entity - using correct API signature
            double width = 0.6;  // Standard minecart width
            double height = 0.7; // Standard minecart height

            // Create box dimensions centered on entity position
            double minX = x - width/2;
            double minY = y;
            double minZ = z - width/2;
            double maxX = x + width/2;
            double maxY = y + height;
            double maxZ = z + width/2;

            // Using the correct method signature with two colors and the required integer parameter
            event.renderer.box(
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    color,             // Fill color
                    color,             // Outline color
                    shapeMode.get(),
                    0                  // Default value for the face mask/render option
            );
        }
        else if (mode == RenderMode.Tracer) {
            // Tracer from player to entity center
            if (mc.player != null) {
                Vec3d eyes = mc.player.getEyePos();
                // Draw line from player eyes to minecart center (y + 0.35 for center height)
                event.renderer.line(
                        eyes.x, eyes.y, eyes.z,
                        x, y + 0.35, z,
                        color
                );
            }
        }
        else {
            // Text or fallback - just use line
            event.renderer.line(
                    x, y, z,
                    x, y + 0.5, z,
                    color
            );
        }
    }

    private boolean isCorrectlyOriented(Entity entity) {
        // Try the cache first
        int entityId = entity.getId();
        if (orientationCache.containsKey(entityId)) {
            return orientationCache.get(entityId);
        }

        float yaw = entity.getYaw() % 360;
        if (yaw < 0) yaw += 360;

        // Check if yaw is close to SOUTH (180 degrees)
        boolean isCorrect = Math.abs(yaw - CORRECT_YAW) <= YAW_TOLERANCE ||
                Math.abs(yaw - CORRECT_YAW - 360) <= YAW_TOLERANCE;

        // Cache the result
        orientationCache.put(entityId, isCorrect);

        return isCorrect;
    }

    private boolean hasWaterNearby(BlockPos entityPos) {
        // Check cache first
        if (waterCache.containsKey(entityPos)) {
            return waterCache.get(entityPos);
        }

        // Check only essential positions for water
        BlockPos[] positions = {
                entityPos,                  // Current position
                entityPos.down(),           // Below
                entityPos.up(),             // Above
                entityPos.north(),          // Cardinal directions
                entityPos.south(),
                entityPos.east(),
                entityPos.west()
        };

        boolean hasWater = false;

        for (BlockPos pos : positions) {
            // Skip if we're out of world bounds
            if (!mc.world.isInBuildLimit(pos)) continue;

            // Check if block state contains water
            if (mc.world.getBlockState(pos).getFluidState().isStill() ||
                    !mc.world.getBlockState(pos).getFluidState().isEmpty()) {
                hasWater = true;
                break;
            }
        }

        // Cache the result
        waterCache.put(entityPos, hasWater);

        return hasWater;
    }

    private void clearExpiredCooldowns() {
        long currentTime = System.currentTimeMillis();
        alertCooldowns.entrySet().removeIf(entry -> entry.getValue() < currentTime);
        wrongDirectionCooldowns.entrySet().removeIf(entry -> entry.getValue() < currentTime);
    }

    private void clearAllCaches() {
        orientationCache.clear();
        waterCache.clear();
        alertCooldowns.clear();
        wrongDirectionCooldowns.clear();
        knownStackedLocations.clear();
        knownWrongDirectionLocations.clear();
        badHopperMinecarts.clear();
        stackedMinecarts.clear();
        lastCacheCleanTime = System.currentTimeMillis();
    }

    /**
     * Logs a message to a specific log file
     */
    private void writeToLogFile(File file, String message, boolean append) {
        if (file == null || !file.exists()) return;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, append))) {
            writer.write(message);
        } catch (IOException e) {
            error("Failed to write to log file: " + e.getMessage());
        }
    }

    /**
     * Gets the current time as a formatted string
     */
    private String getCurrentTimeStamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date());
    }

    /**
     * Logs an error message to chat
     */
    private void error(String message) {
        ChatUtils.error("[MinecartDetector] " + message);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        // Just clear caches, no session end logging
        clearAllCaches();
        tickCounter = 0;
    }
}
