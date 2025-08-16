package dev.hybridious.modules;

import dev.hybridious.Hybridious;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.passive.StriderEntity;
import net.minecraft.util.math.MathHelper;
import meteordevelopment.orbit.EventHandler;

public class SethBoat extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRotation = settings.createGroup("Rotation Lock");
    private final SettingGroup sgAutoWalk = settings.createGroup("Auto Walk");

    // General settings
    private final Setting<Boolean> smartMode = sgGeneral.add(new BoolSetting.Builder()
        .name("smart-mode")
        .description("Automatically detects when riding entities and applies rotation lock accordingly.")
        .defaultValue(true)
        .build()
    );

    // Rotation Lock settings
    private final Setting<Boolean> enableRotationLock = sgRotation.add(new BoolSetting.Builder()
        .name("enable-rotation-lock")
        .description("Locks player rotation in the direction they're facing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> lockOnActivation = sgRotation.add(new BoolSetting.Builder()
        .name("lock-on-activation")
        .description("Lock rotation to current direction when module is activated.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> vehicleRotation = sgRotation.add(new BoolSetting.Builder()
        .name("vehicle-rotation")
        .description("Apply rotation lock to rideable entities (boats, horses, etc.).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> rotationSpeed = sgRotation.add(new DoubleSetting.Builder()
        .name("rotation-speed")
        .description("Speed of rotation changes for vehicles (deprecated - now uses instant snapping).")
        .defaultValue(5.0)
        .min(1.0)
        .max(20.0)
        .sliderMax(20.0)
        .build()
    );

    // Auto Walk settings
    private final Setting<Boolean> enableAutoWalk = sgAutoWalk.add(new BoolSetting.Builder()
        .name("enable-auto-walk")
        .description("Automatically walks forward.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> walkInVehicles = sgAutoWalk.add(new BoolSetting.Builder()
        .name("walk-in-vehicles")
        .description("Continue auto-walking when in vehicles.")
        .defaultValue(true)
        .build()
    );

    // Internal state
    private float lockedYaw = 0.0f;
    private float lockedPitch = 0.0f;
    private boolean isLocked = false;

    // 24 directional constants (15 degrees each)
    private static final int TOTAL_DIRECTIONS = 24;
    private static final float DEGREES_PER_DIRECTION = 360.0f / TOTAL_DIRECTIONS; // 15 degrees

    private static final String[] DIRECTION_NAMES = {
        "North", "NNE", "NE", "ENE", "East", "ESE", "SE", "SSE",
        "South", "SSW", "SW", "WSW", "West", "WNW", "NW", "NNW",
        "North+", "NNE+", "NE+", "ENE+", "East+", "ESE+", "SE+", "SSE+"
    };

    public SethBoat() {
        super(Hybridious.CATEGORY, "SethBoat", "Created for SethQuest for the ice boat highways.");
    }

    @Override
    public void onActivate() {
        if (lockOnActivation.get() && enableRotationLock.get()) {
            lockCurrentRotation();
        }
    }

    @Override
    public void onDeactivate() {
        isLocked = false;
        // Stop auto-walking
        if (mc.player != null) {
            mc.options.forwardKey.setPressed(false);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        Entity ridingEntity = mc.player.getVehicle();
        boolean isRiding = ridingEntity != null;

        // Handle smart mode activation
        if (smartMode.get() && !isLocked && isRiding && isRideableEntity(ridingEntity)) {
            lockCurrentRotation();
        }

        // Apply rotation lock
        if (enableRotationLock.get() && isLocked) {
            applyRotationLock(ridingEntity, isRiding);
        }

        // Apply auto walk
        if (enableAutoWalk.get()) {
            applyAutoWalk(isRiding);
        }
    }

    private void lockCurrentRotation() {
        if (mc.player == null) return;

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        // Snap to nearest 24-direction
        lockedYaw = snapToDirection(currentYaw);
        lockedPitch = currentPitch; // Keep original pitch or snap if desired
        isLocked = true;

        // Get direction name for info display
        String directionName = getDirectionName(lockedYaw);
        info("Rotation locked to %s (%.1f°)", directionName, lockedYaw);
    }

    private void applyRotationLock(Entity ridingEntity, boolean isRiding) {
        if (mc.player == null) return;

        if (isRiding && vehicleRotation.get() && isRideableEntity(ridingEntity)) {
            // Apply rotation to vehicle
            applyVehicleRotation(ridingEntity);
        } else if (!isRiding) {
            // Apply rotation to player
            Rotations.rotate(lockedYaw, lockedPitch);
        }
    }

    private void applyVehicleRotation(Entity vehicle) {
        if (vehicle == null) return;

        // Direct snap to locked direction for vehicles
        float targetYaw = lockedYaw;

        // Apply rotation based on vehicle type
        if (vehicle instanceof BoatEntity boat) {
            boat.setYaw(targetYaw);
            boat.prevYaw = targetYaw;
        } else if (vehicle instanceof MinecartEntity minecart) {
            minecart.setYaw(targetYaw);
        } else if (vehicle instanceof HorseEntity horse) {
            horse.setYaw(targetYaw);
            if (horse instanceof LivingEntity livingHorse) {
                livingHorse.setBodyYaw(targetYaw);
            }
        } else if (vehicle instanceof PigEntity pig) {
            pig.setYaw(targetYaw);
            pig.setBodyYaw(targetYaw);
        } else if (vehicle instanceof StriderEntity strider) {
            strider.setYaw(targetYaw);
            strider.setBodyYaw(targetYaw);
        } else if (vehicle instanceof LlamaEntity llama) {
            llama.setYaw(targetYaw);
            llama.setBodyYaw(targetYaw);
        }

        // Also apply rotation to player to keep camera aligned
        Rotations.rotate(lockedYaw, lockedPitch);
    }

    private void applyAutoWalk(boolean isRiding) {
        if (mc.player == null) return;

        boolean shouldWalk = !isRiding || walkInVehicles.get();

        if (shouldWalk) {
            mc.options.forwardKey.setPressed(true);
        } else {
            mc.options.forwardKey.setPressed(false);
        }
    }

    private boolean isRideableEntity(Entity entity) {
        return entity instanceof BoatEntity ||
            entity instanceof MinecartEntity ||
            entity instanceof HorseEntity ||
            entity instanceof PigEntity ||
            entity instanceof StriderEntity ||
            entity instanceof LlamaEntity ||
            entity.hasControllingPassenger();
    }

    // Utility method to manually lock rotation (can be bound to key)
    public void toggleRotationLock() {
        if (isLocked) {
            isLocked = false;
            info("Rotation lock disabled");
        } else {
            lockCurrentRotation();
        }
    }

    /**
     * Snaps the given yaw to the nearest of 24 directions (15° increments)
     */
    private float snapToDirection(float yaw) {
        // Normalize yaw to 0-360 range
        yaw = MathHelper.wrapDegrees(yaw);
        if (yaw < 0) yaw += 360;

        // Calculate which direction index this yaw is closest to
        int directionIndex = Math.round(yaw / DEGREES_PER_DIRECTION) % TOTAL_DIRECTIONS;

        // Convert back to yaw angle
        float snappedYaw = directionIndex * DEGREES_PER_DIRECTION;

        // Convert back to -180 to 180 range that Minecraft expects
        if (snappedYaw > 180) {
            snappedYaw -= 360;
        }

        return snappedYaw;
    }

    /**
     * Gets a readable name for the given direction
     */
    private String getDirectionName(float yaw) {
        // Normalize to 0-360
        float normalizedYaw = yaw;
        if (normalizedYaw < 0) normalizedYaw += 360;

        int directionIndex = Math.round(normalizedYaw / DEGREES_PER_DIRECTION) % TOTAL_DIRECTIONS;
        return DIRECTION_NAMES[directionIndex];
    }

    /**
     * Gets the current locked direction as an index (0-23)
     */
    public int getLockedDirectionIndex() {
        if (!isLocked) return -1;

        float normalizedYaw = lockedYaw;
        if (normalizedYaw < 0) normalizedYaw += 360;

        return Math.round(normalizedYaw / DEGREES_PER_DIRECTION) % TOTAL_DIRECTIONS;
    }

    // Getters for other modules or GUI
    public boolean isRotationLocked() {
        return isLocked && enableRotationLock.get();
    }

    public float getLockedYaw() {
        return lockedYaw;
    }

    public float getLockedPitch() {
        return lockedPitch;
    }
}
