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
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
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
    private final SettingGroup sgCountAlert = settings.createGroup("Count Alert");

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

    private final Setting<Boolean> detectOffRails = sgGeneral.add(new BoolSetting.Builder()
            .name("detect-off-rails")
            .description("Alerts when minecarts are not on rails.")
            .defaultValue(false)
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

    private final Setting<Boolean> streamingMode = sgGeneral.add(new BoolSetting.Builder()
            .name("streaming-mode")
            .description("Hides coordinates in chat messages but still logs them to file.")
            .defaultValue(false)
            .build()
    );

    // Count Alert Settings
    private final Setting<Boolean> enableCountAlert = sgCountAlert.add(new BoolSetting.Builder()
            .name("enable-count-alert")
            .description("Alerts when a certain number of minecarts are in render distance.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> minecartCountThreshold = sgCountAlert.add(new IntSetting.Builder()
            .name("minecart-count-threshold")
            .description("Alert when this many minecarts are detected.")
            .defaultValue(50)
            .min(1)
            .sliderRange(1, 500)
            .visible(() -> enableCountAlert.get())
            .build()
    );

    private final Setting<Boolean> countAllMinecarts = sgCountAlert.add(new BoolSetting.Builder()
            .name("count-all-minecarts")
            .description("Count all minecart types. If disabled, only counts chest and hopper minecarts.")
            .defaultValue(true)
            .visible(() -> enableCountAlert.get())
            .build()
    );

    private final Setting<Boolean> playSoundOnCountAlert = sgCountAlert.add(new BoolSetting.Builder()
            .name("play-sound-on-count-alert")
            .description("Plays a sound when minecart count threshold is reached.")
            .defaultValue(true)
            .visible(() -> enableCountAlert.get())
            .build()
    );

    private final Setting<Integer> countAlertCooldown = sgCountAlert.add(new IntSetting.Builder()
            .name("count-alert-cooldown")
            .description("Cooldown in seconds between count alerts.")
            .defaultValue(30)
            .min(5)
            .sliderRange(5, 300)
            .visible(() -> enableCountAlert.get())
            .build()
    );

    private final Setting<Boolean> logCountAlert = sgCountAlert.add(new BoolSetting.Builder()
            .name("log-count-alert")
            .description("Logs minecart count alerts to the module log file.")
            .defaultValue(true)
            .visible(() -> enableCountAlert.get())
            .build()
    );

    // Logging Settings
    private final Setting<Boolean> logToFile = sgLogging.add(new BoolSetting.Builder()
            .name("log-to-file")
            .description("Logs all detections to MinecartDetector.log file.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> logStackedMinecarts = sgLogging.add(new BoolSetting.Builder()
            .name("log-stacked-minecarts")
            .description("Logs stacked minecarts to the module log file.")
            .defaultValue(true)
            .visible(() -> logToFile.get())
            .build()
    );

    private final Setting<Boolean> logWrongDirectionMinecarts = sgLogging.add(new BoolSetting.Builder()
            .name("log-wrong-direction-minecarts")
            .description("Logs minecarts facing the wrong direction to the module log file.")
            .defaultValue(true)
            .visible(() -> logToFile.get())
            .build()
    );

    private final Setting<Boolean> logOffRailsMinecarts = sgLogging.add(new BoolSetting.Builder()
            .name("log-off-rails-minecarts")
            .description("Logs minecarts not on rails to the module log file.")
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

    private final Setting<Boolean> notifyOffRails = sgLogging.add(new BoolSetting.Builder()
            .name("notify-off-rails")
            .description("Sends a chat message when a minecart not on rails is found.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> offRailsCooldown = sgLogging.add(new IntSetting.Builder()
            .name("off-rails-cooldown")
            .description("Cooldown in ticks between alerts for off-rails minecarts.")
            .defaultValue(100)
            .min(1)
            .sliderRange(1, 200)
            .visible(() -> notifyOffRails.get())
            .build()
    );

    // Render Settings
    public enum RenderMode {
        Line,
        Box,
        Both,
        None
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
            .visible(() -> renderMode.get() == RenderMode.Box || renderMode.get() == RenderMode.Both)
            .build()
    );

    private final Setting<Boolean> enableTracers = sgRender.add(new BoolSetting.Builder()
            .name("enable-tracers")
            .description("Draw tracer lines to minecarts (independent of box rendering).")
            .defaultValue(false)
            .build()
    );

    private final Setting<Double> tracerThickness = sgRender.add(new DoubleSetting.Builder()
            .name("tracer-thickness")
            .description("The thickness of tracer lines.")
            .defaultValue(1.5)
            .min(0.1)
            .max(5.0)
            .sliderRange(0.1, 5.0)
            .visible(() -> enableTracers.get())
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

    private final Setting<SettingColor> offRailsColor = sgRender.add(new ColorSetting.Builder()
            .name("off-rails-color")
            .description("The color of minecarts not on rails.")
            .defaultValue(new SettingColor(0, 255, 255, 75))
            .build()
    );

    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
            .name("tracer-color")
            .description("The color of tracer lines.")
            .defaultValue(new SettingColor(255, 255, 255, 200))
            .visible(() -> enableTracers.get())
            .build()
    );

    // Tracking variables
    private final Set<Entity> badHopperMinecarts = new HashSet<>();
    private final Set<Entity> stackedMinecarts = new HashSet<>();
    private final Set<Entity> offRailsMinecarts = new HashSet<>();
    private final Map<String, Long> knownStackedLocations = new HashMap<>();
    private final Map<String, Long> knownWrongDirectionLocations = new HashMap<>();
    private final Map<String, Long> knownOffRailsLocations = new HashMap<>();
    private final Map<Integer, Long> wrongDirectionCooldowns = new HashMap<>();
    private final Map<Integer, Long> offRailsCooldowns = new HashMap<>();

    // Cache for orientation checks
    private final Map<Integer, CachedOrientation> orientationCache = new LinkedHashMap<>(200, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, CachedOrientation> eldest) {
            return size() > 200;
        }
    };

    private final Map<BlockPos, Boolean> waterCache = new LinkedHashMap<>(100, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockPos, Boolean> eldest) {
            return size() > 100;
        }
    };

    // Count alert tracking
    private long lastCountAlertTime = 0;
    private long lastCountDisplayTime = 0;
    private int lastMinecartCount = 0;

    // Single log file for all detections
    private File moduleLogFile;

    // Constants
    private static final float CORRECT_YAW = 0.0f; // North direction
    private static final float YAW_TOLERANCE = 10.0f; // ±10 degrees tolerance
    private static final long CACHE_CLEANUP_INTERVAL = 300000; // 5 minutes
    private static final long ORIENTATION_CACHE_VALID_TIME = 5000; // 5 seconds

    // Performance counters
    private int tickCounter = 0;
    private long lastCacheCleanTime = System.currentTimeMillis();

    // Inner class for cached orientation
    private static class CachedOrientation {
        boolean isCorrect;
        float yaw;
        long timestamp;

        CachedOrientation(boolean isCorrect, float yaw) {
            this.isCorrect = isCorrect;
            this.yaw = yaw;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isValid() {
            return System.currentTimeMillis() - timestamp < ORIENTATION_CACHE_VALID_TIME;
        }
    }

    public MinecartDetector() {
        super(Hybridious.CATEGORY, "minecart-detector", "Detects and highlights problematic minecarts.");
    }

    @Override
    public void onActivate() {
        if (mc.world == null) return;

        // Initialize log files
        if (logToFile.get()) {
            initializeLogFiles();
        }

        // Clear all tracking data on activation
        clearAllCaches();
        tickCounter = 0;
        lastCountAlertTime = 0;
        lastCountDisplayTime = 0;
        lastMinecartCount = 0;

        info("MinecartDetector activated");
    }

    @Override
    public void onDeactivate() {
        clearAllCaches();
        info("MinecartDetector deactivated");
    }

    private void initializeLogFiles() {
        try {
            // Create hybridious_mod directory in meteor-client folder
            File hybridModDir = meteordevelopment.meteorclient.MeteorClient.FOLDER.toPath().resolve("hybridious_mod").toFile();

            if (!hybridModDir.exists()) {
                hybridModDir.mkdirs();
            }

            // Single log file for all detections
            if (logToFile.get()) {
                moduleLogFile = new File(hybridModDir, "MinecartDetector.log");
                if (!moduleLogFile.exists()) {
                    moduleLogFile.createNewFile();
                    writeToLogFile(moduleLogFile, "=== MinecartDetector Log ===\n", false);
                    writeToLogFile(moduleLogFile, "Log started: " + getCurrentTimeStamp() + "\n\n", true);
                }
            }

        } catch (IOException e) {
            error("Failed to initialize log file: " + e.getMessage());
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        tickCounter++;

        // Only check minecarts at specified frequency to reduce performance impact
        if (tickCounter % checkFrequency.get() != 0) return;

        // Clean up old caches periodically
        if (System.currentTimeMillis() - lastCacheCleanTime > CACHE_CLEANUP_INTERVAL) {
            orientationCache.clear();
            waterCache.clear();
            lastCacheCleanTime = System.currentTimeMillis();
        }

        // Clear expired cooldowns
        clearExpiredCooldowns();

        // Clear tracking sets for this tick
        badHopperMinecarts.clear();
        stackedMinecarts.clear();
        offRailsMinecarts.clear();

        Vec3d playerPos = mc.player.getPos();

        // Track current session locations
        Set<String> currentWrongDirectionLocations = new HashSet<>();
        Set<String> currentOffRailsLocations = new HashSet<>();
        int totalMinecartCount = 0;
        List<Entity> containerMinecarts = new ArrayList<>();

        // Check all entities in render distance
        for (Entity entity : mc.world.getEntities()) {

            // Count minecarts for alert feature
            if (enableCountAlert.get()) {
                if (countAllMinecarts.get()) {
                    if (entity instanceof AbstractMinecartEntity) {
                        totalMinecartCount++;
                    }
                } else {
                    if (entity instanceof ChestMinecartEntity || entity instanceof HopperMinecartEntity) {
                        totalMinecartCount++;
                    }
                }
            }

            // Check for hopper minecarts facing wrong direction
            // Both hoppers and chests need correct orientation for item flow
            if (highlightIncorrectDirection.get() &&
                    (entity instanceof HopperMinecartEntity || entity instanceof ChestMinecartEntity)) {
                checkMinecartDirection(entity, currentWrongDirectionLocations);
            }

            // Check for minecarts not on rails
            if (detectOffRails.get() && entity instanceof AbstractMinecartEntity) {
                checkOffRails((AbstractMinecartEntity) entity, currentOffRailsLocations);
            }

            // Collect container minecarts for stacking check (chest and hopper)
            if (detectEntityStacking.get() &&
                    (entity instanceof ChestMinecartEntity || entity instanceof HopperMinecartEntity)) {
                containerMinecarts.add(entity);
            }
        }

        // Clean up old wrong direction locations
        long currentTime = System.currentTimeMillis();
        knownWrongDirectionLocations.entrySet().removeIf(entry ->
                !currentWrongDirectionLocations.contains(entry.getKey()) &&
                        currentTime - entry.getValue() > 300000); // 5 minutes

        // Clean up old off-rails locations
        knownOffRailsLocations.entrySet().removeIf(entry ->
                !currentOffRailsLocations.contains(entry.getKey()) &&
                        currentTime - entry.getValue() > 300000); // 5 minutes

        // Check for stacked minecarts
        if (!containerMinecarts.isEmpty()) {
            checkForStackedEntities(containerMinecarts);
        }

        // Handle minecart count alerts
        if (enableCountAlert.get()) {
            handleCountAlert(totalMinecartCount);
        }
    }

    private void checkMinecartDirection(Entity entity, Set<String> currentWrongDirectionLocations) {
        // Check orientation
        if (isCorrectlyOriented(entity)) {
            return;
        }

        // Check water exclusion
        BlockPos entityPos = entity.getBlockPos();
        if (excludeWaterMinecarts.get() && hasWaterNearby(entityPos)) {
            return;
        }

        // Add to bad minecarts set for rendering
        badHopperMinecarts.add(entity);

        // Create location key
        String locationKey = String.format("%d,%d,%d",
                entityPos.getX(),
                entityPos.getY(),
                entityPos.getZ());

        // Add to current session
        currentWrongDirectionLocations.add(locationKey);

        // Check if we should alert about this location
        boolean isNewLocation = !knownWrongDirectionLocations.containsKey(locationKey);
        boolean shouldAlert = isNewLocation;

        // Check entity-specific cooldown
        if (shouldAlert && notifyWrongDirection.get()) {
            int entityId = entity.getId();
            long now = System.currentTimeMillis();

            if (wrongDirectionCooldowns.containsKey(entityId)) {
                long cooldownEnd = wrongDirectionCooldowns.get(entityId);
                if (now < cooldownEnd) {
                    shouldAlert = false;
                }
            }

            if (shouldAlert) {
                // Set cooldown for this entity
                long cooldownTime = wrongDirectionCooldown.get() * 50L; // ticks to ms
                wrongDirectionCooldowns.put(entityId, now + cooldownTime);
            }
        }

        // Alert if needed
        if (shouldAlert) {
            Vec3d pos = entity.getPos();
            float yaw = entity.getYaw();
            String serverName = getServerName();
            String minecartType = getMinecartTypeName((AbstractMinecartEntity) entity);

            // Send chat notification
            if (notifyWrongDirection.get()) {
                if (streamingMode.get()) {
                    ChatUtils.warning(String.format("[MinecartDetector] Wrong direction %s detected on %s (coordinates hidden)", minecartType.toLowerCase(), serverName));
                } else {
                    ChatUtils.warning(String.format("[MinecartDetector] Wrong direction %s at X: %d, Y: %d, Z: %d (Yaw: %.1f°) on %s",
                            minecartType.toLowerCase(), entityPos.getX(), entityPos.getY(), entityPos.getZ(), yaw, serverName));
                }
            }

            // Log to file
            if (logWrongDirectionMinecarts.get() && moduleLogFile != null) {
                String logEntry = String.format("[%s] [%s] WRONG_DIRECTION - %s at X: %d, Y: %d, Z: %d (Yaw: %.2f°) - Distance: %.1f blocks\n",
                        getCurrentTimeStamp(),
                        serverName,
                        minecartType,
                        entityPos.getX(),
                        entityPos.getY(),
                        entityPos.getZ(),
                        yaw,
                        mc.player.getPos().distanceTo(pos));
                writeToLogFile(moduleLogFile, logEntry, true);
            }

            // Play sound alert
            if (playSoundAlert.get()) {
                mc.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 0.5f);
            }

            // Remember this location
            knownWrongDirectionLocations.put(locationKey, System.currentTimeMillis());
        }
    }

    private void checkOffRails(AbstractMinecartEntity entity, Set<String> currentOffRailsLocations) {
        // Check if minecart is on rails
        BlockPos entityPos = entity.getBlockPos();

        // Check the block at the minecart's position and below
        boolean onRails = false;

        // Check current position
        if (isRailBlock(mc.world.getBlockState(entityPos))) {
            onRails = true;
        }
        // Check one block below (minecarts can be slightly above rails)
        else if (isRailBlock(mc.world.getBlockState(entityPos.down()))) {
            onRails = true;
        }

        // If on rails, skip
        if (onRails) {
            return;
        }

        // Skip if water is nearby (ice boat systems, water elevators, etc.)
        if (hasWaterNearby(entityPos)) {
            return;
        }

        // Add to off-rails set for rendering
        offRailsMinecarts.add(entity);

        // Create location key
        String locationKey = String.format("%d,%d,%d",
                entityPos.getX(),
                entityPos.getY(),
                entityPos.getZ());

        // Add to current session
        currentOffRailsLocations.add(locationKey);

        // Check if we should alert about this location
        boolean isNewLocation = !knownOffRailsLocations.containsKey(locationKey);
        boolean shouldAlert = isNewLocation;

        // Check entity-specific cooldown
        if (shouldAlert && notifyOffRails.get()) {
            int entityId = entity.getId();
            long now = System.currentTimeMillis();

            if (offRailsCooldowns.containsKey(entityId)) {
                long cooldownEnd = offRailsCooldowns.get(entityId);
                if (now < cooldownEnd) {
                    shouldAlert = false;
                }
            }

            if (shouldAlert) {
                // Set cooldown for this entity
                long cooldownTime = offRailsCooldown.get() * 50L; // ticks to ms
                offRailsCooldowns.put(entityId, now + cooldownTime);
            }
        }

        // Alert if needed
        if (shouldAlert) {
            Vec3d pos = entity.getPos();
            String serverName = getServerName();
            String minecartType = getMinecartTypeName(entity);

            // Send chat notification
            if (notifyOffRails.get()) {
                if (streamingMode.get()) {
                    ChatUtils.warning(String.format("[MinecartDetector] %s not on rails on %s (coordinates hidden)", minecartType, serverName));
                } else {
                    ChatUtils.warning(String.format("[MinecartDetector] %s not on rails at X: %d, Y: %d, Z: %d on %s",
                            minecartType, entityPos.getX(), entityPos.getY(), entityPos.getZ(), serverName));
                }
            }

            // Log to file
            if (logOffRailsMinecarts.get() && moduleLogFile != null) {
                String logEntry = String.format("[%s] [%s] OFF_RAILS - %s at X: %d, Y: %d, Z: %d - Distance: %.1f blocks\n",
                        getCurrentTimeStamp(),
                        serverName,
                        minecartType,
                        entityPos.getX(),
                        entityPos.getY(),
                        entityPos.getZ(),
                        mc.player.getPos().distanceTo(pos));
                writeToLogFile(moduleLogFile, logEntry, true);
            }

            // Play sound alert
            if (playSoundAlert.get()) {
                mc.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.0f, 1.5f);
            }

            // Remember this location
            knownOffRailsLocations.put(locationKey, System.currentTimeMillis());
        }
    }

    private boolean isRailBlock(net.minecraft.block.BlockState state) {
        // Check if the block is any type of rail using AbstractRailBlock
        return state.getBlock() instanceof net.minecraft.block.AbstractRailBlock;
    }

    private String getMinecartTypeName(AbstractMinecartEntity entity) {
        if (entity instanceof ChestMinecartEntity) {
            return "Chest minecart";
        } else if (entity instanceof HopperMinecartEntity) {
            return "Hopper minecart";
        } else if (entity instanceof net.minecraft.entity.vehicle.FurnaceMinecartEntity) {
            return "Furnace minecart";
        } else if (entity instanceof net.minecraft.entity.vehicle.TntMinecartEntity) {
            return "TNT minecart";
        } else if (entity instanceof net.minecraft.entity.vehicle.SpawnerMinecartEntity) {
            return "Spawner minecart";
        } else if (entity instanceof net.minecraft.entity.vehicle.CommandBlockMinecartEntity) {
            return "Command block minecart";
        } else {
            return "Minecart";
        }
    }

    private void checkForStackedEntities(List<Entity> containerMinecarts) {
        double radius = checkRadius.get();
        Set<String> currentSessionLocations = new HashSet<>();
        int size = containerMinecarts.size();

        for (int i = 0; i < size; i++) {
            Entity minecart1 = containerMinecarts.get(i);
            Vec3d pos1 = minecart1.getPos();

            for (int j = i + 1; j < size; j++) {
                Entity minecart2 = containerMinecarts.get(j);
                Vec3d pos2 = minecart2.getPos();

                double distance = pos1.distanceTo(pos2);

                if (distance <= radius) {
                    stackedMinecarts.add(minecart1);
                    stackedMinecarts.add(minecart2);

                    // Create location key
                    BlockPos blockPos = minecart1.getBlockPos();
                    String locationKey = String.format("%d,%d,%d",
                            blockPos.getX(),
                            blockPos.getY(),
                            blockPos.getZ());

                    currentSessionLocations.add(locationKey);

                    // Check if we should alert
                    if (!knownStackedLocations.containsKey(locationKey)) {
                        // New stacked location found
                        String serverName = getServerName();
                        String type1 = getMinecartTypeName((AbstractMinecartEntity) minecart1);
                        String type2 = getMinecartTypeName((AbstractMinecartEntity) minecart2);

                        if (streamingMode.get()) {
                            ChatUtils.warning(String.format("[MinecartDetector] Stacked minecarts detected on %s (coordinates hidden)", serverName));
                        } else {
                            ChatUtils.warning(String.format("[MinecartDetector] Stacked %s and %s at X: %d, Y: %d, Z: %d (Distance: %.3f blocks) on %s",
                                    type1.toLowerCase(),
                                    type2.toLowerCase(),
                                    blockPos.getX(),
                                    blockPos.getY(),
                                    blockPos.getZ(),
                                    distance,
                                    serverName));
                        }

                        // Log to file
                        if (logStackedMinecarts.get() && moduleLogFile != null) {
                            String logEntry = String.format("[%s] [%s] STACKED - %s and %s at X: %d, Y: %d, Z: %d (Distance: %.3f blocks)\n",
                                    getCurrentTimeStamp(),
                                    serverName,
                                    type1,
                                    type2,
                                    blockPos.getX(),
                                    blockPos.getY(),
                                    blockPos.getZ(),
                                    distance);
                            writeToLogFile(moduleLogFile, logEntry, true);
                        }

                        // Play sound
                        if (playSoundAlert.get()) {
                            mc.player.playSound(net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                        }

                        // Remember this location
                        knownStackedLocations.put(locationKey, System.currentTimeMillis());
                    }
                }
            }
        }

        // Clean up old stacked locations
        long currentTime = System.currentTimeMillis();
        knownStackedLocations.entrySet().removeIf(entry ->
                !currentSessionLocations.contains(entry.getKey()) &&
                        currentTime - entry.getValue() > 300000); // 5 minutes
    }

    private void handleCountAlert(int count) {
        lastMinecartCount = count;
        long currentTime = System.currentTimeMillis();

        // Only alert if threshold is reached
        if (count >= minecartCountThreshold.get()) {
            // Check cooldown
            if (currentTime - lastCountAlertTime > countAlertCooldown.get() * 1000L) {
                String countType = countAllMinecarts.get() ? "all minecarts" : "chest/hopper minecarts";
                String serverName = getServerName();
                String message = String.format("§e[MinecartDetector] §cAlert: %d %s detected on %s! (Threshold: %d)",
                        count, countType, serverName, minecartCountThreshold.get());

                ChatUtils.info(message);

                // Play sound
                if (playSoundOnCountAlert.get()) {
                    mc.player.playSound(net.minecraft.sound.SoundEvents.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
                }

                // Log to file
                if (logCountAlert.get() && moduleLogFile != null) {
                    String logEntry = String.format("[%s] [%s] COUNT_ALERT - %d %s detected (Threshold: %d)\n",
                            getCurrentTimeStamp(), serverName, count, countType, minecartCountThreshold.get());
                    writeToLogFile(moduleLogFile, logEntry, true);
                }

                lastCountAlertTime = currentTime;
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        // Skip rendering if nothing to render
        if (badHopperMinecarts.isEmpty() && stackedMinecarts.isEmpty() && offRailsMinecarts.isEmpty()) return;

        RenderMode mode = renderMode.get();
        boolean drawBoxes = (mode == RenderMode.Box || mode == RenderMode.Both);
        boolean drawTracers = enableTracers.get();

        // Render incorrectly oriented hopper minecarts
        if (highlightIncorrectDirection.get() && !badHopperMinecarts.isEmpty()) {
            Color color = incorrectDirectionColor.get();
            for (Entity entity : badHopperMinecarts) {
                if (drawBoxes) {
                    renderBox(event, entity, color);
                }
                if (drawTracers) {
                    renderTracer(event, entity, tracerColor.get());
                }
            }
        }

        // Render stacked chest minecarts
        if (!stackedMinecarts.isEmpty()) {
            Color color = stackedEntityColor.get();
            for (Entity entity : stackedMinecarts) {
                if (drawBoxes) {
                    renderBox(event, entity, color);
                }
                if (drawTracers) {
                    renderTracer(event, entity, tracerColor.get());
                }
            }
        }

        // Render off-rails minecarts
        if (detectOffRails.get() && !offRailsMinecarts.isEmpty()) {
            Color color = offRailsColor.get();
            for (Entity entity : offRailsMinecarts) {
                if (drawBoxes) {
                    renderBox(event, entity, color);
                }
                if (drawTracers) {
                    renderTracer(event, entity, tracerColor.get());
                }
            }
        }
    }

    private void renderBox(Render3DEvent event, Entity entity, Color color) {
        // Interpolate entity position for smoother rendering
        double delta = event.tickDelta;
        double x = entity.prevX + (entity.getX() - entity.prevX) * delta;
        double y = entity.prevY + (entity.getY() - entity.prevY) * delta;
        double z = entity.prevZ + (entity.getZ() - entity.prevZ) * delta;

        double width = 0.6;
        double height = 0.7;

        double minX = x - width / 2;
        double minY = y;
        double minZ = z - width / 2;
        double maxX = x + width / 2;
        double maxY = y + height;
        double maxZ = z + width / 2;

        event.renderer.box(minX, minY, minZ, maxX, maxY, maxZ,
                color, color, shapeMode.get(), 0);
    }

    private void renderTracer(Render3DEvent event, Entity entity, Color color) {
        if (mc.player == null) return;

        // Get camera position
        Vec3d eyes = mc.player.getEyePos();

        // In first person, start tracer slightly forward from camera to make it more visible
        Vec3d startPos = eyes;
        if (mc.options.getPerspective().isFirstPerson()) {
            // Get look direction and start tracer 0.5 blocks forward
            Vec3d lookVec = mc.player.getRotationVec(event.tickDelta);
            startPos = eyes.add(lookVec.multiply(0.5));
        }

        // Interpolate entity position for smoother rendering
        double delta = event.tickDelta;
        double x = entity.prevX + (entity.getX() - entity.prevX) * delta;
        double y = entity.prevY + (entity.getY() - entity.prevY) * delta + 0.35; // Center of minecart
        double z = entity.prevZ + (entity.getZ() - entity.prevZ) * delta;

        event.renderer.line(startPos.x, startPos.y, startPos.z, x, y, z, color);
    }

    private boolean isCorrectlyOriented(Entity entity) {
        int entityId = entity.getId();
        float currentYaw = entity.getYaw();

        // Check cache
        if (orientationCache.containsKey(entityId)) {
            CachedOrientation cached = orientationCache.get(entityId);
            // If yaw hasn't changed significantly and cache is still valid, use cached result
            if (cached.isValid() && Math.abs(cached.yaw - currentYaw) < 1.0f) {
                return cached.isCorrect;
            }
        }

        // Normalize yaw to 0-360 range
        float yaw = currentYaw % 360;
        if (yaw < 0) yaw += 360;

        // Check if yaw is close to NORTH (0 degrees)
        // Handle wrap-around: 350° to 10° is acceptable (0° ±10°)
        boolean isCorrect = yaw <= YAW_TOLERANCE || yaw >= (360 - YAW_TOLERANCE);

        // Cache the result
        orientationCache.put(entityId, new CachedOrientation(isCorrect, currentYaw));

        return isCorrect;
    }

    private boolean hasWaterNearby(BlockPos entityPos) {
        if (waterCache.containsKey(entityPos)) {
            return waterCache.get(entityPos);
        }

        BlockPos[] positions = {
                entityPos,
                entityPos.down(),
                entityPos.up(),
                entityPos.north(),
                entityPos.south(),
                entityPos.east(),
                entityPos.west()
        };

        boolean hasWater = false;

        for (BlockPos pos : positions) {
            if (!mc.world.isInBuildLimit(pos)) continue;

            if (mc.world.getBlockState(pos).getFluidState().isStill() ||
                    !mc.world.getBlockState(pos).getFluidState().isEmpty()) {
                hasWater = true;
                break;
            }
        }

        waterCache.put(entityPos, hasWater);
        return hasWater;
    }

    private void clearExpiredCooldowns() {
        long currentTime = System.currentTimeMillis();
        wrongDirectionCooldowns.entrySet().removeIf(entry -> entry.getValue() < currentTime);
        offRailsCooldowns.entrySet().removeIf(entry -> entry.getValue() < currentTime);
    }

    private void clearAllCaches() {
        orientationCache.clear();
        waterCache.clear();
        wrongDirectionCooldowns.clear();
        offRailsCooldowns.clear();
        knownStackedLocations.clear();
        knownWrongDirectionLocations.clear();
        knownOffRailsLocations.clear();
        badHopperMinecarts.clear();
        stackedMinecarts.clear();
        offRailsMinecarts.clear();
        lastCacheCleanTime = System.currentTimeMillis();
    }

    private void writeToLogFile(File file, String message, boolean append) {
        if (file == null || !file.exists()) return;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, append))) {
            writer.write(message);
        } catch (IOException e) {
            error("Failed to write to log file: " + e.getMessage());
        }
    }

    private String getCurrentTimeStamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date());
    }

    private String getServerName() {
        if (mc.isIntegratedServerRunning()) {
            return "Singleplayer";
        } else if (mc.getCurrentServerEntry() != null) {
            return mc.getCurrentServerEntry().address;
        } else {
            return "Unknown Server";
        }
    }

    private void error(String message) {
        ChatUtils.error("[MinecartDetector] " + message);
    }

    private void info(String message) {
        ChatUtils.info("[MinecartDetector] " + message);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        clearAllCaches();
        tickCounter = 0;
        lastCountAlertTime = 0;
        lastCountDisplayTime = 0;
        lastMinecartCount = 0;
    }
}
