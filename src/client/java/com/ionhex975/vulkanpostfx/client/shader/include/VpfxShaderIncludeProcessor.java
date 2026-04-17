package com.ionhex975.vulkanpostfx.client.shader.include;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VPFX shader include 预处理器。
 *
 * 支持：
 * 1. 相对 include
 *    #include "common.glsl"
 *    #include "../shared/math.glsl"
 *
 * 2. pack-root 绝对 include
 *    #include "/include/common.glsl"
 *
 * 规则：
 * - 只在 ZIP 内部解析
 * - 不允许越过 ZIP 根目录
 * - 检测 include 循环
 * - 失败时明确报错
 */
public final class VpfxShaderIncludeProcessor {
    private static final Pattern INCLUDE_PATTERN =
            Pattern.compile("^\\s*#include\\s+\"([^\"]+)\"\\s*$");

    private static final int MAX_INCLUDE_DEPTH = 64;
    private static final int USED = 16;

    private final ZipFile zipFile;
    private final Map<String, String> flattenedCache = new HashMap<>();

    public VpfxShaderIncludeProcessor(ZipFile zipFile) {
        this.zipFile = zipFile;
    }

    /**
     * 对一个 shader entry 做完整 include 展开。
     *
     * @param zipEntryPath ZIP 内路径，例如：
     *                     shaders/post/fullscreen.vsh
     */
    public String process(String zipEntryPath) throws VpfxShaderIncludeException {
        if (zipEntryPath == null || zipEntryPath.isBlank()) {
            throw new VpfxShaderIncludeException(
                    "I000",
                    "(blank)",
                    "Shader entry path is blank"
            );
        }

        String normalized = normalizeZipPath(zipEntryPath);
        Deque<String> stack = new ArrayDeque<>();
        Set<String> active = new HashSet<>();
        return flattenRecursive(normalized, stack, active, 0);
    }

    private String flattenRecursive(
            String currentPath,
            Deque<String> stack,
            Set<String> active,
            int depth
    ) throws VpfxShaderIncludeException {
        if (depth > MAX_INCLUDE_DEPTH) {
            throw new VpfxShaderIncludeException(
                    "I001",
                    currentPath,
                    "Include depth exceeded max depth " + MAX_INCLUDE_DEPTH
            );
        }

        String cached = flattenedCache.get(currentPath);
        if (cached != null) {
            return cached;
        }

        if (active.contains(currentPath)) {
            StringBuilder cycle = new StringBuilder();
            for (String item : stack) {
                if (!cycle.isEmpty()) {
                    cycle.append(" -> ");
                }
                cycle.append(item);
            }
            if (!cycle.isEmpty()) {
                cycle.append(" -> ");
            }
            cycle.append(currentPath);

            throw new VpfxShaderIncludeException(
                    "I002",
                    currentPath,
                    "Include cycle detected: " + cycle
            );
        }

        ZipEntry entry = zipFile.getEntry(currentPath);
        if (entry == null || entry.isDirectory()) {
            throw new VpfxShaderIncludeException(
                    "I003",
                    currentPath,
                    "Included file not found in zip: " + currentPath
            );
        }

        active.add(currentPath);
        stack.addLast(currentPath);

        String source = readZipText(currentPath);
        StringBuilder out = new StringBuilder();
        String[] lines = source.split("\\R", -1);

        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            Matcher matcher = INCLUDE_PATTERN.matcher(line);

            if (!matcher.matches()) {
                out.append(line);
                if (lineIndex < lines.length - 1) {
                    out.append('\n');
                }
                continue;
            }

            String includeTarget = matcher.group(1);
            String resolvedPath = resolveIncludePath(currentPath, includeTarget);

            out.append("// >>> begin include: ").append(resolvedPath).append('\n');
            out.append(flattenRecursive(resolvedPath, stack, active, depth + 1));
            out.append('\n');
            out.append("// <<< end include: ").append(resolvedPath);

            if (lineIndex < lines.length - 1) {
                out.append('\n');
            }
        }

        stack.removeLast();
        active.remove(currentPath);

        String flattened = out.toString();
        flattenedCache.put(currentPath, flattened);
        return flattened;
    }

    private String resolveIncludePath(String currentPath, String includeTarget)
            throws VpfxShaderIncludeException {
        if (includeTarget == null || includeTarget.isBlank()) {
            throw new VpfxShaderIncludeException(
                    "I004",
                    currentPath,
                    "Include target is blank"
            );
        }

        final String candidate;
        if (includeTarget.startsWith("/")) {
            candidate = includeTarget.substring(1);
        } else {
            int slash = currentPath.lastIndexOf('/');
            String currentDir = slash >= 0 ? currentPath.substring(0, slash + 1) : "";
            candidate = currentDir + includeTarget;
        }

        String normalized = normalizeZipPath(candidate);
        if (normalized.isBlank()) {
            throw new VpfxShaderIncludeException(
                    "I005",
                    currentPath,
                    "Resolved include path is blank for target: " + includeTarget
            );
        }

        return normalized;
    }

    /**
     * 归一化 ZIP 内路径：
     * - 统一分隔符
     * - 处理 .
     * - 处理 ..
     * - 不允许越过根目录
     */
    private String normalizeZipPath(String rawPath) throws VpfxShaderIncludeException {
        String path = rawPath.replace('\\', '/').trim();

        while (path.startsWith("./")) {
            path = path.substring(2);
        }

        String[] parts = path.split("/");
        Deque<String> normalized = new ArrayDeque<>();

        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }

            if ("..".equals(part)) {
                if (normalized.isEmpty()) {
                    throw new VpfxShaderIncludeException(
                            "I006",
                            rawPath,
                            "Include path escapes zip root: " + rawPath
                    );
                }
                normalized.removeLast();
                continue;
            }

            normalized.addLast(part);
        }

        return String.join("/", normalized);
    }

    private String readZipText(String zipEntryPath) throws VpfxShaderIncludeException {
        try (InputStream in = zipFile.getInputStream(zipFile.getEntry(zipEntryPath))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new VpfxShaderIncludeException(
                    "I007",
                    zipEntryPath,
                    "Failed to read zip entry: " + e.getMessage()
            );
        }
    }
}