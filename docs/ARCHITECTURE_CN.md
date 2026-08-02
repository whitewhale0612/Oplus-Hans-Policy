# 架构说明

## 边界

Hans Policy 只修改 Oplus Hans 内部决策，不会：

- 写入 `cgroup.freeze`；
- 直接调用内核 freezer 接口；
- 修改 Athena/RUS 配置文件；
- 在实际冻结被阻止后伪造“冻结成功”；
- 允许任意广播携带或注入策略数据。

未被拦截的状态转换、组件代理、冻结与解冻仍由厂商原实现执行。

## 运行时数据流

```text
管理界面
  -> Device Protected SharedPreferences（带版本的 JSON）
  -> exported PolicyProvider（仅模块 UID 与 system UID）
  -> system_server 内专用 HansPolicy HandlerThread
  -> immutable PolicySnapshot
  -> 原 Hans 调用链上的 Hook 决策
```

管理应用与 `system_server` 使用不同 UID，因此需要跨进程 Provider。Provider 读取只允许
模块 UID 和 system UID，运行状态写入进一步限制为 system UID。

刷新广播只通知运行时重新读取 Provider，不携带规则。磁盘与跨进程操作都不在 Hans
状态机线程执行。

冷启动阶段使用一个极窄的 `OplusAppStartupManager.shouldPreventStartProvider` Hook：
只有调用方为 system UID、目标为本模块 Provider 时才放行。这解决了部分 ROM 在用户打开
管理界面前阻止 Direct Boot Provider 拉起的问题。

## 包名与 UID

规则以包名为键。Packet 回调只有 UID 时，按以下顺序解析：

1. 从 Hans 当前 `OplusHansPackage` 读取包名。
2. 回退到 `PackageManager.getPackagesForUid(uid)`。
3. 两种来源都不可用时 fail-open。

观察到的 UID 只缓存在内存。新策略需要立即解除旧限制时，模块会枚举 Android 用户并
重新解析包 UID，然后调用 ROM 自己的
`hansUnFreeze(uid, "force", "HansPolicy")`。

## 27 个 Hook

| 区域 | 目标 | 用途 |
| --- | --- | --- |
| 启动 | `OplusAppStartupManager.shouldPreventStartProvider` | 允许 system UID 拉起模块 Provider。 |
| 运行时 | `OplusHansManager.init` | 获取 context 与 manager。 |
| 运行时 | `OplusHansManager.bootCompleted` | 冷启动初始化后备入口。 |
| 时序 | `OplusHansDBConfig.getRtoMCheckTime` | 按包覆盖 R 到 M。 |
| 时序 | `OplusHansDBConfig.getMtoFCheckTime` | 按包覆盖 M 到 F、Packet 和 Alarm 专用时间。 |
| 豁免 | `OplusHansManager.isHansCoreApp` | 组件策略豁免。 |
| 豁免 | `OplusHansManager.isLcdOnNonRestrictPkg` | 标准状态机豁免。旧固件缺少该方法时回退到 `OplusHansDBConfig.isHansWhitelistApp(int)`。 |
| 冻结守门 | `HansCGroup.hansFreezeLocked` | 按来源阻止冻结且不伪造成功。 |
| 解冻守门 | `HansCGroup.hansUnfreezeLocked` | 按 reason 最终拦截框架与场景解冻。 |
| 内核唤醒 | `OplusHansManager.unfreezeForKernel` | 在 native thaw 前控制 Binder、Signal 和 Packet。 |
| 闹钟 | `OplusHansManager.checkAlarmIfRestricted` | 控制 Alarm 唤醒投递。 |
| 闹钟 | `OplusHansManager.enqueueProxyBroadcastLocked` | 拦截已阻止 Alarm 批次中的后续广播。 |
| 闹钟 | `OplusHansManager.unFreezeForwl(List, String)` | 从 Alarm 关联的 WakeLock 解冻中移除受控 UID。 |
| 闹钟 | `OplusHansManager.unFreezeForwl(int, String)` | 阻止 Alarm 关联的单 UID WakeLock 解冻。 |
| 资源 | `Action.proxyService` | 保留 Service 调度。 |
| 资源 | `Action.proxyBroadcast` | 保留广播投递。 |
| 资源 | `Action.proxyJob` | 保留 Job 调度。 |
| 资源 | `Action.proxySensor` | 保留传感器。 |
| 资源 | `Action.proxyBinder` | 保留异步 Binder。 |
| 资源 | `Action.manageAlarm` | 保留闹钟。 |
| 资源 | `Action.proxyWakeLock` | 保留 WakeLock。 |
| 资源 | `Action.proxyGPS` | 保留 GPS。 |
| 资源 | `Action.proxyAudio` | 保留音频。 |
| 资源 | `Action.proxyBtScan` | 保留蓝牙扫描。 |
| 资源 | `OplusHansManager.tryProxyWakeLock` | 保持 manager 层 WakeLock 决策一致。 |
| 网络 | `OplusHansManager.nwPowerSetFirewall` | 跳过 Hans 防火墙限制。 |
| 网络 | `OplusHansManager.restrictRStateNonKeyProcsNet` | 跳过 R 状态网络限制。 |

