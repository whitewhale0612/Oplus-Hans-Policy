# Architecture

## Boundaries

Hans Policy changes decisions inside Oplus Hans. It does not:

- write `cgroup.freeze`;
- call kernel freezer interfaces directly;
- modify Athena/RUS configuration files;
- claim that a blocked freeze succeeded;
- inject policy data from arbitrary broadcasts.

The OEM implementation still performs every accepted state transition, proxy action,
freeze, and unfreeze.

## Runtime flow

```text
Manager UI
  -> device-protected SharedPreferences (versioned JSON)
  -> exported PolicyProvider (module UID and system UID only)
  -> dedicated HansPolicy HandlerThread in system_server
  -> immutable PolicySnapshot
  -> hook decisions on the original Hans call path
```

The exported provider is necessary because Android loads the module UI and
`system_server` under different UIDs. Reads are restricted to the module UID and system
UID. Runtime status writes are restricted further to system UID.

A package-scoped refresh broadcast carries no rule data. It only asks the runtime thread
to read and validate the provider again. Disk and Binder work never runs on a Hans state
machine thread.

During cold boot, a narrow `OplusAppStartupManager.shouldPreventStartProvider` hook lets
system UID start only this module's provider. This fixes ROM startup policy preventing the
direct-boot provider from being created before the manager UI is opened.

## Package and UID resolution

Rules are keyed by package name. For a Packet callback that only carries a UID, runtime
lookup proceeds in this order:

1. Query the active `OplusHansPackage` from Hans and read its package name.
2. Fall back to `PackageManager.getPackagesForUid(uid)`.
3. Fail open if neither source is available.

Observed UIDs are cached only in memory. When a newly permissive policy needs immediate
cleanup, the module resolves the package for every Android user and calls the ROM's own
`hansUnFreeze(uid, "force", "HansPolicy")`.

## Hook map

The validated build installs 27 targets:

| Area | Target | Purpose |
| --- | --- | --- |
| Bootstrap | `OplusAppStartupManager.shouldPreventStartProvider` | Allow system UID to start the module provider. |
| Runtime | `OplusHansManager.init` | Capture context and manager instance. |
| Runtime | `OplusHansManager.bootCompleted` | Cold-boot initialization fallback. |
| Timing | `OplusHansDBConfig.getRtoMCheckTime` | Per-package R to M delay. |
| Timing | `OplusHansDBConfig.getMtoFCheckTime` | Per-package M to F, Packet, and Alarm-specific delay. |
| Exemption | `OplusHansManager.isHansCoreApp` | Component-policy exemption. |
| Exemption | `OplusHansManager.isLcdOnNonRestrictPkg` | Standard state-machine exemption. Falls back to `OplusHansDBConfig.isHansWhitelistApp(int)` on older firmware. |
| Freeze gate | `HansCGroup.hansFreezeLocked` | Block selected sources without faking success. |
| Unfreeze gate | `HansCGroup.hansUnfreezeLocked` | Final reason-based gate for framework and scene wakes. |
| Kernel wake | `OplusHansManager.unfreezeForKernel` | Gate Binder, signal, and Packet callbacks before native thaw. |
| Alarm | `OplusHansManager.checkAlarmIfRestricted` | Allow, throttle, or block Alarm wake delivery. |
| Alarm | `OplusHansManager.enqueueProxyBroadcastLocked` | Suppress sibling broadcasts from a blocked Alarm batch. |
| Alarm | `OplusHansManager.unFreezeForwl(List, String)` | Remove controlled UIDs from Alarm-correlated WakeLock unfreezes. |
| Alarm | `OplusHansManager.unFreezeForwl(int, String)` | Suppress direct Alarm-correlated WakeLock unfreezes. |
| Resources | `Action.proxyService` | Preserve Service dispatch. |
| Resources | `Action.proxyBroadcast` | Preserve broadcast delivery. |
| Resources | `Action.proxyJob` | Preserve Job dispatch. |
| Resources | `Action.proxySensor` | Preserve sensors. |
| Resources | `Action.proxyBinder` | Preserve asynchronous Binder handling. |
| Resources | `Action.manageAlarm` | Preserve alarms. |
| Resources | `Action.proxyWakeLock` | Preserve WakeLock handling. |
| Resources | `Action.proxyGPS` | Preserve GPS handling. |
| Resources | `Action.proxyAudio` | Preserve audio handling. |
| Resources | `Action.proxyBtScan` | Preserve Bluetooth scanning. |
| Resources | `OplusHansManager.tryProxyWakeLock` | Keep the manager WakeLock decision coherent. |
| Network | `OplusHansManager.nwPowerSetFirewall` | Bypass Hans firewall restriction. |
| Network | `OplusHansManager.restrictRStateNonKeyProcsNet` | Bypass R-state network restriction. |

