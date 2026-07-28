package io.github.whitewhale.hanspolicy.model;

import java.util.Objects;

public final class PolicyRule {
    public static final int PACKET_WAKE_ALLOW = 0;
    public static final int PACKET_WAKE_THROTTLE = 1;
    public static final int PACKET_WAKE_BLOCK = 2;
    public static final long DEFAULT_PACKET_WAKE_COOLDOWN_MS = 60_000L;

    public static final int FREEZE_NORMAL = 1;
    public static final int FREEZE_FAST = 1 << 1;
    public static final int FREEZE_SUPER = 1 << 2;
    public static final int FREEZE_PRELOAD = 1 << 3;
    public static final int ALL_FREEZE_SOURCES = FREEZE_NORMAL | FREEZE_FAST
            | FREEZE_SUPER | FREEZE_PRELOAD;

    public static final int PROXY_SERVICE = 1;
    public static final int PROXY_BROADCAST = 1 << 1;
    public static final int PROXY_JOB = 1 << 2;
    public static final int PROXY_SENSOR = 1 << 3;
    public static final int PROXY_BINDER = 1 << 4;
    public static final int PROXY_ALARM = 1 << 5;
    public static final int PROXY_WAKELOCK = 1 << 6;
    public static final int PROXY_GPS = 1 << 7;
    public static final int PROXY_AUDIO = 1 << 8;
    public static final int PROXY_BT_SCAN = 1 << 9;
    public static final int ALL_PROXY_FLAGS = PROXY_SERVICE | PROXY_BROADCAST
            | PROXY_JOB | PROXY_SENSOR | PROXY_BINDER | PROXY_ALARM
            | PROXY_WAKELOCK | PROXY_GPS | PROXY_AUDIO | PROXY_BT_SCAN;

    public final String packageName;
    public final boolean enabled;
    public final boolean fullExempt;
    public final boolean customTiming;
    public final long rToMMs;
    public final long mToFMs;
    public final int blockedFreezeSources;
    public final int bypassProxyFlags;
    public final boolean keepNetwork;
    public final int packetWakeMode;
    public final long packetWakeCooldownMs;
    public final long packetRefreezeMs;

    public PolicyRule(String packageName, boolean enabled, boolean fullExempt,
                      boolean customTiming, long rToMMs, long mToFMs,
                      int blockedFreezeSources, int bypassProxyFlags,
                      boolean keepNetwork, int packetWakeMode,
                      long packetWakeCooldownMs, long packetRefreezeMs) {
        this.packageName = Objects.requireNonNull(packageName);
        this.enabled = enabled;
        this.fullExempt = fullExempt;
        this.customTiming = customTiming;
        this.rToMMs = rToMMs;
        this.mToFMs = mToFMs;
        this.blockedFreezeSources = blockedFreezeSources;
        this.bypassProxyFlags = bypassProxyFlags;
        this.keepNetwork = keepNetwork;
        this.packetWakeMode = packetWakeMode;
        this.packetWakeCooldownMs = packetWakeCooldownMs;
        this.packetRefreezeMs = packetRefreezeMs;
    }

    public String key() {
        return packageName;
    }

    public boolean isExempt() {
        return enabled && fullExempt;
    }

    public boolean hasCustomTiming() {
        return enabled && customTiming;
    }

    public boolean blocksFreeze(int source) {
        return enabled && (blockedFreezeSources & source) != 0;
    }

    public boolean bypassesProxy(int proxyFlag) {
        return enabled && (bypassProxyFlags & proxyFlag) != 0;
    }

    public boolean keepsNetwork() {
        return enabled && keepNetwork;
    }

    public boolean blocksPacketWake() {
        return enabled && packetWakeMode == PACKET_WAKE_BLOCK;
    }

    public boolean throttlesPacketWake() {
        return enabled && packetWakeMode == PACKET_WAKE_THROTTLE;
    }

    public boolean hasCustomPacketRefreeze() {
        return enabled && packetRefreezeMs > 0L;
    }

    public boolean hasIntervention() {
        return enabled && (fullExempt || customTiming || blockedFreezeSources != 0
                || bypassProxyFlags != 0 || keepNetwork
                || packetWakeMode != PACKET_WAKE_ALLOW || packetRefreezeMs > 0L);
    }

    public boolean needsCleanupComparedTo(PolicyRule previous) {
        if (!enabled) {
            return false;
        }
        if (previous == null || !previous.enabled) {
            return fullExempt || blockedFreezeSources != 0
                    || bypassProxyFlags != 0 || keepNetwork;
        }
        return (fullExempt && !previous.fullExempt)
                || (blockedFreezeSources & ~previous.blockedFreezeSources) != 0
                || (bypassProxyFlags & ~previous.bypassProxyFlags) != 0
                || (keepNetwork && !previous.keepNetwork);
    }
}
