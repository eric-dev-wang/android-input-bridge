package com.ericdevwang.androidinputbridge.plugin.toolwindow

import com.ericdevwang.androidinputbridge.plugin.mock.MockBridgeState
import com.ericdevwang.androidinputbridge.plugin.mock.MockBridgeStateStore
import com.ericdevwang.androidinputbridge.plugin.mock.MockConnectionState
import com.intellij.openapi.Disposable
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

class InputBridgePanel(
    private val stateStore: MockBridgeStateStore,
) : JPanel(BorderLayout(0, 12)), Disposable {
    internal val refreshButton = JButton("Refresh")
    internal val copyButton = JButton("Copy")
    internal val copyAndClearButton = JButton("Copy & Clear")
    internal val reconnectButton = JButton("Reconnect")
    internal val textArea = JTextArea()
    internal val feedbackLabel = JLabel()
    internal val statusTextLabel = JLabel()

    private val deviceLabel = JLabel()
    private val adbLabel = JLabel()
    private val forwardLabel = JLabel()
    private val serverLabel = JLabel()
    private val lastRefreshLabel = JLabel()
    private val lengthLabel = JLabel()
    private val versionLabel = JLabel()
    private val listener: (MockBridgeState) -> Unit = ::render

    init {
        border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
        add(createHeader(), BorderLayout.NORTH)
        add(createTextArea(), BorderLayout.CENTER)
        add(createFooter(), BorderLayout.SOUTH)

        refreshButton.addActionListener { stateStore.refresh() }
        copyButton.addActionListener { stateStore.copy() }
        copyAndClearButton.addActionListener { stateStore.copyAndClear() }
        reconnectButton.addActionListener { stateStore.reconnect() }
        stateStore.addListener(listener)
    }

    override fun dispose() {
        stateStore.removeListener(listener)
    }

    private fun createHeader(): JPanel = JPanel(GridBagLayout()).apply {
        val constraints = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = Insets(0, 0, 4, 0)
        }
        add(JLabel("Android Input Bridge").apply {
            font = font.deriveFont(Font.BOLD, 16f)
        }, constraints)
        constraints.gridy = 1
        add(statusTextLabel, constraints)
        constraints.gridy = 2
        add(deviceLabel, constraints)
        constraints.gridy = 3
        add(adbLabel, constraints)
        constraints.gridy = 4
        add(forwardLabel, constraints)
        constraints.gridy = 5
        add(serverLabel, constraints)
        constraints.gridy = 6
        add(lastRefreshLabel, constraints)
    }

    private fun createTextArea(): JScrollPane {
        textArea.isEditable = false
        textArea.lineWrap = true
        textArea.wrapStyleWord = false
        textArea.font = Font(Font.MONOSPACED, Font.PLAIN, 13)
        textArea.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        return JScrollPane(textArea)
    }

    private fun createFooter(): JPanel = JPanel(BorderLayout(0, 8)).apply {
        add(JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.X_AXIS)
            add(lengthLabel)
            add(javax.swing.Box.createHorizontalStrut(16))
            add(versionLabel)
        }, BorderLayout.NORTH)
        add(feedbackLabel, BorderLayout.CENTER)
        add(JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.X_AXIS)
            add(refreshButton)
            add(javax.swing.Box.createHorizontalStrut(8))
            add(copyButton)
            add(javax.swing.Box.createHorizontalStrut(8))
            add(copyAndClearButton)
            add(javax.swing.Box.createHorizontalStrut(8))
            add(reconnectButton)
        }, BorderLayout.SOUTH)
    }

    private fun render(state: MockBridgeState) {
        textArea.text = state.text
        statusTextLabel.text = "Status: ${state.connectionState.displayName}"
        deviceLabel.text = "Device: ${state.deviceSerial ?: "—"}"
        adbLabel.text = "ADB: ${state.adbStatus}"
        forwardLabel.text = "Forward: ${state.forwardStatus}"
        serverLabel.text = "Server: ${state.serverStatus}"
        lastRefreshLabel.text = "Last refresh: ${state.lastRefresh ?: "—"}"
        lengthLabel.text = "Length: ${state.text.length}"
        versionLabel.text = "Version: ${state.version}"
        feedbackLabel.text = state.feedback?.displayText ?: state.errorMessage.orEmpty()

        val isConnected = state.connectionState == MockConnectionState.CONNECTED
        refreshButton.isEnabled = isConnected && !state.isBusy
        copyButton.isEnabled = isConnected && !state.isBusy && state.text.isNotEmpty()
        copyAndClearButton.isEnabled = copyButton.isEnabled
        reconnectButton.isEnabled = !state.isBusy
    }
}
