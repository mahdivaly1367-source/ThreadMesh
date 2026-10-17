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
import androidx.compose.material.icons.filled.ChangeCircle
import androidx.compose.material.icons.filled.Info
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
fun StanceChangeScreen(
    targetEventId: String,
    targetTextPreview: String,
    viewModel: ThreadMeshViewModel,
    onBackClick: () -> Unit,
    onSuccess: () -> Unit
) {
    var explanationText by remember { mutableStateOf("") }
    var selectedScope by remember { mutableStateOf(PublishScope.LOCAL_ONLY) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val maxChars = 4000

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ثبت تغییر دیدگاه (Stance Change)",
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
            // Explanatory Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Sky50,
                border = androidx.compose.foundation.BorderStroke(1.dp, Sky600.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Sky600,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "شفافیت دیدگاه: در ThreadMesh، دیدگاه قبلی شما پاک یا ویرایش پنهانی نمی‌شود؛ بلکه این رویداد تحول اندیشهٔ شما را با ارجاع به استدلال پیشین ثبت می‌کند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate800,
                        lineHeight = 20.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Previous stance reference
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "ارجاع به دیدگاه یا یادداشت اولیه:",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate500,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = targetTextPreview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate900
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Explanation input
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = explanationText,
                        onValueChange = {
                            if (it.length <= maxChars) {
                                explanationText = it
                                errorMessage = null
                            }
                        },
                        label = { Text("دیدگاه جدید شما چیست و چه عاملی باعث این تغییر شد؟") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Sky600,
                            unfocusedBorderColor = Slate200
                        ),
                        isError = errorMessage != null
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = errorMessage ?: "حداکثر ۴۰۰۰ نویسه",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (errorMessage != null) Rose600 else Slate400
                        )
                        Text(
                            text = "${explanationText.length} / $maxChars",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (explanationText.length > 3900) Rose600 else Slate500
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scope Selector
            Text(
                text = "دامنهٔ انتشار:",
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
                    color = if (isSelected) Sky50 else Color.White,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) Sky600 else Slate200
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
                                Text(
                                    text = "توجه: ${PublishScope.PHASE1_NOTICE}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = Amber600
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val trimmed = explanationText.trim()
                    if (trimmed.isEmpty()) {
                        errorMessage = "توضیح تغییر دیدگاه نمی‌تواند خالی باشد."
                        return@Button
                    }
                    if (trimmed.length > maxChars) {
                        errorMessage = "طول متن بیش از ۴۰۰۰ کاراکتر است."
                        return@Button
                    }

                    viewModel.recordStanceChange(
                        targetEventId = targetEventId,
                        explanation = trimmed,
                        scope = selectedScope,
                        onSuccess = onSuccess
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Sky600)
            ) {
                Icon(Icons.Default.ChangeCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ثبت رویداد تغییر دیدگاه در پایگاه محلی",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
