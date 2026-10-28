package com.sorrowmist.uselessgenerators;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The blueprint is independent of Minecraft so geometry and rotations can be checked. */
public final class ReactorLayout {
    public static final int WIDTH = 80, HEIGHT = 32, CHUNKS = 5;
    public static final long RATE = 9_200_000_000_000_000_000L;
    public static final int CHECKS_PER_TICK = 256;
    public static final int MAX_HATCHES = 32;
    public enum Part { CASING, COIL, CORE, CONTROLLER, HULL, GLASS, AIR, FLUID_INPUT, ITEM_INPUT, OUTPUT, ENERGY, CONNECTOR }
    public record Cell(int x, int y, int z, Part part) {}
    public record Horizontal(int x, int z) {}
    public static final Cell CONTROLLER = new Cell(39, 1, 0, Part.CONTROLLER);
    public static final List<Cell> CELLS = create();
    public static final List<Cell> HATCH_SLOTS = CELLS.stream().filter(c -> c.part == Part.CASING || isHatch(c.part)).toList();
    public static final int MATERIAL_COUNT = (int) CELLS.stream().filter(cell -> cell.part != Part.AIR).count();
    private static final Map<Integer, Cell> INDEX = index();

    private ReactorLayout() {}
    public static boolean isHatch(Part part) { return part.ordinal() >= Part.FLUID_INPUT.ordinal(); }
    public static boolean acceptsHatch(Part position, Part hatch) { return isHatch(hatch) && (position == Part.CASING || position == hatch); }
    private static void put(Map<Integer, Cell> cells, int x, int y, int z, Part part) {
        cells.put((y * WIDTH + z) * WIDTH + x, new Cell(x, y, z, part));
    }
    private static List<Cell> create() {
        Map<Integer, Cell> cells = new LinkedHashMap<>();
        for (int z = 0; z < WIDTH; z++) for (int x = 0; x < WIDTH; x++) {
            double dx = x - 39.5, dz = z - 39.5;
            double radius = Math.hypot(dx, dz), octagon = octagon(dx, dz);
            // A low continuous outer terrace, four paths and a stepped central pedestal.
            for (int y = 0; y <= 3; y++) {
                if (octagon <= 39.75 && octagon >= 35.75 && y <= 1)
                    put(cells, x, y, z, octagon > 38.4 || octagon < 36.7 ? Part.CASING : Part.HULL);
                double pedestal = 20.5 - y;
                if (octagon <= pedestal) put(cells, x, y, z, octagon > pedestal - 1.4 ? Part.CASING : Part.HULL);
                if (y == 1 && radius >= 18 && radius <= 37 && (Math.abs(dx) < 2.5 || Math.abs(dz) < 2.5))
                    put(cells, x, y, z, Part.CASING);
            }
            // A single spherical vessel. White meridians hold dark alloy caps and four windows.
            for (int y = 1; y < HEIGHT; y++) {
                double dy = y - 16, distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (distance >= 13.6 && distance <= 15.55) {
                    boolean meridian = Math.abs(dx) < 2.4 || Math.abs(dz) < 2.4;
                    boolean window = Math.abs(dy) < 9.0 && !meridian;
                    Part part = meridian ? Part.CASING : window ? Part.GLASS : Part.HULL;
                    if (Math.abs(dy) < 1.0 && !window) part = Part.COIL;
                    if (!window || distance >= 14.7) put(cells, x, y, z, part);
                }
                // Thick tilted orbit; it reads as one unified ring, not 25 cages.
                if (radius >= 23.2 && radius <= 26.2 && Math.abs(y - (15 + dx * .13)) <= .7)
                    put(cells, x, y, z, radius < 24 || radius > 25.3 ? Part.CASING : Part.HULL);
                // Only the central universe volume must remain empty. Other voids permit decoration.
                if (distance <= 10.0) put(cells, x, y, z, Part.AIR);
            }
            // Four compact octagonal stabilizers, with a crystal embedded in each upper cap.
            for (int station = 0; station < 4; station++) {
                double sx = station == 0 ? -30 : station == 1 ? 30 : 0;
                double sz = station == 2 ? -30 : station == 3 ? 30 : 0;
                double local = octagon(dx - sx, dz - sz);
                for (int y = 2; y <= 11; y++) {
                    double width = y <= 3 ? 5.5 : y >= 9 ? 4.1 : 3.1;
                    if (local <= width) {
                        Part part = y == 3 || y == 9 ? Part.CASING : y == 7 || y == 8 ? Part.COIL : Part.HULL;
                        if (y >= 10 && local < 1.6) part = Part.CORE;
                        put(cells, x, y, z, part);
                    }
                }
            }
        }
        // The three unused control stations must be casing, preventing rotated controllers
        // from sharing the exact same array. Overlapping arrays also have an ownership check.
        for (int rotation = 0; rotation < 4; rotation++) {
            Horizontal p = rotate(CONTROLLER.x, CONTROLLER.z, rotation);
            put(cells, p.x, CONTROLLER.y, p.z, rotation == 0 ? Part.CONTROLLER : Part.CASING);
        }
        // Five service ports replace existing terrace blocks; they add no extra volume.
        Part[] ports = {Part.FLUID_INPUT, Part.ITEM_INPUT, Part.OUTPUT, Part.ENERGY, Part.CONNECTOR};
        for (int i = 0; i < ports.length; i++) put(cells, 35 + i * 2, 1, 2, ports[i]);
        return List.copyOf(new ArrayList<>(cells.values()));
    }
    private static double octagon(double x, double z) {
        return Math.max(Math.abs(x), Math.abs(z)) + .41421356237 * Math.min(Math.abs(x), Math.abs(z));
    }
    private static Map<Integer, Cell> index() {
        Map<Integer, Cell> result = new java.util.HashMap<>();
        for (Cell cell : CELLS) result.put((cell.y * WIDTH + cell.z) * WIDTH + cell.x, cell);
        return Map.copyOf(result);
    }
    public static Cell at(int x, int y, int z) {
        if (x < 0 || z < 0 || y < 0 || x >= WIDTH || z >= WIDTH || y >= HEIGHT) return null;
        return INDEX.get((y * WIDTH + z) * WIDTH + x);
    }
    /** Clockwise rotation in an 80x80 bounding square: north, east, south, west. */
    public static Horizontal rotate(int x, int z, int rotation) {
        return switch (Math.floorMod(rotation, 4)) {
            case 1 -> new Horizontal(WIDTH - 1 - z, x);
            case 2 -> new Horizontal(WIDTH - 1 - x, WIDTH - 1 - z);
            case 3 -> new Horizontal(z, WIDTH - 1 - x);
            default -> new Horizontal(x, z);
        };
    }
    public static boolean aligned(int x, int z) { return Math.floorMod(x, 16) == 0 && Math.floorMod(z, 16) == 0; }
    public static boolean overlaps(int ax, int ay, int az, int bx, int by, int bz) {
        return ax < bx + WIDTH && bx < ax + WIDTH && az < bz + WIDTH && bz < az + WIDTH
                && ay < by + HEIGHT && by < ay + HEIGHT;
    }
}
