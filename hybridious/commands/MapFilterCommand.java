package dev.hybridious.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.hybridious.modules.MapFilterModule;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class MapFilterCommand extends Command {
    public MapFilterCommand() {
        super("mapfilter", "Manage map filter cache");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("stats")
                        .executes(context -> {
                            MapFilterModule module = Modules.get().get(MapFilterModule.class);
                            if (module == null) {
                                error("Module not found");
                                return SINGLE_SUCCESS;
                            }
                            info("Cache Stats: " + module.getCacheStats());
                            return SINGLE_SUCCESS;
                        }))
                .then(literal("clear")
                        .executes(context -> {
                            MapFilterModule module = Modules.get().get(MapFilterModule.class);
                            if (module == null) {
                                error("Module not found");
                                return SINGLE_SUCCESS;
                            }
                            module.clearHashCache();
                            info("Hash cache cleared");
                            return SINGLE_SUCCESS;
                        }))
                .then(literal("whitelist")
                        .executes(context -> {
                            MapFilterModule module = Modules.get().get(MapFilterModule.class);
                            if (module == null) {
                                error("Module not found");
                                return SINGLE_SUCCESS;
                            }

                            Integer mapId = getHeldMapId();
                            if (mapId == null) {
                                error("No map found in hand");
                                return SINGLE_SUCCESS;
                            }

                            if (module.whitelistMap(mapId)) {
                                info("Map " + mapId + " whitelisted as safe");
                            } else {
                                error("Failed to whitelist map " + mapId);
                            }
                            return SINGLE_SUCCESS;
                        }))
                .then(literal("blacklist")
                        .executes(context -> {
                            MapFilterModule module = Modules.get().get(MapFilterModule.class);
                            if (module == null) {
                                error("Module not found");
                                return SINGLE_SUCCESS;
                            }

                            Integer mapId = getHeldMapId();
                            if (mapId == null) {
                                error("No map found in hand");
                                return SINGLE_SUCCESS;
                            }

                            if (module.blacklistMap(mapId)) {
                                info("Map " + mapId + " blacklisted as NSFW");
                            } else {
                                error("Failed to blacklist map " + mapId);
                            }
                            return SINGLE_SUCCESS;
                        }));
    }

    private Integer getHeldMapId() {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (player == null) return null;

        // Check main hand
        ItemStack mainHand = player.getMainHandStack();
        if (mainHand.isOf(Items.FILLED_MAP)) {
            MapIdComponent mapId = mainHand.get(DataComponentTypes.MAP_ID);
            if (mapId != null) return mapId.id();
        }

        // Check off hand
        ItemStack offHand = player.getOffHandStack();
        if (offHand.isOf(Items.FILLED_MAP)) {
            MapIdComponent mapId = offHand.get(DataComponentTypes.MAP_ID);
            if (mapId != null) return mapId.id();
        }

        return null;
    }
}