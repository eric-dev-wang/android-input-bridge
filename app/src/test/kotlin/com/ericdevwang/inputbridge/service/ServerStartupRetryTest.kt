package com.ericdevwang.inputbridge.service

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerStartupRetryTest {
    @Test
    fun retriesWithBoundedDelaysUntilServerStarts() = runTest {
        var attempts = 0
        val delays = mutableListOf<Long>()

        val started = ServerStartupRetry(
            start = {
                attempts += 1
                if (attempts < 3) error("port unavailable")
            },
            wait = { delays += it },
        ).run()

        assertTrue(started)
        assertEquals(3, attempts)
        assertEquals(listOf(1_000L, 2_000L), delays)
    }

    @Test
    fun stopsAfterThreeFailedAttempts() = runTest {
        var attempts = 0
        val failures = mutableListOf<Int>()

        val started = ServerStartupRetry(
            start = {
                attempts += 1
                error("port unavailable")
            },
            wait = { },
            onFailure = { attempt, _ -> failures += attempt },
        ).run()

        assertFalse(started)
        assertEquals(3, attempts)
        assertEquals(listOf(1, 2, 3), failures)
    }
}
