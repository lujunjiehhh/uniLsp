package com.frenchef.intellijlsp.server

import com.frenchef.intellijlsp.config.LspSettings
import com.frenchef.intellijlsp.config.TransportMode
import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.services.PortAllocator
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Factory and lifecycle manager for LSP servers.
 */
object LspServerManager {
    private val log = logger<LspServerManager>()

    /**
     * Create and start an LSP server for the given project.
     * 
     * @param project The project to create the server for
     * @param jsonRpcHandler The JSON-RPC handler for this server
     * @return The created and started server, or null if creation failed
     */
    fun createAndStartServer(project: Project, jsonRpcHandler: JsonRpcHandler): LspServer? {
        val settings = LspSettings.getInstance()
        
        return when (settings.transportMode) {
            TransportMode.TCP -> createTcpServer(project, jsonRpcHandler)
            TransportMode.UDS -> createUdsServer(project, jsonRpcHandler)
        }
    }

    /**
     * Create and start a TCP server.
     */
    private fun createTcpServer(project: Project, jsonRpcHandler: JsonRpcHandler): LspServer? {
        val port = PortAllocator.allocatePort(project.name)
        
        if (port == null) {
            log.error("Failed to allocate port for project: ${project.name}")
            return null
        }

        return try {
            val server = TcpLspServer(project, port, jsonRpcHandler)
            server.start()
            log.info("Created TCP server on port $port for project: ${project.name}")
            server
        } catch (e: Exception) {
            log.error("Failed to create TCP server", e)
            PortAllocator.releasePort(port)
            null
        }
    }

    /**
     * Create and start a UDS server.
     */
    private fun createUdsServer(project: Project, jsonRpcHandler: JsonRpcHandler): LspServer? {
        val socketPath = getSocketPath(project)
        
        return try {
            val server = UdsLspServer(project, socketPath, jsonRpcHandler)
            server.start()
            log.info("Created UDS server on socket $socketPath for project: ${project.name}")
            server
        } catch (e: Exception) {
            log.error("Failed to create UDS server", e)
            null
        }
    }

    /**
     * Stop a server and clean up resources.
     * 
     * @param server The server to stop
     */
    fun stopServer(server: LspServer) {
        try {
            // Release port if it's a TCP server
            server.getPort()?.let { port ->
                PortAllocator.releasePort(port)
            }
            
            // Stop the server
            server.stop()
        } catch (e: Exception) {
            log.error("Error stopping server", e)
        }
    }

    /**
     * Get the socket path for a project.
     */
    private fun getSocketPath(project: Project): String {
        val socketDir = File(System.getProperty("user.home"), ".intellij-lsp")
        socketDir.mkdirs()
        
        // Use project base path hash to create a unique socket file
        val projectHash = project.basePath?.hashCode()?.toString(16) ?: "unknown"
        return File(socketDir, "project-$projectHash.sock").absolutePath
    }
}

