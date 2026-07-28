# Oplus Hans Policy

[English](README.md)

一个按应用覆盖 Oplus Hans/OFreezer 策略的 LSPosed 模块。模块运行在
`system_server`，规则只保存包名，在命中时动态解析实际 UID；它不会写内核 freezer
文件，也不会替换厂商状态机。

> [!WARNING]
> 本模块 Hook `system_server` 中的 Oplus 私有 API，并且与 ROM 版本强相关。不兼容的
> OTA 可能导致系统启动异常。请提前保留 LSPosed/Vector 安全模式入口，首次测试时保持
> 管理应用内的策略总开关关闭。

<p align="center">
  <img src="docs/images/main-screen.png" width="30%" alt="运行状态与应用规则">
  <img src="docs/images/rule-dialog.png" width="30%" alt="时序与网络包唤醒控制">
  <img src="docs/images/packet-control.png" width="30%" alt="冻结来源与资源控制">
</p>

## 问题与控制链

在已验证的 Oplus Android 16 固件中，数秒级后台冻结由 `system_server` 内的 Hans
执行：

```text
应用离开前台
  -> HansAppStateMachine: Running（R）
  -> Middle（M）：代理组件、整理内存
  -> Frozen（F）
  -> HansCGroup.hansFreezeLocked()
  -> Process.freezeCgroupUid(uid, true)
  -> /sys/fs/cgroup/apps/uid_<uid>/cgroup.freeze
```

Athena 是厂商策略配置的提供者和更新通道，实时决策与最终 cgroup 动作仍在
Hans/OFreezer。只修改 Athena 静态名单，不能精确覆盖所有运行时入口。

网络包是另一条重要解冻路径。QQ 的 MSF/iLink 等持久连接收到数据后，Hans native
monitor 会进入 `OplusHansManager.unfreezeForKernel(type=4)`，并以 `reason=Packet`
解冻整个 UID。本模块可以允许、限频或阻止这个事件。

## 功能

- 规则只以包名为键，运行时动态解析 UID，不保存 UID 或 Android 用户 ID。
- 完全豁免 Hans，并在最终 cgroup 冻结入口守门。
- 分别覆盖 R 到 M、M 到 F 的状态时序。
- 分别阻止普通状态机、Fast Freezer、Super Freeze、预加载四类冻结来源。
- 即使允许 cgroup 冻结，也可选择保留网络、Service、Job、广播、闹钟、Binder、
  传感器、GPS、WakeLock、音频和蓝牙扫描行为。
- 网络包唤醒可选择跟随系统、限制最短唤醒间隔或完全阻止。
- 可单独设置 Packet 解冻后的再次冻结延时。
- Direct Boot 策略存储，保存后动态重载，不需要重启 `system_server`。
- 管理界面上报 boot ID、策略 revision、初始化来源、Hook 数量和错误。
- 配置解析、包名解析或单个 Hook 失败时 fail-open，保留厂商原行为。

全新安装时策略总开关默认关闭；只有打开总开关后规则才会介入。

## 兼容性

| 项目 | 已验证值 |
| --- | --- |
| 设备 | OnePlus PKX110 |
| 系统 | Android 16 / API 36 |
| 固件 | `PKX110_16.0.9.401(CN01)` |
| 逆向样本 | 上述固件的 Oplus `oplus-services.jar` |
| 模块版本 | `0.3.1`（`versionCode 5`） |
| Hook 进程 | `android` / `system_server` |
| 框架 | LSPosed API 82 兼容；同时在 Vector Framework 2.0 验证 |

当前没有宣称适配其他 ColorOS/OxygenOS 版本。每次 OTA 后都应先核对核心方法签名，
确认 22 个 Hook 全部安装成功，再重新打开策略总开关。

## 安装

