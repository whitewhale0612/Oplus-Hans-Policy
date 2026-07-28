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

The validated build installs 22 targets:

| Area | Target | Purpose |
| --- | --- | --- |
| Bootstrap | `OplusAppStartupManager.shouldPreventStartProvider` | Allow system UID to start the module provider. |
| Runtime | `OplusHansManager.init` | Capture context and manager instance. |
| Runtime | `OplusHansManager.bootCompleted` | Cold-boot initialization fallback. |
| Timing | `OplusHansDBConfig.getRtoMCheckTime` | Per-package R to M delay. |
| Timing | `OplusHansDBConfig.getMtoFCheckTime` | Per-package M to F and Packet-specific delay. |
| Exemption | `OplusHansManager.isHansCoreApp` | Component-policy exemption. |
| Exemption | `OplusHansManager.isLcdOnNonRestrictPkg` | Standard state-machine exemption. |
| Freeze gate | `HansCGroup.hansFreezeLocked` | Block selected sources without faking success. |
| Packet | `OplusHansManager.unfreezeForKernel` | Allow, throttle, or block `type=4` wake. |
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

## Failure behavior

- Hook installation errors are recorded and shown in the manager.
- Policy JSON is schema validated before becoming the active immutable snapshot.
- Provider errors retain the previous snapshot and schedule a retry.
- Package lookup failure allows the OEM operation.
- Individual decision errors preserve the original return value.
- Schema v1 and v2 migrate to v3; legacy user IDs are discarded.
