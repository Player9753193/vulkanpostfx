package com.ionhex975.vulkanpostfx.client.shader.uniform;

import com.ionhex975.vulkanpostfx.client.shadow.ShadowFrameState;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

/**
 * Shadow Apply Debug v1 builtin UBO writer。
 *
 * layout(std140) uniform VpfxBuiltins {
 *     vec4 vpfx_TimeInfo;
 *     vec4 vpfx_ViewInfo;
 *     vec4 vpfx_SceneInfo;
 *     vec4 vpfx_ShadowInfo;
 *     mat4 vpfx_InverseProjectionMatrix;
 *     mat4 vpfx_InverseViewRotationMatrix;
 *     mat4 vpfx_ShadowViewProjectionMatrix;
 * };
 *
 * vpfx_TimeInfo:
 *   x = vpfx_Time
 *   y = vpfx_DeltaTime
 *   z = vpfx_GameTime
 *   w = vpfx_FrameIndex
 *
 * vpfx_ViewInfo:
 *   x = vpfx_CameraPos.x
 *   y = vpfx_CameraPos.y
 *   z = vpfx_CameraPos.z
 *   w = vpfx_RainStrength
 *
 * vpfx_SceneInfo:
 *   x = vpfx_ViewSize.x
 *   y = vpfx_ViewSize.y
 *   z = vpfx_InvViewSize.x
 *   w = vpfx_InvViewSize.y
 *
 * vpfx_ShadowInfo:
 *   x = vpfx_ZNear
 *   y = vpfx_ZFar
 *   z = vpfx_ShadowMapSize
 *   w = vpfx_ShadowBias
 */
public final class VpfxBuiltinUniformBuffer {
    public static final String BLOCK_NAME = "VpfxBuiltins";

    public static final int UBO_SIZE = new Std140SizeCalculator()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
            .putMat4f()
            .putMat4f()
            .putMat4f()
            .get();

    private static long lastGameTick = Long.MIN_VALUE;
    private static float lastPartialTick = Float.NaN;

    private static float cachedTimeSeconds = 0.0F;
    private static float cachedDeltaSeconds = 0.0F;
    private static float cachedGameTimeSeconds = 0.0F;
    private static float cachedFrameIndex = 0.0F;

    private static long lastFallbackNanoTime = System.nanoTime();

    private VpfxBuiltinUniformBuffer() {
    }

    public static void writeToExisting(GpuBuffer buffer) {
        if (buffer == null || buffer.isClosed()) {
            return;
        }

        RenderSystem.assertOnRenderThread();

        Snapshot snapshot = snapshot();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, UBO_SIZE)
                    .putVec4(
                            snapshot.timeSeconds,
                            snapshot.deltaSeconds,
                            snapshot.gameTimeSeconds,
                            snapshot.frameIndex
                    )
                    .putVec4(
                            snapshot.cameraX,
                            snapshot.cameraY,
                            snapshot.cameraZ,
                            snapshot.rainStrength
                    )
                    .putVec4(
                            snapshot.viewWidth,
                            snapshot.viewHeight,
                            snapshot.invViewWidth,
                            snapshot.invViewHeight
                    )
                    .putVec4(
                            snapshot.zNear,
                            snapshot.zFar,
                            snapshot.shadowMapSize,
                            snapshot.shadowBias
                    )
                    .putMat4f(snapshot.inverseProjectionMatrix)
                    .putMat4f(snapshot.inverseViewRotationMatrix)
                    .putMat4f(snapshot.shadowViewProjectionMatrix)
                    .get();

