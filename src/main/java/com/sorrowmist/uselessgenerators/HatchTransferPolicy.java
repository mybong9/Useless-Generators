package com.sorrowmist.uselessgenerators;

/** Bounded scheduling and saturating counts, independent of Minecraft. */
public final class HatchTransferPolicy {
    public static final int CONFIG_SLOTS = 16, OUTPUT_SLOTS = 32, OPERATIONS = 4;
    public static final long ITEM_CAPACITY = 4096, FLUID_CAPACITY = 1_000_000_000;
    /** Per input mark, in items or mB. Fits Forge's signed int capability amounts. */
    public static final long MAX_TARGET = 2_100_000_000L;
    public static long target(long requested) { return Math.max(1, Math.min(MAX_TARGET, requested)); }
    private int misses;
    public int nextDelay(boolean moved) { if (moved) { misses = 0; return 5; } misses = Math.min(4, misses + 1); return 5 << misses; }
    public void wake() { misses = 0; }
    public static long accepted(long reported, long offered) { return Math.max(0, Math.min(offered, reported)); }
    public static long room(long capacity, long stored) { return Math.max(0, capacity - Math.max(0, Math.min(capacity, stored))); }
    public HatchTransferPolicy() {}
}
