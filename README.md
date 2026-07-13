# Android Input Bridge

Android Input Bridge 是一个通过 USB ADB 把 Android 手机上的临时文本桥接到 Android Studio 的本地工具。

Android 手机负责输入和保存文本；Android Studio 插件负责通过 ADB 读取文本、在 Tool Window 中展示，并在用户明确操作后写入 Windows 系统剪贴板。用户随后在目标程序中手动执行正常的粘贴操作。

## 当前状态

当前仓库使用一个根 Gradle 项目管理 Android App、共享协议和 Android Studio Plugin 三个模块：

```text
app/
protocol/
android-studio-plugin/
```

`protocol/` 是 Android App 和 Android Studio Plugin 共享的纯 Kotlin/JVM HTTP 协议模块。

具体需求、协议、边界、测试和交付标准见：[docs/requirements.md](docs/requirements.md)。

## 工作方式

```text
Android 输入框
    ↓
Android App 保存当前文本
    ↓
Android App 监听 127.0.0.1:18080
    ↓
adb forward tcp:18080 tcp:18080
    ↓
Android Studio 插件访问 localhost
    ↓
Tool Window 展示文本
    ↓
用户点击 Copy / Copy & Clear
    ↓
Windows 系统剪贴板
    ↓
用户在目标程序中手动 Ctrl+V
```

## 第一阶段范围

Android App：

- 提供支持中文、英文、Emoji、多行文本和代码片段的输入框。
- 保存当前文本、版本号和更新时间。
- 提供本地 HTTP Server、读取、清空和健康检查接口。
- Server 只绑定 `127.0.0.1`。

Android Studio 插件：

- 提供 `Android Input Bridge` Tool Window。
- 定位 ADB、检测设备并建立端口转发。
- 读取 Android App 的 HTTP API 并展示当前文本。
- 支持 Refresh、设备选择和 Reconnect。
- 支持 Copy：将 Tool Window 当前显示的文本写入系统剪贴板，不重新请求 Android App。
- 支持 Copy & Clear：复制成功后按当前版本请求清空；版本冲突时保留新文本并刷新显示。
- 空文本时禁用 Copy 和 Copy & Clear；操作期间禁用所有桥接操作。

## 明确不做的事情

第一阶段不实现：

- 自动向其他应用的输入框输入文本。
- 模拟键盘、`Ctrl+V`、Enter、`SendInput` 或 UI Automation。
- Global Hotkey、全局键盘 Hook 或后台快捷键。
- 读取 Windows 剪贴板、Android Studio 编辑器、项目文件或电脑内容。
- Windows 独立桌面程序、云服务、账户系统、局域网同步或电脑到手机同步。

## 项目结构

```text
.
├── README.md
├── docs/
├── app/
├── protocol/
└── android-studio-plugin/
```

`app/`、`protocol/` 和 `android-studio-plugin/` 属于同一个 Gradle 项目。`protocol/` 只包含 HTTP wire model、序列化配置和协议常量，不依赖 Android、Ktor Server 或 IntelliJ Platform。插件当前已接入 ADB 发现、设备选择、端口转发、HTTP health/text 探测、重连和剪贴板操作。

## 构建入口

所有命令从仓库根目录执行：

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :protocol:test
./gradlew :android-studio-plugin:test
./gradlew :android-studio-plugin:buildPlugin
./gradlew :android-studio-plugin:verifyPlugin
```

如果本机已安装目标 Android Studio，可以使用本地安装验证插件，避免重复下载 IDE：

```bash
./gradlew -PandroidStudioPath="$HOME/Applications/Android Studio.app" :android-studio-plugin:test
./gradlew -PandroidStudioPath="$HOME/Applications/Android Studio.app" :android-studio-plugin:buildPlugin
./gradlew -PandroidStudioPath="$HOME/Applications/Android Studio.app" :android-studio-plugin:verifyPlugin
```

`androidStudioPath` also selects the IDE used by `verifyPlugin`; without it, the verifier uses the current Gradle target instead of an automatically recommended release.

插件 ZIP 预期位于：

```text
android-studio-plugin/build/distributions/
```

Gradle wrapper 位于仓库根目录。插件构建默认使用 Android Studio 2026.1.1 Patch 2 的平台依赖；开发环境也可以通过 `androidStudioPath` 使用本机安装。

## Phase 6 验证和发布

Phase 6 使用固定配置：Android Server 和 ADB forward 都使用 `18080`；HTTP connect timeout 为 `1s`、request timeout 为 `2s`，ADB timeout 为 `5s`。插件不提供 Settings 页面，也不持久化设备选择或连接配置。

CI 在 Pull Request 和推送到 `main` 时运行完整验证矩阵：

```bash
./gradlew :app:lintDebug :app:testDebugUnitTest :protocol:test \
  :android-studio-plugin:test :android-studio-plugin:buildPlugin \
  :android-studio-plugin:verifyPlugin
```

发布通过 `v<major>.<minor>.<patch>` Tag 触发，例如 `v1.2.3`。发布 Workflow 会先运行完整验证，然后创建 GitHub Release 并上传：

```text
android-input-bridge-v1.2.3-debug.apk
android-input-bridge-plugin-v1.2.3.zip
```

Tag 版本同时用于 Android `versionName`、Android `versionCode` 和 Plugin 版本。非 Tag 本地构建使用 `0.0.0-SNAPSHOT`，Android `versionCode` 为 `1`。

## 开发顺序

建议按以下阶段推进：

1. Android 基础输入、版本号和本地持久化。
2. Android 本地 HTTP Server 和 version conflict。
3. 插件 Tool Window 静态 UI。
4. ADB 定位、设备检测、forward、HTTP 探测和重连。
5. 剪贴板和 Copy & Clear 完整链路（已实现）。
6. 超时、错误处理、日志脱敏、稳定性测试、CI 和 Tag 发布。

每个阶段都必须能够独立构建和验证；实现前先输出计划，完成阶段后运行对应测试和构建检查。Phase 5 目标平台为 Android Studio 2026.1.1（IntelliJ Platform build branch 261），插件使用 Java 21。

## MVP 完成标准

MVP 必须满足：

- Android App 可输入并持久化当前文本。
- HTTP Server 只监听 `127.0.0.1`。
- ADB forward 后可以访问健康检查和文本接口。
- 插件可以检测设备、建立 forward 并在 Tool Window 展示文本。
- Copy 可以正确写入 Windows 剪贴板。
- Copy & Clear 只有在复制成功后才清空，并且使用版本号避免误删新文本。
- 中文、Emoji、多行文本和代码片段可以原样传输。
- ADB 和 HTTP 请求不会阻塞 Android Studio UI 线程。
- 日志不包含完整用户文本，且没有 Global Hotkey、SendInput 或电脑到手机的数据通道。
- HTTP 和 ADB 操作使用固定超时；插件不提供设置页面或配置持久化。

## 文档

- [完整需求说明](docs/requirements.md)
- [HTTP API](docs/http-api.md)
- [Git 提交规范](docs/git-commit-convention.md)