1. 从 [最新 Release](https://github.com/whitewhale0612/Oplus-Hans-Policy/releases/latest)
   下载 `HansPolicy-v0.3.1.apk`。
   安装前核对 SHA-256：

   ```text
   d4c10d2fa49f5a2d4a315d317c13f989e9b071b0e32469993ec31342f56ca423
   ```

2. 安装或覆盖更新：

   ```bash
   adb install -r HansPolicy-v0.3.1.apk
   ```

3. 在 LSPosed/Vector 中启用 **Hans Policy**。
4. 作用域只选择 **系统框架**（`android` / `system`），不需要选择目标应用。
5. 重启设备。
6. 打开管理应用，确认显示“Hook 已连接 · 22 个目标”，并且 system/local revision
   一致。
7. 先添加一条测试规则，最后再打开管理应用内的策略总开关。

应用重装、后台进程重建或 UID 变化后不需要修改规则。模块会优先从 Hans 内部对象取
包名，失败时再通过 PackageManager 按当前 UID 解析。

## 网络包唤醒

| 模式 | 行为 |
| --- | --- |
| 跟随系统 | 每次 Packet 事件都交给 Hans 原逻辑处理。 |
| 限制唤醒频率 | 首个 Packet 允许解冻，冷却时间内的后续事件被拦截。 |
| 完全阻止唤醒 | 匹配 UID 的所有 `type=4` Packet 解冻调用都被拦截。 |

完全阻止可能延迟消息、VoIP 信令、长连接推进和其他后台网络工作。该功能不是防火墙，
不会直接丢弃数据包；它阻止的是 Hans 因该网络包解冻 UID。通信应用必须实际测试。

“自定义网络唤醒后保持时间”只覆盖 `reason=Packet` 的 M 到 F 时序。如果应用同时获得
音频焦点、前台服务或其他系统场景，Hans 状态机仍可能拒绝当次转 F；模块不会伪造冻结
成功状态。

## 验证

查看模块与 Hans 日志：

```bash
adb logcat -v time | grep -E 'HansPolicy|OplusHansManager'
```

典型日志：

```text
HansPolicy: installed 22 hooks
HansPolicy: Packet wake blocked uid=<uid> pkg=<package>
HansPolicy: Packet wake throttled uid=<uid> pkg=<package>
OplusHansManager: unfreeze uid: <uid> ... reason: Packet
OplusHansManager: freeze uid: <uid> ...
```

冻结后的内核状态：

```bash
adb shell su -c 'cat /sys/fs/cgroup/apps/uid_<uid>/cgroup.events'
```

预期包含 `frozen 1`。v0.3.1 的设备测试证据见
[真机验证记录](docs/VERIFICATION_CN.md)。

## 构建

要求：

- JDK 17 或更高版本
- Android SDK `platforms;android-35`
- Android SDK `build-tools;35.0.0`
- 设置 `ANDROID_HOME` / `ANDROID_SDK_ROOT`，或创建本地 `local.properties`

Linux/macOS：

```bash
chmod +x scripts/build.sh
./scripts/build.sh
```

Windows：

```bat
scripts\build.bat
```

默认会执行 lint，并生成 `dist/HansPolicy-v0.3.1-debug.apk`。Xposed API 82 JAR 使用
`compileOnly`，不会打入 APK。

也可以直接执行：

```bash
./gradlew lintDebug assembleDebug
```

## 仓库结构

```text
app/                 Android 管理应用与 LSPosed Hook 源码
docs/                架构、验证记录与界面截图
scripts/             Linux/macOS 与 Windows 构建脚本
.github/workflows/   lint 与可复现 debug 构建
dist/                本地构建输出（gitignored）
artifacts/           本地签名与发布产物（gitignored）
```

## 恢复

正常情况下，在管理应用内关闭总开关即可立即恢复系统原策略。如果设备无法稳定进入
桌面，请从框架安全模式禁用模块后重启。Vector Framework 用户可以执行：

```bash
adb shell /data/adb/modules/zygisk_vector/cli modules disable io.github.whitewhale.hanspolicy
adb reboot
```

模块没有修改 RUS 数据、系统属性或 cgroup 文件，禁用后没有额外厂商配置需要恢复。

## 限制

- Oplus 私有方法签名可能随固件变化。
- 完全豁免和资源保留会明显改变后台功耗。
- 阻止 Packet 唤醒可能延迟用户可见的通信事件。
- Android shared UID 下的多个包仍会作为同一个 UID 被控制。
- 已经进入 Handler 队列的旧状态消息不会被重排；新时序从下一次状态转换生效。

完整设计与 22 个 Hook 的映射见[架构文档](docs/ARCHITECTURE_CN.md)。
