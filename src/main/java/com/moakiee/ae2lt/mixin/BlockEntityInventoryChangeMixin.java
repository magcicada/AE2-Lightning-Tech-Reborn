package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.blockentity.OverloadedInterfaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class BlockEntityInventoryChangeMixin {
    // This is a latency hint; normal polling remains the fallback if it cannot be injected.
    @Inject(method = "setChanged()V", at = @At("TAIL"), require = 0, expect = 0)
    private void ae2lt$wakeWirelessImport(CallbackInfo callback) {
        OverloadedInterfaceBlockEntity.onTargetInventoryChanged((BlockEntity) (Object) this);
    }
}
