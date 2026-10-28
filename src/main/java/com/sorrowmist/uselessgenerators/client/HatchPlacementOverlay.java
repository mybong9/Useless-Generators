package com.sorrowmist.uselessgenerators.client;

import com.sorrowmist.uselessgenerators.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Nearby accessible slots only, refreshed at 2 Hz; never scans chunks or loads them. */
@Mod.EventBusSubscriber(modid=UselessGenerators.ID,value=Dist.CLIENT)
public final class HatchPlacementOverlay {
    private static final int LIMIT=128,RANGE=24;
    private static List<BlockPos> positions=List.of();
    private static Level previousLevel; private static MeHatchKind previousKind;
    private static long nextRefresh;
    private static MeHatchKind held(ItemStack stack){return stack.getItem() instanceof BlockItem item&&item.getBlock() instanceof MeHatchBlock block?block.kind:null;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance(); MeHatchKind kind=mc.player==null?null:held(mc.player.getMainHandItem());
        if(kind==null&&mc.player!=null)kind=held(mc.player.getOffhandItem());
        if(mc.level==null||mc.player==null||kind==null){positions=List.of();previousKind=null;previousLevel=null;nextRefresh=0;return;}
        if(previousLevel==mc.level&&previousKind==kind&&mc.level.getGameTime()<nextRefresh)return;
        previousLevel=mc.level;previousKind=kind;nextRefresh=mc.level.getGameTime()+10;
        GeneratorBlockEntity nearest=null;double distance=Double.MAX_VALUE;
        var eye=mc.player.getEyePosition();
        for(var pos:ReactorStructure.clientControllers(mc.level)) {
            if(!mc.level.hasChunkAt(pos)||!(mc.level.getBlockEntity(pos) instanceof GeneratorBlockEntity core)||core.reactor==null)continue;
            var o=core.reactor.origin();
            if(o.getY()<mc.level.getMinBuildHeight()||o.getY()+32>mc.level.getMaxBuildHeight())continue;
            double dx=Math.max(0,Math.max(o.getX()-eye.x,eye.x-o.getX()-80)),dy=Math.max(0,Math.max(o.getY()-eye.y,eye.y-o.getY()-32)),dz=Math.max(0,Math.max(o.getZ()-eye.z,eye.z-o.getZ()-80));
            double d=dx*dx+dy*dy+dz*dz;
            if(d<=RANGE*RANGE&&d<distance){nearest=core;distance=d;}
        }
        if(nearest==null){positions=List.of();return;}
        var origin=nearest.reactor.origin();
        for(int x=0;x<5;x++)for(int z=0;z<5;z++)if(!mc.level.hasChunkAt(origin.offset(x*16,0,z*16))){positions=List.of();return;}
        var candidates=new ArrayList<BlockPos>();int hatchCount=0;
        var heldPart=ReactorLayout.Part.valueOf(kind.name());
        for(var cell:ReactorLayout.HATCH_SLOTS) {
            BlockPos pos=nearest.reactor.position(cell);if(!mc.level.hasChunkAt(pos))continue;
            var state=mc.level.getBlockState(pos);
            if(state.getBlock() instanceof MeHatchBlock){hatchCount++;continue;}
            if(!ReactorLayout.acceptsHatch(cell.part(),heldPart)||!mc.level.getWorldBorder().isWithinBounds(pos))continue;
            if(pos.distToCenterSqr(eye)>RANGE*RANGE||!state.isAir()&&!state.is(UselessGenerators.REACTOR_CASING.get()))continue;
            boolean exposed=false;
            for(var direction:Direction.values()) {
                var adjacent=pos.relative(direction);
                if(mc.level.hasChunkAt(adjacent)&&!mc.level.getBlockState(adjacent).isSolidRender(mc.level,adjacent)){exposed=true;break;}
            }
            if(exposed)candidates.add(pos);
        }
        if(hatchCount>=ReactorLayout.MAX_HATCHES){positions=List.of();return;}
        candidates.sort(Comparator.comparingDouble(p->p.distToCenterSqr(eye)));
        positions=List.copyOf(candidates.subList(0,Math.min(LIMIT,candidates.size())));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||positions.isEmpty())return;
        var mc=Minecraft.getInstance();if(mc.level!=previousLevel||mc.player==null)return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        var buffers=mc.renderBuffers().bufferSource();var lines=buffers.getBuffer(RenderType.lines());
        for(var pos:positions) {
            if(!mc.level.hasChunkAt(pos))continue;
            var state=mc.level.getBlockState(pos);if(!state.isAir()&&!state.is(UselessGenerators.REACTOR_CASING.get()))continue;
            var box=new AABB(pos).inflate(.003);
            if(event.getFrustum().isVisible(box))LevelRenderer.renderLineBox(pose,lines,box,.25f,1f,.48f,.85f);
        }
        buffers.endBatch(RenderType.lines());pose.popPose();
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();if(positions.isEmpty()||mc.player==null||mc.screen!=null)return;
        var g=event.getGuiGraphics();var message=CosmicPreview.text("hatch_overlay",positions.size());int width=mc.getWindow().getGuiScaledWidth(),y=mc.getWindow().getGuiScaledHeight()-73;
        int size=mc.font.width(message);g.fill((width-size)/2-4,y-3,(width+size)/2+4,y+11,0xb0222b25);g.drawCenteredString(mc.font,message,width/2,y,0xff94e6a6);
    }
    private HatchPlacementOverlay(){}
}
