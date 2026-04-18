package com.ionhex975.vulkanpostfx.client.shader.uniform;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

/**
 * Projection / Linear Depth v3 builtin UBO writer。
 *
 * layout(std140) uniform VpfxBuiltins {
 *     mat4 vpfx_ProjectionMatrix;
 *     mat4 vpfx_InverseProjectionMatrix;
 *     vec4 vpfx_TimeInfo;
 *     vec4 vpfx_ViewInfo;
 *     vec4 vpfx_ProjectionInfo;
 *     vec4 vpfx_ScreenInfo;
 * };
 *
 * 关键变化：
 * - 不再从 GameRenderState 猜 projection；
 * - 直接从 world render 阶段抓下来的 VpfxFrameProjectionState 读。
 */
public final class VpfxBuiltinUniformBuffer {
    public static final String BLOCK_NAME = "VpfxBuiltins";

    public static final int UBO_SIZE = new Std140SizeCalculator()
            .putMat4f()
            .putMat4f()
            .putVec4()
            .putVec4()
            .putVec4()
            .putVec4()
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
                    .putMat4f(snapshot.projectionMatrix)
                    .putMat4f(snapshot.inverseProjectionMatrix)
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
                            snapshot.zNear,
                            snapshot.zFar,
                            snapshot.aspect,
                            0.0F
                    )
                    .putVec4(
                            snapshot.screenWidth,
                            snapshot.screenHeight,
                            snapshot.invScreenWidth,
                            snapshot.invScreenHeight
                    )
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

        double camX = 0.0;
        double camY = 0.0;
        double camZ = 0.0;

        Entity cameraEntity = minecraft.getCameraEntity();
        if (cameraEntity == null) {
            cameraEntity = minecraft.player;
        }

        if (cameraEntity != null) {
            camX = cameraEntity.getX();
            camY = cameraEntity.getY();
            camZ = cameraEntity.getZ();
        }

        float rainStrength = 0.0F;
        if (minecraft.level != null) {
            rainStrength = clamp(minecraft.level.getRainLevel(partialTick), 0.0F, 1.0F);
        }

        VpfxFrameProjectionState.Snapshot projectionState = VpfxFrameProjectionState.snapshot();

        Matrix4f projectionMatrix = new Matrix4f();
        Matrix4f inverseProjectionMatrix = new Matrix4f();

        float zNear;
        float zFar;
        float aspect;
        float screenWidth;
        float screenHeight;

        if (projectionState.valid()) {
            projectionMatrix.set(projectionState.projectionMatrix());
            inverseProjectionMatrix.set(projectionState.inverseProjectionMatrix());
            zNear = projectionState.zNear();
            zFar = projectionState.zFar();
            aspect = projectionState.aspect();
            screenWidth = projectionState.screenWidth();
            screenHeight = projectionState.screenHeight();
        } else {
            projectionMatrix.identity();
            inverseProjectionMatrix.identity();
            zNear = 0.05F;
            zFar = 1.0F;
            screenWidth = Math.max(1.0F, minecraft.getMainRenderTarget().width);
            screenHeight = Math.max(1.0F, minecraft.getMainRenderTarget().height);
            aspect = screenWidth / screenHeight;
        }

        float invScreenWidth = 1.0F / screenWidth;
        float invScreenHeight = 1.0F / screenHeight;

        return new Snapshot(
                projectionMatrix,
                inverseProjectionMatrix,
                cachedTimeSeconds,
                cachedDeltaSeconds,
                cachedGameTimeSeconds,
                cachedFrameIndex,
                (float) camX,
                (float) camY,
                (float) camZ,
                rainStrength,
                zNear,
                zFar,
                aspect,
                screenWidth,
                screenHeight,
                invScreenWidth,
                invScreenHeight
        );
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Snapshot(
            Matrix4f projectionMatrix,
            Matrix4f inverseProjectionMatrix,
            float timeSeconds,
            float deltaSeconds,
            float gameTimeSeconds,
            float frameIndex,
            float cameraX,
            float cameraY,
            float cameraZ,
            float rainStrength,
            float zNear,
            float zFar,
            float aspect,
            float screenWidth,
            float screenHeight,
            float invScreenWidth,
            float invScreenHeight
    ) {
    }
}