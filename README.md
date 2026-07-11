# Android Input Bridge

Android Input Bridge 是一个通过 USB ADB 把 Android 手机上的临时文本桥接到 Android Studio 的本地工具。

Android 手机负责输入和保存文本；Android Studio 插件负责通过 ADB 读取文本、在 Tool Window 中展示，并在用户明确操作后写入 Windows 系统剪贴板。用户随后在目标程序中手动执行正常的粘贴操作。

## 当前状态

当前仓库处于需求和工程初始化阶段。第一阶段计划包含两个独立项目：

```text
android-app/
android-studio-plugin/
```

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
- 支持 Refresh、Copy、Copy & Clear 和 Reconnect。
- 只向系统剪贴板写入用户明确选择的文本。

## 明确不做的事情

第一阶段不实现：

- 自动向其他应用的输入框输入文本。
- 模拟键盘、`Ctrl+V`、Enter、`SendInput` 或 UI Automation。
- Global Hotkey、全局键盘 Hook 或后台快捷键。
- 读取 Windows 剪贴板、Android Studio 编辑器、项目文件或电脑内容。
- Windows 独立桌面程序、云服务、账户系统、局域网同步或电脑到手机同步。

## 预期目录

```text
.
├── README.md
├── docs/
│   ├── requirements.md
│   └── superpowers/
│       └── plans/
├── android-app/
└── android-studio-plugin/
```

两个子项目应保持独立构建。第一阶段不强制抽取共享模块；如果未来抽取协议模块，也不得让 Android App 和插件共享不兼容的平台依赖。

## 构建入口

子项目创建后，预期使用以下命令：

```bash
./gradlew :app:assembleDebug
```

构建 Android Studio 插件：

```bash
./gradlew buildPlugin
```

插件 ZIP 预期位于：

```text
android-studio-plugin/build/distributions/
```

实际 Gradle wrapper、模块路径和最低兼容的 Android Studio 版本必须在对应子项目 README 中明确记录。

## 开发顺序

建议按以下阶段推进：

1. Android 基础输入、版本号和本地持久化。
2. Android 本地 HTTP Server 和 version conflict。
3. 插件 Tool Window 静态 UI。
4. ADB 定位、设备检测、forward 和重连。
5. HTTP Client、剪贴板和 Copy & Clear 完整链路。
6. 超时、错误处理、日志脱敏、设置持久化和测试。

每个阶段都必须能够独立构建和验证；实现前先输出计划，完成阶段后运行对应测试和构建检查。

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

## 文档

- [完整需求说明](docs/requirements.md)
- [Git 提交规范](docs/git-commit-convention.md)
