package com.moakiee.ae2lt.debug;

import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ae2lt_wireless_binding")
@PrefixGameTestTemplate(false)
public final class TianshuWirelessBindingGameTests {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static void passed(String message) { System.out.println("TIANSHU_BINDING_PASS " + message); }
    private static ServerPlayer player(ServerLevel level, String name) {
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
        player.getInventory().clearContent();
        return player;
    }

    @GameTest(templateNamespace = "ae2lt_main_fixes", template = "empty")
    public static void wirelessAccessPointBinding(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = player(level, "TianshuBinding");
        for (var item : List.of(ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get())) {
            var terminal = new ItemStack(item);
            terminal.getOrCreateTag().putDouble("internalCurrentPower", 12345.0);
            terminal.setHoverName(Component.literal("Binding preserves NBT"));
            // Bind and then rebind through AE2's actual GUI slots, without prewriting the target.
            for (int x = 1; x <= 2; x++) {
                var pos = helper.absolutePos(new BlockPos(x, 1, 1));
                level.setBlockAndUpdate(pos, appeng.core.definitions.AEBlocks.WIRELESS_ACCESS_POINT
                        .block().defaultBlockState());
                var accessPoint = (appeng.blockentity.networking.WirelessAccessPointBlockEntity)
                        level.getBlockEntity(pos);
                var menu = new appeng.menu.implementations.WirelessAccessPointMenu(
                        7, player.getInventory(), accessPoint);
                player.containerMenu = menu;
                try {
                    var input = menu.getSlots(appeng.menu.SlotSemantics.MACHINE_INPUT).get(0);
                    var output = menu.getSlots(appeng.menu.SlotSemantics.MACHINE_OUTPUT).get(0);
                    require(!input.mayPlace(new ItemStack(Items.STONE)), "binding slot rejects unrelated items");
                    require(input.mayPlace(terminal), "wireless access point rejects " + item);
                    var expected = terminal.copy();
                    appeng.items.tools.powered.WirelessTerminalItem.LINKABLE_HANDLER.link(expected,
                            net.minecraft.core.GlobalPos.of(level.dimension(), pos));
                    menu.setCarried(terminal);
                    menu.clicked(input.index, 0, ClickType.PICKUP, player);
                    require(menu.getCarried().isEmpty() && !input.hasItem(),
                            "binding consumes exactly the carried terminal");
                    require(output.getItem().getCount() == 1
                                    && ItemStack.isSameItemSameTags(expected, output.getItem()),
                            "binding writes this access point and preserves all other terminal components");
                    menu.clicked(output.index, 0, ClickType.PICKUP, player);
                    require(!output.hasItem() && menu.getCarried().getCount() == 1
                                    && ItemStack.isSameItemSameTags(expected, menu.getCarried()),
                            "bound terminal can be taken out exactly once");
                    terminal = menu.getCarried();
                    menu.setCarried(ItemStack.EMPTY);
                } finally {
                    menu.removed(player);
                    player.containerMenu = player.inventoryMenu;
                }
            }
        }
        passed("native access point binds and rebinds the wireless pattern terminal without losing components");
        helper.succeed();
    }

}
