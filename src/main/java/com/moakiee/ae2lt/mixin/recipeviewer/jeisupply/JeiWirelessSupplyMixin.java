package com.moakiee.ae2lt.mixin.recipeviewer.jeisupply;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moakiee.ae2lt.client.compat.JeiWirelessSupplyClient;
import com.moakiee.ae2lt.client.compat.JeiSupplyTransferContext;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.RecipeTransferResult;
import mezz.jei.common.transfer.RecipeTransferService;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserves the selected handler and JEI transfer lifecycle during asynchronous refill. */
@Mixin(value = RecipeTransferService.class, remap = false)
public abstract class JeiWirelessSupplyMixin {
    @WrapOperation(method = "transferRecipe(Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;Lmezz/jei/api/gui/IRecipeLayoutDrawable;Lnet/minecraft/world/entity/player/Player;ZZ)Ljava/util/Optional;",
            remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lmezz/jei/api/recipe/transfer/IRecipeTransferHandler;transferRecipe(Lmezz/jei/api/recipe/transfer/IRecipeTransferContext;Z)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;"), require = 1)
    private IRecipeTransferError ae2lt$supply(IRecipeTransferHandler<AbstractContainerMenu, Object> handler,
                                              IRecipeTransferContext<Object, AbstractContainerMenu> context,
                                              boolean doTransfer, Operation<IRecipeTransferError> original) {
        var completion = new JeiSupplyTransferContext.Completion(context);
        var nativeTransfer = new JeiWirelessSupplyClient.NativeTransfer() {
            @Override
            public IRecipeTransferError transfer(boolean maximum, boolean take) {
                var forwarded = new JeiSupplyTransferContext(context, maximum, take, completion);
                var error = original.call(handler, forwarded, take);
                if (take && error != null && !error.getType().allowsTransfer) {
                    completion.complete(RecipeTransferResult.REJECTED);
                }
                // Context-aware handlers report completion themselves. Legacy handlers cannot.
                if (take && (error == null || error.getType().allowsTransfer)
                        && JeiSupplyTransferContext.usesLegacyHandler(handler)) {
                    completion.complete(RecipeTransferResult.SUCCESS);
                }
                return error;
            }
            @Override
            public void reject() {
                if (doTransfer) completion.complete(RecipeTransferResult.REJECTED);
            }
        };
        try {
            var error = JeiWirelessSupplyClient.transfer(handler, context.getContainer(), context.getRecipe(),
                    context.getRecipeSlots(), context.getPlayer(), context.isMaxTransfer(), doTransfer, nativeTransfer);
            if (doTransfer && error != null && !error.getType().allowsTransfer
                    && !JeiWirelessSupplyClient.isPending(nativeTransfer)) nativeTransfer.reject();
            return error;
        } catch (RuntimeException error) {
            nativeTransfer.reject();
            throw error;
        }
    }
}
