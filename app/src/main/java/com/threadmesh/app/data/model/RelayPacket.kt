package com.threadmesh.app.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Encapsulates an acknowledgment packet sent back from receiving node to transmitting peer
 * upon successful validation and local persistence of an EventEntity in Room.
 */
data class AckPacket(
    val packetId: String,
    val eventId: String,
    val ackFromNodeId: String,
    val status: String = STATUS_STORED, // STORED or DUPLICATE
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE = "MESH_ACK"
        const val STATUS_STORED = "STORED"
        const val STATUS_DUPLICATE = "DUPLICATE"

        fun fromJson(jsonStr: String): AckPacket? {
            return try {
                val json = JSONObject(jsonStr)
                if (json.optString("type") != TYPE) return null
                AckPacket(
                    packetId = json.getString("packetId"),
                    eventId = json.getString("eventId"),
                    ackFromNodeId = json.getString("ackFromNodeId"),
                    status = json.optString("status", STATUS_STORED),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis())
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    fun toJson(): String {
        return JSONObject().apply {
            put("type", TYPE)
            put("packetId", packetId)
            put("eventId", eventId)
            put("ackFromNodeId", ackFromNodeId)
            put("status", status)
            put("timestamp", timestamp)
        }.toString()
    }
}

/**
 * Encapsulates a multi-hop mesh envelope carrying an immutable EventEntity.
 *
 * Requirements:
 * - hopCount incremented at each hop.
 * - maxHops prevents unbounded flooding across the physical mesh.
 * - visitedNodeIds prevents routing loops (A -> B -> A).
 * - expiresAt discards stale packets after TTL.
 * - Preserves origin author, origin event ID, timestamp, and content completely intact.
 */
data class RelayPacket(
    val packetId: String,
    val event: EventEntity,
    val hopCount: Int = 0,
    val maxHops: Int = DEFAULT_MAX_HOPS,
    val visitedNodeIds: List<String> = emptyList(),
    val expiresAt: Long = System.currentTimeMillis() + DEFAULT_TTL_MILLIS,
    val relayedByNodeId: String = "",
    val relayedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val TYPE = "MESH_RELAY"
        const val DEFAULT_MAX_HOPS = 5
        const val DEFAULT_TTL_MILLIS = 7 * 24 * 60 * 60 * 1000L // 7 days

        fun fromEvent(
            event: EventEntity,
            originNodeId: String,
            maxHops: Int = DEFAULT_MAX_HOPS,
            ttlMillis: Long = DEFAULT_TTL_MILLIS
        ): RelayPacket {
            val now = System.currentTimeMillis()
            return RelayPacket(
                packetId = "pkt_${event.id}_0",
                event = event,
                hopCount = 0,
                maxHops = maxHops,
                visitedNodeIds = listOf(originNodeId),
                expiresAt = now + ttlMillis,
                relayedByNodeId = originNodeId,
                relayedAt = now
            )
        }

        fun fromJson(jsonStr: String): RelayPacket {
            val json = JSONObject(jsonStr)
            val eventJson = json.getJSONObject("event")

            val event = EventEntity(
                id = eventJson.getString("id"),
                localAuthorId = eventJson.getString("localAuthorId"),
                displayName = eventJson.getString("displayName"),
                createdAt = eventJson.getLong("createdAt"),
                eventType = EventType.valueOf(eventJson.getString("eventType")),
                rootThreadId = eventJson.getString("rootThreadId"),
                parentEventId = if (eventJson.has("parentEventId") && !eventJson.isNull("parentEventId")) eventJson.getString("parentEventId") else null,
                targetEventId = if (eventJson.has("targetEventId") && !eventJson.isNull("targetEventId")) eventJson.getString("targetEventId") else null,
                text = eventJson.getString("text"),
                publishScope = PublishScope.valueOf(eventJson.getString("publishScope")),
                lastViewedAt = null
            )

            val visitedArray = json.optJSONArray("visitedNodeIds") ?: JSONArray()
            val visitedList = mutableListOf<String>()
            for (i in 0 until visitedArray.length()) {
                visitedList.add(visitedArray.getString(i))
            }

            return RelayPacket(
                packetId = json.getString("packetId"),
                event = event,
                hopCount = json.getInt("hopCount"),
                maxHops = json.optInt("maxHops", DEFAULT_MAX_HOPS),
                visitedNodeIds = visitedList,
                expiresAt = json.optLong("expiresAt", System.currentTimeMillis() + DEFAULT_TTL_MILLIS),
                relayedByNodeId = json.optString("relayedByNodeId", ""),
                relayedAt = json.optLong("relayedAt", System.currentTimeMillis())
            )
        }
    }

    fun isExpired(currentTime: Long = System.currentTimeMillis()): Boolean = currentTime > expiresAt

    fun canForward(localNodeId: String): Boolean {
        // Must be MESH_PUBLIC
        if (event.publishScope != PublishScope.MESH_PUBLIC) return false
        // Must not exceed max hops
        if (hopCount >= maxHops) return false
        // Loop prevention: must not have visited this node previously
        if (visitedNodeIds.contains(localNodeId)) return false
        // Must not be expired
        if (isExpired()) return false
        return true
    }

    fun nextHop(localNodeId: String): RelayPacket {
        val now = System.currentTimeMillis()
        return copy(
            packetId = "pkt_${event.id}_${hopCount + 1}",
            hopCount = hopCount + 1,
            visitedNodeIds = if (visitedNodeIds.contains(localNodeId)) visitedNodeIds else visitedNodeIds + localNodeId,
            relayedByNodeId = localNodeId,
            relayedAt = now
        )
    }

    fun toJson(): String {
        val json = JSONObject()
        json.put("type", TYPE)
        json.put("packetId", packetId)
        json.put("hopCount", hopCount)
        json.put("maxHops", maxHops)
        json.put("expiresAt", expiresAt)
        json.put("relayedByNodeId", relayedByNodeId)
        json.put("relayedAt", relayedAt)

        val visitedArr = JSONArray()
        visitedNodeIds.forEach { visitedArr.put(it) }
        json.put("visitedNodeIds", visitedArr)

        val eventJson = JSONObject().apply {
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
        json.put("event", eventJson)

        return json.toString()
    }
}
