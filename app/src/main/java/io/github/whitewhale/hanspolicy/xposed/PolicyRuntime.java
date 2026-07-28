package io.github.whitewhale.hanspolicy.xposed;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import io.github.whitewhale.hanspolicy.Constants;
import io.github.whitewhale.hanspolicy.model.PolicyCodec;
import io.github.whitewhale.hanspolicy.model.PolicyRule;
import io.github.whitewhale.hanspolicy.model.PolicySnapshot;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class PolicyRuntime {
    private static final long INIT_RETRY_DELAY_MS = 1_000L;
    private static final long RETRY_DELAY_MS = 15_000L;
    private static final long PACKET_BLOCK_LOG_INTERVAL_MS = 60_000L;
    private static final String FAST_FREEZER = "enterFF";
    private static final String SUPER_FREEZE = "Super_F";
    private static final String PRELOAD_FREEZE = "Preload_F";
    private static final AtomicBoolean RUNTIME_THREAD_STARTED = new AtomicBoolean();
    private static final ConcurrentMap<String, Set<Integer>> OBSERVED_UIDS =
            new ConcurrentHashMap<>();
    private static final ConcurrentMap<Integer, Long> LAST_PACKET_WAKE_MS =
            new ConcurrentHashMap<>();
    private static final ConcurrentMap<Integer, Long> LAST_PACKET_BLOCK_LOG_MS =
            new ConcurrentHashMap<>();
    private static volatile PolicySnapshot snapshot = PolicySnapshot.disabled();
    private static volatile String lastError = "";
    private static volatile String runtimeSource = "";
    private static volatile boolean receiverRegistered;

    @SuppressLint("StaticFieldLeak")
    private static Context context;
    private static Object hansManager;
    private static Handler handler;
    private static HookSummary hookSummary;

    private PolicyRuntime() {
    }

    static void initializeEarly(ClassLoader loader, Class<?> managerClass,
                                HookSummary summary) {
        try {
            Class<?> activityThreadClass = XposedHelpers.findClass(
                    "android.app.ActivityThread", loader);
            Object activityThread = XposedHelpers.callStaticMethod(
                    activityThreadClass, "currentActivityThread");
            Object systemContext = XposedHelpers.callMethod(activityThread, "getSystemContext");
            Object manager = XposedHelpers.callStaticMethod(managerClass, "getInstance");
            if (systemContext instanceof Context) {
                initialize((Context) systemContext, manager, summary, "system context");
            }
        } catch (Throwable throwable) {
            XposedBridge.log("HansPolicy: early runtime init unavailable: " + throwable);
        }
    }

    static void initializeFromManager(Object manager, HookSummary summary, String source) {
        try {
            Object managerContext = XposedHelpers.getObjectField(manager, "mAmsContext");
            if (managerContext instanceof Context) {
                initialize((Context) managerContext, manager, summary, source);
            }
        } catch (Throwable throwable) {
            XposedBridge.log("HansPolicy: runtime init from " + source + " failed: " + throwable);
        }
    }

    private static synchronized void initialize(Context systemContext, Object manager,
                                                HookSummary summary, String source) {
        context = systemContext;
        hansManager = manager;
        hookSummary = summary;
        runtimeSource = source;
        if (!RUNTIME_THREAD_STARTED.compareAndSet(false, true)) {
            scheduleRuntimeInit(0L);
            return;
        }
        HandlerThread thread = new HandlerThread("HansPolicy");
        thread.start();
        handler = new Handler(thread.getLooper());
        scheduleRuntimeInit(0L);
    }

    private static final BroadcastReceiver REFRESH_RECEIVER = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ignored, Intent intent) {
            scheduleReload(0L);
        }
    };

    private static final Runnable INITIALIZE_RUNTIME = new Runnable() {
        @Override
        public void run() {
            if (receiverRegistered || context == null) {
                return;
            }
            try {
                registerRefreshReceiver();
                receiverRegistered = true;
                XposedBridge.log("HansPolicy: runtime initialized from " + runtimeSource);
                scheduleReload(0L);
            } catch (Throwable throwable) {
                XposedBridge.log("HansPolicy: runtime services not ready, retrying: "
                        + throwable);
                scheduleRuntimeInit(INIT_RETRY_DELAY_MS);
            }
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private static void registerRefreshReceiver() {
        IntentFilter filter = new IntentFilter(Constants.ACTION_REFRESH);
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(REFRESH_RECEIVER, filter, null, handler,
                    Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(REFRESH_RECEIVER, filter, null, handler);
        }
    }

    private static void scheduleRuntimeInit(long delayMs) {
        if (handler == null || receiverRegistered) {
            return;
        }
        handler.removeCallbacks(INITIALIZE_RUNTIME);
        handler.postDelayed(INITIALIZE_RUNTIME, delayMs);
    }

    static boolean isExempt(int uid, String packageName) {
        PolicyRule rule = ruleFor(uid, packageName);
        return rule != null && rule.isExempt();
    }

    static boolean shouldBlockFreeze(Object hansPackage, String from) {
        try {
            int uid = (Integer) XposedHelpers.callMethod(hansPackage, "getUid");
            String packageName = (String) XposedHelpers.callMethod(
                    hansPackage, "getPkgName");
            PolicyRule rule = ruleFor(uid, packageName);
            if (rule == null) {
                return false;
            }
            if (rule.isExempt()) {
                return true;
            }
            int source;
            if (FAST_FREEZER.equals(from)) {
                source = PolicyRule.FREEZE_FAST;
            } else if (PRELOAD_FREEZE.equals(from)) {
                source = PolicyRule.FREEZE_PRELOAD;
            } else if (SUPER_FREEZE.equals(from)
                    || Boolean.TRUE.equals(XposedHelpers.callMethod(
                    hansPackage, "getSuperFRZMode"))) {
                source = PolicyRule.FREEZE_SUPER;
            } else {
                source = PolicyRule.FREEZE_NORMAL;
            }
            return rule.blocksFreeze(source);
        } catch (Throwable throwable) {
            recordError("freeze source", throwable);
            return false;
        }
    }

    static boolean shouldBypassProxy(Object action, int proxyFlag) {
        try {
            Object hansPackage = XposedHelpers.getObjectField(action, "mHansPackage");
            int uid = (Integer) XposedHelpers.callMethod(hansPackage, "getUid");
            String packageName = (String) XposedHelpers.callMethod(
                    hansPackage, "getPkgName");
            PolicyRule rule = ruleFor(uid, packageName);
            return rule != null && rule.bypassesProxy(proxyFlag);
        } catch (Throwable throwable) {
            recordError("proxy policy", throwable);
            return false;
        }
    }

    static boolean shouldBypassWakeLock(Object hansPackage) {
        PolicyRule rule = ruleForHansPackage(hansPackage);
        return rule != null && rule.bypassesProxy(PolicyRule.PROXY_WAKELOCK);
    }

    static boolean shouldKeepNetwork(Object hansPackage) {
        PolicyRule rule = ruleForHansPackage(hansPackage);
        return rule != null && rule.keepsNetwork();
    }

    static boolean shouldKeepNetwork(int uid, String packageName) {
        PolicyRule rule = ruleFor(uid, packageName);
        return rule != null && rule.keepsNetwork();
    }

    static void overrideDelay(XC_MethodHook.MethodHookParam param, boolean mToF) {
        try {
            int uid = (Integer) param.args[0];
            String packageName = (String) param.args[1];
            PolicyRule rule = ruleFor(uid, packageName);
            if (rule == null) {
                return;
            }
            if (mToF && param.args.length > 4 && "Packet".equals(param.args[4])
                    && rule.hasCustomPacketRefreeze()) {
                param.setResult(rule.packetRefreezeMs);
            } else if (rule.hasCustomTiming()) {
                param.setResult(mToF ? rule.mToFMs : rule.rToMMs);
            }
        } catch (Throwable throwable) {
            recordError("delay override", throwable);
        }
    }

    static boolean shouldBlockPacketWake(int uid) {
        PolicyRule rule = ruleForUid(uid);
        if (rule == null || rule.packetWakeMode == PolicyRule.PACKET_WAKE_ALLOW) {
            return false;
        }
        long now = SystemClock.elapsedRealtime();
        if (rule.blocksPacketWake()) {
            logBlockedPacketWake(uid, rule.packageName, "blocked", now);
            return true;
        }
        if (!rule.throttlesPacketWake()) {
            return false;
        }
        synchronized (LAST_PACKET_WAKE_MS) {
            Long lastWake = LAST_PACKET_WAKE_MS.get(uid);
            if (lastWake == null || now - lastWake >= rule.packetWakeCooldownMs) {
                LAST_PACKET_WAKE_MS.put(uid, now);
                return false;
            }
        }
        logBlockedPacketWake(uid, rule.packageName, "throttled", now);
        return true;
    }

    private static PolicyRule ruleForHansPackage(Object hansPackage) {
        try {
            int uid = (Integer) XposedHelpers.callMethod(hansPackage, "getUid");
            String packageName = (String) XposedHelpers.callMethod(
                    hansPackage, "getPkgName");
            return ruleFor(uid, packageName);
        } catch (Throwable throwable) {
            recordError("package policy", throwable);
            return null;
        }
    }

    private static PolicyRule ruleFor(int uid, String packageName) {
        if (packageName == null) {
            return null;
        }
        if (uid >= 0) {
            OBSERVED_UIDS.computeIfAbsent(packageName,
                    ignored -> ConcurrentHashMap.newKeySet()).add(uid);
        }
        return snapshot.getRule(packageName);
    }

    private static PolicyRule ruleForUid(int uid) {
        String packageName = packageNameFromHans(uid);
        if (packageName != null) {
            PolicyRule rule = ruleFor(uid, packageName);
            if (rule != null) {
                return rule;
            }
        }
        if (context == null) {
            return null;
        }
        try {
            String[] packages = context.getPackageManager().getPackagesForUid(uid);
            if (packages != null) {
                for (String candidate : packages) {
                    PolicyRule rule = ruleFor(uid, candidate);
                    if (rule != null) {
                        return rule;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Packet handling must fail open if PackageManager is unavailable.
        }
        return null;
    }

    private static String packageNameFromHans(int uid) {
        if (hansManager == null) {
            return null;
        }
        try {
            Object config = XposedHelpers.getObjectField(hansManager, "mHansDBConfig");
            Object hansPackage = XposedHelpers.callMethod(config, "getHansPackage", uid);
            if (hansPackage != null) {
                return (String) XposedHelpers.callMethod(hansPackage, "getPkgName");
            }
        } catch (Throwable ignored) {
            // Fall back to PackageManager below.
        }
        return null;
    }

    private static void logBlockedPacketWake(int uid, String packageName,
                                             String action, long now) {
        Long lastLog = LAST_PACKET_BLOCK_LOG_MS.get(uid);
        if (lastLog != null && now - lastLog < PACKET_BLOCK_LOG_INTERVAL_MS) {
            return;
        }
        LAST_PACKET_BLOCK_LOG_MS.put(uid, now);
        XposedBridge.log("HansPolicy: Packet wake " + action + " uid=" + uid
                + " pkg=" + packageName);
    }

    private static void scheduleReload(long delayMs) {
        if (handler == null) {
            return;
        }
        handler.removeCallbacks(RELOAD);
        handler.postDelayed(RELOAD, delayMs);
    }

    private static final Runnable RELOAD = new Runnable() {
        @Override
        public void run() {
            try {
                Bundle result = context.getContentResolver().call(
                        Constants.POLICY_URI, Constants.METHOD_GET_POLICY, null, null);
                String json = result == null ? null : result.getString(Constants.KEY_POLICY_JSON);
                PolicySnapshot next = PolicyCodec.decode(json);
                PolicySnapshot previous = snapshot;
                snapshot = next;
                LAST_PACKET_WAKE_MS.clear();
                LAST_PACKET_BLOCK_LOG_MS.clear();
                lastError = hookSummary == null ? "" : hookSummary.errorsText();
                cleanupNewInterventions(previous, next);
                reportStatus();
            } catch (Throwable throwable) {
                recordError("policy reload", throwable);
                reportStatus();
                scheduleReload(RETRY_DELAY_MS);
            }
        }
    };

    private static void cleanupNewInterventions(PolicySnapshot oldSnapshot,
                                                PolicySnapshot newSnapshot) {
        if (!newSnapshot.enabled || hansManager == null) {
            return;
        }
        for (PolicyRule rule : newSnapshot.getRules()) {
            PolicyRule previous = oldSnapshot.getRule(rule.packageName);
            if (rule.needsCleanupComparedTo(previous)) {
                unfreezePackage(rule.packageName);
            }
        }
    }

    private static void unfreezePackage(String packageName) {
        for (int uid : resolveUids(packageName)) {
            try {
                XposedHelpers.callMethod(hansManager, "hansUnFreeze",
                        uid, "force", "HansPolicy");
            } catch (Throwable throwable) {
                recordError("unfreeze " + packageName + "/" + uid, throwable);
            }
        }
    }

    private static Set<Integer> resolveUids(String packageName) {
        Set<Integer> result = new HashSet<>();
        Set<Integer> observed = OBSERVED_UIDS.get(packageName);
        if (observed != null) {
            result.addAll(observed);
        }
        if (context == null) {
            return result;
        }
        Set<Integer> userIds = new HashSet<>();
        userIds.add(0);
        try {
            Object userManager = context.getSystemService(Context.USER_SERVICE);
            Object value = XposedHelpers.callMethod(userManager, "getUserHandles", true);
            if (value instanceof List<?>) {
                for (Object item : (List<?>) value) {
                    if (item != null) {
                        userIds.add((Integer) XposedHelpers.callMethod(
                                item, "getIdentifier"));
                    }
                }
            }
        } catch (Throwable throwable) {
            XposedBridge.log("HansPolicy: user enumeration unavailable: " + throwable);
        }
        PackageManager packageManager = context.getPackageManager();
        for (int userId : userIds) {
            try {
                int uid = (Integer) XposedHelpers.callMethod(packageManager,
                        "getPackageUidAsUser", packageName, userId);
                result.add(uid);
            } catch (Throwable ignored) {
                // The package may not be installed for every Android user.
            }
        }
        return result;
    }

    private static void reportStatus() {
        if (context == null) {
            return;
        }
        try {
            Bundle status = new Bundle();
            status.putBoolean("active", hookSummary != null && hookSummary.isOperational());
            status.putString("stage", "runtime_initialized");
            status.putString("runtime_source", runtimeSource);
            status.putInt("hook_count", hookSummary == null ? 0 : hookSummary.count());
            status.putString("hook_targets",
                    hookSummary == null ? "" : hookSummary.targetsText());
            status.putString("last_error", lastError);
            status.putString("fingerprint", Build.FINGERPRINT);
            status.putString("boot_id", readBootId());
            status.putLong("policy_revision", snapshot.revision);
            context.getContentResolver().call(Constants.POLICY_URI,
                    Constants.METHOD_REPORT_STATUS, null, status);
        } catch (Throwable throwable) {
            XposedBridge.log("HansPolicy: status report failed: " + throwable);
        }
    }

    private static String readBootId() {
        try (BufferedReader reader = new BufferedReader(new FileReader(
                "/proc/sys/kernel/random/boot_id"))) {
            String value = reader.readLine();
            return value == null ? "" : value.trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void recordError(String operation, Throwable throwable) {
        String message = throwable.getMessage();
        lastError = operation + ": " + throwable.getClass().getSimpleName()
                + (message == null ? "" : " (" + message + ")");
        XposedBridge.log("HansPolicy: " + lastError);
    }
}