## Packet wake state

`unfreezeForKernel(type=4, ..., targetUid, ...)` is the Packet wake entry. Complete block
returns before the OEM method runs. Throttling stores the elapsed realtime of the last
allowed event per current UID; the first event is allowed and later events within the
cooldown are suppressed. State is cleared whenever a new policy snapshot is loaded.

Blocked-event logging is limited to one message per UID per 60 seconds to avoid turning a
packet storm into a `system_server` log storm.

## Wake-source gates

`unfreezeForKernel` calls `OplusHansProcessFreeze.unfreezeProcessForKernel` before the
Hans state transition. The hook therefore classifies kernel types 0-3 and returns before
the OEM method for a blocked Async Binder, Sync Binder, Transaction Binder, or Signal.
Type 4 remains controlled by the richer Packet allow/throttle/block policy. Block logs
include caller PID/UID, AIDL descriptor, and transaction code.

`HansCGroup.hansUnfreezeLocked(OplusHansPackage, reason, cpnInfo)` is the last common
framework-side gate before firewall relaxation and cgroup thaw. Reasons are grouped into
Activity/Input, Service, Broadcast, Provider, Job/Sync, WakeLock, audio/media,
connectivity, system-scene, Binder, signal, and Other categories. Unknown strings map to
Other, so future OEM reasons remain controllable. The module's own `force/HansPolicy`
cleanup and full-exemption rules bypass every wake gate.

Activity/Input and system-scene blocking are intentionally opt-in. Enabling either can
prevent foreground launch or lifecycle recovery.

## Alarm wake state

`checkAlarmIfRestricted(uid, packageName, action)` runs before AlarmManager delivery. A
block or throttle hit returns `true`, keeping the event on the Hans proxy path instead of
calling `unfreezeAndTransState(..., "Alarm", action)`. Throttle state is keyed by the
current UID and cleared on policy reload. Blocked-event logging is rate-limited per UID.

AlarmManager can dispatch several alarms for one UID in a single batch. On this firmware,
only the first item necessarily passes through `checkAlarmIfRestricted`; sibling items can
continue through the broadcast path and cause `reason=Broadcast`. A blocked Alarm arms a
five-second per-UID gate. During that narrow window, matching package broadcasts in
`enqueueProxyBroadcastLocked` are treated as already handled, preventing the same batch
from bypassing the Alarm decision. The gate is also cleared on policy reload.

The same batch can update a system WakeLock whose `WorkSource` contains the target UID
even when its WorkChain belongs to another app and JobScheduler. The OEM implementation
passes every attributed UID to `unFreezeForwl`, producing `acquireWakeLock` and
`updateWLWorkSource` unfreezes through its two overloads. While the five-second gate is
active, the module suppresses direct controlled-UID calls and removes controlled UIDs
from list calls, leaving unrelated system/app UIDs untouched.

## Failure behavior

- Hook installation errors are recorded and shown in the manager.
- Policy JSON is schema validated before becoming the active immutable snapshot.
- Provider errors retain the previous snapshot and schedule a retry.
- Package lookup failure allows the OEM operation.
- Individual decision errors preserve the original return value.
- Schema v1-v4 migrate to v5; legacy user IDs are discarded.
