package com.ericdevwang.androidinputbridge.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    fun protocolVersionIsSharedByClientsAndServer() {
        assertEquals(1, ProtocolConstants.CURRENT_VERSION)
    }
}
