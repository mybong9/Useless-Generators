package com.sorrowmist.uselessgenerators;

public enum MeHatchKind {
    FLUID_INPUT("me_fluid_input"), ITEM_INPUT("me_item_input"), OUTPUT("me_output"),
    ENERGY("me_energy"), CONNECTOR("me_connector");
    public final String id;
    MeHatchKind(String id) { this.id = id; }
    public boolean input() { return this == FLUID_INPUT || this == ITEM_INPUT; }
    public boolean energy() { return this == ENERGY || this == OUTPUT; }
}
