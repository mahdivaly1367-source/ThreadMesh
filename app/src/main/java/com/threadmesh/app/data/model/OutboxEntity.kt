package com.threadmesh.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import org.json.JSONObject

enum class OutboxStatus {
    PENDING,        // Active and ready to be delivered to peers not yet in deliveredPeerIds
    RELAYED,        // Delivered to at least one peer (retained for backward compatibility)
    EXPIRED,        // Dropped due to TTL expiration
    DROPPED         // Manually dropped or invalid
}

@Entity(
    tableName = "mesh_outbox",
    indices = [
        Index(value = ["eventId"]),
        Index(value = ["status"]),
        Index(value = ["expiresAt"])
    ]
)
data class OutboxEntity(
    @PrimaryKey
    val packetId: String,
    val eventId: String,
    val rawJson: String,
    val status: OutboxStatus = OutboxStatus.PENDING,
    val hopCount: Int,
    val expiresAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val lastRelayAttemptAt: Long? = null,
    val relayedToPeersCount: Int = 0,
    val deliveredPeerIds: String = "", // Comma-separated endpoint IDs confirmed to have received this packet
    @ColumnInfo(defaultValue = "'{}'")
    val peerAttemptTimes: String = "{}" // JSON object: endpoint ID -> last send attempt timestamp
) {
    fun lastAttemptAtFor(peerId: String): Long {
        if (peerId.isBlank() || peerAttemptTimes.isBlank()) return 0L
        return runCatching { JSONObject(peerAttemptTimes).optLong(peerId, 0L) }.getOrDefault(0L)
    }

    fun withPeerAttempt(peerId: String, attemptedAt: Long): OutboxEntity {
        if (peerId.isBlank()) return this
        val attempts = runCatching { JSONObject(peerAttemptTimes) }.getOrElse { JSONObject() }
        attempts.put(peerId, attemptedAt)
        return copy(lastRelayAttemptAt = attemptedAt, peerAttemptTimes = attempts.toString())
    }

    fun hasDeliveredTo(peerId: String): Boolean {
        if (deliveredPeerIds.isBlank() || peerId.isBlank()) return false
        val set = deliveredPeerIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return set.contains(peerId.trim())
    }

    fun withConfirmedDelivery(peerId: String): OutboxEntity {
        val currentList = if (deliveredPeerIds.isBlank()) emptyList() else deliveredPeerIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val updatedList = if (currentList.contains(peerId.trim())) currentList else currentList + peerId.trim()
        val now = System.currentTimeMillis()
        return copy(
            deliveredPeerIds = updatedList.joinToString(","),
            relayedToPeersCount = updatedList.size,
            lastRelayAttemptAt = now,
            // Packet remains PENDING so it can be delivered to other subsequent peers until TTL expires!
            status = OutboxStatus.PENDING
        )
    }
}
