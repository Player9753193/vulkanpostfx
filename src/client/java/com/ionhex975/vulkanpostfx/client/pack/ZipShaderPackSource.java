package com.ionhex975.vulkanpostfx.client.pack;

import com.ionhex975.vulkanpostfx.VulkanPostFX;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ZipShaderPackSource implements ShaderPackSource {
    public static final String SOURCE_ID = "zip";
    private static final String ZIP_SUFFIX = ".zip";
    private static final String MANIFEST_PATH = "pack.json";

    private final Path shaderPackDirectory;

    public ZipShaderPackSource(Path shaderPackDirectory) {
        this.shaderPackDirectory = shaderPackDirectory;
    }

    @Override
    public String id() {
        return SOURCE_ID;
    }

    @Override
    public List<ShaderPackContainer> discoverPacks() {
        ensureDirectoryExists();

        List<ShaderPackContainer> discovered = new ArrayList<>();

        try (var stream = Files.list(shaderPackDirectory)) {
            stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(ZIP_SUFFIX))
                    .sorted()
                    .forEach(path -> {
                        ShaderPackContainer container = createContainerFromZip(path);
                        if (container != null) {
                            discovered.add(container);
                        }
                    });
        } catch (IOException e) {
            VulkanPostFX.LOGGER.error(
                    "[{}] Failed to scan shader pack directory: {}",
                    VulkanPostFX.MOD_ID,
                    shaderPackDirectory,
                    e
            );
        }

        VulkanPostFX.LOGGER.info(
                "[{}] Zip shader pack source scanned '{}', found {} valid zip pack(s)",
                VulkanPostFX.MOD_ID,
                shaderPackDirectory,
                discovered.size()
        );

        return discovered;
    }

    private void ensureDirectoryExists() {
        try {
            Files.createDirectories(shaderPackDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create shader pack directory: " + shaderPackDirectory, e);
        }
    }

    private ShaderPackContainer createContainerFromZip(Path zipPath) {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            ZipEntry manifestEntry = zipFile.getEntry(MANIFEST_PATH);
            if (manifestEntry == null) {
                VulkanPostFX.LOGGER.warn(
                        "[{}] Skipping zip shader pack without {} at zip root: {}",
                        VulkanPostFX.MOD_ID,
                        MANIFEST_PATH,
                        zipPath
                );
                return null;
            }

            try (Reader reader = new InputStreamReader(
                    zipFile.getInputStream(manifestEntry),
                    StandardCharsets.UTF_8
            )) {
                ShaderPackManifest manifest = ShaderPackManifestParser.parse(reader);

                VulkanPostFX.LOGGER.info(
                        "[{}] Parsed shader pack manifest from zip '{}': id='{}', name='{}', version={}, entryEffectKey={}, entryPostEffect={}",
                        VulkanPostFX.MOD_ID,
                        zipPath.getFileName(),
                        manifest.id(),
                        manifest.name(),
                        manifest.version(),
                        manifest.entryEffectKey(),
                        manifest.entryPostEffect()
                );

                return new ShaderPackContainer(manifest, SOURCE_ID, zipPath);
            }
        } catch (Exception e) {
            VulkanPostFX.LOGGER.error(
                    "[{}] Failed to parse zip shader pack: {}",
                    VulkanPostFX.MOD_ID,
                    zipPath,
                    e
            );
            return null;
        }
    }
}