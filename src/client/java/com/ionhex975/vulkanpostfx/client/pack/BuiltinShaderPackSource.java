package com.ionhex975.vulkanpostfx.client.pack;

import com.ionhex975.vulkanpostfx.client.effect.PostFxEffectRegistry;

import java.util.List;

/**
 * 内置开发包来源。
 *
 * 这不是最终产品形态，而是开发期兜底来源。
 */
public final class BuiltinShaderPackSource implements ShaderPackSource {
    public static final String SOURCE_ID = "builtin";

    @Override
    public String id() {
        return SOURCE_ID;
    }

    @Override
    public List<ShaderPackContainer> discoverPacks() {
        ShaderPackManifest manifest = new ShaderPackManifest(
                "builtin_debug_pack",
                "Builtin Debug Pack",
                1,
                PostFxEffectRegistry.DEBUG_INVERT,
                "assets/vulkanpostfx/post_effect/debug_invert.json"
        );

        return List.of(new ShaderPackContainer(manifest, SOURCE_ID, null));
    }
}