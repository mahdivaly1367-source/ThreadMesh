package com.threadmesh.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threadmesh.app.data.model.PublishScope
import com.threadmesh.app.ui.theme.*
import com.threadmesh.app.ui.viewmodel.ThreadMeshViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    parentEventId: String? = null,
    parentPreview: String? = null,
    viewModel: ThreadMeshViewModel,
    onBackClick: () -> Unit,
    onSuccess: () -> Unit
) {
    val isReply = parentEventId != null
    var text by remember { mutableStateOf("") }
    var selectedScope by remember { mutableStateOf(PublishScope.LOCAL_ONLY) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val maxChars = 4000

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isReply) "پاسخ به یادداشت" else "شروع موضوع جدید",
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Parent preview if replying
            if (isReply && !parentPreview.isNullOrEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "در پاسخ به یادداشت:",
                            style = MaterialTheme.typography.labelSmall,
                            color = Indigo600,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = parentPreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate600,
                            maxLines = 3
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Input card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            if (it.length <= maxChars) {
                                text = it
                                errorMessage = null
                            }
                        },
                        label = { Text(if (isReply) "متن پاسخ خود را بنویسید..." else "موضوع بحث و استدلال اولیه...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Slate900,
                            unfocusedBorderColor = Slate200
                        ),
                        isError = errorMessage != null
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Character counter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorMessage ?: "حداکثر ۴۰۰۰ نویسه مجاز است.",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (errorMessage != null) Rose600 else Slate400
                        )
                        Text(
                            text = "${text.length} / $maxChars",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (text.length > 3900) Rose600 else Slate500
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scope Selector
            Text(
                text = "دامنهٔ انتشار رویداد:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )
            Spacer(modifier = Modifier.height(8.dp))

            PublishScope.values().forEach { scope ->
                val isSelected = selectedScope == scope
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { selectedScope = scope },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Indigo50 else Color.White,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) Indigo600 else Slate200
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedScope = scope }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = scope.labelFa,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                            if (scope != PublishScope.LOCAL_ONLY) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Amber50)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "توجه: ${PublishScope.PHASE1_NOTICE}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = Amber600,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val trimmed = text.trim()
                    if (trimmed.isEmpty()) {
                        errorMessage = "متن نمی‌تواند خالی باشد."
                        return@Button
                    }
                    if (trimmed.length > maxChars) {
                        errorMessage = "طول متن بیش از ۴۰۰۰ کاراکتر است."
                        return@Button
                    }

                    if (isReply) {
                        viewModel.addReply(
                            parentEventId = parentEventId!!,
                            text = trimmed,
                            scope = selectedScope,
                            onSuccess = onSuccess
                        )
                    } else {
                        viewModel.createTopic(
                            text = trimmed,
                            scope = selectedScope,
                            onSuccess = { onSuccess() }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Slate900)
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isReply) "ثبت محلی پاسخ" else "ایجاد موضوع در پایگاه محلی",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
