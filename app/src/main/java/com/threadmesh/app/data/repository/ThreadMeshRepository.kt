package com.threadmesh.app.data.repository

import com.threadmesh.app.data.local.ThreadMeshDao
import com.threadmesh.app.data.local.OutboxDao
import com.threadmesh.app.data.model.OutboxEntity
import com.threadmesh.app.data.model.OutboxStatus
import com.threadmesh.app.data.model.RelayPacket
import com.threadmesh.app.data.model.EventEntity
import com.threadmesh.app.data.model.EventType
import com.threadmesh.app.data.model.PublishScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class TreeNode(
    val event: EventEntity,
    val depth: Int,
    val children: MutableList<TreeNode> = mutableListOf(),
    val stanceChanges: MutableList<EventEntity> = mutableListOf(),
    var isExpanded: Boolean = true
)

data class ThreadListItem(
    val rootEvent: EventEntity,
    val replyCount: Int,
    val lastActivityTime: Long,
    val lastPreviewText: String,
    val hasUnseenChanges: Boolean
)

data class RecentChangeEventItem(
    val event: EventEntity,
    val rootEventTitle: String,
    val parentPreviewText: String?
)


class ThreadMeshRepository(
    private val dao: ThreadMeshDao,
    private val outboxDao: OutboxDao? = null
) {

    val rootThreadsFlow: Flow<List<EventEntity>> = dao.getAllRootThreadsFlow()
    val allEventsFlow: Flow<List<EventEntity>> = dao.getAllEventsFlow()

    fun getPendingOutboxCountFlow(): Flow<Int>? = outboxDao?.getPendingCountFlow()
    fun getRelayedOutboxCountFlow(): Flow<Int>? = outboxDao?.getRelayedCountFlow()

    fun getThreadEventsFlow(rootThreadId: String): Flow<List<EventEntity>> =
        dao.getEventsForThreadFlow(rootThreadId)

    suspend fun createRootThread(
        authorId: String,
        displayName: String,
        text: String,
        scope: PublishScope
    ): Result<EventEntity> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("متن موضوع نمی‌تواند خالی باشد."))
        }
        if (trimmed.length > 4000) {
            return Result.failure(IllegalArgumentException("متن موضوع نمی‌تواند بیش از ۴۰۰۰ نویسه باشد."))
        }

        val eventId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val event = EventEntity(
            id = eventId,
            localAuthorId = authorId,
            displayName = displayName,
            createdAt = now,
            eventType = EventType.ROOT_THREAD,
            rootThreadId = eventId,
            parentEventId = null,
            targetEventId = null,
            text = trimmed,
            publishScope = scope,
            lastViewedAt = now
        )
        dao.insertEvent(event)
        enqueueIfMeshPublic(event)
        return Result.success(event)
    }

    private suspend fun enqueueIfMeshPublic(event: EventEntity) {
        if (event.publishScope == PublishScope.MESH_PUBLIC && outboxDao != null) {
            val packet = RelayPacket.fromEvent(event, originNodeId = event.localAuthorId)
            val outboxItem = OutboxEntity(
                packetId = packet.packetId,
                eventId = event.id,
                rawJson = packet.toJson(),
                status = OutboxStatus.PENDING,
                hopCount = packet.hopCount,
                expiresAt = packet.expiresAt
            )
            outboxDao.insertOutboxItem(outboxItem)
        }
    }

    suspend fun addReply(
        parentEventId: String,
        authorId: String,
        displayName: String,
        text: String,
        scope: PublishScope
    ): Result<EventEntity> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("متن پاسخ نمی‌تواند خالی باشد."))
        }
        if (trimmed.length > 4000) {
            return Result.failure(IllegalArgumentException("متن پاسخ نمی‌تواند بیش از ۴۰۰۰ نویسه باشد."))
        }

        val parent = dao.getEventById(parentEventId)
            ?: return Result.failure(IllegalArgumentException("پست مورد نظر یافت نشد."))

        val eventId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val event = EventEntity(
            id = eventId,
            localAuthorId = authorId,
            displayName = displayName,
            createdAt = now,
            eventType = EventType.REPLY,
            rootThreadId = parent.rootThreadId,
            parentEventId = parent.id,
            targetEventId = null,
            text = trimmed,
            publishScope = scope
        )
        dao.insertEvent(event)
        enqueueIfMeshPublic(event)
        return Result.success(event)
    }

    suspend fun recordStanceChange(
        targetEventId: String,
        authorId: String,
        displayName: String,
        explanationText: String,
        scope: PublishScope
    ): Result<EventEntity> {
        val trimmed = explanationText.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("توضیح تغییر دیدگاه نمی‌تواند خالی باشد."))
        }
        if (trimmed.length > 4000) {
            return Result.failure(IllegalArgumentException("توضیح نمی‌تواند بیش از ۴۰۰۰ نویسه باشد."))
        }

        val targetEvent = dao.getEventById(targetEventId)
            ?: return Result.failure(IllegalArgumentException("رویداد هدف جهت تغییر دیدگاه یافت نشد."))

        val eventId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        // Stance change creates a NEW immutable event linked to the target event
        val event = EventEntity(
            id = eventId,
            localAuthorId = authorId,
            displayName = displayName,
            createdAt = now,
            eventType = EventType.STANCE_CHANGED,
            rootThreadId = targetEvent.rootThreadId,
            parentEventId = targetEvent.id,
            targetEventId = targetEvent.id,
            text = trimmed,
            publishScope = scope
        )
        dao.insertEvent(event)
        enqueueIfMeshPublic(event)
        return Result.success(event)
    }

    suspend fun markThreadAsViewed(rootThreadId: String) {
        dao.updateThreadLastViewed(rootThreadId, System.currentTimeMillis())
    }

    suspend fun buildTree(rootThreadId: String): TreeNode? {
        val events = dao.getEventsForThread(rootThreadId)
        val rootEvent = events.firstOrNull { it.id == rootThreadId } ?: return null

        val nodeMap = mutableMapOf<String, TreeNode>()
        val stanceList = mutableListOf<EventEntity>()

        for (e in events) {
            if (e.eventType == EventType.STANCE_CHANGED) {
                stanceList.add(e)
            } else {
                nodeMap[e.id] = TreeNode(event = e, depth = 0)
            }
        }

        val rootNode = nodeMap[rootEvent.id] ?: return null

        for (e in events) {
            if (e.id != rootEvent.id && e.eventType != EventType.STANCE_CHANGED) {
                val parentId = e.parentEventId
                val parentNode = if (parentId != null) nodeMap[parentId] else rootNode
                val currentNode = nodeMap[e.id]
                if (currentNode != null && parentNode != null) {
                    currentNode.run {
                        // calculate depth relative to tree
                    }
                    parentNode.children.add(currentNode)
                }
            }
        }

        // Attach stance changes to target node
        for (sc in stanceList) {
            val targetId = sc.targetEventId ?: sc.parentEventId
            if (targetId != null && nodeMap.containsKey(targetId)) {
                nodeMap[targetId]?.stanceChanges?.add(sc)
            } else {
                rootNode.stanceChanges.add(sc)
            }
        }

        // Assign depths recursively
        fun assignDepth(node: TreeNode, depth: Int) {
            val field = TreeNode::class.java.getDeclaredField("depth")
            field.isAccessible = true
            field.setInt(node, depth)
            for (child in node.children) {
                assignDepth(child, depth + 1)
            }
        }
        assignDepth(rootNode, 0)

        return rootNode
    }

    /**
     * Imports an incoming event received via transport (Google Nearby Connections).
     * Strictly verifies:
     * 1. publishScope must be MESH_PUBLIC (LOCAL_ONLY and GLOBAL_PUBLIC are rejected)
     * 2. text is non-empty and <= 4000 chars
     * 3. eventId does not already exist in local database (deduplication)
     * 4. Saves to Room local database before returning success
     */
    suspend fun importIncomingMeshEvent(event: EventEntity): Result<Boolean> {
        if (event.publishScope != PublishScope.MESH_PUBLIC) {
            return Result.failure(
                IllegalArgumentException("رویداد به دلیل عدم تطابق دامنه رد شد. فقط MESH_PUBLIC مجاز است.")
            )
        }

        val trimmed = event.text.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("متن رویداد دریافتی نمی‌تواند خالی باشد."))
        }
        if (trimmed.length > 4000) {
            return Result.failure(IllegalArgumentException("طول متن رویداد دریافتی بیش از حد مجاز ۴۰۰۰ نویسه است."))
        }

        // Check for deduplication
        val existing = dao.getEventById(event.id)
        if (existing != null) {
            // Event already exists, ignore duplicate safely without throwing or double-inserting
            return Result.success(false)
        }

        // If it's a new root thread from a peer, leave lastViewedAt null so it surfaces in RecentChanges
        val eventToInsert = if (event.eventType == EventType.ROOT_THREAD) {
            event.copy(lastViewedAt = null)
        } else {
            event
        }

        val rowId = dao.insertEvent(eventToInsert)
        return Result.success(rowId > 0)
    }

    /**
     * Retrieves only MESH_PUBLIC events for synchronization with nearby peers.
     * LOCAL_ONLY and GLOBAL_PUBLIC are strictly filtered out.
     */
    suspend fun getMeshPublicEvents(): List<EventEntity> {
        return dao.getAllMeshPublicEvents()
    }
}
