package com.frenchef.intellijlsp.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.frenchef.intellijlsp.server.LspServer
import com.frenchef.intellijlsp.server.LspServerManager

/**
 * Project-level service that manages the LSP server lifecycle for a single project.
 * Each project has its own instance of this service and its own LSP server.
 */
@Service(Service.Level.PROJECT)
class LspProjectService(private val project: Project) : Disposable {
    private val log = logger<LspProjectService>()
    private var server: LspServer? = null

    /**
     * Get the current server status as a string.
     */
    fun getServerStatus(): String {
        val srv = server ?: return "Not started"
        
        return if (srv.isRunning()) {
            val port = srv.getPort()
            val socketPath = srv.getSocketPath()
            val clientCount = srv.getClientCount()
            
            when {
                port != null -> "Running on port $port ($clientCount client(s))"
                socketPath != null -> "Running on socket $socketPath ($clientCount client(s))"
                else -> "Running"
            }
        } else {
            "Stopped"
        }
    }

    /**
     * Set the server instance (called by LspServerStartupActivity).
     */
    fun setServer(server: LspServer) {
        this.server = server
    }

    /**
     * Get the server instance.
     */
    fun getServer(): LspServer? {
        return server
    }

    override fun dispose() {
        log.info("Disposing LspProjectService for project: ${project.name}")
        val srv = server
        if (srv != null) {
            LspServerManager.stopServer(srv)
            server = null
        }
    }
}

