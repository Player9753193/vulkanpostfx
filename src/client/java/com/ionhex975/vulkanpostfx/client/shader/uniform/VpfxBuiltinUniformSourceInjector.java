package com.ionhex975.vulkanpostfx.client.shader.uniform;

/**
 * VPFX builtin uniform 源码注入器。
 *
 * Shadow Apply Debug v1：
 * - 扩展 VpfxBuiltins，加入 SceneDepth 重建和 Shadow Apply 所需矩阵
 * - 提供 helper：
 *   - vpfx_ViewPositionFromRaw(...)
 *   - vpfx_WorldPositionFromRaw(...)
 */
public final class VpfxBuiltinUniformSourceInjector {
    private static final String BLOCK = """
#ifndef VPFX_BUILTIN_UNIFORMS
#define VPFX_BUILTIN_UNIFORMS

layout(std140) uniform VpfxBuiltins {
    vec4 vpfx_TimeInfo;
    vec4 vpfx_ViewInfo;
    vec4 vpfx_SceneInfo;
    vec4 vpfx_ShadowInfo;
    mat4 vpfx_InverseProjectionMatrix;
    mat4 vpfx_InverseViewRotationMatrix;
    mat4 vpfx_ShadowViewProjectionMatrix;
};

#define vpfx_Time           (vpfx_TimeInfo.x)
#define vpfx_DeltaTime      (vpfx_TimeInfo.y)
#define vpfx_GameTime       (vpfx_TimeInfo.z)
#define vpfx_FrameIndex     (vpfx_TimeInfo.w)

#define vpfx_CameraPos      (vpfx_ViewInfo.xyz)
#define vpfx_RainStrength   (vpfx_ViewInfo.w)

#define vpfx_ViewSize       (vpfx_SceneInfo.xy)
#define vpfx_InvViewSize    (vpfx_SceneInfo.zw)

#define vpfx_ZNear          (vpfx_ShadowInfo.x)
#define vpfx_ZFar           (vpfx_ShadowInfo.y)
#define vpfx_ShadowMapSize  (vpfx_ShadowInfo.z)
#define vpfx_ShadowBias     (vpfx_ShadowInfo.w)

/**
 * 从 raw scene depth 重建 view-space position。
 *
 * 当前按 Vulkan / zero-to-one depth 路线处理。
 * 如果后续发现 y 方向需要翻转，只改这里就行。
 */
vec3 vpfx_ViewPositionFromRaw(sampler2D depthSampler, vec2 uv) {
    float rawDepth = texture(depthSampler, uv).r;

    // SceneDepth Y-flip 修正：
    // 当前链路下，屏幕空间 depth 取样与我们原先假设的 Y 方向相反。
    vec2 ndcUv = vec2(
        uv.x,
        1.0 - uv.y
    );

    vec4 clip = vec4(
        ndcUv * 2.0 - 1.0,
        rawDepth,
        1.0
    );

    vec4 view = vpfx_InverseProjectionMatrix * clip;
    return view.xyz / max(abs(view.w), 1e-6);
}

/**
 * 从 raw scene depth 重建 world-space position。
 *
 * 注意：
 * 这里的 vpfx_InverseViewRotationMatrix 只做方向旋转，
 * 平移单独通过 vpfx_CameraPos 补回。
 */
vec3 vpfx_WorldPositionFromRaw(sampler2D depthSampler, vec2 uv) {
    vec3 viewPos = vpfx_ViewPositionFromRaw(depthSampler, uv);
    return (vpfx_InverseViewRotationMatrix * vec4(viewPos, 0.0)).xyz + vpfx_CameraPos;
}

#endif
""";

    private VpfxBuiltinUniformSourceInjector() {
    }

    public static String inject(String source) {
        if (source == null || source.isBlank()) {
            return source;
        }

        if (source.contains("VPFX_BUILTIN_UNIFORMS")
                || source.contains("layout(std140) uniform VpfxBuiltins")
                || source.contains("uniform VpfxBuiltins")) {
            return source;
        }

        int versionIndex = source.indexOf("#version");
        if (versionIndex < 0) {
            return BLOCK + "\n" + source;
        }

        int lineEnd = source.indexOf('\n', versionIndex);
        if (lineEnd < 0) {
            return source + "\n" + BLOCK + "\n";
        }

        return source.substring(0, lineEnd + 1)
                + "\n"
                + BLOCK
                + "\n"
                + source.substring(lineEnd + 1);
    }
}