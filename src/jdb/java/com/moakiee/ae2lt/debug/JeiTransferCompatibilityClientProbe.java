package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.client.compat.JeiRecipeTransferMetadata;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** Loads the real JEI target and exercises the injected simulated-click and success guards. */
@EventBusSubscriber(modid = "ae2lt", value = Dist.CLIENT)
public final class JeiTransferCompatibilityClientProbe {
    private static int ticks;
    private static boolean done;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done
                || !Boolean.getBoolean("ae2lt.jeiTransferCompatibilityProbe")) return;
        if (++ticks < 100) return;
        done = true;
        try {
            verify();
            Files.writeString(Path.of("jei-transfer-result.txt"), "PASS\n");
            Minecraft.getInstance().stop();
        } catch (Throwable failure) {
            failure.printStackTrace();
            try { Files.writeString(Path.of("jei-transfer-result.txt"), "FAIL: " + failure); }
            catch (Exception ignored) { }
            Minecraft.getInstance().stop();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void verify() throws Exception {
        Class<?> button = Class.forName("mezz.jei.gui.recipes.RecipeTransferButton");
        // Allocation avoids coupling this probe to JEI's version-dependent constructor.
        var unsafeClass = Class.forName("sun.misc.Unsafe");
        var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object instance = unsafeClass.getMethod("allocateInstance", Class.class)
                .invoke(unsafeField.get(null), button);
        Method click = Arrays.stream(button.getDeclaredMethods())
                .filter(m -> m.getName().equals("onMouseClicked")).findFirst().orElseThrow();
        click.setAccessible(true);
        Class<?> input = click.getParameterTypes()[0];
        Class<? extends Enum> inputType = (Class<? extends Enum>) Class.forName(
                input.getPackageName() + ".InputType");
        Object simulated = input.getConstructor(InputConstants.Key.class, double.class, double.class,
                int.class, inputType).newInstance(InputConstants.UNKNOWN, 0.0, 0.0, 0,
                Enum.valueOf(inputType, "SIMULATE"));

        var metadata = JeiRecipeTransferMetadata.class.getDeclaredField("CURRENT");
        metadata.setAccessible(true);
        var current = (ThreadLocal<JeiRecipeTransferMetadata.Snapshot>) metadata.get(null);
        current.set(new JeiRecipeTransferMetadata.Snapshot(null, "sentinel", List.of()));
        if (!Boolean.TRUE.equals(click.invoke(instance, simulated))) {
            throw new AssertionError("Simulated click changed JEI's return value");
        }
        if (current.get() != null) throw new AssertionError("Simulated click retained transfer metadata");

        Method success = Arrays.stream(button.getDeclaredMethods())
                .filter(m -> m.getName().contains("ae2lt$keepRecipePageForDirectUpload")
                        && Arrays.equals(m.getParameterTypes(), new Class<?>[] {Runnable.class}))
                .findFirst().orElseThrow();
        success.setAccessible(true);
        var called = new AtomicBoolean();
        success.invoke(instance, (Runnable) () -> called.set(true));
        if (!called.get()) throw new AssertionError("Success callback was suppressed outside RecipesGui");
    }
}
