# Repository Guidelines

## Project Structure & Module Organization

This repository uses one root Gradle project with two independent modules:

- `app/`: Kotlin Android application, with production code under `app/src/main`, unit tests under `app/src/test`, and Android resources under `app/src/main/res`.
- `android-studio-plugin/`: Kotlin IntelliJ Platform plugin module, with code under `src/main/kotlin`, tests under `src/test/kotlin`, and plugin metadata under `src/main/resources/META-INF`. The module will be added to the root build when plugin implementation begins.
- `docs/`: requirements, commit conventions, and implementation notes.
- `.worktrees/`: local isolated Git worktrees used for feature implementation; its contents are not committed.

Keep the Android App and plugin independently buildable within the same root Gradle project. Use `docs/requirements.md` as the functional source of truth and avoid adding shared modules until a clear protocol-sharing need exists.

## Worktree Workflow

Create temporary or isolated worktrees under `.worktrees/` using a descriptive name, for example:

```bash
git worktree add .worktrees/feature-name -b agent/feature-name
```

Keep implementation changes inside the selected worktree and remove it after the branch has been integrated. Do not commit files generated under `.worktrees/`.

## Build, Test, and Development Commands

Run commands from the repository root:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :android-studio-plugin:buildPlugin
./gradlew :android-studio-plugin:test
```

The plugin commands become available after the plugin module is added to the root build.

Use `adb forward tcp:18080 tcp:18080` for manual end-to-end checks. HTTP and ADB operations must run off the IntelliJ EDT and use bounded timeouts.

## Coding Style & Naming Conventions

Use Kotlin official style with four-space indentation. Use `PascalCase` for classes and composables, `camelCase` for functions and properties, and `UPPER_SNAKE_CASE` only for constants. Keep files focused by responsibility and prefer explicit interfaces between storage, server, ADB, HTTP, clipboard, and UI layers. Follow Compose conventions in the Android App and IntelliJ/Swing threading rules in the plugin.

## Testing Guidelines

Test version changes, persistence, UTF-8/multiline text, API responses, ADB parsing, version conflicts, clipboard failure handling, and Copy-before-Clear ordering. Name tests after observable behavior, such as `clearWithStaleVersionReturnsConflict`. Add or update tests with every behavior change; no global coverage threshold is defined yet.

## Commit & Pull Request Guidelines

Use the repository convention in [`docs/git-commit-convention.md`](docs/git-commit-convention.md): English by default and messages such as `feat(app): persist the current text state` or `fix(plugin): prevent duplicate refresh tasks`. The existing Git history and this documented convention are authoritative.

Keep commits focused. Pull requests should describe scope and validation commands, link relevant issues when available, include UI screenshots for Tool Window changes, and call out protocol or compatibility changes. Do not include generated build output or unrelated cleanup.

## Architecture Constraints

The Android server listens only on `127.0.0.1:18080`. The plugin communicates through ADB forwarding and the versioned HTTP API. Do not add global hotkeys, simulated input, automatic paste, or Windows clipboard reads.
