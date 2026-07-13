# Repository Guidelines

## Project Structure & Module Organization

This repository uses one root Gradle project with two product modules and one shared module:

- `app/`: Kotlin Android application, with production code under `app/src/main`, unit tests under `app/src/test`, and Android resources under `app/src/main/res`.
- `protocol/`: plain Kotlin/JVM HTTP protocol module, with shared DTOs under `protocol/src/main/kotlin` and serialization tests under `protocol/src/test/kotlin`.
- `android-studio-plugin/`: Kotlin IntelliJ Platform plugin module, with code under `src/main/kotlin`, tests under `src/test/kotlin`, and plugin metadata under `src/main/resources/META-INF`.
- `docs/`: requirements, commit conventions, and implementation notes.
- `.github/workflows/`: pull request/main CI and SemVer Tag release workflows.
- `.worktrees/`: local isolated Git worktrees used for feature implementation; its contents are not committed.

Keep the Android App and plugin independently buildable within the same root Gradle project. Keep `protocol/` limited to versioned wire models and serialization contracts. Use `docs/requirements.md` as the functional source of truth.

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
./gradlew :protocol:test
./gradlew :android-studio-plugin:buildPlugin
./gradlew :android-studio-plugin:test
./gradlew :android-studio-plugin:verifyPlugin
```

Use `adb forward tcp:18080 tcp:18080` for manual end-to-end checks. HTTP and ADB operations must run off the IntelliJ EDT and use bounded timeouts.

## Coding Style & Naming Conventions

Use Kotlin official style with four-space indentation. Use `PascalCase` for classes and composables, `camelCase` for functions and properties, and `UPPER_SNAKE_CASE` only for constants. Keep files focused by responsibility and prefer explicit interfaces between storage, server, ADB, HTTP, clipboard, and UI layers. Follow Compose conventions in the Android App and IntelliJ/Swing threading rules in the plugin.

## Testing Guidelines

Test version changes, persistence, UTF-8/multiline text, API responses, ADB parsing, version conflicts, clipboard failure handling, and Copy-before-Clear ordering. Name tests after observable behavior, such as `clearWithStaleVersionReturnsConflict`. Add or update tests with every behavior change; no global coverage threshold is defined yet.

Before a PR, run the full matrix: Android lint and unit tests, Protocol tests, Plugin tests, `buildPlugin`, and `verifyPlugin`. Use a local `androidStudioPath` when the default IDE artifact is unavailable.

## Commit & Pull Request Guidelines

Use the repository convention in [`docs/git-commit-convention.md`](docs/git-commit-convention.md): English by default and messages such as `feat(app): persist the current text state` or `fix(plugin): prevent duplicate refresh tasks`. The existing Git history and this documented convention are authoritative.

Keep commits focused. Pull requests should describe scope and validation commands, link relevant issues when available, include UI screenshots for Tool Window changes, and call out protocol or compatibility changes. Do not include generated build output or unrelated cleanup.

## Architecture Constraints

The Android server listens only on `127.0.0.1:18080`. The plugin communicates through ADB forwarding and the versioned HTTP API. HTTP uses 1-second connect and 2-second request timeouts; ADB commands use a 5-second timeout. Do not add settings persistence, global hotkeys, simulated input, automatic paste, or Windows clipboard reads.
