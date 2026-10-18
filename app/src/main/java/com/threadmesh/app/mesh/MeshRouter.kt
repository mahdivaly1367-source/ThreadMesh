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
import com.threadmesh.app.security.PeerIdentityProtocol
import com.threadmesh.app.security.PeerIdentityMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
    val packetsDroppedSignature: Int = 0,
    val packetsDroppedMalformedRoute: Int = 0
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

    private val peerAuthLock = Any()
    private data class PendingPeerChallenge(val nonce: String, val createdAt: Long)
    private val pendingPeerChallenges = mutableMapOf<String, PendingPeerChallenge>()
    private val _authenticatedPeerIdentities = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Nearby endpoint ID -> verified public-key fingerprint for this live connection. */
    val authenticatedPeerIdentities: StateFlow<Map<String, String>> = _authenticatedPeerIdentities.asStateFlow()

    companion object {
        /** Wait before retrying the same packet to the same peer after a missing ACK. */
        internal const val PEER_RETRY_INTERVAL_MILLIS = 30_000L
        private const val RETRY_SCAN_INTERVAL_MILLIS = 5_000L
    }

    init {
        if (transport != null) {
            // 1. Listen to raw transport payloads (RelayPacket and AckPacket)
            scope.launch {
                transport.incomingPayloads.collect { (fromEndpointId, rawJson) ->
                    handleIncomingPayload(rawJson, fromEndpointId)
                }
            }

            // Release queued content only after the live endpoint proves control of its key.
            scope.launch {
                transport.peers.collect { peerList ->
                    val connectedIds = peerList
                        .filter { it.status == PeerConnectionStatus.CONNECTED }
                        .map { it.endpointId }
                        .toSet()
                    synchronized(peerAuthLock) {
                        pendingPeerChallenges.keys.retainAll(connectedIds)
                        _authenticatedPeerIdentities.value =
                            _authenticatedPeerIdentities.value.filterKeys { it in connectedIds }
                    }
                    for (peer in peerList.filter { it.status == PeerConnectionStatus.CONNECTED }) {
                        if (_authenticatedPeerIdentities.value[peer.endpointId] == null) {
                            startPeerAuthentication(peer.endpointId)
                        } else {
                            flushOutboxToPeer(peer.endpointId)
                        }
                    }
                }
            }

            // Keep retrying packets whose ACK was lost while the peer remains connected.
            // Per-peer timestamps prevent a busy peer from blocking first delivery to a new peer.
            scope.launch {
                while (true) {
                    delay(RETRY_SCAN_INTERVAL_MILLIS)
                    val peers = transport.peers.value
                        .filter { it.status == PeerConnectionStatus.CONNECTED }
                    if (peers.isEmpty()) {
                        outboxDao.markExpiredPackets(System.currentTimeMillis())
                    } else {
                        for (peer in peers) {
                            flushOutboxToPeer(peer.endpointId)
                        }
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
                if (_authenticatedPeerIdentities.value[p.endpointId] == null) {
                    startPeerAuthentication(p.endpointId)
                } else {
                    flushOutboxToPeer(p.endpointId)
                }
            }
        }

        return Result.success(true)
    }

    /**
     * Dispatches an incoming raw string payload (ACK or RELAY packet).
     */
    suspend fun handleIncomingPayload(rawJson: String, fromEndpointId: String = "") {
        // Refuse oversized frames before feeding JSON parsers (covers relay envelopes and ACKs).
        if (rawJson.toByteArray(Charsets.UTF_8).size > RelayPacket.MAX_ENVELOPE_BYTES) {
            _stats.update { it.copy(packetsDroppedMalformedRoute = it.packetsDroppedMalformedRoute + 1) }
            log("بستهٔ دریافتی از سقف اندازهٔ مجاز بیشتر بود و رد شد.")
            return
        }

        val rawObject = runCatching { JSONObject(rawJson) }.getOrNull()
        when (rawObject?.optString("type")) {
            PeerIdentityProtocol.CHALLENGE_TYPE -> {
                val challenge = PeerIdentityProtocol.parse(rawJson) as? PeerIdentityMessage.Challenge
                if (challenge == null) rejectPeerAuth("challenge malformed")
                else handlePeerIdentityChallenge(challenge, fromEndpointId)
                return
            }
            PeerIdentityProtocol.RESPONSE_TYPE -> {
                val response = PeerIdentityProtocol.parse(rawJson) as? PeerIdentityMessage.Response
                if (response == null) rejectPeerAuth("response malformed")
                else handlePeerIdentityResponse(response, fromEndpointId)
                return
            }
        }

        val ack = AckPacket.fromJson(rawJson)
        if (ack != null) {
            handleIncomingAck(ack, fromEndpointId)
            return
        }

        // Legacy direct EventEntity JSON is ignored; only signed relay envelopes are accepted.
        if (rawObject != null && !rawObject.has("packetId") && rawObject.has("eventType")) return

        // No relay data is processed until this endpoint proves possession of the signing key.
        if (!isPeerAuthenticated(fromEndpointId)) {
            _stats.update { it.copy(packetsDroppedSignature = it.packetsDroppedSignature + 1) }
            log("بسته از اتصال احراز هویت‌نشده رد شد؛ ابتدا باید کلید گرهٔ مقابل تأیید شود.")
            return
        }

        processIncomingPacket(rawJson, fromEndpointId)
    }

    private fun rejectPeerAuth(reason: String) {
        _stats.update { it.copy(packetsDroppedSignature = it.packetsDroppedSignature + 1) }
        log("پیام احراز هویت همتا رد شد: $reason.")
    }

    private fun isConnectedPeer(endpointId: String): Boolean =
        endpointId.isNotBlank() && transport?.peers?.value?.any {
            it.endpointId == endpointId && it.status == PeerConnectionStatus.CONNECTED
        } == true

    private fun isPeerAuthenticated(endpointId: String): Boolean =
        endpointId.isNotBlank() && _authenticatedPeerIdentities.value.containsKey(endpointId)

    /**
     * Starts a proof-of-possession exchange for one live Nearby endpoint. A fingerprint is a key
     * identity, not proof of the owner's human identity.
     */
    internal fun startPeerAuthentication(endpointId: String): Boolean {
        val auth = eventAuthenticator ?: return false
        val activeTransport = transport ?: return false
        if (!isConnectedPeer(endpointId) || isPeerAuthenticated(endpointId)) return false

        val now = System.currentTimeMillis()
        val challenge = synchronized(peerAuthLock) {
            val pending = pendingPeerChallenges[endpointId]
            if (pending != null && now - pending.createdAt < PeerIdentityProtocol.CHALLENGE_TIMEOUT_MILLIS) {
                return false
            }
            PeerIdentityProtocol.createChallenge(auth).also {
                pendingPeerChallenges[endpointId] = PendingPeerChallenge(it.nonce, now)
            }
        }
        val sent = activeTransport.sendMeshPayload(endpointId, challenge.toJson())
        if (sent.isSuccess) {
            log("آغاز احراز هویت رمزنگاری‌شده برای همتای $endpointId.")
            return true
        }
        synchronized(peerAuthLock) {
            if (pendingPeerChallenges[endpointId]?.nonce == challenge.nonce) pendingPeerChallenges.remove(endpointId)
        }
        log("ارسال چالش احراز هویت به $endpointId ناموفق بود.")
        return false
    }

    private fun handlePeerIdentityChallenge(
        challenge: PeerIdentityMessage.Challenge,
        fromEndpointId: String
    ) {
        val auth = eventAuthenticator
        val activeTransport = transport
        if (auth == null || activeTransport == null || !isConnectedPeer(fromEndpointId)) {
            rejectPeerAuth("connection unavailable")
            return
        }
        if (challenge.senderNodeId == auth.localIdentityId() || !PeerIdentityProtocol.verify(challenge, auth)) {
            rejectPeerAuth("invalid signature or self-identity")
            return
        }
        val existingIdentity = _authenticatedPeerIdentities.value[fromEndpointId]
        if (existingIdentity != null && existingIdentity != challenge.senderNodeId) {
            rejectPeerAuth("identity changed during a live connection")
            return
        }

        val response = PeerIdentityProtocol.createResponse(auth, challenge.nonce)
        if (activeTransport.sendMeshPayload(fromEndpointId, response.toJson()).isSuccess) {
            log("پاسخ رمزنگاری‌شده به چالش $fromEndpointId ارسال شد.")
        } else {
            log("پاسخ احراز هویت به $fromEndpointId ارسال نشد.")
        }

        // Answering their challenge authenticates us to them, not the reverse. Require their
        // response to our own unpredictable nonce before trusting this endpoint.
        if (!isPeerAuthenticated(fromEndpointId)) startPeerAuthentication(fromEndpointId)
    }

    private suspend fun handlePeerIdentityResponse(
        response: PeerIdentityMessage.Response,
        fromEndpointId: String
    ) {
        val auth = eventAuthenticator
        if (auth == null || !isConnectedPeer(fromEndpointId)) {
            rejectPeerAuth("connection unavailable")
            return
        }
        if (response.senderNodeId == auth.localIdentityId() || !PeerIdentityProtocol.verify(response, auth)) {
            rejectPeerAuth("invalid response signature")
            return
        }

        val now = System.currentTimeMillis()
        val pending = synchronized(peerAuthLock) { pendingPeerChallenges[fromEndpointId] }
        if (
            pending == null ||
            now - pending.createdAt > PeerIdentityProtocol.CHALLENGE_TIMEOUT_MILLIS ||
            response.challengeNonce != pending.nonce
        ) {
            rejectPeerAuth("challenge mismatch or expired")
            return
        }

        var identityConflict = false
        synchronized(peerAuthLock) {
            val current = pendingPeerChallenges[fromEndpointId]
            if (current == null || current.nonce != response.challengeNonce) return
            val existingIdentity = _authenticatedPeerIdentities.value[fromEndpointId]
            if (existingIdentity != null && existingIdentity != response.senderNodeId) {
                pendingPeerChallenges.remove(fromEndpointId)
                identityConflict = true
            } else {
                pendingPeerChallenges.remove(fromEndpointId)
                _authenticatedPeerIdentities.value =
                    _authenticatedPeerIdentities.value + (fromEndpointId to response.senderNodeId)
            }
        }
        if (identityConflict) {
            rejectPeerAuth("identity changed during a live connection")
            return
        }

        log("هویت رمزنگاری‌شدهٔ همتای " + fromEndpointId + " تأیید شد (" + response.senderNodeId.take(12) + "…).")
        flushOutboxToPeer(fromEndpointId)
    }

    /**
     * Handles receipt of an application-level ACK confirming successful storage in destination node.
     */
    suspend fun handleIncomingAck(ack: AckPacket, fromEndpointId: String) {
        val authenticator = eventAuthenticator
        val signature = ack.signature
        val boundIdentity = _authenticatedPeerIdentities.value[fromEndpointId]
        if (
            authenticator == null ||
            signature == null ||
            boundIdentity == null ||
            boundIdentity != ack.ackFromNodeId ||
            ack.ackFromNodeId != signature.signerKeyId ||
            !authenticator.verifyPayload(ack.canonicalBytes(), signature)
        ) {
            _stats.update { it.copy(packetsDroppedSignature = it.packetsDroppedSignature + 1) }
            log("تأییدیهٔ دریافت به‌دلیل امضای نامعتبر رد شد.")
            return
        }
        if (ack.status != AckPacket.STATUS_STORED && ack.status != AckPacket.STATUS_DUPLICATE) {
            _stats.update { it.copy(packetsDroppedMalformedRoute = it.packetsDroppedMalformedRoute + 1) }
            log("تأییدیه با وضعیت ناشناخته رد شد.")
            return
        }
        if (fromEndpointId.isBlank()) {
            _stats.update { it.copy(packetsDroppedMalformedRoute = it.packetsDroppedMalformedRoute + 1) }
            log("تأییدیه بدون شناسهٔ اتصال معتبر رد شد.")
            return
        }
        val outgoing = outboxDao.getByPacketId(ack.packetId)
        if (outgoing == null || outgoing.eventId != ack.eventId) {
            _stats.update { it.copy(packetsDroppedMalformedRoute = it.packetsDroppedMalformedRoute + 1) }
            log("تأییدیه‌ای که با بستهٔ ارسالی تطبیق ندارد رد شد.")
            return
        }

        _stats.update { it.copy(acksReceived = it.acksReceived + 1) }
        // Record by the exact Nearby endpoint ID used by sendMeshPayload.
        log("تأییدیهٔ امضاشده از $fromEndpointId برای بسته ${ack.packetId} دریافت شد.")
        outboxDao.recordConfirmedPeerDelivery(ack.packetId, fromEndpointId)
    }

    /**
     * Queue the next hop durably. Do this before acknowledging receipt so the sender is not told
     * the message has been accepted by this relay until the forwarding responsibility is stored.
     */
    private suspend fun queueNextHop(packet: RelayPacket, fromEndpointId: String): Boolean {
        if (!packet.canForward(localNodeId)) return false
        val next = packet.nextHop(localNodeId)
        val outboxItem = OutboxEntity(
            packetId = next.packetId,
            eventId = next.event.id,
            rawJson = next.toJson(),
            status = OutboxStatus.PENDING,
            hopCount = next.hopCount,
            expiresAt = next.expiresAt,
            deliveredPeerIds = fromEndpointId
        )
        val inserted = outboxDao.insertOutboxItem(outboxItem)
        if (inserted >= 0L) {
            log("بستهٔ پرش بعدی در صف پایدار ثبت شد (${next.hopCount}/${next.maxHops}).")
        }
        return inserted >= 0L
    }

    private suspend fun flushOtherConnectedPeers(exceptEndpointId: String) {
        val peers = transport?.peers?.value
            ?.filter { it.status == PeerConnectionStatus.CONNECTED && it.endpointId != exceptEndpointId }
            .orEmpty()
        for (peer in peers) {
            if (isPeerAuthenticated(peer.endpointId)) flushOutboxToPeer(peer.endpointId)
            else startPeerAuthentication(peer.endpointId)
        }
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

        if (rawJson.toByteArray(Charsets.UTF_8).size > RelayPacket.MAX_ENVELOPE_BYTES) {
            _stats.update { it.copy(packetsDroppedMalformedRoute = it.packetsDroppedMalformedRoute + 1) }
            return Result.failure(IllegalArgumentException("بسته از سقف اندازهٔ مجاز بیشتر است."))
        }

        val packet: RelayPacket = try {
            RelayPacket.fromJson(rawJson)
        } catch (e: Exception) {
            log("خطا در تجزیه ساختار بسته مش: ${e.message}")
            return Result.failure(e)
        }

        val event = packet.event

        // Bound decoded fields before cryptographic verification, database writes, or relay.
        if (!packet.hasValidEventMetadata()) {
            _stats.update { it.copy(packetsDroppedMalformedRoute = it.packetsDroppedMalformedRoute + 1) }
            log("بسته دارای فیلدهای محتوایی نامعتبر یا بیش از حد مجاز است.")
            return Result.failure(IllegalArgumentException("فرادادهٔ محتوایی رویداد نامعتبر است."))
        }

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

        // Route header fields can be altered in transit, so validate their bounds independently.
        if (!packet.hasValidRoutingMetadata()) {
            _stats.update { it.copy(packetsDroppedMalformedRoute = it.packetsDroppedMalformedRoute + 1) }
            log("بسته به‌دلیل ناسازگاری شناسه، مسیر، شمارش پرش یا محدودیت عمر رد شد.")
            return Result.failure(IllegalArgumentException("فرادادهٔ مسیریابی نامعتبر است."))
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

            // Crash recovery: an event row may have been committed just before an earlier process
            // death prevented the associated forwarding row from being written.
            val queued = queueNextHop(packet, fromEndpointId)
            if (queued) flushOtherConnectedPeers(fromEndpointId)

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

        // Persist forwarding responsibility before acknowledging durable receipt.
        val queuedForNextHop = queueNextHop(packet, fromEndpointId)
        sendAck(fromEndpointId, packet.packetId, event.id, AckPacket.STATUS_STORED)

        if (queuedForNextHop) {
            flushOtherConnectedPeers(fromEndpointId)
        } else {
            log("بسته به سقف پرش مجاز (${packet.hopCount}/${packet.maxHops}) رسید یا از قبل در صف ثبت شده بود.")
        }

        return Result.success(true)
    }

    private fun sendAck(toEndpointId: String, packetId: String, eventId: String, status: String) {
        if (toEndpointId.isBlank() || transport == null) return
        if (!isPeerAuthenticated(toEndpointId)) {
            log("تأییدیه برای اتصال احراز هویت‌نشده ارسال نشد.")
            return
        }
        val authenticator = eventAuthenticator ?: return
        val unsignedAck = AckPacket(
            packetId = packetId,
            eventId = eventId,
            ackFromNodeId = authenticator.localIdentityId(),
            status = status
        )
        val ack = unsignedAck.copy(signature = authenticator.signPayload(unsignedAck.canonicalBytes()))
        val result = transport.sendMeshPayload(toEndpointId, ack.toJson())
        if (result.isSuccess) {
            _stats.update { it.copy(acksSent = it.acksSent + 1) }
            log("تأییدیهٔ امضاشده به همتای $toEndpointId ارسال شد.")
        } else {
            log("ارسال تأییدیه به همتای $toEndpointId ناموفق بود؛ دریافت‌کننده می‌تواند در ارسال بعدی تلاش کند.")
        }
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
        if (!isConnectedPeer(peerEndpointId)) return 0
        if (!isPeerAuthenticated(peerEndpointId)) {
            startPeerAuthentication(peerEndpointId)
            return 0
        }

        val now = System.currentTimeMillis()
        outboxDao.markExpiredPackets(now)
        val pending = outboxDao.getPendingPackets(now)
        var relayedCount = 0

        for (item in pending) {
            val packet = runCatching { RelayPacket.fromJson(item.rawJson) }.getOrNull()
            if (packet == null) {
                outboxDao.markDroppedPacket(item.packetId)
                log("بستهٔ خراب ${item.packetId} از صف انتقال کنار گذاشته شد.")
                continue
            }

            val authenticator = eventAuthenticator
            if (authenticator == null || !authenticator.verify(packet.event, packet.signature)) {
                outboxDao.markDroppedPacket(item.packetId)
                _stats.update { it.copy(packetsDroppedSignature = it.packetsDroppedSignature + 1) }
                log("بستهٔ بدون امضای معتبر از صف انتقال کنار گذاشته شد.")
                continue
            }
            if (!packet.hasValidRoutingMetadata(now)) {
                outboxDao.markDroppedPacket(item.packetId)
                _stats.update { it.copy(packetsDroppedMalformedRoute = it.packetsDroppedMalformedRoute + 1) }
                log("بستهٔ دارای فرادادهٔ مسیریابی نامعتبر از صف انتقال کنار گذاشته شد.")
                continue
            }

            // 1. Skip if this specific peer was already confirmed as delivered.
            if (item.hasDeliveredTo(peerEndpointId)) {
                continue
            }

            // Endpoint IDs are transport-local and cannot be compared to stable node IDs in
            // visitedNodeIds. The receiver checks its own stable node ID before accepting.

            // 2. Retry this packet only after the per-peer ACK timeout has elapsed.
            val lastAttemptAt = item.lastAttemptAtFor(peerEndpointId)
            if (lastAttemptAt > 0L && now - lastAttemptAt < PEER_RETRY_INTERVAL_MILLIS) {
                continue
            }

            // Persist attempt before sending so a missing ACK or transient failure is retried
            // with a delay. The transaction preserves a concurrently received ACK.
            outboxDao.recordPeerRelayAttempt(item.packetId, peerEndpointId, now)

            // 3. Transmit full packet envelope (preserving hopCount, visitedNodeIds, and TTL).
            val res = transport?.sendMeshPayload(peerEndpointId, item.rawJson)
                ?: transport?.sendMeshEvent(peerEndpointId, packet.event)

            if (res?.isSuccess == true) {
                // Successful transmission metric. Delivery is confirmed only by a signed ACK.
                outboxDao.markRelayed(item.packetId, now)
                relayedCount++
                _stats.update { it.copy(packetsRelayed = it.packetsRelayed + 1) }
                log("بسته ${item.packetId} با موفقیت به همتای $peerEndpointId ارسال شد (در انتظار ACK تأیید مقصد).")
            }
        }

        return relayedCount
    }
}
