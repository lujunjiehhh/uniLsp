package com.frenchef.intellijlsp.server

/**
 * Interface for LSP server implementations (TCP or Unix Domain Socket).
 */
interface LspServer {
    /**
     * Start the LSP server.
     */
    fun start()

    /**
     * Stop the LSP server and clean up resources.
     */
    fun stop()

    /**
     * Check if the server is currently running.
     * 
     * @return true if running, false otherwise
     */
    fun isRunning(): Boolean

    /**
     * Get the port number (TCP mode only).
     * 
     * @return The port number, or null if not applicable
     */
    fun getPort(): Int?

    /**
     * Get the socket path (UDS mode only).
     * 
     * @return The socket path, or null if not applicable
     */
    fun getSocketPath(): String?

    /**
     * Get the number of connected clients.
     * 
     * @return The number of connected clients
     */
    fun getClientCount(): Int
}

