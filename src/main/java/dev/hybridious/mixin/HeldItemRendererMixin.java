package dev.hybridious.mixin;

import dev.hybridious.modules.MapFilterModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HeldItemRenderer.class, priority = 1100)
public class HeldItemRendererMixin {

    @Inject(
            method = "renderFirstPersonMap",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void onRenderFirstPersonMap(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                        int swingProgress, ItemStack map, CallbackInfo ci) {
        try {
            MapFilterModule module = Modules.get().get(MapFilterModule.class);

            if (module != null && module.isActive()) {
                MapIdComponent mapId = map.get(DataComponentTypes.MAP_ID);

                if (mapId != null) {
                    int id = mapId.id();

                    if (!module.shouldRenderMap(id)) {
                        ci.cancel();
                    }
                }
            }
        } catch (Exception e) {
            // Fail-safe: allow rendering on error
        }
    }
}
