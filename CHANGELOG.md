# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2026-07-16

### Added

- Added coordinator-owned HTTP polling at a fixed 300ms interval for near-real-time text synchronization.
- Added an injectable polling scheduler and lifecycle tests for cancellation, failure handling, and request de-duplication.
- Added a wrapping Tool Window action layout so buttons remain visible in narrow windows.
- Added narrow-window and wide-window layout coverage.

### Changed

- Set the default local project version to `1.0.1` for the Android App and Android Studio plugin.
- Made `bridgeVersion` the single source of truth for local builds and releases.
- Changed release automation to read `bridgeVersion` while using a pushed SemVer tag as the release trigger and marker.

### Fixed

- Prevented polling from silently stopping when the scheduled polling task encounters an exception.
- Prevented Tool Window action buttons from being clipped when the available width is limited.

## [1.0.0] - 2026-07-14

### Added

- Added the Android text input application with multiline Unicode input, in-memory state management, and local persistence.
- Added the loopback HTTP server with health, text retrieval, and version-aware clear endpoints.
- Added the shared protocol module for Android App and plugin wire models.
- Added the Android Studio plugin Tool Window for device status, text refresh, copy, and Copy & Clear workflows.
- Added ADB discovery, device selection, port forwarding, HTTP connectivity checks, and reconnect handling.
- Added bounded ADB and HTTP operations, sanitized logging, lifecycle protection, and stability tests.
- Added CI verification and release automation for the Android debug APK and plugin distribution ZIP.

### Changed

- Consolidated the Android App, protocol module, and Android Studio plugin into one Gradle project.
- Restricted the Android HTTP server to `127.0.0.1:18080` and communication to the ADB-forwarded local channel.

[1.0.1]: https://github.com/eric-dev-wang/android-input-bridge/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/eric-dev-wang/android-input-bridge/releases/tag/v1.0.0
