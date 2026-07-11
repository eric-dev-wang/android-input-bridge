# Android Input Bridge 需求说明

## 1. 产品定义

Android Input Bridge 是一个通过 USB ADB 读取 Android 手机上的临时文本，并在 Android Studio Tool Window 中展示和复制的本地文本桥接工具。

Android 是文本输入端；Android Studio 插件是文本查看和复制端。用户最终通过正常的系统复制粘贴操作，把文本手动粘贴到目标位置。

第一阶段不自动向其他应用输入文本，不模拟键盘，也不操作电脑当前焦点窗口。

## 2. 项目目标

### 2.1 核心链路

```text
Android 手机输入文本
        ↓
Android App 保存文本
        ↓
Android App 本地 HTTP Server
        ↓
ADB forward 端口映射
        ↓
Android Studio 插件访问 localhost
        ↓
Tool Window 展示文本
        ↓
用户点击 Copy
        ↓
文本进入 Windows 系统剪贴板
        ↓
用户手动在目标程序中按 Ctrl+V
```

### 2.2 用户目标

用户应能够：

1. 在 Android 手机上打开 App。
2. 使用任意输入法在 App 输入框中输入文本。
3. 在 Windows 上打开 Android Studio。
4. 在插件 Tool Window 中看到手机上的文本。
5. 点击 Copy，将文本复制到电脑剪贴板。
6. 点击 Copy & Clear，在复制成功后清空手机文本。
7. 在其他程序中手动粘贴文本。

### 2.3 技术目标

- 不依赖 Wi-Fi 或局域网。
- 手机与电脑之间只通过 USB ADB 通信。
- Android HTTP Server 不暴露到手机局域网。
- 支持中文、英文、Emoji、多行文本和代码片段。
- 不使用 Windows 全局键盘 Hook、`SendInput` 或 Global Hotkey。
- 不读取 Windows 当前输入框、当前窗口内容或系统剪贴板内容。
- 只向 Windows 剪贴板写入用户明确选择的文本。

## 3. 第一阶段模块边界

仓库第一阶段包含两个独立模块：

```text
android-app/
android-studio-plugin/
```

### 3.1 `android-app/`

Android App 负责：

- 提供文本输入界面。
- 保存当前文本。
- 启动本地 HTTP Server。
- 提供读取、清空和健康检查接口。
- 显示服务状态。
- 在 App 前台运行期间稳定提供服务。

### 3.2 `android-studio-plugin/`

插件负责：

- 提供 Tool Window。
- 检测可用 ADB。
- 检测已连接 Android 设备。
- 建立 ADB 端口转发。
- 请求 Android App HTTP Server。
- 展示手机端当前文本。
- 将文本写入系统剪贴板。
- 请求 Android App 清空文本。
- 展示设备、ADB、forward、服务连接和错误状态。

## 4. 非目标和硬边界

### 4.1 不实现自动输入

第一阶段不得实现：

- 自动向当前焦点输入框输入文字。
- 模拟 `Ctrl+V`、键盘事件或 Enter。
- 调用 Windows `SendInput`。
- 调用 Windows UI Automation。
- 定位或修改其他程序的输入框。
- 自动发送消息。

### 4.2 不实现全局快捷键

第一阶段不得实现：

- `RegisterHotKey`。
- 全局键盘 Hook。
- 监听所有系统按键。
- 后台快捷键触发复制。

这些能力未来可以作为独立可选功能评估，但不能进入第一阶段。

### 4.3 数据方向固定为 Android → Windows

第一阶段只允许：

```text
Android → Windows
```

插件不得：

- 读取 Windows 剪贴板并发送给手机。
- 读取 Android Studio 编辑器内容并发送给手机。
- 读取项目源代码并发送给手机。
- 读取电脑文件并发送给手机。

### 4.4 不使用云服务

不使用云数据库、第三方 API、外部服务器、互联网同步、用户账户或登录系统。

