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
  <img src="docs/images/main-screen.png" width="30%" alt="Module status and system information">
  <img src="docs/images/rule-dialog.png" width="30%" alt="Per-app freeze policy configuration">
  <img src="docs/images/packet-control.png" width="30%" alt="Per-source wake blocking">
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

## Policy guide and recommendations

The three similarly named sections act at different stages. **Block freeze sources**
keeps an app from entering Frozen. **Preserve resources while frozen** relaxes Hans
proxying and resource cleanup. **Block wake sources** rejects selected events after the
app is already Frozen. Start with every checkbox clear and add one control at a time
after identifying its reason in logs.

### Basics and timing

| Setting | Effect | Possible impact | Recommendation |
| --- | --- | --- | --- |
| Policy master switch | Enables or disables all package rules together. | Every enabled rule takes effect immediately. | Keep off after first install or OTA; enable only after all 27 hooks report healthy. |
| Enable this rule | Toggles one package rule without deleting it. | Disabled apps fully follow OEM policy. | Use for A/B comparisons and troubleshooting. |
| Full Hans exemption | Prevents Hans freezing and restrictions for the app. | Background CPU, network, and power use can rise substantially; wake gates become irrelevant. | Reserve for navigation or continuous playback apps that genuinely cannot be frozen. |
| Custom R / M / F timing | Replaces the default Hans timing with the two delays below. | A poor combination can freeze too early, delay background behavior, or cause freeze/thaw churn. | Leave off by default; record the OEM timing first and override only an app with a demonstrated need. |
| R to M delay | Sets how long an app stays Running before Middle after leaving foreground. | Too short proxies components early; too long increases background activity. | Follow system first; if needed, begin around 10-30 seconds. |
| M to F delay | Sets the wait from Middle to Frozen. | Too short can cause freeze/thaw churn; too long reduces savings. | Follow system first. Three seconds is aggressive and only suitable for validated apps. |

### Block freeze sources

Checking one means “do not let this mechanism freeze the app”; it does not make freezing
more aggressive.

| Setting | Effect | Possible impact | Recommendation |
| --- | --- | --- | --- |
| Normal state-machine freeze | Stops the normal R/M/F path at the final freeze gate. | Most background freezing is disabled and power use rises. | Leave off; adjust timing instead in most cases. |
| Fast Freezer | Blocks the low-latency freezer entry. | The app can remain active longer after backgrounding. | Enable only when Fast Freezer is confirmed to break behavior. |
| Super Freeze | Blocks stronger Super Freeze scenes. | Long-idle restrictions weaken and standby drain can rise. | Leave off by default. |
| Preload freeze | Blocks the preload-specific freezer entry. | Preloaded processes may remain resident. | Use only for an app confirmed to follow this path. |

### Preserve resources while frozen

These settings bypass Hans proxying or cleanup for a resource. They do not guarantee
that frozen threads can continue executing.

| Setting | Effect | Possible impact | Recommendation |
| --- | --- | --- | --- |
| Network and existing connections | Preserves firewall allowance and established connections. | Traffic may continue and persistent sockets stay alive longer. | Use only for required keepalive connections; normally leave off when blocking Packet wakes. |
| Service dispatch | Does not proxy Service events. | Services can cause more wakeups and background work. | Enable only when delayed services break required behavior. |
| Job dispatch | Does not proxy JobScheduler work. | Maintenance tasks run more often. | Leave off; enable selectively for required synchronization. |
| Broadcast delivery | Does not proxy broadcasts. | System and app broadcasts can wake the process frequently. | Leave off by default. |
| Alarms and timers | Does not proxy Alarm events. | Periodic alarms return and create more wakeups. | Must stay off when throttling or blocking Alarm wakes; use for alarm-class apps only. |
| Async Binder | Does not proxy corresponding Binder calls. | `W_AsyncBinder` and related wakes can return. | Leave off when the goal is to block Binder wakes. |
| Sensors | Preserves sensor access. | Continuous sampling increases power use. | Use only for motion or health apps that require it. |
| Location / GPS | Preserves location resources. | Location and radio power use can rise sharply. | Use only for navigation or track recording. |
| WakeLock | Preserves app WakeLocks. | The device may fail to enter deep sleep. | Leave off unless proxying demonstrably interrupts required work. |
| Audio | Preserves audio behavior. | Background audio and focus can remain active. | Enable selectively for music or calling apps. |
| Bluetooth scan | Preserves Bluetooth scanning. | Scan power and callback volume increase. | Enable selectively for wearable or accessory apps. |

### Block wake sources

Checking one keeps that event from thawing an already frozen UID. New framework reasons
that are not recognized map to **Other and future unknown reasons**.

