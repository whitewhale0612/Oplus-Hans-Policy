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

    public PolicySnapshot(boolean enabled, long revision, List<PolicyRule> rules) {
        this.enabled = enabled;
        this.revision = revision;
        LinkedHashMap<String, PolicyRule> copy = new LinkedHashMap<>();
        for (PolicyRule rule : rules) {
            copy.put(rule.key(), rule);
        }
        this.rules = Collections.unmodifiableMap(copy);
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

    public List<PolicyRule> getRules() {
        return new ArrayList<>(rules.values());
    }
}
