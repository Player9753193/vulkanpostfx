package com.ionhex975.vulkanpostfx.client.runtime.zip;

import java.nio.file.Files;
import java.nio.file.Path;

public final class RuntimeZipPackLocator {
    private RuntimeZipPackLocator() {
    }

    public static boolean isReady() {
        return RuntimeZipPackState.isActive()
                && RuntimeZipPackState.getRuntimeRoot() != null
                && Files.exists(RuntimeZipPackState.getRuntimeRoot().resolve("pack.mcmeta"));
    }

    public static Path getRuntimeRootOrThrow() {
        Path root = RuntimeZipPackState.getRuntimeRoot();
        if (root == null) {
            throw new IllegalStateException("runtime zip pack root is null");
        }
        return root;
    }
}