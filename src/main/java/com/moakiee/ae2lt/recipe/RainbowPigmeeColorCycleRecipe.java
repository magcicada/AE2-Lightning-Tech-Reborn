package com.moakiee.ae2lt.recipe;

import com.moakiee.ae2lt.registry.ModFumos;
import com.moakiee.ae2lt.registry.ModRecipeTypes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** One reusable Rainbow Pigmee advances one item through Minecraft's sixteen dye colors. */
public final class RainbowPigmeeColorCycleRecipe extends CustomRecipe {
    private RainbowPigmeeColoring coloring = RainbowPigmeeColoring.EMPTY;

    public RainbowPigmeeColorCycleRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    void setColoring(RainbowPigmeeColoring coloring) {
        this.coloring = coloring;
    }

    @Override
    public boolean matches(CraftingContainer input, Level level) {
        return !coloring.next(findTarget(input), level.registryAccess()).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingContainer input, RegistryAccess registries) {
        return coloring.next(findTarget(input), registries);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.RAINBOW_PIGMEE_COLOR_CYCLE_SERIALIZER.get();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer input) {
        var remaining = NonNullList.withSize(input.getContainerSize(), ItemStack.EMPTY);
        for (int slot = 0; slot < input.getContainerSize(); slot++) {
            var stack = input.getItem(slot);
            if (stack.is(ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get())) {
                remaining.set(slot, stack.copyWithCount(1));
            }
        }
        return remaining;
    }

    private static ItemStack findTarget(CraftingContainer input) {
        ItemStack target = ItemStack.EMPTY;
        boolean foundPigmee = false;
        for (int slot = 0; slot < input.getContainerSize(); slot++) {
            var stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ModFumos.RAINBOW_PIGMEE_FUMO_ITEM.get())) {
                if (foundPigmee) {
                    return ItemStack.EMPTY;
                }
                foundPigmee = true;
            } else if (target.isEmpty()) {
                target = stack;
            } else {
                return ItemStack.EMPTY;
            }
        }
        return foundPigmee ? target : ItemStack.EMPTY;
    }
}
