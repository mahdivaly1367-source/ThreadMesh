package com.threadmesh.app.data.local

import androidx.room.TypeConverter
import com.threadmesh.app.data.model.EventType
import com.threadmesh.app.data.model.PublishScope

class Converters {
    @TypeConverter
    fun fromEventType(value: EventType): String = value.name

    @TypeConverter
    fun toEventType(value: String): EventType = runCatching { EventType.valueOf(value) }.getOrDefault(EventType.ROOT_THREAD)

    @TypeConverter
    fun fromPublishScope(value: PublishScope): String = value.name

    @TypeConverter
    fun toPublishScope(value: String): PublishScope = runCatching { PublishScope.valueOf(value) }.getOrDefault(PublishScope.LOCAL_ONLY)

    @TypeConverter
    fun fromOutboxStatus(value: com.threadmesh.app.data.model.OutboxStatus): String = value.name

    @TypeConverter
    fun toOutboxStatus(value: String): com.threadmesh.app.data.model.OutboxStatus =
        runCatching { com.threadmesh.app.data.model.OutboxStatus.valueOf(value) }.getOrDefault(com.threadmesh.app.data.model.OutboxStatus.PENDING)
}
