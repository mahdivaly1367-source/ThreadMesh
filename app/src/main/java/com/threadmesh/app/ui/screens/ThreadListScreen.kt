package com.threadmesh.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threadmesh.app.data.repository.ThreadListItem
import com.threadmesh.app.ui.theme.*
import com.threadmesh.app.ui.viewmodel.ThreadMeshViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadListScreen(
    viewModel: ThreadMeshViewModel,
    onThreadClick: (String) -> Unit,
    onCreateThreadClick: () -> Unit
) {
    val items by viewModel.threadListItems.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "گفتگوهای شبکه محلی",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ThreadMesh • کاملاً آفلاین و متنی",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate500
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateThreadClick,
                containerColor = Slate900,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "ایجاد موضوع جدید")
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "هنوز موضوعی آغاز نشده است",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate700
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "برای شروع اولین شاخهٔ گفتگوی محلی، دکمهٔ + را لمس کنید.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate500
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Slate100),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.rootEvent.id }) { item ->
                    ThreadCard(item = item, onClick = { onThreadClick(item.rootEvent.id) })
                }
            }
        }
    }
}

@Composable
fun ThreadCard(
    item: ThreadListItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.rootEvent.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Indigo600,
                    fontWeight = FontWeight.SemiBold
                )
                if (item.hasUnseenChanges) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Amber50)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "تغییر جدید",
                            color = Amber600,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.rootEvent.text,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = Slate900
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (item.replyCount > 0) {
                Text(
                    text = "آخرین فعالیت: ${item.lastPreviewText}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Slate400
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${item.replyCount} پاسخ",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate500
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Slate400
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = formatPersianTime(item.lastActivityTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate500
                    )
                }
            }
        }
    }
}

fun formatPersianTime(timeMillis: Long): String {
    val diff = System.currentTimeMillis() - timeMillis
    return when {
        diff < 60_000 -> "هم‌اکنون"
        diff < 3600_000 -> "${diff / 60_000} دقیقه پیش"
        diff < 86400_000 -> "${diff / 3600_000} ساعت پیش"
        else -> SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(timeMillis))
    }
}
