package com.ericdevwang.androidinputbridge.plugin.connection

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

class BridgeNetworkConfigTest {
    @Test
    fun usesFixedBridgePortAndTimeouts() {
        assertEquals(18080, BridgeNetworkConfig.PORT)
        assertEquals(Duration.ofSeconds(1), BridgeNetworkConfig.httpConnectTimeout)
        assertEquals(Duration.ofSeconds(2), BridgeNetworkConfig.httpRequestTimeout)
        assertEquals(Duration.ofSeconds(5), BridgeNetworkConfig.adbTimeout)
    }
}
