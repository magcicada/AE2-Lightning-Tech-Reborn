package com.moakiee.ae2lt.debug;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.blockentity.qnb.QuantumBridgeBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.cluster.implementations.QuantumCluster;
import com.moakiee.ae2lt.integration.ae2wtlib.TianshuWTMenuHost;
import com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu;
import com.moakiee.ae2lt.registry.ModItems;
import com.mojang.authlib.GameProfile;
import de.mari_023.ae2wtlib.terminal.WTMenuHost;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real transformed hosts and menus. The stale cache reproduces the reported tick boundary. */
@GameTestHolder("ae2lt_quantum")
@PrefixGameTestTemplate(false)
public final class TianshuQuantumBridgeGameTests {
    private static void check(boolean value, String message) {
        if (!value) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }

    private static ServerPlayer player(GameTestHelper helper, String name, Item terminal) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        player.getInventory().clearContent();
        var stack = new ItemStack(terminal);
        stack.getOrCreateTag().putDouble("internalCurrentPower", 1_000_000.0);
        player.getInventory().setItem(0, stack);
        return player;
    }

    private static void set(WTMenuHost host, String name, Object value) {
        try {
            var field = WTMenuHost.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(host, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static Object get(WTMenuHost host, String name) {
        try {
            var field = WTMenuHost.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(host);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void stale(WTMenuHost host) throws Exception {
        // AE2 destroy() clears center, while the terminal still remembers connected=true.
        var cluster = new QuantumCluster(BlockPos.ZERO, new BlockPos(2, 2, 0));
        check(cluster.getCenter() == null, "fixture must have no bridge center");
        set(host, "quantumBridge", cluster);
    }

    private static void disconnected(WTMenuHost host) throws Exception {
        check(host.getActionableNode() == null, "stale quantum bridge must yield no node");
        check(!host.rangeCheck(), "stale connected status must be cleared");
        check(get(host, "quantumBridge") == null, "discard stale bridge so later discovery can reconnect");
    }

    @GameTest(templateNamespace = "ae2lt_quantum", template = "empty", timeoutTicks = 100)
    public static void patternMenuSurvivesStaleBridge(GameTestHelper helper) throws Exception {
        var player = player(helper, "QuantumPattern", ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get());
        var host = new TianshuWTMenuHost(player, 0, player.getInventory().getItem(0), (p, menu) -> {});
        var menu = new TianshuWirelessPatternEncodingTermMenu(10, player.getInventory(), host);
        player.containerMenu = menu;
        try {
            stale(host);
            // In the crash, this calls Tianshu discovery before the native menu refresh.
            menu.broadcastChanges();
            disconnected(host);
        } finally {
            menu.removed(player);
            player.containerMenu = player.inventoryMenu;
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "ae2lt_quantum", template = "empty", timeoutTicks = 100)
    public static void statusAndConnectionRefreshDiscardStaleBridge(GameTestHelper helper) throws Exception {
        var player = player(helper, "QuantumStatus", ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get());
        var host = new TianshuWTMenuHost(player, 0, player.getInventory().getItem(0), (p, menu) -> {});
        stale(host);
        check(!host.rangeCheck(), "status queried before node must also invalidate the dead bridge");
        disconnected(host);
        stale(host);
        host.rangeCheck();
        check(get(host, "quantumBridge") == null, "normal connection refresh must release the cache without a getter");
        host.rangeCheck();
        disconnected(host);
        helper.succeed();
    }

    private record Bridge(BlockPos center, ItemStack singularity, DriveBlockEntity drive) {}

    private static Bridge bridge(GameTestHelper helper, ServerPlayer player) {
        var level = helper.getLevel();
        var center = helper.absolutePos(new BlockPos(3, 2, 2));
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) {
            var block = x == 0 && y == 0 ? AEBlocks.QUANTUM_LINK.block() : AEBlocks.QUANTUM_RING.block();
            level.setBlockAndUpdate(center.offset(x, y, 0), block.defaultBlockState());
        }
        level.setBlockAndUpdate(center.west(2), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(center.west(3), AEBlocks.DRIVE.block().defaultBlockState());
        var cell = AEItems.ITEM_CELL_1K.stack();
        var storage = StorageCells.getCellInventory(cell, null);
        storage.insert(AEItemKey.of(Items.OAK_PLANKS), 256, Actionable.MODULATE, IActionSource.ofPlayer(player));
        storage.persist();
        var drive = (DriveBlockEntity) level.getBlockEntity(center.west(3));
        drive.getInternalInventory().setItemDirect(0, cell);
        var singularity = AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack();
        singularity.getOrCreateTag().putLong("freq", UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE);
        ((QuantumBridgeBlockEntity) level.getBlockEntity(center)).getInternalInventory().setItemDirect(0, singularity.copy());
        return new Bridge(center, singularity, drive);
    }

    private static void equipQuantum(WTMenuHost host, Bridge bridge) {
        check(host.getUpgrades().isInstalled(de.mari_023.ae2wtlib.AE2wtlib.QUANTUM_BRIDGE_CARD),
                "quantum upgrade must be accepted by the real terminal");
        check(host.rangeCheck(), "powered real quantum bridge must connect: " + host.rangeCheck());
        check(host.getActionableNode() != null, "quantum connection must supply a grid node");
    }

    private static void equipQuantumStack(ItemStack stack, Bridge bridge) {
        var upgrades = appeng.api.upgrades.UpgradeInventories.forItem(stack,
                de.mari_023.ae2wtlib.wut.WUTHandler.getUpgradeCardCount());
        check(upgrades.addItems(new ItemStack(de.mari_023.ae2wtlib.AE2wtlib.QUANTUM_BRIDGE_CARD)).isEmpty(),
                "native quantum card installation");
        var inventory = new appeng.util.inv.AppEngInternalInventory(null, 1);
        inventory.setItemDirect(0, bridge.singularity().copy());
        inventory.writeToNBT(stack.getOrCreateTag(), "singularity");
    }

    @GameTest(templateNamespace = "ae2lt_quantum", template = "empty", timeoutTicks = 140)
    public static void realBridgeLossFallsBackToLocalAccessPoint(GameTestHelper helper) {
        if (!ModList.get().isLoaded("ae2wtlib")) {
            helper.succeed();
            return;
        }
        var player = player(helper, "QuantumFallback", ModItems.TIANSHU_WIRELESS_PATTERN_ENCODING_TERMINAL.get());
        var bridge = bridge(helper, player);
        var center = bridge.center();
        // Default orientation exposes only its south (back) side to the energy cell.
        var wap = center.west(2).north();
        helper.getLevel().setBlockAndUpdate(wap, AEBlocks.WIRELESS_ACCESS_POINT.block().defaultBlockState());
        player.setPos(wap.getX() + .5, wap.getY() + 1, wap.getZ() + .5);
        player.getInventory().getItem(0).getOrCreateTag().put("accessPoint", GlobalPos.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, GlobalPos.of(helper.getLevel().dimension(), wap)).result().orElseThrow());
        helper.runAfterDelay(50, () -> {
            equipQuantumStack(player.getInventory().getItem(0), bridge);
            var host = new TianshuWTMenuHost(player, 0, player.getInventory().getItem(0), (p, m) -> {});
            equipQuantum(host, bridge);
            var menu = new TianshuWirelessPatternEncodingTermMenu(13, player.getInventory(), host);
            player.containerMenu = menu;
            helper.getLevel().setBlockAndUpdate(center.east(), Blocks.AIR.defaultBlockState());
            // Let AE2 finish channel recalculation, keeping the terminal's old cache untouched.
            helper.runAfterDelay(30, () -> {
                try {
                    check(host.rangeCheck() && host.getActionableNode() != null,
                            "local fallback: range=" + host.rangeCheck() + " node=" + host.getActionableNode()
                            + " wap=" + ((appeng.blockentity.networking.WirelessAccessPointBlockEntity) helper.getLevel().getBlockEntity(wap)).getMainNode().isActive()
                            + " grid=" + get(host, "targetGrid")
                            + " wapGrid=" + ((appeng.blockentity.networking.WirelessAccessPointBlockEntity) helper.getLevel().getBlockEntity(wap)).getMainNode().getGrid());
                    menu.broadcastChanges();
                    check(get(host, "quantumBridge") == null, "local fallback must not retain dead quantum cache");
                    check(host.getInventory().extract(AEItemKey.of(Items.OAK_PLANKS), 1, Actionable.SIMULATE,
                            IActionSource.ofPlayer(player)) == 1, "local fallback must keep the real ME inventory available");
                } finally {
                    menu.removed(player);
                    player.containerMenu = player.inventoryMenu;
                }
                helper.succeed();
            });
        });
    }
}
