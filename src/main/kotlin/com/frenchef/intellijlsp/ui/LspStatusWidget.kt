package com.frenchef.intellijlsp.ui

import com.frenchef.intellijlsp.services.LspProjectService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.Consumer
import java.awt.Component
import java.awt.event.MouseEvent
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Status bar widget factory for the LSP server.
 */
class LspStatusWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "LspServerStatus"

    override fun getDisplayName(): String = "LSP Server Status"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget {
        return LspStatusWidget(project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

/**
 * Status bar widget that displays LSP server status.
 * Updates every 2 seconds to reflect current state.
 */
class LspStatusWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.TextPresentation {
    private var statusBar: StatusBar? = null
    private val updateExecutor = Executors.newSingleThreadScheduledExecutor()

    override fun ID(): String = "LspServerStatus"

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        
        // Schedule periodic updates every 2 seconds
        updateExecutor.scheduleAtFixedRate({
            updateWidget()
        }, 0, 2, TimeUnit.SECONDS)
    }

    override fun dispose() {
        updateExecutor.shutdown()
        this.statusBar = null
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation {
        return this
    }

    override fun getText(): String {
        val projectService = project.getService(LspProjectService::class.java)
        val server = projectService?.getServer()
        
        if (server == null) {
            return "LSP: Not started"
        }
        
        return if (server.isRunning()) {
            val port = server.getPort()
            val socketPath = server.getSocketPath()
            val clientCount = server.getClientCount()
            
            when {
                port != null -> "LSP: :$port ($clientCount)"
                socketPath != null -> "LSP: UDS ($clientCount)"
                else -> "LSP: Running"
            }
        } else {
            "LSP: Stopped"
        }
    }

    override fun getAlignment(): Float = Component.CENTER_ALIGNMENT

    override fun getTooltipText(): String? {
        val projectService = project.getService(LspProjectService::class.java)
        val server = projectService?.getServer()
        
        if (server == null) {
            return "LSP server is not started"
        }
        
        if (!server.isRunning()) {
            return "LSP server is stopped"
        }
        
        val port = server.getPort()
        val socketPath = server.getSocketPath()
        val clientCount = server.getClientCount()
        
        return when {
            port != null -> "LSP server running on TCP port $port\n$clientCount client(s) connected"
            socketPath != null -> "LSP server running on Unix socket\n$socketPath\n$clientCount client(s) connected"
            else -> "LSP server is running"
        }
    }

    override fun getClickConsumer(): Consumer<MouseEvent>? {
        return Consumer { _ ->
            // Could open settings or tool window on click
            // For now, just trigger an immediate update
            updateWidget()
        }
    }

    /**
     * Update the widget display.
     */
    private fun updateWidget() {
        ApplicationManager.getApplication().invokeLater {
            val bar = statusBar ?: return@invokeLater
            bar.updateWidget(ID())
        }
    }
}

