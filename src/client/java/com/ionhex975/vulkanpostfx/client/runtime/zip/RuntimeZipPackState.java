package com.ionhex975.vulkanpostfx.client.runtime.zip;

import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxTargetDefinition;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.Map;

public final class RuntimeZipPackState {
    private static volatile boolean active;
    private static volatile String packId = "";
    private static volatile String runtimeNamespace = "";
    private static volatile Path runtimeRoot;
    private static volatile Identifier externalPostEffectId;
    private static volatile Map<String, VpfxTargetDefinition> targetDefinitions = Map.of();

    private RuntimeZipPackState() {
    }

    public static void apply(RuntimeZipPackMaterializationResult result) {
        active = true;
        packId = result.packId();
        runtimeNamespace = result.runtimeNamespace();
        runtimeRoot = result.runtimeRoot();
        externalPostEffectId = result.externalPostEffectId();
        targetDefinitions = Map.copyOf(result.targetDefinitions());
    }

    public static void clear() {
        active = false;
        packId = "";
        runtimeNamespace = "";
        runtimeRoot = null;
        externalPostEffectId = null;
        targetDefinitions = Map.of();
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

    public static Map<String, VpfxTargetDefinition> getTargetDefinitions() {
        return targetDefinitions;
    }

    public static VpfxTargetDefinition getTargetDefinition(String targetId) {
        return targetDefinitions.get(targetId);
    }

    public static boolean hasScaledTargets() {
        for (VpfxTargetDefinition definition : targetDefinitions.values()) {
            if (definition.getScale().isPresent()) {
                double scale = definition.getScale().get();
                if (Math.abs(scale - 1.0) > 1.0E-6) {
                    return true;
                }
            }
        }
        return false;
    }
}