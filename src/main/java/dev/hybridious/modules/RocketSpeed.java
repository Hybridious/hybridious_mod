package dev.hybridious.modules;

import dev.hybridious.Hybridious;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class RocketSpeed extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speedThreshold = sgGeneral.add(new DoubleSetting.Builder()
            .name("speed-threshold")
            .description("Speed threshold in km/h to trigger firework boost.")
            .defaultValue(50.0)
            .min(0.0)
            .max(200.0)
            .sliderMax(100.0)
            .build()
    );

    private final Setting<Integer> fireworkCount = sgGeneral.add(new IntSetting.Builder()
            .name("firework-count")
            .description("Number of fireworks to use per boost.")
            .defaultValue(1)
            .min(1)
            .max(5)
            .sliderMax(3)
            .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
            .name("cooldown")
            .description("Cooldown in ticks between boosts.")
            .defaultValue(10)
            .min(0)
            .max(100)
            .sliderMax(40)
            .build()
    );

    private final Setting<Boolean> elytraOnly = sgGeneral.add(new BoolSetting.Builder()
            .name("elytra-only")
            .description("Only boost when flying with elytra.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> minFireworks = sgGeneral.add(new IntSetting.Builder()
            .name("min-fireworks")
            .description("Minimum fireworks required in inventory before boosting.")
            .defaultValue(5)
            .min(1)
            .max(64)
            .sliderMax(20)
            .build()
    );

    private final Setting<Boolean> smartBoost = sgGeneral.add(new BoolSetting.Builder()
            .name("smart-boost")
            .description("Only boost when speed is decreasing (not when already accelerating).")
            .defaultValue(true)
            .build()
    );

    private int cooldownTicks = 0;
    private double lastSpeed = 0;
    private double previousSpeed = 0;

    public RocketSpeed() {
        super(Hybridious.CATEGORY, "rocket-speed", "Automatically uses fireworks when speed drops below threshold.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        // Decrease cooldown
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        // Check if elytra flying - check if player is flying and has elytra equipped
        if (elytraOnly.get() && !isElytraFlying()) {
            return;
        }

        // Calculate speed in km/h
        double deltaX = mc.player.getX() - mc.player.prevX;
        double deltaY = mc.player.getY() - mc.player.prevY;
        double deltaZ = mc.player.getZ() - mc.player.prevZ;
        double speed = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
        double speedKmh = speed * 72.0; // Convert to km/h: blocks/tick * 20 tps * 3.6 m/s to km/h

        // Smart boost check - only boost if speed is decreasing
        boolean shouldBoost = speedKmh < speedThreshold.get();
        if (smartBoost.get() && shouldBoost) {
            shouldBoost = speedKmh < previousSpeed; // Only if losing speed
        }

        previousSpeed = lastSpeed;
        lastSpeed = speedKmh;

        // Check if below threshold
        if (shouldBoost) {
            useFireworks();
        }
    }

    private boolean isElytraFlying() {
        if (mc.player == null) return false;
        // Check if player is not on ground and has elytra equipped
        return !mc.player.isOnGround() &&
                mc.player.getInventory().getArmorStack(2).getItem() == Items.ELYTRA;
    }

    private void useFireworks() {
        if (mc.player == null || mc.interactionManager == null) return;

        // Count total fireworks in hotbar
        int totalFireworks = 0;
        int fireworkSlot = -1;

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.FIREWORK_ROCKET) {
                if (fireworkSlot == -1) fireworkSlot = i;
                totalFireworks += mc.player.getInventory().getStack(i).getCount();
            }
        }

        // Check if we have enough fireworks
        if (totalFireworks < minFireworks.get()) {
            warning("Not enough fireworks! (" + totalFireworks + "/" + minFireworks.get() + ")");
            return;
        }

        if (fireworkSlot == -1) {
            error("No fireworks found in hotbar!");
            return;
        }

        // Save current slot
        int previousSlot = mc.player.getInventory().selectedSlot;

        // Use fireworks
        for (int i = 0; i < fireworkCount.get(); i++) {
            mc.player.getInventory().selectedSlot = fireworkSlot;
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        }

        // Restore previous slot
        mc.player.getInventory().selectedSlot = previousSlot;

        // Set cooldown
        cooldownTicks = cooldown.get();
    }

    @Override
    public String getInfoString() {
        if (mc.player == null) return null;

        // Count total fireworks
        int totalFireworks = 0;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.FIREWORK_ROCKET) {
                totalFireworks += mc.player.getInventory().getStack(i).getCount();
            }
        }

        return String.format("%.1f km/h | %d🎆", lastSpeed, totalFireworks);
    }
}
