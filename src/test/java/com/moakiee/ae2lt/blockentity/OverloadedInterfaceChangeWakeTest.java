package com.moakiee.ae2lt.blockentity;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class OverloadedInterfaceChangeWakeTest {
    @Test void dueAndNextTickEntriesDoNotNeedDuplicateWakeups() {
        for (long now : new long[] {0, 1, 100, Long.MAX_VALUE - 1}) {
            assertFalse(OverloadedInterfaceBlockEntity.importChangeNeedsWake(now, now));
            assertFalse(OverloadedInterfaceBlockEntity.importChangeNeedsWake(now + 1, now));
            if (now > 0) assertFalse(OverloadedInterfaceBlockEntity.importChangeNeedsWake(now - 1, now));
        }
    }
    @Test void coldIdleEntriesStillWakeBeforeTheWatchdogDeadline() {
        for (long now : new long[] {0, 1, 100, 1_000_000}) {
            for (int delay = 2; delay <= 20; delay++) {
                assertTrue(OverloadedInterfaceBlockEntity.importChangeNeedsWake(now + delay, now));
            }
        }
    }
    @Test void maximumGameTimeDoesNotOverflowIntoAFalseWakeup() {
        assertFalse(OverloadedInterfaceBlockEntity.importChangeNeedsWake(Long.MAX_VALUE, Long.MAX_VALUE));
        assertFalse(OverloadedInterfaceBlockEntity.importChangeNeedsWake(0, Long.MAX_VALUE));
        assertFalse(OverloadedInterfaceBlockEntity.importChangeNeedsWake(Long.MAX_VALUE, Long.MAX_VALUE - 1));
        assertTrue(OverloadedInterfaceBlockEntity.importChangeNeedsWake(Long.MAX_VALUE, Long.MAX_VALUE - 2));
    }
}
