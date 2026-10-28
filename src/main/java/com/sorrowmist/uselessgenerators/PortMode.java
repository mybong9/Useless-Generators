package com.sorrowmist.uselessgenerators;

public enum PortMode {
    DISABLED(false, false), INPUT(true, false), OUTPUT(false, true), BOTH(true, true);
    public final boolean input;
    public final boolean output;
    PortMode(boolean input, boolean output) { this.input = input; this.output = output; }
    public PortMode next() { return values()[(ordinal() + 1) % values().length]; }
    public static PortMode decode(int value) { return value >= 0 && value < values().length ? values()[value] : DISABLED; }
    public String translation() { return "gui.useless_generators.mode." + name().toLowerCase(java.util.Locale.ROOT); }
}