### 4.5 不强制复杂后台保活

第一阶段可以要求 Android App 保持前台运行。不强制实现 Foreground Service、开机启动、后台长期保活、Doze 绕过或电池优化白名单引导；只有在后续确认需要时再单独评估。

## 5. 总体架构和端口

### 5.1 地址和数据流

Android App 在手机上监听：

```text
127.0.0.1:18080
```

Windows 电脑执行：

```bash
adb forward tcp:18080 tcp:18080
```

插件访问：

```text
http://127.0.0.1:18080
```

请求通过 ADB 转发到 Android 手机上的本地 HTTP Server。

### 5.2 端口配置

默认手机端和电脑端都使用 `18080`。端口必须集中配置，不得在多个文件中硬编码。

建议配置项：

```text
androidServerHost = 127.0.0.1
androidServerPort = 18080
desktopForwardPort = 18080
```

第一阶段可以固定电脑端端口；如果后续开放修改，必须同时更新 forward 和 HTTP Client 配置。

### 5.3 协议选择

第一阶段使用 HTTP，不使用 WebSocket。原因是插件主要执行主动刷新或低频轮询，HTTP 更容易调试，不需要连接保活、WebSocket 重连或复杂双向状态。

## 6. Android App 技术和 UI 需求

### 6.1 推荐技术栈

- Kotlin。
- Jetpack Compose。
- Kotlin Coroutines。
- 轻量级嵌入式 HTTP Server。

HTTP Server 可选 Ktor Server CIO、NanoHTTPD 或其他体积较小且许可证允许商用的库。选择优先级为：实现简单、稳定、支持协程或后台线程、可以绑定 `127.0.0.1`、依赖数量少。

### 6.2 主界面

主界面至少包含：

```text
Android Input Bridge

Server: Running / Stopped
Address: 127.0.0.1:18080

┌──────────────────────────────┐
│ 多行文本输入框               │
│                              │
│                              │
└──────────────────────────────┘

Characters: 123
Version: 17

[Clear]
```

可以增加 Android 端 Copy、Start Server、Stop Server 和服务错误信息，但第一阶段核心只要求输入框、Clear 和服务状态。

### 6.3 输入框

输入框必须：

- 支持多行文本、中文、英文、Emoji、代码文本、换行和制表符。
- 不自动修改内容、不自动发送、不限制输入法。
- 建议最大长度为 `100,000` 个字符。
- 超过限制时阻止继续输入或显示明确提示，不得静默截断。

### 6.4 文本状态模型

```kotlin
data class TextState(
    val text: String,
    val version: Long,
    val updatedAt: Long
)
```

字段含义：

- `text`：当前输入内容。
- `version`：内容版本号。
- `updatedAt`：最后更新时间，Unix epoch milliseconds。

每当文本发生实际变化时，`version = version + 1`，包括用户输入、删除、用户清空和 API 成功清空。如果新文本与旧文本完全相同，则不增加版本。

### 6.5 持久化

建议使用 DataStore Preferences 或 App 私有目录中的本地文件。要求：

- 普通进程回收后，重新打开 App 可以恢复上一次未清空文本。
- 存储位于 App 私有目录。
- 不写入公共 Download 目录。
- 不申请额外存储权限。
- 只保存当前状态，不保留文本历史。

### 6.6 HTTP Server 生命周期

- App 启动后自动启动 HTTP Server。
- App 前台运行期间 Server 保持运行。
- Server 启动失败时界面显示错误。
- App 销毁时停止 Server。
- 重复启动不得造成端口重复绑定。
- Server 内部错误不得导致整个 App 崩溃。

### 6.7 绑定地址

Server 必须只监听：

```text
127.0.0.1
```

不得监听 `0.0.0.0`。启动日志应明确打印：

```text
Listening on 127.0.0.1:18080
```

## 7. HTTP API 协议

所有请求和响应使用：

```text
Content-Type: application/json; charset=utf-8
```

