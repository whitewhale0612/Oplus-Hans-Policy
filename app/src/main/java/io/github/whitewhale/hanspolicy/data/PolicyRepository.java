package io.github.whitewhale.hanspolicy.data;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import io.github.whitewhale.hanspolicy.Constants;
import io.github.whitewhale.hanspolicy.model.PolicyCodec;
import io.github.whitewhale.hanspolicy.model.PolicyRule;
import io.github.whitewhale.hanspolicy.model.PolicySnapshot;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PolicyRepository {
    static final String POLICY_PREFS = "policy";
    static final String STATUS_PREFS = "runtime_status";
    static final String KEY_JSON = "json";

    private final Context context;
    private final SharedPreferences preferences;

    public PolicyRepository(Context context) {
        this.context = context.createDeviceProtectedStorageContext();
        this.preferences = this.context.getSharedPreferences(POLICY_PREFS, Context.MODE_PRIVATE);
    }

    public synchronized PolicySnapshot load() {
        String json = preferences.getString(KEY_JSON, null);
        if (json == null) {
            return PolicySnapshot.disabled();
        }
        try {
            return PolicyCodec.decode(json);
        } catch (JSONException | IllegalArgumentException e) {
            return PolicySnapshot.disabled();
        }
    }

    public synchronized PolicySnapshot setMasterEnabled(boolean enabled) {
        PolicySnapshot old = load();
        return persist(new PolicySnapshot(enabled, old.revision + 1L, old.getRules()));
    }

    public synchronized PolicySnapshot upsert(PolicyRule rule, String oldKey) {
        PolicySnapshot old = load();
        List<PolicyRule> rules = old.getRules();
        if (oldKey != null) {
            rules.removeIf(item -> item.key().equals(oldKey));
        }
        rules.removeIf(item -> item.key().equals(rule.key()));
        rules.add(rule);
        rules.sort(Comparator.comparing(item -> item.packageName));
        return persist(new PolicySnapshot(old.enabled, old.revision + 1L, rules));
    }

    public synchronized PolicySnapshot delete(String key) {
        PolicySnapshot old = load();
        List<PolicyRule> rules = new ArrayList<>(old.getRules());
        rules.removeIf(item -> item.key().equals(key));
        return persist(new PolicySnapshot(old.enabled, old.revision + 1L, rules));
    }

    public RuntimeStatus loadStatus() {
        SharedPreferences prefs = context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE);
        return new RuntimeStatus(
                prefs.getBoolean("active", false),
                prefs.getString("stage", ""),
                prefs.getString("runtime_source", ""),
                prefs.getInt("hook_count", 0),
                prefs.getString("hook_targets", ""),
                prefs.getString("last_error", ""),
                prefs.getString("fingerprint", ""),
                prefs.getString("boot_id", ""),
                prefs.getLong("policy_revision", -1L),
                prefs.getLong("last_report_ms", 0L));
    }

    public static String currentBootId() {
        try (BufferedReader reader = new BufferedReader(new FileReader(
                "/proc/sys/kernel/random/boot_id"))) {
            String value = reader.readLine();
            return value == null ? "" : value.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private PolicySnapshot persist(PolicySnapshot snapshot) {
        try {
            String json = PolicyCodec.encode(snapshot);
            if (!preferences.edit().putString(KEY_JSON, json).commit()) {
                throw new IllegalStateException("策略写入失败");
            }
        } catch (JSONException e) {
            throw new IllegalStateException("策略序列化失败", e);
        }
        Intent refresh = new Intent(Constants.ACTION_REFRESH);
        refresh.setPackage("android");
        context.sendBroadcast(refresh);
        return snapshot;
    }

    public static final class RuntimeStatus {
        public final boolean active;
        public final String stage;
        public final String runtimeSource;
        public final int hookCount;
        public final String hookTargets;
        public final String lastError;
        public final String fingerprint;
        public final String bootId;
        public final long policyRevision;
        public final long lastReportMs;

        RuntimeStatus(boolean active, String stage, String runtimeSource,
                      int hookCount, String hookTargets, String lastError,
                      String fingerprint, String bootId, long policyRevision, long lastReportMs) {
            this.active = active;
            this.stage = stage;
            this.runtimeSource = runtimeSource;
            this.hookCount = hookCount;
            this.hookTargets = hookTargets;
            this.lastError = lastError;
            this.fingerprint = fingerprint;
            this.bootId = bootId;
            this.policyRevision = policyRevision;
            this.lastReportMs = lastReportMs;
        }
    }
}
