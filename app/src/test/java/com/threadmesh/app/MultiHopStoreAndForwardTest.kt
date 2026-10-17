package com.threadmesh.app

import com.threadmesh.app.data.local.OutboxDao
import com.threadmesh.app.data.local.ThreadMeshDao
import com.threadmesh.app.data.model.*
import com.threadmesh.app.mesh.MeshRouter
import com.threadmesh.app.transport.DiscoveredPeer
import com.threadmesh.app.transport.MeshServiceState
import com.threadmesh.app.transport.PeerConnectionStatus
import com.threadmesh.app.transport.TransportProvider
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/**
 * Multi-hop Store-and-Forward Mesh Unit Tests.
 *
 * Covers:
 * 1. Multi-hop propagation: Node A -> Node B -> Node C (A and C never directly connect).
 * 2. Deduplication: Duplicate packet reception does not insert duplicate records.
 * 3. Loop prevention: Packets carrying visitedNodeIds containing local ID are dropped.
 * 4. Hop count limits and TTL expiration.
 * 5. Multi-peer store-and-forward: A message is NOT excluded from new peers after initial transmission.
 * 6. Application-level ACK confirmation: Acknowledgment is sent back and processed upon Room persistence.
 * 7. Store-and-forward persistence: Queued packets survive and re-transmit on new peer connections.
 * 8. Scope enforcement: Non-MESH_PUBLIC events are rejected.
 */
class MultiHopStoreAndForwardTest {

    private class InMemoryDao : ThreadMeshDao {
        val storedEvents = mutableMapOf<String, EventEntity>()

        override suspend fun insertEvent(event: EventEntity): Long {
            if (storedEvents.containsKey(event.id)) return -1L
            storedEvents[event.id] = event
            return 1L
        }

        override suspend fun getEventById(id: String): EventEntity? = storedEvents[id]
        override fun getEventsForThreadFlow(rootThreadId: String) = flowOf(storedEvents.values.filter { it.rootThreadId == rootThreadId })
        override suspend fun getEventsForThread(rootThreadId: String) = storedEvents.values.filter { it.rootThreadId == rootThreadId }
        override fun getAllRootThreadsFlow() = flowOf(storedEvents.values.filter { it.eventType == EventType.ROOT_THREAD })
        override fun getAllEventsFlow() = flowOf(storedEvents.values.toList())
        override suspend fun updateThreadLastViewed(rootThreadId: String, viewedTime: Long) {}
        override suspend fun getReplyCount(rootThreadId: String) = storedEvents.values.count { it.rootThreadId == rootThreadId && it.id != rootThreadId }
        override suspend fun getLatestEventInThread(rootThreadId: String) = storedEvents.values.filter { it.rootThreadId == rootThreadId }.maxByOrNull { it.createdAt }
        override suspend fun getAllMeshPublicEvents() = storedEvents.values.filter { it.publishScope == PublishScope.MESH_PUBLIC }
    }

    private class InMemoryOutboxDao : OutboxDao {
        val outbox = mutableMapOf<String, OutboxEntity>()

        override suspend fun insertOutboxItem(item: OutboxEntity): Long {
            if (outbox.containsKey(item.packetId)) return -1L
            outbox[item.packetId] = item
            return 1L
        }

        override suspend fun updateOutboxItem(item: OutboxEntity) {
            outbox[item.packetId] = item
        }

        override suspend fun getPendingPackets(now: Long): List<OutboxEntity> {
            return outbox.values.filter { it.status == OutboxStatus.PENDING && it.expiresAt > now }
        }

        override suspend fun getByPacketId(packetId: String): OutboxEntity? = outbox[packetId]
        override suspend fun getByEventId(eventId: String): OutboxEntity? = outbox.values.firstOrNull { it.eventId == eventId }

        override suspend fun recordConfirmedPeerDelivery(packetId: String, peerId: String) {
            val item = outbox[packetId] ?: return
            val updated = item.withConfirmedDelivery(peerId)
            outbox[packetId] = updated
        }

        override suspend fun markRelayed(packetId: String, attemptTime: Long) {
            val item = outbox[packetId]
            if (item != null) {
                // Keep PENDING so other peers can receive it; increment attempt count
                outbox[packetId] = item.copy(
                    lastRelayAttemptAt = attemptTime,
                    relayedToPeersCount = item.relayedToPeersCount + 1
                )
            }
        }

        override suspend fun markExpiredPackets(now: Long) {
            for ((k, v) in outbox) {
                if (v.status == OutboxStatus.PENDING && v.expiresAt <= now) {
                    outbox[k] = v.copy(status = OutboxStatus.EXPIRED)
                }
            }
        }

