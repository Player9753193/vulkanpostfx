package com.ionhex975.vulkanpostfx.client.runtime.zip;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.pack.ShaderPackContainer;
import com.ionhex975.vulkanpostfx.client.pack.ZipShaderPackReader;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ZipPackMaterializer {
    private static final int RESOURCE_PACK_FORMAT = 85;

    private ZipPackMaterializer() {
    }

    public static RuntimeZipPackMaterializationResult materialize(
            ShaderPackContainer activePack,
            Path gameDirectory
    ) throws IOException {
        if (activePack == null || activePack.sourcePath() == null) {
            throw new IOException("active pack or source path is null");
        }

        String packId = activePack.manifest().id();
        String runtimeNamespace = ActiveZipRuntimeNamespace.fromPackId(packId);

        Path runtimeBaseDir = gameDirectory.resolve("vulkanpostfx_runtime");
        Path runtimeRoot = runtimeBaseDir.resolve(runtimeNamespace);

        recreateDirectory(runtimeRoot);
        writePackMcmeta(runtimeRoot);

        String entryPostEffectRaw = ZipShaderPackReader.readText(
                activePack.sourcePath(),
                activePack.manifest().entryPostEffect()
        );

        String rewrittenMainJson = ZipPostEffectNamespaceRewriter.rewrite(
                entryPostEffectRaw,
                packId,
                runtimeNamespace
        );

        Path mainJsonPath = runtimeRoot
                .resolve("assets")
                .resolve(runtimeNamespace)
                .resolve("post_effect")
                .resolve("main.json");

        Files.createDirectories(mainJsonPath.getParent());
        Files.writeString(mainJsonPath, rewrittenMainJson, StandardCharsets.UTF_8);

        copyZipShaderTree(activePack.sourcePath(), runtimeRoot, runtimeNamespace);

        return new RuntimeZipPackMaterializationResult(
                packId,
                runtimeNamespace,
                runtimeRoot,
                Identifier.fromNamespaceAndPath(runtimeNamespace, "main")
        );
    }

    private static void recreateDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (RuntimeException e) {
                if (e.getCause() instanceof IOException io) {
                    throw io;
                }
                throw e;
            }
        }

        Files.createDirectories(dir);
    }

    private static void writePackMcmeta(Path runtimeRoot) throws IOException {
        String mcmeta = """
            {
              "pack": {
                "pack_format": %d,
                "supported_formats": {
                  "min_format": %d,
                  "max_format": %d
                },
                "description": "VulkanPostFX runtime zip shader pack"
              }
            }
            """.formatted(RESOURCE_PACK_FORMAT, RESOURCE_PACK_FORMAT, RESOURCE_PACK_FORMAT);

        Files.writeString(runtimeRoot.resolve("pack.mcmeta"), mcmeta, StandardCharsets.UTF_8);
    }

    private static void copyZipShaderTree(
            Path zipPath,
            Path runtimeRoot,
            String runtimeNamespace
    ) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            var entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }

                String name = entry.getName().replace('\\', '/');

                if (!name.startsWith("shaders/")) {
                    continue;
                }

                if (name.endsWith(".DS_Store") || name.contains("/.DS_Store")) {
                    VulkanPostFX.LOGGER.info(
                            "[{}] Skipped ZIP junk file: {}",
                            VulkanPostFX.MOD_ID,
                            name
                    );
                    continue;
                }

                Path outPath = runtimeRoot
                        .resolve("assets")
                        .resolve(runtimeNamespace)
                        .resolve(name);

                Files.createDirectories(outPath.getParent());

                try (InputStream in = zipFile.getInputStream(entry)) {
                    Files.copy(in, outPath);
                }

                VulkanPostFX.LOGGER.info(
                        "[{}] Materialized ZIP shader asset: {} -> {}",
                        VulkanPostFX.MOD_ID,
                        name,
                        outPath
                );
            }
        }
    }
}