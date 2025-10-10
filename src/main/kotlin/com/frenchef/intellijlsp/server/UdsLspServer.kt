package com.frenchef.intellijlsp.server

import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.MessageReader
import com.frenchef.intellijlsp.protocol.MessageWriter
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unix Domain Socket-based LSP server implementation.
 */
class UdsLspServer(
    private val project: Project,
    private val socketPath: String,
    private val jsonRpcHandler: JsonRpcHandler
) : LspServer {
    private val log = logger<UdsLspServer>()
    
    @Volatile
    private var running = false
    
    private var serverChannel: ServerSocketChannel? = null
    private var acceptJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val clients = ConcurrentHashMap<Int, ClientConnection>()
    private val clientIdCounter = AtomicInteger(0)

    override fun start() {
        if (running) {
            log.warn("Server is already running on socket $socketPath")
            return
        }

        try {
            // Create parent directory if it doesn't exist
            val socketFile = File(socketPath)
            socketFile.parentFile?.mkdirs()
            
            // Remove existing socket file if it exists
            if (socketFile.exists()) {
                socketFile.delete()
            }
            
            // Create Unix domain socket server
            val address = UnixDomainSocketAddress.of(socketPath)
            serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
            serverChannel?.bind(address)
            
            // Set permissions to user-only (0600)
            try {
                val path = socketFile.toPath()
                val permissions = setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
                )
                Files.setPosixFilePermissions(path, permissions)
            } catch (e: Exception) {
                log.warn("Could not set socket file permissions", e)
            }
            
            running = true
            log.info("UDS LSP Server started on socket $socketPath for project: ${project.name}")
            
            // Start accepting connections
            acceptJob = scope.launch {
                acceptConnections()
            }
        } catch (e: Exception) {
            log.error("Failed to start UDS server on socket $socketPath", e)
            throw e
        }
    }

    override fun stop() {
        if (!running) {
            return
        }

        log.info("Stopping UDS LSP Server on socket $socketPath")
        running = false
        
        // Close all client connections
        clients.values.forEach { it.close() }
        clients.clear()
        
        // Close server channel
        try {
            serverChannel?.close()
        } catch (e: Exception) {
            log.warn("Error closing server channel", e)
        }
        
        // Delete socket file
        try {
            File(socketPath).delete()
        } catch (e: Exception) {
            log.warn("Error deleting socket file", e)
        }
        
        // Cancel accept job
        acceptJob?.cancel()
        
        // Cancel all coroutines
        scope.cancel()
        
        log.info("UDS LSP Server stopped")
    }

    override fun isRunning(): Boolean = running

    override fun getPort(): Int? = null

    override fun getSocketPath(): String = socketPath

    override fun getClientCount(): Int = clients.size

    /**
     * Send a message to all connected clients.
     */
    fun broadcast(message: Any) {
        clients.values.forEach { client ->
            try {
                client.writer.writeMessage(message)
            } catch (e: Exception) {
                log.warn("Error broadcasting to client ${client.id}", e)
            }
        }
    }

    /**
     * Accept incoming client connections.
     */
    private suspend fun acceptConnections() {
        val channel = serverChannel ?: return
        
        while (running && channel.isOpen) {
            try {
                val clientChannel = withContext(Dispatchers.IO) {
                    channel.accept()
                }
                
                val clientId = clientIdCounter.incrementAndGet()
                val client = ClientConnection(clientId, clientChannel)
                clients[clientId] = client
                
                log.info("Client $clientId connected via UDS")
                
                // Handle this client in a separate coroutine
                scope.launch {
                    handleClient(client)
                }
            } catch (e: Exception) {
                if (running) {
                    log.error("Error accepting connection", e)
                }
            }
        }
    }

    /**
     * Handle messages from a connected client.
     */
    private suspend fun handleClient(client: ClientConnection) {
        try {
            while (running && client.channel.isOpen) {
                val message = withContext(Dispatchers.IO) {
                    client.reader.readMessage()
                } ?: break
                
                log.info("Received message from client ${client.id}: ${message.get("method")?.asString ?: "unknown"}")
                
                // Process the message
                val response = jsonRpcHandler.handleMessage(message)
                
                // Send response if it's a request (not a notification)
                if (response != null) {
                    log.info("Sending response to client ${client.id} for id: ${response.id}")
                    withContext(Dispatchers.IO) {
                        client.writer.writeMessage(response)
                    }
                    log.info("Response sent successfully to client ${client.id}")
                } else {
                    log.debug("No response needed (notification)")
                }
            }
        } catch (e: Exception) {
            if (running) {
                log.error("Error handling client ${client.id}", e)
            }
        } finally {
            log.info("Client ${client.id} disconnected")
            clients.remove(client.id)
            client.close()
        }
    }

    /**
     * Represents a connected client.
     */
    private inner class ClientConnection(
        val id: Int,
        val channel: SocketChannel
    ) {
        val reader = MessageReader(channel.socket().getInputStream())
        val writer = MessageWriter(channel.socket().getOutputStream())

        fun close() {
            try {
                channel.close()
            } catch (e: Exception) {
                log.warn("Error closing client $id channel", e)
            }
        }
    }
}

