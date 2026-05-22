package com.safesms.app

import android.content.Intent
import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

// ─── Palette ─────────────────────────────────────────────────────────────────
private val BgDeep        = Color(0xFF060D1F)
private val BgSurface     = Color(0xFF0D1B2E)
private val BgCard        = Color(0xFF0F2040)
private val AccentCyan    = Color(0xFF00D4FF)
private val SafeGreen     = Color(0xFF00E676)
private val ScamRed       = Color(0xFFFF3B5C)
private val SuspOrange    = Color(0xFFFF8C42)
private val TextPrimary   = Color(0xFFEFF6FF)
private val TextSecondary = Color(0xFF8899BB)
private val BorderColor   = Color(0xFF1E3A5F)

enum class AppTab { HOME, HISTORY, STATS, SETTINGS }

// ─── Activity ─────────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {

    private lateinit var modelController: SafeSMSModelController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {}

        val permissions = mutableListOf(Manifest.permission.RECEIVE_SMS)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionLauncher.launch(permissions.toTypedArray())

        val serviceIntent = Intent(this, ProtectionService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        modelController = SafeSMSModelController(this)
        handleIntent(intent)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = BgDeep) {
                    SafeSMSApp(modelController)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val smsBody = intent?.getStringExtra("sms_body")
        if (!smsBody.isNullOrBlank()) {
            SmsRepository.emitSms(smsBody)
        }
    }

    override fun onStart() { super.onStart(); SmsRepository.isUiActive = true }
    override fun onStop()  { super.onStop();  SmsRepository.isUiActive = false }
}

// ─── App Shell ────────────────────────────────────────────────────────────────
@Composable
fun SafeSMSApp(modelController: SafeSMSModelController) {
    var isModelReady by remember { mutableStateOf(modelController.isModelDownloaded()) }
    
    if (!isModelReady) {
        DownloadScreen(onDownloadComplete = { isModelReady = true })
    } else {
        var selectedTab by remember { mutableStateOf(AppTab.HOME) }
        Scaffold(
            containerColor = BgDeep,
            bottomBar = { SafeSMSBottomNav(selectedTab) { selectedTab = it } }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (selectedTab) {
                    AppTab.HOME     -> HomeScreen(modelController)
                    AppTab.HISTORY  -> HistoryScreen()
                    AppTab.STATS    -> StatsScreen()
                    AppTab.SETTINGS -> SettingsScreen()
                }
            }
        }
    }
}

// ─── Bottom Nav ───────────────────────────────────────────────────────────────
@Composable
fun SafeSMSBottomNav(selected: AppTab, onSelect: (AppTab) -> Unit) {
    val items = listOf(
        Triple(AppTab.HOME,     Icons.Filled.Security,  "Home"),
        Triple(AppTab.HISTORY,  Icons.Filled.List,      "History"),
        Triple(AppTab.STATS,    Icons.Filled.PieChart,  "Stats"),
        Triple(AppTab.SETTINGS, Icons.Filled.Settings,  "Settings")
    )
    NavigationBar(
        containerColor = BgSurface,
        tonalElevation = 0.dp,
        modifier = Modifier.drawBehind {
            drawLine(BorderColor, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
        }
    ) {
        items.forEach { (tab, icon, label) ->
            val isSelected = selected == tab
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp)) },
                label = {
                    Text(
                        label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = AccentCyan,
                    selectedTextColor   = AccentCyan,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor      = AccentCyan.copy(alpha = 0.12f)
                )
            )
        }
    }
}

