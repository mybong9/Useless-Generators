package com.sorrowmist.uselessgenerators;

public enum GeneratorTier {
    K1("1k", 1_000L), K16("16k", 16_000L), K256("256k", 256_000L),
    M1("1m", 1_000_000L), M16("16m", 16_000_000L), M256("256m", 256_000_000L),
    G1("1g", 1_000_000_000L), G16("16g", 16_000_000_000L),
    G256("256g", 256_000_000_000L), T1("1t", 1_000_000_000_000L);

    public final String id;
    public final long perSecond;
    GeneratorTier(String suffix, long perSecond) {
        this.id = "generator_" + suffix;
        this.perSecond = perSecond;
    }
    public long perTick() { return perSecond / 20; }
    public long capacity() { return perSecond * 20; }
}
