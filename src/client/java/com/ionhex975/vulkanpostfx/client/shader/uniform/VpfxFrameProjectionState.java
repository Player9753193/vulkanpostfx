package com.ionhex975.vulkanpostfx.client.shader.uniform;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;

/**
 * 抓取 world render 实际使用的 live projection / inverse projection，
 * 同时补充 view rotation / inverse view rotation。
 *
 * Shadow Apply Debug v1：
 * - 从 SceneDepth 重建 view position 需要 inverseProjection
 * - 从 view position 回到 world-relative direction 需要 inverseViewRotation
 */
public final class VpfxFrameProjectionState {
    private static final Object LOCK = new Object();

    private static boolean valid = false;

    private static final Matrix4f PROJECTION_MATRIX = new Matrix4f();
    private static final Matrix4f INVERSE_PROJECTION_MATRIX = new Matrix4f();

    private static final Matrix4f VIEW_ROTATION_MATRIX = new Matrix4f();
    private static final Matrix4f INVERSE_VIEW_ROTATION_MATRIX = new Matrix4f();

    private static float zNear = Camera.PROJECTION_Z_NEAR;
    private static float zFar = 1.0F;

    private static float screenWidth = 1.0F;
    private static float screenHeight = 1.0F;
    private static float aspect = 1.0F;

    private VpfxFrameProjectionState() {
    }

    public static void capture(
            CameraRenderState cameraState,
            int framebufferWidth,
            int framebufferHeight
    ) {
        if (cameraState == null || !cameraState.initialized) {
            clear();
            return;
        }

        synchronized (LOCK) {
            PROJECTION_MATRIX.set(cameraState.projectionMatrix);
            INVERSE_PROJECTION_MATRIX.set(cameraState.projectionMatrix).invert();

            VIEW_ROTATION_MATRIX.set(cameraState.viewRotationMatrix);
            INVERSE_VIEW_ROTATION_MATRIX.set(cameraState.viewRotationMatrix).invert();

            zNear = Camera.PROJECTION_Z_NEAR;
            zFar = Math.max(cameraState.depthFar, zNear + 1.0F);

            screenWidth = Math.max(1.0F, framebufferWidth);
            screenHeight = Math.max(1.0F, framebufferHeight);
            aspect = screenWidth / screenHeight;

            valid = true;
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            valid = false;

            PROJECTION_MATRIX.identity();
            INVERSE_PROJECTION_MATRIX.identity();

            VIEW_ROTATION_MATRIX.identity();
            INVERSE_VIEW_ROTATION_MATRIX.identity();

            zNear = Camera.PROJECTION_Z_NEAR;
            zFar = 1.0F;

            screenWidth = 1.0F;
            screenHeight = 1.0F;
            aspect = 1.0F;
        }
    }

    public static Snapshot snapshot() {
        synchronized (LOCK) {
            return new Snapshot(
                    valid,
                    new Matrix4f(PROJECTION_MATRIX),
                    new Matrix4f(INVERSE_PROJECTION_MATRIX),
                    new Matrix4f(VIEW_ROTATION_MATRIX),
                    new Matrix4f(INVERSE_VIEW_ROTATION_MATRIX),
                    zNear,
                    zFar,
                    screenWidth,
                    screenHeight,
                    aspect
            );
        }
    }

    public record Snapshot(
            boolean valid,
            Matrix4f projectionMatrix,
            Matrix4f inverseProjectionMatrix,
            Matrix4f viewRotationMatrix,
            Matrix4f inverseViewRotationMatrix,
            float zNear,
            float zFar,
            float screenWidth,
            float screenHeight,
            float aspect
    ) {
    }
}