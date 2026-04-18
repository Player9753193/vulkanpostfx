package com.ionhex975.vulkanpostfx.client.shadow;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
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

        ChunkSectionsToRender shadowChunks = levelRenderer.prepareChunkRenders(
                shadowState.getShadowViewMatrix()
        );

        if (!hasOpaqueDraws(shadowChunks)) {
            logSkip(SkipReason.NO_OPAQUE_DRAWS);
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
        try {
            GpuBufferSlice projectionSlice = SHADOW_PROJECTION.getBuffer(
                    shadowState.getShadowProjectionMatrix()
            );
            RenderSystem.setProjectionMatrix(projectionSlice, ProjectionType.ORTHOGRAPHIC);

            renderGroupToTarget(
                    minecraft,
                    shadowChunks,
                    ChunkSectionLayerGroup.OPAQUE,
                    chunkLayerSampler,
                    shadowTarget
            );

            lastSkipReason = null;

            if (!firstTerrainSuccessLogged) {
                firstTerrainSuccessLogged = true;
                VulkanPostFX.LOGGER.info(
                        "[{}] Shadow terrain pass submitted opaque terrain draws successfully",
                        VulkanPostFX.MOD_ID
                );
            }

            return true;
        } catch (Throwable t) {
            VulkanPostFX.LOGGER.error(
                    "[{}] Shadow terrain pass failed during submission",
                    VulkanPostFX.MOD_ID,
                    t
            );
            return false;
        } finally {
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private static boolean hasOpaqueDraws(ChunkSectionsToRender chunkSections) {
        if (chunkSections == null) {
            return false;
        }

        for (ChunkSectionLayer layer : ChunkSectionLayerGroup.OPAQUE.layers()) {
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

    private static void renderGroupToTarget(
            Minecraft minecraft,
            ChunkSectionsToRender chunkSections,
            ChunkSectionLayerGroup group,
            GpuSampler sampler,
            RenderTarget renderTarget
    ) {
        RenderSystem.AutoStorageIndexBuffer autoIndices =
                RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer defaultIndexBuffer =
                chunkSections.maxIndicesRequired() == 0 ? null : autoIndices.getBuffer(chunkSections.maxIndicesRequired());
        VertexFormat.IndexType defaultIndexType =
                chunkSections.maxIndicesRequired() == 0 ? null : autoIndices.type();

        ChunkSectionLayer[] layers = group.layers();

        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> "VulkanPostFX Shadow Terrain",
                        renderTarget.getColorTextureView(),
                        OptionalInt.empty(),
                        renderTarget.getDepthTextureView(),
                        OptionalDouble.empty()
                )) {
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.bindTexture("Sampler0", chunkSections.textureView(), sampler);
            renderPass.bindTexture(
                    "Sampler2",
                    minecraft.gameRenderer.lightmap(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
            );

            for (ChunkSectionLayer layer : layers) {
                renderPass.setPipeline(layer.pipeline());

                Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>> drawGroup =
                        chunkSections.drawGroupsPerLayer().get(layer);

                if (drawGroup == null || drawGroup.isEmpty()) {
                    continue;
                }

                for (List<RenderPass.Draw<GpuBufferSlice[]>> draws : drawGroup.values()) {
                    if (!draws.isEmpty()) {
                        List<RenderPass.Draw<GpuBufferSlice[]>> submitDraws = draws;
                        if (layer == ChunkSectionLayer.TRANSLUCENT) {
                            submitDraws = draws.reversed();
                        }

                        renderPass.drawMultipleIndexed(
                                submitDraws,
                                defaultIndexBuffer,
                                defaultIndexType,
                                List.of("ChunkSection"),
                                chunkSections.chunkSectionInfos()
                        );
                    }
                }
            }
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
        NO_OPAQUE_DRAWS
    }
}