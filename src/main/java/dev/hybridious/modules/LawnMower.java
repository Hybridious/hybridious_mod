package dev.hybridious.modules;

import dev.hybridious.Hybridious;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class LawnMower extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> range = sgGeneral.add(new IntSetting.Builder()
        .name("range")
        .description("The range to search for vegetation.")
        .defaultValue(4)
        .min(1)
        .max(6)
        .sliderMin(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Delay between breaking blocks in ticks.")
        .defaultValue(0)
        .min(0)
        .max(10)
        .sliderMin(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Boolean> breakGrass = sgGeneral.add(new BoolSetting.Builder()
        .name("break-grass")
        .description("Break short and tall grass.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> breakFlowers = sgGeneral.add(new BoolSetting.Builder()
        .name("break-flowers")
        .description("Break all types of flowers.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> breakSaplings = sgGeneral.add(new BoolSetting.Builder()
        .name("break-saplings")
        .description("Break all types of saplings.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> breakDeadBush = sgGeneral.add(new BoolSetting.Builder()
        .name("break-dead-bush")
        .description("Break dead bushes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> breakFerns = sgGeneral.add(new BoolSetting.Builder()
        .name("break-ferns")
        .description("Break ferns and large ferns.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> breakSeagrass = sgGeneral.add(new BoolSetting.Builder()
        .name("break-seagrass")
        .description("Break seagrass and tall seagrass.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> switchToShears = sgGeneral.add(new BoolSetting.Builder()
        .name("switch-to-shears")
        .description("Automatically switch to shears when breaking vegetation.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotate towards blocks when breaking them.")
        .defaultValue(true)
        .build()
    );

    private int timer = 0;

    public LawnMower() {
        super(Hybridious.CATEGORY, "LawnMower", "Automatically breaks grass, flowers, and saplings around you.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (timer > 0) {
            timer--;
            return;
        }

        List<BlockPos> targets = findTargets();

        if (targets.isEmpty()) return;

        // Sort by closest first
        targets.sort((a, b) -> {
            double distA = mc.player.squaredDistanceTo(Vec3d.ofCenter(a));
            double distB = mc.player.squaredDistanceTo(Vec3d.ofCenter(b));
            return Double.compare(distA, distB);
        });

        BlockPos target = targets.get(0);

        // Switch to shears if enabled and available
        if (switchToShears.get()) {
            FindItemResult shears = InvUtils.find(Items.SHEARS);
            if (shears.found()) {
                InvUtils.swap(shears.slot(), false);
            }
        }

        // Break the block
        if (BlockUtils.breakBlock(target, rotate.get())) {
            timer = delay.get();
        }
    }

    private List<BlockPos> findTargets() {
        List<BlockPos> targets = new ArrayList<>();

        BlockPos playerPos = mc.player.getBlockPos();
        int r = range.get();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    Block block = mc.world.getBlockState(pos).getBlock();

                    if (isTargetBlock(block)) {
                        targets.add(pos);
                    }
                }
            }
        }

        return targets;
    }

    private boolean isTargetBlock(Block block) {
        // Grass types
        if (breakGrass.get()) {
            if (block == Blocks.SHORT_GRASS || block == Blocks.TALL_GRASS) {
                return true;
            }
        }

        // Flowers
        if (breakFlowers.get()) {
            if (block instanceof FlowerBlock ||
                block instanceof TallFlowerBlock ||
                block == Blocks.POPPY ||
                block == Blocks.DANDELION ||
                block == Blocks.BLUE_ORCHID ||
                block == Blocks.ALLIUM ||
                block == Blocks.AZURE_BLUET ||
                block == Blocks.RED_TULIP ||
                block == Blocks.ORANGE_TULIP ||
                block == Blocks.WHITE_TULIP ||
                block == Blocks.PINK_TULIP ||
                block == Blocks.OXEYE_DAISY ||
                block == Blocks.CORNFLOWER ||
                block == Blocks.LILY_OF_THE_VALLEY ||
                block == Blocks.WITHER_ROSE ||
                block == Blocks.SUNFLOWER ||
                block == Blocks.LILAC ||
                block == Blocks.ROSE_BUSH ||
                block == Blocks.PEONY) {
                return true;
            }
        }

        // Saplings
        if (breakSaplings.get() && block instanceof SaplingBlock) {
            return true;
        }

        // Dead bush
        if (breakDeadBush.get() && block == Blocks.DEAD_BUSH) {
            return true;
        }

        // Ferns
        if (breakFerns.get()) {
            if (block == Blocks.FERN || block == Blocks.LARGE_FERN) {
                return true;
            }
        }

        // Seagrass
        if (breakSeagrass.get()) {
            if (block == Blocks.SEAGRASS || block == Blocks.TALL_SEAGRASS) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String getInfoString() {
        return String.valueOf(findTargets().size());
    }
}
