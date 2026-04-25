package com.ionhex975.vulkanpostfx.client.shadow;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxCapabilityResolver;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxRuntimeCapabilities;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;

public final class ShadowDepthPassLite {
    private static Boolean lastLoggedCastersRendered;

    private ShadowDepthPassLite() {
    }

    public static void execute(Minecraft minecraft, LevelRenderer levelRenderer) {
        RenderSystem.assertOnRenderThread();

        VpfxRuntimeCapabilities caps = new VpfxCapabilityResolver().resolve();
        if (!caps.isShadowDepth()) {
            return;
        }

        ShadowFrameState state = ShadowFrameState.get();
        ShadowRenderTargetsLite targets = ShadowRenderTargetsLite.get();

        if (!state.isValid() || !state.isShadowPassEnabled() || !state.isShadowTargetReady() || !targets.isReady()) {
            return;
        }

        if (!state.consumeShadowRenderRequest()) {
            return;
        }

        RenderTarget target = targets.getShadowDepthTarget();
        if (target == null) {
            return;
        }

        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

            if (target.getColorTexture() != null && target.getDepthTexture() != null) {
                encoder.clearColorAndDepthTextures(
                        target.getColorTexture(),
                        0,
                        target.getDepthTexture(),
                        1.0
                );
            } else if (target.getDepthTexture() != null) {
                encoder.clearDepthTexture(target.getDepthTexture(), 1.0);
            } else {
                throw new IllegalStateException("Shadow target has no depth texture");
            }

            boolean terrainRendered = ShadowTerrainPassLite.execute(
                    minecraft,
                    levelRenderer,
                    state,
                    target
            );

            int entitySubmitted = ShadowEntityPassLite.execute(
                    minecraft,
                    levelRenderer,
                    state,
                    target
            );

            boolean castersRendered = terrainRendered || entitySubmitted > 0;

            state.markShadowPassExecuted(castersRendered);

            VulkanPostFX.LOGGER.info(
                    "[{}] Shadow depth pass executed: shadowMapSize={}, terrainShadowDistance={}, entityShadowDistance={}, terrainRendered={}, entitySubmitted={}, castersRendered={}",
                    VulkanPostFX.MOD_ID,
                    state.getShadowMapSize(),
                    state.getTerrainShadowDistance(),
                    state.getEntityShadowDistance(),
                    terrainRendered,
                    entitySubmitted,
                    castersRendered
            );
        } catch (Throwable t) {
            VulkanPostFX.LOGGER.error(
                    "[{}] Shadow depth pass execution failed",
                    VulkanPostFX.MOD_ID,
                    t
            );
        }
    }
}