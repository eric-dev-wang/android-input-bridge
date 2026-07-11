# Repository Guidelines

## Project Structure & Module Organization

This repository will contain two independent Gradle projects:

- `android-app/`: Kotlin Android application, with production code under `app/src/main`, unit tests under `app/src/test`, and Android resources under `app/src/main/res`.
- `android-studio-plugin/`: Kotlin IntelliJ Platform plugin, with code under `src/main/kotlin`, tests under `src/test/kotlin`, and plugin metadata under `src/main/resources/META-INF`.
- `docs/`: requirements, commit conventions, and implementation notes.

Keep the Android App and plugin independently buildable. Use `docs/requirements.md` as the functional source of truth and avoid adding shared modules until a clear protocol-sharing need exists.

## Build, Test, and Development Commands

Run commands from the relevant project directory after its Gradle wrapper exists:

```bash
cd android-app && ./gradlew :app:assembleDebug
cd android-app && ./gradlew :app:testDebugUnitTest
cd android-studio-plugin && ./gradlew buildPlugin
cd android-studio-plugin && ./gradlew test
```

Use `adb forward tcp:18080 tcp:18080` for manual end-to-end checks. HTTP and ADB operations must run off the IntelliJ EDT and use bounded timeouts.

## Coding Style & Naming Conventions

Use Kotlin official style with four-space indentation. Use `PascalCase` for classes and composables, `camelCase` for functions and properties, and `UPPER_SNAKE_CASE` only for constants. Keep files focused by responsibility and prefer explicit interfaces between storage, server, ADB, HTTP, clipboard, and UI layers. Follow Compose conventions in the Android App and IntelliJ/Swing threading rules in the plugin.

## Testing Guidelines

Test version changes, persistence, UTF-8/multiline text, API responses, ADB parsing, version conflicts, clipboard failure handling, and Copy-before-Clear ordering. Name tests after observable behavior, such as `clearWithStaleVersionReturnsConflict`. Add or update tests with every behavior change; no global coverage threshold is defined yet.

## Commit & Pull Request Guidelines

Use the repository convention in [`docs/git-commit-convention.md`](docs/git-commit-convention.md): English by default and messages such as `feat(app): persist the current text state` or `fix(plugin): prevent duplicate refresh tasks`. Git history is not initialized in the current workspace, so this documented convention is authoritative.

Keep commits focused. Pull requests should describe scope and validation commands, link relevant issues when available, include UI screenshots for Tool Window changes, and call out protocol or compatibility changes. Do not include generated build output or unrelated cleanup.

## Architecture Constraints

The Android server listens only on `127.0.0.1:18080`. The plugin communicates through ADB forwarding and the versioned HTTP API. Do not add global hotkeys, simulated input, automatic paste, or Windows clipboard reads.
