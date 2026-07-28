# Oplus Hans Policy

[简体中文](README_CN.md)

An LSPosed module for per-app policy overrides in Oplus Hans/OFreezer. It runs in
`system_server`, matches rules by package name, and changes Hans decisions without
writing kernel freezer files or replacing the OEM state machine.

> [!WARNING]
> This module hooks private Oplus framework APIs in `system_server`. It is version
> locked to the ROM listed below. An incompatible OTA can cause boot instability.
> Keep an LSPosed/Vector recovery path available and test with the master switch off.

<p align="center">
  <img src="docs/images/main-screen.png" width="30%" alt="Runtime status and app rules">
  <img src="docs/images/rule-dialog.png" width="30%" alt="Timing and packet wake controls">
  <img src="docs/images/packet-control.png" width="30%" alt="Freeze source and resource controls">
</p>

## Why this exists

On the validated Oplus Android 16 build, short-cycle background freezing is controlled
by Hans inside `system_server`:

```text
app leaves foreground
  -> HansAppStateMachine: Running (R)
  -> Middle (M): proxy components and trim memory
  -> Frozen (F)
  -> HansCGroup.hansFreezeLocked()
  -> Process.freezeCgroupUid(uid, true)
  -> /sys/fs/cgroup/apps/uid_<uid>/cgroup.freeze
```

Athena supplies and updates OEM policy, but the real-time decision and cgroup action
are performed by Hans/OFreezer. Editing a static Athena list alone does not provide
precise per-app control over every runtime path.

Network traffic is another important path. A packet for a frozen persistent socket,
such as QQ MSF/iLink, reaches `OplusHansManager.unfreezeForKernel(type=4)` and normally
unfreezes the whole UID with `reason=Packet`. Hans Policy can allow, throttle, or block
that event.

Binder is independent of Packet and WakeLock. A kernel `type=0` callback can pass the
OEM asynchronous-Binder whitelist and unfreeze the UID with `reason=W_AsyncBinder`.
Hans Policy can stop that callback before the native process-freezer thaw, and a final
unfreeze gate covers framework component, scene, and future unknown reasons.

## Features

- Package-name rules with runtime UID resolution; no stored UID or Android user ID.
- Full Hans exemption with a final cgroup freeze gate.
- Independent R to M and M to F timing overrides.
- Freeze-source gates for normal state-machine, Fast Freezer, Super Freeze, and preload.
- Optional preservation of network, Service, Job, broadcast, alarm, Binder, sensor, GPS,
  WakeLock, audio, and Bluetooth scan behavior while Hans restrictions are active.
- Packet wake policy: follow system, minimum-interval throttling, or complete blocking.
- Optional Packet-specific refreeze delay.
- Alarm wake policy: follow system, minimum-interval throttling, or complete blocking.
- Optional Alarm-specific refreeze delay.
- Independent wake-source gates for Async/Sync/Transaction Binder, signal,
  Activity/Input, Service, Broadcast, Provider, Job/Sync, WakeLock, audio/media,
  connectivity, system scenes, and unknown future reasons.
- Direct-boot policy storage and live reload without restarting `system_server`.
- Runtime health reporting with boot ID, policy revision, initialization source, hook
  count, and hook errors.
- Fail-open behavior when policy parsing, package lookup, or an individual hook fails.

The master switch is off on a clean install. Rules do not intervene until it is enabled.

## Compatibility

| Item | Validated value |
| --- | --- |
| Device | OnePlus PKX110 |
| OS | Android 16 / API 36 |
| ROM | `PKX110_16.0.9.401(CN01)` |
| Framework classes | Oplus `oplus-services.jar` from the ROM above |
| Current source | `0.5.0` (`versionCode 7`) |
| Hook target | `android` / `system_server` |
| Framework | LSPosed API 82 compatible; also tested with Vector Framework 2.0 |

Other ColorOS/OxygenOS versions are not claimed compatible. Check all hook signatures
after every OTA before re-enabling the policy master switch.

## Install

