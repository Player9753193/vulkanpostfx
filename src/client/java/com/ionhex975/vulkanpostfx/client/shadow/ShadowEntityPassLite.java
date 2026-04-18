package com.ionhex975.vulkanpostfx.client.shadow;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.mixin.LevelRendererShadowAccess;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

public final class ShadowEntityPassLite {
    private static final ProjectionMatrixBuffer SHADOW_PROJECTION =
            new ProjectionMatrixBuffer("vpfx_shadow_entity");

    private static SkipReason lastSkipReason;
    private static boolean firstEntitySuccessLogged;

    private ShadowEntityPassLite() {
    }

    public static int execute(
            Minecraft minecraft,
            LevelRenderer levelRenderer,
            ShadowFrameState shadowState,
            RenderTarget shadowTarget
    ) {
        RenderSystem.assertOnRenderThread();

        if (minecraft.level == null) {
            logSkip(SkipReason.NO_LEVEL);
            return 0;
        }

        if (levelRenderer == null) {
            logSkip(SkipReason.NO_LEVEL_RENDERER);
            return 0;
        }

        if (shadowTarget == null) {
            logSkip(SkipReason.NO_SHADOW_TARGET);
            return 0;
        }

        LevelRendererShadowAccess access = (LevelRendererShadowAccess) levelRenderer;
        LevelRenderState levelRenderState = access.vulkanpostfx$getLevelRenderState();
        if (levelRenderState == null || levelRenderState.entityRenderStates.isEmpty()) {
            logSkip(SkipReason.NO_ENTITY_RENDER_STATES);
            return 0;
        }

        EntityRenderDispatcher entityRenderDispatcher = access.vulkanpostfx$getEntityRenderDispatcher();
        FeatureRenderDispatcher featureRenderDispatcher = access.vulkanpostfx$getFeatureRenderDispatcher();
        RenderBuffers renderBuffers = access.vulkanpostfx$getRenderBuffers();
        SubmitNodeStorage submitNodeStorage = featureRenderDispatcher.getSubmitNodeStorage();

        submitNodeStorage.clear();

        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(shadowState.getShadowViewMatrix());

        Vec3 cameraPos = shadowState.getCameraPos();
        double camX = cameraPos.x;
        double camY = cameraPos.y;
        double camZ = cameraPos.z;

        double entityShadowDistanceSq = shadowState.getEntityShadowDistance();
        entityShadowDistanceSq *= entityShadowDistanceSq;

        int submittedCount = 0;

        for (EntityRenderState renderState : levelRenderState.entityRenderStates) {
            if (renderState == null || renderState.entityType == null) {
                continue;
            }

            if (renderState.isInvisible) {
                continue;
            }

            boolean isPlayer = renderState.entityType == EntityType.PLAYER;
            if (isPlayer) {
                if (!shadowState.isShadowPlayer()) {
                    continue;
                }
            } else {
                if (!shadowState.isShadowEntities()) {
                    continue;
                }
            }

            if (renderState.distanceToCameraSq > entityShadowDistanceSq) {
                continue;
            }

            entityRenderDispatcher.submit(
                    renderState,
                    levelRenderState.cameraRenderState,
                    renderState.x - camX,
                    renderState.y - camY,
                    renderState.z - camZ,
                    poseStack,
                    submitNodeStorage
            );
            submittedCount++;
        }

        if (submittedCount == 0) {
            submitNodeStorage.clear();
            logSkip(SkipReason.NO_SUBMITTED_ENTITIES);
            return 0;
        }

        RenderSystem.outputColorTextureOverride = shadowTarget.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = shadowTarget.getDepthTextureView();

        RenderSystem.backupProjectionMatrix();
        try {
            GpuBufferSlice projectionSlice = SHADOW_PROJECTION.getBuffer(
                    shadowState.getShadowProjectionMatrix()
            );
            RenderSystem.setProjectionMatrix(projectionSlice, ProjectionType.ORTHOGRAPHIC);

            // Entity shadow v1：只跑 solid feature。
            // 不跑 translucent feature，避免把 shadowFeature/nameTag/text 等透明阶段内容带进 shadow pass。
            featureRenderDispatcher.renderSolidFeatures();

            renderBuffers.bufferSource().endBatch();
            renderBuffers.crumblingBufferSource().endBatch();
            renderBuffers.outlineBufferSource().endOutlineBatch();

            lastSkipReason = null;

            if (!firstEntitySuccessLogged) {
                firstEntitySuccessLogged = true;
                VulkanPostFX.LOGGER.info(
                        "[{}] Shadow entity pass submitted entity caster draws successfully: submittedCount={}",
                        VulkanPostFX.MOD_ID,
                        submittedCount
                );
            }

            return submittedCount;
        } catch (Throwable t) {
            VulkanPostFX.LOGGER.error(
                    "[{}] Shadow entity pass failed during submission",
                    VulkanPostFX.MOD_ID,
                    t
            );
            return 0;
        } finally {
            featureRenderDispatcher.clearSubmitNodes();
            RenderSystem.outputColorTextureOverride = null;
            RenderSystem.outputDepthTextureOverride = null;
            RenderSystem.restoreProjectionMatrix();
        }
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
        NO_LEVEL,
        NO_LEVEL_RENDERER,
        NO_SHADOW_TARGET,
        NO_ENTITY_RENDER_STATES,
        NO_SUBMITTED_ENTITIES
    }
}