package com.sorrowmist.uselessgenerators.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import java.util.Optional;

public final class CosmicPreviewScreen extends Screen {
    private final Screen parent; private final CosmicPreview preview;
    private float scale; private int left,top;
    public CosmicPreviewScreen(Screen parent,CosmicPreview preview) {super(CosmicPreview.text("structure"));this.parent=parent;this.preview=preview;}
    @Override protected void init() { scale=Math.min(2.5f,Math.min((width-24f)/CosmicPreview.WIDTH,(height-35f)/CosmicPreview.HEIGHT));left=(int)(width-CosmicPreview.WIDTH*scale)/2;top=(int)(height-CosmicPreview.HEIGHT*scale)/2; }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        renderBackground(g); g.pose().pushPose();g.pose().translate(left,top,0);g.pose().scale(scale,scale,1);
        preview.draw(g,(mx-left)/scale,(my-top)/scale);g.pose().popPose();
        var tip=preview.tooltip((mx-left)/scale,(my-top)/scale);if(!tip.isEmpty())g.renderTooltip(font,tip,Optional.empty(),mx,my);
        g.drawCenteredString(font,CosmicPreview.text("close_hint"),width/2,height-12,0xffe6e0ef);
    }
    @Override public boolean mouseClicked(double x,double y,int b) {return preview.mouseClicked((x-left)/scale,(y-top)/scale,b)||super.mouseClicked(x,y,b);}
    @Override public boolean mouseReleased(double x,double y,int b) {return preview.mouseReleased((x-left)/scale,(y-top)/scale,b)||super.mouseReleased(x,y,b);}
    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy) {return preview.mouseDragged((x-left)/scale,(y-top)/scale,b,dx/scale,dy/scale)||super.mouseDragged(x,y,b,dx,dy);}
    @Override public boolean mouseScrolled(double x,double y,double delta) {return preview.mouseScrolled((x-left)/scale,(y-top)/scale,delta)||super.mouseScrolled(x,y,delta);}
    @Override public void onClose() {minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen() {return false;}
}