1. Download `HansPolicy-v0.4.0.apk` from the
   [latest release](https://github.com/whitewhale0612/Oplus-Hans-Policy/releases/latest).
   Verify its SHA-256 before installation:

   ```text
   6e4ae5c1f47faaf561675cfb7d9eabe4e115b084f019d500124487e88beaea82
   ```

2. Install or update it:

   ```bash
   adb install --no-incremental -r HansPolicy-v0.4.0.apk
   ```

3. Enable **Hans Policy** in LSPosed/Vector.
4. Scope it only to **System Framework** (`android` / `system`). Do not scope target apps.
5. Reboot the device.
6. Open the manager and confirm `Hook connected - 27 targets` with matching system and
   local revisions.
7. Add one test rule, then enable the in-app master switch.

Package rules are resolved to current UIDs at runtime. Reinstalling or recreating an app
does not require editing the rule when its UID changes.

## Packet wake policy

| Mode | Behavior |
| --- | --- |
| Follow system | Hans handles every Packet wake normally. |
| Throttle | The first Packet wake is allowed; later events inside the configured cooldown are blocked. |
| Block | Every `type=4` Packet wake for the matched UID is suppressed. |

Blocking Packet wake can delay messages, VoIP signaling, socket progress, and other
background network work. It does not drop packets at the firewall; it prevents that Hans
callback from unfreezing the UID. Test communication apps carefully.

Alarm is a separate wake path. QQ MSF/iLink registers `ELAPSED_WAKEUP` alarms; on the
validated sample, a dynamically numbered `ALARM_ACTION(...)` fired about every five
minutes and entered
`OplusHansManager.checkAlarmIfRestricted`, which normally unfreezes the UID with
`reason=Alarm`.

## Alarm wake policy

| Mode | Behavior |
| --- | --- |
| Follow system | Hans handles an expired Alarm normally. |
| Throttle | The first Alarm wake is allowed; later events inside the cooldown remain proxied. |
| Block | Matching Alarm delivery is suppressed before `reason=Alarm` can unfreeze the UID. |

This controls Alarm delivery for a frozen app; it does not delete alarms registered by
the app. The separate "preserve alarms" resource option has the opposite purpose and
should not be enabled when the goal is to prevent periodic Alarm wakeups.

## Verify

```bash
adb logcat -v time | grep -E 'HansPolicy|OplusHansManager'
```

Useful events include:

```text
HansPolicy: installed 27 hooks
HansPolicy: Wake source blocked uid=<uid> pkg=<package> source=AsyncBinder reason=kernel:AsyncBinder detail=<aidl>/<code> caller=<pid>/<uid>
HansPolicy: Packet wake blocked uid=<uid> pkg=<package>
HansPolicy: Packet wake throttled uid=<uid> pkg=<package>
HansPolicy: Alarm wake blocked uid=<uid> pkg=<package> action=<action>
HansPolicy: Alarm batch broadcast suppressed uid=<uid> pkg=<package>
OplusHansManager: unfreeze uid: <uid> ... reason: Packet
OplusHansManager: freeze uid: <uid> ...
```

For a frozen UID:

```bash
adb shell su -c 'cat /sys/fs/cgroup/apps/uid_<uid>/cgroup.events'
```

Expected kernel state includes `frozen 1`. See [verification notes](docs/VERIFICATION.md)
for the captured v0.5.0 results.

## Build

Requirements:

- JDK 17 or newer
- Android SDK `platforms;android-35`
- Android SDK `build-tools;35.0.0`
- `ANDROID_HOME` or `ANDROID_SDK_ROOT`, or a local `local.properties`

Linux/macOS:

```bash
chmod +x scripts/build.sh
./scripts/build.sh
```

Windows:

```bat
scripts\build.bat
```

The default build runs lint and creates `dist/HansPolicy-v0.5.0-debug.apk`. The Xposed API
82 JAR is a `compileOnly` dependency and is not packaged into the APK.

Manual Gradle build:

```bash
./gradlew lintDebug assembleDebug
```

## Project layout

```text
app/                 Android manager and LSPosed hook sources
docs/                Architecture, verification notes, and screenshots
scripts/             Linux/macOS and Windows build helpers
.github/workflows/   Reproducible debug build and lint workflow
dist/                Local build output (gitignored)
artifacts/           Local signed/release artifacts (gitignored)
```

## Recovery

Normally, turn off the in-app master switch to restore stock Hans behavior immediately.
If the device cannot reach the launcher, disable the module from the framework's safe
mode and reboot. Vector Framework users can use:

```bash
adb shell /data/adb/modules/zygisk_vector/cli modules disable io.github.whitewhale.hanspolicy
adb reboot
```

The module does not modify RUS data, system properties, or cgroup files. Disabling it
leaves no persistent OEM-policy mutation to undo.

## Limitations

- Private Oplus method signatures can change between ROM builds.
- Full exemption and resource preservation can materially increase background power use.
- Packet blocking can delay user-visible communications.
- Blocking Activity/Input or system-scene wakes can prevent normal app launch or system
  lifecycle recovery. These high-risk controls are off by default.
- Android shared UIDs are controlled as one UID even when several packages share it.
- Already queued state-machine messages are not rescheduled; timing changes apply from
  the next state transition.

Implementation details and the complete hook map are in
[Architecture](docs/ARCHITECTURE.md).
