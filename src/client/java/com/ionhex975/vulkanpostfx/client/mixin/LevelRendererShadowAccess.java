package com.ionhex975.vulkanpostfx.client.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface LevelRendererShadowAccess {
    @Accessor("entityRenderDispatcher")
    EntityRenderDispatcher vulkanpostfx$getEntityRenderDispatcher();

    @Accessor("renderBuffers")
    RenderBuffers vulkanpostfx$getRenderBuffers();

    @Accessor("featureRenderDispatcher")
    FeatureRenderDispatcher vulkanpostfx$getFeatureRenderDispatcher();

    @Accessor("levelRenderState")
    LevelRenderState vulkanpostfx$getLevelRenderState();
}