package com.frenchef.intellijlsp.config

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.frenchef.intellijlsp.services.LspProjectService
import javax.swing.*

/**
 * Settings UI for the LSP server plugin.
 */
class LspConfigurable : Configurable {
    private var tcpRadioButton: JBRadioButton? = null
    private var udsRadioButton: JBRadioButton? = null
    private var portField: JBTextField? = null
    private var autoStartCheckBox: JCheckBox? = null
    private var hoverFormatHtmlButton: JBRadioButton? = null
    private var hoverFormatMarkdownButton: JBRadioButton? = null
    private var statusLabel: JLabel? = null

    override fun getDisplayName(): String {
        return "IntelliJ LSP Server"
    }

    override fun createComponent(): JComponent {
        val settings = LspSettings.getInstance()

        // Transport mode selection
        tcpRadioButton = JBRadioButton("TCP Socket", settings.transportMode == TransportMode.TCP)
        udsRadioButton = JBRadioButton("Unix Domain Socket", settings.transportMode == TransportMode.UDS)
        
        val transportGroup = ButtonGroup()
        transportGroup.add(tcpRadioButton)
        transportGroup.add(udsRadioButton)
        
        // Create a horizontal panel for the radio buttons
        val transportPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(tcpRadioButton)
            add(Box.createHorizontalStrut(10)) // Add spacing between buttons
            add(udsRadioButton)
        }

        // Port configuration
        portField = JBTextField(settings.startingPort.toString(), 10)
        portField?.isEnabled = settings.transportMode == TransportMode.TCP

        // Listen to transport mode changes to enable/disable port field
        tcpRadioButton?.addActionListener {
            portField?.isEnabled = tcpRadioButton?.isSelected == true
        }
        udsRadioButton?.addActionListener {
            portField?.isEnabled = tcpRadioButton?.isSelected == true
        }

        // Auto-start option
        autoStartCheckBox = JCheckBox("Auto-start server when project opens", settings.autoStart)

        // Hover format selection
        hoverFormatHtmlButton = JBRadioButton("Raw HTML", settings.hoverFormat == LspSettings.HoverFormat.RAW_HTML)
        hoverFormatMarkdownButton = JBRadioButton("Markdown", settings.hoverFormat == LspSettings.HoverFormat.MARKDOWN)

        val hoverFormatButtonGroup = ButtonGroup()
        hoverFormatButtonGroup.add(hoverFormatHtmlButton)
        hoverFormatButtonGroup.add(hoverFormatMarkdownButton)

        // Create a horizontal panel for the radio buttons
        val hoverFormatPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(hoverFormatHtmlButton)
            add(Box.createHorizontalStrut(10)) // Add spacing between buttons
            add(hoverFormatMarkdownButton)
        }

        // Status display
        statusLabel = JLabel(getServerStatusText())

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Transport Mode:", transportPanel)
            .addSeparator()
            .addLabeledComponent("Starting Port (TCP):", portField!!)
            .addTooltip("The server will try this port first, then increment if unavailable.")
            .addSeparator()
            .addComponent(autoStartCheckBox!!)
            .addSeparator()
            .addLabeledComponent("Hover formatting style:", hoverFormatPanel)
            .addSeparator()
            .addLabeledComponent("Server Status:", statusLabel!!)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val settings = LspSettings.getInstance()
        
        val selectedMode = getSelectedTransportMode()
        val portValue = portField?.text?.toIntOrNull() ?: settings.startingPort
        val autoStart = autoStartCheckBox?.isSelected ?: settings.autoStart
        val hoverFormat = getSelectedHoverFormat()

        return selectedMode != settings.transportMode ||
                portValue != settings.startingPort ||
                autoStart != settings.autoStart ||
                hoverFormat != settings.hoverFormat
    }

    override fun apply() {
        val settings = LspSettings.getInstance()
        
        settings.transportMode = getSelectedTransportMode()
        settings.startingPort = portField?.text?.toIntOrNull() ?: 2087
        settings.autoStart = autoStartCheckBox?.isSelected ?: true
        settings.hoverFormat = getSelectedHoverFormat()

        // Update status display
        statusLabel?.text = getServerStatusText()
    }

    override fun reset() {
        val settings = LspSettings.getInstance()
        
        tcpRadioButton?.isSelected = settings.transportMode == TransportMode.TCP
        udsRadioButton?.isSelected = settings.transportMode == TransportMode.UDS
        portField?.text = settings.startingPort.toString()
        portField?.isEnabled = settings.transportMode == TransportMode.TCP
        autoStartCheckBox?.isSelected = settings.autoStart
        hoverFormatHtmlButton?.isSelected = settings.hoverFormat == LspSettings.HoverFormat.RAW_HTML
        hoverFormatMarkdownButton?.isSelected = settings.hoverFormat == LspSettings.HoverFormat.MARKDOWN
        statusLabel?.text = getServerStatusText()
    }

    private fun getServerStatusText(): String {
        val projectManager = ProjectManager.getInstance()
        val openProjects = projectManager.openProjects
        
        if (openProjects.isEmpty()) {
            return "No projects open"
        }

        val statusLines = mutableListOf<String>()
        for (project in openProjects) {
            val service = project.getService(LspProjectService::class.java)
            val status = service?.getServerStatus() ?: "Not started"
            statusLines.add("${project.name}: $status")
        }

        return "<html>${statusLines.joinToString("<br>")}</html>"
    }

    private fun getSelectedTransportMode(): TransportMode =
        if (tcpRadioButton?.isSelected == true) TransportMode.TCP else TransportMode.UDS

    private fun getSelectedHoverFormat(): LspSettings.HoverFormat =
        if (hoverFormatHtmlButton?.isSelected == true) LspSettings.HoverFormat.RAW_HTML else LspSettings.HoverFormat.MARKDOWN
}

