package com.sorrowmist.uselessgenerators.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sorrowmist.uselessgenerators.GeneratorBlockEntity;
import com.sorrowmist.uselessgenerators.UniverseEffects;
import com.sorrowmist.uselessgenerators.UniverseMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** One bounded, alpha-blended scene: sky sphere, round stars, planets and exterior paths. */
public final class CosmicHeartRenderer implements BlockEntityRenderer<GeneratorBlockEntity> {
    private static final java.util.WeakHashMap<GeneratorBlockEntity, Motion> MOTION = new java.util.WeakHashMap<>();
    private static final double[][] CIRCLE = new double[17][2];
    static { for (int i = 0; i <= 16; i++) { CIRCLE[i][0] = Math.cos(i * Math.PI / 8); CIRCLE[i][1] = Math.sin(i * Math.PI / 8); } }
    private static final class Motion {
        double last, time, activity;
        Motion(double now, boolean active) { last = now; time = now; activity = active ? 1 : 0; }
    }
    public CosmicHeartRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(GeneratorBlockEntity generator, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        if (generator.reactor == null || !generator.cosmicFormed || generator.getLevel() == null) return;
        var origin = generator.reactor.origin();
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 eye = camera.getPosition().subtract(origin.getX() + UniverseEffects.CENTER_X,
                origin.getY() + UniverseEffects.CENTER_Y, origin.getZ() + UniverseEffects.CENTER_Z);
        double now = generator.getLevel().getGameTime() + partialTick;
        var motion = MOTION.computeIfAbsent(generator, core -> new Motion(now, core.cosmicWorking));
        double dt = Math.max(0, Math.min(20, now - motion.last)); motion.last = now;
        motion.activity += ((generator.cosmicWorking ? 1 : 0) - motion.activity) * (1 - Math.exp(-dt / 10));
        motion.time += dt * (.08 + .92 * motion.activity);
        double time = motion.time, brightness = .28 + .72 * motion.activity;
        int step = eye.lengthSqr() > 10000 ? 4 : eye.lengthSqr() > 3600 || motion.activity < .1 ? 2 : 1;
        Vector3f right = new Vector3f(1, 0, 0).rotate(camera.rotation());
        Vector3f up = new Vector3f(0, 1, 0).rotate(camera.rotation());
        pose.pushPose();
        try {
            var controller = generator.getBlockPos();
            pose.translate(origin.getX() - controller.getX() + UniverseEffects.CENTER_X,
                    origin.getY() - controller.getY() + UniverseEffects.CENTER_Y,
                    origin.getZ() - controller.getZ() + UniverseEffects.CENTER_Z);
            Matrix4f matrix = pose.last().pose();
            // The far hemisphere is the backdrop. The near hemisphere fades out,
            // so the sky never paints over the planets when viewed from outside.
            sky(buffers.getBuffer(CosmicEffectRenderType.SKY), matrix, eye, time, brightness, step);
            VertexConsumer out = buffers.getBuffer(CosmicEffectRenderType.UNIVERSE);
            for (int i = 0; i < UniverseEffects.STARS + UniverseEffects.MIST; i += step) {
                var star = UniverseEffects.sample(i, time, true);
                disc(out, matrix, right, up, star.x(), star.y(), star.z(), star.size(), star.color(), star.alpha() * brightness);
            }
            // Keep all three planets at every distance; LOD affects dust, not the focal objects.
            for (int i = UniverseEffects.STARS + UniverseEffects.MIST; i < UniverseEffects.COUNT; i++) {
                var planet = UniverseEffects.sample(i, time, true);
                sphere(out, matrix, eye, UniverseMesh.PLANET, planet.x(), planet.y(), planet.z(), planet.size(), planet.color(), .93 * brightness);
            }
            for (int path = 0; path < UniverseEffects.PATHS; path++) {
                int segments = Math.max(16, UniverseEffects.segments(path) / step);
                var a = UniverseEffects.pathPoint(path, 0, time);
                for (int j = 0; j < segments; j++) {
                    double fraction = (double) (j + 1) / segments;
                    var b = UniverseEffects.pathPoint(path, fraction, time);
                    ribbon(out, matrix, eye, a, b, UniverseEffects.pathWidth(path), UniverseEffects.pathColor(path),
                            UniverseEffects.pathAlpha(path, fraction, time) * brightness);
                    a = b;
                }
            }
            for (int i = 0; i < 5; i++) {
                var p = UniverseEffects.beacon(i);
                double size = i == 0 ? 1.12 : .48;
                int color = i == 0 ? 0xd9d7c9 : 0x92c5d3;
                disc(out, matrix, right, up, p.x(), p.y(), p.z(), size * 2.25, color, .16 * brightness);
                sphere(out, matrix, eye, i == 0 ? UniverseMesh.CORE : UniverseMesh.PLANET,
                        p.x(), p.y(), p.z(), size, color, .94 * brightness);
                if (i > 0) {
                    var flow = UniverseEffects.pathPoint(i + 11, (time * .004 + i * .23) % 1, time);
                    disc(out, matrix, right, up, flow.x(), flow.y(), flow.z(), .22, 0xb0cee0, .72 * brightness);
                }
            }
        } finally { pose.popPose(); }
    }
    private static void sky(VertexConsumer out, Matrix4f matrix, Vec3 eye, double time, double brightness, int step) {
        boolean inside = eye.lengthSqr() < UniverseEffects.SKY_RADIUS * UniverseEffects.SKY_RADIUS;
        Vec3 direction = eye.normalize();
        float offset = (float) ((time * .000008) % 1);
        float tint = (float) (.58 + .42 * brightness);
        for (var v : step > 1 ? UniverseMesh.SKY_FAR : UniverseMesh.SKY) {
            double dot = v.x() * direction.x + v.y() * direction.y + v.z() * direction.z;
            double fade = Math.max(0, Math.min(1, (.12 - dot) / .22));
            float alpha = (float) (.9 * (inside ? 1 : fade));
            out.vertex(matrix, (float) (v.x() * UniverseEffects.SKY_RADIUS), (float) (v.y() * UniverseEffects.SKY_RADIUS),
                            (float) (v.z() * UniverseEffects.SKY_RADIUS))
                    .uv(v.u() + offset, v.v()).color(tint, tint, tint, alpha).endVertex();
        }
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, double x, double y, double z, int color, double alpha, double shade) {
        out.vertex(matrix, (float) x, (float) y, (float) z)
                .color((float) ((color >> 16 & 255) / 255.0 * shade), (float) ((color >> 8 & 255) / 255.0 * shade),
                        (float) ((color & 255) / 255.0 * shade), (float) alpha).endVertex();
    }
    private static void sphere(VertexConsumer out, Matrix4f matrix, Vec3 eye, UniverseMesh.Vertex[] mesh,
                               double x, double y, double z, double radius, int color, double alpha) {
        for (int i = 0; i < mesh.length; i += 4) {
            // Only the visible surface: no six overlapping cube faces or bright back faces.
            double nx = 0, ny = 0, nz = 0;
            for (int j = 0; j < 4; j++) { var v = mesh[i+j]; nx += v.x(); ny += v.y(); nz += v.z(); }
            if (nx * (eye.x - x) + ny * (eye.y - y) + nz * (eye.z - z) <= 0) continue;
            for (int j = 0; j < 4; j++) {
                var v = mesh[i+j];
                double shade = .64 + .30 * Math.max(0, -.35 * v.x() + .65 * v.y() + .67 * v.z());
                vertex(out, matrix, x + v.x() * radius, y + v.y() * radius, z + v.z() * radius, color, alpha, shade);
            }
        }
    }
    private static void disc(VertexConsumer out, Matrix4f matrix, Vector3f right, Vector3f up,
                             double x, double y, double z, double radius, int color, double alpha) {
        for (int i = 0; i < 16; i++) {
            vertex(out, matrix, x, y, z, color, alpha, 1);
            for (int j = i; j <= i + 1; j++) {
                double u = CIRCLE[j][0] * radius, v = CIRCLE[j][1] * radius;
                vertex(out, matrix, x + right.x * u + up.x * v, y + right.y * u + up.y * v,
                        z + right.z * u + up.z * v, color, 0, 1);
            }
            vertex(out, matrix, x, y, z, color, alpha, 1);
        }
    }
    private static void ribbon(VertexConsumer out, Matrix4f matrix, Vec3 eye, UniverseEffects.Point a, UniverseEffects.Point b,
                               double width, int color, double alpha) {
        double dx = b.x() - a.x(), dy = b.y() - a.y(), dz = b.z() - a.z();
        double ex = eye.x - (a.x() + b.x()) * .5, ey = eye.y - (a.y() + b.y()) * .5, ez = eye.z - (a.z() + b.z()) * .5;
        double nx = dy * ez - dz * ey, ny = dz * ex - dx * ez, nz = dx * ey - dy * ex;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1e-7) return;
        nx *= width / length; ny *= width / length; nz *= width / length;
        vertex(out, matrix, a.x()-nx, a.y()-ny, a.z()-nz, color, alpha, 1);
        vertex(out, matrix, a.x()+nx, a.y()+ny, a.z()+nz, color, alpha, 1);
        vertex(out, matrix, b.x()+nx, b.y()+ny, b.z()+nz, color, alpha, 1);
        vertex(out, matrix, b.x()-nx, b.y()-ny, b.z()-nz, color, alpha, 1);
    }
    @Override public boolean shouldRender(GeneratorBlockEntity generator, Vec3 camera) {
        if (generator.reactor == null || !generator.cosmicFormed) return false;
        var box = generator.getRenderBoundingBox();
        var frustum = Minecraft.getInstance().levelRenderer.getFrustum();
        double dx = Math.max(Math.max(box.minX - camera.x, camera.x - box.maxX), 0);
        double dy = Math.max(Math.max(box.minY - camera.y, camera.y - box.maxY), 0);
        double dz = Math.max(Math.max(box.minZ - camera.z, camera.z - box.maxZ), 0);
        return dx * dx + dy * dy + dz * dz < 160 * 160
                && (box.contains(camera) || frustum == null || frustum.isVisible(box));
    }
    @Override public boolean shouldRenderOffScreen(GeneratorBlockEntity generator) { return generator.reactor != null; }
    @Override public int getViewDistance() { return 160; }
}
