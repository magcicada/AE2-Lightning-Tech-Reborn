package com.moakiee.ae2lt.recipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.ShulkerBoxColoring;

/** Recolors existing items; it never invents a colored variant for an unrelated item. */
public final class RainbowPigmeeColoring {
    private static final DyeColor[] COLORS = DyeColor.values();
    // Match light_blue/light_gray before their shorter suffixes.
    private static final DyeColor[] NAME_ORDER = Arrays.stream(COLORS)
            .sorted(Comparator.comparingInt((DyeColor color) -> color.getName().length()).reversed())
            .toArray(DyeColor[]::new);
    private static final Map<Item, Optional<Variant>> COLORED_VARIANTS = new ConcurrentHashMap<>();
    public static final RainbowPigmeeColoring EMPTY = new RainbowPigmeeColoring(List.of());
    private final List<CraftingRecipe> recipes;
    private volatile Map<Item, Variant> uncolored;

    private RainbowPigmeeColoring(List<CraftingRecipe> recipes) {
        this.recipes = recipes;
    }

    /** Each reload/network sync gives its own recipe objects a fresh, shared coloring context. */
    public static void bindRecipes(RecipeManager manager) {
        var recipes = manager.getAllRecipesFor(RecipeType.CRAFTING);
        var coloring = new RainbowPigmeeColoring(recipes);
        for (var recipe : recipes) {
            if (recipe instanceof RainbowPigmeeColorCycleRecipe cycle) {
                cycle.setColoring(coloring);
            } else if (recipe instanceof RainbowPigmeeDyeRecipe dye) {
                dye.setColoring(coloring);
            }
        }
    }

    public ItemStack next(ItemStack input) {
        return next(input, RegistryAccess.EMPTY);
    }

