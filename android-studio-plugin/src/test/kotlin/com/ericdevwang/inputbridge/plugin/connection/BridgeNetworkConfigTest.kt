package com.ericdevwang.inputbridge.plugin.connection

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

class BridgeNetworkConfigTest {
    @Test
    fun usesFixedBridgePortAndTimeouts() {
        assertEquals(18080, BridgeNetworkConfig.PORT)
        assertEquals(Duration.ofSeconds(1), BridgeNetworkConfig.websocketConnectTimeout)
        assertEquals(Duration.ofSeconds(2), BridgeNetworkConfig.websocketRequestTimeout)
        assertEquals(Duration.ofSeconds(5), BridgeNetworkConfig.adbTimeout)
    }
}