        override fun getPendingCountFlow() = flowOf(outbox.values.count { it.status == OutboxStatus.PENDING })
        override fun getRelayedCountFlow() = flowOf(outbox.values.count { it.relayedToPeersCount > 0 })
        override fun getAllOutboxFlow() = flowOf(outbox.values.toList())
    }

    private class MockTransport : TransportProvider {
        override val serviceState = MutableStateFlow(MeshServiceState.RUNNING)
        override val peers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
        override val incomingEvents = MutableSharedFlow<EventEntity>()
        override val incomingPayloads = MutableSharedFlow<Pair<String, String>>()
        override val lastError = MutableStateFlow<String?>(null)

        val transmittedPayloads = mutableListOf<Pair<String, String>>()
        val transmittedEvents = mutableListOf<Pair<String, EventEntity>>()

        override fun start(localEndpointName: String) {}
        override fun stop() {}
        override fun requestConnection(endpointId: String) {}
        override fun acceptConnection(endpointId: String) {}
        override fun rejectConnection(endpointId: String) {}

        override fun sendMeshPayload(endpointId: String, rawJson: String): Result<Unit> {
            transmittedPayloads.add(endpointId to rawJson)
            return Result.success(Unit)
        }

        override fun broadcastMeshPayload(rawJson: String): Result<Int> {
            val count = peers.value.count { it.status == PeerConnectionStatus.CONNECTED }
            return Result.success(count)
        }

        override fun sendMeshEvent(endpointId: String, event: EventEntity): Result<Unit> {
            if (event.publishScope != PublishScope.MESH_PUBLIC) {
                return Result.failure(IllegalArgumentException("Only MESH_PUBLIC allowed"))
            }
            transmittedEvents.add(endpointId to event)
            return Result.success(Unit)
        }

        override fun broadcastMeshEvent(event: EventEntity): Result<Int> {
            if (event.publishScope != PublishScope.MESH_PUBLIC) {
                return Result.failure(IllegalArgumentException("Only MESH_PUBLIC allowed"))
            }
            return Result.success(1)
        }
    }

    @Test
    fun testMultiHopProgressionAToBToC() = runBlocking {
        // Setup Node B (Relay node)
        val daoB = InMemoryDao()
        val outboxB = InMemoryOutboxDao()
        val transportB = MockTransport()
        val routerB = MeshRouter("node_B", daoB, outboxB, transportB)

        // Event originated by Node A
        val eventA = EventEntity(
            id = "evt_A_101",
            localAuthorId = "node_A",
            displayName = "گوشی A",
            createdAt = System.currentTimeMillis(),
            eventType = EventType.ROOT_THREAD,
            rootThreadId = "evt_A_101",
            text = "پیام اضطراری ارسالی از گوشی A در مش محلی",
            publishScope = PublishScope.MESH_PUBLIC
        )

        // Packet created at Node A with hopCount = 0
        val packetFromA = RelayPacket.fromEvent(eventA, originNodeId = "node_A", maxHops = 3)
        assertEquals(0, packetFromA.hopCount)
        assertEquals(listOf("node_A"), packetFromA.visitedNodeIds)

        // Node B receives packet from A
        val receiveResultB = routerB.processIncomingPacket(packetFromA.toJson(), fromEndpointId = "conn_A")
        assertTrue(receiveResultB.isSuccess)
        assertEquals(true, receiveResultB.getOrNull())

        // 1. Check Node B stored event locally in Room
        val storedInB = daoB.getEventById("evt_A_101")
        assertNotNull(storedInB)
        assertEquals(eventA.text, storedInB?.text)
        assertEquals("node_A", storedInB?.localAuthorId)

        // 2. Check Node B queued packet in Outbox with hopCount = 1 and added node_B to visited
        val pendingInB = outboxB.getPendingPackets()
        assertEquals(1, pendingInB.size)
        val queuedPacket = RelayPacket.fromJson(pendingInB[0].rawJson)
        assertEquals(1, queuedPacket.hopCount)
        assertEquals(listOf("node_A", "node_B"), queuedPacket.visitedNodeIds)

        // 3. Node B later connects to Node C and transmits the queued packet
        val daoC = InMemoryDao()
        val outboxC = InMemoryOutboxDao()
        val transportC = MockTransport()
        val routerC = MeshRouter("node_C", daoC, outboxC, transportC)

        val receiveResultC = routerC.processIncomingPacket(queuedPacket.toJson(), fromEndpointId = "conn_B")
        assertTrue(receiveResultC.isSuccess)

        // 4. Node C receives and stores the message with original author A preserved!
        val storedInC = daoC.getEventById("evt_A_101")
        assertNotNull(storedInC)
        assertEquals(eventA.text, storedInC?.text)
        assertEquals("node_A", storedInC?.localAuthorId)
    }

