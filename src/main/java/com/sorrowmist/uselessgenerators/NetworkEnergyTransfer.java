package com.sorrowmist.uselessgenerators;

import java.util.function.DoubleUnaryOperator;

/** Bridges integral FE to a network API that returns the rejected remainder. */
final class NetworkEnergyTransfer {
    private NetworkEnergyTransfer() {}
    static long transfer(GeneratorEnergy energy, double unitsPerFe, DoubleUnaryOperator inject) {
        if (!Double.isFinite(unitsPerFe) || unitsPerFe <= 0) return 0;
        // AE's double API cannot account for tiny accepted amounts in a 9.2E offer.
        // Keep its offer exactly representable; AppFlux receives the remaining long budget.
        long available = energy.extract(1L << 40, true);
        if (available <= 0) return 0;
        double offered = available * unitsPerFe;
        if (!Double.isFinite(offered) || offered <= 0) return 0;
        double rejected = inject.applyAsDouble(offered);
        double accepted = Math.max(0, Math.min(offered, offered - rejected));
        // A fractional FE must never become free network power.
        long consumed = Math.min(available, (long) Math.ceil(accepted / unitsPerFe));
        return energy.extract(consumed, false);
    }
}
