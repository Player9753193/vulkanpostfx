package com.ionhex975.vulkanpostfx.client.runtime;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.effect.PostFxEffectRegistry;
import com.ionhex975.vulkanpostfx.client.pack.ActiveShaderPackManager;
import com.ionhex975.vulkanpostfx.client.pack.ShaderPackContainer;
import com.ionhex975.vulkanpostfx.client.pack.ZipShaderPackReader;
import com.ionhex975.vulkanpostfx.client.runtime.posteffect.ZipPostEffectConfig;
import com.ionhex975.vulkanpostfx.client.runtime.posteffect.ZipPostEffectParser;
import com.ionhex975.vulkanpostfx.client.runtime.posteffect.ZipShaderReferenceValidationResult;
import com.ionhex975.vulkanpostfx.client.runtime.posteffect.ZipShaderReferenceValidator;
import com.ionhex975.vulkanpostfx.client.runtime.zip.RuntimeZipPackMaterializationResult;
import com.ionhex975.vulkanpostfx.client.runtime.zip.RuntimeZipPackState;
import com.ionhex975.vulkanpostfx.client.runtime.zip.ZipPackMaterializer;
import com.ionhex975.vulkanpostfx.client.state.PostFxRuntimeState;
import net.minecraft.client.Minecraft;

public final class ActivePostEffectBridge {
    private static ActivePostEffectSource activeSource = ActivePostEffectSource.NONE;

    private ActivePostEffectBridge() {
    }

    public static void refreshFromActivePack() {
        ShaderPackContainer activePack = ActiveShaderPackManager.getActivePack();
        if (activePack == null) {
            activeSource = ActivePostEffectSource.NONE;
            RuntimeZipPackState.clear();
            PostFxRuntimeState.clearActiveExternalPostEffectId();
            PostFxRuntimeState.setActiveEffectKey(PostFxEffectRegistry.DEBUG_INVERT);

            VulkanPostFX.LOGGER.warn("[{}] No active shader pack; active post effect source cleared", VulkanPostFX.MOD_ID);
            return;
        }

        String entryPostEffect = activePack.manifest().entryPostEffect();
        if (entryPostEffect == null || entryPostEffect.isBlank()) {
            activeSource = ActivePostEffectSource.NONE;
            RuntimeZipPackState.clear();
            PostFxRuntimeState.clearActiveExternalPostEffectId();
            PostFxRuntimeState.setActiveEffectKey(PostFxEffectRegistry.DEBUG_INVERT);

            VulkanPostFX.LOGGER.warn(
                    "[{}] Active shader pack '{}' does not declare entry_post_effect",
                    VulkanPostFX.MOD_ID,
                    activePack.manifest().name()
            );
            return;
        }

        if ("builtin".equals(activePack.sourceId())) {
            activeSource = new ActivePostEffectSource(
                    "builtin",
                    entryPostEffect,
                    "",
                    null,
                    null
            );

            RuntimeZipPackState.clear();
            PostFxRuntimeState.clearActiveExternalPostEffectId();
            PostFxRuntimeState.setActiveEffectKey(ActiveShaderPackManager.getActiveEffectKey());

            VulkanPostFX.LOGGER.info(
                    "[{}] Active post effect source prepared from builtin pack: {}, resolvedBuiltinEffectKey={}",
                    VulkanPostFX.MOD_ID,
                    entryPostEffect,
                    PostFxRuntimeState.getActiveEffectKey()
            );
            return;
        }

        if ("zip".equals(activePack.sourceId())) {
            try {
                String rawJson = ZipShaderPackReader.readText(activePack.sourcePath(), entryPostEffect);
                ZipPostEffectConfig parsedConfig = ZipPostEffectParser.parse(rawJson);
                ZipShaderReferenceValidationResult validationResult =
                        ZipShaderReferenceValidator.validate(activePack, parsedConfig);

                if (!validationResult.isValid()) {
                    activeSource = ActivePostEffectSource.NONE;
                    RuntimeZipPackState.clear();
                    PostFxRuntimeState.clearActiveExternalPostEffectId();
                    PostFxRuntimeState.setActiveEffectKey(PostFxEffectRegistry.DEBUG_INVERT);

                    VulkanPostFX.LOGGER.error(
                            "[{}] Active ZIP post effect source failed shader validation: checked={}, missing={}",
                            VulkanPostFX.MOD_ID,
                            validationResult.checkedCount(),
                            validationResult.missingReferences()
                    );
                    return;
                }

                RuntimeZipPackMaterializationResult materialized = ZipPackMaterializer.materialize(
                        activePack,
                        Minecraft.getInstance().gameDirectory.toPath()
                );

                RuntimeZipPackState.apply(materialized);
                PostFxRuntimeState.setActiveExternalPostEffectId(materialized.externalPostEffectId());
                PostFxRuntimeState.setActiveEffectKey(PostFxEffectRegistry.DEBUG_INVERT);

                activeSource = new ActivePostEffectSource(
                        "zip",
                        activePack.sourcePath() + "!/" + entryPostEffect,
                        rawJson,
                        parsedConfig,
                        validationResult
                );

                VulkanPostFX.LOGGER.info(
                        "[{}] Active post effect source loaded from zip: {} ({} chars, {} targets, {} passes, checkedShaders={}, runtimeNamespace={}, externalPostEffectId={})",
                        VulkanPostFX.MOD_ID,
                        activeSource.displayPath(),
                        rawJson.length(),
                        parsedConfig.targets().size(),
                        parsedConfig.passes().size(),
                        validationResult.checkedCount(),
                        materialized.runtimeNamespace(),
                        materialized.externalPostEffectId()
                );
                return;
            } catch (Exception e) {
                activeSource = ActivePostEffectSource.NONE;
                RuntimeZipPackState.clear();
                PostFxRuntimeState.clearActiveExternalPostEffectId();
                PostFxRuntimeState.setActiveEffectKey(PostFxEffectRegistry.DEBUG_INVERT);

                VulkanPostFX.LOGGER.error(
                        "[{}] Failed to load active ZIP post effect source from '{}'",
                        VulkanPostFX.MOD_ID,
                        entryPostEffect,
                        e
                );
                return;
            }
        }

        activeSource = ActivePostEffectSource.NONE;
        RuntimeZipPackState.clear();
        PostFxRuntimeState.clearActiveExternalPostEffectId();
        PostFxRuntimeState.setActiveEffectKey(PostFxEffectRegistry.DEBUG_INVERT);

        VulkanPostFX.LOGGER.warn(
                "[{}] Unsupported shader pack source '{}'; active post effect source cleared",
                VulkanPostFX.MOD_ID,
                activePack.sourceId()
        );
    }

    public static ActivePostEffectSource getActiveSource() {
        return activeSource;
    }
}