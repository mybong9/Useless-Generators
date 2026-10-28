package com.sorrowmist.uselessgenerators;

/** Preserves the unused energy of a consumed millibucket, even across saves. */
public final class FuelBurn {
    private long remainder;
    public record Result(long generated, int consumedMb) {}
    public Result burn(long requested, long perMb, int availableMb) {
        if (requested <= 0) return new Result(0, 0);
        int consumed = 0;
        if (remainder < requested && perMb > 0 && availableMb > 0) {
            long needed = requested - remainder;
            consumed = (int) Math.min(availableMb, 1 + (needed - 1) / perMb);
            remainder = Math.addExact(remainder, Math.multiplyExact((long) consumed, perMb));
        }
        long generated = Math.min(requested, remainder);
        remainder -= generated;
        return new Result(generated, consumed);
    }
    public long remainder() { return remainder; }
    public void restore(long value) { remainder = Math.max(0, Math.min(value, FuelKind.USEFUL.perMb())); }
}
