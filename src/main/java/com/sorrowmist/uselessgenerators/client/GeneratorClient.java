package com.sorrowmist.uselessgenerators.client;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.sorrowmist.uselessgenerators.UselessGenerators.ID,
        bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.MOD, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class GeneratorClient {
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void spriteLoaders(net.minecraftforge.client.event.RegisterTextureAtlasSpriteLoadersEvent event) {
        event.register("matter_flow", new MatterSpriteLoader());
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (var fuel : com.sorrowmist.uselessgenerators.GeneratorFuels.FUELS) {
                net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(fuel.source.get(), net.minecraft.client.renderer.RenderType.translucent());
                net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(fuel.flowing.get(), net.minecraft.client.renderer.RenderType.translucent());
            }
            net.minecraft.client.gui.screens.MenuScreens.register(com.sorrowmist.uselessgenerators.UselessGenerators.MENU.get(), GeneratorScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(com.sorrowmist.uselessgenerators.UselessGenerators.COSMIC_MENU.get(), CosmicHeartScreen::new);
            net.minecraft.client.gui.screens.MenuScreens.register(com.sorrowmist.uselessgenerators.UselessGenerators.HATCH_MENU.get(), MeHatchScreen::new);
        });
    }
    private GeneratorClient() {}
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void renderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(com.sorrowmist.uselessgenerators.UselessGenerators.GENERATOR.get(), CosmicHeartRenderer::new);
    }
}