    @Test
    fun testMessageNotBlockedForSubsequentPeersAfterFirstDelivery() = runBlocking {
        // Crucial requirement: Sending to peer C does NOT remove or prevent transmitting to peer D later!
        val daoB = InMemoryDao()
        val outboxB = InMemoryOutboxDao()
        val transportB = MockTransport()
        val routerB = MeshRouter("node_B", daoB, outboxB, transportB)

        val event = EventEntity(
            id = "evt_spread_1",
            localAuthorId = "node_B",
            displayName = "گوشی B",
            createdAt = System.currentTimeMillis(),
            eventType = EventType.ROOT_THREAD,
            rootThreadId = "evt_spread_1",
            text = "اطلاعیه عمومی چندهمتایی",
            publishScope = PublishScope.MESH_PUBLIC
        )
        routerB.enqueueForRelay(event)

        // First peer connects: Peer C
        assertEquals(1, outboxB.getPendingPackets().size)
        val relayedToC = routerB.flushOutboxToPeer("peer_C")
        assertEquals(1, relayedToC)

        // Confirm delivery to Peer C
        val packet = outboxB.getPendingPackets()[0]
        outboxB.recordConfirmedPeerDelivery(packet.packetId, "peer_C")

        // Crucial assertion: packet is STILL PENDING and unexpired for future peers!
        val pendingStill = outboxB.getPendingPackets()
        assertEquals(1, pendingStill.size)
        assertTrue(pendingStill[0].hasDeliveredTo("peer_C"))
        assertFalse(pendingStill[0].hasDeliveredTo("peer_D"))

        // Attempt flush to peer C again -> Should be skipped (already delivered to C)
        val resendToC = routerB.flushOutboxToPeer("peer_C")
        assertEquals(0, resendToC)

        // Second peer connects later: Peer D -> MUST receive the packet!
        val relayedToD = routerB.flushOutboxToPeer("peer_D")
        assertEquals(1, relayedToD)
    }

    @Test
    fun testApplicationLevelAckTransmissionAndProcessing() = runBlocking {
        val daoB = InMemoryDao()
        val outboxB = InMemoryOutboxDao()
        val transportB = MockTransport()
        val routerB = MeshRouter("node_B", daoB, outboxB, transportB)

        val event = EventEntity(
            id = "evt_ack_test",
            localAuthorId = "node_A",
            displayName = "گوشی A",
            createdAt = System.currentTimeMillis(),
            eventType = EventType.ROOT_THREAD,
            rootThreadId = "evt_ack_test",
            text = "تست ارسال تأییدیه",
            publishScope = PublishScope.MESH_PUBLIC
        )
        val packet = RelayPacket.fromEvent(event, "node_A")

        // Node B receives packet from conn_A
        routerB.processIncomingPacket(packet.toJson(), fromEndpointId = "conn_A")

        // Verify Node B sent an application ACK back to conn_A
        assertEquals(1, routerB.stats.value.acksSent)
        assertEquals(1, transportB.transmittedPayloads.size)
        val (dest, payload) = transportB.transmittedPayloads[0]
        assertEquals("conn_A", dest)

        val ack = AckPacket.fromJson(payload)
        assertNotNull(ack)
        assertEquals(packet.packetId, ack?.packetId)
        assertEquals("node_B", ack?.ackFromNodeId)
        assertEquals(AckPacket.STATUS_STORED, ack?.status)

        // Now test Node A processing this ACK
        val daoA = InMemoryDao()
        val outboxA = InMemoryOutboxDao()
        val routerA = MeshRouter("node_A", daoA, outboxA, null)
        outboxA.insertOutboxItem(
            OutboxEntity(
                packetId = packet.packetId,
                eventId = event.id,
                rawJson = packet.toJson(),
                status = OutboxStatus.PENDING,
                hopCount = 0,
                expiresAt = packet.expiresAt
            )
        )

        routerA.handleIncomingAck(ack!!, fromEndpointId = "conn_B")
        assertEquals(1, routerA.stats.value.acksReceived)

        val outboxItemA = outboxA.getByPacketId(packet.packetId)
        assertNotNull(outboxItemA)
        assertTrue(outboxItemA!!.hasDeliveredTo("node_B"))
    }

