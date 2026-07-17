# Android Input Bridge WebSocket Protocol

## Overview

The Android App exposes one versioned WebSocket endpoint for the Android Studio
plugin. The endpoint is available only through the loopback address on the
Android device and an ADB port forward.

```bash
adb forward tcp:18080 tcp:18080
```

The client connects to:

```text
ws://127.0.0.1:18080/api/v1/ws
```

The Android server listens only on `127.0.0.1:18080`. It does not expose a LAN
endpoint and does not accept computer-to-phone text updates.

Wire models are defined in the root `:protocol` Kotlin/JVM module and are
serialized with Kotlin serialization. Messages are UTF-8 JSON text frames.
Binary frames are rejected.

## Protocol constants

| Name | Value |
| --- | --- |
| Protocol version | `2` |
| Endpoint | `/api/v1/ws` |
| Server host | `127.0.0.1` |
| Server/forward port | `18080` |
| Maximum message size | `512 KiB` |

The server sends WebSocket ping frames every 15 seconds and considers the
connection idle after 30 seconds without activity. The client must treat socket
closure as an offline state and may recover through the manual Reconnect action.

## Message format

Every JSON message has a `type` discriminator. Commands that require a response
carry a unique `requestId`; the response must echo that identifier.

Unknown fields are ignored for forward compatibility. Unknown message types,
malformed JSON, binary frames, and unsupported message order are protocol
errors.

## Handshake

The client must send `hello` as the first message:

```json
{
  "type": "hello",
  "protocolVersion": 2,
  "requestId": "hello-1"
}
```

The server responds with `hello_ack`:

```json
{
  "type": "hello_ack",
  "status": "ok",
  "appVersion": "1.0.1",
  "protocolVersion": 2,
  "serverTime": 1783780000000,
  "requestId": "hello-1"
}
```

After the acknowledgement, the server sends the current snapshot. The initial
snapshot is an event and therefore has no request ID:

```json
{
  "type": "text_snapshot",
  "text": "当前输入内容\n第二行 😀",
  "version": 17,
  "updatedAt": 1783780000000,
  "requestId": null
}
```

If the protocol version is unsupported, the server sends `error` with code
`UNSUPPORTED_PROTOCOL_VERSION` and closes the session.

## Server events

### `text_changed`

The server pushes the latest in-memory text state whenever it changes:

```json
{
  "type": "text_changed",
  "text": "new text",
  "version": 18,
  "updatedAt": 1783780001000
}
```

The Repository's in-memory state is authoritative. Rapid updates may be
coalesced per connection, but the latest state must be observable after the
updates settle. Clients must not use polling to compensate for this event.

## Client commands

### `get_snapshot`

Requests an explicit snapshot over an existing connection:

```json
{
  "type": "get_snapshot",
  "requestId": "snapshot-1"
}
```

Response:

```json
{
  "type": "text_snapshot",
  "text": "current text",
  "version": 18,
  "updatedAt": 1783780001000,
  "requestId": "snapshot-1"
}
```

### `clear`

Clearing is version-aware. The client sends the version of the displayed
snapshot:

```json
{
  "type": "clear",
  "expectedVersion": 18,
  "requestId": "clear-1"
}
```

On success:

```json
{
  "type": "clear_succeeded",
  "clearedVersion": 18,
  "newVersion": 19,
  "requestId": "clear-1"
}
```

The server may then push the resulting empty state as `text_changed`.

If the phone text changed after the client loaded it, the server does not clear
the newer text:

```json
{
  "type": "version_conflict",
  "currentVersion": 19,
  "requestId": "clear-1"
}
```

Negative versions return `INVALID_EXPECTED_VERSION`. Persistence failures
return `TEXT_CLEAR_FAILED`. Both errors include the clear request ID.

## Errors

Errors use this message shape:

```json
{
  "type": "error",
  "code": "ERROR_CODE",
  "message": "Human-readable message",
  "details": {},
  "requestId": "request-1"
}
```

`requestId` is omitted or `null` when an error cannot be associated with a
specific command.

Defined error codes include:

| Code | Meaning |
| --- | --- |
| `MALFORMED_MESSAGE` | JSON or frame type is invalid |
| `INVALID_HANDSHAKE` | The first message is not `hello` |
| `UNSUPPORTED_PROTOCOL_VERSION` | Client and server versions differ |
| `UNEXPECTED_MESSAGE` | Message is not valid in the current session state |
| `INVALID_EXPECTED_VERSION` | Clear version is negative |
| `INITIAL_SNAPSHOT_TIMEOUT` | Initial Repository state was unavailable in time |
| `TEXT_CLEAR_FAILED` | The Repository could not clear the text |

Protocol errors close the session with a WebSocket protocol-error close reason.
The plugin displays the connection failure and leaves recovery to Reconnect.

## Lifecycle and testing

The server is owned by the Android Foreground Service. Starting the service
starts the server; stopping the service closes active sessions. The plugin runs
ADB and socket operations off the IntelliJ EDT with bounded timeouts.

Automated tests cover handshake, snapshots, pushed changes, clear success and
conflicts, malformed messages, frame limits, session fan-out, client request
correlation, socket failures, and lifecycle disposal. Manual verification uses
the ADB forwarding command above and includes rapid Unicode input, reconnect,
service restart, and Copy & Clear version conflicts.
