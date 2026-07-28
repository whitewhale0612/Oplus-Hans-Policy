# 真机验证记录

采集日期：2026-07-29（Asia/Shanghai）。

| 项目 | 值 |
| --- | --- |
| 设备 | OnePlus PKX110 |
| 固件 | `PKX110_16.0.9.401(CN01)` |
| Android | 16 / API 36 |
| 模块 | `0.3.1`（`versionCode 5`） |
| 测试应用 | QQ `com.tencent.mobileqq` |
| 运行时 | 22 个 Hook，active，无上报错误 |

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

## 冷启动与 Provider 拉起

重启后不打开管理应用，运行时状态为：

```text
hook_count=22
active=true
last_error=""
runtime_source=OplusHansManager.bootCompleted
```

system 与 local policy revision 一致，证明 Provider 启动 Hook 已解决此前冷启动一直显示
“等待 system_server 上报”的问题。

## 发布产物

```text
HansPolicy-v0.3.1.apk
SHA-256 d4c10d2fa49f5a2d4a315d317c13f989e9b071b0e32469993ec31342f56ca423
签名证书 SHA-256 9fdb1352f5672e4ac7afdebf8f5f7cef4692882bea7e2e9d6b05b0b352db600c
```

发布 APK 不带 debuggable 标记，并通过 APK Signature Scheme v3 校验。

以上结论只覆盖这一版固件，不代表所有 Oplus Android 16 固件兼容。
