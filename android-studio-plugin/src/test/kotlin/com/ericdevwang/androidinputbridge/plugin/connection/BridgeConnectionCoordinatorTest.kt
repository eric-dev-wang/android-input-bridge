package com.ericdevwang.androidinputbridge.plugin.connection

import com.ericdevwang.androidinputbridge.plugin.adb.AdbClient
import com.ericdevwang.androidinputbridge.plugin.adb.AdbDevice
import com.ericdevwang.androidinputbridge.plugin.adb.AdbError
import com.ericdevwang.androidinputbridge.plugin.adb.AdbLocator
import com.ericdevwang.androidinputbridge.plugin.adb.AdbResult
import com.ericdevwang.androidinputbridge.plugin.adb.DeviceSelector
import com.ericdevwang.androidinputbridge.plugin.adb.PortForward
import com.ericdevwang.androidinputbridge.plugin.clipboard.ClipboardWriteResult
import com.ericdevwang.androidinputbridge.plugin.clipboard.ClipboardWriter
import com.ericdevwang.androidinputbridge.plugin.http.BridgeProbe
import com.ericdevwang.androidinputbridge.plugin.http.HttpProbeClient
import com.ericdevwang.androidinputbridge.plugin.http.HttpProbeResult
import com.ericdevwang.androidinputbridge.plugin.http.ProbeError
import com.ericdevwang.androidinputbridge.plugin.http.ProbeFailureCategory
import com.ericdevwang.androidinputbridge.protocol.HealthResponse
import com.ericdevwang.androidinputbridge.protocol.ClearResponse
import com.ericdevwang.androidinputbridge.protocol.TextResponse
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.Executor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
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

    @Test
    fun reconnectReusesProbeClientAndClosesItWhenDisposed() {
        val device = AdbDevice("serial", "Pixel 8")
        val adb = FakeAdbClient(deviceLists = ArrayDeque(listOf(listOf(device))))
        val probe = ClosableSuccessfulProbe()
        var factoryCalls = 0
        val coordinator = newCoordinator(
            adb = adb,
            probe = probe,
            probeFactory = {
                factoryCalls++
                probe
            },
        )

        coordinator.reconnect()
        coordinator.reconnect()
        coordinator.dispose()

        assertEquals(1, factoryCalls)
        assertEquals(1, probe.closeCalls)
    }

    @Test
    fun listenerNotificationDoesNotHoldCoordinatorLock() {
        val device = AdbDevice("serial", "Pixel 8")
        val coordinator = newCoordinator(
            adb = FakeAdbClient(deviceLists = ArrayDeque(listOf(listOf(device)))),
            probe = SuccessfulProbe(),
        )
        val listenerEntered = CountDownLatch(1)
        val reentrantOperationCompleted = CountDownLatch(1)
        coordinator.addListener { state ->
            if (state.isBusy) {
                Thread {
                    coordinator.addListener { }
                    reentrantOperationCompleted.countDown()
                }.start()
                listenerEntered.countDown()
                assertTrue(reentrantOperationCompleted.await(1, TimeUnit.SECONDS))
            }
        }

        coordinator.reconnect()

        assertTrue(listenerEntered.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun rejectedExecutorClearsBusyState() {
        val device = AdbDevice("serial", "Pixel 8")
        val coordinator = newCoordinator(
            adb = FakeAdbClient(deviceLists = ArrayDeque(listOf(listOf(device)))),
            probe = SuccessfulProbe(),
            executor = Executor { throw RejectedExecutionException("executor closed") },
        )

        coordinator.reconnect()

        assertFalse(coordinator.state.isBusy)
        assertEquals(BridgeConnectionState.ERROR, coordinator.state.connectionState)
        assertTrue(coordinator.state.errorMessage!!.contains("scheduled"))
    }

    @Test
    fun runtimeExecutorFailureClearsBusyState() {
        val coordinator = newCoordinator(
            adb = FakeAdbClient(deviceLists = ArrayDeque(listOf(listOf(AdbDevice("serial", "Pixel 8"))))),
            probe = SuccessfulProbe(),
            executor = Executor { throw IllegalStateException("executor closed") },
        )

        coordinator.reconnect()

        assertFalse(coordinator.state.isBusy)
        assertEquals(BridgeConnectionState.ERROR, coordinator.state.connectionState)
        assertTrue(coordinator.state.errorMessage!!.contains("scheduled"))
    }

    @Test
    fun copyUsesDisplayedSnapshotAndDoesNotCallHttp() {
        val probe = RecordingProbe()
        val clipboard = RecordingClipboardWriter()
        val coordinator = newCoordinator(probe = probe, clipboardWriter = clipboard)
        coordinator.reconnect()

        coordinator.copy()

        assertEquals(listOf("你好"), clipboard.writes)
        assertEquals(0, probe.clearCalls)
        assertEquals("你好", coordinator.state.text)
        assertEquals("Copied", coordinator.state.feedbackMessage)
    }

    @Test
    fun emptyCopyAndClearDoNotCallClipboardOrHttp() {
        val probe = RecordingProbe(initialText = TextResponse("", 17, 1783780000000))
        val clipboard = RecordingClipboardWriter()
        val coordinator = newCoordinator(probe = probe, clipboardWriter = clipboard)
        coordinator.reconnect()

        coordinator.copyAndClear()

        assertTrue(clipboard.writes.isEmpty())
        assertEquals(0, probe.clearCalls)
        assertEquals(17L, coordinator.state.version)
    }

    @Test
    fun copyFailurePreventsClear() {
        val probe = RecordingProbe()
        val clipboard = RecordingClipboardWriter(ClipboardWriteResult.Failure("Clipboard write failed."))
        val coordinator = newCoordinator(probe = probe, clipboardWriter = clipboard)
        coordinator.reconnect()

        coordinator.copyAndClear()

        assertEquals(0, probe.clearCalls)
        assertEquals("你好", coordinator.state.text)
        assertEquals("Clipboard write failed.", coordinator.state.feedbackMessage)
    }

    @Test
    fun copyAndClearWritesClipboardBeforeSendingClear() {
        val events = mutableListOf<String>()
        val probe = RecordingProbe(events = events)
        val clipboard = RecordingClipboardWriter(events = events)
        val coordinator = newCoordinator(probe = probe, clipboardWriter = clipboard)
        coordinator.reconnect()

        coordinator.copyAndClear()

        assertEquals(listOf("clipboard:你好", "clear:17"), events)
    }

    @Test
    fun successfulClearUpdatesLocalTextWithoutFetchingAgain() {
        val probe = RecordingProbe()
        val coordinator = newCoordinator(probe = probe, clipboardWriter = RecordingClipboardWriter())
        coordinator.reconnect()

        coordinator.copyAndClear()

        assertEquals("", coordinator.state.text)
        assertEquals(18L, coordinator.state.version)
        assertEquals(0, probe.fetchCalls)
        assertEquals("Copied and cleared", coordinator.state.feedbackMessage)
    }

    @Test
    fun clearFailurePreservesDisplayedSnapshotWithoutRetry() {
        val probe = RecordingProbe(
            clearResult = HttpProbeResult.Failure(
                ProbeError(ProbeFailureCategory.CONNECTION, "timeout", retryable = true),
            ),
        )
        val coordinator = newCoordinator(probe = probe, clipboardWriter = RecordingClipboardWriter())
        coordinator.reconnect()

        coordinator.copyAndClear()

        assertEquals(1, probe.clearCalls)
        assertEquals("你好", coordinator.state.text)
        assertEquals(17L, coordinator.state.version)
        assertEquals("Text was copied, but the phone content could not be cleared.", coordinator.state.feedbackMessage)
    }

    @Test
    fun versionConflictRefreshesTextOnceAndPreservesCopiedSnapshot() {
        val probe = RecordingProbe(
            clearResult = HttpProbeResult.Failure(
                ProbeError(
                    category = ProbeFailureCategory.INVALID_RESPONSE,
                    message = "HTTP clear returned status 409.",
                    retryable = false,
                    statusCode = 409,
                ),
            ),
            refreshedText = TextResponse("new text", 18, 1783780001000),
        )
        val coordinator = newCoordinator(probe = probe, clipboardWriter = RecordingClipboardWriter())
        coordinator.reconnect()

        coordinator.copyAndClear()

        assertEquals(1, probe.clearCalls)
        assertEquals(1, probe.fetchCalls)
        assertEquals("new text", coordinator.state.text)
        assertEquals(18L, coordinator.state.version)
        assertEquals(
            "Text was copied, but the phone content changed and was not cleared.",
            coordinator.state.feedbackMessage,
        )
    }

    @Test
    fun allBridgeActionsAreIgnoredWhileCopyAndClearIsBusy() {
        val executor = FirstTaskImmediateExecutor()
        val adb = FakeAdbClient(deviceLists = ArrayDeque(listOf(listOf(AdbDevice("serial", "Pixel 8")))))
        val probe = RecordingProbe()
        val coordinator = newCoordinator(
            adb = adb,
            probe = probe,
            clipboardWriter = RecordingClipboardWriter(),
            executor = executor,
        )
        coordinator.reconnect()
        val actionCount = adb.actions.size

        coordinator.copyAndClear()
        coordinator.refresh()
        coordinator.reconnect()
        coordinator.selectDevice("serial")

        assertTrue(coordinator.state.isBusy)
        assertEquals(actionCount, adb.actions.size)
        assertEquals(1, executor.queuedTasks.size)

        executor.queuedTasks.removeFirst().invoke()
        assertFalse(coordinator.state.isBusy)
    }

    @Test
    fun disposedCoordinatorIgnoresLateCopyResult() {
        val executor = FirstTaskImmediateExecutor()
        val clipboard = RecordingClipboardWriter()
        val coordinator = newCoordinator(
            adb = FakeAdbClient(deviceLists = ArrayDeque(listOf(listOf(AdbDevice("serial", "Pixel 8"))))),
            probe = RecordingProbe(),
            clipboardWriter = clipboard,
            executor = executor,
        )
        coordinator.reconnect()
        coordinator.copy()
        coordinator.dispose()

        executor.queuedTasks.removeFirst().invoke()

        assertTrue(clipboard.writes.isEmpty())
    }

    private fun newCoordinator(
        adb: FakeAdbClient = FakeAdbClient(
            deviceLists = ArrayDeque(listOf(listOf(AdbDevice("serial", "Pixel 8")))),
        ),
        probe: HttpProbeClient,
        selector: DeviceSelector = DeviceSelector { it.first() },
        probeFactory: () -> HttpProbeClient = { probe },
        executor: Executor = Executor { it.run() },
        clipboardWriter: ClipboardWriter = RecordingClipboardWriter(),
    ): BridgeConnectionCoordinator = BridgeConnectionCoordinator(
        adbLocator = AdbLocator(
            configuredAdbProvider = { Path.of("/adb") },
            isUsable = { true },
        ),
        adbClientFactory = { adb },
        httpProbeClientFactory = probeFactory,
        deviceSelector = selector,
        executor = executor,
        clipboardWriter = clipboardWriter,
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

    private open class SuccessfulProbe : HttpProbeClient {
        override fun probe(): HttpProbeResult<BridgeProbe> =
            HttpProbeResult.Success(BridgeProbe(healthResponse(), textResponse()))

        override fun fetchText(): HttpProbeResult<TextResponse> = HttpProbeResult.Success(textResponse())

        override fun clearText(expectedVersion: Long): HttpProbeResult<ClearResponse> =
            HttpProbeResult.Success(ClearResponse(expectedVersion, expectedVersion + 1))
    }

    private class ClosableSuccessfulProbe : SuccessfulProbe() {
        var closeCalls = 0

        override fun close() {
            closeCalls++
        }
    }

    private class SequencedProbe(
        private vararg val results: HttpProbeResult<BridgeProbe>,
    ) : HttpProbeClient {
        var probeCalls = 0

        override fun probe(): HttpProbeResult<BridgeProbe> = results[probeCalls++].also { }

        override fun fetchText(): HttpProbeResult<TextResponse> = HttpProbeResult.Success(textResponse())

        override fun clearText(expectedVersion: Long): HttpProbeResult<ClearResponse> =
            HttpProbeResult.Success(ClearResponse(expectedVersion, expectedVersion + 1))
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

        override fun clearText(expectedVersion: Long): HttpProbeResult<ClearResponse> =
            HttpProbeResult.Success(ClearResponse(expectedVersion, expectedVersion + 1))
    }

    private class RecordingClipboardWriter(
        private val result: ClipboardWriteResult = ClipboardWriteResult.Success,
        private val events: MutableList<String>? = null,
    ) : ClipboardWriter {
        val writes = mutableListOf<String>()

        override fun write(text: String): ClipboardWriteResult {
            writes += text
            events?.add("clipboard:$text")
            return result
        }
    }

    private class RecordingProbe(
        private val initialText: TextResponse = textResponse(),
        private val clearResult: HttpProbeResult<ClearResponse> =
            HttpProbeResult.Success(ClearResponse(17, 18)),
        private val refreshedText: TextResponse = textResponse(),
        private val events: MutableList<String>? = null,
    ) : HttpProbeClient {
        var clearCalls = 0
        var fetchCalls = 0

        override fun probe(): HttpProbeResult<BridgeProbe> =
            HttpProbeResult.Success(BridgeProbe(healthResponse(), initialText))

        override fun fetchText(): HttpProbeResult<TextResponse> {
            fetchCalls++
            return HttpProbeResult.Success(refreshedText)
        }

        override fun clearText(expectedVersion: Long): HttpProbeResult<ClearResponse> {
            clearCalls++
            events?.add("clear:$expectedVersion")
            return clearResult
        }
    }

    private class FirstTaskImmediateExecutor : Executor {
        private var executions = 0
        val queuedTasks = ArrayDeque<() -> Unit>()

        override fun execute(command: Runnable) {
            if (executions++ == 0) {
                command.run()
            } else {
                queuedTasks.addLast { command.run() }
            }
        }
    }

    private companion object {
        fun healthResponse() = HealthResponse("ok", "1.0.0", 1, 1783780000000)

        fun textResponse() = TextResponse("你好", 17, 1783780000000)
    }
}
