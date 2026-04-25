package com.ionhex975.vulkanpostfx.client.shadow;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import org.joml.Matrix4fStack;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public final class ShadowTerrainPassLite {
    private static final ProjectionMatrixBuffer SHADOW_PROJECTION =
            new ProjectionMatrixBuffer("vpfx_shadow");

    private static GpuSampler chunkLayerSampler;

    private static SkipReason lastSkipReason;
    private static boolean firstTerrainSuccessLogged;

    private ShadowTerrainPassLite() {
    }

    public static boolean execute(
            Minecraft minecraft,
            LevelRenderer levelRenderer,
            ShadowFrameState shadowState,
            RenderTarget shadowTarget
    ) {
        RenderSystem.assertOnRenderThread();

        if (minecraft.level == null) {
            logSkip(SkipReason.NO_LEVEL);
            return false;
        }

        if (levelRenderer == null) {
            logSkip(SkipReason.NO_LEVEL_RENDERER);
            return false;
        }

        if (shadowTarget == null) {
            logSkip(SkipReason.NO_SHADOW_TARGET);
            return false;
        }

        /*
         * 注意：
         * 这里仍然沿用 prepareChunkRenders(...) 来拿 chunk draw groups，
         * 但真正 raster 时已经不再使用 vanilla terrain pipeline，
         * 而是改用我们自己的 shadow-only pipelines。
         */
        ChunkSectionsToRender shadowChunks = levelRenderer.prepareChunkRenders(
                shadowState.getShadowViewProjectionMatrix()
        );

        if (!hasShadowRelevantDraws(shadowChunks)) {
            logSkip(SkipReason.NO_RELEVANT_DRAWS);
            return false;
        }

        if (chunkLayerSampler == null) {
            chunkLayerSampler = RenderSystem.getDevice().createSampler(
                    AddressMode.CLAMP_TO_EDGE,
                    AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR,
                    FilterMode.LINEAR,
                    1,
                    OptionalDouble.empty()
            );
        }

        RenderSystem.backupProjectionMatrix();

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.identity();

        try {
            /*
             * 关键点：
             * 自定义 shadow vertex shader 只使用 ProjMat，不使用 ModelViewMat，
             * 这里直接把 shadow VP 塞进 Projection。
             */
            GpuBufferSlice projectionSlice = SHADOW_PROJECTION.getBuffer(
                    shadowState.getShadowViewProjectionMatrix()
            );
            RenderSystem.setProjectionMatrix(projectionSlice, ProjectionType.ORTHOGRAPHIC);

            renderShadowTerrainGroups(
                    minecraft,
                    shadowChunks,
                    chunkLayerSampler,
                    shadowTarget
            );

            lastSkipReason = null;

            if (!firstTerrainSuccessLogged) {
                firstTerrainSuccessLogged = true;
                VulkanPostFX.LOGGER.info(
                        "[{}] Shadow terrain pass submitted custom shadow-only terrain pipelines successfully",
                        VulkanPostFX.MOD_ID
                );
            }

            return true;
        } catch (Throwable t) {
            VulkanPostFX.LOGGER.error(
                    "[{}] Shadow terrain pass failed during custom shadow terrain submission",
                    VulkanPostFX.MOD_ID,
                    t
            );
            return false;
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private static boolean hasShadowRelevantDraws(ChunkSectionsToRender chunkSections) {
        if (chunkSections == null) {
            return false;
        }

        for (ChunkSectionLayer layer : new ChunkSectionLayer[]{ChunkSectionLayer.SOLID, ChunkSectionLayer.CUTOUT}) {
            Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>> drawGroup =
                    chunkSections.drawGroupsPerLayer().get(layer);

            if (drawGroup == null || drawGroup.isEmpty()) {
                continue;
            }

            for (List<RenderPass.Draw<GpuBufferSlice[]>> draws : drawGroup.values()) {
                if (draws != null && !draws.isEmpty()) {
                    return true;
                }
            }
        }

        return false;
    }

    private static void renderShadowTerrainGroups(
            Minecraft minecraft,
            ChunkSectionsToRender chunkSections,
            GpuSampler sampler,
            RenderTarget renderTarget
    ) {
        RenderSystem.AutoStorageIndexBuffer autoIndices =
                RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer defaultIndexBuffer =
                chunkSections.maxIndicesRequired() == 0 ? null : autoIndices.getBuffer(chunkSections.maxIndicesRequired());
        VertexFormat.IndexType defaultIndexType =
                chunkSections.maxIndicesRequired() == 0 ? null : autoIndices.type();

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> "VulkanPostFX Shadow Terrain Custom",
                        renderTarget.getColorTextureView(),
                        OptionalInt.empty(),
                        renderTarget.getDepthTextureView(),
                        /*
                         * 这里恢复为标准 shadow clear：1.0
                         * 之前的 0.0 只是判别实验。
                         */
                        OptionalDouble.of(1.0)
                )) {
            /*
             * 仍然绑定默认 uniforms，让 Projection UBO / ChunkSection UBO 链继续可用。
             * 但由于我们现在用的是自定义 shadow shader，
             * 它不会再吃 vanilla core/terrain 里的 CameraBlockPos / CameraOffset 语义。
             */
            RenderSystem.bindDefaultUniforms(renderPass);

            /*
             * CUTOUT shadow pass 需要采样 block atlas alpha；
             * SOLID 也可以复用这组绑定，不影响。
             */
            renderPass.bindTexture("Sampler0", chunkSections.textureView(), sampler);

            submitLayer(
                    renderPass,
                    chunkSections,
                    ChunkSectionLayer.SOLID,
                    ShadowRenderPipelines.SHADOW_TERRAIN_SOLID,
                    defaultIndexBuffer,
                    defaultIndexType
            );

            submitLayer(
                    renderPass,
                    chunkSections,
                    ChunkSectionLayer.CUTOUT,
                    ShadowRenderPipelines.SHADOW_TERRAIN_CUTOUT,
                    defaultIndexBuffer,
                    defaultIndexType
            );
        }
    }

    private static void submitLayer(
            RenderPass renderPass,
            ChunkSectionsToRender chunkSections,
            ChunkSectionLayer sourceLayer,
            RenderPipeline shadowPipeline,
            GpuBuffer defaultIndexBuffer,
            VertexFormat.IndexType defaultIndexType
    ) {
        Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>> drawGroup =
                chunkSections.drawGroupsPerLayer().get(sourceLayer);

        if (drawGroup == null || drawGroup.isEmpty()) {
            return;
        }

        renderPass.setPipeline(shadowPipeline);

        for (List<RenderPass.Draw<GpuBufferSlice[]>> draws : drawGroup.values()) {
            if (draws == null || draws.isEmpty()) {
                continue;
            }

            renderPass.drawMultipleIndexed(
                    draws,
                    defaultIndexBuffer,
                    defaultIndexType,
                    List.of("ChunkSection"),
                    chunkSections.chunkSectionInfos()
            );
        }
    }

    private static void logSkip(SkipReason reason) {
        if (lastSkipReason != reason) {
            lastSkipReason = reason;
            VulkanPostFX.LOGGER.info(
                    "[{}] Shadow terrain pass skipped: {}",
                    VulkanPostFX.MOD_ID,
                    reason
            );
        }
    }

    private enum SkipReason {
        NO_LEVEL,
        NO_LEVEL_RENDERER,
        NO_SHADOW_TARGET,
        NO_RELEVANT_DRAWS
    }
}