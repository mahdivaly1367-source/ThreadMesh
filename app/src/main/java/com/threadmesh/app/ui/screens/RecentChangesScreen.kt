package com.threadmesh.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChangeCircle
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threadmesh.app.data.model.EventType
import com.threadmesh.app.ui.theme.*
import com.threadmesh.app.ui.viewmodel.ThreadMeshViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentChangesScreen(
    viewModel: ThreadMeshViewModel,
    onThreadClick: (String) -> Unit
) {
    val recentChanges by viewModel.recentChanges.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "تغییرات جدید و رویدادهای تازه",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "بر اساس زمان‌سنجی رویدادهای ثبت‌شده در این دستگاه",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate500
                        )
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
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Slate600,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "رویدادهای دریافت‌شده پس از آخرین بازکردن هر موضوع در حافظهٔ دستگاه نمایش داده می‌شوند.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate600
                    )
                }
            }

            if (recentChanges.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "تغییر جدیدی ثبت نشده است",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Slate700
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "تمامی گفتگوهای محلی را تا آخرین رویداد مشاهده کرده‌اید.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate500
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recentChanges, key = { it.event.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onThreadClick(item.event.rootThreadId) },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val isStance = item.event.eventType == EventType.STANCE_CHANGED
                                        Icon(
                                            imageVector = if (isStance) Icons.Default.ChangeCircle else Icons.Default.ChatBubbleOutline,
                                            contentDescription = null,
                                            tint = if (isStance) Sky600 else Indigo600,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isStance) "تغییر دیدگاه از ${item.event.displayName}" else "پاسخ از ${item.event.displayName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate800
                                        )
                                    }

                                    Text(
                                        text = formatPersianTime(item.event.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Slate400
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = "در موضوع: ${item.rootEventTitle}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Slate500
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = item.event.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Slate900,
                                    maxLines = 3
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
