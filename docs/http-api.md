# Android Input Bridge HTTP API

## Overview

The Android App exposes a local HTTP API for clients connected through ADB.
The server listens only on `127.0.0.1:18080` on the Android device.

Forward the device-local port to the host computer:

```bash
adb forward tcp:18080 tcp:18080
```

The host client then uses:

```text
http://127.0.0.1:18080
```

The API is versioned under `/api/v1`. The protocol does not use authentication,
tokens, accounts, or a global success envelope. HTTP status codes indicate
success or failure.

The wire models are defined in the root project's `:protocol` Kotlin/JVM
module. Both the Android server and the future Android Studio plugin client
must consume these shared models instead of defining duplicate response types.

All JSON responses use:

```text
Content-Type: application/json; charset=utf-8
```

## Error Response

Every error response uses this structure:

```json
{
  "code": "ERROR_CODE",
  "message": "Human-readable message",
  "details": {}
}
```

`details` contains endpoint-specific values when needed. For example, a
version conflict includes the current version.

## Health Check

### Request

```http
GET /api/v1/health
```

### Success: `200 OK`

```json
{
  "status": "ok",
  "appVersion": "1.0.0",
  "protocolVersion": 1,
  "serverTime": 1783780000000
}
```

`serverTime` is Unix epoch time in milliseconds.

## Get Current Text

### Request

```http
GET /api/v1/text
```

### Success: `200 OK`

```json
{
  "text": "当前输入内容\n第二行 😀",
  "version": 17,
  "updatedAt": 1783780000000
}
```

The `text` value is returned exactly as persisted. Clients must expect
Unicode, Chinese, Emoji, newlines, tabs, code snippets, and empty text. The
server does not trim, normalize, or format the value.

The endpoint reads the persisted state exposed by `TextRepository`. Because UI
saves are memory-first and persisted asynchronously, a client may briefly see
the previous persisted version while a newer UI change is being written.

Example:

```bash
curl -i http://127.0.0.1:18080/api/v1/text
```

Read failure: `500 Internal Server Error`

```json
{
  "code": "TEXT_READ_FAILED",
  "message": "Current text could not be read.",
  "details": {}
}
```

## Clear Current Text

### Request

```http
POST /api/v1/text/clear/{expectedVersion}
```

The request has no body. `expectedVersion` must be a non-negative decimal
integer previously observed by the client.

Example:

```bash
curl -i -X POST \
  http://127.0.0.1:18080/api/v1/text/clear/17
```

The clear operation is atomic. It succeeds only when:

```text
expectedVersion == currentVersion
```

### Success: `200 OK`

```json
{
  "clearedVersion": 17,
  "newVersion": 18
}
```

If the text is already empty and the version matches, clearing is a successful
no-op and the version does not increase:

```json
{
  "clearedVersion": 18,
  "newVersion": 18
}
```

### Version Conflict: `409 Conflict`

The server does not modify the text when the expected version is stale.

```json
{
  "code": "VERSION_CONFLICT",
  "message": "Text changed after the client loaded it.",
  "details": {
    "currentVersion": 18
  }
}
```

Clients should call `GET /api/v1/text` again after a conflict.

### Invalid Version: `400 Bad Request`

```json
{
  "code": "INVALID_EXPECTED_VERSION",
  "message": "Expected version must be a non-negative integer.",
  "details": {}
}
```

### Persistence Failure: `500 Internal Server Error`

```json
{
  "code": "TEXT_CLEAR_FAILED",
  "message": "Current text could not be cleared.",
  "details": {}
}
```

## Common Routing Errors

Unknown path: `404 Not Found`

```json
{
  "code": "NOT_FOUND",
  "message": "The requested endpoint was not found.",
  "details": {}
}
```

Unsupported method: `405 Method Not Allowed`

```json
{
  "code": "METHOD_NOT_ALLOWED",
  "message": "The HTTP method is not allowed for this endpoint.",
  "details": {}
}
```

Unexpected server failure: `500 Internal Server Error`

```json
{
  "code": "INTERNAL_SERVER_ERROR",
  "message": "Unexpected server error.",
  "details": {}
}
```

## Operational Boundary

The HTTP Server is available only while the Android Foreground Service is
running. It does not expose the service to the phone's Wi-Fi or LAN
interfaces, and it does not accept computer-to-phone text updates.
