package com.example.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.ConnectionState
import com.example.ui.components.AudioWaveformVisualizer
import com.example.ui.components.DiagnosticsBottomSheet
import com.example.ui.components.GlowAnimationCatalogue
import com.example.ui.components.GlowStylesBottomSheet
import com.example.ui.components.PulsatingRecordButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceTypingScreen(
    viewModel: VoiceTypingViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val isRecording by viewModel.isRecording.collectAsState()
    val isSmartMode by viewModel.isSmartMode.collectAsState()
    val isBubbleEnabled by viewModel.isBubbleEnabled.collectAsState()
    val isAccessibilityConnected by viewModel.isAccessibilityConnected.collectAsState()
    val isAecEnabled by viewModel.isAecEnabled.collectAsState()
    val isNoiseSuppressorEnabled by viewModel.isNoiseSuppressorEnabled.collectAsState()
    val isAecSupported = viewModel.isAecSupported
    val isNoiseSuppressorSupported = viewModel.isNoiseSuppressorSupported
    val connectionState by viewModel.connectionState.collectAsState()
    val finalizedTranscript by viewModel.finalizedTranscript.collectAsState()
    val interimTranscript by viewModel.interimTranscript.collectAsState()
    val amplitude by viewModel.audioAmplitude.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val diagnosticEntries by viewModel.diagnosticEntries.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val customApiKey by viewModel.customApiKey.collectAsState()
    val selectedGlowStyleId by viewModel.selectedGlowStyleId.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var showDiagnostics by remember { mutableStateOf(false) }
    val diagnosticsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showGlowStylesSheet by remember { mutableStateOf(false) }
    val glowStylesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // RECORD_AUDIO Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecording()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Audio recording permission is required for voice typing."
                )
            }
        }
    }

    val onToggleRecord = {
        if (isRecording) {
            viewModel.stopRecording()
        } else {
            val permissionCheck = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            )
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                viewModel.startRecording()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val fullTranscript = buildString {
        append(finalizedTranscript)
        if (interimTranscript.isNotEmpty()) {
            if (isNotEmpty()) append(" ")
            append(interimTranscript)
        }
    }.trim()

    val wordCount = remember(fullTranscript) {
        if (fullTranscript.isBlank()) 0
        else fullTranscript.split("\\s+".toRegex()).count { it.isNotBlank() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(8.dp))

                // Glow Animation Styles Picker Row (Opens 21 styles bottom sheet)
                val activeGlowStyle = GlowAnimationCatalogue.getById(selectedGlowStyleId)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            scope.launch {
                                drawerState.close()
                                showGlowStylesSheet = true
                            }
                        }
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "Glow Animation Styles",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Glow Animation Styles",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "21",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Active: ${activeGlowStyle.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Tap to preview & pick from 21 animations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "Open glow styles",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Smart Mode",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isRecording) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "On: cleans up filler words and adds punctuation. Off: raw verbatim transcript, fastest.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isRecording) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = isSmartMode,
                        onCheckedChange = { viewModel.setSmartMode(it) },
                        enabled = !isRecording,
                        modifier = Modifier.testTag("smart_mode_switch")
                    )
                }

                val isSaveRecordingsEnabled by viewModel.isSaveRecordingsEnabled.collectAsState()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Save audio recordings",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Saves 16 kHz WAV audio files locally on phone for review, playback, and export.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = isSaveRecordingsEnabled,
                        onCheckedChange = { viewModel.setSaveRecordingsEnabled(it) },
                        modifier = Modifier.testTag("save_recordings_switch")
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                // Everywhere Mode Section (Floating Bubble 🛟)
                Text(
                    text = "Everywhere Voice Bubble 🛟",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Floating Voice Ring",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Floats over TikTok, WhatsApp, etc. Tap to dictate, long-touch to drag anywhere.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = isBubbleEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !viewModel.canDrawOverlays()) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                                Toast.makeText(
                                    context,
                                    "Please grant 'Draw over other apps' permission",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            viewModel.setBubbleEnabled(enabled)
                        },
                        modifier = Modifier.testTag("floating_bubble_switch")
                    )
                }

                // Overlay Permission Status Button
                val hasOverlayPermission = viewModel.canDrawOverlays()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Draw Over Other Apps",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (hasOverlayPermission) "✓ Permission granted" else "Required for floating bubble",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasOverlayPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    if (!hasOverlayPermission) {
                        FilledTonalButton(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        ) {
                            Text("Grant", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Accessibility Service Status Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Keyboard & Field Detector",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isAccessibilityConnected) "✓ Service active" else "Required to auto-show bubble & paste text",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isAccessibilityConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    if (!isAccessibilityConnected) {
                        FilledTonalButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                                Toast.makeText(
                                    context,
                                    "Find 'VoxStream Voice Typing Assistant' and turn it ON",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        ) {
                            Text("Turn On", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

                // Microphone Audio Enhancements Section
                Text(
                    text = "Microphone Filters 🎙️",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )

                // Acoustic Echo Canceler Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Acoustic Echo Canceler",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Filters device speaker audio (TTS, media) from looping into the mic.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isAecSupported) "✓ Supported on this device" else "✗ Not supported by hardware",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isAecSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = isAecEnabled && isAecSupported,
                        onCheckedChange = { viewModel.setAecEnabled(it) },
                        enabled = isAecSupported && !isRecording,
                        modifier = Modifier.testTag("aec_switch")
                    )
                }

                // Noise Suppressor Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Noise Suppressor",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Reduces continuous background noise like fans, AC, and room hum.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isNoiseSuppressorSupported) "✓ Supported on this device" else "✗ Not supported by hardware",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isNoiseSuppressorSupported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Switch(
                        checked = isNoiseSuppressorEnabled && isNoiseSuppressorSupported,
                        onCheckedChange = { viewModel.setNoiseSuppressorEnabled(it) },
                        enabled = isNoiseSuppressorSupported && !isRecording,
                        modifier = Modifier.testTag("noise_suppressor_switch")
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        },
        modifier = modifier.fillMaxSize()
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            },
                            modifier = Modifier.testTag("drawer_hamburger_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = "Open settings drawer"
                            )
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.GraphicEq,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "VoxStream",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                actions = {
                    // Protocol status pill
                    ConnectionStatusChip(connectionState = connectionState)

                    // Diagnostics / Log Button
                    IconButton(
                        onClick = { showDiagnostics = true },
                        modifier = Modifier.testTag("open_diagnostics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Terminal,
                            contentDescription = "Protocol diagnostics",
                            tint = if (stats.lastError != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Stats Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusColor = when (connectionState) {
                        is ConnectionState.Streaming -> Color(0xFF10B981)
                        is ConnectionState.Connecting, is ConnectionState.ConnectedWaitingSetup -> Color(0xFFF59E0B)
                        is ConnectionState.Error -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.outline
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRecording) {
                            val mins = stats.durationSeconds / 60
                            val secs = stats.durationSeconds % 60
                            String.format("LIVE %02d:%02d", mins, secs)
                        } else "STANDBY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                Text(
                    text = "$wordCount words • ${fullTranscript.length} chars",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Live Transcript Display Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .testTag("transcript_display_card")
            ) {
                val scrollState = rememberScrollState()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    if (fullTranscript.isEmpty() && !isRecording) {
                        // Empty State Placeholder
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Tap the microphone to start dictating",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Real-time streaming speech-to-text via Gemini Live",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        // Active / Committed Transcript
                        val annotatedText = buildAnnotatedString {
                            if (finalizedTranscript.isNotEmpty()) {
                                withStyle(
                                    SpanStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 18.sp,
                                        letterSpacing = 0.2.sp
                                    )
                                ) {
                                    append(finalizedTranscript)
                                }
                            }
                            if (interimTranscript.isNotEmpty()) {
                                if (finalizedTranscript.isNotEmpty()) append(" ")
                                withStyle(
                                    SpanStyle(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontStyle = FontStyle.Italic,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 18.sp
                                    )
                                ) {
                                    append(interimTranscript)
                                }
                            }
                        }

                        Text(
                            text = annotatedText,
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                                .testTag("transcript_text")
                        )
                    }

                    // Floating action toolbar at bottom of transcript
                    if (fullTranscript.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                                .padding(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val copied = viewModel.copyTranscript(context)
                                    if (copied) {
                                        Toast.makeText(context, "Transcript copied", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(36.dp).testTag("copy_transcript_button")
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy transcript", modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = {
                                    val sendIntent: Intent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, fullTranscript)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Share Transcript")
                                    context.startActivity(shareIntent)
                                },
                                modifier = Modifier.size(36.dp).testTag("share_transcript_button")
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = "Share transcript", modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = { viewModel.clearTranscript() },
                                modifier = Modifier.size(36.dp).testTag("clear_transcript_button")
                            ) {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear transcript", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Audio Waveform Visualizer
            AudioWaveformVisualizer(
                amplitude = amplitude,
                isRecording = isRecording,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Central Pulsating Record Button
            PulsatingRecordButton(
                isRecording = isRecording,
                onClick = onToggleRecord,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Text(
                text = if (isRecording) "Listening... tap to stop" else "Tap to start voice typing",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    }
    }

    // Diagnostics Sheet
    if (showDiagnostics) {
        val recordings by viewModel.recordings.collectAsState()
        DiagnosticsBottomSheet(
            sheetState = diagnosticsSheetState,
            stats = stats,
            connectionState = connectionState,
            liveLogs = logs,
            diagnosticEntries = diagnosticEntries,
            notes = notes,
            recordings = recordings,
            isDictating = isRecording,
            selectedModel = selectedModel,
            apiKeyConfigured = viewModel.isApiKeyConfigured(),
            customApiKey = customApiKey,
            isSmartMode = isSmartMode,
            onCustomApiKeyChange = { viewModel.setCustomApiKey(it) },
            onAddNote = { viewModel.addNote(it) },
            onClearAllDiagnostics = { viewModel.clearAllDiagnostics() },
            onDeleteRecording = { viewModel.deleteRecording(it) },
            onClearAllRecordings = { viewModel.clearAllRecordings() },
            onDismiss = { showDiagnostics = false }
        )
    }

    if (showGlowStylesSheet) {
        GlowStylesBottomSheet(
            sheetState = glowStylesSheetState,
            selectedStyleId = selectedGlowStyleId,
            onSelectStyle = { styleId ->
                viewModel.setGlowStyle(styleId)
            },
            onDismiss = { showGlowStylesSheet = false }
        )
    }
}

@Composable
private fun ConnectionStatusChip(connectionState: ConnectionState) {
    val (label, bg, fg) = when (connectionState) {
        is ConnectionState.Streaming -> Triple("LIVE", Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF10B981))
        is ConnectionState.Connecting -> Triple("CONNECTING", Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFF59E0B))
        is ConnectionState.ConnectedWaitingSetup -> Triple("SETUP...", Color(0xFF3B82F6).copy(alpha = 0.15f), Color(0xFF3B82F6))
        is ConnectionState.Stopping -> Triple("STOPPING", Color(0xFF6B7280).copy(alpha = 0.15f), Color(0xFF6B7280))
        is ConnectionState.Error -> Triple("ERROR", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
        is ConnectionState.Idle -> Triple("IDLE", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("connection_status_chip")
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}
