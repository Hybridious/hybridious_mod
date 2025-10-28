package dev.hybridious.modules;

import dev.hybridious.Hybridious;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BannerPatternsComponent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class BannerFinder extends Module {
    private static final Path BLACKLIST_DIR = Paths.get(System.getenv("APPDATA"), ".minecraft", "meteor-client", "hybridious_mod");
    private static final Path BLACKLIST_FILE = BLACKLIST_DIR.resolve("banner_blacklist.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> blacklistedPatterns = new HashSet<>();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgSound = settings.createGroup("Sound");



    private final Setting<Boolean> ignoreBlacklisted = sgGeneral.add(new BoolSetting.Builder()
            .name("ignore-blacklisted")
            .description("Ignores banners that are in the blacklist (doesn't alert).")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> tracer = sgRender.add(new BoolSetting.Builder()
            .name("tracer")
            .description("Draws a tracer line to the banner.")
            .defaultValue(true)
            .build()
    );

    private final Setting<SettingColor> tracerColor = sgRender.add(new ColorSetting.Builder()
            .name("tracer-color")
            .description("The color of the tracer.")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .visible(tracer::get)
            .build()
    );

    private final Setting<Double> tracerWidth = sgRender.add(new DoubleSetting.Builder()
            .name("tracer-width")
            .description("The width of the tracer line.")
            .defaultValue(1.0)
            .min(0.1)
            .max(5.0)
            .sliderMin(0.1)
            .sliderMax(5.0)
            .visible(tracer::get)
            .build()
    );

    private final Setting<Boolean> box = sgRender.add(new BoolSetting.Builder()
            .name("box")
            .description("Renders a box around the banner.")
            .defaultValue(true)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the box is rendered.")
            .defaultValue(ShapeMode.Both)
            .visible(box::get)
            .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color")
            .description("The side color of the box.")
            .defaultValue(new SettingColor(255, 0, 0, 50))
            .visible(box::get)
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color")
            .description("The line color of the box.")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .visible(box::get)
            .build()
    );

    private final Setting<Boolean> soundAlert = sgSound.add(new BoolSetting.Builder()
            .name("sound-alert")
            .description("Plays a sound when a new banner is found.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> soundCooldown = sgSound.add(new IntSetting.Builder()
            .name("sound-cooldown")
            .description("Cooldown between sound alerts in ticks.")
            .defaultValue(40)
            .min(0)
            .sliderMax(200)
            .visible(soundAlert::get)
            .build()
    );

    private final Set<BlockPos> foundBanners = new HashSet<>();
    private final Set<BlockPos> notifiedBanners = new HashSet<>();
    private int soundCooldownTicks = 0;

    public BannerFinder() {
        super(Hybridious.CATEGORY, "banner-finder", "Finds and highlights banners with tracers and alerts.");
        initBlacklist();
    }

    @Override
    public void onActivate() {
        foundBanners.clear();
        notifiedBanners.clear();
        soundCooldownTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        foundBanners.clear();

        if (soundCooldownTicks > 0) {
            soundCooldownTicks--;
        }

        BlockPos playerPos = mc.player.getBlockPos();
        int renderDistanceChunks = mc.options.getViewDistance().getValue();
        int renderDistanceBlocks = renderDistanceChunks * 16;

        // Iterate through loaded chunks and their block entities
        int chunkX = playerPos.getX() >> 4;
        int chunkZ = playerPos.getZ() >> 4;

        for (int x = -renderDistanceChunks; x <= renderDistanceChunks; x++) {
            for (int z = -renderDistanceChunks; z <= renderDistanceChunks; z++) {
                var chunk = mc.world.getChunk(chunkX + x, chunkZ + z);
                if (chunk == null) continue;

                // Iterate through all block entities in the chunk
                var blockEntities = chunk.getBlockEntities();
                for (var entry : blockEntities.entrySet()) {
                    BlockPos pos = entry.getKey();
                    BlockEntity blockEntity = entry.getValue();

                    if (!(blockEntity instanceof BannerBlockEntity bannerEntity)) continue;

                    // Check distance
                    if (Math.abs(pos.getX() - playerPos.getX()) > renderDistanceBlocks ||
                            Math.abs(pos.getZ() - playerPos.getZ()) > renderDistanceBlocks) {
                        continue;
                    }

                    if (ignoreBlacklisted.get() && isBlacklisted(bannerEntity)) {
                        continue;
                    }

                    foundBanners.add(pos);

                    if (soundAlert.get() && !notifiedBanners.contains(pos) && soundCooldownTicks == 0) {
                        mc.getSoundManager().play(PositionedSoundInstance.master(
                                SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 1.0f
                        ));
                        notifiedBanners.add(pos);
                        soundCooldownTicks = soundCooldown.get();
                    }
                }
            }
        }

        notifiedBanners.removeIf(pos -> !foundBanners.contains(pos));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        for (BlockPos pos : foundBanners) {
            double x = pos.getX();
            double y = pos.getY();
            double z = pos.getZ();

            if (tracer.get()) {
                // Get player's look vector
                var rotationVec = mc.player.getRotationVec(1.0f);
                double startX = mc.player.getX() + rotationVec.x * 0.5;
                double startY = mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()) + rotationVec.y * 0.5;
                double startZ = mc.player.getZ() + rotationVec.z * 0.5;

                // Draw line with width by drawing multiple lines
                double width = tracerWidth.get();
                if (width <= 1.0) {
                    event.renderer.line(
                            startX, startY, startZ,
                            x + 0.5, y + 0.5, z + 0.5,
                            tracerColor.get()
                    );
                } else {
                    // Draw thicker line by rendering multiple offset lines
                    for (double offset = -width / 2; offset <= width / 2; offset += 0.1) {
                        event.renderer.line(
                                startX + offset * 0.01, startY + offset * 0.01, startZ,
                                x + 0.5, y + 0.5, z + 0.5,
                                tracerColor.get()
                        );
                    }
                }
            }

            if (box.get()) {
                Box box = new Box(x, y, z, x + 1, y + 1, z + 1);
                event.renderer.box(box, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            }
        }
    }

    // Blacklist management
    private void initBlacklist() {
        try {
            Files.createDirectories(BLACKLIST_DIR);
            loadBlacklist();
        } catch (IOException e) {
            error("Failed to initialize banner blacklist: " + e.getMessage());
        }
    }

    public boolean addCurrentBanner() {
        if (mc.player == null) return false;

        var stack = mc.player.getMainHandStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof net.minecraft.item.BannerItem)) {
            return false;
        }

        String hash = getBannerHash(stack);
        if (hash == null) return false;

        blacklistedPatterns.add(hash);
        saveBlacklist();
        return true;
    }

    public boolean removeCurrentBanner() {
        if (mc.player == null) return false;

        var stack = mc.player.getMainHandStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof net.minecraft.item.BannerItem)) {
            return false;
        }

        String hash = getBannerHash(stack);
        if (hash == null) return false;

        boolean removed = blacklistedPatterns.remove(hash);
        if (removed) {
            saveBlacklist();
        }
        return removed;
    }

    public void clearBlacklist() {
        blacklistedPatterns.clear();
        saveBlacklist();
    }

    public int getBlacklistSize() {
        return blacklistedPatterns.size();
    }

    private boolean isBlacklisted(BannerBlockEntity entity) {
        String hash = getBannerHash(entity);
        return hash != null && blacklistedPatterns.contains(hash);
    }

    private String getBannerHash(net.minecraft.item.ItemStack stack) {
        try {
            BannerPatternsComponent patterns = stack.get(DataComponentTypes.BANNER_PATTERNS);
            if (patterns == null) return "EMPTY";

            StringBuilder hash = new StringBuilder();
            patterns.layers().forEach(layer -> {
                hash.append(layer.pattern().value().assetId()).append(":");
                hash.append(layer.color().getId()).append(";");
            });
            return hash.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String getBannerHash(BannerBlockEntity entity) {
        try {
            BannerPatternsComponent patterns = entity.getPatterns();
            if (patterns == null) return "EMPTY";

            StringBuilder hash = new StringBuilder();
            patterns.layers().forEach(layer -> {
                hash.append(layer.pattern().value().assetId()).append(":");
                hash.append(layer.color().getId()).append(";");
            });
            return hash.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void loadBlacklist() {
        if (!Files.exists(BLACKLIST_FILE)) return;

        try (Reader reader = Files.newBufferedReader(BLACKLIST_FILE)) {
            Type type = new TypeToken<Set<String>>(){}.getType();
            Set<String> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                blacklistedPatterns.clear();
                blacklistedPatterns.addAll(loaded);
            }
        } catch (IOException e) {
            error("Failed to load banner blacklist: " + e.getMessage());
        }
    }

    private void saveBlacklist() {
        try (Writer writer = Files.newBufferedWriter(BLACKLIST_FILE)) {
            GSON.toJson(blacklistedPatterns, writer);
        } catch (IOException e) {
            error("Failed to save banner blacklist: " + e.getMessage());
        }
    }
}
