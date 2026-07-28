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

## 22 个 Hook

| 区域 | 目标 | 用途 |
| --- | --- | --- |
| 启动 | `OplusAppStartupManager.shouldPreventStartProvider` | 允许 system UID 拉起模块 Provider。 |
| 运行时 | `OplusHansManager.init` | 获取 context 与 manager。 |
| 运行时 | `OplusHansManager.bootCompleted` | 冷启动初始化后备入口。 |
| 时序 | `OplusHansDBConfig.getRtoMCheckTime` | 按包覆盖 R 到 M。 |
| 时序 | `OplusHansDBConfig.getMtoFCheckTime` | 按包覆盖 M 到 F 和 Packet 专用时间。 |
| 豁免 | `OplusHansManager.isHansCoreApp` | 组件策略豁免。 |
| 豁免 | `OplusHansManager.isLcdOnNonRestrictPkg` | 标准状态机豁免。 |
| 冻结守门 | `HansCGroup.hansFreezeLocked` | 按来源阻止冻结且不伪造成功。 |
| 网络包 | `OplusHansManager.unfreezeForKernel` | 控制 `type=4` Packet 唤醒。 |
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

## 失败策略

- Hook 安装错误会记录并显示在管理界面。
- JSON 通过 schema 校验后才替换当前 immutable snapshot。
- Provider 读取失败时保留旧 snapshot，并定时重试。
- 包名解析失败时允许厂商原操作。
- 单个决策异常时保留原返回值。
- schema v1/v2 自动迁移到 v3，旧 user ID 被丢弃。
