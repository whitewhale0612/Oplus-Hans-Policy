package io.github.whitewhale.hanspolicy.model;

import io.github.whitewhale.hanspolicy.Constants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class PolicyCodec {
    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*");

    private PolicyCodec() {
    }

    public static String encode(PolicySnapshot snapshot) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("version", Constants.SCHEMA_VERSION);
        root.put("enabled", snapshot.enabled);
        root.put("revision", snapshot.revision);
        JSONArray rules = new JSONArray();
        for (PolicyRule rule : snapshot.getRules()) {
            JSONObject item = new JSONObject();
            item.put("package", rule.packageName);
            item.put("enabled", rule.enabled);
            item.put("fullExempt", rule.fullExempt);
            item.put("customTiming", rule.customTiming);
            item.put("rToMMs", rule.rToMMs);
            item.put("mToFMs", rule.mToFMs);
            item.put("blockedFreezeSources", rule.blockedFreezeSources);
            item.put("bypassProxyFlags", rule.bypassProxyFlags);
            item.put("keepNetwork", rule.keepNetwork);
            item.put("packetWakeMode", rule.packetWakeMode);
            item.put("packetWakeCooldownMs", rule.packetWakeCooldownMs);
            item.put("packetRefreezeMs", rule.packetRefreezeMs);
            rules.put(item);
        }
        root.put("rules", rules);
        return root.toString();
    }

    public static PolicySnapshot decode(String json) throws JSONException {
        if (json == null || json.isEmpty()) {
            return PolicySnapshot.disabled();
        }
        JSONObject root = new JSONObject(json);
        int version = root.optInt("version", -1);
        if (version == 1) {
            return decodeVersion1(root);
        }
        if (version != 2 && version != Constants.SCHEMA_VERSION) {
            throw new JSONException("Unsupported policy schema");
        }
        List<PolicyRule> rules = new ArrayList<>();
        JSONArray array = root.optJSONArray("rules");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String packageName = item.getString("package").trim();
                boolean enabled = item.optBoolean("enabled", true);
                boolean fullExempt = item.optBoolean("fullExempt", false);
                boolean customTiming = item.optBoolean("customTiming", false);
                long rToM = item.optLong("rToMMs", 60_000L);
                long mToF = item.optLong("mToFMs", 60_000L);
                int blockedSources = item.optInt("blockedFreezeSources", 0);
                int bypassFlags = item.optInt("bypassProxyFlags", 0);
                boolean keepNetwork = item.optBoolean("keepNetwork", false);
                int packetWakeMode = item.optInt("packetWakeMode",
                        PolicyRule.PACKET_WAKE_ALLOW);
                long packetWakeCooldownMs = item.optLong("packetWakeCooldownMs",
                        PolicyRule.DEFAULT_PACKET_WAKE_COOLDOWN_MS);
                long packetRefreezeMs = item.optLong("packetRefreezeMs", 0L);
                validate(packageName, rToM, mToF, blockedSources, bypassFlags,
                        packetWakeMode, packetWakeCooldownMs, packetRefreezeMs);
                rules.add(new PolicyRule(packageName, enabled, fullExempt, customTiming,
                        rToM, mToF, blockedSources, bypassFlags, keepNetwork,
                        packetWakeMode, packetWakeCooldownMs, packetRefreezeMs));
            }
        }
        return new PolicySnapshot(root.optBoolean("enabled", false),
                Math.max(0L, root.optLong("revision", 0L)), rules);
    }

    private static PolicySnapshot decodeVersion1(JSONObject root) throws JSONException {
        Map<String, PolicyRule> migrated = new LinkedHashMap<>();
        JSONArray array = root.optJSONArray("rules");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String packageName = item.getString("package").trim();
                String mode = item.optString("mode", "FOLLOW_SYSTEM");
                boolean enabled = item.optBoolean("enabled", true);
                long rToM = item.optLong("rToMMs", 60_000L);
                long mToF = item.optLong("mToFMs", 60_000L);
                validate(packageName, rToM, mToF, 0, 0,
                        PolicyRule.PACKET_WAKE_ALLOW,
                        PolicyRule.DEFAULT_PACKET_WAKE_COOLDOWN_MS, 0L);

                PolicyRule previous = migrated.get(packageName);
                boolean fullExempt = "EXEMPT".equals(mode)
                        || previous != null && previous.fullExempt;
                boolean customTiming = "CUSTOM".equals(mode)
                        || previous != null && previous.customTiming;
                boolean mergedEnabled = enabled || previous != null && previous.enabled;
                long mergedRToM = "CUSTOM".equals(mode) ? rToM
                        : previous == null ? rToM : previous.rToMMs;
                long mergedMToF = "CUSTOM".equals(mode) ? mToF
                        : previous == null ? mToF : previous.mToFMs;
                migrated.put(packageName, new PolicyRule(packageName, mergedEnabled,
                        fullExempt, customTiming, mergedRToM, mergedMToF, 0, 0, false,
                        PolicyRule.PACKET_WAKE_ALLOW,
                        PolicyRule.DEFAULT_PACKET_WAKE_COOLDOWN_MS, 0L));
            }
        }
        return new PolicySnapshot(root.optBoolean("enabled", false),
                Math.max(0L, root.optLong("revision", 0L)),
                new ArrayList<>(migrated.values()));
    }

    public static void validate(String packageName, long rToM, long mToF,
                                int blockedSources, int bypassFlags,
                                int packetWakeMode, long packetWakeCooldownMs,
                                long packetRefreezeMs) {
        if (packageName == null || !PACKAGE_PATTERN.matcher(packageName).matches()) {
            throw new IllegalArgumentException("无效包名");
        }
        if (!validDelay(rToM) || !validDelay(mToF)) {
            throw new IllegalArgumentException("冻结时长须在 1 秒到 24 小时之间");
        }
        if ((blockedSources & ~PolicyRule.ALL_FREEZE_SOURCES) != 0) {
            throw new IllegalArgumentException("冻结来源掩码无效");
        }
        if ((bypassFlags & ~PolicyRule.ALL_PROXY_FLAGS) != 0) {
            throw new IllegalArgumentException("资源策略掩码无效");
        }
        if (packetWakeMode < PolicyRule.PACKET_WAKE_ALLOW
                || packetWakeMode > PolicyRule.PACKET_WAKE_BLOCK) {
            throw new IllegalArgumentException("网络包唤醒模式无效");
        }
        if (!validDelay(packetWakeCooldownMs)) {
            throw new IllegalArgumentException("网络唤醒间隔须在 1 秒到 24 小时之间");
        }
        if (packetRefreezeMs != 0L && !validDelay(packetRefreezeMs)) {
            throw new IllegalArgumentException("网络唤醒保持时长须在 1 秒到 24 小时之间");
        }
    }

    private static boolean validDelay(long value) {
        return value >= Constants.MIN_DELAY_MS && value <= Constants.MAX_DELAY_MS;
    }
}