## Packet 唤醒状态

`unfreezeForKernel(type=4, ..., targetUid, ...)` 是 Packet 唤醒入口。完全阻止会在厂商
方法执行前返回；限频按当前实际 UID 保存上次允许事件的 elapsed realtime，首次事件
放行，冷却时间内的后续事件拦截。新策略快照加载时会清空限频状态。

Packet 拦截日志对每个 UID 限制为 60 秒一条，避免网络包风暴演变成 `system_server`
日志风暴。

## 解冻来源控制

`unfreezeForKernel` 会先调用
`OplusHansProcessFreeze.unfreezeProcessForKernel`，之后才进入 Hans 状态转换。因此模块在
厂商方法执行前分类内核 type 0-3，分别控制异步 Binder、同步 Binder、事务 Binder 和
Signal；type 4 继续交给支持允许/限频/阻止的 Packet 专用策略。拦截日志包含 caller
PID/UID、AIDL descriptor 与 transaction code。

`HansCGroup.hansUnfreezeLocked(OplusHansPackage, reason, cpnInfo)` 是框架侧放开防火墙和
cgroup thaw 前的最终公共入口。模块将 reason 分为 Activity/Input、Service、Broadcast、
Provider、Job/Sync、WakeLock、音频媒体、连接状态、系统场景、Binder、Signal 与 Other；
无法识别的新字符串会进入 Other，因此未来新增厂商 reason 仍可被控制。模块自己的
`force/HansPolicy` 清理和“完全豁免”规则始终放行。

Activity/Input 与系统场景默认不勾选。启用后可能阻止应用正常前台启动或系统生命周期
恢复。

## Alarm 唤醒状态

`checkAlarmIfRestricted(uid, packageName, action)` 在 AlarmManager 投递前执行。阻止或
限频命中时模块返回 `true`，使事件保持在 Hans 代理路径中，不执行
`unfreezeAndTransState(..., "Alarm", action)`。限频状态按当前 UID 记录，策略重载时清空；
拦截日志同样按 UID 限制为 60 秒一条。

AlarmManager 可能在一个批次中为同一 UID 投递多个 Alarm。在当前固件上，只有批次首项
一定经过 `checkAlarmIfRestricted`，后续项可能继续进入广播链路并触发
`reason=Broadcast`。阻止 Alarm 时，模块会为当前 UID 开启 5 秒闸门；在这段窄窗口内，
`enqueueProxyBroadcastLocked` 中属于同一策略包的广播会被视为已处理，避免同批事件绕过
Alarm 决策。策略重载时也会清空闸门。

同一批事件还可能更新系统 WakeLock：即使 WorkChain 属于其他应用与 JobScheduler，
`WorkSource` 仍可能包含目标 UID。厂商实现通过 `unFreezeForwl` 的两个重载产生
`acquireWakeLock` 与 `updateWLWorkSource` 解冻。5 秒闸门有效时，模块阻止受控单 UID
调用，并只从列表调用中移除受控 UID，不影响列表内其他系统或应用 UID。

## 失败策略

- Hook 安装错误会记录并显示在管理界面。
- JSON 通过 schema 校验后才替换当前 immutable snapshot。
- Provider 读取失败时保留旧 snapshot，并定时重试。
- 包名解析失败时允许厂商原操作。
- 单个决策异常时保留原返回值。
- schema v1-v4 自动迁移到 v5，旧 user ID 被丢弃。
