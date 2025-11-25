package dev.hybridious.modules;
import dev.hybridious.Hybridious;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class TabGuiScale extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> tabGuiScale = sgGeneral.add(new IntSetting.Builder()
            .name("tab-gui-scale")
            .description("The GUI scale to use when the tab/player list is open.")
            .defaultValue(2)
            .min(1)
            .max(4)
            .sliderMin(1)
            .sliderMax(4)
            .build()
    );

    private int originalGuiScale = -1;
    private boolean tabListOpen = false;

    public TabGuiScale() {
        super(Hybridious.CATEGORY, "tab-gui-scale", "Changes GUI scale when opening the tab/player list.");
    }

    @Override
    public void onActivate() {
        originalGuiScale = mc.options.getGuiScale().getValue();
        tabListOpen = false;
    }

    @Override
    public void onDeactivate() {
        // Restore original GUI scale when module is disabled
        if (originalGuiScale != -1 && mc.options.getGuiScale().getValue() != originalGuiScale) {
            mc.options.getGuiScale().setValue(originalGuiScale);
            mc.onResolutionChanged();
        }
        originalGuiScale = -1;
        tabListOpen = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Check if tab key is pressed (player list is shown)
        boolean isTabPressed = mc.options.playerListKey.isPressed();

        if (isTabPressed && !tabListOpen) {
            // Tab just pressed - change to custom GUI scale
            if (originalGuiScale == -1) {
                originalGuiScale = mc.options.getGuiScale().getValue();
            }

            if (mc.options.getGuiScale().getValue() != tabGuiScale.get()) {
                mc.options.getGuiScale().setValue(tabGuiScale.get());
                mc.onResolutionChanged();
            }
            tabListOpen = true;
        } else if (!isTabPressed && tabListOpen) {
            // Tab just released - restore original GUI scale
            if (originalGuiScale != -1 && mc.options.getGuiScale().getValue() != originalGuiScale) {
                mc.options.getGuiScale().setValue(originalGuiScale);
                mc.onResolutionChanged();
            }
            tabListOpen = false;
        }
    }

    @Override
    public String getInfoString() {
        return tabListOpen ? "Active" : null;
    }
}
