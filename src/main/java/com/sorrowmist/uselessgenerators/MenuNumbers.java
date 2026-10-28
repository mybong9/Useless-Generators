package com.sorrowmist.uselessgenerators;

/** Vanilla menu data packets carry signed 16-bit values, so long values need four words. */
public final class MenuNumbers {
    private MenuNumbers() {}
    public static int word(long value, int part) { return (int) ((value >>> (part * 16)) & 0xffff); }
    public static long join(java.util.function.IntUnaryOperator words, int start) {
        long value = 0;
        for (int i = 0; i < 4; i++) value |= ((long) words.applyAsInt(start + i) & 0xffff) << (16 * i);
        return value;
    }
}
