# Device verification

Captured on 2026-07-29 (Asia/Shanghai).

| Item | Value |
| --- | --- |
| Device | OnePlus PKX110 |
| ROM | `PKX110_16.0.9.401(CN01)` |
| Android | 16 / API 36 |
| Module | `0.5.0` (`versionCode 7`) |
| Test package | QQ `com.tencent.mobileqq` 9.3.25 (15220) |
| Runtime hooks | 27, active, no reported error |

## Packet block

With complete Packet blocking enabled, a real inbound event produced:

```text
HansPolicy: Packet wake blocked uid=10357 pkg=com.tencent.mobileqq
```

QQ main, MSF, and iLink processes remained frozen. The UID cgroup reported:

```text
cgroup.freeze: 1
cgroup.events:
populated 1
frozen 1
```

## Packet throttle

With a 60-second cooldown, the first Packet event was allowed. Later events in the same
window were blocked, and QQ returned to the Frozen state:

```text
01:09:01.902 unfreeze uid: 10357 reason: Packet
01:09:01.902 HansPolicy: Packet wake throttled uid=10357
01:09:06.904 F enter(), M stay=5
01:09:06.915 freeze uid: 10357
```

## Packet-specific refreeze delay

A temporary two-second Packet delay was loaded through the manager UI. A real Packet
event reached the M-to-F timer almost exactly two seconds later:

```text
01:19:33.508 unfreeze uid: 10357 reason: Packet
01:19:35.511 can not transition from M to F. reason: Packet
```

QQ held audio focus during this sample, so the OEM state machine rejected that first
transition. This verifies the two-second timer without bypassing Hans scene checks; a
later OEM transition froze the UID normally.

## Five-minute Alarm wake root cause

QQ registers dynamically named MSF/iLink wakeup alarms. AlarmManager grouped three
events at the same elapsed-realtime boundary:

```text
ELAPSED_WAKEUP action=ALARM_ACTION(16011)
ELAPSED_WAKEUP action=com.tencent.mobileqq:MSF_112904847
RTC_WAKEUP     action=com.tencent.mobileqq:MSF_85333640
```

The first event enters `checkAlarmIfRestricted`. Before the complete fix, sibling events
fell through to `reason=Broadcast`; system WakeLock attribution also produced
`acquireWakeLock ... WorkSource{10357}` and `updateWLWorkSource` unfreezes. The periodic
event is therefore an Alarm batch with broadcast and WakeLock side paths, not an inbound
network packet.

With complete Alarm blocking enabled, the final build produced:

```text
03:52:27.851 HansPolicy: Alarm wake blocked uid=10357 pkg=com.tencent.mobileqq
03:52:27.866 HansPolicy: Alarm batch broadcast suppressed uid=10357 pkg=com.tencent.mobileqq
```

There was no `reason=Alarm`, `reason=Broadcast`, `acquireWakeLock`, or
`updateWLWorkSource` unfreeze for UID 10357 in the batch window. Ten seconds later the
kernel state was still:

```text
cgroup.freeze: 1
cgroup.events:
populated 1
frozen 1
```

Independent `W_AsyncBinder` wakes were also observed. They are not part of the roughly
five-minute Alarm chain.

## Asynchronous Binder wake

The kernel callback reported as `W_AsyncBinder` was traced through
`OplusHansManager.unfreezeForKernel` before the normal Hans state transition. The real
QQ callback was an OEM-whitelisted phone-state listener transaction from
`system_server`:

```text
HansPolicy: Wake source blocked uid=10357 pkg=com.tencent.mobileqq source=AsyncBinder reason=kernel:AsyncBinder detail=com.android.internal.telephony.IPhoneStateListener/10 caller=3404/1000
```

Immediately before the callback, and again after it was blocked, the UID cgroup showed:

```text
populated 1
frozen 1
```

Launching QQ from the home screen still worked and changed the cgroup to `frozen 0`.
Returning home allowed Hans to freeze it again. An unconfigured WeChat
`W_AsyncBinder` event was allowed, confirming that the gate is package-specific.

## Cold boot and provider startup

After reboot, without opening the manager first:

```text
hook_count=27
active=true
last_error=""
runtime_source=OplusHansManager.bootCompleted
policy_revision=25
```

The system and local policy revisions matched. This confirms that the provider bootstrap
hook resolves the previous "waiting for system_server" cold-start state.

## Build artifact

```text
HansPolicy-v0.5.0.apk
SHA-256 1a8ecd9578e7152511fecea988b68f6a4364e2d5f325b466513cd0f45b07b433
Signer certificate SHA-256 9fdb1352f5672e4ac7afdebf8f5f7cef4692882bea7e2e9d6b05b0b352db600c
```

The release APK is not debuggable and verifies with APK Signature Schemes v3 and v4. It was
installed with `adb install --no-incremental -r`; incremental install is unsuitable here
because the framework may need the module APK before the incremental path is available.

These results validate one firmware build, not all Oplus Android 16 releases.
