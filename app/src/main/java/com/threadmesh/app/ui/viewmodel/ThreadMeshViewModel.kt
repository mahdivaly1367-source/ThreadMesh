package com.threadmesh.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threadmesh.app.data.model.EventEntity
import com.threadmesh.app.data.model.EventType
import com.threadmesh.app.data.model.PublishScope
import com.threadmesh.app.data.local.OutboxDao
import com.threadmesh.app.data.local.ThreadMeshDao
import com.threadmesh.app.mesh.MeshRouter
import com.threadmesh.app.mesh.MeshRouterStats
import com.threadmesh.app.security.EventAuthenticator
import com.threadmesh.app.data.repository.RecentChangeEventItem
import com.threadmesh.app.data.repository.ThreadListItem
import com.threadmesh.app.data.repository.ThreadMeshRepository
import com.threadmesh.app.data.repository.TreeNode
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

import com.threadmesh.app.transport.DiscoveredPeer
import com.threadmesh.app.transport.MeshServiceState
import com.threadmesh.app.transport.PeerConnectionStatus
import com.threadmesh.app.transport.TransportProvider

class ThreadMeshViewModel(
    private val repository: ThreadMeshRepository,
    private val transport: TransportProvider? = null,
    threadMeshDao: ThreadMeshDao? = null,
    outboxDao: OutboxDao? = null,
    initialLocalAuthorId: String? = null,
    initialLocalNodeId: String? = null,
    eventAuthenticator: EventAuthenticator? = null
) : ViewModel() {

    private val _localAuthorId = MutableStateFlow(
        initialLocalAuthorId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().take(8)
    )
    val localAuthorId: StateFlow<String> = _localAuthorId.asStateFlow()

    // The router belongs to the ViewModel lifecycle and shares the same stable identity as local posts.
    private val meshRouter: MeshRouter? = if (threadMeshDao != null && outboxDao != null) {
        MeshRouter(
            localNodeId = initialLocalNodeId?.takeIf { it.isNotBlank() } ?: _localAuthorId.value,
            eventDao = threadMeshDao,
            outboxDao = outboxDao,
            transport = transport,
            scope = viewModelScope,
            eventAuthenticator = eventAuthenticator
        )
    } else {
        null
    }

    /** Live router diagnostics shown on the connection screen; empty defaults keep previews safe. */
    val meshRouterStats: StateFlow<MeshRouterStats> =
        meshRouter?.stats ?: MutableStateFlow(MeshRouterStats()).asStateFlow()

    val meshRelayLog: StateFlow<List<String>> =
        meshRouter?.relayLog ?: MutableStateFlow(emptyList<String>()).asStateFlow()

    private val _displayName = MutableStateFlow("کاربر محلی")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _selectedThreadId = MutableStateFlow<String?>(null)
    val selectedThreadId: StateFlow<String?> = _selectedThreadId.asStateFlow()

    private val _collapsedNodeIds = MutableStateFlow<Set<String>>(emptySet())
    val collapsedNodeIds: StateFlow<Set<String>> = _collapsedNodeIds.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // Nearby Connections state flows
    val meshServiceState: StateFlow<MeshServiceState> = transport?.serviceState
        ?: MutableStateFlow(MeshServiceState.STOPPED).asStateFlow()

    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = transport?.peers
        ?: MutableStateFlow(emptyList<DiscoveredPeer>()).asStateFlow()

    val meshError: StateFlow<String?> = transport?.lastError
        ?: MutableStateFlow<String?>(null).asStateFlow()

    // MeshRouter owns incoming-event persistence and relay processing to avoid duplicate writes.

    // Threads overview flow
    val threadListItems: StateFlow<List<ThreadListItem>> = combine(
        repository.rootThreadsFlow,
        repository.allEventsFlow
    ) { roots, allEvents ->
        roots.map { root ->
            val threadEvents = allEvents.filter { it.rootThreadId == root.id }
            val replies = threadEvents.filter { it.id != root.id }
            val latest = threadEvents.maxByOrNull { it.createdAt } ?: root
            val lastViewed = root.lastViewedAt ?: 0L
            val hasUnseen = threadEvents.any { it.createdAt > lastViewed && it.id != root.id }

            ThreadListItem(
                rootEvent = root,
                replyCount = replies.size,
                lastActivityTime = latest.createdAt,
                lastPreviewText = latest.text.take(120),
                hasUnseenChanges = hasUnseen
            )
        }.sortedByDescending { it.lastActivityTime }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Recent changes across all threads
    val recentChanges: StateFlow<List<RecentChangeEventItem>> = combine(
        repository.rootThreadsFlow,
        repository.allEventsFlow
    ) { roots, allEvents ->
        val rootMap = roots.associateBy { it.id }
        val eventMap = allEvents.associateBy { it.id }

        allEvents.filter { event ->
            val root = rootMap[event.rootThreadId]
            val lastViewed = root?.lastViewedAt ?: 0L
            event.createdAt > lastViewed && event.id != root?.id
        }.map { event ->
            val root = rootMap[event.rootThreadId]
            val parent = event.parentEventId?.let { eventMap[it] }
            RecentChangeEventItem(
                event = event,
                rootEventTitle = root?.text?.take(50) ?: "موضوع",
                parentPreviewText = parent?.text?.take(60)
            )
        }.sortedByDescending { it.event.createdAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateDisplayName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            _displayName.value = trimmed
        }
    }

    fun selectThread(threadId: String) {
        _selectedThreadId.value = threadId
        viewModelScope.launch {
            repository.markThreadAsViewed(threadId)
        }
    }

    fun toggleNodeCollapsed(nodeId: String) {
        _collapsedNodeIds.update { set ->
            if (set.contains(nodeId)) set - nodeId else set + nodeId
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun createTopic(text: String, scope: PublishScope, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.createRootThread(
                authorId = _localAuthorId.value,
                displayName = _displayName.value,
                text = text,
                scope = scope
            )
            result.onSuccess { event ->
                _userMessage.value = "موضوع اصلی با موفقیت ذخیره شد."
                if (event.publishScope == PublishScope.MESH_PUBLIC) {
                    viewModelScope.launch { flushPendingToConnectedPeers() }
                }
                onSuccess(event.id)
            }.onFailure { err ->
                _userMessage.value = err.message ?: "خطا در ثبت موضوع"
            }
        }
    }

    fun addReply(parentEventId: String, text: String, scope: PublishScope, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = repository.addReply(
                parentEventId = parentEventId,
                authorId = _localAuthorId.value,
                displayName = _displayName.value,
                text = text,
                scope = scope
            )
            result.onSuccess { event ->
                _userMessage.value = "پاسخ با موفقیت به ساختار گفتگو پیوست شد."
                if (event.publishScope == PublishScope.MESH_PUBLIC) {
                    viewModelScope.launch { flushPendingToConnectedPeers() }
                }
                onSuccess()
            }.onFailure { err ->
                _userMessage.value = err.message ?: "خطا در ثبت پاسخ"
            }
        }
    }

    fun recordStanceChange(targetEventId: String, explanation: String, scope: PublishScope, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = repository.recordStanceChange(
                targetEventId = targetEventId,
                authorId = _localAuthorId.value,
                displayName = _displayName.value,
                explanationText = explanation,
                scope = scope
            )
            result.onSuccess { event ->
                _userMessage.value = "تغییر دیدگاه بدون بازنویسی پست اصلی ثبت شد."
                if (event.publishScope == PublishScope.MESH_PUBLIC) {
                    viewModelScope.launch { flushPendingToConnectedPeers() }
                }
                onSuccess()
            }.onFailure { err ->
                _userMessage.value = err.message ?: "خطا در ثبت تغییر دیدگاه"
            }
        }
    }

    /**
     * Newly created public mesh posts are already persisted in the Room outbox by the repository.
     * If peers are connected now, flush the durable queue immediately instead of waiting for a
     * later connection-state change or a manual sync button.
     */
    private suspend fun flushPendingToConnectedPeers() {
        val router = meshRouter ?: return
        val connectedPeers = transport?.peers?.value
            ?.filter { it.status == PeerConnectionStatus.CONNECTED }
            .orEmpty()
        connectedPeers.forEach { peer ->
            router.flushOutboxToPeer(peer.endpointId)
        }
    }

    // Nearby Connections Controls
    fun toggleMeshService(enabled: Boolean, hasPermissions: Boolean) {
        if (transport == null) {
            _userMessage.value = "لایه انتقال Nearby در این محیط در دسترس نیست."
            return
        }

        if (enabled) {
            if (!hasPermissions) {
                _userMessage.value = "مجوزهای لازم بلوتوث و شبکه برای روشن‌کردن مش اعطا نشده است."
                return
            }
            transport.start(_displayName.value)
            _userMessage.value = "مش نزدیک روشن شد: در حال تبلیغ و جستجوی همتاها (P2P_CLUSTER)..."
        } else {
            transport.stop()
            _userMessage.value = "مش نزدیک خاموش شد."
        }
    }

    fun connectToPeer(endpointId: String) {
        transport?.requestConnection(endpointId)
    }

    fun acceptPeer(endpointId: String) {
        // Once the connection reaches CONNECTED, MeshRouter flushes the durable RelayPacket outbox.
        transport?.acceptConnection(endpointId)
    }

    fun rejectPeer(endpointId: String) {
        transport?.rejectConnection(endpointId)
    }

    fun syncMeshEventsToPeer(endpointId: String) {
        viewModelScope.launch {
            val count = meshRouter?.flushOutboxToPeer(endpointId) ?: 0
            _userMessage.value = "$count بستهٔ مش برای همتای انتخاب‌شده ارسال شد؛ تأیید ذخیرهٔ مقصد جداگانه دریافت می‌شود."
        }
    }
}
