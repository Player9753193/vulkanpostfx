package com.ionhex975.vulkanpostfx.client.shader.uniform;

/**
 * VPFX builtin uniform 源码注入器。
 *
 * Projection / Linear Depth v2.1：
 * - 直接提供真实 ProjectionMatrix / InverseProjectionMatrix
 * - 使用 inverse projection 做 depth reconstruction
 *
 * 当前导出：
 * - vpfx_Time
 * - vpfx_DeltaTime
 * - vpfx_GameTime
 * - vpfx_FrameIndex
 * - vpfx_CameraPos
 * - vpfx_RainStrength
 * - vpfx_ZNear
 * - vpfx_ZFar
 * - vpfx_Aspect
 * - vpfx_ScreenSize
 * - vpfx_InvScreenSize
 * - vpfx_ProjectionMatrix
 * - vpfx_InverseProjectionMatrix
 *
 * 并提供 helper：
 * - vpfx_RawSceneDepth(...)
 * - vpfx_ViewPositionFromRaw(...)
 * - vpfx_LinearViewDepthFromRaw(...)
 * - vpfx_LinearDepth01FromRaw(...)
 * - vpfx_LinearViewDepth(...)
 * - vpfx_LinearDepth01(...)
 * - vpfx_DepthNear01(...)
 * - vpfx_DepthFar01(...)
 *
 * 关键修正：
 * raw depth sample 是 [0, 1]；
 * 当前这条 projection/inverse-projection 链更适合在重建前先转换为 clip z:
 *   clipZ = raw * 2 - 1
 */
public final class VpfxBuiltinUniformSourceInjector {
    private static final String BLOCK = """
#ifndef VPFX_BUILTIN_UNIFORMS
#define VPFX_BUILTIN_UNIFORMS

layout(std140) uniform VpfxBuiltins {
    mat4 vpfx_ProjectionMatrix;
    mat4 vpfx_InverseProjectionMatrix;
    vec4 vpfx_TimeInfo;
    vec4 vpfx_ViewInfo;
    vec4 vpfx_ProjectionInfo;
    vec4 vpfx_ScreenInfo;
};

#define vpfx_Time          (vpfx_TimeInfo.x)
#define vpfx_DeltaTime     (vpfx_TimeInfo.y)
#define vpfx_GameTime      (vpfx_TimeInfo.z)
#define vpfx_FrameIndex    (vpfx_TimeInfo.w)

#define vpfx_CameraPos     (vpfx_ViewInfo.xyz)
#define vpfx_RainStrength  (vpfx_ViewInfo.w)

#define vpfx_ZNear         (vpfx_ProjectionInfo.x)
#define vpfx_ZFar          (vpfx_ProjectionInfo.y)
#define vpfx_Aspect        (vpfx_ProjectionInfo.z)

#define vpfx_ScreenSize    (vpfx_ScreenInfo.xy)
#define vpfx_InvScreenSize (vpfx_ScreenInfo.zw)

float vpfx_RawSceneDepth(sampler2D depthSampler, vec2 uv) {
    return texture(depthSampler, uv).r;
}

/**
 * 将当前屏幕 UV + raw depth 还原到 view space。
 *
 * 当前修正：
 * - raw depth sample 是 [0, 1]
 * - inverse projection 更适合吃 [-1, 1] clip z
 * - 因此先做 clipZ = raw * 2 - 1
 *
 * Y 仍使用屏幕到 NDC 的常规翻转。
 */
vec3 vpfx_ViewPositionFromRaw(sampler2D depthSampler, vec2 uv) {
    float rawDepth = clamp(vpfx_RawSceneDepth(depthSampler, uv), 0.0, 1.0);

    vec2 ndcXY = vec2(
        uv.x * 2.0 - 1.0,
        (1.0 - uv.y) * 2.0 - 1.0
    );

    float clipZ = rawDepth * 2.0 - 1.0;
    vec4 clipPos = vec4(ndcXY, clipZ, 1.0);

    vec4 viewPos = vpfx_InverseProjectionMatrix * clipPos;
    float w = max(abs(viewPos.w), 1e-6);

    return viewPos.xyz / w;
}

float vpfx_LinearViewDepthFromRaw(sampler2D depthSampler, vec2 uv) {
    vec3 viewPos = vpfx_ViewPositionFromRaw(depthSampler, uv);
    return abs(viewPos.z);
}

float vpfx_LinearDepth01FromRaw(sampler2D depthSampler, vec2 uv) {
    float nearPlane = max(vpfx_ZNear, 1e-6);
    float farPlane = max(vpfx_ZFar, nearPlane + 1e-6);
    float linearDepth = vpfx_LinearViewDepthFromRaw(depthSampler, uv);
    return clamp((linearDepth - nearPlane) / (farPlane - nearPlane), 0.0, 1.0);
}

float vpfx_LinearViewDepth(sampler2D depthSampler, vec2 uv) {
    return vpfx_LinearViewDepthFromRaw(depthSampler, uv);
}

float vpfx_LinearDepth01(sampler2D depthSampler, vec2 uv) {
    return vpfx_LinearDepth01FromRaw(depthSampler, uv);
}

float vpfx_DepthNear01(sampler2D depthSampler, vec2 uv) {
    return 1.0 - vpfx_LinearDepth01(depthSampler, uv);
}

float vpfx_DepthFar01(sampler2D depthSampler, vec2 uv) {
    return vpfx_LinearDepth01(depthSampler, uv);
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