package com.threadmesh.app.data.local

import androidx.room.*
import com.threadmesh.app.data.model.EventEntity
import com.threadmesh.app.data.model.EventType
import kotlinx.coroutines.flow.Flow

data class ThreadSummary(
    val rootId: String,
    val title: String,
    val author: String,
    val createdAt: Long,
    val totalReplies: Int,
    val lastActivityAt: Long,
    val lastPreviewText: String,
    val lastViewedAt: Long?
)

@Dao
interface ThreadMeshDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: EventEntity): Long

    @Query("SELECT * FROM mesh_events WHERE id = :id LIMIT 1")
    suspend fun getEventById(id: String): EventEntity?

    @Query("SELECT * FROM mesh_events WHERE rootThreadId = :rootThreadId ORDER BY createdAt ASC")
    fun getEventsForThreadFlow(rootThreadId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM mesh_events WHERE rootThreadId = :rootThreadId ORDER BY createdAt ASC")
    suspend fun getEventsForThread(rootThreadId: String): List<EventEntity>

    @Query("SELECT * FROM mesh_events WHERE eventType = 'ROOT_THREAD' ORDER BY createdAt DESC")
    fun getAllRootThreadsFlow(): Flow<List<EventEntity>>

    @Query("SELECT * FROM mesh_events ORDER BY createdAt DESC")
    fun getAllEventsFlow(): Flow<List<EventEntity>>

    @Query("UPDATE mesh_events SET lastViewedAt = :viewedTime WHERE id = :rootThreadId")
    suspend fun updateThreadLastViewed(rootThreadId: String, viewedTime: Long)

    @Query("SELECT COUNT(*) FROM mesh_events WHERE rootThreadId = :rootThreadId AND id != :rootThreadId")
    suspend fun getReplyCount(rootThreadId: String): Int

    @Query("SELECT * FROM mesh_events WHERE rootThreadId = :rootThreadId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestEventInThread(rootThreadId: String): EventEntity?

    @Query("SELECT * FROM mesh_events WHERE publishScope = 'MESH_PUBLIC' ORDER BY createdAt ASC")
    suspend fun getAllMeshPublicEvents(): List<EventEntity>
}
