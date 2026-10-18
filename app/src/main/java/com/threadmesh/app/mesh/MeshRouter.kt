package com.threadmesh.app.mesh

import com.threadmesh.app.data.local.OutboxDao
import com.threadmesh.app.data.local.ThreadMeshDao
import com.threadmesh.app.data.model.AckPacket
import com.threadmesh.app.data.model.EventEntity
import com.threadmesh.app.data.model.OutboxEntity
import com.threadmesh.app.data.model.OutboxStatus
import com.threadmesh.app.data.model.PublishScope
import com.threadmesh.app.data.model.RelayPacket
import com.threadmesh.app.transport.PeerConnectionStatus
import com.threadmesh.app.transport.TransportProvider
import com.threadmesh.app.security.EventAuthenticator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

data class MeshRouterStats(
    val packetsReceived: Int = 0,
    val packetsStoredLocally: Int = 0,
    val packetsRelayed: Int = 0,
    val acksReceived: Int = 0,
    val acksSent: Int = 0,
    val packetsDroppedLoop: Int = 0,
    val packetsDroppedExpired: Int = 0,
    val packetsDroppedDuplicate: Int = 0,
    val packetsDroppedScope: Int = 0,
    val packetsDroppedSignature: Int = 0
)

class MeshRouter(
    val localNodeId: String,
    private val eventDao: ThreadMeshDao,
    private val outboxDao: OutboxDao,
    private val transport: TransportProvider?,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val eventAuthenticator: EventAuthenticator? = null
) {
    private val _stats = MutableStateFlow(MeshRouterStats())
    val stats: StateFlow<MeshRouterStats> = _stats.asStateFlow()

    private val _relayLog = MutableStateFlow<List<String>>(emptyList())
    val relayLog: StateFlow<List<String>> = _relayLog.asStateFlow()

    init {
        if (transport != null) {
            // 1. Listen to raw transport payloads (RelayPacket and AckPacket)
            scope.launch {
                transport.incomingPayloads.collect { (fromEndpointId, rawJson) ->
                    handleIncomingPayload(rawJson, fromEndpointId)
                }
            }

            // 2. When any peer connects, flush unexpired pending outbox items to that peer
            scope.launch {
                transport.peers.collect { peerList ->
                    val newlyConnected = peerList.filter { it.status == PeerConnectionStatus.CONNECTED }
                    for (peer in newlyConnected) {
                        flushOutboxToPeer(peer.endpointId)
                    }
                }
            }
        }
    }

    private fun log(message: String) {
        _relayLog.update { (listOf(message) + it).take(50) }
    }

    /**
     * Enqueues a locally-created or received event for multi-hop mesh store-and-forward.
     * Enforces:
     * - ONLY MESH_PUBLIC events are placed in outbox.
     * - Preserves immutable event payload.
     * - Preserves outbox persistence across disconnects and restarts.
     */
    suspend fun enqueueForRelay(event: EventEntity, maxHops: Int = RelayPacket.DEFAULT_MAX_HOPS): Result<Boolean> {
        if (event.publishScope != PublishScope.MESH_PUBLIC) {
            _stats.update { it.copy(packetsDroppedScope = it.packetsDroppedScope + 1) }
            log("انتقال لغو شد: رویداد ${event.id.take(8)} دارای دامنه ${event.publishScope} است و وارد مش نمی‌شود.")
            return Result.failure(IllegalArgumentException("فقط رویدادهای MESH_PUBLIC اجازه ورود به صف انتقال مش را دارند."))
        }

        // Check if already in outbox
        val existing = outboxDao.getByEventId(event.id)
        if (existing != null) {
            return Result.success(false)
        }

        val authenticator = eventAuthenticator
            ?: return Result.failure(IllegalStateException("امضای دیجیتال برای انتشار مش پیکربندی نشده است."))
        val packet = RelayPacket.fromEvent(
            event,
            localNodeId,
            maxHops = maxHops,
            signature = authenticator.sign(event)
        )
        val outboxItem = OutboxEntity(
            packetId = packet.packetId,
            eventId = event.id,
            rawJson = packet.toJson(),
            status = OutboxStatus.PENDING,
            hopCount = packet.hopCount,
            expiresAt = packet.expiresAt,
            deliveredPeerIds = ""
        )

        outboxDao.insertOutboxItem(outboxItem)
        log("رویداد ${event.id.take(8)} در صف انتقال چندمرحله‌ای (Outbox) ذخیره شد (پرش: ۰، سقف: $maxHops).")

        // If any peers currently connected, attempt flush
        transport?.let { tp ->
            val connected = tp.peers.value.filter { it.status == PeerConnectionStatus.CONNECTED }
            for (p in connected) {
                flushOutboxToPeer(p.endpointId)
            }
        }

        return Result.success(true)
    }

    /**
     * Dispatches an incoming raw string payload (ACK or RELAY packet).
     */
    suspend fun handleIncomingPayload(rawJson: String, fromEndpointId: String = "") {
        // Check if this payload is an ACK
        val ack = AckPacket.fromJson(rawJson)
        if (ack != null) {
            handleIncomingAck(ack, fromEndpointId)
            return
        }

        // Legacy direct EventEntity JSON is emitted through incomingEvents. Do not parse it
        // again as a relay envelope or produce a misleading malformed-packet error.
        val rawObject = runCatching { JSONObject(rawJson) }.getOrNull()
        if (rawObject != null && !rawObject.has("packetId") && rawObject.has("eventType")) {
            return
        }

        // Otherwise process as RelayPacket
        processIncomingPacket(rawJson, fromEndpointId)
    }

    /**
     * Handles receipt of an application-level ACK confirming successful storage in destination node.
     */
    suspend fun handleIncomingAck(ack: AckPacket, fromEndpointId: String) {
        _stats.update { it.copy(acksReceived = it.acksReceived + 1) }
        // Outbox delivery deduplication uses Nearby endpoint IDs, not logical node IDs.
        val peerIdentifier = if (fromEndpointId.isNotBlank()) fromEndpointId else ack.ackFromNodeId
        log("تأییدیه دریافت و ذخیره در گره مقصد (ACK) از $peerIdentifier برای بسته ${ack.packetId} دریافت شد (وضعیت: ${ack.status}).")

        // Record confirmed delivery for this specific peer without depriving other future peers!
        outboxDao.recordConfirmedPeerDelivery(ack.packetId, peerIdentifier)
    }

    /**
     * Processes an incoming RelayPacket received from another mesh peer.
     * Implements full multi-hop validation:
     * 1. Scope check (rejects non-MESH_PUBLIC)
     * 2. Expiration check (drops stale packets)
     * 3. Loop prevention (drops if visitedNodeIds contains this node)
     * 4. Deduplication (drops duplicate event IDs in local DB)
     * 5. Local storage in Room
     * 6. Return explicit Application ACK to transmitting peer
     * 7. Preparation of next-hop packet and queuing in Outbox for forwarding to next nodes
     */
    suspend fun processIncomingPacket(rawJson: String, fromEndpointId: String = ""): Result<Boolean> {
        _stats.update { it.copy(packetsReceived = it.packetsReceived + 1) }

        val packet: RelayPacket = try {
            RelayPacket.fromJson(rawJson)
        } catch (e: Exception) {
            log("خطا در تجزیه ساختار بسته مش: ${e.message}")
            return Result.failure(e)
        }

        val event = packet.event

        // 1. Scope check
        if (event.publishScope != PublishScope.MESH_PUBLIC) {
            _stats.update { it.copy(packetsDroppedScope = it.packetsDroppedScope + 1) }
            log("بسته دریافتی به دلیل دامنه نامعتبر ${event.publishScope} رد شد.")
            return Result.failure(IllegalArgumentException("رویداد فاقد مجوز MESH_PUBLIC است."))
        }

        // Reject unsigned or modified event content before persistence and relaying.
        val authenticator = eventAuthenticator
        if (authenticator == null || !authenticator.verify(event, packet.signature)) {
            _stats.update { it.copy(packetsDroppedSignature = it.packetsDroppedSignature + 1) }
            log("بسته به‌دلیل نبودن امضای معتبر یا دستکاری محتوا رد شد.")
            return Result.failure(SecurityException("امضای رویداد نامعتبر است."))
        }

        // 2. Expiration check
        if (packet.isExpired()) {
            _stats.update { it.copy(packetsDroppedExpired = it.packetsDroppedExpired + 1) }
            log("بسته ${packet.packetId} منقضی شده است (انقضا: ${packet.expiresAt}). نادیده گرفته شد.")
            return Result.success(false)
        }

        // 3. Loop prevention check
        if (packet.visitedNodeIds.contains(localNodeId)) {
            _stats.update { it.copy(packetsDroppedLoop = it.packetsDroppedLoop + 1) }
            log("حلقه در مسیر انتقال کشف شد: بسته قبلاً از این گره ($localNodeId) عبور کرده بود. نادیده گرفته شد.")
            return Result.success(false)
        }

        // 4. Deduplication check
        val existingLocal = eventDao.getEventById(event.id)
        if (existingLocal != null) {
            _stats.update { it.copy(packetsDroppedDuplicate = it.packetsDroppedDuplicate + 1) }
            log("رویداد تکراری: رویداد ${event.id.take(8)} قبلاً در دیتابیس محلی ذخیره شده است.")
            
            // Send ACK back so sender knows we already have it
            sendAck(fromEndpointId, packet.packetId, event.id, AckPacket.STATUS_DUPLICATE)
            return Result.success(false)
        }

        // 5. Store locally in Room database
        val eventToInsert = if (event.eventType == com.threadmesh.app.data.model.EventType.ROOT_THREAD) {
            event.copy(lastViewedAt = null) // mark unviewed so it surfaces in Recent Changes
        } else {
            event
        }
        val insertResult = eventDao.insertEvent(eventToInsert)
        if (insertResult <= 0L) {
            // Another collector may have inserted this event after the initial existence check.
            _stats.update { it.copy(packetsDroppedDuplicate = it.packetsDroppedDuplicate + 1) }
            sendAck(fromEndpointId, packet.packetId, event.id, AckPacket.STATUS_DUPLICATE)
            return Result.success(false)
        }
        _stats.update { it.copy(packetsStoredLocally = it.packetsStoredLocally + 1) }
        log("پیام [${event.displayName}]: «${event.text.take(30)}...» با پرش ${packet.hopCount} در پایگاه محلی ذخیره شد.")

        // 6. Send application-level confirmation ACK back to transmitting peer
        sendAck(fromEndpointId, packet.packetId, event.id, AckPacket.STATUS_STORED)

        // 7. Store-and-Forward: can this packet hop further?
        if (packet.canForward(localNodeId)) {
            val nextHopPacket = packet.nextHop(localNodeId)
            val outboxItem = OutboxEntity(
                packetId = nextHopPacket.packetId,
                eventId = event.id,
                rawJson = nextHopPacket.toJson(),
                status = OutboxStatus.PENDING,
                hopCount = nextHopPacket.hopCount,
                expiresAt = nextHopPacket.expiresAt,
                deliveredPeerIds = if (fromEndpointId.isNotBlank()) fromEndpointId else ""
            )
            outboxDao.insertOutboxItem(outboxItem)
            log("بسته برای بازنشر به گره‌های بعدی در صف Outbox ذخیره شد (پرش بعدی: ${nextHopPacket.hopCount} از ${nextHopPacket.maxHops}).")

            // Attempt relay to currently connected peers (excluding sender)
            transport?.let { tp ->
                val otherPeers = tp.peers.value
                    .filter { it.status == PeerConnectionStatus.CONNECTED && it.endpointId != fromEndpointId }
                for (p in otherPeers) {
                    flushOutboxToPeer(p.endpointId)
                }
            }
        } else {
            log("بسته به سقف پرش مجاز (${packet.hopCount}/${packet.maxHops}) رسید و دیگر رله نمی‌شود.")
        }

        return Result.success(true)
    }

    private fun sendAck(toEndpointId: String, packetId: String, eventId: String, status: String) {
        if (toEndpointId.isBlank() || transport == null) return
        val ack = AckPacket(
            packetId = packetId,
            eventId = eventId,
            ackFromNodeId = localNodeId,
            status = status
        )
        transport.sendMeshPayload(toEndpointId, ack.toJson())
        _stats.update { it.copy(acksSent = it.acksSent + 1) }
        log("تأییدیه دریافت (ACK) به همتای $toEndpointId ارسال شد.")
    }

    /**
     * Backward-compatible helper for incoming raw EventEntity from transport.
     */
    suspend fun processIncomingEvent(event: EventEntity, fromEndpointId: String = ""): Result<Boolean> {
        // Signing an unsigned inbound event here would falsely claim that this relay authored it.
        return Result.failure(SecurityException("رویداد قدیمی فاقد پاکت امضاشده است و پذیرفته نمی‌شود."))
    }

    /**
     * Transmits all pending unexpired outbox items to a connected peer.
     * KEY FIX: Does NOT permanently mark packets as RELAYED in a way that stops other peers!
     * Instead, tracks per-peer delivery so newly connected devices (e.g. Node D) still receive
     * the message even after Node C has already received it.
     */
    suspend fun flushOutboxToPeer(peerEndpointId: String): Int {
        val now = System.currentTimeMillis()
        val pending = outboxDao.getPendingPackets(now)
        var relayedCount = 0

        for (item in pending) {
            val packet = runCatching { RelayPacket.fromJson(item.rawJson) }.getOrNull() ?: continue

            // 1. Skip if this specific peer was already confirmed as delivered
            if (item.hasDeliveredTo(peerEndpointId)) {
                continue
            }

            // 2. Skip if peer has already been visited in packet loop trace
            if (packet.visitedNodeIds.contains(peerEndpointId)) {
                continue
            }

            // 3. Transmit full packet envelope (preserving all hopCount, visitedNodeIds, and TTL)
            val res = transport?.sendMeshPayload(peerEndpointId, item.rawJson)
                ?: transport?.sendMeshEvent(peerEndpointId, packet.event)

            if (res?.isSuccess == true) {
                // Record provisional relay attempt. When application ACK returns, confirmed delivery is recorded.
                outboxDao.markRelayed(item.packetId, now)
                relayedCount++
                _stats.update { it.copy(packetsRelayed = it.packetsRelayed + 1) }
                log("بسته ${item.packetId} با موفقیت به همتای $peerEndpointId ارسال شد (در انتظار ACK تأیید مقصد).")
            }
        }

        return relayedCount
    }
}
