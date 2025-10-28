package dev.hybridious.modules;


import dev.hybridious.Hybridious;
import meteordevelopment.meteorclient.systems.modules.Module;

public class AutoGF extends Module {
    // Hardcoded message - change this to whatever you want
    private static final String HARDCODED_MESSAGE = ">PLEASE be my gf I'm literally begging you. I'll do ANYTHING. Please please please just give me ONE chance. I'm on my knees rn. PLEASE";;

    public AutoGF() {
        super(Hybridious.CATEGORY, "auto-gf", "A scientifically proven method of getting a gf.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;

        // Send the message immediately when the module is toggled on
        mc.player.networkHandler.sendChatMessage(HARDCODED_MESSAGE);

        // Auto-disable after sending
        toggle();
    }
}
