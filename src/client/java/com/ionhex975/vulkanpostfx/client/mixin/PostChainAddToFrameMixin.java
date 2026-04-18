package com.ionhex975.vulkanpostfx.client.mixin;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import com.ionhex975.vulkanpostfx.client.pack.vpfx.VpfxTargetDefinition;
import com.ionhex975.vulkanpostfx.client.runtime.zip.RuntimeZipPackState;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceHandle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(PostChain.class)
public abstract class PostChainAddToFrameMixin {
    @Shadow
    @Final
    private List<PostPass> passes;

    @Shadow
    @Final
    private Set<Identifier> externalTargets;

    @Shadow
    @Final
    private Projection projection;

    @Shadow
    @Final
    private ProjectionMatrixBuffer projectionMatrixBuffer;

    @Unique
    private static boolean vulkanpostfx$firstScaledTargetLog;

    @Inject(
            method = "addToFrame",
            at = @At("HEAD"),
            cancellable = true
    )
    private void vulkanpostfx$addScaledTargets(
            FrameGraphBuilder frame,
            int screenWidth,
            int screenHeight,
            PostChain.TargetBundle providedTargets,
            CallbackInfo ci
    ) {
        if (!RuntimeZipPackState.isActive()) {
            return;
        }

        String runtimeNamespace = RuntimeZipPackState.getRuntimeNamespace();
        if (runtimeNamespace == null || runtimeNamespace.isBlank()) {
            return;
        }

        PostChain self = (PostChain) (Object) this;
        Map<Identifier, PostChainConfig.InternalTarget> internalTargets =
                ((PostChainAccessor) self).vulkanpostfx$getInternalTargets();

        if (internalTargets == null || internalTargets.isEmpty()) {
            return;
        }

        if (!belongsToRuntimeNamespace(internalTargets.keySet(), runtimeNamespace)) {
            return;
        }

        if (!hasScaledTarget(internalTargets)) {
            return;
        }

        if (!vulkanpostfx$firstScaledTargetLog) {
            vulkanpostfx$firstScaledTargetLog = true;
            VulkanPostFX.LOGGER.info(
                    "[{}] Intercepting PostChain.addToFrame for VPFX runtime chain with scaled internal targets",
                    VulkanPostFX.MOD_ID
            );
        }

        this.projection.setSize(screenWidth, screenHeight);
        GpuBufferSlice projectionBuffer = this.projectionMatrixBuffer.getBuffer(this.projection);

        Map<Identifier, ResourceHandle<RenderTarget>> targets =
                new HashMap<>(internalTargets.size() + this.externalTargets.size());

        for (Identifier id : this.externalTargets) {
            targets.put(id, providedTargets.getOrThrow(id));
        }

        for (Map.Entry<Identifier, PostChainConfig.InternalTarget> entry : internalTargets.entrySet()) {
            Identifier id = entry.getKey();
            PostChainConfig.InternalTarget parsedTarget = entry.getValue();
            VpfxTargetDefinition runtimeTargetDefinition =
                    RuntimeZipPackState.getTargetDefinition(id.toString());

            int width = resolveWidth(screenWidth, parsedTarget, runtimeTargetDefinition);
            int height = resolveHeight(screenHeight, parsedTarget, runtimeTargetDefinition);
            boolean useDepth = resolveUseDepth(runtimeTargetDefinition);
            int clearColor = resolveClearColor(parsedTarget, runtimeTargetDefinition);

            RenderTargetDescriptor descriptor =
                    new RenderTargetDescriptor(width, height, useDepth, clearColor);

            if (parsedTarget.persistent()) {
                RenderTarget persistentTarget =
                        ((PostChainAccessor) self).vulkanpostfx$invokeGetOrCreatePersistentTarget(id, descriptor);
                targets.put(id, frame.importExternal(id.toString(), persistentTarget));
            } else {
                targets.put(id, frame.createInternal(id.toString(), descriptor));
            }
        }

        for (PostPass pass : this.passes) {
            pass.addToFrame(frame, targets, projectionBuffer);
        }

        for (Identifier id : this.externalTargets) {
            ResourceHandle<RenderTarget> handle = targets.get(id);
            if (handle != null) {
                providedTargets.replace(id, handle);
            }
        }

        ci.cancel();
    }

    @Unique
    private static boolean belongsToRuntimeNamespace(Set<Identifier> targetIds, String runtimeNamespace) {
        for (Identifier id : targetIds) {
            if (runtimeNamespace.equals(id.getNamespace())) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static boolean hasScaledTarget(Map<Identifier, PostChainConfig.InternalTarget> internalTargets) {
        for (Identifier id : internalTargets.keySet()) {
            VpfxTargetDefinition definition = RuntimeZipPackState.getTargetDefinition(id.toString());
            if (definition != null && definition.getScale().isPresent()) {
                double scale = definition.getScale().get();
                if (Math.abs(scale - 1.0) > 1.0E-6) {
                    return true;
                }
            }
        }
        return false;
    }

    @Unique
    private static int resolveWidth(
            int screenWidth,
            PostChainConfig.InternalTarget parsedTarget,
            VpfxTargetDefinition runtimeTargetDefinition
    ) {
        return parsedTarget.width().orElseGet(() ->
                computeScaledDimension(screenWidth, runtimeTargetDefinition)
        );
    }

    @Unique
    private static int resolveHeight(
            int screenHeight,
            PostChainConfig.InternalTarget parsedTarget,
            VpfxTargetDefinition runtimeTargetDefinition
    ) {
        return parsedTarget.height().orElseGet(() ->
                computeScaledDimension(screenHeight, runtimeTargetDefinition)
        );
    }

    @Unique
    private static int computeScaledDimension(
            int fullSize,
            VpfxTargetDefinition runtimeTargetDefinition
    ) {
        if (runtimeTargetDefinition == null || runtimeTargetDefinition.getScale().isEmpty()) {
            return fullSize;
        }

        double scale = runtimeTargetDefinition.getScale().get();
        return Math.max(1, (int) Math.round(fullSize * scale));
    }

    @Unique
    private static boolean resolveUseDepth(VpfxTargetDefinition runtimeTargetDefinition) {
        if (runtimeTargetDefinition == null) {
            return true;
        }
        return runtimeTargetDefinition.isUseDepth();
    }

    @Unique
    private static int resolveClearColor(
            PostChainConfig.InternalTarget parsedTarget,
            VpfxTargetDefinition runtimeTargetDefinition
    ) {
        if (runtimeTargetDefinition == null || runtimeTargetDefinition.getClearColor().isEmpty()) {
            return parsedTarget.clearColor();
        }

        float[] rgba = runtimeTargetDefinition.getClearColor().get();
        if (rgba.length != 4) {
            return parsedTarget.clearColor();
        }

        int r = to8Bit(rgba[0]);
        int g = to8Bit(rgba[1]);
        int b = to8Bit(rgba[2]);
        int a = to8Bit(rgba[3]);

        return ((a & 0xFF) << 24)
                | ((r & 0xFF) << 16)
                | ((g & 0xFF) << 8)
                | (b & 0xFF);
    }

    @Unique
    private static int to8Bit(float value) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        return Math.round(clamped * 255.0F);
    }
}