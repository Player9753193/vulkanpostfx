package com.ionhex975.vulkanpostfx.client.postfx;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;

import java.util.Set;

public final class PostFxExternalTargetIds {
    public static final Identifier SHADOW_DEPTH =
            Identifier.tryParse(VulkanPostFX.MOD_ID + ":shadow_depth");

    /**
     * 当前对外允许的 external targets。
     *
     * Shadow Debug View v1：
     * - minecraft:main
     * - vulkanpostfx:shadow_depth
     */
    private static final Set<Identifier> ALLOWED = Set.of(
            PostChain.MAIN_TARGET_ID,
            SHADOW_DEPTH
    );

    private PostFxExternalTargetIds() {
    }

    public static Set<Identifier> allowedTargets() {
        return ALLOWED;
    }
}