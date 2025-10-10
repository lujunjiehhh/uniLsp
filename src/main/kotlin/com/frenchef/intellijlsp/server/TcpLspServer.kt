package com.frenchef.intellijlsp.server

import com.frenchef.intellijlsp.protocol.JsonRpcHandler
import com.frenchef.intellijlsp.protocol.MessageReader
import com.frenchef.intellijlsp.protocol.MessageWriter
import com.frenchef.intellijlsp.protocol.models.LspResponse
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * TCP-based LSP server implementation.
 * Listens on localhost only for security.
 */
class TcpLspServer(
    private val project: Project,
    private val port: Int,
    private val jsonRpcHandler: JsonRpcHandler
) : LspServer {
    private val log = logger<TcpLspServer>()
    
    @Volatile
    private var running = false
    
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val clients = ConcurrentHashMap<Int, ClientConnection>()
    private val clientIdCounter = AtomicInteger(0)

    override fun start() {
        if (running) {
            log.warn("Server is already running on port $port")
            return
        }

        try {
            // Bind to localhost only
            serverSocket = ServerSocket(port, 50, InetAddress.getLoopbackAddress())
            running = true
            
            log.info("TCP LSP Server started on port $port for project: ${project.name}")
            
            // Start accepting connections
            acceptJob = scope.launch {
                acceptConnections()
            }
        } catch (e: Exception) {
            log.error("Failed to start TCP server on port $port", e)
            throw e
        }
    }

    override fun stop() {
        if (!running) {
            return
        }

        log.info("Stopping TCP LSP Server on port $port")
        running = false
        
        // Close all client connections
        clients.values.forEach { it.close() }
        clients.clear()
        
        // Close server socket
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            log.warn("Error closing server socket", e)
        }
        
        // Cancel accept job
        acceptJob?.cancel()
        
        // Cancel all coroutines
        scope.cancel()
        
        log.info("TCP LSP Server stopped")
    }

    override fun isRunning(): Boolean = running

    override fun getPort(): Int = port

    override fun getSocketPath(): String? = null

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
        val socket = serverSocket ?: return
        
        while (running && !socket.isClosed) {
            try {
                val clientSocket = withContext(Dispatchers.IO) {
                    socket.accept()
                }
                
                val clientId = clientIdCounter.incrementAndGet()
                val client = ClientConnection(clientId, clientSocket)
                clients[clientId] = client
                
                log.info("Client $clientId connected from ${clientSocket.remoteSocketAddress}")
                
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
            while (running && !client.socket.isClosed) {
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
        val socket: Socket
    ) {
        val reader = MessageReader(socket.getInputStream())
        val writer = MessageWriter(socket.getOutputStream())

        fun close() {
            try {
                socket.close()
            } catch (e: Exception) {
                log.warn("Error closing client $id socket", e)
            }
        }
    }
}

