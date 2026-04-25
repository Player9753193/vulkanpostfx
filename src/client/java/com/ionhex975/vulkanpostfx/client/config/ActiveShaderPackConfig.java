package com.ionhex975.vulkanpostfx.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ionhex975.vulkanpostfx.VulkanPostFX;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 当前活动光影包配置。
 *
 * active_pack_id:
 * - 空字符串：不启用任何外部 ZIP 包，继续走 builtin
 * - 非空字符串：显式启用对应 id 的外部包
 */
public final class ActiveShaderPackConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final String activePackId;

    public ActiveShaderPackConfig(String activePackId) {
        this.activePackId = activePackId == null ? "" : activePackId;
    }

    public String activePackId() {
        return activePackId;
    }

    public boolean usesExternalPack() {
        return !activePackId.isBlank();
    }

    public static ActiveShaderPackConfig loadOrCreate(Path configPath) {
        try {
            Files.createDirectories(configPath.getParent());

            if (!Files.exists(configPath)) {
                ActiveShaderPackConfig defaultConfig = new ActiveShaderPackConfig("");
                save(configPath, defaultConfig);
                VulkanPostFX.LOGGER.info(
                        "[{}] Created default shader pack config at {}",
                        VulkanPostFX.MOD_ID,
                        configPath
                );
                return defaultConfig;
            }

            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                String activePackId = root.has("active_pack_id")
                        ? root.get("active_pack_id").getAsString()
                        : "";

                ActiveShaderPackConfig config = new ActiveShaderPackConfig(activePackId);
                VulkanPostFX.LOGGER.info(
                        "[{}] Loaded shader pack config: active_pack_id='{}'",
                        VulkanPostFX.MOD_ID,
                        config.activePackId()
                );
                return config;
            }
        } catch (Exception e) {
            VulkanPostFX.LOGGER.error(
                    "[{}] Failed to load shader pack config from {}, falling back to builtin-only mode",
                    VulkanPostFX.MOD_ID,
                    configPath,
                    e
            );
            return new ActiveShaderPackConfig("");
        }
    }

    public static void save(Path configPath, ActiveShaderPackConfig config) throws IOException {
        Files.createDirectories(configPath.getParent());

        JsonObject root = new JsonObject();
        root.addProperty("active_pack_id", config.activePackId());

        try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
    }
}