package com.threadmesh.app

import com.threadmesh.app.data.local.ThreadMeshDao
import com.threadmesh.app.data.model.EventEntity
import com.threadmesh.app.data.model.EventType
import com.threadmesh.app.data.model.PublishScope
import com.threadmesh.app.data.repository.ThreadMeshRepository
import com.threadmesh.app.transport.SimulatedNearbyTransport
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MeshTransportFilterTest {

    // Simple fake DAO for in-memory testing of deduplication and inserts
    private class FakeDao : ThreadMeshDao {
        val storedEvents = mutableMapOf<String, EventEntity>()

        override suspend fun insertEvent(event: EventEntity): Long {
            if (storedEvents.containsKey(event.id)) {
                return -1L // OnConflictStrategy.IGNORE returns -1
            }
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

    @Test
    fun testTransportRejectsLocalOnlyAndGlobalPublic() {
        val transport = SimulatedNearbyTransport()
        transport.start("تست گره")

        val localOnlyEvent = EventEntity(
            id = UUID.randomUUID().toString(),
            localAuthorId = "node_local",
            displayName = "کاربر محلی",
            createdAt = System.currentTimeMillis(),
            eventType = EventType.ROOT_THREAD,
            rootThreadId = "123",
            text = "یادداشت کاملاً خصوصی محلی",
            publishScope = PublishScope.LOCAL_ONLY
        )

        val globalEvent = EventEntity(
            id = UUID.randomUUID().toString(),
            localAuthorId = "node_local",
            displayName = "کاربر محلی",
            createdAt = System.currentTimeMillis(),
            eventType = EventType.ROOT_THREAD,
            rootThreadId = "123",
            text = "یادداشت سراسری بدون مش",
            publishScope = PublishScope.GLOBAL_PUBLIC
        )

        val meshPublicEvent = EventEntity(
            id = UUID.randomUUID().toString(),
            localAuthorId = "node_local",
            displayName = "کاربر محلی",
            createdAt = System.currentTimeMillis(),
            eventType = EventType.ROOT_THREAD,
            rootThreadId = "123",
            text = "یادداشت عمومی برای مش",
            publishScope = PublishScope.MESH_PUBLIC
        )

        // LOCAL_ONLY must fail to send
        val resLocal = transport.sendMeshEvent("peer_1", localOnlyEvent)
        assertTrue(resLocal.isFailure)

        // GLOBAL_PUBLIC must fail to send over Nearby mesh
        val resGlobal = transport.sendMeshEvent("peer_1", globalEvent)
        assertTrue(resGlobal.isFailure)

        // MESH_PUBLIC must succeed
        val resMesh = transport.sendMeshEvent("peer_1", meshPublicEvent)
        assertTrue(resMesh.isSuccess)
    }

    @Test
    fun testRepositoryRejectsInvalidIncomingMeshEvents() = runBlocking {
        val fakeDao = FakeDao()
        val repository = ThreadMeshRepository(fakeDao)

        // Rejects LOCAL_ONLY
        val invalidScopeEvent = EventEntity(
            id = UUID.randomUUID().toString(),
            localAuthorId = "node_ext",
            displayName = "همتا",
            createdAt = System.currentTimeMillis(),
            eventType = EventType.ROOT_THREAD,
            rootThreadId = "test",
            text = "تلاش برای تزریق محلی",
            publishScope = PublishScope.LOCAL_ONLY
        )
        val res1 = repository.importIncomingMeshEvent(invalidScopeEvent)
        assertTrue(res1.isFailure)

        // Rejects empty text
        val emptyTextEvent = invalidScopeEvent.copy(text = "   ", publishScope = PublishScope.MESH_PUBLIC)
        val res2 = repository.importIncomingMeshEvent(emptyTextEvent)
        assertTrue(res2.isFailure)

        // Rejects text exceeding 4000 characters
        val overflowEvent = invalidScopeEvent.copy(text = "A".repeat(4001), publishScope = PublishScope.MESH_PUBLIC)
        val res3 = repository.importIncomingMeshEvent(overflowEvent)
        assertTrue(res3.isFailure)
    }

    @Test
    fun testDeduplicationPreventsDuplicateRecords() = runBlocking {
        val fakeDao = FakeDao()
        val repository = ThreadMeshRepository(fakeDao)

        val uniqueId = "evt_dedup_test_999"
        val validMeshEvent = EventEntity(
            id = uniqueId,
            localAuthorId = "node_peer",
            displayName = "کاربر همتا",
            createdAt = System.currentTimeMillis(),
            eventType = EventType.ROOT_THREAD,
            rootThreadId = uniqueId,
            text = "موضوع عمومی مش برای بررسی عدم تکرار",
            publishScope = PublishScope.MESH_PUBLIC
        )

        // First import must succeed and return true (new event stored)
        val firstImport = repository.importIncomingMeshEvent(validMeshEvent)
        assertTrue(firstImport.isSuccess)
        assertEquals(true, firstImport.getOrNull())
        assertEquals(1, fakeDao.storedEvents.size)

        // Second import of the EXACT same event ID must return false (duplicate ignored without error)
        val secondImport = repository.importIncomingMeshEvent(validMeshEvent)
        assertTrue(secondImport.isSuccess)
        assertEquals(false, secondImport.getOrNull())
        // Stored records count must remain exactly 1
        assertEquals(1, fakeDao.storedEvents.size)
    }
}
