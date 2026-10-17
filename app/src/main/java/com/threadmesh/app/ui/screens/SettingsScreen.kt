package com.threadmesh.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threadmesh.app.ui.theme.*
import com.threadmesh.app.ui.viewmodel.ThreadMeshViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ThreadMeshViewModel
) {
    val currentName by viewModel.displayName.collectAsState()
    val authorId by viewModel.localAuthorId.collectAsState()
    var tempName by remember(currentName) { mutableStateOf(currentName) }
    var savedNotice by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "تنظیمات هویت محلی",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
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
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "نام نمایشی در این دستگاه:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = {
                            tempName = it
                            savedNotice = false
                        },
                        label = { Text("نام یا نام مستعار") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Slate900,
                            unfocusedBorderColor = Slate200
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (tempName.isNotBlank()) {
                                viewModel.updateDisplayName(tempName)
                                savedNotice = true
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ذخیره نام نمایشی محلی")
                    }

                    if (savedNotice) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "نام نمایشی در پایگاه محلی به‌روزرسانی شد.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Emerald600
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Author UUID card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "شناسهٔ یکتای گره محلی (UUID):",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate500,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = authorId,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate800
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Non-claim disclaimer
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = Slate50,
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Slate600,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "توجه: این شناسه و نام، صرفاً مربوط به ذخیره‌سازی محلی روی همین دستگاه است. هیچ سرور مرکزی، ثبت‌نام آنلاین یا هویت تأییدشده‌ای در این فاز وجود ندارد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate600,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
