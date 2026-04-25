package com.ionhex975.vulkanpostfx.client.shadow;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Shadow Matrices Lite - fast convergence debug version
 *
 * 当前目标：
 * 1. 固定 3D 方向光，切断动态 sunDir 链的不确定性
 * 2. 显式构造 light view
 * 3. 使用更保守的 Vulkan zZeroToOne 正交深度范围
 * 4. 先验证 shadow depth 是否真正写入，再回头收紧覆盖范围
 */
public final class ShadowMatricesLite {
    /**
     * 对于当前这条链：
     * - vertex 最终写 gl_Position = ProjMat * vec4(worldPos, 1.0)
     * - Projection 使用 setOrtho(..., zZeroToOne=true)
     *
     * 这里先使用“全正”的 near/far，避免跨 0 引起 clip-depth 约定混乱。
     */
    public static final float NEAR = 1.0F;
    public static final float FAR  = 2048.0F;

    /**
     * 方向光数学相机距离。
     * 不是太阳真实距离，只是构造 light view 用。
     */
    private static final float LIGHT_DISTANCE = 768.0F;

    /**
     * 覆盖放大系数。
     * 当前先宽一点，保证先看到地形轮廓。
     */
    private static final float COVERAGE_SCALE = 3.0F;

    /**
     * 固定 3D 光方向。
     * Y < 0：从上往下照
     * Z != 0：避免二维退化
     */
    private static final Vector3f FIXED_SUN_DIR =
            new Vector3f(0.35f, -0.87f, 0.35f).normalize();

    private ShadowMatricesLite() {
    }

    /**
     * 当前调试版：直接返回固定 3D sunDir。
     * 保留签名只是为了不改外部调用。
     */
    public static Vector3f createSunDirection(float shadowAngle, float sunPathRotationDegrees) {
        return new Vector3f(FIXED_SUN_DIR);
    }

    /**
     * 显式构造 light view。
     *
     * 约定：
     * - sunDir 表示“光照传播方向”（从光源指向场景）
     * - eye 放在 center 的反方向远处
     * - target 看向 center
     */
    private static Matrix4f createExplicitLightView(
            Vector3f center,
            Vector3f sunDir
    ) {
        Vector3f forward = new Vector3f(sunDir).normalize();

        Vector3f eye = new Vector3f(center)
                .sub(new Vector3f(forward).mul(LIGHT_DISTANCE));

        Vector3f target = new Vector3f(center);

        Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f);
        if (Math.abs(forward.dot(up)) > 0.98f) {
            up.set(0.0f, 0.0f, 1.0f);
        }

        return new Matrix4f().lookAt(
                eye.x, eye.y, eye.z,
                target.x, target.y, target.z,
                up.x, up.y, up.z
        );
    }

    /**
     * Vulkan zZeroToOne 正交投影。
     *
     * 当前调试策略：
     * - 覆盖范围先给大
     * - Z 范围只用正值 near/far
     */
    public static Matrix4f createOrthoMatrix(float halfPlaneLength, float nearPlane, float farPlane) {
        float h = Math.max(halfPlaneLength * COVERAGE_SCALE, 320.0f);

        return new Matrix4f().setOrtho(
                -h, h,
                -h, h,
                nearPlane, farPlane,
                true
        );
    }

    public static Matrix4f createModelViewMatrix(
            float shadowAngle,
            float shadowIntervalSize,
            float sunPathRotationDegrees,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        Vector3f sunDir = createSunDirection(shadowAngle, sunPathRotationDegrees);

        /*
         * 关键调试点：
         * 不把 shadow box 的中心死死钉在 camera 位置，
         * 而是沿光方向前推一点，让当前视野前方地形更容易落进盒子里。
         */
        Vector3f center = new Vector3f(
                (float) cameraX,
                (float) cameraY,
                (float) cameraZ
        ).add(new Vector3f(sunDir).mul(96.0f));

        return createExplicitLightView(center, sunDir);
    }

    public static Matrix4f createViewProjectionMatrix(
            float halfPlaneLength,
            float shadowAngle,
            float shadowIntervalSize,
            float sunPathRotationDegrees,
            double cameraX,
            double cameraY,
            double cameraZ
    ) {
        Matrix4f projection = createOrthoMatrix(halfPlaneLength, NEAR, FAR);
        Matrix4f modelView = createModelViewMatrix(
                shadowAngle,
                shadowIntervalSize,
                sunPathRotationDegrees,
                cameraX,
                cameraY,
                cameraZ
        );

        return new Matrix4f(projection).mul(modelView);
    }
}