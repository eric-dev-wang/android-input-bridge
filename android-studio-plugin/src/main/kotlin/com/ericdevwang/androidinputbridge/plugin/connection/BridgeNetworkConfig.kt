package com.ericdevwang.androidinputbridge.plugin.connection

import com.ericdevwang.androidinputbridge.protocol.ProtocolConstants
import java.time.Duration

object BridgeNetworkConfig {
    const val HOST = ProtocolConstants.LOCALHOST
    const val PORT = ProtocolConstants.HTTP_PORT
    const val ADB_TIMEOUT_SECONDS = 5L
    const val HTTP_CONNECT_TIMEOUT_SECONDS = 1L
    const val HTTP_REQUEST_TIMEOUT_SECONDS = 2L

    val adbTimeout: Duration = Duration.ofSeconds(ADB_TIMEOUT_SECONDS)
    val httpConnectTimeout: Duration = Duration.ofSeconds(HTTP_CONNECT_TIMEOUT_SECONDS)
    val httpRequestTimeout: Duration = Duration.ofSeconds(HTTP_REQUEST_TIMEOUT_SECONDS)
}
