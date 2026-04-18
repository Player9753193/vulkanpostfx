package com.ionhex975.vulkanpostfx.client.shader.uniform;

/**
 * VPFX builtin uniform 源码注入器。
 *
 * 当前 v1 扩展集合：
 * - vpfx_Time
 * - vpfx_DeltaTime
 * - vpfx_GameTime
 * - vpfx_FrameIndex
 * - vpfx_CameraPos
 * - vpfx_RainStrength
 *
 * 对应 runtime 注入的 uniforms.VpfxBuiltins。
 */
public final class VpfxBuiltinUniformSourceInjector {
    private static final String BLOCK = """
#ifndef VPFX_BUILTIN_UNIFORMS
#define VPFX_BUILTIN_UNIFORMS

layout(std140) uniform VpfxBuiltins {
    vec4 vpfx_TimeInfo;
    vec4 vpfx_ViewInfo;
};

#define vpfx_Time         (vpfx_TimeInfo.x)
#define vpfx_DeltaTime    (vpfx_TimeInfo.y)
#define vpfx_GameTime     (vpfx_TimeInfo.z)
#define vpfx_FrameIndex   (vpfx_TimeInfo.w)

#define vpfx_CameraPos    (vpfx_ViewInfo.xyz)
#define vpfx_RainStrength (vpfx_ViewInfo.w)

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