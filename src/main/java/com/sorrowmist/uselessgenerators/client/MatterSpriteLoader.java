package com.sorrowmist.uselessgenerators.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteTicker;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.client.textures.ForgeTextureMetadata;
import net.minecraftforge.client.textures.ITextureAtlasSpriteLoader;

/** Scroll the original pixels in the atlas: no replacement art, extra image buffers or per-block tickers. */
public final class MatterSpriteLoader implements ITextureAtlasSpriteLoader {
    @Override public SpriteContents loadContents(ResourceLocation name, Resource resource, FrameSize size,
            NativeImage image, AnimationMetadataSection animation, ForgeTextureMetadata metadata) {
        return new Contents(name, size, image, animation, metadata);
    }
    @Override public TextureAtlasSprite makeSprite(ResourceLocation atlas, SpriteContents contents,
            int atlasWidth, int atlasHeight, int x, int y, int mipLevel) {
        return new Sprite(atlas, contents, atlasWidth, atlasHeight, x, y);
    }
    private static final class Sprite extends TextureAtlasSprite {
        Sprite(ResourceLocation atlas, SpriteContents contents, int width, int height, int x, int y) {
            super(atlas, contents, width, height, x, y);
        }
    }
    private static final class Contents extends SpriteContents {
        Contents(ResourceLocation name, FrameSize size, NativeImage image,
                AnimationMetadataSection animation, ForgeTextureMetadata metadata) {
            super(name, size, image, animation, metadata);
        }
        @Override public SpriteTicker createTicker() {
            // A resource pack can still supply a conventional animated strip.
            var original = super.createTicker();
            if (original != null) return original;
            boolean flowing = name().getPath().endsWith("_flow");
            return new SpriteTicker() {
                private int ticks;
                @Override public void tickAndUpload(int atlasX, int atlasY) {
                    ticks++;
                    int interval = flowing ? 4 : 8;
                    if (ticks % interval != 0) return;
                    int phase = ticks / interval;
                    for (int mip = 0; mip < byMipLevel.length; mip++) {
                        int width = Math.max(1, width() >> mip), height = Math.max(1, height() >> mip);
                        int shift = Math.floorMod(-(phase >> mip), height);
                        var image = byMipLevel[mip];
                        // Two non-overlapping rectangles, always inside this sprite at every mip.
                        image.upload(mip, atlasX >> mip, atlasY >> mip, 0, shift, width, height - shift,
                                byMipLevel.length > 1, false);
                        if (shift > 0) image.upload(mip, atlasX >> mip, (atlasY >> mip) + height - shift,
                                0, 0, width, shift, byMipLevel.length > 1, false);
                    }
                    // Avoid overflow while retaining continuity at every power-of-two mip size.
                    if (ticks >= height() * 1024) ticks = 0;
                }
                @Override public void close() { /* SpriteContents owns the original images. */ }
            };
        }
    }
}
