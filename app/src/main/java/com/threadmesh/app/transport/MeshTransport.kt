package com.threadmesh.app.transport

import com.threadmesh.app.data.model.EventEntity
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class PeerConnectionStatus {
    DISCOVERED,
    CONNECTING,
    AUTHENTICATION_PENDING,
    CONNECTED,
    REJECTED,
    DISCONNECTED
}

data class DiscoveredPeer(
    val endpointId: String,
    val endpointName: String,
    val status: PeerConnectionStatus,
    val authDigits: String? = null,
    val isIncomingRequest: Boolean = false
)

enum class MeshServiceState {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

/**
 * TransportProvider abstraction.
 * Decouples the forum/thread logic from Google Play Services Nearby Connections.
 * Supports both high-level EventEntity transmission and multi-hop RelayPacket / AckPacket payloads.
 */
interface TransportProvider {
    val serviceState: StateFlow<MeshServiceState>
    val peers: StateFlow<List<DiscoveredPeer>>
    val incomingEvents: SharedFlow<EventEntity>
    val incomingPayloads: SharedFlow<Pair<String, String>> // Pair(fromEndpointId, rawJson)
    val lastError: StateFlow<String?>

    fun start(localEndpointName: String)
    fun stop()
    fun requestConnection(endpointId: String)
    fun acceptConnection(endpointId: String)
    fun rejectConnection(endpointId: String)
    
    // Low-level raw payload transmission (RelayPacket, AckPacket)
    fun sendMeshPayload(endpointId: String, rawJson: String): Result<Unit>
    fun broadcastMeshPayload(rawJson: String): Result<Int>

    // High-level backward-compatible transmission
    fun sendMeshEvent(endpointId: String, event: EventEntity): Result<Unit>
    fun broadcastMeshEvent(event: EventEntity): Result<Int>
}
