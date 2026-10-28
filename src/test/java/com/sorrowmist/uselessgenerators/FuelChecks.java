package com.sorrowmist.uselessgenerators;

final class FuelChecks {
    private static void check(boolean valid) { if (!valid) throw new AssertionError("Fuel accounting failed"); }
    static void run() {
        for (FuelKind kind : FuelKind.values()) {
            FuelBurn burn = new FuelBurn();
            long produced = 0;
            int used = 0;
            for (int mb = 0; mb < 1000; mb++) {
                FuelBurn.Result result = burn.burn(kind.perMb(), kind.perMb(), 1000 - used);
                produced = Math.addExact(produced, result.generated()); used += result.consumedMb();
            }
            check(used == 1000 && produced == kind.perBucket && burn.remainder() == 0);
            check(burn.burn(50, 0, 0).generated() == 0);
        }
        FuelBurn useful = new FuelBurn();
        FuelBurn.Result first = useful.burn(50, FuelKind.USEFUL.perMb(), 1000);
        check(first.consumedMb() == 1 && first.generated() == 50);
        FuelBurn loaded = new FuelBurn(); loaded.restore(useful.remainder());
        FuelBurn.Result next = loaded.burn(50, 0, 0);
        check(next.consumedMb() == 0 && next.generated() == 50);
        check(loaded.remainder() + 100 == FuelKind.USEFUL.perMb());
        check(loaded.burn(0, FuelKind.USEFUL.perMb(), 16000).consumedMb() == 0);
        FuelBurn poor = new FuelBurn();
        var limited = poor.burn(50_000_000_000L, FuelKind.USELESS_1.perMb(), 16000);
        check(limited.generated() == 160_000 && limited.consumedMb() == 16000);
        for (GeneratorTier tier : GeneratorTier.values()) {
            check(FuelKind.USEFUL.allowedIn(tier));
            check(FuelKind.LAVA.allowedIn(tier) == (tier.ordinal() <= 2));
            for (FuelKind kind : FuelKind.values()) {
                if (tier == GeneratorTier.T1) check(kind.allowedIn(tier) == (kind == FuelKind.USEFUL));
            }
            GeneratorEnergy empty = new GeneratorEnergy(tier);
            empty.tick(0); check(empty.stored() == 0);
            empty.tick(Long.MAX_VALUE); check(empty.stored() == tier.perTick());
        }
        System.out.println("Fuel checks passed: exact FE/B, 9.2E without overflow, restrictions, empty tank, partial mB, save/reload and backpressure.");
    }
}