    // RecipeManager in 1.20 does not retain registry access; crafting calls supply it instead.
    public ItemStack next(ItemStack input, RegistryAccess registries) {
        if (input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (input.getItem() instanceof DyeableLeatherItem dyeable) {
            return color(input, dyeable.hasCustomColor(input)
                    ? nextColor(nearestColor(dyeable.getColor(input))) : DyeColor.WHITE, 1, registries);
        }
        if (input.is(Items.SHIELD)) {
            var tag = input.getTagElement("BlockEntityTag");
            var current = tag != null && tag.contains("Base", 99) ? DyeColor.byId(tag.getInt("Base")) : null;
            return color(input, current == null ? DyeColor.WHITE : nextColor(current), 1, registries);
        }
        return variant(input.getItem(), registries)
                .map(variant -> copyAs(input, variant.items()[variant.color() == null
                        ? DyeColor.WHITE.getId() : nextColor(variant.color()).getId()], 1))
                .orElse(ItemStack.EMPTY);
    }

    public ItemStack color(ItemStack input, DyeColor color, int count, RegistryAccess registries) {
        if (input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result;
        if (input.getItem() instanceof DyeableLeatherItem dyeable) {
            result = input.copyWithCount(count);
            dyeable.setColor(result, rgb(color));
        } else if (input.is(Items.SHIELD)) {
            result = input.copyWithCount(count);
            result.getOrCreateTagElement("BlockEntityTag").putInt("Base", color.getId());
        } else {
            result = variant(input.getItem(), registries)
                    .map(variant -> copyAs(input, variant.items()[color.getId()], count))
                    .orElse(ItemStack.EMPTY);
        }
        // A single crafting result cannot safely hold four non-stackable containers or tools.
        return !result.isEmpty() && count <= result.getMaxStackSize() ? result : ItemStack.EMPTY;
    }

    // Save/load also preserves Forge capability data when changing the item identity.
    private static ItemStack copyAs(ItemStack input, Item item, int count) {
        var data = input.save(new CompoundTag());
        data.putString("id", BuiltInRegistries.ITEM.getKey(item).toString());
        data.putByte("Count", (byte) count);
        return ItemStack.of(data);
    }

    public static int rgb(DyeColor color) {
        var channels = color.getTextureDiffuseColors();
        return Math.round(channels[0] * 255) << 16 | Math.round(channels[1] * 255) << 8
                | Math.round(channels[2] * 255);
    }

    private static DyeColor nextColor(DyeColor color) {
        return DyeColor.byId((color.getId() + 1) % COLORS.length);
    }

    private static DyeColor nearestColor(int rgb) {
        DyeColor nearest = DyeColor.WHITE;
        int distance = Integer.MAX_VALUE;
        for (var color : COLORS) {
            int candidate = rgb(color);
            int red = ((rgb >> 16) & 255) - ((candidate >> 16) & 255);
            int green = ((rgb >> 8) & 255) - ((candidate >> 8) & 255);
            int blue = (rgb & 255) - (candidate & 255);
            int squared = red * red + green * green + blue * blue;
            if (squared < distance) {
                nearest = color;
                distance = squared;
            }
        }
        return nearest;
    }

    private Optional<Variant> variant(Item item, RegistryAccess registries) {
        var colored = coloredVariant(item);
        return colored.isPresent() ? colored : Optional.ofNullable(uncoloredVariants(registries).get(item));
    }

    private static Optional<Variant> coloredVariant(Item item) {
        return COLORED_VARIANTS.computeIfAbsent(item, RainbowPigmeeColoring::findColoredVariant);
    }

    private static Optional<Variant> findColoredVariant(Item item) {
        var id = BuiltInRegistries.ITEM.getKey(item);
        String path = id.getPath();
        for (var color : NAME_ORDER) {
            String name = color.getName();
            for (int start = path.indexOf(name); start >= 0; start = path.indexOf(name, start + 1)) {
                int end = start + name.length();
                if ((start == 0 || path.charAt(start - 1) == '_' || path.charAt(start - 1) == '/')
                        && (end == path.length() || path.charAt(end) == '_' || path.charAt(end) == '/')) {
                    var family = family(id.getNamespace(), path.substring(0, start), path.substring(end), color);
                    if (family.isPresent()) {
                        return family;
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Map<Item, Variant> uncoloredVariants(RegistryAccess registries) {
        var result = uncolored;
        if (result == null) {
            synchronized (this) {
                result = uncolored;
                if (result == null) {
                    // Resolve lazily: client recipes may arrive before their ingredient tags.
                    uncolored = result = discoverUncolored(registries);
                }
            }
        }
        return result;
    }

    private Map<Item, Variant> discoverUncolored(RegistryAccess registries) {
        var result = new HashMap<Item, Variant>();
        var ambiguous = new HashSet<Item>();
        for (var recipe : recipes) {
            // This vanilla special recipe has no advertised ingredients/result. Its only base
            // is the uncolored shulker; recognize it only while the actual recipe is installed.
            if (recipe instanceof ShulkerBoxColoring) {
                coloredVariant(Items.WHITE_SHULKER_BOX).ifPresent(v ->
                        addUncolored(result, ambiguous, Items.SHULKER_BOX, v));
                continue;
            }
            if (!(recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)
                    || recipe.isSpecial() || recipe instanceof RainbowPigmeeDyeRecipe) {
                continue;
            }
            var output = recipe.getResultItem(registries);
            var colored = coloredVariant(output.getItem());
            if (output.isEmpty() || colored.isEmpty() || output.hasTag()) {
                continue;
            }
            DyeColor dye = colored.get().color();
            var materials = new ArrayList<Ingredient>();
            int dyes = 0;
            boolean valid = true;
            for (var ingredient : recipe.getIngredients()) {
                if (ingredient.isEmpty()) {
                    continue;
                }
                if (!ingredient.isSimple() || ingredient.getItems().length == 0) {
                    valid = false;
                    break;
                }
                if (Arrays.stream(ingredient.getItems()).allMatch(stack -> isDye(stack, dye))) {
                    dyes++;
                } else {
                    materials.add(ingredient);
                }
            }
            if (!valid || dyes != 1 || materials.isEmpty() || output.getCount() != materials.size()) {
                continue;
            }
            // An ingredient tag may accept several items, but every occupied material slot
            // must accept the SAME item. n input items must yield exactly n colored items.
            for (var candidate : materials.get(0).getItems()) {
                if (coloredVariant(candidate.getItem()).isEmpty()
                        && materials.stream().allMatch(ingredient -> ingredient.test(candidate))) {
                    addUncolored(result, ambiguous, candidate.getItem(), colored.get());
                }
            }
        }
        ambiguous.forEach(result::remove);
        return Map.copyOf(result);
    }

    private static boolean isDye(ItemStack stack, DyeColor color) {
        return stack.getItem() instanceof DyeItem dye ? dye.getDyeColor() == color : stack.is(color.getTag());
    }

    private static void addUncolored(Map<Item, Variant> result, Set<Item> ambiguous, Item item, Variant family) {
        var existing = result.putIfAbsent(item, new Variant(family.items(), null));
        if (existing != null && !Arrays.equals(existing.items(), family.items())) {
            ambiguous.add(item);
        }
    }

    private static Optional<Variant> family(String namespace, String prefix, String suffix, DyeColor current) {
        var items = new Item[COLORS.length];
        for (var color : COLORS) {
            var id = new ResourceLocation(namespace, prefix + color.getName() + suffix);
            var item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
            if (item == Items.AIR) {
                return Optional.empty();
            }
            items[color.getId()] = item;
        }
        return Optional.of(new Variant(items, current));
    }

    private record Variant(Item[] items, DyeColor color) {
    }
}
