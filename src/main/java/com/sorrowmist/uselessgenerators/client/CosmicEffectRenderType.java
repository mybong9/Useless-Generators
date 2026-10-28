package com.sorrowmist.uselessgenerators.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Untextured, depth-tested alpha geometry; independent of the lightning weather pass. */
final class CosmicEffectRenderType extends RenderType {
    static final RenderType SKY = create("useless_generators_universe_sky", DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS, 262144, false, true, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(GameRenderer::getPositionTexColorShader))
                    .setTextureState(new TextureStateShard(new ResourceLocation("useless_generators", "textures/effect/universe_sky.png"), true, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));
    static final RenderType UNIVERSE = create("useless_generators_universe", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 524288, false, true, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(GameRenderer::getPositionColorShader))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private CosmicEffectRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                                  boolean crumbling, boolean sorted, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sorted, setup, clear);
    }
}
