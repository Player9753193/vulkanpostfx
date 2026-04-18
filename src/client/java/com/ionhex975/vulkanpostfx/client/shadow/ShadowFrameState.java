package com.ionhex975.vulkanpostfx.client.shadow;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 每帧阴影状态。
 *
 * Shadow Map v1 第二批：
 * - 明确 shadow pass enabled / target ready / pass executed / casters rendered
 * - 明确 terrain/entity 阴影距离
 * - 明确 shadowEntities / shadowPlayer
 * - 明确不再把 main depth mirror 当成真正 shadow map
 */
public final class ShadowFrameState {
    private static final ShadowFrameState INSTANCE = new ShadowFrameState();

    private Vec3 cameraPos = Vec3.ZERO;
    private float shadowAngle;
    private final Vector3f sunDirection = new Vector3f(0.0F, -1.0F, 0.0F);
    private final Matrix4f shadowViewMatrix = new Matrix4f();
    private final Matrix4f shadowProjectionMatrix = new Matrix4f();
    private final Matrix4f shadowViewProjectionMatrix = new Matrix4f();

    private boolean valid;
    private boolean shadowPassEnabled;
    private boolean shadowTargetReady;
    private int shadowMapSize;

    private float terrainShadowDistance = 160.0F;
    private float entityShadowDistance = 64.0F;
    private boolean shadowEntities = true;
    private boolean shadowPlayer = true;

    private boolean shadowRenderRequested;
    private boolean shadowPassExecuted;
    private boolean shadowCastersRendered;
    private boolean shadowDepthMirrored;

    private ShadowFrameState() {
    }

    public static ShadowFrameState get() {
        return INSTANCE;
    }

    public void update(
            Vec3 cameraPos,
            float shadowAngle,
            Vector3f sunDirection,
            Matrix4f shadowViewMatrix,
            Matrix4f shadowProjectionMatrix
    ) {
        this.cameraPos = cameraPos;
        this.shadowAngle = shadowAngle;
        this.sunDirection.set(sunDirection);
        this.shadowViewMatrix.set(shadowViewMatrix);
        this.shadowProjectionMatrix.set(shadowProjectionMatrix);
        this.shadowViewProjectionMatrix.set(shadowProjectionMatrix).mul(shadowViewMatrix);

        this.valid = true;
        this.shadowPassExecuted = false;
        this.shadowCastersRendered = false;
        this.shadowDepthMirrored = false;
    }

    public void setShadowPassEnabled(boolean enabled) {
        this.shadowPassEnabled = enabled;
    }

    public boolean isShadowPassEnabled() {
        return shadowPassEnabled;
    }

    public void setShadowTargetState(boolean ready, int size) {
        this.shadowTargetReady = ready;
        this.shadowMapSize = size;
    }

    public void setShadowDistances(float terrainShadowDistance, float entityShadowDistance) {
        this.terrainShadowDistance = terrainShadowDistance;
        this.entityShadowDistance = entityShadowDistance;
    }

    public void setShadowCasterControls(boolean shadowEntities, boolean shadowPlayer) {
        this.shadowEntities = shadowEntities;
        this.shadowPlayer = shadowPlayer;
    }

    public void requestShadowRender() {
        this.shadowRenderRequested = true;
    }

    public boolean consumeShadowRenderRequest() {
        boolean requested = this.shadowRenderRequested;
        this.shadowRenderRequested = false;
        return requested;
    }

    public void markShadowPassExecuted(boolean castersRendered) {
        this.shadowPassExecuted = true;
        this.shadowCastersRendered = castersRendered;
    }

    public boolean wasShadowPassExecuted() {
        return this.shadowPassExecuted;
    }

    public boolean wereShadowCastersRendered() {
        return this.shadowCastersRendered;
    }

    public void markShadowDepthMirrored() {
        this.shadowDepthMirrored = true;
    }

    public boolean wasShadowDepthMirrored() {
        return this.shadowDepthMirrored;
    }

    public void invalidate() {
        this.valid = false;
        this.shadowPassEnabled = false;
        this.shadowTargetReady = false;
        this.shadowMapSize = 0;
        this.shadowRenderRequested = false;
        this.shadowPassExecuted = false;
        this.shadowCastersRendered = false;
        this.shadowDepthMirrored = false;
    }

    public boolean isValid() {
        return this.valid;
    }

    public boolean isShadowTargetReady() {
        return this.shadowTargetReady;
    }

    public int getShadowMapSize() {
        return this.shadowMapSize;
    }

    public float getTerrainShadowDistance() {
        return terrainShadowDistance;
    }

    public float getEntityShadowDistance() {
        return entityShadowDistance;
    }

    public boolean isShadowEntities() {
        return shadowEntities;
    }

    public boolean isShadowPlayer() {
        return shadowPlayer;
    }

    public Vec3 getCameraPos() {
        return this.cameraPos;
    }

    public float getShadowAngle() {
        return this.shadowAngle;
    }

    public Vector3f getSunDirection() {
        return new Vector3f(this.sunDirection);
    }

    public Matrix4f getShadowViewMatrix() {
        return new Matrix4f(this.shadowViewMatrix);
    }

    public Matrix4f getShadowProjectionMatrix() {
        return new Matrix4f(this.shadowProjectionMatrix);
    }

    public Matrix4f getShadowViewProjectionMatrix() {
        return new Matrix4f(this.shadowViewProjectionMatrix);
    }
}