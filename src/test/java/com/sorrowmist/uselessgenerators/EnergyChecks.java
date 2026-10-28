package com.sorrowmist.uselessgenerators;

public final class EnergyChecks {
    private static void check(boolean valid) { if (!valid) throw new AssertionError(); }
    public static void main(String[] args) {
        FuelChecks.run();
        MenuChecks.run();
        ReactorChecks.run();
        HatchChecks.run();
        check(GeneratorTier.values().length == 10);
        for (GeneratorTier tier : GeneratorTier.values()) {
            GeneratorEnergy energy = new GeneratorEnergy(tier);
            long total = 0;
            for (int tick = 0; tick < 20; tick++) {
                energy.tick(tier.perTick());
                check(energy.extract(-1, false) == 0);
                check(energy.extract(Long.MAX_VALUE, true) == tier.perTick());
                long received = 0;
                while (true) {
                    long chunk = energy.extract(Integer.MAX_VALUE, false);
                    if (chunk == 0) break;
                    received += chunk;
                }
                check(received == tier.perTick());
                check(energy.extract(1, false) == 0);
                total += received;
            }
            check(total == tier.perSecond);
            for (int tick = 0; tick < 500; tick++) energy.tick(tier.perTick());
            check(energy.stored() == tier.capacity());
            check(energy.receive(Long.MAX_VALUE, false) == 0);
            energy.restore(Long.MAX_VALUE);
            check(energy.stored() == tier.capacity());
            check(energy.extract(Long.MAX_VALUE, false) == 0);
            energy.restore(-1);
            check(energy.stored() == 0);
        }
        GeneratorEnergy bridge = new GeneratorEnergy(GeneratorTier.K1);
        bridge.tick(50);
        check(NetworkEnergyTransfer.transfer(bridge, 0.5, offered -> offered) == 0);
        check(bridge.stored() == 50);
        check(NetworkEnergyTransfer.transfer(bridge, 0.5, offered -> 10) == 30);
        check(bridge.extract(Long.MAX_VALUE, false) == 20);
        check(NetworkEnergyTransfer.transfer(bridge, 0.5, offered -> 0) == 0);
        bridge.tick(50);
        check(NetworkEnergyTransfer.transfer(bridge, 0.5, offered -> offered - 0.25) == 1);
        check(bridge.stored() == 49);
        for (GeneratorTier tier : GeneratorTier.values()) {
            GeneratorEnergy network = new GeneratorEnergy(tier);
            network.tick(tier.perTick());
            check(NetworkEnergyTransfer.transfer(network, 0.5, offered -> 0) == tier.perTick());
            check(network.stored() == 0);
            network.tick(tier.perTick());
            long available = tier.perTick();
            check(FluxEnergyTransfer.transfer(network, offered -> 0) == 0);
            check(network.stored() == available);
            long partial = Math.max(1, available / 3);
            check(FluxEnergyTransfer.transfer(network, offered -> partial) == partial);
            check(network.stored() == available - partial);
            long ae = NetworkEnergyTransfer.transfer(network, 0.5, offered -> offered / 2);
            long flux = FluxEnergyTransfer.transfer(network, offered -> offered);
            check(partial + ae + flux == available);
            check(network.extract(Long.MAX_VALUE, false) == 0);
            check(FluxEnergyTransfer.transfer(network, offered -> { throw new AssertionError("exhausted budget"); }) == 0);
            // Full buffers must remain intact when the destination cannot accept FE.
            network.restore(tier.capacity());
            network.tick(0);
            check(FluxEnergyTransfer.transfer(network, offered -> 0) == 0);
            check(network.stored() == tier.capacity());
            check(FluxEnergyTransfer.transfer(network, offered -> offered) == tier.perTick());
            check(network.extract(Long.MAX_VALUE, false) == 0);
        }
        // Native Flux plug pulls and active FE/AppFlux pushes compete for the same exascale budget.
        GeneratorEnergy cosmic = new GeneratorEnergy(9_200_000_000_000_000_000L, Long.MAX_VALUE);
        cosmic.tick(9_200_000_000_000_000_000L);
        long before = cosmic.stored();
        check(cosmic.extract(Long.MAX_VALUE, true) == before);
        check(cosmic.stored() == before);
        long nativePull = cosmic.extract(4_000_000_000_000_000_000L, false);
        long ordinaryPush = ForgeEnergyTransfer.transfer(cosmic, offered -> offered);
        long fluxPush = FluxEnergyTransfer.transfer(cosmic, offered -> offered / 2);
        long remainingPush = FluxEnergyTransfer.transfer(cosmic, offered -> offered);
        check(nativePull + ordinaryPush + fluxPush + remainingPush == before);
        check(cosmic.stored() == 0);
        check(cosmic.extract(Long.MAX_VALUE, false) == 0);
        check(FluxEnergyTransfer.transfer(cosmic, offered -> { throw new AssertionError("duplicate output"); }) == 0);
        System.out.println("All ten tiers and exascale mixed Flux/FE/AppFlux output passed: simulation, saturation, shared budgets and conservation.");
    }
}
