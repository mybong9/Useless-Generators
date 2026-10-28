package com.sorrowmist.uselessgenerators.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.sorrowmist.uselessgenerators.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import java.util.*;

/** Cached baked faces with real neighbor culling; no item renderer per structure block. */
public final class CosmicPreviewModel {
    private record Face(ReactorLayout.Cell cell, BakedQuad quad, float shade, int tint) {}
    private record Projected(Face face, float[] xy, double depth) {}
    public int layer = -1;
    public double yaw = -.68, pitch = .56, zoom = 1, panX, panY;
    public ReactorLayout.Cell selected;
    private int cachedLayer = -2;
    private Object generation;
    private List<Face> faces = List.of();
    private List<Projected> projected = List.of();
    private double lastYaw = Double.NaN, lastPitch;
    private double drawX,drawY,scale;
    private void prepare() {
        var mc = Minecraft.getInstance(); var token = mc.getModelManager().getMissingModel();
        if (cachedLayer == layer && generation == token) return;
        var world = new World(); var entries = new ArrayList<ReactorLayout.Cell>();
        for (var cell : ReactorLayout.CELLS) {
            if (cell.part() == ReactorLayout.Part.AIR || layer >= 0 && cell.y() != layer) continue;
            entries.add(cell); world.states.put(CosmicMaterials.local(cell), CosmicMaterials.state(cell,Direction.NORTH));
        }
        var result = new ArrayList<Face>(); var random = RandomSource.create(0);
        for (var cell : entries) {
            var pos = CosmicMaterials.local(cell); var state = world.getBlockState(pos);
            var model = mc.getBlockRenderer().getBlockModel(state); var data = model.getModelData(world,pos,state,ModelData.EMPTY);
            for (int f = 0; f <= 6; f++) {
                Direction direction = f == 6 ? null : Direction.values()[f];
                if (direction != null && !Block.shouldRenderFace(state,world,pos,direction,pos.relative(direction))) continue;
                random.setSeed(state.getSeed(pos));
                for (var quad : model.getQuads(state,direction,random,data,null)) {
                    int tint = quad.isTinted() ? mc.getBlockColors().getColor(state,world,pos,quad.getTintIndex()) : 0xffffff;
                    result.add(new Face(cell,quad,world.getShade(quad.getDirection(),quad.isShade()),tint));
                }
            }
        }
        faces = result; cachedLayer = layer; generation = token; lastYaw = Double.NaN;
    }
    private double[] point(double x,double y,double z) {
        x -= 40; z -= 40; y -= layer < 0 ? 14 : layer + .5;
        double rx = x*Math.cos(yaw)-z*Math.sin(yaw), rz = x*Math.sin(yaw)+z*Math.cos(yaw);
        return new double[]{rx,y*Math.cos(pitch)+rz*Math.sin(pitch),-y*Math.sin(pitch)+rz*Math.cos(pitch)};
    }
    private void project() {
        if (lastYaw == yaw && lastPitch == pitch) return;
        var result = new ArrayList<Projected>();
        for (var face : faces) {
            var d = face.quad.getDirection();
            double normalZ = -d.getStepY()*Math.sin(pitch)+(d.getStepX()*Math.sin(yaw)+d.getStepZ()*Math.cos(yaw))*Math.cos(pitch);
            if (normalZ >= 0) continue;
            float[] xy = new float[8]; double depth = 0;
            var data = face.quad.getVertices(); int stride = data.length/4;
            for (int corner = 0; corner < 4; corner++) {
                int i = corner*stride; var cell = face.cell;
                var p = point(cell.x()+Float.intBitsToFloat(data[i]),cell.y()+Float.intBitsToFloat(data[i+1]),cell.z()+Float.intBitsToFloat(data[i+2]));
                xy[corner*2] = (float)p[0]; xy[corner*2+1] = (float)p[1]; depth += p[2];
            }
            result.add(new Projected(face,xy,depth/4));
        }
        result.sort(Comparator.comparingDouble(Projected::depth).reversed());
        projected = result; lastYaw = yaw; lastPitch = pitch;
    }
    public void draw(GuiGraphics g,int x,int y,int width,int height) {
        prepare(); project(); g.flush();
        scale = Math.min(width/112.0,height/82.0)*zoom;
        drawX = x+width/2.0+panX; drawY = y+height/2.0+panY;
        var top = g.pose().last().pose().transformPosition(new Vector3f(x,y,0));
        var bottom = g.pose().last().pose().transformPosition(new Vector3f(x+width,y+height,0));
        g.enableScissor((int)top.x,(int)top.y,(int)bottom.x,(int)bottom.y);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), blend = GL11.glIsEnabled(GL11.GL_BLEND);
        var shader = RenderSystem.getShader(); int texture = RenderSystem.getShaderTexture(0); float[] color = RenderSystem.getShaderColor().clone();
        g.pose().pushPose();
        try {
            g.pose().translate(drawX,drawY,0); g.pose().scale((float)scale,(float)-scale,1);
            RenderSystem.disableDepthTest(); RenderSystem.disableCull(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1,1,1,1); RenderSystem.setShader(GameRenderer::getPositionColorTexShader); RenderSystem.setShaderTexture(0,InventoryMenu.BLOCK_ATLAS);
            var buffer = Tesselator.getInstance().getBuilder(); buffer.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_COLOR_TEX);
            for (var projectedFace : projected) {
                var f = projectedFace.face; var data = f.quad.getVertices(); int stride = data.length/4;
                float red = f.cell.equals(selected) ? 1 : .95f, green = f.cell.equals(selected) ? .7f : .95f, blue = f.cell.equals(selected) ? .3f : 1;
                for (int corner = 0; corner < 4; corner++) {
                    int i = corner*stride, c = data[i+3]; float shade = f.shade;
                    buffer.vertex(g.pose().last().pose(),projectedFace.xy[corner*2],projectedFace.xy[corner*2+1],0)
                            .color(red*shade*(c&255)/255f*((f.tint>>16)&255)/255f,green*shade*((c>>8)&255)/255f*((f.tint>>8)&255)/255f,
                                    blue*shade*((c>>16)&255)/255f*(f.tint&255)/255f,((c>>>24)&255)/255f)
                            .uv(Float.intBitsToFloat(data[i+4]),Float.intBitsToFloat(data[i+5])).endVertex();
                }
            }
            BufferUploader.drawWithShader(buffer.end());
        } finally {
            g.pose().popPose(); g.disableScissor();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]); RenderSystem.setShader(() -> shader); RenderSystem.setShaderTexture(0,texture);
        }
    }
    public void pick(double x,double y) {
        if (scale == 0) return;
        double px = (x-drawX)/scale, py = (drawY-y)/scale;
        selected = null;
        for (int i = projected.size()-1; i >= 0; i--) {
            var p = projected.get(i); boolean inside = false;
            for (int a = 0,b = 3; a < 4; b = a++) {
                float ax = p.xy[a*2],ay = p.xy[a*2+1],bx = p.xy[b*2],by = p.xy[b*2+1];
                if ((ay>py)!=(by>py) && px < (bx-ax)*(py-ay)/(by-ay)+ax) inside = !inside;
            }
            if (inside) { selected = p.face.cell; return; }
        }
    }
    public void turn(double dx,double dy) { yaw += dx*.012; pitch = Math.max(-1.3,Math.min(1.3,pitch+dy*.012)); }
    public void zoom(double delta) { zoom = Math.max(.4,Math.min(5,zoom*Math.pow(1.15,delta))); }
    public void reset() { yaw = -.68; pitch = .56; zoom = 1; panX = panY = 0; }
    private static final class World implements BlockAndTintGetter {
        final Map<BlockPos,BlockState> states = new HashMap<>();
        public BlockState getBlockState(BlockPos pos) { return states.getOrDefault(pos,Blocks.AIR.defaultBlockState()); }
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        public int getHeight() { return 32; }
        public int getMinBuildHeight() { return 0; }
        public float getShade(Direction face,boolean shade) { return !shade?1:switch(face) {case UP->1;case DOWN->.5f;case EAST,WEST->.75f;default->.9f;}; }
        public LevelLightEngine getLightEngine() { return null; }
        public int getBrightness(LightLayer layer,BlockPos pos) { return 15; }
        public int getRawBrightness(BlockPos pos,int darken) { return 15; }
        public int getBlockTint(BlockPos pos,ColorResolver resolver) { return 0xffffff; }
    }
}
