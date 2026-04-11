package com.ionhex975.vulkanpostfx.client.runtime.zip;

import net.minecraft.resources.Identifier;

import java.nio.file.Path;

public final class RuntimeZipPackState {
    private static volatile boolean active;
    private static volatile String packId = "";
    private static volatile String runtimeNamespace = "";
    private static volatile Path runtimeRoot;
    private static volatile Identifier externalPostEffectId;

    private RuntimeZipPackState() {
    }

    public static void apply(RuntimeZipPackMaterializationResult result) {
        active = true;
        packId = result.packId();
        runtimeNamespace = result.runtimeNamespace();
        runtimeRoot = result.runtimeRoot();
        externalPostEffectId = result.externalPostEffectId();
    }

    public static void clear() {
        active = false;
        packId = "";
        runtimeNamespace = "";
        runtimeRoot = null;
        externalPostEffectId = null;
    }

    public static boolean isActive() {
        return active;
    }

    public static String getPackId() {
        return packId;
    }

    public static String getRuntimeNamespace() {
        return runtimeNamespace;
    }

    public static Path getRuntimeRoot() {
        return runtimeRoot;
    }

    public static Identifier getExternalPostEffectId() {
        return externalPostEffectId;
    }
}