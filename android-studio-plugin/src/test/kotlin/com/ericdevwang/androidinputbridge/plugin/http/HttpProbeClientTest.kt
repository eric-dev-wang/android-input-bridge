package com.ericdevwang.androidinputbridge.plugin.http

import com.ericdevwang.androidinputbridge.protocol.ProtocolConstants
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpProbeClientTest {
    @Test
    fun probeParsesHealthAndTextResponses() {
        val transport = FakeHttpTransport(
            responses = ArrayDeque(
                listOf(
                    HttpResponse(200, healthJson()),
                    HttpResponse(200, textJson()),
                ),
            ),
        )

        val result = JdkHttpProbeClient(transport).probe()

        val success = result as HttpProbeResult.Success
        assertEquals("ok", success.value.health.status)
        assertEquals("你好 👋\ncode", success.value.text.text)
        assertEquals(listOf("/api/v1/health", "/api/v1/text"), transport.paths)
    }

    @Test
    fun probeRejectsProtocolMismatch() {
        val transport = FakeHttpTransport(
            responses = ArrayDeque(
                listOf(HttpResponse(200, healthJson(protocolVersion = ProtocolConstants.CURRENT_VERSION + 1))),
            ),
        )

        val result = JdkHttpProbeClient(transport).probe()

        val failure = result as HttpProbeResult.Failure
        assertEquals(ProbeFailureCategory.INVALID_RESPONSE, failure.error.category)
        assertTrue(failure.error.message.contains("protocol"))
    }

    @Test
    fun fetchTextPreservesUnicodeAndNewlines() {
        val transport = FakeHttpTransport(
            responses = ArrayDeque(listOf(HttpResponse(200, textJson()))),
        )

        val result = JdkHttpProbeClient(transport).fetchText()

        assertEquals("你好 👋\ncode", (result as HttpProbeResult.Success).value.text)
    }

    @Test
    fun connectionFailureIsRetryable() {
        val transport = FakeHttpTransport(exception = IOException("connection refused"))

        val result = JdkHttpProbeClient(transport).probe()

        val failure = result as HttpProbeResult.Failure
        assertEquals(ProbeFailureCategory.CONNECTION, failure.error.category)
        assertTrue(failure.error.retryable)
    }

    private fun healthJson(protocolVersion: Int = ProtocolConstants.CURRENT_VERSION): String =
        """{"status":"ok","appVersion":"1.0.0","protocolVersion":$protocolVersion,"serverTime":1783780000000}"""

    private fun textJson(): String =
        """{"text":"你好 👋\ncode","version":17,"updatedAt":1783780000000}"""

    private class FakeHttpTransport(
        private val responses: ArrayDeque<HttpResponse> = ArrayDeque(),
        private val exception: IOException? = null,
    ) : HttpProbeTransport {
        val paths = mutableListOf<String>()

        override fun get(path: String): HttpResponse {
            paths += path
            exception?.let { throw it }
            return responses.removeFirst()
        }
    }
}