统一成功响应：

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

统一失败响应：

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable message"
  }
}
```

本节中的 `/api/v1/...` 是唯一规范路径。

### 7.1 健康检查

请求：

```http
GET /api/v1/health
```

响应：

```json
{
  "success": true,
  "data": {
    "status": "ok",
    "appVersion": "1.0.0",
    "protocolVersion": 1,
    "serverTime": 1783780000000
  },
  "error": null
}
```

用途：验证 ADB forward、Android App Server 和协议版本兼容性。

### 7.2 获取当前文本

请求：

```http
GET /api/v1/text
```

响应：

```json
{
  "success": true,
  "data": {
    "text": "当前输入内容",
    "version": 17,
    "updatedAt": 1783780000000
  },
  "error": null
}
```

要求：原样返回文本，保留换行、制表符和 Emoji；不自动 trim、不自动格式化，不把文本先做 HTML 转义；JSON 序列化必须正确处理特殊字符和 UTF-8。

### 7.3 按版本清空当前文本

请求：

```http
POST /api/v1/text/clear
Content-Type: application/json
```

请求 Body：

```json
{
  "expectedVersion": 17
}
```

成功响应：

```json
{
  "success": true,
  "data": {
    "clearedVersion": 17,
    "newVersion": 18
  },
  "error": null
}
```

当 `expectedVersion != currentVersion` 时返回 HTTP `409 Conflict`：

```json
{
  "success": false,
  "data": {
    "currentVersion": 18
  },
  "error": {
    "code": "VERSION_CONFLICT",
    "message": "Text changed after the client loaded it."
  }
}
```

只有 `expectedVersion == currentVersion` 时才允许清空。这样可以避免插件读取版本 17 后，用户继续输入形成版本 18，插件却误清空版本 18。

### 7.4 可选服务信息接口

可以提供：

```http
GET /api/v1/info
```

可返回 App 版本、协议版本、Android 版本、设备型号、当前端口和当前文本长度。第一阶段非必需。

## 8. Android App 运行边界

Android App 必须满足：

- Server 只绑定 `127.0.0.1`。
- 如果 HTTP Server 库不需要 Internet 权限，则不得添加；只有技术上确实需要时才允许添加。
- 不读取系统剪贴板。
- 只保存当前状态，不保留文本历史。
- 日志中不得输出完整文本内容。

允许：

```text
Text updated, version=17, length=123
```

## 9. Android Studio 插件技术和 UI 需求

### 9.1 技术栈

- Kotlin。
- IntelliJ Platform Plugin SDK。
- Gradle IntelliJ Platform Plugin。
- Swing UI。
- IntelliJ Platform Services。
- IntelliJ Clipboard API 或标准 Java Clipboard API。

目标 IDE 为 Android Studio，可兼容 IntelliJ IDEA，但第一阶段以 Android Studio 为主要目标。插件工程必须在实现时明确写出最低兼容的 Android Studio 版本。

### 9.2 Tool Window

插件提供：

```text
View → Tool Windows → Android Input Bridge
```

Tool Window 推荐默认停靠在右侧，也可以停靠在底部。

### 9.3 Tool Window UI

至少包含：

```text
Android Input Bridge

Device: emulator-5554 / physical-device-serial
ADB: Connected
Forward: localhost:18080 → device:18080
Server: Online
Last refresh: 20:31:42

┌────────────────────────────────────┐
│ 手机上的当前文本                  │
│                                    │
│                                    │
└────────────────────────────────────┘

Length: 123
Version: 17

