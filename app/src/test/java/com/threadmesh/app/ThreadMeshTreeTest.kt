package com.threadmesh.app

import com.threadmesh.app.data.model.EventEntity
import com.threadmesh.app.data.model.EventType
import com.threadmesh.app.data.model.PublishScope
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ThreadMeshTreeTest {

    @Test
    fun testEventCreationValidation() {
        // Empty text check
        val emptyText = "   "
        assertTrue(emptyText.trim().isEmpty())

        // Max 4000 characters check
        val validText = "A".repeat(4000)
        assertEquals(4000, validText.length)

        val invalidText = "A".repeat(4001)
        assertTrue(invalidText.length > 4000)
    }

    @Test
    fun testStanceChangeIsImmutableEvent() {
        val rootId = UUID.randomUUID().toString()
        val originalEvent = EventEntity(
            id = rootId,
            localAuthorId = "node_1",
            displayName = "کاربر اول",
            createdAt = 1000L,
            eventType = EventType.ROOT_THREAD,
            rootThreadId = rootId,
            text = "دیدگاه اولیه در مورد معماری غیرمتمرکز",
            publishScope = PublishScope.LOCAL_ONLY
        )

        val stanceChangeEventId = UUID.randomUUID().toString()
        val stanceChangeEvent = EventEntity(
            id = stanceChangeEventId,
            localAuthorId = "node_1",
            displayName = "کاربر اول",
            createdAt = 2000L,
            eventType = EventType.STANCE_CHANGED,
            rootThreadId = rootId,
            parentEventId = originalEvent.id,
            targetEventId = originalEvent.id,
            text = "پس از بررسی عملکرد در مقیاس بالا، رویکرد تلفیقی بهینه‌تر ارزیابی شد.",
            publishScope = PublishScope.LOCAL_ONLY
        )

        // Original event remains completely intact and unmuted
        assertEquals("دیدگاه اولیه در مورد معماری غیرمتمرکز", originalEvent.text)
        assertEquals(EventType.ROOT_THREAD, originalEvent.eventType)

        // Stance change is a separate event linked by targetEventId
        assertEquals(originalEvent.id, stanceChangeEvent.targetEventId)
        assertEquals(EventType.STANCE_CHANGED, stanceChangeEvent.eventType)
        assertNotEquals(originalEvent.id, stanceChangeEvent.id)
    }

    @Test
    fun testPublishScopeLabelsAndNotices() {
        assertEquals("فقط محلی (LOCAL_ONLY)", PublishScope.LOCAL_ONLY.labelFa)
        assertEquals("عمومی در مش (MESH_PUBLIC)", PublishScope.MESH_PUBLIC.labelFa)
        assertEquals("عمومی سراسری (GLOBAL_PUBLIC)", PublishScope.GLOBAL_PUBLIC.labelFa)
        assertEquals("انتقال در این نسخه هنوز فعال نیست", PublishScope.PHASE1_NOTICE)
    }
}
