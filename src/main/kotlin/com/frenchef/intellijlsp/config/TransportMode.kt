package com.frenchef.intellijlsp.config

/**
 * Transport mode for the LSP server.
 */
enum class TransportMode {
    /**
     * TCP socket server (localhost only).
     */
    TCP,

    /**
     * Unix Domain Socket.
     */
    UDS
}

