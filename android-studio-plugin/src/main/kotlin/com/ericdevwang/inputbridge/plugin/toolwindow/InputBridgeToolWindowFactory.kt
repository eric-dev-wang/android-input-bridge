package com.ericdevwang.inputbridge.plugin.toolwindow

import com.ericdevwang.inputbridge.plugin.service.InputBridgeProjectService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class InputBridgeToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val service = project.service<InputBridgeProjectService>()
        val panel = InputBridgePanel(service.connectionController, service.notifier)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
        service.connectionController.reconnect()
    }
}
