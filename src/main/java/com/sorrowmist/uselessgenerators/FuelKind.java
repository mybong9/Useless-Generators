package com.sorrowmist.uselessgenerators;

/** Energy values are per BUCKET (1000 mB), not FE/t or FE/mB. */
public enum FuelKind {
    USELESS_1("useless_fuel_1", 10_000L), USELESS_2("useless_fuel_2", 20_000L),
    USELESS_3("useless_fuel_3", 100_000L), USELESS_4("useless_fuel_4", 10_000_000L),
    USELESS_5("useless_fuel_5", 100_000_000L), USELESS_6("useless_fuel_6", 1_000_000_000L),
    USELESS_7("useless_fuel_7", 100_000_000_000L), USELESS_8("useless_fuel_8", 10_000_000_000_000L),
    USELESS_9("useless_fuel_9", 100_000_000_000_000L),
    USEFUL("useful_fuel", 9_200_000_000_000_000_000L), LAVA("lava", 1_000_000L);
    public final String id;
    public final long perBucket;
    FuelKind(String id, long perBucket) { this.id = id; this.perBucket = perBucket; }
    public long perMb() { return perBucket / 1000; }
    public boolean allowedIn(GeneratorTier tier) {
        if (tier == GeneratorTier.T1) return this == USEFUL;
        return this != LAVA || tier.ordinal() <= GeneratorTier.K256.ordinal();
    }
}
