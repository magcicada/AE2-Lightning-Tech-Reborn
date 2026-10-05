package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.moakiee.ae2lt.logic.energy.PowerCostUtil;
import net.minecraft.network.chat.Component;
import appeng.api.stacks.AEItemKey;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OverloadedInterfaceExportPlanTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void unchangedPlanReusesIndexedStatesAndChangedOrderPreservesKeyState() {
        var stone = AEItemKey.of(Items.STONE);
        var dirt = AEItemKey.of(Items.DIRT);
        var entries = List.of(new OverloadedInterfaceBlockEntity.ExportConfigEntry(stone, 64),
                new OverloadedInterfaceBlockEntity.ExportConfigEntry(dirt, 64));
        var state = new OverloadedInterfaceBlockEntity.ConnectionState();
        var plan = state.exportPlan(stone.getType(), entries);
        plan.transfers()[0].untilTick = 91;
        for (int i = 0; i < 10_000; i++) assertSame(plan, state.exportPlan(stone.getType(), entries));
        var reordered = List.of(new OverloadedInterfaceBlockEntity.ExportConfigEntry(dirt, 32),
                new OverloadedInterfaceBlockEntity.ExportConfigEntry(stone, 16));
        var replacement = state.exportPlan(stone.getType(), reordered);
        assertNotSame(plan, replacement);
        assertSame(plan.transfers()[0], replacement.transfers()[1]);
        assertEquals(91, replacement.transfers()[1].untilTick);
        state.resetWirelessIo(OverloadedInterfaceBlockEntity.IOSpeedMode.FAST);
        assertNotSame(replacement.transfers()[1], state.exportPlan(stone.getType(), reordered).transfers()[1]);
    }

    @Test void duplicateKeySlotsShareStateAndEmptyPlanDoesNotRetainEntries() {
        var key = AEItemKey.of(Items.STONE);
        var state = new OverloadedInterfaceBlockEntity.ConnectionState();
        var plan = state.exportPlan(key.getType(), List.of(
                new OverloadedInterfaceBlockEntity.ExportConfigEntry(key, 64),
                new OverloadedInterfaceBlockEntity.ExportConfigEntry(key, 32)));
        assertSame(plan.transfers()[0], plan.transfers()[1]);
        var empty = state.exportPlan(key.getType(), List.of());
        assertEquals(0, empty.transfers().length);
        assertEquals(1, state.exportPlans.size());
    }

    @Test void slotHintUsesLiveAcceptanceAndRelearnsAfterHandlerReplacement() {
        var key = AEItemKey.of(Items.STONE);
        var transfer = new OverloadedInterfaceBlockEntity.ExportTransferState();
        var handler = new ItemStackHandler(27) {
            @Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 26; }
        };
        assertEquals(64, OverloadedInterfaceBlockEntity.insertIntoItemHandler(transfer, handler, key, 64, true));
        assertEquals(-1, transfer.preferredSlot, "simulation must not mutate the hint");
        assertEquals(64, OverloadedInterfaceBlockEntity.insertIntoItemHandler(transfer, handler, key, 64, false));
        assertEquals(26, transfer.preferredSlot);
        assertEquals(0, OverloadedInterfaceBlockEntity.insertIntoItemHandler(transfer, handler, key, 64, false));
        var replacement = new ItemStackHandler(1);
        assertEquals(64, OverloadedInterfaceBlockEntity.insertIntoItemHandler(transfer, replacement, key, 64, false));
        assertEquals(0, transfer.preferredSlot);
        assertEquals(128, handler.getStackInSlot(26).getCount() + replacement.getStackInSlot(0).getCount());
    }

    @Test void rejectionAtPreferredSlotFallsBackAndSimulationDoesNotMoveContents() {
        var key = AEItemKey.of(Items.STONE);
        var transfer = new OverloadedInterfaceBlockEntity.ExportTransferState();
        transfer.preferredSlot = 0;
        var handler = new ItemStackHandler(3);
        handler.setStackInSlot(0, new ItemStack(Items.DIRT, 64));
        assertEquals(64, OverloadedInterfaceBlockEntity.insertIntoItemHandler(transfer, handler, key, 64, true));
        assertTrue(handler.getStackInSlot(1).isEmpty());
        assertEquals(0, transfer.preferredSlot);
        assertEquals(64, OverloadedInterfaceBlockEntity.insertIntoItemHandler(transfer, handler, key, 64, false));
        assertEquals(1, transfer.preferredSlot);
        assertEquals(64, handler.getStackInSlot(0).getCount());
        assertEquals(64, handler.getStackInSlot(1).getCount());
    }

    private static final class Supply implements MEStorage {
        long items = 100;
        int simulations, extractions;
        Runnable afterExtract = () -> {};
        @Override public Component getDescription() { return Component.literal("supply"); }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            if (mode == Actionable.SIMULATE) { simulations++; return Math.min(items, amount); }
            extractions++;
            long got = Math.min(items, amount);
            items -= got;
            afterExtract.run();
            return got;
        }
    }

    private static IGrid grid(double[] power) {
        var energy = (IEnergyService) Proxy.newProxyInstance(IEnergyService.class.getClassLoader(),
                new Class<?>[]{IEnergyService.class}, (p, m, args) -> {
                    if (m.getName().equals("getIdlePowerUsage")) return 4.0;
                    if (m.getName().equals("extractAEPower")) {
                        double got = Math.min(power[0], (Double) args[0]);
                        if (args[1] == Actionable.MODULATE) power[0] -= got;
                        return got;
                    }
                    throw new AssertionError(m);
                });
        return (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[]{IGrid.class},
                (p, m, args) -> {
                    if (m.getName().equals("getEnergyService") || m.getName().equals("getService")) return energy;
                    throw new AssertionError(m);
                });
    }

    @Test void boundedExportUsesActualSupplyReceiptWithoutRedundantSuccessSimulation() {
        var key = AEItemKey.of(Items.STONE);
        var supply = new Supply(); supply.items = 23;
        var handler = new ItemStackHandler(1);
        double[] power = {100};
        var grid = grid(power);
        var transfer = new OverloadedInterfaceBlockEntity.ExportTransferState();
        assertEquals(23, OverloadedInterfaceBlockEntity.exportBoundedItemKey(transfer,
                new OverloadedInterfaceBlockEntity.ExportConfigEntry(key, 64), handler, supply, IActionSource.empty(),
                new PowerCostUtil.EnergyAccess(), () -> grid, (k, a) -> fail("unexpected overflow"),
                OverloadedInterfaceBlockEntity.IOSpeedMode.FAST, 10, -1));
        assertEquals(0, supply.items);
        assertEquals(23, handler.getStackInSlot(0).getCount());
        assertEquals(0, supply.simulations);
        assertEquals(1, supply.extractions);
        assertEquals(94, power[0]);
    }

    @Test void targetMutationAfterExtractionBuffersOnlyTheUndeliveredReceipt() {
        var key = AEItemKey.of(Items.STONE);
        var supply = new Supply();
        var handler = new ItemStackHandler(1);
        supply.afterExtract = () -> handler.setStackInSlot(0, new ItemStack(Items.DIRT, 64));
        long[] overflow = {0}; double[] power = {100}; var grid = grid(power);
        assertEquals(0, OverloadedInterfaceBlockEntity.exportBoundedItemKey(
                new OverloadedInterfaceBlockEntity.ExportTransferState(),
                new OverloadedInterfaceBlockEntity.ExportConfigEntry(key, 64), handler, supply, IActionSource.empty(),
                new PowerCostUtil.EnergyAccess(), () -> grid, (k, a) -> { assertEquals(key, k); overflow[0] += a; },
                OverloadedInterfaceBlockEntity.IOSpeedMode.FAST, 10, -1));
        assertEquals(100, supply.items + overflow[0]);
        assertEquals(64, overflow[0]);
        assertEquals(100, power[0], "rejected output must not consume transfer power");
    }

    @Test void noPowerOrOfflineNeverExtractsAndPartialPowerPreservesIdleReserve() {
        var key = AEItemKey.of(Items.STONE);
        var entry = new OverloadedInterfaceBlockEntity.ExportConfigEntry(key, 64);
        var supply = new Supply(); var handler = new ItemStackHandler(1);
        double[] power = {4}; var grid = grid(power);
        var access = new PowerCostUtil.EnergyAccess();
        for (IGrid candidate : new IGrid[]{null, grid}) {
            assertEquals(0, OverloadedInterfaceBlockEntity.exportBoundedItemKey(
                    new OverloadedInterfaceBlockEntity.ExportTransferState(), entry, handler, supply, IActionSource.empty(),
                    access, () -> candidate, (k, a) -> fail(), OverloadedInterfaceBlockEntity.IOSpeedMode.FAST, 10, -1));
        }
        assertEquals(0, supply.extractions);
        power[0] = 12;
        assertEquals(32, OverloadedInterfaceBlockEntity.exportBoundedItemKey(
                new OverloadedInterfaceBlockEntity.ExportTransferState(), entry, handler, supply, IActionSource.empty(),
                access, () -> grid, (k, a) -> fail(), OverloadedInterfaceBlockEntity.IOSpeedMode.FAST, 11, -1));
        assertEquals(4, power[0]);
        assertEquals(100, supply.items + handler.getStackInSlot(0).getCount());
    }

    @Test void fullTargetAndEmptySupplyKeepShortagePollingWithoutMutatingEitherOwner() {
        var key = AEItemKey.of(Items.STONE);
        var supply = new Supply(); supply.items = 0;
        var handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        double[] power = {100}; var grid = grid(power);
        var transfer = new OverloadedInterfaceBlockEntity.ExportTransferState();
        for (int now = 10; now < 20; now++) {
            assertEquals(0, OverloadedInterfaceBlockEntity.exportBoundedItemKey(transfer,
                    new OverloadedInterfaceBlockEntity.ExportConfigEntry(key, 64), handler, supply, IActionSource.empty(),
                    new PowerCostUtil.EnergyAccess(), () -> grid, (k, a) -> fail(),
                    OverloadedInterfaceBlockEntity.IOSpeedMode.FAST, now, -1));
        }
        assertEquals(10, supply.simulations);
        assertEquals(0, supply.extractions);
        assertEquals(64, handler.getStackInSlot(0).getCount());
    }

    @Test void vanillaBarrelProjectionMatchesForgeSimulationForComponentsAndSlotLimits() {
        var barrel = new BarrelBlockEntity(BlockPos.ZERO, Blocks.BARREL.defaultBlockState());
        var handler = new InvWrapper(barrel);
        var transfer = new OverloadedInterfaceBlockEntity.ExportTransferState();
        var plain = AEItemKey.of(Items.STONE);
        var named = new ItemStack(Items.STONE);
        named.setHoverName(Component.literal("variant"));
        var tagged = AEItemKey.of(named);
        var random = new java.util.SplittableRandom(20261004L);
        for (int round = 0; round < 2000; round++) {
            for (int slot = 0; slot < 27; slot++) {
                var key = slot % 3 == 0 ? tagged : slot % 3 == 1 ? plain : AEItemKey.of(Items.DIRT);
                barrel.setItem(slot, random.nextBoolean() ? key.toStack(random.nextInt(1, 65)) : ItemStack.EMPTY);
            }
            transfer.preferredSlot = random.nextInt(-1, 29);
            var key = round % 2 == 0 ? plain : tagged;
            int amount = random.nextInt(1, 2049);
            int expected = amount;
            var remainder = key.toStack(amount);
            int preferred = transfer.preferredSlot < 27 ? transfer.preferredSlot : -1;
            if (preferred >= 0) remainder = handler.insertItem(preferred, remainder, true);
            for (int slot = 0; slot < 27 && !remainder.isEmpty(); slot++) {
                if (slot != preferred) remainder = handler.insertItem(slot, remainder, true);
            }
            expected -= remainder.getCount();
            assertEquals(expected, OverloadedInterfaceBlockEntity.insertIntoItemHandler(transfer, handler, key, amount, true));
        }
    }
    @Test void customWrapperOnVanillaBarrelStillHonorsSimulationCallbacks() {
        var barrel = new BarrelBlockEntity(BlockPos.ZERO, Blocks.BARREL.defaultBlockState());
        var calls = new AtomicInteger();
        var handler = new InvWrapper(barrel) {
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                calls.incrementAndGet();
                return stack;
            }
        };
        var transfer = new OverloadedInterfaceBlockEntity.ExportTransferState();
        assertEquals(0, OverloadedInterfaceBlockEntity.insertIntoItemHandler(
                transfer, handler, AEItemKey.of(Items.STONE), 64, true));
        assertEquals(27, calls.get(), "a custom capability must not use vanilla capacity projection");
        assertEquals(-1, transfer.preferredSlot);
        assertTrue(barrel.isEmpty());
    }

    @Test void vanillaProjectionRespectsUnstackableAndSixteenItemLimits() {
        var barrel = new BarrelBlockEntity(BlockPos.ZERO, Blocks.BARREL.defaultBlockState());
        var handler = new InvWrapper(barrel);
        var transfer = new OverloadedInterfaceBlockEntity.ExportTransferState();
        for (var item : List.of(Items.ENDER_PEARL, Items.IRON_SWORD)) {
            barrel.clearContent();
            var key = AEItemKey.of(item);
            int limit = key.getMaxStackSize();
            for (int slot = 0; slot < 26; slot++) barrel.setItem(slot, key.toStack(limit));
            int before = transfer.preferredSlot = 26;
            var remainder = handler.insertItem(26, key.toStack(64), true);
            assertEquals(64 - remainder.getCount(), OverloadedInterfaceBlockEntity.insertIntoItemHandler(
                    transfer, handler, key, 64, true));
            assertEquals(limit, 64 - remainder.getCount());
            assertTrue(barrel.getItem(26).isEmpty());
            assertEquals(before, transfer.preferredSlot);
        }
    }

    @Test void cacheCapacityEvictionCannotSplitDuplicateKeyStatesWithinAPlan() {
        var state = new OverloadedInterfaceBlockEntity.ConnectionState();
        for (int i = 0; i < 127; i++) {
            var stack = new ItemStack(Items.STONE);
            stack.setHoverName(Component.literal("old-" + i));
            var key = AEItemKey.of(stack);
            state.exportPlan(key.getType(), List.of(new OverloadedInterfaceBlockEntity.ExportConfigEntry(key, 64)));
        }
        var shared = AEItemKey.of(Items.DIRT);
        var fresh = AEItemKey.of(Items.COBBLESTONE);
        var entries = List.of(new OverloadedInterfaceBlockEntity.ExportConfigEntry(shared, 64),
                new OverloadedInterfaceBlockEntity.ExportConfigEntry(fresh, 64),
                new OverloadedInterfaceBlockEntity.ExportConfigEntry(shared, 32));
        var plan = state.exportPlan(shared.getType(), entries);
        assertSame(plan.transfers()[0], plan.transfers()[2], "capacity eviction must precede complete plan construction");
        plan.transfers()[0].untilTick = 42;
        var reordered = state.exportPlan(shared.getType(), List.of(entries.get(1), entries.get(0)));
        assertSame(plan.transfers()[0], reordered.transfers()[1]);
        assertEquals(42, reordered.transfers()[1].untilTick);
    }

    @Test void vanillaProjectionTreatsNullAndEmptyTagsExactlyLikeForge() {
        var barrel = new BarrelBlockEntity(BlockPos.ZERO, Blocks.BARREL.defaultBlockState());
        var handler = new InvWrapper(barrel);
        var transfer = new OverloadedInterfaceBlockEntity.ExportTransferState();
        for (boolean emptySourceTag : List.of(false, true)) {
            barrel.clearContent();
            for (int slot = 0; slot < 26; slot++) barrel.setItem(slot, new ItemStack(Items.DIRT, 64));
            var source = new ItemStack(Items.STONE, 1);
            var stored = new ItemStack(Items.STONE, 32);
            if (emptySourceTag) source.setTag(new net.minecraft.nbt.CompoundTag());
            else stored.setTag(new net.minecraft.nbt.CompoundTag());
            barrel.setItem(26, stored);
            var key = AEItemKey.of(source);
            transfer.preferredSlot = 26;
            var remainder = handler.insertItem(26, key.toStack(64), true);
            assertEquals(32, 64 - remainder.getCount());
            assertEquals(64 - remainder.getCount(), OverloadedInterfaceBlockEntity.insertIntoItemHandler(
                    transfer, handler, key, 64, true), "null and empty NBT must follow Forge stack compatibility, not strict AE key equality");
            assertEquals(32, barrel.getItem(26).getCount());
        }
    }

}
