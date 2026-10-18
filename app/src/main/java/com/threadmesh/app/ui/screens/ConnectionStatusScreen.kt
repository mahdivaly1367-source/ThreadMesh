package com.threadmesh.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threadmesh.app.transport.DiscoveredPeer
import com.threadmesh.app.transport.MeshServiceState
import com.threadmesh.app.transport.PeerConnectionStatus
import com.threadmesh.app.ui.theme.*
import com.threadmesh.app.ui.viewmodel.ThreadMeshViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionStatusScreen(
    viewModel: ThreadMeshViewModel
) {
    val serviceState by viewModel.meshServiceState.collectAsState()
    val peers by viewModel.discoveredPeers.collectAsState()
    val errorMsg by viewModel.meshError.collectAsState()
    val userMsg by viewModel.userMessage.collectAsState()
    val routerStats by viewModel.meshRouterStats.collectAsState()
    val relayLog by viewModel.meshRelayLog.collectAsState()
    val authenticatedPeerIdentities by viewModel.authenticatedPeerIdentities.collectAsState()

    var permissionsGranted by remember { mutableStateOf(false) }
    var permissionDeniedNotice by remember { mutableStateOf(false) }

    // Required Nearby permissions based on Android SDK version
    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        permissionsGranted = allGranted
        if (allGranted) {
            permissionDeniedNotice = false
            viewModel.toggleMeshService(true, true)
        } else {
            permissionDeniedNotice = true
        }
    }

    val isMeshRunning = serviceState == MeshServiceState.RUNNING || serviceState == MeshServiceState.STARTING

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "شبکه همتا به همتا (Nearby Connections)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "ارتباط محلی آفلاین با استراتژی P2P_CLUSTER",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate500
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Slate100),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Master Switch Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(if (isMeshRunning) Emerald50 else Slate100),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isMeshRunning) Icons.Default.Sensors else Icons.Default.SensorsOff,
                                        contentDescription = null,
                                        tint = if (isMeshRunning) Emerald600 else Slate500,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "مش نزدیک (Nearby Connections)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate900
                                    )
                                    Text(
                                        text = if (isMeshRunning) "در حال تبلیغ و کشف دستگاه‌های همسایه" else "خاموش (هیچ رادیویی فعال نیست)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isMeshRunning) Emerald600 else Slate500
                                    )
                                }
                            }

                            Switch(
                                checked = isMeshRunning,
                                onCheckedChange = { enable ->
                                    if (enable) {
                                        permissionLauncher.launch(requiredPermissions)
                                    } else {
                                        viewModel.toggleMeshService(false, true)
                                    }
                                }
                            )
                        }

                        if (permissionDeniedNotice) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = Amber50,
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Amber600.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "مجوز بلوتوث/دستگاه‌های نزدیک رد شد. برای فعال‌سازی مش نیاز است این مجوزها از تنظیمات سیستم اعطا شوند.",
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Amber600
                                )
                            }
                        }

                        if (errorMsg != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                color = Rose600.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = errorMsg ?: "",
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Rose600
                                )
                            }
                        }
                    }
                }
            }

            // Architecture & Cluster Notice Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = Indigo600,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "شفافیت فنی استراتژی P2P_CLUSTER",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Indigo600
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "هر پیوند رادیویی Nearby مستقیم است؛ انتقال چندمرحله‌ای در لایهٔ برنامه و با صف ذخیره‌و‌انتقال بین چند پیوند انجام می‌شود. عملکرد واقعی آن باید روی چند گوشی و با قطع‌و‌وصل شبکه آزمایش شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate700,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // Discovered Peers Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "دستگاه‌های پیدا شده در مجاورت:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    Text(
                        text = "${peers.size} همتا",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate500
                    )
                }
            }

            if (!isMeshRunning) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "برای شروع جستجو، مش نزدیک را در بالا روشن کنید.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate400
                        )
                    }
                }
            } else if (peers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = Slate600
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "در حال پویش امواج بلوتوث و Wi-Fi برای همتاهای ThreadMesh...",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500
                            )
                        }
                    }
                }
            } else {
                items(peers, key = { it.endpointId }) { peer ->
                    PeerCard(
                        peer = peer,
                        authenticatedIdentity = authenticatedPeerIdentities[peer.endpointId],
                        onRequestConnect = { viewModel.connectToPeer(peer.endpointId) },
                        onAccept = { viewModel.acceptPeer(peer.endpointId) },
                        onReject = { viewModel.rejectPeer(peer.endpointId) },
                        onSync = { viewModel.syncMeshEventsToPeer(peer.endpointId) }
                    )
                }
            }

            // Live Router Diagnostics Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Analytics,
                                contentDescription = null,
                                tint = Indigo600,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "آمار زندهٔ موتور مش",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Slate900
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            DiagnosticMetric("دریافت‌شده", routerStats.packetsReceived.toString(), Modifier.weight(1f))
                            DiagnosticMetric("ذخیره‌شده", routerStats.packetsStoredLocally.toString(), Modifier.weight(1f))
                            DiagnosticMetric("ارسال‌شده", routerStats.packetsRelayed.toString(), Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            DiagnosticMetric("ACK معتبر", routerStats.acksReceived.toString(), Modifier.weight(1f))
                            DiagnosticMetric("رد امضا", routerStats.packetsDroppedSignature.toString(), Modifier.weight(1f))
                            DiagnosticMetric("مسیر خراب", routerStats.packetsDroppedMalformedRoute.toString(), Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "آخرین رویدادهای انتقال",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Slate700
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        if (relayLog.isEmpty()) {
                            Text(
                                text = "هنوز رویدادی از موتور مسیریابی ثبت نشده است.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate500
                            )
                        } else {
                            relayLog.take(5).forEach { message ->
                                Text(
                                    text = "• $message",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate700,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Security & Scope Filter Notice
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate50),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "ملاحظات امنیتی و فیلتر دامنه در این نسخه:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "۱. فقط رویدادهای MESH_PUBLIC از مش منتقل می‌شوند.\n۲. رویدادها با ECDSA امضا می‌شوند و پیش از ذخیره بررسی می‌گردند.\n۳. هر اتصال باید با چالش تصادفی و امضای ECDSA احراز هویت شود؛ ACK فقط از کلید تأییدشدهٔ همان اتصال پذیرفته می‌شود.\n۴. اثرانگشت کلید، هویت انسانی را اثبات نمی‌کند و فعلاً بین نصب‌های مجدد پین نمی‌شود.\n۵. انتقال واقعی چندمرحله‌ای باید با چند گوشی آزمایش شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate600,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun DiagnosticMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Indigo600
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Slate500
        )
    }
}


@Composable
fun PeerCard(
    peer: DiscoveredPeer,
    authenticatedIdentity: String? = null,
    onRequestConnect: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onSync: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = peer.endpointName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    Text(
                        text = "شناسه گره: ${peer.endpointId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate400
                    )
                }

                when (peer.status) {
                    PeerConnectionStatus.DISCOVERED -> {
                        Button(
                            onClick = onRequestConnect,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Slate900)
                        ) {
                            Text("درخواست اتصال", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    PeerConnectionStatus.CONNECTING -> {
                        Text(
                            text = "در حال اتصال...",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate500
                        )
                    }
                    PeerConnectionStatus.CONNECTED -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Emerald600)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "متصل",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Emerald600
                            )
                        }
                    }
                    PeerConnectionStatus.AUTHENTICATION_PENDING -> {
                        Text(
                            text = "نیاز به تأیید",
                            style = MaterialTheme.typography.labelSmall,
                            color = Sky600,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    PeerConnectionStatus.REJECTED -> {
                        Text(
                            text = "رد شد",
                            style = MaterialTheme.typography.labelSmall,
                            color = Rose600
                        )
                    }
                    PeerConnectionStatus.DISCONNECTED -> {
                        Text(
                            text = "قطع شد",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate400
                        )
                    }
                }
            }

            if (peer.status == PeerConnectionStatus.CONNECTED) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = if (authenticatedIdentity != null) Emerald50 else Amber50,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        (if (authenticatedIdentity != null) Emerald600 else Amber600).copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (authenticatedIdentity != null) {
                                "هویت کلید تأیید شد · SHA-256 " + authenticatedIdentity.take(12) + "…"
                            } else {
                                "در انتظار احراز هویت رمزنگاری‌شده؛ انتقال داده هنوز مجاز نیست."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (authenticatedIdentity != null) Emerald600 else Amber600
                        )
                    }
                }
            }

            // Handshake Authentication Code Box
            if (peer.status == PeerConnectionStatus.AUTHENTICATION_PENDING) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = Sky50,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Sky600.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = Sky600, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "کد تأیید دوطرفه (Handshake): ${peer.authDigits ?: "----"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Sky600
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "قبل از پذیرش، مطمئن شوید همین کد روی دستگاه مقابل نیز دیده می‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate700
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onReject) {
                                Text("رد اتصال", color = Rose600, style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = onAccept,
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("تأیید و پذیرش", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Sync button when connected
            if (peer.status == PeerConnectionStatus.CONNECTED) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onSync,
                        enabled = authenticatedIdentity != null,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("همگام‌سازی رویدادهای MESH_PUBLIC", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
