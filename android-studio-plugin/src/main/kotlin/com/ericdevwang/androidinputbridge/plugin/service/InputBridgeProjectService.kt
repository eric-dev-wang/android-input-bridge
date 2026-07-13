package com.ericdevwang.androidinputbridge.plugin.service

import com.ericdevwang.androidinputbridge.plugin.adb.AdbLocator
import com.ericdevwang.androidinputbridge.plugin.adb.ProcessAdbClient
import com.ericdevwang.androidinputbridge.plugin.adb.RandomDeviceSelector
import com.ericdevwang.androidinputbridge.plugin.clipboard.IntellijClipboardWriter
import com.ericdevwang.androidinputbridge.plugin.connection.BridgeConnectionController
import com.ericdevwang.androidinputbridge.plugin.connection.BridgeConnectionCoordinator
import com.ericdevwang.androidinputbridge.plugin.http.JdkHttpProbeClient
import com.ericdevwang.androidinputbridge.plugin.http.JdkHttpProbeTransport
import com.ericdevwang.androidinputbridge.plugin.notifications.InputBridgeNotifier
import com.ericdevwang.androidinputbridge.plugin.notifications.IntelliJInputBridgeNotifier
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil

class InputBridgeProjectService(
    project: Project,
) : Disposable {
    val notifier: InputBridgeNotifier = IntelliJInputBridgeNotifier(project)

    val connectionController: BridgeConnectionController = BridgeConnectionCoordinator(
        adbLocator = AdbLocator.forProject(project),
        adbClientFactory = { adbPath -> ProcessAdbClient(adbPath) },
        httpProbeClientFactory = {
            JdkHttpProbeClient(JdkHttpProbeTransport.create())
        },
        deviceSelector = RandomDeviceSelector(),
        executor = AppExecutorUtil.getAppExecutorService(),
        clipboardWriter = IntellijClipboardWriter(),
    )

    override fun dispose() {
        connectionController.dispose()
    }
}
