package com.ionhex975.vulkanpostfx.client.pack;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.config.ActiveShaderPackConfig;
import com.ionhex975.vulkanpostfx.client.effect.PostFxEffectRegistry;
import net.minecraft.client.Minecraft;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 当前活动光影包管理器。
 *
 * 当前阶段职责：
 * - 注册 pack source
 * - 发现可用包
 * - 读取 active_pack_id 配置
 * - 仅在用户显式指定时激活外部 ZIP 包
 * - 否则默认回退到 builtin
 */
public final class ActiveShaderPackManager {
    private static final String SHADER_PACK_DIRECTORY_NAME = "shaderpacks";
    private static final String CONFIG_DIRECTORY_NAME = "config";
    private static final String CONFIG_FILE_NAME = "vulkanpostfx.json";

    private static final List<ShaderPackSource> SOURCES = new ArrayList<>();

    private static ShaderPackContainer activePack;
    private static List<ShaderPackContainer> discoveredPacks = List.of();
    private static ActiveShaderPackConfig activeConfig = new ActiveShaderPackConfig("");

    private ActiveShaderPackManager() {
    }

    public static void bootstrap() {
        SOURCES.clear();

        Path runDirectory = Minecraft.getInstance().gameDirectory.toPath();
        Path shaderPackDirectory = runDirectory.resolve(SHADER_PACK_DIRECTORY_NAME);
        Path configPath = runDirectory.resolve(CONFIG_DIRECTORY_NAME).resolve(CONFIG_FILE_NAME);

        SOURCES.add(new BuiltinShaderPackSource());
        SOURCES.add(new ZipShaderPackSource(shaderPackDirectory));

        List<ShaderPackContainer> discovered = new ArrayList<>();
        for (ShaderPackSource source : SOURCES) {
            discovered.addAll(source.discoverPacks());
        }

        discoveredPacks = List.copyOf(discovered);
        activeConfig = ActiveShaderPackConfig.loadOrCreate(configPath);

        ShaderPackContainer builtinPack = discovered.stream()
                .filter(pack -> BuiltinShaderPackSource.SOURCE_ID.equals(pack.sourceId()))
                .findFirst()
                .orElse(null);

        if (activeConfig.usesExternalPack()) {
            ShaderPackContainer selectedExternal = discovered.stream()
                    .filter(pack -> !BuiltinShaderPackSource.SOURCE_ID.equals(pack.sourceId()))
                    .filter(pack -> activeConfig.activePackId().equals(pack.manifest().id()))
                    .findFirst()
                    .orElse(null);

            if (selectedExternal != null) {
                activePack = selectedExternal;
                VulkanPostFX.LOGGER.info(
                        "[{}] Active external shader pack selected by config: '{}' (id='{}', entryPostEffect='{}')",
                        VulkanPostFX.MOD_ID,
                        selectedExternal.manifest().name(),
                        selectedExternal.manifest().id(),
                        selectedExternal.manifest().entryPostEffect()
                );
            } else {
                activePack = builtinPack;
                VulkanPostFX.LOGGER.warn(
                        "[{}] Config requested external pack id='{}', but it was not found. Falling back to builtin pack.",
                        VulkanPostFX.MOD_ID,
                        activeConfig.activePackId()
                );
            }
        } else {
            activePack = builtinPack;
            VulkanPostFX.LOGGER.info(
                    "[{}] No external shader pack selected; using builtin pack by default",
                    VulkanPostFX.MOD_ID
            );
        }

        if (activePack == null) {
            VulkanPostFX.LOGGER.warn("[{}] No shader packs discovered at all", VulkanPostFX.MOD_ID);
            return;
        }

        VulkanPostFX.LOGGER.info(
                "[{}] Active shader pack set to '{}' from source '{}', entryEffectKey={}, entryPostEffect={}",
                VulkanPostFX.MOD_ID,
                activePack.manifest().name(),
                activePack.sourceId(),
                activePack.manifest().entryEffectKey(),
                activePack.manifest().entryPostEffect()
        );

        logDiscoveredPacks();
    }

    public static ShaderPackContainer getActivePack() {
        return activePack;
    }

    public static List<ShaderPackContainer> getDiscoveredPacks() {
        return discoveredPacks;
    }

    public static ActiveShaderPackConfig getActiveConfig() {
        return activeConfig;
    }

    public static String getActiveEffectKey() {
        if (activePack == null) {
            return PostFxEffectRegistry.DEBUG_INVERT;
        }

        String entryEffectKey = activePack.manifest().entryEffectKey();
        if (entryEffectKey == null || entryEffectKey.isBlank()) {
            return PostFxEffectRegistry.DEBUG_INVERT;
        }

        return entryEffectKey;
    }

    public static String getActiveEntryPostEffect() {
        if (activePack == null) {
            return "";
        }

        return activePack.manifest().entryPostEffect();
    }

    private static void logDiscoveredPacks() {
        for (ShaderPackContainer pack : discoveredPacks) {
            VulkanPostFX.LOGGER.info(
                    "[{}] Discovered shader pack: name='{}', id='{}', source='{}', path={}, entryPostEffect={}",
                    VulkanPostFX.MOD_ID,
                    pack.manifest().name(),
                    pack.manifest().id(),
                    pack.sourceId(),
                    pack.sourcePath(),
                    pack.manifest().entryPostEffect()
            );
        }
    }
}