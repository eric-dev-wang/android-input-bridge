package com.ericdevwang.androidinputbridge.plugin.connection

import java.time.Duration

object BridgeNetworkConfig {
    const val HOST = "127.0.0.1"
    const val PORT = 18080
    const val ADB_TIMEOUT_SECONDS = 5L
    const val HTTP_CONNECT_TIMEOUT_SECONDS = 1L
    const val HTTP_REQUEST_TIMEOUT_SECONDS = 2L

    val adbTimeout: Duration = Duration.ofSeconds(ADB_TIMEOUT_SECONDS)
    val httpConnectTimeout: Duration = Duration.ofSeconds(HTTP_CONNECT_TIMEOUT_SECONDS)
    val httpRequestTimeout: Duration = Duration.ofSeconds(HTTP_REQUEST_TIMEOUT_SECONDS)
}
