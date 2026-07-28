# Device verification

Captured on 2026-07-29 (Asia/Shanghai).

| Item | Value |
| --- | --- |
| Device | OnePlus PKX110 |
| ROM | `PKX110_16.0.9.401(CN01)` |
| Android | 16 / API 36 |
| Module | `0.3.1` (`versionCode 5`) |
| Test package | QQ `com.tencent.mobileqq` |
| Runtime hooks | 22, active, no reported error |

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

## Cold boot and provider startup

After reboot, without opening the manager first:

```text
hook_count=22
active=true
last_error=""
runtime_source=OplusHansManager.bootCompleted
```

The system and local policy revisions matched. This confirms that the provider bootstrap
hook resolves the previous "waiting for system_server" cold-start state.

## Build artifact

```text
HansPolicy-v0.3.1.apk
SHA-256 d4c10d2fa49f5a2d4a315d317c13f989e9b071b0e32469993ec31342f56ca423
Signer certificate SHA-256 9fdb1352f5672e4ac7afdebf8f5f7cef4692882bea7e2e9d6b05b0b352db600c
```

The release APK is not debuggable and verifies with APK Signature Scheme v3.

These results validate one firmware build, not all Oplus Android 16 releases.
