package dev.hybridious;

import com.mojang.logging.LogUtils;
import dev.hybridious.modules.*;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.commands.Commands;
import org.slf4j.Logger;

public class Hybridious extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Hybridious");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Hybridious Addon for Minecraft 1.21.4");
        Modules.get().add(new DeathExplore());
        Modules.get().add(new automoss());
        Modules.get().add(new B36());
        Modules.get().add(new SethBoat());
        Modules.get().add(new MinecartDetector());
        Modules.get().add(new AutoGF());
        Modules.get().add(new BannerFinder());
        Modules.get().add(new LawnMower());
        Modules.get().add(new SoundOnSneak());
        
        // Commands
        Commands.add(new BannerBlacklist());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "dev.hybridious";
    }

}
