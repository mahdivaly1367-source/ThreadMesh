package com.threadmesh.app.data.local

import androidx.room.*
import com.threadmesh.app.data.model.OutboxEntity
import com.threadmesh.app.data.model.OutboxStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOutboxItem(item: OutboxEntity): Long

    @Update
    suspend fun updateOutboxItem(item: OutboxEntity)

    /**
     * Retrieves unexpired active packets in the outbox.
     * Packets remain in this list even after sending to peer 1,
     * so they can be forwarded to peer 2, peer 3, etc. until TTL expires.
     */
    @Query("SELECT * FROM mesh_outbox WHERE status = 'PENDING' AND expiresAt > :now ORDER BY createdAt ASC")
    suspend fun getPendingPackets(now: Long = System.currentTimeMillis()): List<OutboxEntity>

    @Query("SELECT * FROM mesh_outbox WHERE packetId = :packetId LIMIT 1")
    suspend fun getByPacketId(packetId: String): OutboxEntity?

    @Query("SELECT * FROM mesh_outbox WHERE eventId = :eventId LIMIT 1")
    suspend fun getByEventId(eventId: String): OutboxEntity?

    /**
     * Records a confirmed delivery and acknowledgment by a specific peer.
     * Appends the peer identifier to deliveredPeerIds and keeps the packet active for other peers.
     */
    @Transaction
    suspend fun recordConfirmedPeerDelivery(packetId: String, peerId: String) {
        val existing = getByPacketId(packetId) ?: return
        val updated = existing.withConfirmedDelivery(peerId)
        updateOutboxItem(updated)
    }

    /**
     * Records a per-peer attempt atomically. A later retry to peer A must not suppress the
     * first delivery to peer B, and updating this timestamp must preserve a concurrent ACK.
     */
    @Transaction
    suspend fun recordPeerRelayAttempt(packetId: String, peerId: String, attemptTime: Long) {
        val existing = getByPacketId(packetId) ?: return
        updateOutboxItem(existing.withPeerAttempt(peerId, attemptTime))
    }

    /**
     * Backward-compatible helper for successful transmission metrics.
     */
    @Query("UPDATE mesh_outbox SET lastRelayAttemptAt = :attemptTime, relayedToPeersCount = relayedToPeersCount + 1 WHERE packetId = :packetId")
    suspend fun markRelayed(packetId: String, attemptTime: Long = System.currentTimeMillis())

    @Query("UPDATE mesh_outbox SET status = 'EXPIRED' WHERE expiresAt <= :now AND status = 'PENDING'")
    suspend fun markExpiredPackets(now: Long = System.currentTimeMillis())

    @Query("UPDATE mesh_outbox SET status = 'DROPPED' WHERE packetId = :packetId AND status = 'PENDING'")
    suspend fun markDroppedPacket(packetId: String)

    @Query("SELECT COUNT(*) FROM mesh_outbox WHERE status = 'PENDING'")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM mesh_outbox WHERE relayedToPeersCount > 0")
    fun getRelayedCountFlow(): Flow<Int>

    @Query("SELECT * FROM mesh_outbox ORDER BY createdAt DESC LIMIT 50")
    fun getAllOutboxFlow(): Flow<List<OutboxEntity>>
}
