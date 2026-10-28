package com.sorrowmist.uselessgenerators;

/** Shared menu, click and recipe-browser geometry. */
public final class MeHatchLayout {
    public static final int WIDTH = 396, HEIGHT = 302, CELL = 22;
    public static final int INVENTORY_X = 118, INVENTORY_Y = 214, HOTBAR_Y = 272;
    private MeHatchLayout() {}
    public static int cellX(int slot) { return 20 + (slot % 8) * 26; }
    public static int markY(int slot) { return 60 + (slot / 8) * 26; }
    public static int stockY(int slot, boolean input) { return (input ? 142 : 60) + (slot / 8) * 26; }
}
