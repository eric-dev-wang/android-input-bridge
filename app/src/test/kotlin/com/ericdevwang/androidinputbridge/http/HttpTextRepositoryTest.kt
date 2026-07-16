package com.ericdevwang.androidinputbridge.http

import com.ericdevwang.androidinputbridge.model.TextState
import com.ericdevwang.androidinputbridge.protocol.ClearResponse
import com.ericdevwang.androidinputbridge.protocol.HealthResponse
import com.ericdevwang.androidinputbridge.protocol.TextResponse
import com.ericdevwang.androidinputbridge.repository.ClearResult
import com.ericdevwang.androidinputbridge.repository.PersistenceResult
import com.ericdevwang.androidinputbridge.repository.TextRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpTextRepositoryTest {
    @Test
    fun textReadsCurrentStateWithoutChangingUnicodeOrWhitespace() = runTest {
        val state = TextState("中文\nemoji 😀\t", version = 7L, updatedAt = 123L)

        val result = HttpTextRepository(
            repository = FakeTextRepository(state),
            appVersion = "1.0.0",
        ).text()

        assertEquals(TextResponse(state.text, state.version, state.updatedAt), result)
    }

    @Test
    fun healthIncludesProtocolAndInjectedServerMetadata() = runTest {
        val result = HttpTextRepository(
            repository = FakeTextRepository(TextState.initial(1L)),
            appVersion = "2.3.4",
            clock = { 456L },
        ).health()

        assertEquals(
            HealthResponse(
                status = "ok",
                appVersion = "2.3.4",
                protocolVersion = 2,
                serverTime = 456L,
            ),
            result,
        )
    }

    @Test
    fun clearMapsSuccessfulRepositoryResult() = runTest {
        val result = HttpTextRepository(
            repository = FakeTextRepository(
                initialState = TextState("text", 3L, 10L),
                clearResult = ClearResult.Cleared(clearedVersion = 3L, newVersion = 4L),
            ),
            appVersion = "1.0.0",
        ).clear(expectedVersion = 3L)

        assertEquals(
            HttpClearResult.Cleared(ClearResponse(clearedVersion = 3L, newVersion = 4L)),
            result,
        )
    }

    @Test
    fun clearMapsVersionConflictRepositoryResult() = runTest {
        val result = HttpTextRepository(
            repository = FakeTextRepository(
                initialState = TextState("new", 4L, 10L),
                clearResult = ClearResult.VersionConflict(currentVersion = 4L),
            ),
            appVersion = "1.0.0",
        ).clear(expectedVersion = 3L)

        assertEquals(HttpClearResult.VersionConflict(currentVersion = 4L), result)
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
