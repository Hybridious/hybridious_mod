package dev.hybridious.mixin;

import dev.hybridious.modules.MapFilterModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.MapRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.map.MapState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MapRenderer.class, priority = 1100)
public class MapRendererMixin {

    @Inject(
            method = "draw(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/component/type/MapIdComponent;Lnet/minecraft/item/map/MapState;ZI)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void onMapDraw(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                           MapIdComponent mapIdComponent, MapState state, boolean hidePlayerIcons,
                           int light, CallbackInfo ci) {
        try {
            MapFilterModule module = Modules.get().get(MapFilterModule.class);

            if (module != null && module.isActive() && mapIdComponent != null) {
                int id = mapIdComponent.id();
                System.out.println("[MapFilter] [MapRenderer] Checking map ID: " + id);

                if (!module.shouldRenderMap(id)) {
                    System.out.println("[MapFilter] [MapRenderer] *** BLOCKING map ID: " + id + " (item frames + held maps) ***");
                    ci.cancel();
                } else {
                    System.out.println("[MapFilter] [MapRenderer] Allowing map ID: " + id);
                }
            }
        } catch (Exception e) {
            System.err.println("[MapFilter] [MapRenderer] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}