package com.ionhex975.vulkanpostfx.client.hook;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.effect.PostFxEffectDefinition;
import com.ionhex975.vulkanpostfx.client.effect.PostFxEffectRegistry;
import com.ionhex975.vulkanpostfx.client.mixin.GameRendererAccessor;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;

public final class PostFxHookBridge {
    private static boolean firstWorldFrameLogged;
    private static boolean firstWorldFrameFinishedLogged;
    private static boolean firstPostSlotLogged;
    private static Boolean lastAppliedDebugState;

    private PostFxHookBridge() {
    }

    public static void onWorldRenderHead(Minecraft minecraft, boolean renderOutline, boolean shouldRenderSky) {
        PostFxRuntimeState.markWorldRenderObserved();

        if (!firstWorldFrameLogged) {
            firstWorldFrameLogged = true;

            String backend = detectBackendName();
            PostFxRuntimeState.setBackendName(backend);

            RenderTarget mainTarget = minecraft.getMainRenderTarget();
            int width = mainTarget.width;
            int height = mainTarget.height;
            boolean improvedTransparency = minecraft.options.improvedTransparency().get();

            VulkanPostFX.LOGGER.info(
                    "[{}] World render observed (HEAD), backend={}, size={}x{}, improvedTransparency={}, renderOutline={}, shouldRenderSky={}",
                    VulkanPostFX.MOD_ID,
                    backend,
                    width,
                    height,
                    improvedTransparency,
                    renderOutline,
                    shouldRenderSky
            );
        }
    }

    public static void onWorldRenderTail(Minecraft minecraft) {
        if (!firstWorldFrameFinishedLogged) {
            firstWorldFrameFinishedLogged = true;

            RenderTarget mainTarget = minecraft.getMainRenderTarget();
            VulkanPostFX.LOGGER.info(
                    "[{}] World render finished (TAIL), mainTarget={}x{}",
                    VulkanPostFX.MOD_ID,
                    mainTarget.width,
                    mainTarget.height
            );
        }
    }

    public static void onPostEffectSlot(Minecraft minecraft, GameRenderer gameRenderer) {
        PostFxRuntimeState.markPostSlotObserved();

        if (!firstPostSlotLogged) {
            firstPostSlotLogged = true;

            RenderTarget mainTarget = minecraft.getMainRenderTarget();
            Identifier currentPostEffect = gameRenderer.currentPostEffect();

            VulkanPostFX.LOGGER.info(
                    "[{}] PostFX slot observed, backend={}, mainTarget={}x{}, currentPostEffect={}",
                    VulkanPostFX.MOD_ID,
                    PostFxRuntimeState.getBackendName(),
                    mainTarget.width,
                    mainTarget.height,
                    currentPostEffect == null ? "none" : currentPostEffect
            );
        }

        applyDesiredDebugEffect(minecraft, gameRenderer);
    }

    private static void applyDesiredDebugEffect(Minecraft minecraft, GameRenderer gameRenderer) {
        boolean desiredEnabled = PostFxRuntimeState.isDebugEffectEnabled();
        boolean reapplyRequested = PostFxRuntimeState.consumeReapplyRequest();

        if (!reapplyRequested && lastAppliedDebugState != null && lastAppliedDebugState == desiredEnabled) {
            return;
        }

        GameRendererAccessor accessor = (GameRendererAccessor) gameRenderer;

        if (!desiredEnabled) {
            accessor.vulkanpostfx$setPostEffectId(null);
            accessor.vulkanpostfx$setEffectActive(false);
            lastAppliedDebugState = false;

            if (reapplyRequested) {
                VulkanPostFX.LOGGER.info(
                        "[{}] Reapplied PostFX state after resource reload: disabled",
                        VulkanPostFX.MOD_ID
                );
            } else {
                VulkanPostFX.LOGGER.info("[{}] Debug post effect disabled", VulkanPostFX.MOD_ID);
            }
            return;
        }

        Identifier chosenEffect = chooseCurrentEffect(minecraft);
        accessor.vulkanpostfx$setPostEffectId(chosenEffect);
        accessor.vulkanpostfx$setEffectActive(true);
        lastAppliedDebugState = true;

        if (reapplyRequested) {
            VulkanPostFX.LOGGER.info(
                    "[{}] Reapplied PostFX state after resource reload: {}",
                    VulkanPostFX.MOD_ID,
                    chosenEffect
            );
        } else {
            VulkanPostFX.LOGGER.info(
                    "[{}] Debug post effect enabled: {}",
                    VulkanPostFX.MOD_ID,
                    chosenEffect
            );
        }
    }

    private static Identifier chooseCurrentEffect(Minecraft minecraft) {
        Identifier externalId = PostFxRuntimeState.getActiveExternalPostEffectId();

        VulkanPostFX.LOGGER.info(
                "[{}] chooseCurrentEffect: activeExternalPostEffectId={}, activeEffectKey={}",
                VulkanPostFX.MOD_ID,
                externalId,
                PostFxRuntimeState.getActiveEffectKey()
        );

        if (externalId != null) {
            PostChain external = minecraft.getShaderManager().getPostChain(externalId, LevelTargetBundle.MAIN_TARGETS);

            VulkanPostFX.LOGGER.info(
                    "[{}] External post chain lookup: id={}, found={}",
                    VulkanPostFX.MOD_ID,
                    externalId,
                    external != null
            );

            if (external != null) {
                VulkanPostFX.LOGGER.info(
                        "[{}] External ZIP post chain is available: {}",
                        VulkanPostFX.MOD_ID,
                        externalId
                );
                return externalId;
            }

            VulkanPostFX.LOGGER.warn(
                    "[{}] External ZIP post chain is NOT available, falling back to builtin chain: {}",
                    VulkanPostFX.MOD_ID,
                    externalId
            );
        }

        String effectKey = PostFxRuntimeState.getActiveEffectKey();
        PostFxEffectDefinition definition = PostFxEffectRegistry.get(effectKey);

        if (definition == null) {
            VulkanPostFX.LOGGER.warn(
                    "[{}] Effect key '{}' is not registered, falling back to minecraft:invert",
                    VulkanPostFX.MOD_ID,
                    effectKey
            );
            return Identifier.withDefaultNamespace("invert");
        }

        PostChain custom = minecraft.getShaderManager().getPostChain(definition.primaryId(), LevelTargetBundle.MAIN_TARGETS);

        VulkanPostFX.LOGGER.info(
                "[{}] Builtin post chain lookup: effectKey={}, id={}, found={}",
                VulkanPostFX.MOD_ID,
                effectKey,
                definition.primaryId(),
                custom != null
        );

        if (custom != null) {
            VulkanPostFX.LOGGER.info(
                    "[{}] Builtin effect '{}' is available: {}",
                    VulkanPostFX.MOD_ID,
                    definition.displayName(),
                    definition.primaryId()
            );
            return definition.primaryId();
        }

        VulkanPostFX.LOGGER.warn(
                "[{}] Builtin effect '{}' failed to load primary chain {}, falling back to {}",
                VulkanPostFX.MOD_ID,
                definition.displayName(),
                definition.primaryId(),
                definition.fallbackId()
        );
        return definition.fallbackId();
    }

    private static String detectBackendName() {
        try {
            GpuDevice device = RenderSystem.tryGetDevice();
            if (device == null) {
                return "device-not-ready";
            }

            return device.getDeviceInfo().backendName();
        } catch (Throwable t) {
            return "unresolved";
        }
    }
}