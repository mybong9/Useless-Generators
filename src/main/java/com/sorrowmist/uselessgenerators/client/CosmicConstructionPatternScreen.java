package com.sorrowmist.uselessgenerators.client;
import com.sorrowmist.uselessgenerators.CosmicMaterials;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
/** Read-only bill, with counts held as integers rather than byte-sized ItemStack.Count. */
public final class CosmicConstructionPatternScreen extends Screen {
    private int first;
    private CosmicConstructionPatternScreen(){super(Component.translatable("item.useless_generators.cosmic_construction_pattern"));}
    public static void open(){Minecraft.getInstance().setScreen(new CosmicConstructionPatternScreen());}
    @Override protected void init(){addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(width/2-80,height-26,160,20).build());}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g);g.drawCenteredString(font,title,width/2,12,0xffffff);
        int y=36;int shown=Math.max(1,(height-78)/18);
        for(int i=first;i<Math.min(CosmicMaterials.all().size(),first+shown);i++){
            var row=CosmicMaterials.all().get(i);
            g.renderItem(row.stack(),width/2-150,y);
            g.drawString(font,row.stack().getHoverName(),width/2-128,y+4,0xffffff);
            String count="× "+row.count();g.drawString(font,count,width/2+150-font.width(count),y+4,0xe0c8ff);y+=18;
        }
        super.render(g,mx,my,partial);
    }
    @Override public boolean mouseScrolled(double x,double y,double delta){
        int shown=Math.max(1,(height-78)/18);
        first=Math.max(0,Math.min(Math.max(0,CosmicMaterials.all().size()-shown),first-(int)Math.signum(delta)));
        return true;
    }
    @Override public boolean isPauseScreen(){return false;}
}
