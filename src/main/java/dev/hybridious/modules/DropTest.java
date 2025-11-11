package dev.hybridious.modules;

import dev.hybridious.Hybridious;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DropTest extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> targetItems = sgGeneral.add(new StringListSetting.Builder()
            .name("target-items")
            .description("List of items to track (e.g., 'minecraft:iron_ingot', 'gold_ingot'). Leave empty to track nothing unless 'track-all-items' is enabled.")
            .defaultValue(List.of("minecraft:iron_ingot"))
            .build()
    );

    private final Setting<Integer> testDuration = sgGeneral.add(new IntSetting.Builder()
            .name("test-duration-minutes")
            .description("Duration of the test in minutes")
            .defaultValue(5)
            .min(1)
            .sliderMax(120)
            .build()
    );

    private final Setting<Boolean> trackAllItems = sgGeneral.add(new BoolSetting.Builder()
            .name("track-all-items")
            .description("Track all item types, not just the target item")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> scanInterval = sgGeneral.add(new IntSetting.Builder()
            .name("scan-interval")
            .description("Ticks between entity scans (higher = better performance)")
            .defaultValue(5)
            .min(1)
            .sliderMax(20)
            .build()
    );

    private final Setting<Boolean> verboseLogging = sgGeneral.add(new BoolSetting.Builder()
            .name("verbose-logging")
            .description("Log detailed information during the test")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> itemStackSize = sgGeneral.add(new IntSetting.Builder()
            .name("item-stack-size")
            .description("Max stack size of the tracked item (1, 16, or 64)")
            .defaultValue(64)
            .min(1)
            .max(64)
            .sliderMin(1)
            .sliderMax(64)
            .build()
    );

    // Button to clear logs
    private final Setting<Boolean> clearLogsButton = sgGeneral.add(new BoolSetting.Builder()
            .name("clear-logs")
            .description("Click to clear all previous logs from DropTest.txt")
            .defaultValue(false)
            .onChanged(value -> {
                if (value) {
                    clearLogFile();
                }
            })
            .build()
    );

    // Test tracking variables - using thread-safe collections
    private volatile boolean testActive = false;
    private volatile long testStartTime;
    private volatile long testEndTime;
    private final Map<String, Integer> itemCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> trackedEntityStacks = new ConcurrentHashMap<>();
    private final Set<UUID> trackedEntities = ConcurrentHashMap.newKeySet();
    private volatile int totalTicks = 0;
    private volatile int checksPerformed = 0;
    private int tickCounter = 0;
    private int scanCounter = 0;

    // Decimal formatter for consistent number formatting
    private final DecimalFormat df2 = new DecimalFormat("0.00");
    private final DecimalFormat df1 = new DecimalFormat("0.0");

    public DropTest() {
        super(Hybridious.CATEGORY, "drop-test", "Tracks entity drops over time and outputs statistics to files");
    }

    @Override
    public void onActivate() {
        startTest();
    }

    @Override
    public void onDeactivate() {
        if (testActive) {
            endTest();
        }
    }

    private void startTest() {
        testActive = true;
        testStartTime = System.currentTimeMillis();
        itemCounts.clear();
        trackedEntities.clear();
        trackedEntityStacks.clear();
        totalTicks = 0;
        checksPerformed = 0;
        tickCounter = 0;
        scanCounter = 0;

        info("Drop test started for " + testDuration.get() + " minutes");
        if (!targetItems.get().isEmpty()) {
            info("Tracking items: " + String.join(", ", targetItems.get()));
        }
        info("Scan interval: " + scanInterval.get() + " ticks");
        if (trackAllItems.get()) {
            info("Tracking ALL items in render distance");
        }
    }

    private void endTest() {
        testActive = false;
        testEndTime = System.currentTimeMillis();

        info("Drop test completed!");

        // Calculate and display statistics
        calculateAndDisplayStats();

        // Always save results to file
        info("Saving results...");
        saveTestResults();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!testActive) return;

        // Safety checks
        if (mc == null || mc.world == null || mc.player == null) {
            return;
        }

        totalTicks++;
        tickCounter++;
        scanCounter++;

        // Check if test duration has elapsed
        long elapsedMillis = System.currentTimeMillis() - testStartTime;
        long targetMillis = testDuration.get() * 60L * 1000L;

        if (elapsedMillis >= targetMillis) {
            toggle();
            return;
        }

        // Log progress every 20 ticks (1 second) if verbose logging is enabled
        if (verboseLogging.get() && tickCounter >= 20) {
            tickCounter = 0;
            double elapsedMinutes = elapsedMillis / 60000.0;
            int totalItems = getTotalItemCount();
            info("Progress: " + df1.format(elapsedMinutes) + " / " + testDuration.get() + " min | Entities: " + trackedEntities.size() + " | Items: " + totalItems);
        }

        // Only scan every N ticks for better performance
        if (scanCounter < scanInterval.get()) {
            return;
        }

        scanCounter = 0;
        checksPerformed++;

        // Scan for item entities in render distance
        try {
            int renderDistance = mc.options.getViewDistance().getValue() * 16;

            List<ItemEntity> entities = mc.world.getEntitiesByClass(
                    ItemEntity.class,
                    mc.player.getBoundingBox().expand(renderDistance),
                    entity -> entity != null && !entity.isRemoved()
            );

            for (ItemEntity itemEntity : entities) {
                try {
                    if (itemEntity == null) continue;

                    UUID entityId = itemEntity.getUuid();
                    if (entityId == null) continue;

                    // Only count each entity once
                    if (trackedEntities.contains(entityId)) {
                        continue;
                    }

                    ItemStack stack = itemEntity.getStack();
                    if (stack == null || stack.isEmpty()) continue;

                    String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                    if (itemId == null) continue;

                    // Check if we should track this item
                    boolean shouldTrack = trackAllItems.get();

                    // If not tracking all, check if this item is in our target list
                    if (!shouldTrack && !targetItems.get().isEmpty()) {
                        String normalizedItemId = itemId;
                        for (String targetItem : targetItems.get()) {
                            if (normalizedItemId.equals(normalizeItemId(targetItem))) {
                                shouldTrack = true;
                                break;
                            }
                        }
                    }

                    if (shouldTrack) {
                        // Check if we've seen this entity before
                        if (trackedEntities.contains(entityId)) {
                            // Entity exists - check if stack count changed (items merged)
                            Integer previousCount = trackedEntityStacks.get(entityId);
                            if (previousCount != null && stack.getCount() > previousCount) {
                                // Stack increased - items merged into this entity
                                int addedItems = stack.getCount() - previousCount;
                                itemCounts.merge(itemId, addedItems, Integer::sum);
                                trackedEntityStacks.put(entityId, stack.getCount());

                                if (verboseLogging.get()) {
                                    info("Stack merge detected: " + addedItems + "x " + itemId + " (new total: " + stack.getCount() + ")");
                                }
                            }
                        } else {
                            // New entity - add it
                            trackedEntities.add(entityId);
                            trackedEntityStacks.put(entityId, stack.getCount());
                            itemCounts.merge(itemId, stack.getCount(), Integer::sum);

                            if (verboseLogging.get()) {
                                info("Found: " + stack.getCount() + "x " + itemId);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Skip this entity if there's any error
                    if (verboseLogging.get()) {
                        warning("Error processing entity: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            // Silently catch any exceptions during entity scanning to prevent crashes
            if (verboseLogging.get()) {
                warning("Error during entity scan: " + e.getMessage());
            }
        }
    }

    private String normalizeItemId(String id) {
        if (id == null || id.isEmpty()) {
            return "minecraft:air";
        }
        if (!id.contains(":")) {
            return "minecraft:" + id;
        }
        return id;
    }

    private int getTotalItemCount() {
        try {
            return itemCounts.values().stream().mapToInt(Integer::intValue).sum();
        } catch (Exception e) {
            return 0;
        }
    }

    private void calculateAndDisplayStats() {
        try {
            long durationMillis = testEndTime - testStartTime;
            double durationMinutes = durationMillis / 60000.0;
            double durationSeconds = durationMillis / 1000.0;

            info("=== Drop Test Results ===");
            info("Duration: " + df2.format(durationMinutes) + "m (" + df1.format(durationSeconds) + "s) | Ticks: " + totalTicks + " | Scans: " + checksPerformed);
            info("Entities found: " + trackedEntities.size());

            if (itemCounts.isEmpty()) {
                warning("No items found during the test!");
                return;
            }

            // Sort items by count (descending)
            List<Map.Entry<String, Integer>> sortedItems = new ArrayList<>(itemCounts.entrySet());
            sortedItems.sort((a, b) -> b.getValue().compareTo(a.getValue()));

            int totalItems = getTotalItemCount();

            info("");
            info("=== Item Statistics ===");
            for (Map.Entry<String, Integer> entry : sortedItems) {
                String itemId = entry.getKey();
                int count = entry.getValue();

                double perSecond = count / durationSeconds;
                double perMinute = count / durationMinutes;
                double perHour = perMinute * 60;
                double percentage = (count * 100.0) / totalItems;

                info(itemId + ": " + count + " items (" + df1.format(percentage) + " percent)");
                info("  " + df2.format(perSecond) + "/s | " + df2.format(perMinute) + "/m | " + df2.format(perHour) + "/h");
            }

            info("");
            info("=== Summary ===");
            info("Total: " + totalItems + " items | " + itemCounts.size() + " types");
            double itemsPerMinute = totalItems / durationMinutes;
            double itemsPerHour = itemsPerMinute * 60;
            info("Rates: " + df2.format(totalItems / durationSeconds) + "/s | " + df2.format(itemsPerMinute) + "/m | " + df2.format(itemsPerHour) + "/h");

            // Shulker box calculations (always shown)
            info("");
            info("=== Shulker Calculations (Stack: " + itemStackSize.get() + ") ===");
            int stackSize = itemStackSize.get();
            int itemsPerShulker = 27 * stackSize;
            int itemsPerDub = 54 * itemsPerShulker;

            if (itemsPerMinute > 0) {
                double minutesPerShulker = itemsPerShulker / itemsPerMinute;
                double hoursPerShulker = minutesPerShulker / 60;
                double hoursPerDub = (itemsPerDub / itemsPerMinute) / 60;
                double daysPerDub = hoursPerDub / 24;

                info("Shulker (" + itemsPerShulker + " items): " + df2.format(minutesPerShulker) + "m (" + df2.format(hoursPerShulker) + "h)");
                info("Dub (" + itemsPerDub + " items): " + df2.format(hoursPerDub) + "h (" + df2.format(daysPerDub) + " days)");

                double shulkersPerHour = 60 / minutesPerShulker;
                double shulkersPerDay = shulkersPerHour * 24;
                double dubsPerDay = 24 / hoursPerDub;

                info("Production: " + df2.format(shulkersPerHour) + " shulkers/h | " + df2.format(shulkersPerDay) + " shulkers/day | " + df2.format(dubsPerDay) + " dubs/day");
            } else {
                info("Not enough data to calculate production rates");
            }

            // Distribution (only if multiple items)
            if (sortedItems.size() > 1) {
                Map.Entry<String, Integer> mostCommon = sortedItems.get(0);
                Map.Entry<String, Integer> leastCommon = sortedItems.get(sortedItems.size() - 1);
                info("");
                info("=== Distribution ===");
                info("Most: " + mostCommon.getKey() + " (" + mostCommon.getValue() + ") | Least: " + leastCommon.getKey() + " (" + leastCommon.getValue() + ")");
                info("Rarity ratio: " + df2.format((double) mostCommon.getValue() / leastCommon.getValue()) + ":1 | Avg per type: " + df2.format((double) totalItems / itemCounts.size()));
            }
        } catch (Exception e) {
            error("Error calculating statistics: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void clearLogFile() {
        // Clear the file on a separate thread
        new Thread(() -> {
            try {
                // Get AppData directory
                String appData = System.getenv("APPDATA");
                if (appData == null) {
                    // Fallback for non-Windows systems
                    String userHome = System.getProperty("user.home");
                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("mac")) {
                        appData = userHome + "/Library/Application Support";
                    } else if (os.contains("linux")) {
                        appData = userHome + "/.local/share";
                    } else {
                        appData = userHome;
                    }
                }

                // Create base directory
                File baseDir = new File(appData, ".minecraft" + File.separator + "meteor-client" + File.separator + "hybridious_mod");
                if (!baseDir.exists()) {
                    baseDir.mkdirs();
                }

                // Delete the old log file
                File resultsFile = new File(baseDir, "DropTest.txt");
                if (resultsFile.exists()) {
                    if (resultsFile.delete()) {
                        info("Previous log file cleared successfully");
                    } else {
                        warning("Failed to clear previous log file");
                    }
                } else {
                    info("No previous log file to clear");
                }

                // Reset the checkbox after a short delay
                try {
                    Thread.sleep(100);
                    clearLogsButton.set(false);
                } catch (InterruptedException e) {
                    // Ignore
                }

            } catch (Exception e) {
                error("Error clearing log file: " + e.getMessage());
                // Still reset the checkbox even if there was an error
                try {
                    Thread.sleep(100);
                    clearLogsButton.set(false);
                } catch (InterruptedException ex) {
                    // Ignore
                }
            }
        }, "DropTest-LogClearer").start();
    }

    private void saveTestResults() {
        // Create a copy of the data to avoid concurrent modification issues
        final Map<String, Integer> itemCountsCopy = new HashMap<>(itemCounts);
        final int trackedEntitiesCount = trackedEntities.size();
        final long startTime = testStartTime;
        final long endTime = testEndTime;
        final int ticks = totalTicks;
        final int checks = checksPerformed;
        final List<String> targets = new ArrayList<>(targetItems.get());
        final boolean trackAll = trackAllItems.get();
        final int stackSize = itemStackSize.get();

        // Run file operations on a separate thread to prevent game crashes
        Thread saveThread = new Thread(() -> {
            BufferedWriter writer = null;
            try {
                // Get AppData directory
                String appData = System.getenv("APPDATA");
                if (appData == null) {
                    // Fallback for non-Windows systems
                    String userHome = System.getProperty("user.home");
                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("mac")) {
                        appData = userHome + "/Library/Application Support";
                    } else if (os.contains("linux")) {
                        appData = userHome + "/.local/share";
                    } else {
                        appData = userHome;
                    }
                }

                // Create base directory
                File baseDir = new File(appData, ".minecraft" + File.separator + "meteor-client" + File.separator + "hybridious_mod");
                if (!baseDir.exists()) {
                    baseDir.mkdirs();
                }

                // Single file: DropTest.txt
                File resultsFile = new File(baseDir, "DropTest.txt");

                // Use BufferedWriter for better performance
                writer = new BufferedWriter(new FileWriter(resultsFile, true));

                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                DecimalFormat df2 = new DecimalFormat("0.00");
                DecimalFormat df1 = new DecimalFormat("0.0");

                writer.write("\n");
                writer.write("=================================================================\n");
                writer.write("                    DROP TEST RESULTS\n");
                writer.write("=================================================================\n\n");

                // Test Configuration
                writer.write("TEST CONFIGURATION:\n");
                writer.write("-----------------------------------------------------------------\n");
                if (!targets.isEmpty()) {
                    writer.write("  Target Items:\n");
                    for (String target : targets) {
                        writer.write("    - " + target + "\n");
                    }
                } else {
                    writer.write("  Target Items: None specified\n");
                }
                writer.write("  Track All Items: " + (trackAll ? "Yes" : "No") + "\n");
                writer.write("  Configured Duration: " + testDuration.get() + " minutes\n");
                writer.write("  Scan Interval: " + scanInterval.get() + " ticks\n");
                writer.write("  Item Stack Size: " + stackSize + "\n");
                writer.write("\n");

                // Test Timeline
                writer.write("TEST TIMELINE:\n");
                writer.write("-----------------------------------------------------------------\n");
                writer.write("  Start Time: " + dateFormat.format(new Date(startTime)) + "\n");
                writer.write("  End Time:   " + dateFormat.format(new Date(endTime)) + "\n");

                long durationMillis = endTime - startTime;
                double durationMinutes = durationMillis / 60000.0;
                double durationSeconds = durationMillis / 1000.0;

                writer.write("  Actual Duration: " + df2.format(durationMinutes) + " minutes (" + df1.format(durationSeconds) + " seconds)\n");
                writer.write("\n");

                // Test Metrics
                writer.write("TEST METRICS:\n");
                writer.write("-----------------------------------------------------------------\n");
                writer.write("  Total Ticks: " + ticks + "\n");
                writer.write("  Checks Performed: " + checks + "\n");
                writer.write("  Unique Entities Found: " + trackedEntitiesCount + "\n");
                writer.write("\n");

                writer.write("=================================================================\n");
                writer.write("                  ITEM DROP STATISTICS\n");
                writer.write("=================================================================\n\n");

                if (itemCountsCopy.isEmpty()) {
                    writer.write("No items were found during this test.\n");
                } else {
                    // Sort items by count (descending)
                    List<Map.Entry<String, Integer>> sortedItems = new ArrayList<>(itemCountsCopy.entrySet());
                    sortedItems.sort((a, b) -> b.getValue().compareTo(a.getValue()));

                    int totalItems = 0;
                    for (Integer count : itemCountsCopy.values()) {
                        totalItems += count;
                    }

                    int rank = 1;
                    for (Map.Entry<String, Integer> entry : sortedItems) {
                        String itemId = entry.getKey();
                        int count = entry.getValue();

                        double perSecond = count / durationSeconds;
                        double perMinute = count / durationMinutes;
                        double perHour = perMinute * 60;
                        double percentage = (count * 100.0) / totalItems;

                        writer.write("#" + rank + " - " + itemId + "\n");
                        writer.write("-----------------------------------------------------------------\n");
                        writer.write("  Total Count: " + count + " items\n");
                        writer.write("  Percentage of Total: " + df2.format(percentage) + " percent\n");
                        writer.write("  Drop Rate (per second): " + df2.format(perSecond) + " items/sec\n");
                        writer.write("  Drop Rate (per minute): " + df2.format(perMinute) + " items/min\n");
                        writer.write("  Drop Rate (per hour): " + df2.format(perHour) + " items/hour\n");
                        writer.write("\n");
                        rank++;
                    }

                    writer.write("=================================================================\n");
                    writer.write("                        SUMMARY\n");
                    writer.write("=================================================================\n\n");
                    writer.write("  Total Items Collected: " + totalItems + "\n");
                    writer.write("  Items Per Second: " + df2.format(totalItems / durationSeconds) + "\n");
                    writer.write("  Items Per Minute: " + df2.format(totalItems / durationMinutes) + "\n");
                    writer.write("  Items Per Hour: " + df2.format((totalItems / durationMinutes) * 60) + "\n");
                    writer.write("  Unique Item Types: " + itemCountsCopy.size() + "\n");
                    writer.write("\n");

                    // Shulker box calculations (always included)
                    int itemsPerShulker = 27 * stackSize; // 27 slots * stack size
                    int itemsPerDoubleChest = 54 * itemsPerShulker; // 54 slots filled with shulkers

                    double itemsPerMinute = totalItems / durationMinutes;
                    double itemsPerHour = itemsPerMinute * 60;

                    // Only calculate if we have valid data
                    if (itemsPerMinute > 0) {
                        double minutesPerShulker = itemsPerShulker / itemsPerMinute;
                        double hoursPerShulker = minutesPerShulker / 60;
                        double minutesPerDub = itemsPerDoubleChest / itemsPerMinute;
                        double hoursPerDub = minutesPerDub / 60;
                        double daysPerDub = hoursPerDub / 24;

                        double shulkersPerHour = 60 / minutesPerShulker;
                        double shulkersPerDay = shulkersPerHour * 24;
                        double dubsPerDay = 24 / hoursPerDub;

                        writer.write("=================================================================\n");
                        writer.write("                  SHULKER BOX CALCULATIONS\n");
                        writer.write("=================================================================\n\n");
                        writer.write("STORAGE CAPACITY:\n");
                        writer.write("-----------------------------------------------------------------\n");
                        writer.write("  Stack Size: " + stackSize + " items per stack\n");
                        writer.write("  Items Per Shulker Box: " + itemsPerShulker + " items (27 slots)\n");
                        writer.write("  Items Per Double Chest (Dub): " + itemsPerDoubleChest + " items (54 shulkers)\n");
                        writer.write("\n");

                        writer.write("TIME TO FILL:\n");
                        writer.write("-----------------------------------------------------------------\n");
                        writer.write("  One Shulker Box:\n");
                        writer.write("    " + df2.format(minutesPerShulker) + " minutes\n");
                        writer.write("    " + df2.format(hoursPerShulker) + " hours\n");
                        writer.write("\n");
                        writer.write("  One Double Chest (Dub):\n");
                        writer.write("    " + df2.format(minutesPerDub) + " minutes\n");
                        writer.write("    " + df2.format(hoursPerDub) + " hours\n");
                        writer.write("    " + df2.format(daysPerDub) + " days\n");
                        writer.write("\n");

                        writer.write("PRODUCTION RATES:\n");
                        writer.write("-----------------------------------------------------------------\n");
                        writer.write("  Shulkers Per Hour: " + df2.format(shulkersPerHour) + "\n");
                        writer.write("  Shulkers Per Day: " + df2.format(shulkersPerDay) + "\n");
                        writer.write("  Dubs Per Day: " + df2.format(dubsPerDay) + "\n");
                        writer.write("\n");

                        // Additional useful calculations
                        writer.write("BULK STORAGE ESTIMATES:\n");
                        writer.write("-----------------------------------------------------------------\n");
                        writer.write("  In 1 Hour: " + df2.format(shulkersPerHour) + " shulkers (" + df2.format(itemsPerHour) + " items)\n");
                        writer.write("  In 24 Hours: " + df2.format(shulkersPerDay) + " shulkers (" + df2.format(itemsPerHour * 24) + " items)\n");
                        writer.write("  In 7 Days: " + df2.format(shulkersPerDay * 7) + " shulkers (" + df2.format(itemsPerHour * 24 * 7) + " items)\n");
                        writer.write("  In 30 Days: " + df2.format(shulkersPerDay * 30) + " shulkers (" + df2.format(itemsPerHour * 24 * 30) + " items)\n");
                        writer.write("\n");
                    } else {
                        writer.write("=================================================================\n");
                        writer.write("                  SHULKER BOX CALCULATIONS\n");
                        writer.write("=================================================================\n\n");
                        writer.write("Not enough data to calculate shulker production rates.\n\n");
                    }

                    // Add distribution analysis if multiple items
                    if (sortedItems.size() > 1) {
                        Map.Entry<String, Integer> mostCommon = sortedItems.get(0);
                        Map.Entry<String, Integer> leastCommon = sortedItems.get(sortedItems.size() - 1);

                        writer.write("=================================================================\n");
                        writer.write("                   DISTRIBUTION ANALYSIS\n");
                        writer.write("=================================================================\n\n");
                        writer.write("  Most Common Item: " + mostCommon.getKey() + "\n");
                        writer.write("    Count: " + mostCommon.getValue() + " items\n");
                        writer.write("    Share: " + df2.format((mostCommon.getValue() * 100.0) / totalItems) + " percent of total\n");
                        writer.write("\n");
                        writer.write("  Least Common Item: " + leastCommon.getKey() + "\n");
                        writer.write("    Count: " + leastCommon.getValue() + " items\n");
                        writer.write("    Share: " + df2.format((leastCommon.getValue() * 100.0) / totalItems) + " percent of total\n");
                        writer.write("\n");
                        writer.write("  Rarity Ratio: " + df2.format((double) mostCommon.getValue() / leastCommon.getValue()) + ":1\n");
                        writer.write("  Average Per Item Type: " + df2.format((double) totalItems / itemCountsCopy.size()) + " items\n");
                        writer.write("\n");
                    }

                    // Add performance metrics
                    writer.write("=================================================================\n");
                    writer.write("                   PERFORMANCE METRICS\n");
                    writer.write("=================================================================\n\n");
                    writer.write("  Total Entities Found: " + trackedEntitiesCount + "\n");
                    writer.write("  Total Scans Performed: " + checks + " scans\n");
                    writer.write("  Scans Per Second: " + df2.format((double) checks / durationSeconds) + "\n");
                    writer.write("  Items Found Per Scan: " + df2.format((double) totalItems / checks) + "\n");
                    writer.write("  Total Ticks: " + ticks + " ticks\n");
                    writer.write("\n");
                }

                writer.write("=================================================================\n");
                writer.write("                      END OF TEST\n");
                writer.write("=================================================================\n\n\n");

                writer.flush();

                info("Test results saved successfully!");
                info("Location: " + resultsFile.getAbsolutePath());

            } catch (Exception e) {
                error("Failed to save test results: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // Always close the writer
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (Exception e) {
                        // Ignore close errors
                    }
                }
            }
        }, "DropTest-FileSaver");

        // Set as daemon thread so it doesn't prevent game shutdown
        saveThread.setDaemon(true);
        saveThread.start();
    }
}
