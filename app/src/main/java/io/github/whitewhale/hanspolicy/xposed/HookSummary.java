package io.github.whitewhale.hanspolicy.xposed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class HookSummary {
    private final List<String> targets = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    void addTarget(String target) {
        targets.add(target);
    }

    void addError(String error) {
        errors.add(error);
    }

    int count() {
        return targets.size();
    }

    boolean isOperational() {
        return targets.contains("OplusAppStartupManager.shouldPreventStartProvider(module)")
                && targets.contains("OplusHansManager.isHansCoreApp")
                && targets.contains("OplusHansDBConfig.getRtoMCheckTime")
                && targets.contains("OplusHansDBConfig.getMtoFCheckTime")
                && targets.contains("HansCGroup.hansFreezeLocked")
                && targets.contains("OplusHansManager.unfreezeForKernel(packet)");
    }

    String targetsText() {
        return String.join(", ", targets);
    }

    String errorsText() {
        return String.join(" | ", errors);
    }

    List<String> errors() {
        return Collections.unmodifiableList(errors);
    }
}