[Refresh] [Copy] [Copy & Clear]
[Reconnect]
```

### 9.4 文本区域

- 默认只读。
- 支持选择部分文本和用户手动复制选中内容。
- 支持自动换行、滚动、中文和 Emoji。
- 不允许用户在插件端修改 Android 状态。
- 不双向同步。

### 9.5 状态显示

至少显示 ADB 是否可用、设备 serial、设备状态、ADB forward 状态、Android Server 是否在线、当前文本版本、最后刷新时间和错误信息。

建议状态：

```text
Disconnected
ADB unavailable
No device
Multiple devices
Unauthorized
Forwarding
Server offline
Connected
Error
```

### 9.6 按钮行为

#### Refresh

执行：

1. 检查 ADB。
2. 确认设备。
3. 确认或重建端口转发。
4. 请求 `GET /api/v1/text`。
5. 更新 Tool Window。

不得阻塞 IDE UI 线程。

#### Copy

1. 使用 Tool Window 当前已显示的文本，不重新请求服务。
2. 文本为空时提示 `Nothing to copy`。
3. 写入系统剪贴板。
4. 成功后显示短暂提示。
5. 不请求 Android 清空。

不重新请求的原因是避免用户看到的文本与复制的文本不一致。

#### Copy & Clear

1. 获取当前 Tool Window 中的 `text` 和 `version` 快照。
2. 将快照写入系统剪贴板。
3. 确认写入成功。
4. 请求 `POST /api/v1/text/clear`。
5. 携带 `expectedVersion`。
6. 清空成功后更新 UI。

顺序必须是：

```text
Copy 成功 → Clear
```

不得先 Clear 后 Copy。执行期间应暂时禁用 Copy & Clear。

#### Version Conflict

服务器返回 `409 Conflict` 时：

- 不清空 Android 文本。
- 保留已经复制到剪贴板的旧版本文本。
- 显示：`Text was copied, but the phone content changed and was not cleared.`
- 自动重新刷新当前文本。

#### Reconnect

1. 重新检测 ADB。
2. 重新检测设备。
3. 可选删除旧 forward。
4. 重新建立 forward。
5. 调用 health。
6. 刷新文本。

## 10. ADB 管理

### 10.1 ADB 查找顺序

优先尝试 Android Studio 当前 SDK 配置中的 ADB，然后依次查找：

1. Android Studio / IntelliJ Android SDK 配置。
2. `ANDROID_SDK_ROOT`。
3. `ANDROID_HOME`。
4. 系统 `PATH` 中的 `adb`。

不得假定 `adb.exe` 一定在 PATH。

### 10.2 命令

设备列表：

```bash
adb devices
```

建立端口映射：

```bash
adb -s <serial> forward tcp:18080 tcp:18080
```

检查映射：

```bash
adb -s <serial> forward --list
```

删除映射：

```bash
adb -s <serial> forward --remove tcp:18080
```

### 10.3 设备选择

- 无设备：显示 `No Android device connected.`。
- 一个设备：自动选择。
- 多个设备：提供下拉框选择，不能静默随机选择；保存最后一次选择的 serial。
- `unauthorized`：显示 `Device authorization required. Check the phone and accept the ADB prompt.`。
- `offline`：显示 `Device is offline.`。

### 10.4 命令执行

ADB 命令必须：

- 在后台线程执行。
- 设置超时，建议 5 秒。
- 捕获 stdout、stderr 和退出码。
- 不阻塞 EDT。
- 不把完整用户文本放进命令行参数。

### 10.5 自动恢复

以下情况应尝试自动重建 forward：HTTP 请求连接失败、插件首次打开、设备重新连接和用户点击 Reconnect。自动重试建议最多 1 次，避免无限循环。

## 11. HTTP Client

### 11.1 超时和重试

建议：

```text
Connect timeout: 1 second
Request timeout: 2 seconds
```

请求失败时最多执行：

```text
第一次请求失败
    ↓
重新执行一次 adb forward
    ↓
