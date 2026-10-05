package com.moakiee.ae2lt.logic.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

class WirelessCadenceSchedulingRegressionTest {
    private final IPatternDetails pattern = new EmptyPattern();

    @Test
    void callerLimitedRefillStopsUsingTheBulkInterval() {
        var cadence = new WirelessBatchCadence<String>();
        cadence.recordSuccess("target", pattern, 0, 2000, false);
        cadence.usesBulkRefill("target", pattern, 2000);
        for (int tick = 5; tick <= 20; tick += 5) {
            cadence.recordSuccess("target", pattern, tick, 512, false,
                    ProviderTarget.BaselineStatus.PREFIX_COMPLETE);
        }
        assertTrue(cadence.usesBulkRefill("target", pattern, 2000));
        // A fair-share or source limit is not evidence of a full bulk refill.
        int delay = cadence.recordSuccess("target", pattern, 25, 128, true);
        assertTrue(delay <= 5, "partial supply retained the bulk wait: " + delay);
        assertFalse(cadence.usesBulkRefill("target", pattern, 2000));
    }

    @Test
    void acceptingTheEntireAllowanceDoesNotProveTheMachineDrainedOnlyThatMuch() {
        var cadence = new WirelessBatchCadence<String>();
        cadence.recordSuccess("target", pattern, 0, 2000, false);
        // The dispatcher allowed only 512 copies. Inferring 2000 / 512 times
        // the elapsed time here creates a self-sustaining slow refill schedule.
        int delay = cadence.recordSuccess("target", pattern, 10, 512, true);
        assertTrue(delay <= 5, "caller-limited success extended the refill interval: " + delay);
    }

    @Test
    void fullMachineDoesNotErasePreviouslyProvenSingleChunkRefills() {
        var cadence = new WirelessBatchCadence<String>();
        for (int tick = 0; tick <= 30; tick += 10) {
            cadence.recordSuccess("target", pattern, tick, 512, false, ProviderTarget.BaselineStatus.PREFIX_COMPLETE);
        }
        assertTrue(cadence.usesSingleChunkRefill("target", pattern));
        cadence.recordFailure("target", pattern, 31);
        assertTrue(cadence.usesSingleChunkRefill("target", pattern));
        cadence.clear();
        assertFalse(cadence.usesSingleChunkRefill("target", pattern));
    }

    @Test
    void knownRejectionDoesNotAlsoTriggerAnUnnecessaryCapacityAudit() {
        var cadence = new WirelessBatchCadence<String>();
        cadence.recordSuccess("target", pattern, 0, 2048, false);
        cadence.recordFailure("target", pattern, 30);
        cadence.recordSuccess("target", pattern, 60, 1024, false);
        assertFalse(cadence.shouldReopenReservoirTail("target", pattern));
    }

    @Test
    void stablePrefixNeedsPhysicalTransactionProofBeforeBulkRefills() {
        var cadence = new WirelessBatchCadence<String>();
        cadence.recordSuccess("target", pattern, 0, 2000, false);
        for (int tick = 5; tick <= 20; tick += 5) {
            cadence.recordSuccess("target", pattern, tick, 512, false,
                    ProviderTarget.BaselineStatus.PREFIX_COMPLETE);
        }
        assertFalse(cadence.usesBulkRefill("target", pattern, 2000));
    }

    @Test
    void provenBulkRefillsSurviveEarlyRejectionAndExpireWhenIdle() {
        var cadence = new WirelessBatchCadence<String>();
        cadence.recordSuccess("target", pattern, 0, 2000, false);
        assertFalse(cadence.usesBulkRefill("target", pattern, 2000));
        int delay = 0;
        for (int tick = 5; tick <= 20; tick += 5) {
            delay = cadence.recordSuccess("target", pattern, tick, 512, false,
                    ProviderTarget.BaselineStatus.PREFIX_COMPLETE);
        }
        assertEquals(20, delay);
        assertTrue(cadence.usesBulkRefill("target", pattern, 2000));
        assertEquals(20, cadence.recordSuccess("target", pattern, 40, 2000, true));
        assertEquals(20, cadence.recordSuccess("target", pattern, 60, 2000, true));
        assertEquals(15, cadence.recordSuccess("target", pattern, 80, 2000, true));
        assertEquals(5, cadence.recordFailure("target", pattern, 95));
        assertTrue(cadence.usesBulkRefill("target", pattern, 2000));
        assertFalse(cadence.usesBulkRefill("target", pattern, 1024));
        assertFalse(cadence.usesBulkRefill("target", pattern, 2000));
        cadence.recordSuccess("target", pattern, 200, 512, true);
        assertFalse(cadence.usesBulkRefill("target", pattern, 2000));
    }

    @Test
    void repeatedBulkRejectionsFallBackToPhysicalRamp() {
        var cadence = new WirelessBatchCadence<String>();
        cadence.recordSuccess("target", pattern, 0, 2000, false);
        assertFalse(cadence.usesBulkRefill("target", pattern, 2000));
        for (int tick = 5; tick <= 20; tick += 5) {
            cadence.recordSuccess("target", pattern, tick, 512, false,
                    ProviderTarget.BaselineStatus.PREFIX_COMPLETE);
        }
        assertTrue(cadence.usesBulkRefill("target", pattern, 2000));

        cadence.recordFailure("target", pattern, 40);
        cadence.recordFailure("target", pattern, 60);
        assertTrue(cadence.usesBulkRefill("target", pattern, 2000));
        cadence.recordFailure("target", pattern, 80);
        assertFalse(cadence.usesBulkRefill("target", pattern, 2000));
        assertFalse(cadence.usesSingleChunkRefill("target", pattern));
    }

    private static final class EmptyPattern implements IPatternDetails {
        public AEItemKey getDefinition() { return null; }
        public IInput[] getInputs() { return new IInput[0]; }
        public GenericStack[] getOutputs() { return new GenericStack[0]; }
        public boolean equals(Object other) { throw new AssertionError("unexpected equality"); }
        public int hashCode() { return 31; }
    }
}
