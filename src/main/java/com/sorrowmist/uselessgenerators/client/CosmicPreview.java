package com.sorrowmist.uselessgenerators.client;

import com.sorrowmist.uselessgenerators.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.List;

/** One interaction model shared by JEI, EMI and the standalone inspection screen. */
public final class CosmicPreview {
    public static final int WIDTH = 316, HEIGHT = 190;
    public final CosmicPreviewModel model = new CosmicPreviewModel();
    private boolean materials, dragging, moved; private double startX,startY;
    public static Component text(String key,Object... args) { return Component.translatable("gui.useless_generators.cosmic."+key,args); }
    public static float viewerScale() { var w=Minecraft.getInstance().getWindow(); return Math.max(.5f,Math.min(1,Math.min((w.getGuiScaledWidth()-40f)/WIDTH,(w.getGuiScaledHeight()-72f)/HEIGHT))); }
    public static int viewerWidth() { return (int)(WIDTH*viewerScale()); }
    public static int viewerHeight() { return (int)(HEIGHT*viewerScale()); }
    public void draw(GuiGraphics g,double mx,double my) {
        var font=Minecraft.getInstance().font;
        g.fill(0,0,WIDTH,HEIGHT,0xffd4d4de); g.fill(1,23,WIDTH-1,HEIGHT-1,0xff272933);
        button(g,4,3,76,"structure",!materials); button(g,83,3,76,"materials",materials);
        button(g,162,3,72,"expand",false); button(g,237,3,75,"copy",false);
        if (materials) {
            for (int i=0;i<CosmicMaterials.all().size();i++) {
                var entry=CosmicMaterials.all().get(i); int x=8+(i/6)*156,y=30+(i%6)*22;
                g.renderItem(entry.stack(),x,y); String name=font.plainSubstrByWidth(entry.stack().getHoverName().getString(),108);
                g.drawString(font,name,x+20,y,0xffe5e2ec,false);
                g.drawString(font,"× "+entry.count(),x+20,y+10,0xffaba4bc,false);
            }
            g.drawString(font,text("materials_total",ReactorLayout.MATERIAL_COUNT),9,168,0xffbdb5cd,false);
        } else {
            model.draw(g,4,26,248,136);
            button(g,257,28,55,"all_layers",model.layer<0);
            mini(g,257,50,25,"−"); mini(g,286,50,26,"+");
            g.drawCenteredString(font,model.layer<0?text("all_layers"):text("layer",model.layer+1),284,74,0xffd7d0e4);
            mini(g,257,92,25,"↶"); mini(g,286,92,26,"↷");
            button(g,257,116,55,"reset",false);
            g.drawString(font,text("size"),8,166,0xffaaa3b8,false);
            var s=model.selected;
            Component line=s==null?text("preview_hint"):text(s.part()==ReactorLayout.Part.CASING?"selected_casing":"selected",
                    ReactorStructure.block(s.part()).getName(),s.x(),s.y(),s.z());
            g.drawString(font,font.plainSubstrByWidth(line.getString(),302),8,178,0xffe8e1f0,false);
        }
    }
    private static void button(GuiGraphics g,int x,int y,int w,String label,boolean selected) {
        mini(g,x,y,w,text(label).getString(),selected);
    }
    private static void mini(GuiGraphics g,int x,int y,int w,String label) { mini(g,x,y,w,label,false); }
    private static void mini(GuiGraphics g,int x,int y,int w,String label,boolean selected) {
        g.fill(x,y,x+w,y+17,selected?0xff9c83b4:0xffb2aebc);g.fill(x+1,y+1,x+w-1,y+16,selected?0xffe8ddf2:0xffd9d6e1);
        var font=Minecraft.getInstance().font;g.drawString(font,label,x+(w-font.width(label))/2,y+5,0xff41384e,false);
    }
    public boolean mouseClicked(double x,double y,int button) {
        if (button==0 && y>=3 && y<20) {
            if (x>=4&&x<80) materials=false;
            else if(x<159&&x>=83) materials=true;
            else if(x>=162&&x<234) { var mc=Minecraft.getInstance(); if (!(mc.screen instanceof CosmicPreviewScreen)) mc.setScreen(new CosmicPreviewScreen(mc.screen,this)); }
            else if(x>=237&&x<312) Minecraft.getInstance().keyboardHandler.setClipboard(CosmicMaterials.textList()); else return false;
            return true;
        }
        if (!materials && x>=257 && x<312 && button==0) {
            if(y>=28&&y<45)model.layer=-1;
            else if(y>=50&&y<67)model.layer=Math.max(0,Math.min(31,model.layer+(x<284?-1:1)));
            else if(y>=92&&y<109)model.yaw+=x<284?-.3:.3;
            else if(y>=116&&y<133)model.reset(); else return false;
            return true;
        }
        if (!materials && inside(x,y) && (button==0||button==1)) { dragging=true;moved=false;startX=x;startY=y;return true; }
        return false;
    }
    private boolean inside(double x,double y) { return x>=4&&x<252&&y>=26&&y<162; }
    public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if(!dragging)return false;
        if(Math.hypot(x-startX,y-startY)>3)moved=true;
        if(moved) { if(button==1) {model.panX+=dx;model.panY+=dy;} else model.turn(dx,dy); }
        return true;
    }
    public boolean mouseReleased(double x,double y,int button) {
        if(!dragging)return false; dragging=false; if(!moved&&button==0)model.pick(x,y); return true;
    }
    public boolean mouseScrolled(double x,double y,double delta) { if(materials||!inside(x,y))return false;model.zoom(delta);return true; }
    public List<Component> tooltip(double x,double y) {
        if(materials)for(int i=0;i<CosmicMaterials.all().size();i++) {
            int sx=8+(i/6)*156,sy=30+(i%6)*22;
            if(x>=sx&&x<sx+150&&y>=sy&&y<sy+21) { var e=CosmicMaterials.all().get(i);return List.of(e.stack().getHoverName(),Component.literal("× "+e.count()),text("material_note")); }
        }
        if(!materials&&model.selected!=null&&y>=175) {var s=model.selected;return List.of(text(s.part()==ReactorLayout.Part.CASING?"selected_casing":"selected",ReactorStructure.block(s.part()).getName(),s.x(),s.y(),s.z()));}
        return List.of();
    }
}
