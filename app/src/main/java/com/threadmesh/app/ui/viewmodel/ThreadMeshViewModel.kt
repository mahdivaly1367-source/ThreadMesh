package com.threadmesh.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threadmesh.app.data.model.EventEntity
import com.threadmesh.app.data.model.EventType
import com.threadmesh.app.data.model.PublishScope
import com.threadmesh.app.data.repository.RecentChangeEventItem
import com.threadmesh.app.data.repository.ThreadListItem
import com.threadmesh.app.data.repository.ThreadMeshRepository
import com.threadmesh.app.data.repository.TreeNode
import com.threadmesh.app.mesh.MeshRouter
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
    private val meshRouter: MeshRouter? = null,
    initialAuthorId: String = UUID.randomUUID().toString().take(8)
) : ViewModel() {

    private val _localAuthorId = MutableStateFlow(initialAuthorId)
    val localAuthorId: StateFlow<String> = _localAuthorId.asStateFlow()

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

    init {
        // Collect incoming mesh events from transport and persist in Room DB
        if (transport != null) {
            viewModelScope.launch {
                transport.incomingEvents.collect { incomingEvent ->
                    val result = repository.importIncomingMeshEvent(incomingEvent)
                    result.onSuccess { isNew ->
                        if (isNew) {
                            _userMessage.value = "رویداد عمومی جدید (${incomingEvent.displayName}) از شبکه مش دریافت و ذخیره شد."
                        }
                    }.onFailure { err ->
                        _userMessage.value = "رویداد نامعتبر از مش دریافت شد: ${err.message}"
                    }
                }
            }
        }
    }

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
            result.onSuccess {
                _userMessage.value = "پاسخ با موفقیت به ساختار گفتگو پیوست شد."
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
            result.onSuccess {
                _userMessage.value = "تغییر دیدگاه بدون بازنویسی پست اصلی ثبت شد."
                onSuccess()
            }.onFailure { err ->
                _userMessage.value = err.message ?: "خطا در ثبت تغییر دیدگاه"
            }
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
        // MeshRouter observes the CONNECTED transition and drains the durable RelayPacket outbox.
        // Do not bypass the relay envelope with raw EventEntity payloads here.
        transport?.acceptConnection(endpointId)
        _userMessage.value = "درخواست پذیرفته شد؛ پس از برقراری اتصال، صف پیام‌های مش ارسال می‌شود."
    }

    fun rejectPeer(endpointId: String) {
        transport?.rejectConnection(endpointId)
    }

    fun syncMeshEventsToPeer(endpointId: String) {
        viewModelScope.launch {
            val count = meshRouter?.flushOutboxToPeer(endpointId) ?: 0
            _userMessage.value = if (meshRouter == null) {
                "موتور انتقال مش در دسترس نیست."
            } else {
                "$count بستهٔ در انتظار انتقال برای این همتا ارسال شد."
            }
        }
    }

    override fun onCleared() {
        meshRouter?.close()
        super.onCleared()
    }
}
