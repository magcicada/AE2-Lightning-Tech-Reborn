package com.moakiee.ae2lt.mixin;

import com.moakiee.ae2lt.recipe.RainbowPigmeeColoring;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rebuild inferred uncolored families after server reloads and client recipe synchronization. */
@Mixin(RecipeManager.class)
public abstract class RainbowPigmeeRecipeManagerMixin {
    @Inject(method = {
            "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            "replaceRecipes"
    }, at = @At("RETURN"))
    private void ae2lt$bindRainbowColoring(CallbackInfo ci) {
        RainbowPigmeeColoring.bindRecipes((RecipeManager) (Object) this);
    }
}
