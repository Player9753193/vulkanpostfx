package com.ionhex975.vulkanpostfx.client.runtime.zip;

import net.minecraft.resources.Identifier;

import java.nio.file.Path;

public final class RuntimeZipPackMaterializationResult {
    private final String packId;
    private final String runtimeNamespace;
    private final Path runtimeRoot;
    private final Identifier externalPostEffectId;

    public RuntimeZipPackMaterializationResult(
            String packId,
            String runtimeNamespace,
            Path runtimeRoot,
            Identifier externalPostEffectId
    ) {
        this.packId = packId;
        this.runtimeNamespace = runtimeNamespace;
        this.runtimeRoot = runtimeRoot;
        this.externalPostEffectId = externalPostEffectId;
    }

    public String packId() {
        return packId;
    }

    public String runtimeNamespace() {
        return runtimeNamespace;
    }

    public Path runtimeRoot() {
        return runtimeRoot;
    }

    public Identifier externalPostEffectId() {
        return externalPostEffectId;
    }
}