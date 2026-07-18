package com.ericdevwang.inputbridge.plugin.connection

import com.ericdevwang.inputbridge.protocol.ProtocolConstants
import java.time.Duration

object BridgeNetworkConfig {
    const val HOST = ProtocolConstants.LOCALHOST
    const val PORT = ProtocolConstants.SERVER_PORT
    const val ADB_TIMEOUT_SECONDS = 5L
    const val WEBSOCKET_CONNECT_TIMEOUT_SECONDS = 1L
    const val WEBSOCKET_REQUEST_TIMEOUT_SECONDS = 2L

    val adbTimeout: Duration = Duration.ofSeconds(ADB_TIMEOUT_SECONDS)
    val websocketConnectTimeout: Duration = Duration.ofSeconds(WEBSOCKET_CONNECT_TIMEOUT_SECONDS)
    val websocketRequestTimeout: Duration = Duration.ofSeconds(WEBSOCKET_REQUEST_TIMEOUT_SECONDS)
}
