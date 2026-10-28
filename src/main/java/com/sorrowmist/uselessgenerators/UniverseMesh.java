package com.sorrowmist.uselessgenerators;

/** Cached unit sphere quads, also used by the offline scene exporter. */
public final class UniverseMesh {
    public record Vertex(double x, double y, double z, float u, float v) {}
    public static final Vertex[] SKY = sphere(64, 32), SKY_FAR = sphere(32, 16);
    public static final Vertex[] CORE = sphere(32, 16), PLANET = sphere(16, 8);
    private UniverseMesh() {}
    private static Vertex vertex(int longitude, int latitude, int slices, int stacks) {
        double u = (double) longitude / slices, v = (double) latitude / stacks;
        double phi = v * Math.PI, theta = u * Math.PI * 2, ring = Math.sin(phi);
        return new Vertex(Math.cos(theta) * ring, Math.cos(phi), Math.sin(theta) * ring, (float) u, (float) v);
    }
    private static Vertex[] sphere(int slices, int stacks) {
        Vertex[] mesh = new Vertex[slices * stacks * 4];
        int at = 0;
        for (int y = 0; y < stacks; y++) for (int x = 0; x < slices; x++) {
            mesh[at++] = vertex(x, y, slices, stacks);
            mesh[at++] = vertex(x + 1, y, slices, stacks);
            mesh[at++] = vertex(x + 1, y + 1, slices, stacks);
            mesh[at++] = vertex(x, y + 1, slices, stacks);
        }
        return mesh;
    }
}
