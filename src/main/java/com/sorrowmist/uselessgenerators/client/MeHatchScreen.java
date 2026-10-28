package com.sorrowmist.uselessgenerators.client;

import appeng.api.stacks.*;
import com.sorrowmist.uselesscore.client.ui.*;
import com.sorrowmist.uselessgenerators.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import java.util.List;
import java.util.Optional;

/** Mark and buffer cards, with an explicit per-mark replenishment target. */
public final class MeHatchScreen extends FittedContainerScreen<MeHatchMenu> {
    private static final UiTheme THEME = UiTheme.LIGHT;
    private int selected;
    private EditBox amount;
    private UiButton toggle, apply;
    private boolean dirty;
    private GenericStack observedMark;
    public MeHatchScreen(MeHatchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth=MeHatchLayout.WIDTH; imageHeight=MeHatchLayout.HEIGHT;
    }
    public static void receive(int id, CompoundTag tag) {
        var player=Minecraft.getInstance().player;
        if(player!=null && player.containerMenu instanceof MeHatchMenu menu && menu.containerId==id)menu.receive(tag);
    }
    private static Component tr(String key,Object... args){return Component.translatable("gui.useless_generators.me."+key,args);}
    private UiButton button(int x,int y,int w,int h,Component text,net.minecraft.client.gui.components.Button.OnPress action){
        return addRenderableWidget(new UiButton(leftPos+x,topPos+y,w,h,text,action,THEME));
    }
    @Override protected void init(){
        String previous=amount==null?null:amount.getValue();boolean wasDirty=dirty;
        super.init();
        amount=addRenderableWidget(new UiEditBox(font,leftPos+274,topPos+79,80,25,tr("target"),THEME));
        amount.setMaxLength(10);amount.setFilter(value->value.isEmpty()||value.matches("[0-9]{1,10}"));
        amount.visible=menu.kind().input();
        amount.setResponder(value->{dirty=true;if(apply!=null)updateApply();});
        var minus=button(250,79,22,25,Component.literal("−"),b->step(-1));
        var plus=button(356,79,22,25,Component.literal("+"),b->step(1));
        minus.visible=plus.visible=menu.kind().input();
        long[] presets={16,64,256,1024};
        for(int i=0;i<presets.length;i++){
            long value=presets[i];button(250+i*33,112,30,18,Component.literal(Long.toString(value)),b->amount.setValue(Long.toString(value))).visible=menu.kind().input();
        }
        apply=button(250,145,128,20,tr("apply"),b->applyAmount()).primary();apply.visible=menu.kind().input();
        toggle=button(250,170,62,20,tr("pause"),b->send(3,0));button(316,170,62,20,tr("refresh"),b->send(8,0));
        if(previous!=null&&wasDirty){amount.setValue(previous);dirty=true;}else select(selected);
        updateApply();
    }
    private long targetValue(){try{return Long.parseLong(amount.getValue());}catch(NumberFormatException ignored){return 0;}}
    private void updateApply(){
        long value=targetValue();boolean valid=value>0&&value<=HatchTransferPolicy.MAX_TARGET;
        apply.active=menu.stack(selected,true)!=null&&valid;amount.setTextColor(valid?THEME.text():THEME.danger());
    }
    private void step(int direction){
        long value=targetValue(),step=hasShiftDown()?64:1;
        amount.setValue(Long.toString(Math.max(1,Math.min(HatchTransferPolicy.MAX_TARGET,value+direction*step))));
    }
    private void applyAmount(){
        updateApply();if(!apply.active)return;
        MeHatchNetwork.action(menu.containerId,2,selected,targetValue());dirty=false;amount.setFocused(false);
    }
    private void send(int action,int slot){MeHatchNetwork.action(menu.containerId,action,slot,0);}
    public Rect2i markScreenArea(int slot){return screenRect(leftPos+MeHatchLayout.cellX(slot),topPos+MeHatchLayout.markY(slot),MeHatchLayout.CELL,MeHatchLayout.CELL);}
    public void acceptMark(int slot,AEKey key){
        if(!menu.kind().input()||slot<0||slot>=16||key==null)return;
        select(slot);var previous=menu.stack(slot,true);
        if(previous==null||!previous.what().equals(key))amount.setValue(menu.kind()==MeHatchKind.FLUID_INPUT?"64000":"64");
        dirty=false;MeHatchNetwork.mark(menu.containerId,slot,key);
    }
    private void select(int slot){
        selected=Math.max(0,Math.min(15,slot));observedMark=menu.stack(selected,true);
        amount.setValue(observedMark==null?(menu.kind()==MeHatchKind.FLUID_INPUT?"64000":"64"):Long.toString(observedMark.amount()));dirty=false;
    }
    @Override protected void containerTick(){
        super.containerTick();toggle.setMessage(tr(menu.snapshot().getBoolean("Paused")?"resume":"pause"));
        GenericStack current=menu.stack(selected,true);
        if(!java.util.Objects.equals(observedMark,current)){
            observedMark=current;if(!dirty&&!amount.isFocused())select(selected);
        }
        updateApply();
    }
    private void panel(GuiGraphics g,int x,int y,int w,int h){UiDraw.panel(g,leftPos+x,topPos+y,w,h,8,THEME.surface(),THEME.border());}
    private void text(GuiGraphics g,Component text,int x,int y,int width,int colour){UiDraw.text(g,font,text.getString(),leftPos+x,topPos+y,width,colour);}
    private void slot(GuiGraphics g,int x,int y,GenericStack stack,boolean chosen){
        int px=leftPos+x,py=topPos+y;UiDraw.slot(g,px,py,MeHatchLayout.CELL,THEME,chosen);if(stack==null)return;
        if(stack.what() instanceof AEFluidKey fluid){
            var fluidStack=fluid.toStack(1000);var ext=IClientFluidTypeExtensions.of(fluid.getFluid());
            var sprite=minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture(fluidStack));int c=ext.getTintColor(fluidStack);
            g.setColor(((c>>16)&255)/255f,((c>>8)&255)/255f,(c&255)/255f,1);g.blit(px+3,py+3,0,16,16,sprite);g.setColor(1,1,1,1);
        }else g.renderItem(stack.what().wrapForDisplayOrFilter(),px+3,py+3);
        String count=shortAmount(stack.amount());g.pose().pushPose();g.pose().translate(0,0,180);g.pose().scale(.65f,.65f,1);
        g.drawString(font,count,(int)((px+21)/.65f)-font.width(count),(int)((py+15)/.65f),0xffffffff,true);g.pose().popPose();
    }
    private static String shortAmount(long n){
        if(n<1000)return Long.toString(n);String[] suffix={"","k","M","G","T","P","E"};double value=n;int i=0;
        while(value>=1000&&i<6){value/=1000;i++;}return String.format(java.util.Locale.ROOT,"%.1f%s",value,suffix[i]);
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my){
        UiDraw.glass(g,leftPos,topPos,imageWidth,imageHeight,12,THEME);panel(g,226,7,158,28);
        boolean online=menu.snapshot().getBoolean("Online");UiDraw.rounded(g,leftPos+234,topPos+15,6,6,3,online?0xff35b978:THEME.danger());
        text(g,tr(online?"online":"offline"),245,12,130,THEME.text());text(g,tr(menu.snapshot().getBoolean("Linked")?"shared":"direct"),245,24,130,THEME.muted());
        panel(g,240,42,144,154);
        if(menu.kind().input()){
            panel(g,12,42,222,74);panel(g,12,124,222,72);
            text(g,tr("marks"),20,48,202,THEME.text());text(g,tr("buffer"),20,130,202,THEME.text());
            for(int i=0;i<16;i++){
                slot(g,MeHatchLayout.cellX(i),MeHatchLayout.markY(i),menu.stack(i,true),selected==i);
                slot(g,MeHatchLayout.cellX(i),MeHatchLayout.stockY(i,true),menu.stack(i,false),selected==i);
            }
            text(g,tr("selected",selected+1),250,50,128,THEME.text());text(g,tr(menu.kind()==MeHatchKind.FLUID_INPUT?"target_fluid":"target_item"),250,65,128,THEME.muted());
            text(g,tr("maximum"),250,132,128,targetValue()>HatchTransferPolicy.MAX_TARGET?THEME.danger():THEME.muted());
        }else if(menu.kind()==MeHatchKind.OUTPUT){
            panel(g,12,42,222,154);text(g,tr("buffer"),20,48,202,THEME.text());
            for(int i=0;i<32;i++)slot(g,MeHatchLayout.cellX(i),MeHatchLayout.stockY(i,false),menu.stack(i,false),false);
            g.drawWordWrap(font,tr("output_hint"),leftPos+250,topPos+54,128,THEME.text());
        }else{
            panel(g,12,42,222,154);g.drawWordWrap(font,tr(menu.kind()==MeHatchKind.ENERGY?"energy_hint":"connector_hint"),leftPos+22,topPos+58,198,THEME.text());
            g.drawWordWrap(font,tr("energy",shortAmount(menu.snapshot().getLong("Energy"))),leftPos+250,topPos+56,128,THEME.text());
            g.drawWordWrap(font,tr("transfer",shortAmount(menu.snapshot().getLong("Transfer"))),leftPos+250,topPos+100,128,THEME.muted());
        }
        panel(g,12,204,372,90);for(var s:menu.slots)UiDraw.slot(g,leftPos+s.x-1,topPos+s.y-1,18,THEME,false);
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my){UiDraw.text(g,font,title.getString(),14,16,204,THEME.text());g.drawString(font,playerInventoryTitle,20,214,THEME.muted(),false);}
    private boolean inside(int x,int y,int sx,int sy){return x>=sx&&x<sx+MeHatchLayout.CELL&&y>=sy&&y<sy+MeHatchLayout.CELL;}
    @Override protected boolean clickContent(double mx,double my,int button){
        int x=(int)mx-leftPos,y=(int)my-topPos;
        if(menu.kind().input())for(int i=0;i<16;i++){
            int sx=MeHatchLayout.cellX(i);
            if(inside(x,y,sx,MeHatchLayout.markY(i))){select(i);if(button==1)send(1,i);else if(button==0&&!menu.getCarried().isEmpty())send(0,i);return true;}
            if(inside(x,y,sx,MeHatchLayout.stockY(i,true))){if(button==0||button==1)send(menu.kind()==MeHatchKind.ITEM_INPUT?4:6,i);return true;}
        }
        if(menu.kind()==MeHatchKind.OUTPUT)for(int i=0;i<32;i++)if(inside(x,y,MeHatchLayout.cellX(i),MeHatchLayout.stockY(i,false))){
            var stock=menu.stack(i,false);boolean filled=net.minecraftforge.fluids.FluidUtil.getFluidContained(menu.getCarried()).filter(fluid->!fluid.isEmpty()).isPresent();
            if(button==0||button==1)send(!menu.getCarried().isEmpty()?(!filled&&stock!=null&&stock.what() instanceof AEFluidKey?6:5):4,i);return true;
        }
        return super.clickContent(mx,my,button);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(amount.visible&&amount.isFocused()&&key!=256){if(key==257||key==335){applyAmount();return true;}if(amount.keyPressed(key,scan,modifiers)||amount.canConsumeInput())return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override protected void renderContent(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);super.renderContent(g,mx,my,partial);renderTooltip(g,mx,my);
        if(amount.visible && amount.isMouseOver(mx,my))g.renderTooltip(font,tr("target_limit","2,100,000,000"),mx,my);
        int x=mx-leftPos,y=my-topPos,slots=menu.kind().input()?16:menu.kind()==MeHatchKind.OUTPUT?32:0;
        for(int i=0;i<slots;i++)for(int row=0;row<(menu.kind().input()?2:1);row++){
            boolean mark=menu.kind().input()&&row==0;int sy=mark?MeHatchLayout.markY(i):MeHatchLayout.stockY(i,menu.kind().input());
            if(inside(x,y,MeHatchLayout.cellX(i),sy)){
                var stack=menu.stack(i,mark);
                if(stack!=null)g.renderTooltip(font,List.of(stack.what().getDisplayName(),Component.literal(Long.toString(stack.amount())+(stack.what() instanceof AEFluidKey?" mB":"")),tr(mark?"mark_hint":"stock_hint")),Optional.empty(),mx,my);
                else if(mark)g.renderTooltip(font,tr("tooltip"),mx,my);
            }
        }
    }
}