            RenderSystem.getDevice()
                    .createCommandEncoder()
                    .writeToBuffer(buffer.slice(), data);
        }
    }

    private static Snapshot snapshot() {
        Minecraft minecraft = Minecraft.getInstance();

        long currentNano = System.nanoTime();
        float fallbackDeltaSeconds = clamp((currentNano - lastFallbackNanoTime) / 1_000_000_000.0F, 0.0F, 0.25F);
        lastFallbackNanoTime = currentNano;

        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        long gameTick = minecraft.level != null ? minecraft.level.getGameTime() : Long.MIN_VALUE;

        boolean advancedFrame = gameTick != lastGameTick
                || Float.compare(partialTick, lastPartialTick) != 0;

        if (advancedFrame) {
            float deltaSeconds = clamp(
                    minecraft.getDeltaTracker().getRealtimeDeltaTicks() / 20.0F,
                    0.0F,
                    0.25F
            );

            if (!(deltaSeconds > 0.0F)) {
                deltaSeconds = fallbackDeltaSeconds;
            }

            cachedDeltaSeconds = deltaSeconds;
            cachedTimeSeconds += deltaSeconds;

            if (minecraft.level != null) {
                cachedGameTimeSeconds = (float) ((minecraft.level.getGameTime() + (double) partialTick) / 20.0);
            } else {
                cachedGameTimeSeconds = cachedTimeSeconds;
            }

            cachedFrameIndex += 1.0F;

            lastGameTick = gameTick;
            lastPartialTick = partialTick;
        }

        float cameraX = 0.0F;
        float cameraY = 0.0F;
        float cameraZ = 0.0F;

        ShadowFrameState shadowState = ShadowFrameState.get();
        if (shadowState.isValid()) {
            Vec3 cam = shadowState.getCameraPos();
            cameraX = (float) cam.x;
            cameraY = (float) cam.y;
            cameraZ = (float) cam.z;
        } else {
            Entity cameraEntity = minecraft.getCameraEntity();
            if (cameraEntity == null) {
                cameraEntity = minecraft.player;
            }

            if (cameraEntity != null) {
                cameraX = (float) cameraEntity.getX();
                cameraY = (float) cameraEntity.getY();
                cameraZ = (float) cameraEntity.getZ();
            }
        }

        float rainStrength = 0.0F;
        if (minecraft.level != null) {
            rainStrength = clamp(minecraft.level.getRainLevel(partialTick), 0.0F, 1.0F);
        }

        VpfxFrameProjectionState.Snapshot projection = VpfxFrameProjectionState.snapshot();

        float viewWidth = projection.valid() ? projection.screenWidth() : 1.0F;
        float viewHeight = projection.valid() ? projection.screenHeight() : 1.0F;
        float invViewWidth = 1.0F / Math.max(1.0F, viewWidth);
        float invViewHeight = 1.0F / Math.max(1.0F, viewHeight);

        Matrix4f inverseProjection = projection.valid()
                ? projection.inverseProjectionMatrix()
                : new Matrix4f().identity();

        Matrix4f inverseViewRotation = projection.valid()
                ? projection.inverseViewRotationMatrix()
                : new Matrix4f().identity();

        Matrix4f shadowViewProjection = shadowState.isValid()
                ? shadowState.getShadowViewProjectionMatrix()
                : new Matrix4f().identity();

        float shadowMapSize = shadowState.isShadowTargetReady()
                ? (float) shadowState.getShadowMapSize()
                : 0.0F;

        float shadowBias = 0.0015F;

        return new Snapshot(
                cachedTimeSeconds,
                cachedDeltaSeconds,
                cachedGameTimeSeconds,
                cachedFrameIndex,
                cameraX,
                cameraY,
                cameraZ,
                rainStrength,
                viewWidth,
                viewHeight,
                invViewWidth,
                invViewHeight,
                projection.valid() ? projection.zNear() : 0.05F,
                projection.valid() ? projection.zFar() : 1.0F,
                shadowMapSize,
                shadowBias,
                inverseProjection,
                inverseViewRotation,
                shadowViewProjection
        );
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Snapshot(
            float timeSeconds,
            float deltaSeconds,
            float gameTimeSeconds,
            float frameIndex,
            float cameraX,
            float cameraY,
            float cameraZ,
            float rainStrength,
            float viewWidth,
            float viewHeight,
            float invViewWidth,
            float invViewHeight,
            float zNear,
            float zFar,
            float shadowMapSize,
            float shadowBias,
            Matrix4f inverseProjectionMatrix,
            Matrix4f inverseViewRotationMatrix,
            Matrix4f shadowViewProjectionMatrix
    ) {
    }
}