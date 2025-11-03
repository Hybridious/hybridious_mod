package dev.hybridious.mixin;

import dev.hybridious.modules.MapFilterModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.MapRenderer;
import net.minecraft.component.type.MapIdComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.render.MapRenderer$MapTexture")
public class MapTextureMixin {

    @Shadow
    private MapIdComponent id;

    @Inject(
            method = "draw",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void onTextureDraw(CallbackInfo ci) {
        System.out.println("[MapFilter] [MIXIN] MapTextureMixin.draw() called!");

        try {
            MapFilterModule module = Modules.get().get(MapFilterModule.class);

            if (module == null) {
                System.out.println("[MapFilter] [MIXIN] Module is null!");
                return;
            }

            if (!module.isActive()) {
                System.out.println("[MapFilter] [MIXIN] Module is not active");
                return;
            }

            if (id == null) {
                System.out.println("[MapFilter] [MIXIN] Map ID is null!");
                return;
            }

            int mapId = id.id();
            System.out.println("[MapFilter] [MIXIN] Checking map ID: " + mapId);

            if (!module.shouldRenderMap(mapId)) {
                System.out.println("[MapFilter] [MIXIN] *** BLOCKING map ID: " + mapId + " ***");
                ci.cancel();
            } else {
                System.out.println("[MapFilter] [MIXIN] Allowing map ID: " + mapId);
            }
        } catch (Exception e) {
            System.err.println("[MapFilter] [MIXIN] Error checking map: " + e.getMessage());
            e.printStackTrace();
        }
    }
}