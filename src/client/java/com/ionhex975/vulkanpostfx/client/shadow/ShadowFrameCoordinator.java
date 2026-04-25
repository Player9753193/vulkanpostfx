package com.ionhex975.vulkanpostfx.client.shadow;

import com.ionhex975.vulkanpostfx.VulkanPostFX;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 每帧同步阴影矩阵、太阳方向。
 *
 * 第二批整理：
 * - 这里只负责“算状态”
 * - 不再在这里 prepare shadow target
 * - 不再隐式推进 shadow pass 生命周期
 */
public final class ShadowFrameCoordinator {
    private static final float DEFAULT_SHADOW_HALF_PLANE = 320.0F;
    private static final float DEFAULT_SHADOW_INTERVAL = 0.0F;
    private static final float DEFAULT_SUN_PATH_ROTATION = 0.0F;

    private static int debugLogCounter;

    private ShadowFrameCoordinator() {
    }

    public static void syncFrame(
            Minecraft minecraft,
            DeltaTracker deltaTracker,
            CameraRenderState cameraState
    ) {
        if (minecraft.level == null || cameraState == null || !cameraState.initialized) {
            ShadowFrameState.get().invalidate();
            return;
        }

        float rawSunAngle = minecraft.gameRenderer
                .getMainCamera()
                .attributeProbe()
                .getValue(EnvironmentAttributes.SUN_ANGLE, 0.0F);

        /*
         * EnvironmentAttributes.SUN_ANGLE 按角度制处理。
         * 先归一化到 [0, 360)，再转成 [0, 1) 的 shadowAngle。
         */
        float sunAngleDegrees = rawSunAngle % 360.0F;
        if (sunAngleDegrees < 0.0F) {
            sunAngleDegrees += 360.0F;
        }

        float shadowAngle = sunAngleDegrees / 360.0F;

        Vector3f sunDirection = SunDirectionProvider.fromSunAngle(
                shadowAngle,
                DEFAULT_SUN_PATH_ROTATION
        );

        Matrix4f shadowView = ShadowMatricesLite.createModelViewMatrix(
                shadowAngle,
                DEFAULT_SHADOW_INTERVAL,
                DEFAULT_SUN_PATH_ROTATION,
                cameraState.pos.x,
                cameraState.pos.y,
                cameraState.pos.z
        );

        Matrix4f shadowProjection = ShadowMatricesLite.createOrthoMatrix(
                DEFAULT_SHADOW_HALF_PLANE,
                ShadowMatricesLite.NEAR,
                ShadowMatricesLite.FAR
        );

//        Matrix4f shadowVP = new Matrix4f(shadowProjection).mul(shadowView);
        ShadowFrameState.get().update(
                cameraState.pos,
                shadowAngle,
                sunDirection,
                shadowView,
                shadowProjection
        );

        debugLogCounter++;
        if (debugLogCounter >= 60) {
            debugLogCounter = 0;
            VulkanPostFX.LOGGER.info(
                    "[{}] Shadow pipeline v1 synced: cameraPos={}, rawSunAngle={}, shadowAngle={}, sunAngleDegrees={}, sunDir=({}, {}, {})",
                    VulkanPostFX.MOD_ID,
                    cameraState.pos,
                    round3(rawSunAngle),
                    round3(shadowAngle),
                    round3(sunAngleDegrees),
                    round3(sunDirection.x),
                    round3(sunDirection.y),
                    round3(sunDirection.z)
            );
        }
    }

    private static float round3(float v) {
        return Math.round(v * 1000.0F) / 1000.0F;
    }
}