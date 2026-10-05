package com.moakiee.ae2lt.logic.energy;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigInteger;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class OverloadedIoCostOverflowTest {
    @Test void unlimitedAmountsCannotWrapIntoFreeOrNegativeTransferCosts() {
        var random = new SplittableRandom(20261004L);
        for (int i = 0; i < 20_000; i++) {
            long amount = i == 0 ? Long.MAX_VALUE : Long.MAX_VALUE - random.nextLong(1_000_000);
            long per = i % 3 == 0 ? 4 : i % 3 == 1 ? 500 : random.nextLong(1, Long.MAX_VALUE);
            double expected = BigInteger.valueOf(amount).add(BigInteger.valueOf(per - 1))
                    .divide(BigInteger.valueOf(per)).doubleValue();
            assertEquals(expected, OverloadedIoCost.cost(amount, per));
            assertTrue(OverloadedIoCost.cost(amount, per) > 0);
        }
    }
}
