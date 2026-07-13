package com.ericdevwang.androidinputbridge.plugin.toolwindow

import com.ericdevwang.androidinputbridge.plugin.adb.AdbDevice
import com.ericdevwang.androidinputbridge.plugin.connection.BridgeConnectionController
import com.ericdevwang.androidinputbridge.plugin.connection.BridgeConnectionState
import com.ericdevwang.androidinputbridge.plugin.connection.BridgeState
import com.intellij.openapi.Disposable
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.DefaultListCellRenderer

class InputBridgePanel(
    private val controller: BridgeConnectionController,
) : JPanel(BorderLayout(0, 12)), Disposable {
    internal val refreshButton = JButton("Refresh")
    internal val copyButton = JButton("Copy")
    internal val copyAndClearButton = JButton("Copy & Clear")
    internal val reconnectButton = JButton("Reconnect")
    internal val deviceSelector = JComboBox<AdbDevice>()
    internal val textArea = JTextArea()
    internal val feedbackLabel = JLabel()
    internal val statusTextLabel = JLabel()

    private val deviceLabel = JLabel()
    private val adbLabel = JLabel()
    private val forwardLabel = JLabel()
    private val serverLabel = JLabel()
    internal val lastRefreshLabel = JLabel()
    private val lengthLabel = JLabel()
    private val versionLabel = JLabel()
    private val lastRefreshFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    private var disposed = false
    private var updatingDeviceSelector = false
    private val listener: (BridgeState) -> Unit = ::dispatchRender

    init {
        border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
        add(createHeader(), BorderLayout.NORTH)
        add(createTextArea(), BorderLayout.CENTER)
        add(createFooter(), BorderLayout.SOUTH)

        refreshButton.addActionListener { controller.refresh() }
        reconnectButton.addActionListener { controller.reconnect() }
        deviceSelector.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ) = super.getListCellRendererComponent(
                list,
                (value as? AdbDevice)?.displayName ?: value,
                index,
                isSelected,
                cellHasFocus,
            )
        }
        deviceSelector.addActionListener {
            if (!updatingDeviceSelector) {
                (deviceSelector.selectedItem as? AdbDevice)?.let { controller.selectDevice(it.serial) }
            }
        }
        controller.addListener(listener)
    }

    override fun dispose() {
        disposed = true
        controller.removeListener(listener)
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
        add(deviceSelector, constraints)
        constraints.gridy = 4
        add(adbLabel, constraints)
        constraints.gridy = 5
        add(forwardLabel, constraints)
        constraints.gridy = 6
        add(serverLabel, constraints)
        constraints.gridy = 7
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
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(lengthLabel)
            add(Box.createHorizontalStrut(16))
            add(versionLabel)
        }, BorderLayout.NORTH)
        add(feedbackLabel, BorderLayout.CENTER)
        add(JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(refreshButton)
            add(Box.createHorizontalStrut(8))
            add(copyButton)
            add(Box.createHorizontalStrut(8))
            add(copyAndClearButton)
            add(Box.createHorizontalStrut(8))
            add(reconnectButton)
        }, BorderLayout.SOUTH)
    }

    private fun dispatchRender(state: BridgeState) {
        if (SwingUtilities.isEventDispatchThread()) {
            render(state)
        } else {
            SwingUtilities.invokeLater {
                if (!disposed) render(state)
            }
        }
    }

    private fun render(state: BridgeState) {
        textArea.text = state.text
        statusTextLabel.text = "Status: ${state.connectionState.displayName}"
        deviceLabel.text = "Device: ${state.devices.firstOrNull { it.serial == state.selectedSerial }?.displayName ?: "—"}"
        adbLabel.text = "ADB: ${state.adbStatus}"
        forwardLabel.text = "Forward: ${state.forwardStatus}"
        serverLabel.text = "Server: ${state.serverStatus}"
        lastRefreshLabel.text = "Last refresh: ${state.lastRefresh?.atZone(ZoneId.systemDefault())?.format(lastRefreshFormatter) ?: "—"}"
        lengthLabel.text = "Length: ${state.text.length}"
        versionLabel.text = "Version: ${state.version ?: "—"}"
        feedbackLabel.text = state.errorMessage.orEmpty()

        updatingDeviceSelector = true
        try {
            val selectedSerial = state.selectedSerial
            deviceSelector.removeAllItems()
            state.devices.forEach(deviceSelector::addItem)
            deviceSelector.selectedItem = state.devices.firstOrNull { it.serial == selectedSerial }
        } finally {
            updatingDeviceSelector = false
        }

        refreshButton.isEnabled = state.connectionState == BridgeConnectionState.CONNECTED && !state.isBusy
        copyButton.isEnabled = false
        copyAndClearButton.isEnabled = false
        reconnectButton.isEnabled = !state.isBusy
        deviceSelector.isEnabled = state.devices.isNotEmpty() && !state.isBusy
    }
}
