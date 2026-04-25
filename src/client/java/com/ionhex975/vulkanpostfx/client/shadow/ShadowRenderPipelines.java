package com.ionhex975.vulkanpostfx.client.shadow;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

/**
 * Custom shadow-only terrain pipelines.
 *
 * 当前阶段目标：
 * 1. 先确认 custom shadow terrain pipeline 是否真的产生了可见片元
 * 2. 暂时关闭 cull，排除 handedness / winding 导致的整批三角形被剔除
 * 3. 保持 depth state 先用 DEFAULT，不同时引入太多变量
 */
public final class ShadowRenderPipelines {
    private ShadowRenderPipelines() {
    }

    public static final RenderPipeline.Snippet SHADOW_TERRAIN_SNIPPET = RenderPipeline.builder()
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withUniform("ChunkSection", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withVertexFormat(DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .withVertexShader("core/shadow_terrain")
            .withFragmentShader("core/shadow_terrain")
            /*
             * 关键调试改动：
             * 关闭 cull，优先排除 light-space 变换导致 front-face 绕序翻转的问题。
             *
             * 原版 RenderPipelines 里确实存在 .withCull(false) 的用法，
             * 说明这个 API 是可用的。:contentReference[oaicite:3]{index=3}
             */
            .withCull(false)
            .buildSnippet();

    public static final RenderPipeline SHADOW_TERRAIN_SOLID = RenderPipeline.builder(SHADOW_TERRAIN_SNIPPET)
            .withLocation("pipeline/vpfx_shadow_terrain_solid")
            .build();

    public static final RenderPipeline SHADOW_TERRAIN_CUTOUT = RenderPipeline.builder(SHADOW_TERRAIN_SNIPPET)
            .withLocation("pipeline/vpfx_shadow_terrain_cutout")
            .withShaderDefine("ALPHA_CUTOUT", 0.5F)
            .build();
}