// ─── HOME SCREEN ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(modelController: SafeSMSModelController) {
    var message       by remember { mutableStateOf("") }
    var result        by remember { mutableStateOf<VerificationResult?>(null) }
    var isProcessing  by remember { mutableStateOf(false) }
    var streamedText  by remember { mutableStateOf("") }
    var currentSender by remember { mutableStateOf("Manual Input") }

    val scope       = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Auto-analyze incoming SMS
    LaunchedEffect(Unit) {
        SmsRepository.incomingSmsFlow.collect { newSms ->
            message = newSms
            currentSender = "Incoming SMS"
            if (!isProcessing) {
                isProcessing = true
                result = null
                streamedText = ""
                scope.launch {
                    modelController.analyzeMessageStream(message)
                        .catch { e -> streamedText += "\nError: ${e.message}" }
                        .onCompletion {
                            val parsed = modelController.parseStreamedResult(streamedText)
                            result = parsed
                            isProcessing = false
                            SmsHistory.add(SmsHistoryEntry(message = message, sender = currentSender, result = parsed))
                        }
                        .collect { chunk -> streamedText += chunk }
                }
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "home")

    val shieldPulse by infiniteTransition.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shield"
    )
    val ringExpand by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "ring"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "termPulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // ── Header ──────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "SafeSMS",
                    fontWeight = FontWeight.Black,
                    fontSize = 30.sp,
                    color = TextPrimary,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    "On-Device AI  •  Gemma 4 LiteRT",
                    fontSize = 12.sp,
                    color = AccentCyan,
                    fontWeight = FontWeight.Medium
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SafeGreen.copy(alpha = 0.12f))
                    .border(1.dp, SafeGreen.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(SafeGreen))
                    Spacer(Modifier.width(6.dp))
                    Text("ACTIVE", color = SafeGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── Animated Shield Hero ─────────────────────────────────
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // Outer pulsing ring
            val outerAlpha = (1f - (ringExpand - 1f) / 0.3f).coerceIn(0f, 0.35f)
            Box(
                modifier = Modifier
                    .size((110 * ringExpand).dp)
                    .clip(CircleShape)
                    .border(1.dp, AccentCyan.copy(alpha = outerAlpha), CircleShape)
            )
            // Middle ring
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(AccentCyan.copy(0.15f), Color.Transparent))
                    )
                    .border(1.dp, AccentCyan.copy(0.25f), CircleShape)
            )
            // Icon
            Icon(
                Icons.Filled.Security,
                contentDescription = "Shield",
                tint = AccentCyan.copy(alpha = shieldPulse),
                modifier = Modifier.size(50.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        // ── Message Input Card ───────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = BgCard),
            border = BorderStroke(1.dp, BorderColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Message, null, tint = AccentCyan, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("MESSAGE INPUT", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    placeholder = { Text("Paste or type a message to scan…", color = TextSecondary, fontSize = 14.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = AccentCyan,
                        unfocusedBorderColor = BorderColor,
                        cursorColor          = AccentCyan,
                        focusedTextColor     = TextPrimary,
                        unfocusedTextColor   = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Analyze Button ────────────────────────────────────────
        val canAnalyze = !isProcessing && message.isNotBlank()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (canAnalyze)
                        Brush.horizontalGradient(listOf(AccentCyan, Color(0xFF006EFF)))
                    else
                        Brush.horizontalGradient(listOf(BgCard, BgCard))
                )
                .clickable(enabled = canAnalyze) {
                    isProcessing = true
                    result = null
                    streamedText = ""
                    currentSender = "Manual Input"
                    scope.launch {
                        modelController.analyzeMessageStream(message)
                            .catch { e -> streamedText += "\nError: ${e.message}" }
                            .onCompletion {
                                val parsed = modelController.parseStreamedResult(streamedText)
                                result = parsed
                                isProcessing = false
                                SmsHistory.add(SmsHistoryEntry(message = message, sender = currentSender, result = parsed))
                            }
                            .collect { chunk -> streamedText += chunk }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isProcessing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("ANALYZING…", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontSize = 14.sp)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, tint = if (canAnalyze) BgDeep else TextSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "ANALYZE MESSAGE",
                        color = if (canAnalyze) BgDeep else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Streaming Terminal ────────────────────────────────────
        if (isProcessing || (streamedText.isNotBlank() && result == null)) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF020810)),
                border = BorderStroke(1.dp, AccentCyan.copy(alpha = pulseAlpha)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(SafeGreen))
                        Spacer(Modifier.width(6.dp))
                        Text("GEMMA 4 INFERENCE ENGINE", color = SafeGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    val length = streamedText.length
                    val statusText = buildString {
                        append("⚡ Initializing LiteRT Engine...\n")
                        if (length > 10) append("🔍 Scanning message patterns...\n")
                        if (length > 50) append("🛡️ Evaluating threat vectors...\n")
                        if (length > 100) append("⏳ Generating assessment...\n")
                    }
                    Text(
                        text = statusText,
                        color = Color(0xFF00FF88),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // ── Result Card ───────────────────────────────────────────
        AnimatedVisibility(
            visible = !isProcessing && result != null,
            enter = fadeIn(tween(400)) + expandVertically(tween(400))
        ) {
            result?.let { res ->
                val (badgeColor, icon) = when (res.label.uppercase()) {
                    "SCAM"       -> ScamRed     to Icons.Filled.Warning
                    "SUSPICIOUS" -> SuspOrange  to Icons.Filled.Info
                    "ERROR"      -> TextSecondary to Icons.Filled.Warning
                    else         -> SafeGreen   to Icons.Filled.CheckCircle
                }
                val animBg by animateColorAsState(targetValue = badgeColor.copy(alpha = 0.08f), label = "cardBg")

                Card(
                    colors = CardDefaults.cardColors(containerColor = animBg),
                    border = BorderStroke(1.5.dp, badgeColor.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        // Label + Confidence ring
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(badgeColor.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, null, tint = badgeColor, modifier = Modifier.size(26.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(res.label.uppercase(), color = badgeColor, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 2.sp)
                                Text("AI Confidence", color = TextSecondary, fontSize = 11.sp)
                            }
                            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = res.confidence / 100f,
                                    modifier = Modifier.fillMaxSize(),
                                    color = badgeColor,
                                    trackColor = badgeColor.copy(alpha = 0.15f),
                                    strokeWidth = 4.dp
                                )
                                Text("${res.confidence}%", color = badgeColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Divider(color = badgeColor.copy(alpha = 0.15f))
                        Spacer(Modifier.height(14.dp))

                        // Why
                        Text("WHY", color = badgeColor.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(res.reason, color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp)

                        Spacer(Modifier.height(12.dp))

                        // Action chip
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(badgeColor.copy(alpha = 0.12f))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Lightbulb, null, tint = badgeColor, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(res.action, color = badgeColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

// ─── HISTORY SCREEN ───────────────────────────────────────────────────────────
@Composable
fun HistoryScreen() {
    val history by SmsHistory.entries.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgSurface)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column {
                Text("Scan History", fontWeight = FontWeight.Black, fontSize = 24.sp, color = TextPrimary)
                Text("${history.size} messages analyzed", fontSize = 13.sp, color = TextSecondary)
            }
        }

        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Inbox, null, tint = TextSecondary, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No messages scanned yet", color = TextSecondary, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Analyze a message on the Home tab", color = TextSecondary.copy(alpha = 0.5f), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history, key = { it.id }) { entry ->
                    HistoryCard(entry)
                }
            }
        }
    }
}

@Composable
fun HistoryCard(entry: SmsHistoryEntry) {
    var expanded by remember { mutableStateOf(false) }
    val (color, icon) = when (entry.result.label.uppercase()) {
        "SCAM"       -> ScamRed     to Icons.Filled.Warning
        "SUSPICIOUS" -> SuspOrange  to Icons.Filled.Info
        "ERROR"      -> TextSecondary to Icons.Filled.Warning
        else         -> SafeGreen   to Icons.Filled.CheckCircle
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .animateContentSize()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(entry.result.label.uppercase(), color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                        Text("• ${entry.result.confidence}%", color = TextSecondary, fontSize = 12.sp)
                    }
                    Text("${entry.formattedTime}  •  ${entry.sender}", color = TextSecondary, fontSize = 11.sp)
                }
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    null, tint = TextSecondary, modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                entry.preview,
                color = TextPrimary.copy(alpha = 0.75f),
                fontSize = 13.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Divider(color = BorderColor)
                Spacer(Modifier.height(10.dp))
                Text("Reason: ${entry.result.reason}", color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text("Action: ${entry.result.action}", color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ─── STATS SCREEN ─────────────────────────────────────────────────────────────
@Composable
fun StatsScreen() {
    val history by SmsHistory.entries.collectAsState()
    val total   = history.size
    val safe    = history.count { it.result.label.uppercase() == "SAFE" }
    val scam    = history.count { it.result.label.uppercase() == "SCAM" }
    val sus     = history.count { it.result.label.uppercase() == "SUSPICIOUS" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Statistics", fontWeight = FontWeight.Black, fontSize = 24.sp, color = TextPrimary)
        Text("Threat analysis overview", fontSize = 13.sp, color = TextSecondary)
        Spacer(Modifier.height(28.dp))

        // Donut chart
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (total > 0) {
                DonutChart(safe, scam, sus, total)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .border(3.dp, BorderColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("0", color = TextSecondary, fontWeight = FontWeight.Black, fontSize = 32.sp)
                            Text("SCANNED", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // Legend row
        if (total > 0) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(SafeGreen, "Safe")
                Spacer(Modifier.width(16.dp))
                LegendDot(ScamRed, "Scam")
                Spacer(Modifier.width(16.dp))
                LegendDot(SuspOrange, "Suspicious")
            }
            Spacer(Modifier.height(28.dp))
        }

        // Stat cards
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("TOTAL", total.toString(), AccentCyan, Icons.Filled.Shield, Modifier.weight(1f))
            StatCard("SAFE", safe.toString(), SafeGreen, Icons.Filled.CheckCircle, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard("SCAM", scam.toString(), ScamRed, Icons.Filled.Block, Modifier.weight(1f))
            StatCard("SUSPECT", sus.toString(), SuspOrange, Icons.Filled.Warning, Modifier.weight(1f))
        }

        Spacer(Modifier.height(20.dp))

        // Protection status card
        Card(
            colors = CardDefaults.cardColors(containerColor = BgCard),
            border = BorderStroke(1.dp, SafeGreen.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SafeGreen.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Security, null, tint = SafeGreen, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Protection Active", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Monitoring all incoming SMS in real-time", color = SafeGreen, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun DonutChart(safe: Int, scam: Int, suspicious: Int, total: Int) {
    val safeAngle = 360f * safe / total
    val scamAngle = 360f * scam / total
    val susAngle  = 360f * suspicious / total

    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw   = 30.dp.toPx()
            val pad  = sw / 2f
            val rect = Size(size.width - pad * 2, size.height - pad * 2)
            val tl   = Offset(pad, pad)
            val style = Stroke(width = sw, cap = StrokeCap.Butt)
            val gap  = 2f

            var angle = -90f
            if (safeAngle > 0) {
                drawArc(SafeGreen, angle, safeAngle - gap, false, topLeft = tl, size = rect, style = style)
                angle += safeAngle
            }
            if (scamAngle > 0) {
                drawArc(ScamRed, angle, scamAngle - gap, false, topLeft = tl, size = rect, style = style)
                angle += scamAngle
            }
            if (susAngle > 0) {
                drawArc(SuspOrange, angle, susAngle - gap, false, topLeft = tl, size = rect, style = style)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(total.toString(), color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 32.sp)
            Text("SCANNED", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
fun StatCard(label: String, value: String, color: Color, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f)),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, color = color, fontWeight = FontWeight.Black, fontSize = 28.sp)
            Text(label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }
    }
}

// ─── SETTINGS SCREEN ──────────────────────────────────────────────────────────
@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Settings", fontWeight = FontWeight.Black, fontSize = 24.sp, color = TextPrimary)
        Text("App configuration", fontSize = 13.sp, color = TextSecondary)

        Spacer(Modifier.height(24.dp))

        // App identity card
        Card(
            colors = CardDefaults.cardColors(containerColor = BgCard),
            border = BorderStroke(1.dp, BorderColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(AccentCyan, Color(0xFF006EFF)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Security, null, tint = Color.White, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("SafeSMS", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text("On-Device AI Scam Detector", color = TextSecondary, fontSize = 13.sp)
                    Text("Version 2.0  •  Gemma 4 LiteRT", color = AccentCyan, fontSize = 11.sp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        SettingsSection("AI Engine") {
            SettingRow(Icons.Filled.Memory,    "Model",    "Gemma 4 (gemma-4-E4B-it.litertlm)", AccentCyan)
            Divider(color = BorderColor, modifier = Modifier.padding(horizontal = 12.dp))
            SettingRow(Icons.Filled.Smartphone, "Runtime", "Google LiteRT — 100% On-Device",    AccentCyan)
            Divider(color = BorderColor, modifier = Modifier.padding(horizontal = 12.dp))
            SettingRow(Icons.Filled.CloudOff,  "Privacy",  "No data ever leaves your device",   SafeGreen)
        }

        Spacer(Modifier.height(14.dp))

        SettingsSection("Permissions") {
            SettingRow(Icons.Filled.Sms,          "Receive SMS",         "Required to intercept messages",    AccentCyan)
            Divider(color = BorderColor, modifier = Modifier.padding(horizontal = 12.dp))
            SettingRow(Icons.Filled.Notifications, "Post Notifications",  "Alert on threat detection",         AccentCyan)
            Divider(color = BorderColor, modifier = Modifier.padding(horizontal = 12.dp))
            SettingRow(Icons.Filled.RestartAlt,    "Boot Completed",      "Auto-start protection on reboot",   AccentCyan)
        }

        Spacer(Modifier.height(14.dp))

        SettingsSection("Data") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { SmsHistory.clear() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(ScamRed.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Delete, null, tint = ScamRed, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Clear History", color = ScamRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Remove all scanned message logs", color = TextSecondary, fontSize = 12.sp)
                }
                Icon(Icons.Filled.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            "SafeSMS  •  Built with Gemma 4 + LiteRT\n100% on-device  •  No cloud  •  No tracking",
            color = TextSecondary.copy(alpha = 0.6f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            lineHeight = 18.sp
        )
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        title.uppercase(),
        color = AccentCyan,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = BorderStroke(1.dp, BorderColor),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
fun SettingRow(icon: ImageVector, title: String, subtitle: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

// ─── DOWNLOAD SCREEN ──────────────────────────────────────────────────────────
@Composable
fun DownloadScreen(onDownloadComplete: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val downloader = remember { ModelDownloader(context) }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(24.dp))
        Text("AI Model Required", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text("SafeSMS requires the Gemma 4 LiteRT model (3.6GB) to run on-device inference securely.", color = TextSecondary, textAlign = TextAlign.Center, fontSize = 14.sp)
        
        Spacer(Modifier.height(48.dp))

        when (val state = downloadState) {
            is DownloadState.Idle -> {
                Button(
                    onClick = {
                        scope.launch {
                            downloader.downloadModel().collect { newState ->
                                downloadState = newState
                                if (newState is DownloadState.Completed) {
                                    onDownloadComplete()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDeep),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("DOWNLOAD MODEL (3.6GB)", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
            is DownloadState.Downloading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = state.progress,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = AccentCyan,
                        trackColor = BorderColor
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Downloading... ${(state.progress * 100).toInt()}%", color = AccentCyan, fontWeight = FontWeight.Bold)
                }
            }
            is DownloadState.Error -> {
                Text("Error: ${state.message}", color = ScamRed, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { downloadState = DownloadState.Idle },
                    colors = ButtonDefaults.buttonColors(containerColor = ScamRed, contentColor = Color.White)
                ) {
                    Text("RETRY")
                }
            }
            is DownloadState.Completed -> {
                Text("Download Complete!", color = SafeGreen, fontWeight = FontWeight.Bold)
            }
        }
    }
}