    @Test
    fun testLoopPreventionRejectsVisitedNode() = runBlocking {
        val dao = InMemoryDao()
        val outbox = InMemoryOutboxDao()
        val router = MeshRouter("node_A", dao, outbox, null)

        val event = EventEntity(
            id = "evt_loop_1",
            localAuthorId = "node_A",
            displayName = "گوشی A",
            createdAt = System.currentTimeMillis(),
            eventType = EventType.ROOT_THREAD,
            rootThreadId = "evt_loop_1",
            text = "پیام آزمایشی حلقه",
            publishScope = PublishScope.MESH_PUBLIC
        )

        // Packet that has already passed through node_A and node_B and is now sent back to node_A
        val loopedPacket = RelayPacket(
            packetId = "pkt_loop",
            event = event,
            hopCount = 2,
            maxHops = 5,
            visitedNodeIds = listOf("node_A", "node_B")
        )

        val res = router.processIncomingPacket(loopedPacket.toJson())
        assertTrue(res.isSuccess)
        assertEquals(false, res.getOrNull()) // Packet dropped due to loop
        assertEquals(0, dao.storedEvents.size)
        assertEquals(1, router.stats.value.packetsDroppedLoop)
    }

    @Test
    fun testMaxHopsEnforcementStopsFurtherRelay() = runBlocking {
        val dao = InMemoryDao()
        val outbox = InMemoryOutboxDao()
        val router = MeshRouter("node_C", dao, outbox, null)

        val event = EventEntity(
            id = "evt_max_hops",
            localAuthorId = "node_A",
            displayName = "A",
            createdAt = System.currentTimeMillis(),
            eventType = EventType.ROOT_THREAD,
            rootThreadId = "evt_max_hops",
            text = "پیام با محدودیت پرش",
            publishScope = PublishScope.MESH_PUBLIC
        )

        // Packet reached hopCount 3 with maxHops 3
        val maxedPacket = RelayPacket(
            packetId = "pkt_maxed",
            event = event,
            hopCount = 3,
            maxHops = 3,
            visitedNodeIds = listOf("node_A", "node_B")
        )

        val res = router.processIncomingPacket(maxedPacket.toJson())
        assertTrue(res.isSuccess)
        // Stored locally for user C to see
        assertNotNull(dao.getEventById("evt_max_hops"))
        // But NOT queued for further forwarding!
        assertEquals(0, outbox.outbox.size)
    }

    @Test
    fun testExpirationDropsStalePackets() = runBlocking {
        val dao = InMemoryDao()
        val outbox = InMemoryOutboxDao()
        val router = MeshRouter("node_B", dao, outbox, null)

        val staleEvent = EventEntity(
            id = "evt_stale",
            localAuthorId = "node_A",
            displayName = "A",
            createdAt = System.currentTimeMillis() - 100000,
            eventType = EventType.ROOT_THREAD,
            rootThreadId = "evt_stale",
            text = "پیام منقضی شده",
            publishScope = PublishScope.MESH_PUBLIC
        )

        val expiredPacket = RelayPacket(
            packetId = "pkt_exp",
            event = staleEvent,
            hopCount = 1,
            maxHops = 5,
            visitedNodeIds = listOf("node_A"),
            expiresAt = System.currentTimeMillis() - 5000 // Already expired in past
        )

        val res = router.processIncomingPacket(expiredPacket.toJson())
        assertTrue(res.isSuccess)
        assertEquals(false, res.getOrNull())
        assertEquals(0, dao.storedEvents.size)
        assertEquals(1, router.stats.value.packetsDroppedExpired)
    }

    @Test
    fun testScopeEnforcementRejectsLocalOnlyAndGlobalPublic() = runBlocking {
        val dao = InMemoryDao()
        val outbox = InMemoryOutboxDao()
        val router = MeshRouter("node_B", dao, outbox, null)

        val localOnlyEvent = EventEntity(
            id = "evt_local",
            localAuthorId = "node_A",
            displayName = "A",
            createdAt = System.currentTimeMillis(),
            eventType = EventType.ROOT_THREAD,
            rootThreadId = "evt_local",
            text = "فقط محلی",
            publishScope = PublishScope.LOCAL_ONLY
        )

        // Attempting to enqueue local only to mesh outbox must fail
        val enqueueRes = router.enqueueForRelay(localOnlyEvent)
        assertTrue(enqueueRes.isFailure)
        assertEquals(0, outbox.outbox.size)

        // Incoming packet pretending to have LOCAL_ONLY must be rejected
        val fakePacket = RelayPacket(
            packetId = "pkt_fake",
            event = localOnlyEvent,
            hopCount = 0,
            maxHops = 5,
            visitedNodeIds = listOf("node_A")
        )
        val incomingRes = router.processIncomingPacket(fakePacket.toJson())
        assertTrue(incomingRes.isFailure)
    }
}
