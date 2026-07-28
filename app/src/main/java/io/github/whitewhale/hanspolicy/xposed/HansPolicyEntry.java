package io.github.whitewhale.hanspolicy.xposed;

import android.content.pm.ApplicationInfo;
import android.os.Process;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import io.github.whitewhale.hanspolicy.Constants;
import io.github.whitewhale.hanspolicy.model.PolicyRule;

import java.util.Set;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class HansPolicyEntry implements IXposedHookLoadPackage {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if ("android".equals(loadPackageParam.packageName)) {
            XposedBridge.log("HansPolicy: entry package=" + loadPackageParam.packageName
                    + " process=" + loadPackageParam.processName);
        }
        if (!"android".equals(loadPackageParam.packageName)
                || !"android".equals(loadPackageParam.processName)
                || !INSTALLED.compareAndSet(false, true)) {
            return;
        }
        HookSummary summary = new HookSummary();
        installProviderBootstrapHook(loadPackageParam.classLoader, summary);
        Class<?> managerClass = installRuntimeInit(loadPackageParam.classLoader, summary);
        installTimingHooks(loadPackageParam.classLoader, summary);
        installExemptionHooks(loadPackageParam.classLoader, summary);
        installFreezeGate(loadPackageParam.classLoader, summary);
        installUnfreezeGate(loadPackageParam.classLoader, summary);
        installPacketWakeHook(loadPackageParam.classLoader, summary);
        installAlarmWakeHook(loadPackageParam.classLoader, summary);
        installResourceHooks(loadPackageParam.classLoader, summary);
        XposedBridge.log("HansPolicy: installed " + summary.count() + " hooks; "
                + summary.targetsText());
        for (String error : summary.errors()) {
            XposedBridge.log("HansPolicy: " + error);
        }
        if (managerClass != null) {
            PolicyRuntime.initializeEarly(loadPackageParam.classLoader, managerClass, summary);
        }
    }

    private static void installProviderBootstrapHook(ClassLoader loader,
                                                     HookSummary summary) {
        try {
            Class<?> processRecordClass = XposedHelpers.findClass(
                    "com.android.server.am.ProcessRecord", loader);
            Class<?> providerRecordClass = XposedHelpers.findClass(
                    "com.android.server.am.ContentProviderRecord", loader);
            XposedHelpers.findAndHookMethod(
                    "com.android.server.am.OplusAppStartupManager", loader,
                    "shouldPreventStartProvider", processRecordClass,
                    providerRecordClass, ApplicationInfo.class, String.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            ApplicationInfo appInfo = (ApplicationInfo) param.args[2];
                            int callingUid = (Integer) param.args[4];
                            if (callingUid == Process.SYSTEM_UID && appInfo != null
                                    && Constants.MODULE_PACKAGE.equals(appInfo.packageName)) {
                                param.setResult(false);
                            }
                        }
                    });
            summary.addTarget("OplusAppStartupManager.shouldPreventStartProvider(module)");
        } catch (Throwable throwable) {
            summary.addError("provider bootstrap hook: " + brief(throwable));
        }
    }

    private static Class<?> installRuntimeInit(ClassLoader loader, HookSummary summary) {
        try {
            Class<?> managerClass = XposedHelpers.findClass(
                    "com.android.server.am.OplusHansManager", loader);
            hookRuntimeMethod(managerClass, "init", summary);
            hookRuntimeMethod(managerClass, "bootCompleted", summary);
            return managerClass;
        } catch (Throwable throwable) {
            summary.addError("runtime hooks: " + brief(throwable));
            return null;
        }
    }

    private static void hookRuntimeMethod(Class<?> managerClass, String method,
                                          HookSummary summary) {
        Set<XC_MethodHook.Unhook> hooks = XposedBridge.hookAllMethods(
                managerClass, method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        PolicyRuntime.initializeFromManager(param.thisObject, summary,
                                "OplusHansManager." + method);
                    }
                });
        if (hooks.isEmpty()) {
            summary.addError("OplusHansManager." + method + " not found");
        } else {
            summary.addTarget("OplusHansManager." + method);
        }
    }

    private static void installTimingHooks(ClassLoader loader, HookSummary summary) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.server.hans.OplusHansDBConfig", loader,
                    "getRtoMCheckTime", int.class, String.class, int.class, long.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            PolicyRuntime.overrideDelay(param, false);
                        }
                    });
            summary.addTarget("OplusHansDBConfig.getRtoMCheckTime");
        } catch (Throwable throwable) {
            summary.addError("R-to-M hook: " + brief(throwable));
        }
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.server.hans.OplusHansDBConfig", loader,
                    "getMtoFCheckTime", int.class, String.class, int.class, long.class,
                    String.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            PolicyRuntime.overrideDelay(param, true);
                        }
                    });
            summary.addTarget("OplusHansDBConfig.getMtoFCheckTime");
        } catch (Throwable throwable) {
            summary.addError("M-to-F hook: " + brief(throwable));
        }
    }

    private static void installExemptionHooks(ClassLoader loader, HookSummary summary) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.server.am.OplusHansManager", loader,
                    "isHansCoreApp", int.class, String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (PolicyRuntime.isExempt((Integer) param.args[0],
                                    (String) param.args[1])) {
                                param.setResult(true);
                            }
                        }
                    });
            summary.addTarget("OplusHansManager.isHansCoreApp");
        } catch (Throwable throwable) {
            summary.addError("core exemption hook: " + brief(throwable));
        }
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.server.am.OplusHansManager", loader,
                    "isLcdOnNonRestrictPkg", int.class, String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (PolicyRuntime.isExempt((Integer) param.args[0],
                                    (String) param.args[1])) {
                                param.setResult(true);
                            }
                        }
                    });
            summary.addTarget("OplusHansManager.isLcdOnNonRestrictPkg");
        } catch (Throwable throwable) {
            summary.addError("non-restrict hook: " + brief(throwable));
        }
    }

    private static void installFreezeGate(ClassLoader loader, HookSummary summary) {
        try {
            Class<?> packageClass = XposedHelpers.findClass(
                    "com.android.server.hans.OplusHansPackage", loader);
            XposedHelpers.findAndHookMethod(
                    "com.android.server.hans.freeze.HansCGroup", loader,
                    "hansFreezeLocked", packageClass, String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (PolicyRuntime.shouldBlockFreeze(
                                    param.args[0], (String) param.args[1])) {
                                param.setResult(false);
                            }
                        }
                    });
            summary.addTarget("HansCGroup.hansFreezeLocked");
        } catch (Throwable throwable) {
            summary.addError("freeze gate hook: " + brief(throwable));
        }
    }

    private static void installUnfreezeGate(ClassLoader loader, HookSummary summary) {
        try {
            Class<?> packageClass = XposedHelpers.findClass(
                    "com.android.server.hans.OplusHansPackage", loader);
            XposedHelpers.findAndHookMethod(
                    "com.android.server.hans.freeze.HansCGroup", loader,
                    "hansUnfreezeLocked", packageClass, String.class, String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (PolicyRuntime.shouldBlockHansUnfreeze(param.args[0],
                                    (String) param.args[1], (String) param.args[2])) {
                                param.setResult(false);
                            }
                        }
                    });
            summary.addTarget("HansCGroup.hansUnfreezeLocked(source gate)");
        } catch (Throwable throwable) {
            summary.addError("unfreeze gate hook: " + brief(throwable));
        }
    }

    private static void installPacketWakeHook(ClassLoader loader, HookSummary summary) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.server.am.OplusHansManager", loader,
                    "unfreezeForKernel", int.class, int.class, int.class, int.class,
                    int.class, String.class, int.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            int type = (Integer) param.args[0];
                            int callerPid = (Integer) param.args[1];
                            int callerUid = (Integer) param.args[2];
                            int targetUid = (Integer) param.args[4];
                            String rpcName = (String) param.args[5];
                            int code = (Integer) param.args[6];
                            if (PolicyRuntime.shouldBlockKernelWake(type, callerPid,
                                    callerUid, targetUid, rpcName, code)) {
                                param.setResult(null);
                            }
                        }
                    });
            summary.addTarget("OplusHansManager.unfreezeForKernel(source gate)");
        } catch (Throwable throwable) {
            summary.addError("kernel wake hook: " + brief(throwable));
        }
    }

    private static void installAlarmWakeHook(ClassLoader loader, HookSummary summary) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.server.am.OplusHansManager", loader,
                    "checkAlarmIfRestricted", int.class, String.class, String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            int uid = (Integer) param.args[0];
                            String packageName = (String) param.args[1];
                            String action = (String) param.args[2];
                            if (PolicyRuntime.shouldBlockAlarmWake(
                                    uid, packageName, action)) {
                                param.setResult(true);
                            }
                        }
                    });
            summary.addTarget("OplusHansManager.checkAlarmIfRestricted");
        } catch (Throwable throwable) {
            summary.addError("Alarm wake hook: " + brief(throwable));
        }
        try {
            Class<?> broadcastRecordClass = XposedHelpers.findClass(
                    "com.android.server.am.BroadcastRecord", loader);
            Class<?> proxyBroadcastClass = XposedHelpers.findClass(
                    "com.android.server.am.OplusProxyBroadcast", loader);
            XposedHelpers.findAndHookMethod(
                    "com.android.server.am.OplusHansManager", loader,
                    "enqueueProxyBroadcastLocked", boolean.class,
                    broadcastRecordClass, Object.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object target = param.args[2];
                            int uid = (Integer) XposedHelpers.callStaticMethod(
                                    proxyBroadcastClass, "getTargetUid", target);
                            String packageName = (String) XposedHelpers.callStaticMethod(
                                    proxyBroadcastClass, "getTargetPkg", target);
                            if (PolicyRuntime.shouldSuppressAlarmBatchBroadcast(
                                    uid, packageName)) {
                                param.setResult(true);
                            }
                        }
                    });
            summary.addTarget("OplusHansManager.enqueueProxyBroadcastLocked(alarm batch)");
        } catch (Throwable throwable) {
            summary.addError("Alarm batch broadcast hook: " + brief(throwable));
        }
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.server.am.OplusHansManager", loader,
                    "unFreezeForwl", List.class, String.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            @SuppressWarnings("unchecked")
                            List<Integer> uids = (List<Integer>) param.args[0];
                            String reason = (String) param.args[1];
                            if (uids != null) {
                                uids.removeIf(uid -> uid != null
                                        && PolicyRuntime.shouldSuppressAlarmBatchWakeLock(
                                        uid, reason));
                            }
                        }
                    });
            summary.addTarget("OplusHansManager.unFreezeForwl(alarm batch WakeLock)");
        } catch (Throwable throwable) {
            summary.addError("Alarm batch WakeLock hook: " + brief(throwable));
        }
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.server.am.OplusHansManager", loader,
                    "unFreezeForwl", int.class, String.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            int uid = (Integer) param.args[0];
                            String reason = (String) param.args[1];
                            if (PolicyRuntime.shouldSuppressAlarmBatchWakeLock(uid, reason)) {
                                param.setResult(null);
                            }
                        }
                    });
            summary.addTarget("OplusHansManager.unFreezeForwl(alarm WakeLock)");
        } catch (Throwable throwable) {
            summary.addError("Alarm WakeLock hook: " + brief(throwable));
        }
    }

    private static void installResourceHooks(ClassLoader loader, HookSummary summary) {
        try {
            Class<?> actionClass = XposedHelpers.findClass(
                    "com.android.server.hans.states.action.Action", loader);
            installActionHook(actionClass, "proxyService", PolicyRule.PROXY_SERVICE, summary);
            installActionHook(actionClass, "proxyBroadcast", PolicyRule.PROXY_BROADCAST, summary);
            installActionHook(actionClass, "proxyJob", PolicyRule.PROXY_JOB, summary);
            installActionHook(actionClass, "proxySensor", PolicyRule.PROXY_SENSOR, summary);
            installActionHook(actionClass, "proxyBinder", PolicyRule.PROXY_BINDER, summary);
            installActionHook(actionClass, "manageAlarm", PolicyRule.PROXY_ALARM, summary);
            installActionHook(actionClass, "proxyWakeLock", PolicyRule.PROXY_WAKELOCK, summary);
            installActionHook(actionClass, "proxyGPS", PolicyRule.PROXY_GPS, summary);
            installActionHook(actionClass, "proxyAudio", PolicyRule.PROXY_AUDIO, summary);
            installActionHook(actionClass, "proxyBtScan", PolicyRule.PROXY_BT_SCAN, summary);
        } catch (Throwable throwable) {
            summary.addError("Action resource hooks: " + brief(throwable));
        }
        installWakeLockHook(loader, summary);
        installNetworkHooks(loader, summary);
    }

    private static void installActionHook(Class<?> actionClass, String method,
                                          int proxyFlag, HookSummary summary) {
        try {
            XposedHelpers.findAndHookMethod(actionClass, method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (PolicyRuntime.shouldBypassProxy(param.thisObject, proxyFlag)) {
                        param.setResult(null);
                    }
                }
            });
            summary.addTarget("Action." + method);
        } catch (Throwable throwable) {
            summary.addError("Action." + method + ": " + brief(throwable));
        }
    }

    private static void installWakeLockHook(ClassLoader loader, HookSummary summary) {
        try {
            Class<?> packageClass = XposedHelpers.findClass(
                    "com.android.server.hans.OplusHansPackage", loader);
            XposedHelpers.findAndHookMethod(
                    "com.android.server.am.OplusHansManager", loader,
                    "tryProxyWakeLock", packageClass, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (PolicyRuntime.shouldBypassWakeLock(param.args[0])) {
                                param.setResult(true);
                            }
                        }
                    });
            summary.addTarget("OplusHansManager.tryProxyWakeLock");
        } catch (Throwable throwable) {
            summary.addError("wakelock hook: " + brief(throwable));
        }
    }

    private static void installNetworkHooks(ClassLoader loader, HookSummary summary) {
        try {
            Class<?> packageClass = XposedHelpers.findClass(
                    "com.android.server.hans.OplusHansPackage", loader);
            XposedHelpers.findAndHookMethod(
                    "com.android.server.am.OplusHansManager", loader,
                    "nwPowerSetFirewall", packageClass, boolean.class, int.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            boolean allow = (Boolean) param.args[1];
                            if (!allow && PolicyRuntime.shouldKeepNetwork(param.args[0])) {
                                param.setResult(null);
                            }
                        }
                    });
            summary.addTarget("OplusHansManager.nwPowerSetFirewall");
        } catch (Throwable throwable) {
            summary.addError("firewall hook: " + brief(throwable));
        }
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.server.am.OplusHansManager", loader,
                    "restrictRStateNonKeyProcsNet", String.class, int.class, String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (PolicyRuntime.shouldKeepNetwork((Integer) param.args[1],
                                    (String) param.args[2])) {
                                param.setResult(false);
                            }
                        }
                    });
            summary.addTarget("OplusHansManager.restrictRStateNonKeyProcsNet");
        } catch (Throwable throwable) {
            summary.addError("R-state network hook: " + brief(throwable));
        }
    }

    private static String brief(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName()
                + (message == null ? "" : ": " + message);
    }
}
