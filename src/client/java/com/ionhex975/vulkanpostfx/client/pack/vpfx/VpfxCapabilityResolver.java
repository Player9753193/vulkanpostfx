package com.ionhex975.vulkanpostfx.client.pack.vpfx;

/**
 * 当前阶段 capability 必须只声明“runtime 真的能稳定兑现”的能力。
 *
 * 现在正式开放：
 * - sceneColor: true
 * - sceneDepth: true
 * - shadowDepth: true
 * - customTargets: true
 * - compute: false
 *
 * 说明：
 * 1. scene depth 走 minecraft:main + use_depth_buffer=true；
 * 2. shadow depth 当前还不是可对外承诺的正式能力；
 * 3. customTargets 仅表示 VPFX graph 内部 target 组织能力存在。
 */
public final class VpfxCapabilityResolver {

    public VpfxRuntimeCapabilities resolve() {
        return new VpfxRuntimeCapabilities(
                true,   // sceneColor
                true,   // sceneDepth
                true,  // shadowDepth
                true,   // customTargets
                false   // compute
        );
    }
}