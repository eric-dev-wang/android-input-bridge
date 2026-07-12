package com.ericdevwang.androidinputbridge.server

import com.ericdevwang.androidinputbridge.http.HttpTextRepository
import com.ericdevwang.androidinputbridge.model.TextState
import com.ericdevwang.androidinputbridge.repository.ClearResult
import com.ericdevwang.androidinputbridge.repository.PersistenceResult
import com.ericdevwang.androidinputbridge.repository.TextRepository
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Test

class InputHttpServerTest {
    @Test
    fun healthReturnsExactJsonBody() = testApplication {
        application {
            module(
                HttpTextRepository(
                    FakeTextRepository(TextState.initial(1L)),
                    appVersion = "1.0.0",
                    clock = { 123L },
                ),
            )
        }

        val response = client.get("/api/v1/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            "{\"status\":\"ok\",\"appVersion\":\"1.0.0\",\"protocolVersion\":1,\"serverTime\":123}",
            response.bodyAsText(),
        )
    }

    @Test
    fun textPreservesUtf8MultilineEmojiAndTabs() = testApplication {
        application {
            module(
                HttpTextRepository(
                    FakeTextRepository(TextState("中文\nline 😀\t", 7L, 123L)),
                ),
            )
        }

        val response = client.get("/api/v1/text")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            "{\"text\":\"中文\\nline 😀\\t\",\"version\":7,\"updatedAt\":123}",
            response.bodyAsText(),
        )
    }

    @Test
    fun textReturnsEmptyText() = testApplication {
        application { module(HttpTextRepository(FakeTextRepository(TextState.initial(5L)))) }

        val response = client.get("/api/v1/text")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            "{\"text\":\"\",\"version\":0,\"updatedAt\":5}",
            response.bodyAsText(),
        )
    }

    @Test
    fun clearReturnsSuccessBody() = testApplication {
        application {
            module(
                HttpTextRepository(
                    FakeTextRepository(
                        TextState("text", 3L, 10L),
                        clearResult = ClearResult.Cleared(3L, 4L),
                    ),
                ),
            )
        }

        val response = client.post("/api/v1/text/clear/3")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            "{\"clearedVersion\":3,\"newVersion\":4}",
            response.bodyAsText(),
        )
    }

    @Test
    fun staleClearReturnsConflictWithCurrentVersion() = testApplication {
        application {
            module(
                HttpTextRepository(
                    FakeTextRepository(
                        TextState("new", 4L, 10L),
                        clearResult = ClearResult.VersionConflict(4L),
                    ),
                ),
            )
        }

        val response = client.post("/api/v1/text/clear/3")

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(
            "{\"code\":\"VERSION_CONFLICT\",\"message\":\"Text changed after the client loaded it.\",\"details\":{\"currentVersion\":4}}",
            response.bodyAsText(),
        )
    }

    @Test
    fun invalidExpectedVersionReturnsBadRequest() = testApplication {
        application { module(HttpTextRepository(FakeTextRepository(TextState.initial(0L)))) }

        val response = client.post("/api/v1/text/clear/not-a-version")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            "{\"code\":\"INVALID_EXPECTED_VERSION\",\"message\":\"Expected version must be a non-negative integer.\",\"details\":null}",
            response.bodyAsText(),
        )
    }

    @Test
    fun negativeExpectedVersionReturnsBadRequest() = testApplication {
        application { module(HttpTextRepository(FakeTextRepository(TextState.initial(0L)))) }

        val response = client.post("/api/v1/text/clear/-1")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(
            "{\"code\":\"INVALID_EXPECTED_VERSION\",\"message\":\"Expected version must be a non-negative integer.\",\"details\":null}",
            response.bodyAsText(),
        )
    }

    @Test
    fun unknownPathReturnsNotFound() = testApplication {
        application { module(HttpTextRepository(FakeTextRepository(TextState.initial(0L)))) }

        val response = client.get("/api/v1/unknown")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(
            "{\"code\":\"NOT_FOUND\",\"message\":\"The requested endpoint was not found.\",\"details\":null}",
            response.bodyAsText(),
        )
    }

    @Test
    fun unsupportedMethodReturnsMethodNotAllowed() = testApplication {
        application { module(HttpTextRepository(FakeTextRepository(TextState.initial(0L)))) }

        val response = client.post {
            url("/api/v1/text")
        }

        assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
        assertEquals(
            "{\"code\":\"METHOD_NOT_ALLOWED\",\"message\":\"The HTTP method is not allowed for this endpoint.\",\"details\":null}",
            response.bodyAsText(),
        )
    }

    @Test
    fun repositoryFailureUsesTextEndpointError() = testApplication {
        application { module(HttpTextRepository(FailingTextRepository())) }

        val response = client.get("/api/v1/text")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(
            "{\"code\":\"TEXT_READ_FAILED\",\"message\":\"Unable to read current text.\",\"details\":null}",
            response.bodyAsText(),
        )
    }

    @Test
    fun repositoryFailureUsesClearEndpointError() = testApplication {
        application { module(HttpTextRepository(FailingTextRepository())) }

        val response = client.post("/api/v1/text/clear/0")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(
            "{\"code\":\"CLEAR_FAILED\",\"message\":\"Unable to clear text.\",\"details\":null}",
            response.bodyAsText(),
        )
    }
}

private class FakeTextRepository(
    initialState: TextState,
    private val clearResult: ClearResult = ClearResult.Cleared(
        clearedVersion = initialState.version,
        newVersion = initialState.version + 1L,
    ),
) : TextRepository {
    override val state: Flow<TextState> = MutableStateFlow(initialState)

    override suspend fun save(state: TextState): PersistenceResult =
        PersistenceResult.Succeeded(state.version)

    override suspend fun clear(expectedVersion: Long): ClearResult = clearResult
}

private class FailingTextRepository : TextRepository {
    override val state: Flow<TextState> = flow { error("read failed") }

    override suspend fun save(state: TextState): PersistenceResult = error("save failed")

    override suspend fun clear(expectedVersion: Long): ClearResult = error("clear failed")
}