| Setting | Effect | Possible impact | Recommendation |
| --- | --- | --- | --- |
| Async Binder, including `W_AsyncBinder` | Rejects async Binder callbacks before native thaw. | Phone-state, observer, and other one-way callbacks are delayed. | Enable after logs confirm useless wakes; recommended for the validated QQ sample. |
| Sync Binder | Prevents synchronous Binder from thawing the target. | The caller can block, time out, or fail. | High risk; leave off. |
| Transaction Binder | Prevents transaction-style Binder thaw. | Cross-process features can fail. | Leave off unless a specific useless transaction is identified. |
| Process signal | Prevents signal-triggered thaw. | Termination, diagnostics, or native recovery can be delayed. | High risk; leave off. |
| Activity / Input | Prevents Activity, relaunch, and input thaw. | The user may be unable to open or operate the app. | Extreme risk; do not enable. |
| Service / Bind / Restart | Prevents service start, bind, and restart thaw. | Push services, foreground services, or bindings can fail. | Leave off; test only for nonessential background services. |
| Broadcast | Prevents broadcast-triggered thaw. | Push, system-state, and app events are delayed. | Test for non-communication apps; normally leave off for messaging. |
| Content Provider | Prevents provider-access thaw. | Calling processes can block or fail queries. | High risk; leave off. |
| Job / Sync | Prevents scheduled task and sync thaw. | Cloud sync, backup, and maintenance are deferred. | Suitable only for apps without required background sync. |
| WakeLock | Prevents WorkSource/WakeLock attribution from thawing the app. | Background work that relies on the WakeLock can fail. | Enable when logs confirm useless WakeLock wakes. |
| Audio / media / Bluetooth control | Prevents playback and control-event thaw. | Background playback, headset control, and calls can fail. | Leave off for media/calling apps; test by logs elsewhere. |
| Connectivity / navigation / traffic | Prevents network-state and navigation-scene thaw. | Network changes and navigation state are not processed promptly. | Leave off by default. |
| System scenes and lifecycle | Prevents screen, sleep-exit, Watchdog, and similar thaw. | Lifecycle recovery and system cleanup can break. | Extreme risk; do not enable. |
| Other and future unknown reasons | Blocks every unclassified framework reason. | A required reason added by an OTA can also be blocked. | Leave off; use only for short diagnostic tests. |

### Suggested profiles

| Goal | Suggested configuration |
| --- | --- |
| Conservative observation | Follow OEM timing, Packet, and Alarm behavior; clear every checkbox and inspect logs first. |
| Ordinary non-realtime app | Packet throttle at 60-300 seconds and Alarm throttle at 15 minutes; add Async Binder, Job/Sync, or WakeLock gates only when logs justify them. |
| Aggressive policy for the validated QQ sample | Block Packet and Alarm, and check only Async Binder. Do not check Activity/Input, system scenes, Sync Binder, Provider, or Signal. Messages and VoIP can be delayed. |
| App that must work continuously | Preserve only the required network, audio, location, or related resource first; use full Hans exemption only if selective preservation is insufficient. |

## Packet wake policy

| Mode | Effect | Possible impact | Recommendation |
| --- | --- | --- | --- |
| Follow system | Hans handles every Packet wake normally. | Background packets can thaw the app frequently. | Use for messaging, VoIP, or initial testing. |
| Throttle | The first Packet wake is allowed; later events inside the cooldown are blocked. | Messages and network work can be delayed inside the window. | Balanced option; begin around 60-300 seconds. |
| Block | Every `type=4` Packet wake for the matched UID is suppressed. | Messaging, VoIP, and persistent-socket progress may wait for another wake or foreground launch. | Use only when background realtime communication is not required. |
| Minimum wake interval | Sets the cooldown between allowed thaws in Throttle mode. | Longer values reduce wakes but increase network-event latency. | Start at 60-300 seconds; unused in Follow system and Block modes. |
| Custom post-wake hold time | Overrides this Packet thaw's M-to-F refreeze delay. | Too short can refreeze before network work completes; too long expands the background execution window. | Leave off initially; after measuring task duration, start around 5-15 seconds. Unused in Block mode. |

Blocking Packet wake can delay messages, VoIP signaling, socket progress, and other
background network work. It does not drop packets at the firewall; it prevents that Hans
callback from unfreezing the UID. Test communication apps carefully.

Alarm is a separate wake path. QQ MSF/iLink registers `ELAPSED_WAKEUP` alarms; on the
validated sample, a dynamically numbered `ALARM_ACTION(...)` fired about every five
minutes and entered
`OplusHansManager.checkAlarmIfRestricted`, which normally unfreezes the UID with
`reason=Alarm`.

## Alarm wake policy

| Mode | Effect | Possible impact | Recommendation |
| --- | --- | --- | --- |
| Follow system | Hans handles an expired Alarm normally. | Periodic alarms can thaw the app frequently. | Use for alarms, calendars, health reminders, and other time-sensitive apps. |
| Throttle | The first Alarm wake is allowed; later events inside the cooldown remain proxied. | Scheduled tasks are delayed inside the window. | Balanced option; the default cooldown is 15 minutes. |
| Block | Matching Alarm delivery is suppressed before `reason=Alarm` can thaw the UID. | Background timers, reminders, or keepalive work may not execute. | Use for confirmed periodic wakes with no timing requirement. |
| Minimum wake interval | Sets the cooldown between allowed Alarm thaws in Throttle mode. | Longer values cause more visible task coalescing or delay. | Keep the 15-minute default initially; unused in Follow system and Block modes. |
| Custom post-wake hold time | Overrides this Alarm thaw's M-to-F refreeze delay. | Too short can interrupt scheduled work; too long lets periodic alarms create extra background activity. | Leave off initially; use only when a task needs a fixed window, starting around 10-30 seconds. Unused in Block mode. |

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
