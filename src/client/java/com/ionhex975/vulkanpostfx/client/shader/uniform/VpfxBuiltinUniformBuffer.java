package com.ionhex975.vulkanpostfx.client.shader.uniform;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

/**
 * 当前 v1 builtin UBO writer。
 *
 * layout(std140) uniform VpfxBuiltins {
 *     vec4 vpfx_TimeInfo;
 *     vec4 vpfx_ViewInfo;
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
 */
public final class VpfxBuiltinUniformBuffer {
    public static final String BLOCK_NAME = "VpfxBuiltins";

    public static final int UBO_SIZE = new Std140SizeCalculator()
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

    /**
     * 更新一个已经存在的 PostPass custom uniform buffer。
     *
     * 注意：
     * 这里只写已有 buffer。
     * buffer 的创建由 PostPass 构造阶段、来自 JSON uniforms 完成。
     */
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

        return new Snapshot(
                cachedTimeSeconds,
                cachedDeltaSeconds,
                cachedGameTimeSeconds,
                cachedFrameIndex,
                (float) camX,
                (float) camY,
                (float) camZ,
                rainStrength
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
            float rainStrength
    ) {
    }
}