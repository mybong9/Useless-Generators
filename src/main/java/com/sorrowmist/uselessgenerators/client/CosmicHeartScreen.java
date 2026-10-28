package com.sorrowmist.uselessgenerators.client;

import com.sorrowmist.uselessgenerators.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import com.sorrowmist.uselesscore.client.ui.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.*;
import static com.sorrowmist.uselessgenerators.client.CosmicPreview.text;

/** Multiblock status display and side functions, following GTM / GTLAdditions' information hierarchy. */
public final class CosmicHeartScreen extends FittedContainerScreen<GeneratorMenu> {
    private final CosmicPreviewModel preview=new CosmicPreviewModel();
    private final MachineButton[] tabs=new MachineButton[4];
    private MachineButton work,recheck,copy,pattern,all,down,up,reset;
    private int page; private boolean dragging,moved; private double startX,startY;
    public CosmicHeartScreen(GeneratorMenu menu,Inventory inventory,Component title) {super(menu,inventory,title);imageWidth=320;imageHeight=238;}
    @Override protected void init() {
        super.init();
        String[] pages={"status","structure","hatches","materials"};
        for(int i=0;i<tabs.length;i++){final int target=i;tabs[i]=button(10+i*76,25,72,pages[i],b->{page=target;refresh();});}
        work=button(10,127,96,"pause",b->send(GeneratorMenu.WORK_ACTION));
        work.setTooltip(Tooltip.create(text("pause_hint")));
        recheck=button(110,127,96,"recheck",b->send(GeneratorMenu.RECHECK_ACTION));
        copy=button(210,127,100,"copy",b->minecraft.keyboardHandler.setClipboard(CosmicMaterials.textList()));
        pattern=button(110,127,96,"pattern",b->send(GeneratorMenu.CONSTRUCTION_PATTERN_ACTION));
        pattern.setTooltip(Tooltip.create(text("pattern_hint")));
        all=button(244,50,60,"all_layers",b->preview.layer=-1);
        down=new MachineButton(leftPos+244,topPos+73,28,17,Component.literal("−"),b->preview.layer=Math.max(0,preview.layer-1));addRenderableWidget(down);
        up=new MachineButton(leftPos+276,topPos+73,28,17,Component.literal("+"),b->preview.layer=Math.min(31,preview.layer+1));addRenderableWidget(up);
        reset=button(244,101,60,"reset",b->preview.reset()); refresh();
    }
    private MachineButton button(int x,int y,int w,String key,net.minecraft.client.gui.components.Button.OnPress callback) {
        return addRenderableWidget(new MachineButton(leftPos+x,topPos+y,w,17,text(key),callback));
    }
    private void send(int action){if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}
    private void refresh(){pattern.visible=page==3;recheck.visible=page!=3;for(int i=0;i<4;i++)tabs[i].setSelected(page==i);all.visible=down.visible=up.visible=reset.visible=page==1;work.setMessage(text(menu.workEnabled()?"pause":"resume"));}
    @Override protected void containerTick(){super.containerTick();refresh();}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my) {
        UiDraw.glass(g,leftPos,topPos,imageWidth,imageHeight,12,UiTheme.DARK);
        UiDraw.panel(g,leftPos+10,topPos+46,300,78,9,UiTheme.DARK.inset(),UiTheme.DARK.border());
        UiDraw.panel(g,leftPos+72,topPos+143,176,89,9,UiTheme.DARK.surface(),UiTheme.DARK.border());
        for(var slot:menu.slots)UiDraw.slot(g,leftPos+slot.x-1,topPos+slot.y-1,18,UiTheme.DARK,false);
        if(page==1) {g.pose().pushPose();g.pose().translate(leftPos,topPos,0);preview.draw(g,12,48,228,74);g.pose().popPose();}
    }
    private void line(GuiGraphics g,int row,Component component,int color){g.drawString(font,font.plainSubstrByWidth(component.getString(),286),15,51+row*12,color,false);}
    private Component status(){return Component.translatable("gui.useless_generators.structure."+menu.structureStatus().name().toLowerCase(Locale.ROOT));}
    private int statusColor(){return menu.structureStatus()==ReactorStructure.Status.FORMED?0xff96c9a3:menu.structureStatus()==ReactorStructure.Status.CHECKING?0xffb6aecb:0xffe1a28f;}
    private Component workState(){return text(!menu.workEnabled()?"paused":menu.structureStatus()!=ReactorStructure.Status.FORMED?"not_formed":menu.generated()>0?"running":menu.stored()>=menu.capacity()?"storage_full":"waiting_fuel");}
    public static String number(long value){if(value<1000)return Long.toString(value);String[] units={"k","M","G","T","P","E"};double n=value;int i=-1;do{n/=1000;i++;}while(n>=1000&&i<5);return String.format(Locale.ROOT,"%.2f%s",n,units[i]).replace(".00","");}
    @Override protected void renderLabels(GuiGraphics g,int mx,int my) {
        g.drawString(font,title,12,11,UiTheme.DARK.text(),false);g.drawString(font,playerInventoryTitle,80,145,UiTheme.DARK.muted(),false);
        if(page==0) {
            line(g,0,status(),statusColor());line(g,1,workState(),menu.generated()>0?0xffa6d4b2:0xffd9c7a0);
            line(g,2,text("power",number(menu.generated()),number(menu.maximumRate())),0xffdfd8eb);
            var fuel=menu.fuel();line(g,3,text("fuel",fuel.isEmpty()?text("empty"):fuel.getDisplayName(),fuel.getAmount()),0xffc2bacf);
            line(g,4,text("energy",number(menu.stored()),number(menu.capacity())),0xffc2bacf);
            line(g,5,text("network",text(menu.aeConnected()?"online":"offline"),menu.onlineHatches()),menu.aeConnected()?0xffa6c3ca:0xffa49dad);
        } else if(page==1) {
            g.drawCenteredString(font,preview.layer<0?text("all_layers"):text("layer",preview.layer+1),274,92,0xffd7cfe2);
        } else if(page==2) {
            for(var kind:MeHatchKind.values())line(g,kind.ordinal(),text("hatch_count",Component.translatable("block.useless_generators."+kind.id),menu.hatchCount(kind)),0xffd3ccdf);
            line(g,5,text("network",text(menu.aeConnected()?"online":"offline"),menu.onlineHatches()),0xffa5bdc7);
        } else {
            for(int i=0;i<CosmicMaterials.all().size();i++) {
                var entry=CosmicMaterials.all().get(i);int x=15+(i/6)*149,y=50+(i%6)*12;
                g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(.625f,.625f,1);g.renderItem(entry.stack(),0,0);g.pose().popPose();
                String name=font.plainSubstrByWidth(entry.stack().getHoverName().getString(),93);
                g.drawString(font,name,x+13,y+1,0xffd4cce0,false);String count=Integer.toString(entry.count());g.drawString(font,count,x+140-font.width(count),y+1,0xffad9ec5,false);
            }
        }
    }
    private List<Component> diagnostic() {
        var lines=new ArrayList<Component>();lines.add(status());lines.add(text("size"));
        lines.add(text("origin",menu.structureOrigin().toShortString()));
        if(menu.structureStatus()==ReactorStructure.Status.CHECKING)lines.add(text("checked",menu.structureChecked(),ReactorLayout.CELLS.size()));
        if(menu.structureStatus()==ReactorStructure.Status.INCOMPLETE) {
            lines.add(text("problem",menu.structureProblem().toShortString()));int part=menu.expectedPart();
            if(part>=0&&part<ReactorLayout.Part.values().length)lines.add(text("expected",ReactorStructure.block(ReactorLayout.Part.values()[part]).getName()));
        }
        return lines;
    }
    @Override protected void renderContent(GuiGraphics g,int mx,int my,float partial) {
        renderBackground(g);super.renderContent(g,mx,my,partial);renderTooltip(g,mx,my);
        int x=mx-leftPos,y=my-topPos;
        if(x>=10&&x<310&&y>=46&&y<124) {
            List<Component> tip=List.of();
            if(page==0&&y<75||page==1)tip=diagnostic();
            else if(page==0&&y>=75&&y<87)tip=List.of(Component.literal(menu.generated()+" / "+menu.maximumRate()+" FE/t"));
            else if(page==0&&y>=87&&y<99)tip=List.of(text("fuel_hint"),Component.literal(menu.fuelRemainder()+" FE"));
            else if(page==0&&y>=99&&y<111)tip=List.of(Component.literal(menu.stored()+" / "+menu.capacity()+" FE"));
            else if(page==2)tip=List.of(text("hatch_hint"));
            else if(page==3){int i=(x-15)/149*6+(y-50)/12;if(x>=15&&y>=50&&i>=0&&i<CosmicMaterials.all().size()){var e=CosmicMaterials.all().get(i);tip=List.of(e.stack().getHoverName(),Component.literal("× "+e.count()),text("material_note"));}}
            if(!tip.isEmpty()&&!dragging)g.renderTooltip(font,tip,Optional.empty(),mx,my);
        }
    }
    @Override protected boolean clickContent(double x,double y,int b) {
        if(page==1&&x-leftPos>=12&&x-leftPos<240&&y-topPos>=48&&y-topPos<122&&(b==0||b==1)){dragging=true;moved=false;startX=x;startY=y;return true;}
        return super.clickContent(x,y,b);
    }
    @Override protected boolean dragContent(double x,double y,int b,double dx,double dy){if(dragging){if(Math.hypot(x-startX,y-startY)>3)moved=true;if(moved){if(b==1){preview.panX+=dx;preview.panY+=dy;}else preview.turn(dx,dy);}return true;}return super.dragContent(x,y,b,dx,dy);}
    @Override protected boolean releaseContent(double x,double y,int b){if(dragging){dragging=false;if(!moved&&b==0)preview.pick(x-leftPos,y-topPos);return true;}return super.releaseContent(x,y,b);}
    @Override protected boolean scrollContent(double x,double y,double delta){if(page==1&&x-leftPos>=12&&x-leftPos<240&&y-topPos>=48&&y-topPos<122){preview.zoom(delta);return true;}return super.scrollContent(x,y,delta);}
}
