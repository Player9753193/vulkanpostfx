package com.ionhex975.vulkanpostfx.client.postfx;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxCapabilityResolver;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxRuntimeCapabilities;
import com.ionhex975.vulkanpostfx.client.shadow.ShadowRenderTargetsLite;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.renderer.PostChain;

public final class PostFxExternalTargetRunner {
    private static boolean firstShadowTargetBoundLogged;
    private static boolean firstShadowFallbackLogged;

    private PostFxExternalTargetRunner() {
    }

    public static void process(
            PostChain chain,
            RenderTarget mainTarget,
            GraphicsResourceAllocator resourceAllocator
    ) {
        FrameGraphBuilder frame = new FrameGraphBuilder();
        MutableTargetBundle bundle = new MutableTargetBundle();

        bundle.put(
                PostChain.MAIN_TARGET_ID,
                frame.importExternal("main", mainTarget)
        );

        VpfxRuntimeCapabilities caps =
                new VpfxCapabilityResolver().resolve();

        if (caps.isShadowDepth()) {
            ShadowRenderTargetsLite shadowTargets = ShadowRenderTargetsLite.get();
            RenderTarget shadowDepthTarget = shadowTargets.getShadowDepthTarget();

            if (shadowTargets.isReady() && shadowDepthTarget != null) {
                bundle.put(
                        PostFxExternalTargetIds.SHADOW_DEPTH,
                        frame.importExternal("shadow_depth", shadowDepthTarget)
                );

                if (!firstShadowTargetBoundLogged) {
                    firstShadowTargetBoundLogged = true;
                    VulkanPostFX.LOGGER.info(
                            "[{}] Added runtime shadow target to PostChain.process bundle: id={}, size={}x{}",
                            VulkanPostFX.MOD_ID,
                            PostFxExternalTargetIds.SHADOW_DEPTH,
                            shadowDepthTarget.width,
                            shadowDepthTarget.height
                    );
                }
            } else {
                // fallback：为了避免 external target 缺失导致 chain 无法运行，
                // 暂时回退到 main target。这样 debug 包至少还能正常执行。
                bundle.put(
                        PostFxExternalTargetIds.SHADOW_DEPTH,
                        frame.importExternal("shadow_depth_fallback_main", mainTarget)
                );

                if (!firstShadowFallbackLogged) {
                    firstShadowFallbackLogged = true;
                    VulkanPostFX.LOGGER.warn(
                            "[{}] Shadow target is not ready during PostChain.process; falling back to main target for external id {}",
                            VulkanPostFX.MOD_ID,
                            PostFxExternalTargetIds.SHADOW_DEPTH
                    );
                }
            }
        }

        chain.addToFrame(frame, mainTarget.width, mainTarget.height, bundle);
        frame.execute(resourceAllocator);
    }
}