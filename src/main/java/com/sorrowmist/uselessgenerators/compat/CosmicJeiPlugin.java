package com.sorrowmist.uselessgenerators.compat;

import com.sorrowmist.uselessgenerators.*;
import com.sorrowmist.uselessgenerators.client.CosmicPreview;
import mezz.jei.api.*;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiGuiEventListener;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.List;

@JeiPlugin
public final class CosmicJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID=new ResourceLocation(UselessGenerators.ID,"cosmic_structure");
    private static final RecipeType<CosmicPreview> TYPE=new RecipeType<>(ID,CosmicPreview.class);
    @Override public ResourceLocation getPluginUid() {return ID;}
    @Override public void registerCategories(IRecipeCategoryRegistration registration) {registration.addRecipeCategories(new Category(registration.getJeiHelpers().getGuiHelper()));}
    @Override public void registerRecipes(IRecipeRegistration registration) {registration.addRecipes(TYPE,List.of(new CosmicPreview()));}
    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(com.sorrowmist.uselessgenerators.client.MeHatchScreen.class,new HatchGhostHandler());
    }
    private static final class HatchGhostHandler implements mezz.jei.api.gui.handlers.IGhostIngredientHandler<com.sorrowmist.uselessgenerators.client.MeHatchScreen> {
        @Override public <I> List<Target<I>> getTargetsTyped(com.sorrowmist.uselessgenerators.client.MeHatchScreen screen,
                mezz.jei.api.ingredients.ITypedIngredient<I> ingredient,boolean start) {
            var kind=screen.getMenu().kind();
            if(!kind.input())return List.of();
            appeng.api.stacks.AEKey key=null;
            Object value=ingredient.getIngredient();
            if(kind==MeHatchKind.ITEM_INPUT && value instanceof ItemStack stack && !stack.isEmpty())
                key=appeng.api.stacks.AEItemKey.of(stack);
            else if(kind==MeHatchKind.FLUID_INPUT) {
                net.minecraftforge.fluids.FluidStack fluid=value instanceof net.minecraftforge.fluids.FluidStack f ? f
                        : value instanceof ItemStack stack ? net.minecraftforge.fluids.FluidUtil.getFluidContained(stack).orElse(net.minecraftforge.fluids.FluidStack.EMPTY)
                        : net.minecraftforge.fluids.FluidStack.EMPTY;
                if(!fluid.isEmpty())key=appeng.api.stacks.AEFluidKey.of(fluid);
            }
            if(key==null)return List.of();
            final var mark=key;
            var targets=new java.util.ArrayList<Target<I>>(16);
            for(int i=0;i<16;i++) {
                final int slot=i;
                targets.add(new Target<>() {
                    public net.minecraft.client.renderer.Rect2i getArea(){return screen.markScreenArea(slot);}
                    public void accept(I value){screen.acceptMark(slot,mark);}
                });
            }
            return targets;
        }
        @Override public void onComplete() {}
    }
    @Override public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new ConstructionTransfer<>(appeng.menu.me.items.PatternEncodingTermMenu.class,
                appeng.menu.me.items.PatternEncodingTermMenu.TYPE),TYPE);
        for(String name:List.of("de.mari_023.ae2wtlib.wet.WETMenu",
                "com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu",
                "com.moakiee.ae2lt.menu.TianshuWirelessPatternEncodingTermMenu")) registerTerminal(registration,name);
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void registerTerminal(IRecipeTransferRegistration registration,String name) {
        try {
            Class<?> menu=Class.forName(name);
            if(appeng.menu.me.items.PatternEncodingTermMenu.class.isAssignableFrom(menu))
                registration.addRecipeTransferHandler(new ConstructionTransfer((Class)menu,
                        (net.minecraft.world.inventory.MenuType)menu.getField("TYPE").get(null)),TYPE);
        } catch(ClassNotFoundException absent) {
            // Optional terminal mod is not installed.
        } catch(ReflectiveOperationException | ClassCastException exception) {
            com.mojang.logging.LogUtils.getLogger().warn("Cannot register construction pattern transfer for {}",name,exception);
        }
    }
    private record ConstructionTransfer<T extends appeng.menu.me.items.PatternEncodingTermMenu>(
            Class<T> menuClass,net.minecraft.world.inventory.MenuType<T> menuType)
            implements mezz.jei.api.recipe.transfer.IRecipeTransferHandler<T,CosmicPreview> {
        public Class<? extends T> getContainerClass(){return menuClass;}
        public java.util.Optional<net.minecraft.world.inventory.MenuType<T>> getMenuType(){return java.util.Optional.of(menuType);}
        public RecipeType<CosmicPreview> getRecipeType(){return TYPE;}
        public mezz.jei.api.recipe.transfer.IRecipeTransferError transferRecipe(T menu,CosmicPreview recipe,
                IRecipeSlotsView slots,net.minecraft.world.entity.player.Player player,boolean max,boolean transfer) {
            if(transfer)MeHatchNetwork.encodeConstruction(menu.containerId);
            return null;
        }
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(UselessGenerators.COSMIC_HEART.get()),TYPE);
    }
    private static final class Category implements IRecipeCategory<CosmicPreview> {
        private final IDrawable icon;
        Category(IGuiHelper helper) {icon=helper.createDrawableItemStack(new ItemStack(UselessGenerators.COSMIC_HEART.get()));}
        @Override public RecipeType<CosmicPreview> getRecipeType(){return TYPE;}
        @Override public Component getTitle(){return Component.translatable("block.useless_generators.cosmic_heart");}
        @Override public IDrawable getIcon(){return icon;}
        @Override @SuppressWarnings("removal") public IDrawable getBackground(){return new IDrawable(){public int getWidth(){return CosmicPreview.viewerWidth();} public int getHeight(){return CosmicPreview.viewerHeight();} public void draw(GuiGraphics g,int x,int y){}};}
        @Override public int getWidth(){return CosmicPreview.viewerWidth();}
        @Override public int getHeight(){return CosmicPreview.viewerHeight();}
        @Override public ResourceLocation getRegistryName(CosmicPreview recipe){return ID;}
        @Override public void setRecipe(IRecipeLayoutBuilder builder,CosmicPreview preview,IFocusGroup focus) {
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStacks(CosmicMaterials.all().stream().map(CosmicMaterials.Material::stack).toList());
            builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).addItemStack(new ItemStack(UselessGenerators.COSMIC_HEART.get()));
        }
        @Override public void createRecipeExtras(IRecipeExtrasBuilder builder,CosmicPreview preview,IFocusGroup focus) {
            builder.addGuiEventListener(new IJeiGuiEventListener() {
                public ScreenRectangle getArea(){return new ScreenRectangle(0,0,getWidth(),getHeight());}
                public boolean mouseClicked(double x,double y,int b){float s=CosmicPreview.viewerScale();return preview.mouseClicked(x/s,y/s,b);}
                public boolean mouseReleased(double x,double y,int b){float s=CosmicPreview.viewerScale();return preview.mouseReleased(x/s,y/s,b);}
                public boolean mouseDragged(double x,double y,int b,double dx,double dy){float s=CosmicPreview.viewerScale();return preview.mouseDragged(x/s,y/s,b,dx/s,dy/s);}
                public boolean mouseScrolled(double x,double y,double delta){float s=CosmicPreview.viewerScale();return preview.mouseScrolled(x/s,y/s,delta);}
            });
        }
        @Override public void draw(CosmicPreview preview,IRecipeSlotsView slots,GuiGraphics g,double mx,double my){float s=CosmicPreview.viewerScale();g.pose().pushPose();g.pose().scale(s,s,1);preview.draw(g,mx/s,my/s);g.pose().popPose();}
        @Override public List<Component> getTooltipStrings(CosmicPreview preview,IRecipeSlotsView slots,double mx,double my){float s=CosmicPreview.viewerScale();return preview.tooltip(mx/s,my/s);}
    }
}
