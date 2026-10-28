package com.sorrowmist.uselessgenerators;

import java.util.function.LongUnaryOperator;

/** AppFlux storage reports accepted FE, unlike AE2's rejected-remainder power API. */
final class FluxEnergyTransfer {
    private FluxEnergyTransfer() {}

    static long transfer(GeneratorEnergy energy, LongUnaryOperator insert) {
        long offered = energy.extract(Long.MAX_VALUE, true);
        if (offered <= 0) return 0;
        long accepted = Math.max(0, Math.min(offered, insert.applyAsLong(offered)));
        return energy.extract(accepted, false);
    }
}
