package com.sorrowmist.uselessgenerators;

/** Shared deterministic geometry for the client and offline previews. */
public final class UniverseEffects {
    public static final int STARS = 224, MIST = 48, PLANETS = 3;
    public static final int COUNT = STARS + MIST + PLANETS, PATHS = 16;
    public static final double CENTER_X = 40, CENTER_Y = 16.5, CENTER_Z = 40;
    public static final double SKY_RADIUS = 12.4, HORIZONTAL_BOUND = 33, VERTICAL_BOUND = 13;
    private static final int[] PLANET_COLORS = {0x80b8d9, 0xb7a0d6, 0xd7ba83};
    public record Mote(double x, double y, double z, double size, int color, double alpha) {}
    public record Point(double x, double y, double z) {}
    private UniverseEffects() {}
    private static double random(int index, int salt) {
        long n = (index * 73856093L + salt * 19349663L) ^ 0x5DEECE66DL;
        n = (n ^ n >>> 13) * 0x5DEECE66DL;
        return (n & 0xffffff) / 16777216.0;
    }
    public static boolean isPlanet(int index) { return index >= STARS + MIST; }
    public static boolean isMist(int index) { return index >= STARS && !isPlanet(index); }
    public static Mote sample(int index, double time, boolean working) {
        double phase = working ? time * .0022 : 0;
        if (isPlanet(index)) {
            int planet = index - STARS - MIST;
            Point p = orbit(planet, phase * (.65 + planet * .18) + planet * 2.3, phase);
            return new Mote(p.x, p.y, p.z, .32 + planet * .09, PLANET_COLORS[planet], working ? .92 : .48);
        }
        double r = 1.8 + (isMist(index) ? 7.1 : 7.8) * Math.sqrt(random(index, 1));
        double angle = r * .72 + (index % 3) * Math.PI * 2 / 3 + phase + (random(index, 2) - .5) * .46;
        double y = Math.sin(angle) * r * .25 + (random(index, 3) - .5) * .5;
        double size = isMist(index) ? .45 + random(index, 4) * .55 : .065 + random(index, 4) * .05;
        int color = isMist(index) ? (index % 2 == 0 ? 0x5277b0 : 0x8a69ab)
                : index % 5 == 0 ? 0xb4a6ce : index % 3 == 0 ? 0x81b8ce : 0xd7e0e8;
        double alpha = isMist(index) ? .13 : .56 + .12 * Math.sin(time * .02 + index * 1.3);
        return new Mote(Math.cos(angle) * r, y, Math.sin(angle) * r, size, color, alpha * (working ? 1 : .4));
    }
    public static Point orbit(int ring, double angle, double phase) {
        return tiltedCircle(9.0 + ring, ring == 0 ? .35 : ring == 1 ? 1.03 : -.82,
                ring * 1.3 + phase * .04, angle);
    }
    private static Point tiltedCircle(double radius, double tilt, double turn, double angle) {
        double x = Math.cos(angle) * radius, z = Math.sin(angle) * radius * Math.cos(tilt);
        return new Point(x * Math.cos(turn) - z * Math.sin(turn), Math.sin(angle) * radius * Math.sin(tilt),
                x * Math.sin(turn) + z * Math.cos(turn));
    }
    public static int segments(int path) { return path < 3 ? 96 : path < 6 ? 40 : path < 10 ? 48 : path < 12 ? 128 : 48; }
    public static double pathWidth(int path) { return path < 3 ? .032 : path < 6 ? .075 : path < 10 ? .045 : path < 12 ? .055 : .045; }
    public static int pathColor(int path) { return path == 1 || path == 4 || path == 11 ? 0xcabb9e : path >= 12 ? 0x939ed2 : 0x83bccb; }
    public static double pathAlpha(int path, double fraction, double time) {
        if (path < 3) return .32;
        if (path < 6) return .08 + .5 * fraction;
        if (path < 10) return .28;
        double wave = .5 + .5 * Math.cos(fraction * Math.PI * 2 * (path < 12 ? 3 : 1) - time * .018);
        return .08 + .35 * Math.pow(wave, 5);
    }
    public static Point pathPoint(int path, double fraction, double time) {
        double phase = time * .0022;
        if (path < 3) return orbit(path, fraction * Math.PI * 2, phase);
        if (path < 6) {
            double angle = (path - 3) * Math.PI * 2 / 3 + phase * .6 + fraction * .85;
            double x = Math.cos(angle) * 24.5, z = Math.sin(angle) * 24.5;
            // Float above the tallest terrace step instead of putting stair-shaped kinks in the arc.
            return new Point(x, 15 + x * .13 + 1.85 - CENTER_Y, z);
        }
        if (path < 10) {
            Point p = beacon(path - 5);
            double angle = fraction * Math.PI * 2, radius = 2.15 + .1 * Math.sin(phase);
            return new Point(p.x + Math.cos(angle) * radius, p.y - .7, p.z + Math.sin(angle) * radius);
        }
        if (path < 12) return tiltedCircle(path == 10 ? 17.4 : 19.4, path == 10 ? .42 : -.5,
                path * .7 + phase * (path == 10 ? .04 : -.035), fraction * Math.PI * 2);
        Point station = beacon(path - 11);
        double radius = 30 - 12.6 * fraction, turn = Math.sin(fraction * Math.PI) * .10;
        double angle = Math.atan2(station.z, station.x) + turn;
        return new Point(Math.cos(angle) * radius, station.y * (1 - fraction) + Math.sin(fraction * Math.PI) * 8.0,
                Math.sin(angle) * radius);
    }
    public static Point beacon(int index) {
        return index == 0 ? new Point(0, 0, 0) : new Point(index == 1 ? -30 : index == 2 ? 30 : 0,
                13.1 - CENTER_Y, index == 3 ? -30 : index == 4 ? 30 : 0);
    }
}
