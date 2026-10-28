package com.sorrowmist.uselessgenerators.compat;

import com.sorrowmist.uselessgenerators.*;
import com.sorrowmist.uselessgenerators.client.CosmicPreview;
import dev.emi.emi.api.*;
import dev.emi.emi.api.recipe.*;
import dev.emi.emi.api.stack.*;
import dev.emi.emi.api.widget.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

@EmiEntrypoint
public final class CosmicEmiPlugin implements EmiPlugin {
    private static final ResourceLocation ID=new ResourceLocation(UselessGenerators.ID,"cosmic_structure");
    private static final EmiRecipeCategory CATEGORY=new EmiRecipeCategory(ID,EmiStack.of(UselessGenerators.COSMIC_HEART.get())) {
        @Override public Component getName(){return Component.translatable("block.useless_generators.cosmic_heart");}
    };
    @Override public void register(EmiRegistry registry) {
        registry.addCategory(CATEGORY);
        registry.removeRecipes(recipe -> !(recipe instanceof HeartRecipe) && recipe.getCategory().getId().equals(ID));
        registry.addWorkstation(CATEGORY,EmiStack.of(UselessGenerators.COSMIC_HEART.get()));
        registry.addRecipe(new HeartRecipe());
    }
    private static final class HeartRecipe implements com.sorrowmist.useless.compat.emi.OmniversalEmiPatternRecipe {
        @Override public void encode(appeng.menu.me.items.PatternEncodingTermMenu menu) {MeHatchNetwork.encodeConstruction(menu.containerId);}
        private final CosmicPreview preview=new CosmicPreview();
        @Override public EmiRecipeCategory getCategory(){return CATEGORY;}
        @Override public ResourceLocation getId(){return ID;}
        @Override public List<EmiIngredient> getInputs(){return CosmicMaterials.all().stream().map(e -> (EmiIngredient)EmiStack.of(e.stack(),e.count())).toList();}
        @Override public List<EmiStack> getOutputs(){return List.of(EmiStack.of(UselessGenerators.COSMIC_HEART.get()));}
        @Override public boolean supportsRecipeTree(){return false;}
        @Override public boolean hideCraftable(){return true;}
        @Override public int getDisplayWidth(){return CosmicPreview.viewerWidth();}
        @Override public int getDisplayHeight(){return CosmicPreview.viewerHeight();}
        @Override public void addWidgets(WidgetHolder widgets) {
            widgets.add(new Widget() {
                @Override public Bounds getBounds(){return new Bounds(0,0,getDisplayWidth(),getDisplayHeight());}
                @Override public void render(GuiGraphics g,int mx,int my,float delta){float s=CosmicPreview.viewerScale();g.pose().pushPose();g.pose().scale(s,s,1);preview.draw(g,mx/s,my/s);g.pose().popPose();}
                @Override public boolean mouseClicked(int x,int y,int b){float s=CosmicPreview.viewerScale();boolean handled=preview.mouseClicked(x/s,y/s,b);preview.mouseReleased(x/s,y/s,b);return handled;}
                @Override public List<ClientTooltipComponent> getTooltip(int x,int y){float s=CosmicPreview.viewerScale();return preview.tooltip(x/s,y/s).stream().map(c -> ClientTooltipComponent.create(c.getVisualOrderText())).toList();}
            });
        }
    }
}
