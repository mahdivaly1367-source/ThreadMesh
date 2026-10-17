package com.threadmesh.app.transport

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.threadmesh.app.data.model.EventEntity
import com.threadmesh.app.data.model.EventType
import com.threadmesh.app.data.model.PublishScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class GoogleNearbyTransport(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : TransportProvider {

    companion object {
        const val SERVICE_ID = "com.threadmesh.app.mesh.p2p"
        const val MAX_TEXT_LENGTH = 4000
    }

    private val connectionsClient: ConnectionsClient by lazy {
        Nearby.getConnectionsClient(context.applicationContext)
    }

    private val _serviceState = MutableStateFlow(MeshServiceState.STOPPED)
    override val serviceState: StateFlow<MeshServiceState> = _serviceState.asStateFlow()

    private val _peers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    override val peers: StateFlow<List<DiscoveredPeer>> = _peers.asStateFlow()

    private val _incomingEvents = MutableSharedFlow<EventEntity>(replay = 0, extraBufferCapacity = 64)
    override val incomingEvents: SharedFlow<EventEntity> = _incomingEvents.asSharedFlow()

    private val _incomingPayloads = MutableSharedFlow<Pair<String, String>>(replay = 0, extraBufferCapacity = 64)
    override val incomingPayloads: SharedFlow<Pair<String, String>> = _incomingPayloads.asSharedFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var localName: String = "گره ناشناس"

    // Callback for discovery
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            _peers.update { list ->
                if (list.none { it.endpointId == endpointId }) {
                    list + DiscoveredPeer(
                        endpointId = endpointId,
                        endpointName = info.endpointName,
                        status = PeerConnectionStatus.DISCOVERED
                    )
                } else list
            }
        }

        override fun onEndpointLost(endpointId: String) {
            _peers.update { list -> list.filterNot { it.endpointId == endpointId } }
        }
    }

    // Callback for connection lifecycle
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            val authCode = connectionInfo.authenticationDigits
            _peers.update { list ->
                val existing = list.firstOrNull { it.endpointId == endpointId }
                val updated = DiscoveredPeer(
                    endpointId = endpointId,
                    endpointName = connectionInfo.endpointName,
                    status = PeerConnectionStatus.AUTHENTICATION_PENDING,
                    authDigits = authCode,
                    isIncomingRequest = connectionInfo.isIncomingConnection
                )
                if (existing != null) {
                    list.map { if (it.endpointId == endpointId) updated else it }
                } else {
                    list + updated
                }
            }
            // Wait for explicit user action acceptConnection(endpointId)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val isSuccess = result.status.isSuccess
            _peers.update { list ->
                list.map { peer ->
                    if (peer.endpointId == endpointId) {
                        peer.copy(
                            status = if (isSuccess) PeerConnectionStatus.CONNECTED else PeerConnectionStatus.REJECTED
                        )
                    } else peer
                }
            }
            if (!isSuccess) {
                _lastError.value = "اتصال به همتا ناموفق بود (کد: ${result.status.statusCode})"
            }
        }

        override fun onDisconnected(endpointId: String) {
            _peers.update { list ->
                list.map { peer ->
                    if (peer.endpointId == endpointId) {
                        peer.copy(status = PeerConnectionStatus.DISCONNECTED)
                    } else peer
                }
            }
        }
    }

    // Callback for payload reception
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            try {
                val jsonString = String(bytes, StandardCharsets.UTF_8)
                
                // 1. Emit raw payload to MeshRouter for packet handling (RelayPacket / AckPacket)
                scope.launch {
                    _incomingPayloads.emit(Pair(endpointId, jsonString))
                }

                // 2. Also handle legacy direct EventEntity payload if present
                val json = JSONObject(jsonString)
                if (!json.has("packetId") && json.has("eventType")) {
                    val scopeString = json.optString("publishScope", "")
                    if (scopeString != PublishScope.MESH_PUBLIC.name) return

                    val text = json.optString("text", "").trim()
                    if (text.isEmpty() || text.length > MAX_TEXT_LENGTH) return

                    val event = EventEntity(
                        id = json.getString("id"),
                        localAuthorId = json.getString("localAuthorId"),
                        displayName = json.getString("displayName"),
                        createdAt = json.getLong("createdAt"),
                        eventType = EventType.valueOf(json.getString("eventType")),
                        rootThreadId = json.getString("rootThreadId"),
                        parentEventId = if (json.has("parentEventId") && !json.isNull("parentEventId")) json.getString("parentEventId") else null,
                        targetEventId = if (json.has("targetEventId") && !json.isNull("targetEventId")) json.getString("targetEventId") else null,
                        text = text,
                        publishScope = PublishScope.MESH_PUBLIC,
                        lastViewedAt = null
                    )

                    scope.launch {
                        _incomingEvents.emit(event)
                    }
                }
            } catch (e: Exception) {
                _lastError.value = "خطا در پردازش بسته داده دریافتی: ${e.message}"
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    override fun start(localEndpointName: String) {
        if (_serviceState.value == MeshServiceState.RUNNING || _serviceState.value == MeshServiceState.STARTING) return
        this.localName = localEndpointName
        _serviceState.value = MeshServiceState.STARTING
        _lastError.value = null

        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startAdvertising(
            localEndpointName,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            connectionsClient.startDiscovery(
                SERVICE_ID,
                endpointDiscoveryCallback,
                discoveryOptions
            ).addOnSuccessListener {
                _serviceState.value = MeshServiceState.RUNNING
            }.addOnFailureListener { e ->
                _serviceState.value = MeshServiceState.ERROR
                _lastError.value = "خطا در آغاز کشف همتاها: ${e.localizedMessage}"
            }
        }.addOnFailureListener { e ->
            _serviceState.value = MeshServiceState.ERROR
            _lastError.value = "خطا در آغاز تبلیغ گره: ${e.localizedMessage}"
        }
    }

    override fun stop() {
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
        } catch (_: Exception) {}

        _serviceState.value = MeshServiceState.STOPPED
        _peers.value = emptyList()
    }

    override fun requestConnection(endpointId: String) {
        _peers.update { list ->
            list.map {
                if (it.endpointId == endpointId) it.copy(status = PeerConnectionStatus.CONNECTING) else it
            }
        }
        connectionsClient.requestConnection(localName, endpointId, connectionLifecycleCallback)
            .addOnFailureListener { e ->
                _lastError.value = "درخواست اتصال ناموفق بود: ${e.localizedMessage}"
                _peers.update { list ->
                    list.map {
                        if (it.endpointId == endpointId) it.copy(status = PeerConnectionStatus.REJECTED) else it
                    }
                }
            }
    }

    override fun acceptConnection(endpointId: String) {
        connectionsClient.acceptConnection(endpointId, payloadCallback)
            .addOnFailureListener { e ->
                _lastError.value = "خطا در پذیرش اتصال: ${e.localizedMessage}"
            }
    }

    override fun rejectConnection(endpointId: String) {
        connectionsClient.rejectConnection(endpointId)
        _peers.update { list ->
            list.map {
                if (it.endpointId == endpointId) it.copy(status = PeerConnectionStatus.REJECTED) else it
            }
        }
    }

    override fun sendMeshPayload(endpointId: String, rawJson: String): Result<Unit> {
        return try {
            val bytes = rawJson.toByteArray(StandardCharsets.UTF_8)
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun broadcastMeshPayload(rawJson: String): Result<Int> {
        val connectedPeers = _peers.value.filter { it.status == PeerConnectionStatus.CONNECTED }
        if (connectedPeers.isEmpty()) return Result.success(0)

        var count = 0
        for (peer in connectedPeers) {
            val res = sendMeshPayload(peer.endpointId, rawJson)
            if (res.isSuccess) count++
        }
        return Result.success(count)
    }

    override fun sendMeshEvent(endpointId: String, event: EventEntity): Result<Unit> {
        if (event.publishScope != PublishScope.MESH_PUBLIC) {
            return Result.failure(IllegalArgumentException("تنها رویدادهای با دامنه MESH_PUBLIC اجازه ارسال به شبکه دارند."))
        }

        if (event.text.length > MAX_TEXT_LENGTH) {
            return Result.failure(IllegalArgumentException("طول متن بیش از حد مجاز ۴۰۰۰ نویسه است."))
        }

        return try {
            val json = JSONObject().apply {
                put("id", event.id)
                put("localAuthorId", event.localAuthorId)
                put("displayName", event.displayName)
                put("createdAt", event.createdAt)
                put("eventType", event.eventType.name)
                put("rootThreadId", event.rootThreadId)
                put("parentEventId", event.parentEventId ?: JSONObject.NULL)
                put("targetEventId", event.targetEventId ?: JSONObject.NULL)
                put("text", event.text)
                put("publishScope", event.publishScope.name)
            }
            sendMeshPayload(endpointId, json.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun broadcastMeshEvent(event: EventEntity): Result<Int> {
        if (event.publishScope != PublishScope.MESH_PUBLIC) {
            return Result.failure(IllegalArgumentException("تنها رویدادهای با دامنه MESH_PUBLIC اجازه ارسال به شبکه دارند."))
        }

        val connectedPeers = _peers.value.filter { it.status == PeerConnectionStatus.CONNECTED }
        if (connectedPeers.isEmpty()) return Result.success(0)

        var count = 0
        for (peer in connectedPeers) {
            val res = sendMeshEvent(peer.endpointId, event)
            if (res.isSuccess) count++
        }
        return Result.success(count)
    }
}
