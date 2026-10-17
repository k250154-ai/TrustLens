package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.api.GeminiClient
import com.example.data.api.GeminiContent
import com.example.data.api.GeminiGenerateRequest
import com.example.data.api.GeminiPart
import com.example.data.api.GenerationConfigDto
import com.example.data.model.ExtractedClaim
import com.example.data.model.VerificationMode
import com.example.data.repository.TrustLensRepository
import com.example.service.TrustLensOverlayService
import com.example.ui.components.EvidenceDrawerSheet
import com.example.ui.components.PrivacyRedactionSheet
import com.example.ui.components.SpatialBadgeOverlay
import com.example.ui.testlab.HistoryScreen
import com.example.ui.testlab.TestFeedScreen
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                TrustLensApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustLensApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { TrustLensRepository(context) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Feed, 1: Overlay, 2: History, 3: Settings
    var isOverlayPermissionGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val isServiceRunning by TrustLensOverlayService.isServiceRunning.collectAsState()

    // Screen verification state
    var isScanning by remember { mutableStateOf(false) }
    val detectedClaims = remember { mutableStateListOf<ExtractedClaim>() }
    var selectedClaimForDrawer by remember { mutableStateOf<ExtractedClaim?>(null) }
    var isPrivacySheetOpen by remember { mutableStateOf(false) }
    var verificationMode by remember { mutableStateOf(VerificationMode.CLOUD_ASSISTED) }

    // Cached claims
    val cachedClaims = remember { mutableStateListOf<ExtractedClaim>() }

    fun refreshHistory() {
        scope.launch {
            val list = repository.getRecentVerifiedClaims()
            cachedClaims.clear()
            cachedClaims.addAll(list)
        }
    }

    LaunchedEffect(Unit) {
        refreshHistory()
    }

    // MediaProjection Permission Launcher
    val mediaProjectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            TrustLensOverlayService.startService(context, result.resultCode, result.data!!)
            Toast.makeText(context, "TrustLens Floating Assistant Started!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Screen capture permission declined.", Toast.LENGTH_SHORT).show()
        }
    }

    // Overlay Permission Launcher
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isOverlayPermissionGranted = Settings.canDrawOverlays(context)
    }

    fun startFloatingAssistant() {
        if (!isOverlayPermissionGranted) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            overlayPermissionLauncher.launch(intent)
            return
        }
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }

    fun triggerInAppScan() {
        isScanning = true
        scope.launch {
            try {
                val simulatedResult = repository.verifyScreenContent(
                    bitmap = android.graphics.Bitmap.createBitmap(1080, 1920, android.graphics.Bitmap.Config.ARGB_8888),
                    mode = verificationMode
                )
                detectedClaims.clear()
                detectedClaims.addAll(simulatedResult.claims)
                refreshHistory()
                Toast.makeText(
                    context,
                    "Detected ${simulatedResult.claims.size} claims with spatial bounding boxes!",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Scan error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isScanning = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        if (isServiceRunning) Color(0xFF00E676) else CyanPrimary,
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "TrustLens",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CyanPrimary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (isServiceRunning) "OVERLAY ACTIVE" else "GEMINI VISION",
                                    color = CyanPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { isPrivacySheetOpen = true },
                            modifier = Modifier.testTag("privacy_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Privacy Guard Scope",
                                tint = CyanPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("main_navigation_bar")
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.DynamicFeed, contentDescription = null) },
                        label = { Text("Feed Lab") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyanPrimary,
                            selectedTextColor = CyanPrimary,
                            indicatorColor = CyanPrimary.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_feed_tab")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Layers, contentDescription = null) },
                        label = { Text("Floating Hub") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyanPrimary,
                            selectedTextColor = CyanPrimary,
                            indicatorColor = CyanPrimary.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_overlay_tab")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = {
                            selectedTab = 2
                            refreshHistory()
                        },
                        icon = { Icon(Icons.Default.History, contentDescription = null) },
                        label = { Text("Evidence") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyanPrimary,
                            selectedTextColor = CyanPrimary,
                            indicatorColor = CyanPrimary.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_history_tab")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                        label = { Text("Engine") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyanPrimary,
                            selectedTextColor = CyanPrimary,
                            indicatorColor = CyanPrimary.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_settings_tab")
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (selectedTab) {
                    0 -> TestFeedScreen(
                        claims = detectedClaims,
                        isScanning = isScanning,
                        activeOverlayCount = detectedClaims.size,
                        onTriggerScan = { triggerInAppScan() },
                        onClaimSelected = { claim -> selectedClaimForDrawer = claim },
                        onOpenPrivacySettings = { isPrivacySheetOpen = true }
                    )
                    1 -> OverlayControlsScreen(
                        isOverlayPermissionGranted = isOverlayPermissionGranted,
                        isServiceRunning = isServiceRunning,
                        onGrantOverlayPermission = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            overlayPermissionLauncher.launch(intent)
                        },
                        onStartService = { startFloatingAssistant() },
                        onStopService = {
                            TrustLensOverlayService.stopService(context)
                            Toast.makeText(context, "Overlay Stopped", Toast.LENGTH_SHORT).show()
                        },
                        onSimulateScan = { triggerInAppScan() }
                    )
                    2 -> HistoryScreen(
                        claims = cachedClaims,
                        onSelectClaim = { claim -> selectedClaimForDrawer = claim },
                        onClearCache = {
                            scope.launch {
                                repository.clearCache()
                                refreshHistory()
                                Toast.makeText(context, "Evidence Cache Cleared", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    3 -> SettingsEngineScreen(
                        repository = repository,
                        verificationMode = verificationMode,
                        onModeChanged = { verificationMode = it },
                        onOpenPrivacySheet = { isPrivacySheetOpen = true }
                    )
                }
            }
        }

        // Spatial Badges Overlay (projected onto screen coordinates when claims are detected)
        if (detectedClaims.isNotEmpty()) {
            SpatialBadgeOverlay(
                claims = detectedClaims,
                onSelectClaim = { claim -> selectedClaimForDrawer = claim },
                onDismissAll = { detectedClaims.clear() }
            )
        }

        // Evidence Drawer Modal Bottom Sheet
        EvidenceDrawerSheet(
            claim = selectedClaimForDrawer,
            onDismiss = { selectedClaimForDrawer = null },
            onRecheck = { claim ->
                scope.launch {
                    val updatedResult = repository.verifyScreenContent(
                        bitmap = android.graphics.Bitmap.createBitmap(100, 100, android.graphics.Bitmap.Config.ARGB_8888),
                        mode = verificationMode
                    )
                    val updated = updatedResult.claims.find { it.id == claim.id } ?: claim
                    selectedClaimForDrawer = updated
                }
            }
        )

        // Privacy Redaction & Sharing Scope Sheet
        PrivacyRedactionSheet(
            isOpen = isPrivacySheetOpen,
            currentMode = verificationMode,
            onModeChanged = { verificationMode = it },
            onConfirmScan = { mode, regions ->
                verificationMode = mode
                triggerInAppScan()
            },
            onDismiss = { isPrivacySheetOpen = false }
        )
    }
}

@Composable
fun OverlayControlsScreen(
    isOverlayPermissionGranted: Boolean,
    isServiceRunning: Boolean,
    onGrantOverlayPermission: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onSimulateScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
            .testTag("overlay_controls_screen")
    ) {
        Text(
            text = "Floating Assistant Controls",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "TrustLens operates as a system-wide floating assistant over WhatsApp, Twitter/X, TikTok, News apps, or chats. Tapping the floating bubble captures the active frame, extracts claims via Gemini Vision, and anchors evidence badges.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Permission Card 1: Draw Over Other Apps
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "1. Display Over Other Apps",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isOverlayPermissionGranted) "Permission Granted" else "Required for floating draggable bubble",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOverlayPermissionGranted) Color(0xFF00E676) else MaterialTheme.colorScheme.error
                        )
                    }

                    if (isOverlayPermissionGranted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Granted",
                            tint = Color(0xFF00E676)
                        )
                    } else {
                        Button(
                            onClick = onGrantOverlayPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color(0xFF090D16)),
                            modifier = Modifier.testTag("grant_overlay_permission_button")
                        ) {
                            Text("Grant")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Service Status & Start Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "2. System Assistant Status",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (isServiceRunning) Color(0xFF00E676) else Color.Gray, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isServiceRunning) "Running (Floating bubble attached to screen)" else "Inactive (Stopped)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isServiceRunning) {
                    Button(
                        onClick = onStartService,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("start_floating_service_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPrimary,
                            contentColor = Color(0xFF090D16)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Floating Assistant", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onStopService,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("stop_floating_service_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop Floating Assistant", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Simulator Quick Trigger
        Text(
            text = "In-App Quick Test",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onSimulateScan,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("test_scan_simulation_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.FilterCenterFocus, contentDescription = null, tint = CyanPrimary)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Simulate Screen Scan & Project Badges", color = CyanPrimary)
        }
    }
}

@Composable
fun SettingsEngineScreen(
    repository: TrustLensRepository,
    verificationMode: VerificationMode,
    onModeChanged: (VerificationMode) -> Unit,
    onOpenPrivacySheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var inputKey by remember { mutableStateOf(repository.getEffectiveApiKey()) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf<String?>(null) }
    var isStatusSuccess by remember { mutableStateOf(false) }
    val hasKey = repository.hasValidApiKey()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
            .testTag("settings_engine_screen")
    ) {
        Text(
            text = "AI Intelligence & Gemini Vision",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "TrustLens uses Gemini Vision (gemini-3.5-flash) to examine your visible screen, detect claims, compute coordinates, and provide evidence-grounded verification.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Gemini API Key Input Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Gemini API Key Configuration",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (hasKey) "Status: Connected to Gemini" else "Status: Key not set (Required for live vision)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (hasKey) Color(0xFF00E676) else Color(0xFFFF9100)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = inputKey,
                    onValueChange = { inputKey = it },
                    label = { Text("Enter Gemini API Key") },
                    placeholder = { Text("AIzaSy...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gemini_api_key_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedLabelColor = CyanPrimary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            repository.saveCustomApiKey(inputKey)
                            Toast.makeText(context, "Gemini API Key saved!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = Color(0xFF090D16)),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_api_key_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save Key", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            isTestingConnection = true
                            connectionStatus = null
                            scope.launch {
                                try {
                                    val testKey = inputKey.trim().ifEmpty { repository.getEffectiveApiKey() }
                                    if (testKey.isEmpty()) {
                                        connectionStatus = "Please enter an API key first."
                                        isStatusSuccess = false
                                        return@launch
                                    }
                                    val request = GeminiGenerateRequest(
                                        contents = listOf(
                                            GeminiContent(parts = listOf(GeminiPart(text = "Respond with 'OK' if you can read this.")))
                                        ),
                                        generationConfig = GenerationConfigDto(maxOutputTokens = 10)
                                    )
                                    val response = withContext(Dispatchers.IO) {
                                        GeminiClient.api.generateContent("gemini-3.5-flash", testKey, request)
                                    }
                                    val reply = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                                    if (reply != null) {
                                        connectionStatus = "Connected! Gemini 3.5 Flash is ready for screen verification."
                                        isStatusSuccess = true
                                        repository.saveCustomApiKey(testKey)
                                    } else {
                                        connectionStatus = "Unexpected response from Gemini."
                                        isStatusSuccess = false
                                    }
                                } catch (e: Exception) {
                                    connectionStatus = "Error: ${e.message}"
                                    isStatusSuccess = false
                                } finally {
                                    isTestingConnection = false
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_gemini_connection_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = CyanPrimary)
                        } else {
                            Text("Test Key", color = CyanPrimary)
                        }
                    }
                }

                connectionStatus?.let { status ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = if (isStatusSuccess) Color(0xFF00363A) else Color(0xFF3E1F1F),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isStatusSuccess) Color(0xFF80F0FF) else Color(0xFFFF8A80),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Privacy Guard Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Local Privacy Protection Scope",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Active Mode: ${verificationMode.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onOpenPrivacySheet,
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                    modifier = Modifier.fillMaxWidth().testTag("open_privacy_sheet_button")
                ) {
                    Text("Adjust Local Redactions & Scope", color = CyanPrimary)
                }
            }
        }
    }
}
