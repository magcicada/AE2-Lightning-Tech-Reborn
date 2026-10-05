package com.moakiee.ae2lt.client.compat;

import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferContext;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.RecipeTransferResult;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Version-specific forwarding adapter for JEI 15.62's internal transfer hook.
 * IRecipeTransferContext is NonExtendable: keep this adapter confined to the pinned compatibility
 * implementation, and re-audit it when upgrading JEI. It preserves the original id and completion.
 */
public final class JeiSupplyTransferContext implements IRecipeTransferContext<Object, AbstractContainerMenu> {
    private final IRecipeTransferContext<Object, AbstractContainerMenu> delegate;
    private final boolean maximum;
    private final boolean actualTransfer;
    private final Completion completion;

    public JeiSupplyTransferContext(IRecipeTransferContext<Object, AbstractContainerMenu> delegate,
                                    boolean maximum, boolean actualTransfer, Completion completion) {
        this.delegate = delegate;
        this.maximum = maximum;
        this.actualTransfer = actualTransfer;
        this.completion = completion;
    }
    public static final class Completion {
        private final IRecipeTransferContext<?, ?> delegate;
        private final AtomicBoolean completed = new AtomicBoolean();
        public Completion(IRecipeTransferContext<?, ?> delegate) { this.delegate = delegate; }
        public void complete(RecipeTransferResult result) {
            if (completed.compareAndSet(false, true)) delegate.completeRecipeTransfer(result);
        }
    }
    public static boolean usesLegacyHandler(IRecipeTransferHandler<?, ?> handler) {
        try {
            return handler.getClass().getMethod("transferRecipe", IRecipeTransferContext.class, boolean.class)
                    .getDeclaringClass() == IRecipeTransferHandler.class;
        } catch (NoSuchMethodException error) {
            throw new IllegalStateException("JEI transfer handler lacks the 15.62 context API", error);
        }
    }
    @Override public int getTransferId() { return delegate.getTransferId(); }
    @Override public void completeRecipeTransfer(RecipeTransferResult result) {
        if (actualTransfer) completion.complete(result);
    }
    @Override public Object getRecipe() { return delegate.getRecipe(); }
    @Override public RecipeType<Object> getRecipeType() { return delegate.getRecipeType(); }
    @Override public AbstractContainerMenu getContainer() { return delegate.getContainer(); }
    @Override public AbstractContainerScreen<AbstractContainerMenu> getScreen() { return delegate.getScreen(); }
    @Override public IRecipeSlotsView getRecipeSlots() { return delegate.getRecipeSlots(); }
    @Override public Player getPlayer() { return delegate.getPlayer(); }
    @Override public boolean isMaxTransfer() { return maximum; }
}
