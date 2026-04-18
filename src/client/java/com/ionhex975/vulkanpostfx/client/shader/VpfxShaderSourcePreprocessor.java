package com.ionhex975.vulkanpostfx.client.shader;

import com.ionhex975.vulkanpostfx.client.shader.include.VpfxShaderIncludeException;
import com.ionhex975.vulkanpostfx.client.shader.include.VpfxShaderIncludeProcessor;
import com.ionhex975.vulkanpostfx.client.shader.uniform.VpfxBuiltinUniformSourceInjector;

import java.util.zip.ZipFile;

/**
 * VPFX shader 源码预处理统一入口。
 *
 * 固定顺序：
 * 1. include 展开
 * 2. builtin uniform 声明注入
 */
public final class VpfxShaderSourcePreprocessor {
    private final VpfxShaderIncludeProcessor includeProcessor;

    public VpfxShaderSourcePreprocessor(ZipFile zipFile) {
        this.includeProcessor = new VpfxShaderIncludeProcessor(zipFile);
    }

    public String preprocess(String zipShaderPath) throws VpfxShaderIncludeException {
        String flattened = includeProcessor.process(zipShaderPath);
        return VpfxBuiltinUniformSourceInjector.inject(flattened);
    }
}