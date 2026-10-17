package com.threadmesh.app.transport

import com.threadmesh.app.data.model.EventEntity
import com.threadmesh.app.data.model.PublishScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SimulatedNearbyTransport(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : TransportProvider {

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

    private var localName: String = "گره شبیه‌ساز"

    override fun start(localEndpointName: String) {
        this.localName = localEndpointName
        _serviceState.value = MeshServiceState.RUNNING
        _lastError.value = null

        // Simulate discovering a nearby peer after 1 second
        scope.launch {
            delay(1200)
            if (_serviceState.value == MeshServiceState.RUNNING) {
                _peers.update { list ->
                    list + DiscoveredPeer(
                        endpointId = "peer_test_01",
                        endpointName = "دستگاه آزمایشی همسایه (Galaxy S23)",
                        status = PeerConnectionStatus.DISCOVERED
                    )
                }
            }
        }
    }

    override fun stop() {
        _serviceState.value = MeshServiceState.STOPPED
        _peers.value = emptyList()
    }

    override fun requestConnection(endpointId: String) {
        _peers.update { list ->
            list.map {
                if (it.endpointId == endpointId) {
                    it.copy(
                        status = PeerConnectionStatus.AUTHENTICATION_PENDING,
                        authDigits = "4892",
                        isIncomingRequest = false
                    )
                } else it
            }
        }
    }

    override fun acceptConnection(endpointId: String) {
        _peers.update { list ->
            list.map {
                if (it.endpointId == endpointId) {
                    it.copy(status = PeerConnectionStatus.CONNECTED)
                } else it
            }
        }
    }

    override fun rejectConnection(endpointId: String) {
        _peers.update { list ->
            list.map {
                if (it.endpointId == endpointId) {
                    it.copy(status = PeerConnectionStatus.REJECTED)
                } else it
            }
        }
    }

    override fun sendMeshPayload(endpointId: String, rawJson: String): Result<Unit> {
        return Result.success(Unit)
    }

    override fun broadcastMeshPayload(rawJson: String): Result<Int> {
        val count = _peers.value.count { it.status == PeerConnectionStatus.CONNECTED }
        return Result.success(count)
    }

    override fun sendMeshEvent(endpointId: String, event: EventEntity): Result<Unit> {
        if (event.publishScope != PublishScope.MESH_PUBLIC) {
            return Result.failure(IllegalArgumentException("تنها رویدادهای MESH_PUBLIC اجازه ارسال دارند."))
        }
        if (event.text.length > 4000) {
            return Result.failure(IllegalArgumentException("طول متن بیش از ۴۰۰۰ نویسه است."))
        }
        return Result.success(Unit)
    }

    override fun broadcastMeshEvent(event: EventEntity): Result<Int> {
        if (event.publishScope != PublishScope.MESH_PUBLIC) {
            return Result.failure(IllegalArgumentException("تنها رویدادهای MESH_PUBLIC اجازه ارسال دارند."))
        }
        val count = _peers.value.count { it.status == PeerConnectionStatus.CONNECTED }
        return Result.success(count)
    }

    fun emitSimulatedIncoming(event: EventEntity) {
        scope.launch {
            _incomingEvents.emit(event)
        }
    }

    fun emitSimulatedPayload(endpointId: String, rawJson: String) {
        scope.launch {
            _incomingPayloads.emit(Pair(endpointId, rawJson))
        }
    }
}
