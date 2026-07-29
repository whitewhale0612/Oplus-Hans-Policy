package io.github.whitewhale.hanspolicy.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PolicySnapshot {
    public final boolean enabled;
    public final long revision;
    private final Map<String, PolicyRule> rules;
    private final int enabledRuleCount;
    private final boolean hasFullExemptRules;
    private final boolean hasTimingOverrides;
    private final int blockedFreezeSources;
    private final int bypassProxyFlags;
    private final boolean hasKeepNetworkRules;
    private final boolean hasPacketWakeControls;
    private final boolean hasPacketWakeBlocks;
    private final boolean hasAlarmWakeControls;
    private final boolean hasAlarmWakeBlocks;
    private final int blockedWakeSources;

    public PolicySnapshot(boolean enabled, long revision, List<PolicyRule> rules) {
        this.enabled = enabled;
        this.revision = revision;
        LinkedHashMap<String, PolicyRule> copy = new LinkedHashMap<>();
        int activeCount = 0;
        boolean fullExempt = false;
        boolean timingOverrides = false;
        int freezeSources = 0;
        int proxyFlags = 0;
        boolean keepNetwork = false;
        boolean packetControls = false;
        boolean packetBlocks = false;
        boolean alarmControls = false;
        boolean alarmBlocks = false;
        int wakeSources = 0;
        for (PolicyRule rule : rules) {
            copy.put(rule.key(), rule);
        }
        for (PolicyRule rule : copy.values()) {
            if (!rule.enabled) {
                continue;
            }
            activeCount++;
            fullExempt |= rule.fullExempt;
            timingOverrides |= rule.customTiming
                    || rule.packetRefreezeMs > 0L || rule.alarmRefreezeMs > 0L;
            freezeSources |= rule.blockedFreezeSources;
            proxyFlags |= rule.bypassProxyFlags;
            keepNetwork |= rule.keepNetwork;
            packetControls |= !rule.fullExempt
                    && rule.packetWakeMode != PolicyRule.PACKET_WAKE_ALLOW;
            packetBlocks |= !rule.fullExempt
                    && rule.packetWakeMode == PolicyRule.PACKET_WAKE_BLOCK;
            alarmControls |= !rule.fullExempt
                    && rule.alarmWakeMode != PolicyRule.ALARM_WAKE_ALLOW;
            alarmBlocks |= !rule.fullExempt
                    && rule.alarmWakeMode == PolicyRule.ALARM_WAKE_BLOCK;
            if (!rule.fullExempt) {
                wakeSources |= rule.blockedWakeSources;
            }
        }
        this.rules = Collections.unmodifiableMap(copy);
        enabledRuleCount = activeCount;
        hasFullExemptRules = fullExempt;
        hasTimingOverrides = timingOverrides;
        blockedFreezeSources = freezeSources;
        bypassProxyFlags = proxyFlags;
        hasKeepNetworkRules = keepNetwork;
        hasPacketWakeControls = packetControls;
        hasPacketWakeBlocks = packetBlocks;
        hasAlarmWakeControls = alarmControls;
        hasAlarmWakeBlocks = alarmBlocks;
        blockedWakeSources = wakeSources;
    }

    public static PolicySnapshot disabled() {
        return new PolicySnapshot(false, 0L, Collections.emptyList());
    }

    public PolicyRule getRule(String packageName) {
        if (!enabled || packageName == null) {
            return null;
        }
        return rules.get(packageName);
    }

    public PolicyRule getEnabledRule(String packageName) {
        PolicyRule rule = getRule(packageName);
        return rule != null && rule.enabled ? rule : null;
    }

    public boolean hasEnabledRules() {
        return enabled && enabledRuleCount > 0;
    }

    public boolean hasFullExemptRules() {
        return hasEnabledRules() && hasFullExemptRules;
    }

    public boolean hasTimingOverrides() {
        return hasEnabledRules() && hasTimingOverrides;
    }

    public boolean hasFreezeInterventions() {
        return hasEnabledRules() && (hasFullExemptRules || blockedFreezeSources != 0);
    }

    public boolean hasBlockedFreezeSource(int source) {
        return hasEnabledRules() && (blockedFreezeSources & source) != 0;
    }

    public boolean hasBypassProxyFlag(int flag) {
        return hasEnabledRules() && (bypassProxyFlags & flag) != 0;
    }

    public boolean hasKeepNetworkRules() {
        return hasEnabledRules() && hasKeepNetworkRules;
    }

    public boolean hasPacketWakeControls() {
        return hasEnabledRules() && hasPacketWakeControls;
    }

    public boolean hasPacketWakeBlocks() {
        return hasEnabledRules() && hasPacketWakeBlocks;
    }

    public boolean hasAlarmWakeControls() {
        return hasEnabledRules() && hasAlarmWakeControls;
    }

    public boolean hasAlarmWakeBlocks() {
        return hasEnabledRules() && hasAlarmWakeBlocks;
    }

    public boolean hasBlockedWakeSource(int source) {
        return hasEnabledRules() && (blockedWakeSources & source) != 0;
    }

    public List<PolicyRule> getRules() {
        return new ArrayList<>(rules.values());
    }
}