再次请求
```

不得无限重试。

### 11.2 线程模型

- HTTP 请求必须在后台线程执行。
- UI 更新必须切回 IntelliJ EDT。
- 禁止在 EDT 执行阻塞 HTTP、ADB 子进程或 `sleep`。

### 11.3 JSON 模型

可以使用 Kotlin serialization 或 Gson。插件与 Android App 的 JSON Model 分开定义；未来可以抽取共享 protocol module，但第一阶段不强制。

## 12. 剪贴板

插件只允许写入剪贴板，不允许读取、保存历史、恢复旧内容或监控变化。

复制必须支持 Unicode、多行文本、Emoji 和长文本。写入失败时：

- 不得清空 Android 文本。
- 显示明确错误。

可以使用 IntelliJ Platform 推荐的剪贴板机制；若使用 AWT，可调用：

```java
Toolkit.getDefaultToolkit().getSystemClipboard()
```

但必须在合适线程调用并处理异常。

## 13. 刷新策略和设置

第一阶段默认手动 Refresh；Tool Window 打开时可以自动刷新一次。

如果实现自动刷新：

- 默认关闭。
- 间隔不得低于 500 ms，推荐 1～2 秒。
- Tool Window 不可见或 IDE 进入后台时可以暂停。
- 不得每次刷新都执行 `adb forward`。
- 文本 version 未变化时不更新 UI。

插件设置位置：

```text
Settings → Tools → Android Input Bridge
```

建议支持：ADB executable path、Device serial、Desktop forward port、Android server port、Request timeout、打开 Tool Window 时自动连接、打开 Tool Window 时自动刷新。

默认值：

```text
Desktop port: 18080
Android port: 18080
Auto connect: true
Auto refresh on open: true
Continuous polling: false
```

## 14. 错误处理

### 14.1 Android App

至少覆盖：Server 端口绑定失败、Server 意外停止、持久化失败、JSON 序列化失败、Clear version conflict 和输入文本超长。

### 14.2 插件

至少覆盖：ADB 未找到、ADB 执行失败、无设备、多设备未选择、设备 unauthorized、设备 offline、forward 失败、端口占用、Server offline、HTTP 超时、HTTP 非 2xx、JSON 格式错误、协议版本不兼容、剪贴板写入失败、Project 已关闭和 Tool Window 已销毁。

### 14.3 提示方式

不得使用频繁弹窗打断用户。优先使用 Tool Window 内状态文字、IntelliJ Notification 或状态栏提示；只有严重且需要立即处理的问题才使用 Dialog。

## 15. 状态机、并发和生命周期

### 15.1 插件状态

建议使用明确状态：

```kotlin
sealed interface BridgeState {
    data object Idle : BridgeState
    data object LocatingAdb : BridgeState
    data object NoAdb : BridgeState
    data object DetectingDevices : BridgeState
    data object NoDevice : BridgeState
    data object Unauthorized : BridgeState
    data object Forwarding : BridgeState
    data object ServerOffline : BridgeState
    data object Connected : BridgeState
    data class Error(val message: String) : BridgeState
}
```

UI 根据状态决定状态文本、按钮可用性以及是否允许 Copy、Clear 和 Reconnect。

### 15.2 Android 统一状态源

输入框状态与 HTTP API 必须访问同一份状态源，由统一 Repository 管理。不得让 UI 和 Server 各持有一份异步同步的文本状态。

### 15.3 插件重复请求

Refresh 正在执行时应禁用 Refresh 或合并重复请求，不得同时启动多个刷新任务。

### 15.4 Copy & Clear 快照

Copy & Clear 使用触发时的 `text` 和 `version` 快照，不得使用请求完成后可能已经变化的 UI 数据。Copy & Clear 过程中暂时禁用同一按钮。

### 15.5 Tool Window 销毁

后台任务返回后确认 Project 未 disposed 且 Panel 仍有效，避免 IDE 关闭时更新已销毁 UI。

## 16. 日志要求

插件使用 IntelliJ Logger；Android 使用标准 Android Log。

允许记录：ADB executable path、设备 serial、端口、请求路径、HTTP status、文本长度、version 和错误堆栈。

禁止记录：完整文本、文本前几百字符、剪贴板内容、Android 输入内容和 Authorization token。

允许示例：

```text
Fetched text successfully: version=17, length=123
```

禁止示例：

```text
Fetched text: 这是用户输入的完整内容
```

## 17. 推荐项目结构

### 17.1 Android App

```text
android-app/
├── app/
│   ├── src/main/java/.../
│   │   ├── MainActivity.kt
│   │   ├── ui/
│   │   │   ├── MainScreen.kt
│   │   │   └── MainViewModel.kt
│   │   ├── model/
│   │   │   └── TextState.kt
│   │   ├── repository/
│   │   │   └── TextRepository.kt
│   │   ├── server/
│   │   │   ├── InputHttpServer.kt
│   │   │   ├── ApiModels.kt
│   │   │   └── ServerController.kt
│   │   └── storage/
│   │       └── TextStateStorage.kt
│   └── src/test/
└── build.gradle.kts
```

### 17.2 Android Studio 插件

```text
android-studio-plugin/
├── src/main/kotlin/.../
│   ├── toolwindow/
│   │   ├── InputBridgeToolWindowFactory.kt
│   │   ├── InputBridgePanel.kt
│   │   └── InputBridgeViewModel.kt
│   ├── adb/
│   │   ├── AdbLocator.kt
│   │   ├── AdbClient.kt
│   │   ├── AdbDevice.kt
│   │   └── PortForwardManager.kt
│   ├── http/
│   │   ├── AndroidBridgeClient.kt
│   │   └── ApiModels.kt
│   ├── clipboard/
│   │   └── ClipboardWriter.kt
│   ├── settings/
│   │   ├── InputBridgeSettings.kt
│   │   └── InputBridgeConfigurable.kt
│   ├── service/
│   │   └── InputBridgeProjectService.kt
│   └── notifications/
│       └── InputBridgeNotifier.kt
├── src/main/resources/
│   └── META-INF/plugin.xml
└── build.gradle.kts
```

## 18. 测试要求

### 18.1 Android 单元测试

至少测试：

- 文本变化时 version 增加。
- 相同文本不增加 version。
- 正确 version 清空成功。
- 旧 version 清空返回冲突。
- 持久化恢复。
- 空文本行为。
- Emoji、多行文本和超长文本边界。

### 18.2 Android API 测试

覆盖：

```text
GET /api/v1/health
GET /api/v1/text
POST /api/v1/text/clear
```

验证 HTTP status、JSON 格式、UTF-8、version conflict 和错误响应。

### 18.3 插件单元测试

至少测试：

- `adb devices` 输出解析。
- 无设备、一个设备、多设备。
- `unauthorized` 和 `offline`。
- ADB path 解析。
- API JSON 解析。
- Version conflict 处理。
- Copy 成功后才 Clear 的调用顺序。
- 请求失败后的单次重连。

### 18.4 手工集成测试

必须验证：

1. Android 手机连接电脑并授权 ADB。
2. App 打开并显示 Server Running。
3. 插件成功建立 forward。
4. 插件 health 检查成功。
5. 纯中文、英文、中英文混合、Emoji、多行文本和代码片段。
6. Copy 后在 Windows 记事本中手动粘贴。
7. Copy 后手机内容保持不变。
8. Copy & Clear 后复制成功且手机清空。
9. 使用旧 version 清空时不会误删新文本。
10. USB 断开后显示离线状态。
11. USB 重连后 Reconnect 成功。
12. App 关闭后显示 Server Offline，重新打开后恢复。
13. 多台设备连接时要求选择设备。
14. Android Studio 重启后设置可恢复。

## 19. 交付标准

### 19.1 Android App

必须包含完整 Android Studio 工程、可构建 Debug APK、README、构建命令、运行说明、HTTP API 文档、已知限制和单元测试。

构建命令：

```bash
./gradlew :app:assembleDebug
```

### 19.2 插件

必须包含完整 IntelliJ Platform 插件工程、可构建插件 ZIP、README、安装步骤、ADB 配置说明、Tool Window 使用说明、已知限制和单元测试。

构建命令应类似：

```bash
./gradlew buildPlugin
```

输出插件包应位于：

```text
build/distributions/
```

## 20. MVP 验收标准

以下条件全部满足时，第一阶段 MVP 才算完成：

- Android App 可输入并保存文本。
- HTTP Server 只监听 `127.0.0.1`。
- ADB forward 后电脑能访问 `/api/v1/health` 和文本接口。
- 插件能检测至少一台 Android 设备。
- 插件能自动建立端口映射。
- Tool Window 能显示 Android 文本。
- Copy 可正确写入 Windows 剪贴板。
- Copy & Clear 仅在复制成功后清空。
- Version conflict 不会误删新文本。
- Android Studio UI 不因网络或 ADB 请求卡顿。
- 中文、Emoji 和多行文本可以完整传输。
- 日志不记录完整文本。
- 不使用 Global Hotkey、SendInput、编辑器内容读取或 Windows 剪贴板读取。
- 不向手机发送电脑端内容。

## 21. 开发阶段

### Phase 1：Android 基础输入

实现 Compose 输入框、TextState、version、本地持久化和 Clear 按钮。

完成标准：重启 App 后文本仍存在，文本修改时 version 正确变化。

### Phase 2：Android HTTP Server

实现 `/api/v1/health`、`/api/v1/text`、`/api/v1/text/clear`、`127.0.0.1` 绑定和 version conflict。

完成标准：通过 `adb forward tcp:18080 tcp:18080` 后，电脑可以调用全部必需接口。

### Phase 3：插件 Tool Window 静态 UI

实现 Tool Window、状态栏、文本区域、Refresh、Copy、Copy & Clear 和 Reconnect；初期可以使用 Mock 数据。

### Phase 4：ADB 集成

实现 ADB 定位、设备检测、设备选择、forward 和重连。

完成标准：插件可以自动连接真实手机服务。

### Phase 5：HTTP 与剪贴板

实现 health、获取文本、Copy、Copy & Clear 和 version conflict。

完成标准：完整链路可用。

### Phase 6：稳定性

实现后台线程、超时、错误提示、日志内容控制、设置持久化和单元测试。

## 22. AI 实现约束

AI 实现时必须：

1. 先输出实现计划，再开始写代码。
2. 分阶段实现，每个阶段必须可构建。
3. 每完成一个阶段都运行测试。
4. 不一次性生成未经验证的大量代码。
5. 不添加需求之外的框架。
6. 不使用反射解决普通问题。
7. 不使用实验性 API，除非明确说明原因。
8. 不实现全局快捷键、模拟输入或自动粘贴。
9. 不读取 Android Studio 编辑器内容或系统剪贴板。
10. 不记录完整文本。
11. 所有网络请求和 ADB 调用必须设置超时。
12. 所有阻塞操作不得运行在 IDE EDT。
13. 提交前运行完整构建和测试。
14. 对未验证的 IntelliJ API 不得凭空假设。
15. 如果 API 与目标 Android Studio 版本不兼容，先查官方 IntelliJ Platform 文档并调整。

## 23. 后续可选功能

以下功能不属于第一阶段，必须在 MVP 稳定后单独评估：

- Tool Window 自动轮询。
- WebSocket 实时同步。
- Android Foreground Service。
- Android 通知栏快捷清空。
- 文本历史、多条草稿和 Markdown Preview。
- 仅作用于 Android Studio 内部的插件快捷键。
- Global Hotkey。
- 自动向焦点输入框粘贴。
- Windows 独立桌面 Client。
- macOS Client、Linux Client。
- 端到端加密、请求 token。
- 多设备同时管理。
- 二维码配对。
- Wi-Fi 模式。

以下能力必须继续保持默认关闭：Global Hotkey、自动粘贴、模拟 Enter 和电脑内容发送到手机。
