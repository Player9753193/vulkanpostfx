package com.ionhex975.vulkanpostfx.client.mixin;

import com.ionhex975.vulkanpostfx.client.hook.PostFxHookBridge;
import com.ionhex975.vulkanpostfx.client.shader.uniform.VpfxFrameProjectionState;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererRenderLevelMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void vulkanpostfx$onRenderLevelHead(
            GraphicsResourceAllocator resourceAllocator,
            DeltaTracker deltaTracker,
            boolean renderOutline,
            CameraRenderState cameraState,
            Matrix4fc modelViewMatrix,
            GpuBufferSlice terrainFog,
            Vector4f fogColor,
            boolean shouldRenderSky,
            ChunkSectionsToRender chunkSectionsToRender,
            CallbackInfo ci
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        VpfxFrameProjectionState.capture(
                cameraState,
                minecraft.getMainRenderTarget().width,
                minecraft.getMainRenderTarget().height
        );

        PostFxHookBridge.onWorldRenderHead(
                minecraft,
                (LevelRenderer) (Object) this,
                deltaTracker,
                cameraState,
                renderOutline,
                shouldRenderSky
        );
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void vulkanpostfx$onRenderLevelTail(
            GraphicsResourceAllocator resourceAllocator,
            DeltaTracker deltaTracker,
            boolean renderOutline,
            CameraRenderState cameraState,
            Matrix4fc modelViewMatrix,
            GpuBufferSlice terrainFog,
            Vector4f fogColor,
            boolean shouldRenderSky,
            ChunkSectionsToRender chunkSectionsToRender,
            CallbackInfo ci
    ) {
        PostFxHookBridge.onWorldRenderTail(Minecraft.getInstance());
    }
}