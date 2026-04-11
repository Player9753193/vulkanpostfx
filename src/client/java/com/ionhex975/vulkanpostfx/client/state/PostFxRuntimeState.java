package com.ionhex975.vulkanpostfx.client.state;

import net.minecraft.resources.Identifier;

/**
 * 当前阶段状态：
 * 1. 客户端是否初始化；
 * 2. 是否已命中真正的世界渲染入口；
 * 3. 是否已命中 PostFX 候选插入位；
 * 4. 当前图形后端名称；
 * 5. 调试效果是否启用；
 * 6. 是否请求在下一帧重新应用效果（用于资源热重载后恢复）；
 * 7. 当前调试效果逻辑名（builtin fallback 使用）；
 * 8. 当前外部 ZIP 运行时入口 post effect id（若存在，则优先使用）。
 */
public final class PostFxRuntimeState {
    private static volatile boolean clientInitialized;
    private static volatile boolean worldRenderObserved;
    private static volatile boolean postSlotObserved;
    private static volatile boolean debugEffectEnabled;
    private static volatile boolean reapplyRequested;
    private static volatile String backendName = "unknown";
    private static volatile String activeEffectKey = "debug_invert";
    private static volatile Identifier activeExternalPostEffectId;

    private PostFxRuntimeState() {
    }

    public static void markClientInit() {
        clientInitialized = true;
    }

    public static boolean isClientInitialized() {
        return clientInitialized;
    }

    public static void markWorldRenderObserved() {
        worldRenderObserved = true;
    }

    public static boolean isWorldRenderObserved() {
        return worldRenderObserved;
    }

    public static void markPostSlotObserved() {
        postSlotObserved = true;
    }

    public static boolean isPostSlotObserved() {
        return postSlotObserved;
    }

    public static void setBackendName(String backend) {
        backendName = backend;
    }

    public static String getBackendName() {
        return backendName;
    }

    public static boolean isDebugEffectEnabled() {
        return debugEffectEnabled;
    }

    public static void setDebugEffectEnabled(boolean enabled) {
        debugEffectEnabled = enabled;
    }

    public static boolean toggleDebugEffectEnabled() {
        debugEffectEnabled = !debugEffectEnabled;
        reapplyRequested = true;
        return debugEffectEnabled;
    }

    public static void requestReapply() {
        reapplyRequested = true;
    }

    public static boolean consumeReapplyRequest() {
        boolean requested = reapplyRequested;
        reapplyRequested = false;
        return requested;
    }

    public static String getActiveEffectKey() {
        return activeEffectKey;
    }

    public static void setActiveEffectKey(String effectKey) {
        activeEffectKey = effectKey;
        reapplyRequested = true;
    }

    public static Identifier getActiveExternalPostEffectId() {
        return activeExternalPostEffectId;
    }

    public static void setActiveExternalPostEffectId(Identifier id) {
        activeExternalPostEffectId = id;
        reapplyRequested = true;
    }

    public static void clearActiveExternalPostEffectId() {
        activeExternalPostEffectId = null;
        reapplyRequested = true;
    }
}