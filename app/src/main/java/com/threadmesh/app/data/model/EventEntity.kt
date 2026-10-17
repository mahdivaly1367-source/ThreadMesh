package com.threadmesh.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mesh_events",
    indices = [
        Index(value = ["rootThreadId"]),
        Index(value = ["parentEventId"]),
        Index(value = ["createdAt"])
    ]
)
data class EventEntity(
    @PrimaryKey
    val id: String, // UUID
    val localAuthorId: String,
    val displayName: String,
    val createdAt: Long,
    val eventType: EventType,
    val rootThreadId: String,
    val parentEventId: String? = null,
    val targetEventId: String? = null, // for STANCE_CHANGED pointing to previous event
    val text: String,
    val publishScope: PublishScope = PublishScope.LOCAL_ONLY,
    val lastViewedAt: Long? = null
)
