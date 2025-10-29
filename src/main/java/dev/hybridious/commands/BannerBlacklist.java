package dev.hybridious.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.hybridious.modules.BannerFinder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class BannerBlacklist extends Command {
    public BannerBlacklist() {
        super("bannerblacklist", "Manages the banner blacklist (prevents alerts).", "bbl");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("add")
                .executes(context -> {
                    BannerFinder module = Modules.get().get(BannerFinder.class);

                    if (module.addCurrentBanner()) {
                        info("Banner added to blacklist. Total: " + module.getBlacklistSize());
                    } else {
                        error("Failed to add banner. Hold a banner in your hand.");
                    }

                    return SINGLE_SUCCESS;
                })
        );

        builder.then(literal("remove")
                .executes(context -> {
                    BannerFinder module = Modules.get().get(BannerFinder.class);

                    if (module.removeCurrentBanner()) {
                        info("Banner removed from blacklist. Total: " + module.getBlacklistSize());
                    } else {
                        error("Failed to remove banner. Hold a banner in your hand or it's not in the blacklist.");
                    }

                    return SINGLE_SUCCESS;
                })
        );

        builder.then(literal("clear")
                .executes(context -> {
                    BannerFinder module = Modules.get().get(BannerFinder.class);
                    module.clearBlacklist();
                    info("Banner blacklist cleared.");
                    return SINGLE_SUCCESS;
                })
        );

        builder.then(literal("count")
                .executes(context -> {
                    BannerFinder module = Modules.get().get(BannerFinder.class);
                    info("Blacklisted banners: " + module.getBlacklistSize());
                    return SINGLE_SUCCESS;
                })
        );
    }
}
