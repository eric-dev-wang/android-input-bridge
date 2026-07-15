package com.ericdevwang.androidinputbridge.plugin.connection

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

fun interface PollingHandle {
    fun cancel()
}

fun interface PollingScheduler {
    fun scheduleWithFixedDelay(
        initialDelayMillis: Long,
        delayMillis: Long,
        task: () -> Unit,
    ): PollingHandle
}

class ExecutorPollingScheduler(
    private val executor: ScheduledExecutorService,
) : PollingScheduler {
    override fun scheduleWithFixedDelay(
        initialDelayMillis: Long,
        delayMillis: Long,
        task: () -> Unit,
    ): PollingHandle {
        val future = executor.scheduleWithFixedDelay(
            task,
            initialDelayMillis,
            delayMillis,
            TimeUnit.MILLISECONDS,
        )
        return PollingHandle { future.cancel(false) }
    }
}
