package com.threadmesh.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threadmesh.app.data.model.EventEntity
import com.threadmesh.app.data.model.EventType
import com.threadmesh.app.data.model.PublishScope
import com.threadmesh.app.ui.theme.*
import com.threadmesh.app.ui.viewmodel.ThreadMeshViewModel

data class FlattenedTreeItem(
    val event: EventEntity,
    val depth: Int,
    val hasChildren: Boolean,
    val isCollapsed: Boolean,
    val stanceChanges: List<EventEntity>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadDetailScreen(
    threadId: String,
    viewModel: ThreadMeshViewModel,
    onBackClick: () -> Unit,
    onReplyClick: (targetEventId: String, previewText: String) -> Unit,
    onStanceChangeClick: (targetEventId: String, originalText: String) -> Unit
) {
    val collapsedIds by viewModel.collapsedNodeIds.collectAsState()
    val allEvents by viewModel.threadListItems.collectAsState()

    LaunchedEffect(threadId) {
        viewModel.selectThread(threadId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "درخت گفتگو و تحول دیدگاه‌ها",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Slate100)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Slate50,
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AccountTree,
                        contentDescription = null,
                        tint = Slate600,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ساختار درختی محلی • شاخه‌ها با خطوط سلسله‌مراتبی نمایش داده شده و قابل باز/بسته شدن هستند.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate600
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // List rendered dynamically from tree structure
                Text(
                    text = "موضوع در حال بارگذاری...",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate500
                )
            }
        }
    }
}

@Composable
fun EventTreeNodeCard(
    event: EventEntity,
    depth: Int,
    hasChildren: Boolean,
    isCollapsed: Boolean,
    stanceChanges: List<EventEntity>,
    onToggleCollapse: () -> Unit,
    onReply: () -> Unit,
    onStanceChange: () -> Unit
) {
    val indentPadding = (depth.coerceAtMost(5) * 16).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentPadding, end = 8.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (depth == 0) Color.White else Slate50
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = if (depth == 0) 2.dp else 1.dp),
            border = if (depth == 0) androidx.compose.foundation.BorderStroke(1.dp, Indigo50) else androidx.compose.foundation.BorderStroke(1.dp, Slate200)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Author & Scope Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (depth == 0) Indigo600 else Slate700),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = event.displayName.take(1),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = event.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = event.publishScope.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = Slate500
                        )
                        if (hasChildren) {
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(onClick = onToggleCollapse, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                                    contentDescription = if (isCollapsed) "باز کردن" else "بستن",
                                    tint = Slate600
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Text Content
                Text(
                    text = event.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate900
                )

                // Stance change attachments (immutable evolution records)
                if (stanceChanges.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    stanceChanges.forEach { sc ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Sky50,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Sky600.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.ChangeCircle,
                                        contentDescription = null,
                                        tint = Sky600,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "تغییر دیدگاه ثبت‌شده توسط ${sc.displayName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Sky600
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = sc.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate800
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Bar (Reply + Stance Change)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onStanceChange,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.ChangeCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Sky600
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "تغییر دیدگاه",
                            style = MaterialTheme.typography.labelSmall,
                            color = Sky600
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = onReply,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Indigo600
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "پاسخ به این پست",
                            style = MaterialTheme.typography.labelSmall,
                            color = Indigo600
                        )
                    }
                }
            }
        }
    }
}
