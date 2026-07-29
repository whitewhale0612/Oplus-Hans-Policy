package io.github.whitewhale.hanspolicy.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public final class PolicySnapshotTest {
    @Test
    public void masterSwitchDisablesAllFastPathFlags() {
        PolicyRule rule = rule("com.example.app", true, false,
                PolicyRule.FREEZE_FAST, PolicyRule.PROXY_JOB, true,
                PolicyRule.PACKET_WAKE_BLOCK, PolicyRule.ALARM_WAKE_BLOCK,
                PolicyRule.WAKE_ASYNC_BINDER, true, 5_000L, 10_000L);
        PolicySnapshot snapshot = new PolicySnapshot(false, 1L,
                Collections.singletonList(rule));

        assertFalse(snapshot.hasEnabledRules());
        assertFalse(snapshot.hasFreezeInterventions());
        assertFalse(snapshot.hasBypassProxyFlag(PolicyRule.PROXY_JOB));
        assertFalse(snapshot.hasKeepNetworkRules());
        assertFalse(snapshot.hasPacketWakeControls());
        assertFalse(snapshot.hasAlarmWakeControls());
        assertFalse(snapshot.hasBlockedWakeSource(PolicyRule.WAKE_ASYNC_BINDER));
        assertNull(snapshot.getEnabledRule(rule.packageName));
    }

    @Test
    public void disabledRulesDoNotContributeFastPathFlags() {
        PolicyRule rule = rule("com.example.disabled", false, true,
                PolicyRule.ALL_FREEZE_SOURCES, PolicyRule.ALL_PROXY_FLAGS, true,
                PolicyRule.PACKET_WAKE_BLOCK, PolicyRule.ALARM_WAKE_BLOCK,
                PolicyRule.ALL_WAKE_SOURCES, true, 5_000L, 10_000L);
        PolicySnapshot snapshot = new PolicySnapshot(true, 2L,
                Collections.singletonList(rule));

        assertFalse(snapshot.hasEnabledRules());
        assertFalse(snapshot.hasFullExemptRules());
        assertFalse(snapshot.hasTimingOverrides());
        assertFalse(snapshot.hasFreezeInterventions());
        assertFalse(snapshot.hasPacketWakeBlocks());
        assertFalse(snapshot.hasAlarmWakeBlocks());
        assertNull(snapshot.getEnabledRule(rule.packageName));
    }

    @Test
    public void enabledRulesAggregateOnlyTheirActiveControls() {
        PolicyRule packetRule = rule("com.example.packet", true, false,
                PolicyRule.FREEZE_FAST, PolicyRule.PROXY_JOB, false,
                PolicyRule.PACKET_WAKE_THROTTLE, PolicyRule.ALARM_WAKE_ALLOW,
                PolicyRule.WAKE_ASYNC_BINDER, false, 0L, 0L);
        PolicyRule exemptRule = rule("com.example.exempt", true, true,
                0, 0, true, PolicyRule.PACKET_WAKE_BLOCK,
                PolicyRule.ALARM_WAKE_BLOCK, PolicyRule.ALL_WAKE_SOURCES,
                true, 5_000L, 10_000L);
        PolicySnapshot snapshot = new PolicySnapshot(true, 3L,
                Arrays.asList(packetRule, exemptRule));

        assertTrue(snapshot.hasEnabledRules());
        assertTrue(snapshot.hasFullExemptRules());
        assertTrue(snapshot.hasTimingOverrides());
        assertTrue(snapshot.hasFreezeInterventions());
        assertTrue(snapshot.hasBlockedFreezeSource(PolicyRule.FREEZE_FAST));
        assertTrue(snapshot.hasBypassProxyFlag(PolicyRule.PROXY_JOB));
        assertTrue(snapshot.hasKeepNetworkRules());
        assertTrue(snapshot.hasPacketWakeControls());
        assertFalse(snapshot.hasPacketWakeBlocks());
        assertFalse(snapshot.hasAlarmWakeControls());
        assertFalse(snapshot.hasAlarmWakeBlocks());
        assertTrue(snapshot.hasBlockedWakeSource(PolicyRule.WAKE_ASYNC_BINDER));
        assertSame(packetRule, snapshot.getEnabledRule(packetRule.packageName));
    }

    @Test
    public void duplicatePackageUsesOnlyLastRuleForFastPathFlags() {
        PolicyRule active = rule("com.example.duplicate", true, false,
                PolicyRule.FREEZE_FAST, PolicyRule.PROXY_JOB, true,
                PolicyRule.PACKET_WAKE_BLOCK, PolicyRule.ALARM_WAKE_BLOCK,
                PolicyRule.WAKE_ASYNC_BINDER, true, 5_000L, 10_000L);
        PolicyRule disabled = rule("com.example.duplicate", false, false,
                0, 0, false, PolicyRule.PACKET_WAKE_ALLOW,
                PolicyRule.ALARM_WAKE_ALLOW, 0, false, 0L, 0L);
        PolicySnapshot snapshot = new PolicySnapshot(true, 4L,
                Arrays.asList(active, disabled));

        assertFalse(snapshot.hasEnabledRules());
        assertFalse(snapshot.hasFreezeInterventions());
        assertFalse(snapshot.hasPacketWakeControls());
        assertNull(snapshot.getEnabledRule(active.packageName));
    }

    private static PolicyRule rule(String packageName, boolean enabled,
                                   boolean fullExempt, int blockedFreezeSources,
                                   int bypassProxyFlags, boolean keepNetwork,
                                   int packetWakeMode, int alarmWakeMode,
                                   int blockedWakeSources, boolean customTiming,
                                   long packetRefreezeMs, long alarmRefreezeMs) {
        return new PolicyRule(packageName, enabled, fullExempt, customTiming,
                30_000L, 60_000L, blockedFreezeSources, bypassProxyFlags,
                keepNetwork, packetWakeMode,
                PolicyRule.DEFAULT_PACKET_WAKE_COOLDOWN_MS, packetRefreezeMs,
                alarmWakeMode, PolicyRule.DEFAULT_ALARM_WAKE_COOLDOWN_MS,
                alarmRefreezeMs, blockedWakeSources);
    }
}
