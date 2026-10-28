package com.sorrowmist.uselessgenerators;

import java.util.function.IntUnaryOperator;

/** Bound work even when a receiver accepts every int packet from an exascale source. */
final class ForgeEnergyTransfer {
    static final int MAX_PACKETS_PER_FACE = 32;
    private ForgeEnergyTransfer() {}
    static long transfer(GeneratorEnergy energy, IntUnaryOperator receive) {
        long total = 0;
        for (int packet = 0; packet < MAX_PACKETS_PER_FACE; packet++) {
            int offered = (int) energy.extract(Integer.MAX_VALUE, true);
            if (offered <= 0) break;
            int accepted = Math.max(0, Math.min(offered, receive.applyAsInt(offered)));
            total += energy.extract(accepted, false);
            if (accepted < offered) break;
        }
        return total;
    }
}
