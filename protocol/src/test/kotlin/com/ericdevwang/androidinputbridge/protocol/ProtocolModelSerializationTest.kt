package com.ericdevwang.androidinputbridge.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtocolModelSerializationTest {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    @Test
    fun responseModelsKeepTheHttpWireFormat() {
        assertEquals(
            "{\"status\":\"ok\",\"appVersion\":\"1.0.0\",\"protocolVersion\":1,\"serverTime\":123}",
            json.encodeToString(HealthResponse("ok", "1.0.0", 1, 123L)),
        )
        assertEquals(
            "{\"text\":\"中文\\n😀\",\"version\":7,\"updatedAt\":123}",
            json.encodeToString(TextResponse("中文\n😀", 7L, 123L)),
        )
        assertEquals(
            "{\"clearedVersion\":7,\"newVersion\":8}",
            json.encodeToString(ClearResponse(7L, 8L)),
        )
    }

    @Test
    fun errorResponseDefaultsToAnEmptyDetailsObject() {
        assertEquals(
            "{\"code\":\"NOT_FOUND\",\"message\":\"Missing\",\"details\":{}}",
            json.encodeToString(ErrorResponse("NOT_FOUND", "Missing")),
        )
    }

    @Test
    fun websocketMessagesUseTypedDiscriminatorAndRequestIds() {
        assertEquals(
            "{\"type\":\"hello\",\"protocolVersion\":2,\"requestId\":\"hello-1\"}",
            ProtocolJson.default.encodeToString<BridgeMessage>(HelloCommand(2, "hello-1")),
        )
        assertEquals(
            "{\"type\":\"text_snapshot\",\"text\":\"中文\\n😀\",\"version\":7,\"updatedAt\":123,\"requestId\":null}",
            ProtocolJson.default.encodeToString<BridgeMessage>(TextSnapshot("中文\n😀", 7L, 123L)),
        )
        assertEquals(
            TextChanged("live", 8L, 124L),
            ProtocolJson.default.decodeFromString<BridgeMessage>(
                "{\"type\":\"text_changed\",\"text\":\"live\",\"version\":8,\"updatedAt\":124}",
            ),
        )
    }

    @Test
    fun allWebsocketMessagesRoundTripThroughSharedJson() {
        val messages = listOf<BridgeMessage>(
            HelloCommand(2, "hello-1"),
            GetSnapshotCommand("snapshot-1"),
            ClearCommand(expectedVersion = 7L, requestId = "clear-1"),
            HelloAck("ok", "1.0.0", 2, 123L, "hello-1"),
            TextSnapshot("", 7L, 123L, requestId = "snapshot-1"),
            TextChanged("中文\n😀", 8L, 124L),
            ClearSucceeded(clearedVersion = 8L, newVersion = 9L, requestId = "clear-1"),
            VersionConflict(currentVersion = 10L, requestId = "clear-1"),
            BridgeError(
                code = "INVALID_MESSAGE",
                message = "Invalid message",
                details = JsonObject(mapOf("currentVersion" to JsonPrimitive(10L))),
                requestId = "clear-1",
            ),
        )

        messages.forEach { message ->
            val encoded = ProtocolJson.default.encodeToString<BridgeMessage>(message)
            assertEquals(message, ProtocolJson.default.decodeFromString<BridgeMessage>(encoded))
        }
    }

    @Test(expected = SerializationException::class)
    fun unknownWebsocketMessageTypeIsRejected() {
        ProtocolJson.default.decodeFromString<BridgeMessage>("{\"type\":\"unknown\"}")
    }

    @Test
    fun knownWebsocketMessageIgnoresUnknownFields() {
        assertEquals(
            HelloCommand(protocolVersion = 2, requestId = "hello-1"),
            ProtocolJson.default.decodeFromString<BridgeMessage>(
                "{\"type\":\"hello\",\"protocolVersion\":2," +
                    "\"requestId\":\"hello-1\",\"futureField\":true}",
            ),
        )
    }

    @Test
    fun protocolVersionIsSharedByClientsAndServer() {
        assertEquals(2, ProtocolConstants.CURRENT_VERSION)
        assertEquals("127.0.0.1", ProtocolConstants.LOCALHOST)
        assertEquals(18080, ProtocolConstants.SERVER_PORT)
    }
}
