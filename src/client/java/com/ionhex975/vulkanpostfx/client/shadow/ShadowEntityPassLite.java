package com.ionhex975.vulkanpostfx.client.shadow;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;

public final class ShadowEntityPassLite {
    private static SkipReason lastSkipReason;

    private ShadowEntityPassLite() {
    }

    public static int execute(
            Minecraft minecraft,
            LevelRenderer levelRenderer,
            ShadowFrameState shadowState,
            RenderTarget shadowTarget
    ) {
        RenderSystem.assertOnRenderThread();

        logSkip(SkipReason.TEMP_DISABLED_FOR_TERRAIN_BRINGUP);
        return 0;
    }

    private static void logSkip(SkipReason reason) {
        if (lastSkipReason != reason) {
            lastSkipReason = reason;
            VulkanPostFX.LOGGER.info(
                    "[{}] Shadow entity pass skipped: {}",
                    VulkanPostFX.MOD_ID,
                    reason
            );
        }
    }

    private enum SkipReason {
        TEMP_DISABLED_FOR_TERRAIN_BRINGUP
    }
}