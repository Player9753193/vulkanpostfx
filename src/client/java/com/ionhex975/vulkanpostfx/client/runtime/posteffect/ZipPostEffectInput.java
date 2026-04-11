package com.ionhex975.vulkanpostfx.client.runtime.posteffect;

/**
 * ZIP 入口后处理中的单个输入定义。
 */
public final class ZipPostEffectInput {
    private final String samplerName;
    private final String target;

    public ZipPostEffectInput(String samplerName, String target) {
        this.samplerName = samplerName;
        this.target = target;
    }

    public String samplerName() {
        return samplerName;
    }

    public String target() {
        return target;
    }
}