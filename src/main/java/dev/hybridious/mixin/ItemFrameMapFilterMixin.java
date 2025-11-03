package dev.hybridious.mixin;

import dev.hybridious.modules.MapFilterModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemFrameEntity.class)
public class ItemFrameMapFilterMixin {

    @Inject(method = "getHeldItemStack", at = @At("RETURN"), cancellable = true)
    private void filterMapInFrame(CallbackInfoReturnable<ItemStack> cir) {
        MapFilterModule module = Modules.get().get(MapFilterModule.class);
        if (module == null || !module.isActive()) return;

        ItemStack stack = cir.getReturnValue();
        if (!stack.isOf(Items.FILLED_MAP)) return;

        MapIdComponent mapId = stack.get(DataComponentTypes.MAP_ID);
        if (mapId == null) return;

        int id = mapId.id();

        if (!module.shouldRenderMap(id)) {
            System.out.println("[MapFilter] [ItemFrame] BLOCKING map " + id + " in item frame");
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }
}