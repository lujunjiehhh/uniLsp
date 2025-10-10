package com.frenchef.intellijlsp.services

import com.frenchef.intellijlsp.config.LspSettings
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

/**
 * Application-level service that allocates ports for LSP servers across all projects.
 * Prevents port conflicts when multiple projects are open.
 */
object PortAllocator {
    private val allocatedPorts = ConcurrentHashMap<Int, String>()

    /**
     * Allocate an available port starting from the configured starting port.
     * 
     * @param projectName The name of the project requesting the port
     * @return The allocated port number, or null if no port is available
     */
    fun allocatePort(projectName: String): Int? {
        val settings = LspSettings.getInstance()
        var port = settings.startingPort

        // Try up to 100 ports
        repeat(100) {
            if (isPortAvailable(port) && !allocatedPorts.containsKey(port)) {
                allocatedPorts[port] = projectName
                return port
            }
            port++
        }

        return null
    }

    /**
     * Release a previously allocated port.
     * 
     * @param port The port to release
     */
    fun releasePort(port: Int) {
        allocatedPorts.remove(port)
    }

    /**
     * Check if a port is currently allocated.
     * 
     * @param port The port to check
     * @return true if the port is allocated, false otherwise
     */
    fun isAllocated(port: Int): Boolean {
        return allocatedPorts.containsKey(port)
    }

    /**
     * Get the project name that has allocated a port.
     * 
     * @param port The port to query
     * @return The project name, or null if the port is not allocated
     */
    fun getProjectForPort(port: Int): String? {
        return allocatedPorts[port]
    }

    /**
     * Get all allocated ports.
     * 
     * @return A map of port to project name
     */
    fun getAllocatedPorts(): Map<Int, String> {
        return allocatedPorts.toMap()
    }

    /**
     * Check if a port is available (not in use by any process).
     * 
     * @param port The port to check
     * @return true if the port is available, false otherwise
     */
    private fun isPortAvailable(port: Int): Boolean {
        return try {
            ServerSocket(port).use { true }
        } catch (e: Exception) {
            false
        }
    }
}

