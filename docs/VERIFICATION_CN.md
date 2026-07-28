# 真机验证记录

采集日期：2026-07-29（Asia/Shanghai）。

| 项目 | 值 |
| --- | --- |
| 设备 | OnePlus PKX110 |
| 固件 | `PKX110_16.0.9.401(CN01)` |
| Android | 16 / API 36 |
| 模块 | `0.5.0`（`versionCode 7`） |
| 测试应用 | QQ `com.tencent.mobileqq` 9.3.25 (15220) |
| 运行时 | 27 个 Hook，active，无上报错误 |

## 完全阻止 Packet 唤醒

打开完全阻止后，真实网络事件产生：

```text
HansPolicy: Packet wake blocked uid=10357 pkg=com.tencent.mobileqq
```

QQ 主进程、MSF 和 iLink 均保持冻结。UID cgroup 状态为：

```text
cgroup.freeze: 1
cgroup.events:
populated 1
frozen 1
```

## Packet 唤醒限频

冷却时间为 60 秒时，首个 Packet 正常放行，同一窗口内的后续事件被拦截，QQ 随后
重新进入 Frozen：

```text
01:09:01.902 unfreeze uid: 10357 reason: Packet
01:09:01.902 HansPolicy: Packet wake throttled uid=10357
01:09:06.904 F enter(), M stay=5
01:09:06.915 freeze uid: 10357
```

## Packet 专用再次冻结时间

通过管理界面临时设置 2 秒 Packet 延时。真实 Packet 事件约 2 秒后到达 M 到 F
计时器：

```text
01:19:33.508 unfreeze uid: 10357 reason: Packet
01:19:35.511 can not transition from M to F. reason: Packet
```

本次样本中 QQ 同时持有音频焦点，因此厂商状态机拒绝了第一次转 F。这证明 2 秒计时
已经命中，同时没有绕过 Hans 的场景检查；之后的厂商状态转换正常冻结 UID。

## 约 5 分钟 Alarm 唤醒根因

QQ 会注册动态命名的 MSF/iLink 唤醒闹钟。AlarmManager 在同一个 elapsed-realtime
时间点合并投递了三个事件：

```text
ELAPSED_WAKEUP action=ALARM_ACTION(16011)
ELAPSED_WAKEUP action=com.tencent.mobileqq:MSF_112904847
RTC_WAKEUP     action=com.tencent.mobileqq:MSF_85333640
```

首项进入 `checkAlarmIfRestricted`。完整修复前，批次后续项会从广播路径触发
`reason=Broadcast`；系统 WakeLock 归因还会产生
`acquireWakeLock ... WorkSource{10357}` 和 `updateWLWorkSource` 解冻。因此该周期事件的
根因是带广播与 WakeLock 旁路的 Alarm 批次，不是入站网络包。

开启“完全阻止 Alarm 唤醒”后，最终构建实测日志为：

```text
03:52:27.851 HansPolicy: Alarm wake blocked uid=10357 pkg=com.tencent.mobileqq
03:52:27.866 HansPolicy: Alarm batch broadcast suppressed uid=10357 pkg=com.tencent.mobileqq
```

该批次窗口内没有 UID 10357 的 `reason=Alarm`、`reason=Broadcast`、
`acquireWakeLock` 或 `updateWLWorkSource` 解冻。10 秒后内核状态仍为：

```text
cgroup.freeze: 1
cgroup.events:
populated 1
frozen 1
```

同时观察到独立的 `W_AsyncBinder` 唤醒；它不属于约 5 分钟的 Alarm 链路。

## 异步 Binder 唤醒

追踪确认，显示为 `W_AsyncBinder` 的内核回调会先经过
`OplusHansManager.unfreezeForKernel`，再进入常规 Hans 状态转换。本次 QQ 的真实回调是
`system_server` 发出的、被厂商列入白名单的电话状态监听事务：

```text
HansPolicy: Wake source blocked uid=10357 pkg=com.tencent.mobileqq source=AsyncBinder reason=kernel:AsyncBinder detail=com.android.internal.telephony.IPhoneStateListener/10 caller=3404/1000
```

回调发生前以及拦截后，UID cgroup 均为：

```text
populated 1
frozen 1
```

从桌面启动 QQ 仍然正常，cgroup 随即变为 `frozen 0`；返回桌面后 Hans 再次将其冻结。
同时观察到未配置的微信 `W_AsyncBinder` 事件正常放行，证明该拦截按包名生效。

## 冷启动与 Provider 拉起

重启后不打开管理应用，运行时状态为：

```text
hook_count=27
active=true
last_error=""
runtime_source=OplusHansManager.bootCompleted
policy_revision=25
```

system 与 local policy revision 一致，证明 Provider 启动 Hook 已解决此前冷启动一直显示
“等待 system_server 上报”的问题。

## 发布产物

```text
HansPolicy-v0.5.0.apk
SHA-256 1a8ecd9578e7152511fecea988b68f6a4364e2d5f325b466513cd0f45b07b433
签名证书 SHA-256 9fdb1352f5672e4ac7afdebf8f5f7cef4692882bea7e2e9d6b05b0b352db600c
```

发布 APK 不带 debuggable 标记，并通过 APK Signature Scheme v3 与 v4 校验。安装使用
`adb install --no-incremental -r`；增量安装路径在框架开机读取模块 APK 时可能尚不可用，
不适合本模块。

以上结论只覆盖这一版固件，不代表所有 Oplus Android 16 固件兼容。
