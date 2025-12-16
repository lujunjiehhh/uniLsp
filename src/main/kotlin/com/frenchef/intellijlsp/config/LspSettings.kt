package com.frenchef.intellijlsp.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent settings for the LSP server plugin.
 */
@State(
    name = "LspSettings",
    storages = [Storage("IntellijLspSettings.xml")]
)
class LspSettings : PersistentStateComponent<LspSettings> {
    /**
     * Transport mode: TCP or Unix Domain Socket.
     */
    var transportMode: TransportMode = TransportMode.TCP

    /**
     * Starting port for TCP mode. Default is 2087.
     */
    var startingPort: Int = 2087

    /**
     * Whether to auto-start the LSP server when a project opens.
     */
    var autoStart: Boolean = true

    /**
     * The formatting style for textDocument/hover responses
     */
    var hoverFormat: HoverFormat = HoverFormat.MARKDOWN

    /**
     * Custom log file path. Empty means use default (~/.intellij-lsp-logs/).
     */
    var logFilePath: String = ""

    override fun getState(): LspSettings {
        return this
    }

    override fun loadState(state: LspSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): LspSettings {
            return ApplicationManager.getApplication().getService(LspSettings::class.java)
        }
    }

    enum class HoverFormat {
        RAW_HTML, MARKDOWN
    }
}

