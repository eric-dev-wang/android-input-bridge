package com.ericdevwang.androidinputbridge.plugin.service

import com.ericdevwang.androidinputbridge.plugin.mock.MockBridgeStateStore
import com.intellij.openapi.Disposable

class InputBridgeProjectService : Disposable {
    val mockStateStore = MockBridgeStateStore()

    override fun dispose() {
        mockStateStore.dispose()
    }
}
