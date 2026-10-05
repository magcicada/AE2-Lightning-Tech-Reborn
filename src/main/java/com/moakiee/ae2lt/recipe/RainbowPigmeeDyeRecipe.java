package com.moakiee.ae2lt.recipe;

import com.google.gson.JsonObject;
import com.moakiee.ae2lt.registry.ModFumos;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** The visible dye layouts also recolor four identical items, without mirroring or shifting. */
public final class RainbowPigmeeDyeRecipe extends ShapedRecipe {
    private RainbowPigmeeColoring coloring = RainbowPigmeeColoring.EMPTY;

    private RainbowPigmeeDyeRecipe(ShapedRecipe recipe) {
        super(recipe.getId(), recipe.getGroup(), recipe.category(), recipe.getWidth(), recipe.getHeight(),
                recipe.getIngredients(), recipe.getResultItem(null), recipe.showNotification());
    }

    void setColoring(RainbowPigmeeColoring coloring) {
        this.coloring = coloring;
    }

    @Override
    public boolean matches(CraftingContainer input, net.minecraft.world.level.Level level) {
        return matchesDyeBases(input) || !recolorResult(input, level.registryAccess()).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer input, RegistryAccess registries) {
        return matchesDyeBases(input) ? super.assemble(input, registries) : recolorResult(input, registries);
    }

    private boolean matchesDyeBases(CraftingContainer input) {
        if (input.getWidth() != 3 || input.getHeight() != 3 || getWidth() != 3 || getHeight() != 3) return false;
        for (int slot = 0; slot < 9; slot++) if (!getIngredients().get(slot).test(input.getItem(slot))) return false;
        return true;
    }

    private ItemStack recolorResult(CraftingContainer input, RegistryAccess registries) {
        if (input.getWidth() != 3 || input.getHeight() != 3 || getWidth() != 3 || getHeight() != 3
                || !input.getItem(4).is(ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get())
                || !(getResultItem(null).getItem() instanceof DyeItem dye)) {
            return ItemStack.EMPTY;
        }
        ItemStack target = ItemStack.EMPTY;
        int count = 0;
        for (int slot = 0; slot < 9; slot++) {
            if (slot == 4) {
                continue;
            }
            var stack = input.getItem(slot);
            if (getIngredients().get(slot).isEmpty()) {
                if (!stack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (stack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                if (target.isEmpty()) {
                    target = stack;
                } else if (!ItemStack.isSameItemSameTags(target, stack)) {
                    return ItemStack.EMPTY;
                }
                count++;
            }
        }
        return count == 4 ? coloring.color(target, dye.getDyeColor(), count, registries) : ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.RAINBOW_PIGMEE_DYE_SERIALIZER.get();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer input) {
        // The input item's container/data moves into the result, so return only the catalyst.
        var remaining = NonNullList.withSize(input.getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < input.getContainerSize(); slot++) {
            var stack = input.getItem(slot);
            if (stack.is(ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get())) {
                remaining.set(slot, stack.copyWithCount(1));
            }
        }
        return remaining;
    }

    public static final class Serializer implements RecipeSerializer<RainbowPigmeeDyeRecipe> {
        private static final ShapedRecipe.Serializer DELEGATE = new ShapedRecipe.Serializer();

        @Override
        public RainbowPigmeeDyeRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new RainbowPigmeeDyeRecipe(DELEGATE.fromJson(id, json));
        }

        @Override
        public RainbowPigmeeDyeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return new RainbowPigmeeDyeRecipe(DELEGATE.fromNetwork(id, buffer));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, RainbowPigmeeDyeRecipe recipe) {
            DELEGATE.toNetwork(buffer, recipe);
        }
    }
}
