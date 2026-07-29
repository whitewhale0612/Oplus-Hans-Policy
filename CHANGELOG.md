# Changelog

## 0.6.0 - 2026-07-29

- Precompute policy capability flags and return early from inactive Hook paths.
- Observe UIDs only for enabled matching rules and clear observations after policy reload.
- Add tests for the master switch, disabled rules, aggregate flags, and duplicate packages.
- Disable high-frequency runtime event logs in Release while retaining full Debug diagnostics.
- Avoid Release log string construction, rate-limit map writes, and unnecessary Packet clock reads.
- Preserve the one-time Hook installation result and Hook installation errors in Release builds.

## 0.5.0 - 2026-07-29

- Add configurable wake-source gates for Async/Sync/Transaction Binder, signals,
  activity/input, services, broadcasts, providers, Job/Sync, WakeLock, audio/media,
  connectivity, system scenes, and unknown future reasons.
- Block kernel Binder and signal callbacks before `unfreezeProcessForKernel` can thaw
  the UID, including OEM-whitelisted `W_AsyncBinder` events.
- Add a final `HansCGroup.hansUnfreezeLocked` gate for framework and scene wake paths.
- Log Binder caller PID/UID, AIDL descriptor, transaction code, raw reason, and component
  detail for blocked wake sources, with per-source rate limiting.
- Preserve foreground behavior by default; high-risk Activity/Input and system-scene
  gates remain opt-in. Module cleanup and full-exemption unfreezes always pass.
- Migrate policy schema to v5 while retaining v1-v4 compatibility.

## 0.4.0 - 2026-07-29

- Add Alarm wake controls: follow system, throttle, and block.
- Add an Alarm-specific refreeze delay.
- Hook `checkAlarmIfRestricted` before `reason=Alarm` unfreezes an app UID.
- Suppress sibling broadcasts in the same blocked Alarm batch so they cannot fall through
  to a `reason=Broadcast` unfreeze.
- Filter Alarm-correlated `acquireWakeLock` and `updateWLWorkSource` callbacks that
  otherwise unfreeze the UID through system WakeLock attribution side paths.
- Resolve Alarm callback UIDs from package-name rules and rate-limit block logging.
- Migrate policy schema to v4 while retaining v1/v2/v3 compatibility.

## 0.3.1 - 2026-07-29

- Add Packet wake controls: follow system, throttle, and block.
- Add a Packet-specific refreeze delay.
- Resolve Packet callback UIDs to package-name rules at runtime.
- Limit repeated Packet block logging per UID.
- Add a narrow provider-startup hook so `system_server` can load policy after cold boot
  without first opening the manager UI.
- Report 22 installed hook targets and synchronize system/local policy revisions.
- Migrate policy schema to v3 while retaining v1/v2 compatibility.
