package com.ericdevwang.androidinputbridge.plugin.connection

import com.ericdevwang.androidinputbridge.plugin.adb.AdbClient
import com.ericdevwang.androidinputbridge.plugin.adb.AdbDevice
import com.ericdevwang.androidinputbridge.plugin.adb.AdbError
import com.ericdevwang.androidinputbridge.plugin.adb.AdbLocator
import com.ericdevwang.androidinputbridge.plugin.adb.AdbResult
import com.ericdevwang.androidinputbridge.plugin.adb.DeviceSelector
import com.ericdevwang.androidinputbridge.plugin.adb.PortForward
import com.ericdevwang.androidinputbridge.plugin.http.BridgeProbe
import com.ericdevwang.androidinputbridge.plugin.http.HttpProbeClient
import com.ericdevwang.androidinputbridge.plugin.http.HttpProbeResult
import com.ericdevwang.androidinputbridge.plugin.http.ProbeError
import com.ericdevwang.androidinputbridge.plugin.http.ProbeFailureCategory
import com.ericdevwang.androidinputbridge.protocol.HealthResponse
import com.ericdevwang.androidinputbridge.protocol.TextResponse
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeConnectionCoordinatorTest {
    @Test
    fun reconnectDiscoversDeviceForwardsAndPublishesConnectedState() {
        val device = AdbDevice("serial", "Pixel 8")
        val adb = FakeAdbClient(deviceLists = ArrayDeque(listOf(listOf(device))))
        val coordinator = newCoordinator(adb = adb, probe = SuccessfulProbe())

        coordinator.reconnect()

        assertEquals(BridgeConnectionState.CONNECTED, coordinator.state.connectionState)
        assertEquals("serial", coordinator.state.selectedSerial)
        assertEquals("你好", coordinator.state.text)
        assertTrue(adb.actions.contains("create:serial"))
        assertFalse(coordinator.state.isBusy)
    }

    @Test
    fun reconnectRebuildsForwardOnceAfterConnectionFailure() {
        val device = AdbDevice("serial", "Pixel 8")
        val adb = FakeAdbClient(deviceLists = ArrayDeque(listOf(listOf(device))))
        val probe = SequencedProbe(
            HttpProbeResult.Failure(
                ProbeError(ProbeFailureCategory.CONNECTION, "offline", retryable = true),
            ),
            HttpProbeResult.Success(
                BridgeProbe(
                    health = healthResponse(),
                    text = textResponse(),
                ),
            ),
        )
        val coordinator = newCoordinator(adb = adb, probe = probe)

        coordinator.reconnect()

        assertEquals(BridgeConnectionState.CONNECTED, coordinator.state.connectionState)
        assertEquals(listOf("create:serial", "remove:serial", "create:serial"), adb.actions)
        assertEquals(2, probe.probeCalls)
    }

    @Test
    fun refreshUsesHttpOnlyAndPreservesTextOnInvalidResponse() {
        val device = AdbDevice("serial", "Pixel 8")
        val adb = FakeAdbClient(deviceLists = ArrayDeque(listOf(listOf(device))))
        val probe = RefreshingProbe()
        val coordinator = newCoordinator(adb = adb, probe = probe)
        coordinator.reconnect()
        val actionCountAfterReconnect = adb.actions.size

        coordinator.refresh()

        assertEquals(actionCountAfterReconnect, adb.actions.size)
        assertEquals(BridgeConnectionState.ERROR, coordinator.state.connectionState)
        assertEquals("你好", coordinator.state.text)
    }

    @Test
    fun reconnectUsesSelectorWhenCurrentDeviceIsUnavailable() {
        val first = AdbDevice("first", "First")
        val second = AdbDevice("second", "Second")
        val adb = FakeAdbClient(deviceLists = ArrayDeque(listOf(listOf(first), listOf(second))))
        val coordinator = newCoordinator(
            adb = adb,
            probe = SuccessfulProbe(),
        )
        coordinator.reconnect()
        coordinator.reconnect()

        assertEquals("second", coordinator.state.selectedSerial)
        assertEquals(listOf("create:first", "remove:first", "create:second"), adb.actions)
    }

    private fun newCoordinator(
        adb: FakeAdbClient,
        probe: HttpProbeClient,
        selector: DeviceSelector = DeviceSelector { it.first() },
    ): BridgeConnectionCoordinator = BridgeConnectionCoordinator(
        adbLocator = AdbLocator(
            configuredAdbProvider = { Path.of("/adb") },
            isUsable = { true },
        ),
        adbClientFactory = { adb },
        httpProbeClientFactory = { probe },
        deviceSelector = selector,
        executor = Executor { it.run() },
        clock = { Instant.parse("2026-07-13T12:00:00Z") },
    )

    private class FakeAdbClient(
        private val deviceLists: ArrayDeque<List<AdbDevice>>,
    ) : AdbClient {
        val actions = mutableListOf<String>()

        override fun devices(): AdbResult<List<AdbDevice>> =
            AdbResult.Success(if (deviceLists.size > 1) deviceLists.removeFirst() else deviceLists.first())

        override fun listForwards(): AdbResult<List<PortForward>> = AdbResult.Success(
            actions.lastOrNull()?.let { action ->
                if (action.startsWith("create:")) listOf(PortForward(action.removePrefix("create:"), 18080, 18080)) else emptyList()
            } ?: emptyList(),
        )

        override fun createForward(serial: String): AdbResult<Unit> {
            actions += "create:$serial"
            return AdbResult.Success(Unit)
        }

        override fun removeForward(serial: String): AdbResult<Unit> {
            actions += "remove:$serial"
            return AdbResult.Success(Unit)
        }
    }

    private class SuccessfulProbe : HttpProbeClient {
        override fun probe(): HttpProbeResult<BridgeProbe> =
            HttpProbeResult.Success(BridgeProbe(healthResponse(), textResponse()))

        override fun fetchText(): HttpProbeResult<TextResponse> = HttpProbeResult.Success(textResponse())
    }

    private class SequencedProbe(
        private vararg val results: HttpProbeResult<BridgeProbe>,
    ) : HttpProbeClient {
        var probeCalls = 0

        override fun probe(): HttpProbeResult<BridgeProbe> = results[probeCalls++].also { }

        override fun fetchText(): HttpProbeResult<TextResponse> = HttpProbeResult.Success(textResponse())
    }

    private class RefreshingProbe : HttpProbeClient {
        private var refreshCalls = 0

        override fun probe(): HttpProbeResult<BridgeProbe> =
            HttpProbeResult.Success(BridgeProbe(healthResponse(), textResponse()))

        override fun fetchText(): HttpProbeResult<TextResponse> {
            refreshCalls++
            return if (refreshCalls == 1) {
                HttpProbeResult.Failure(
                    ProbeError(ProbeFailureCategory.INVALID_RESPONSE, "invalid", retryable = false),
                )
            } else {
                HttpProbeResult.Success(textResponse())
            }
        }
    }

    private companion object {
        fun healthResponse() = HealthResponse("ok", "1.0.0", 1, 1783780000000)

        fun textResponse() = TextResponse("你好", 17, 1783780000000)
    }
}
