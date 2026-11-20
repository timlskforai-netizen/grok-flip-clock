package com.example.grokflipclock

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.grokflipclock.ui.ClockViewModel
import com.example.grokflipclock.ui.components.FlipClockPanel
import com.example.grokflipclock.ui.components.GrokPanel
import com.example.grokflipclock.ui.components.SettingsDialog
import com.example.grokflipclock.ui.theme.GrokFlipClockTheme
import com.example.grokflipclock.ui.theme.LocalClockPalette

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val viewModel: ClockViewModel = viewModel()
            val settings by viewModel.settings.collectAsState()
            val now by viewModel.now.collectAsState()
            val weather by viewModel.weather.collectAsState()
            val messages by viewModel.chatMessages.collectAsState()
            val isAmbientLoading by viewModel.isAmbientLoading.collectAsState()
            val isChatLoading by viewModel.isChatLoading.collectAsState()

            var isFullscreen by remember { mutableStateOf(true) }
            var showSettingsDialog by remember { mutableStateOf(false) }

            // 預設關閉內置 AI Chatbot，需人手按右上角 ✨ 先會打開
            var showAiPanel by remember { mutableStateOf(false) }

            val focusManager = LocalFocusManager.current
            val keyboardController = LocalSoftwareKeyboardController.current

            LaunchedEffect(showAiPanel) {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }

            val locationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    viewModel.refreshWeather()
                }
            }

            LaunchedEffect(Unit) {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }

            LaunchedEffect(isFullscreen) {
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                if (isFullscreen) {
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }

            GrokFlipClockTheme(themeMode = settings.themeMode) {
                val palette = LocalClockPalette.current

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette.background)
                        .then(if (!isFullscreen) Modifier.safeDrawingPadding() else Modifier)
                ) {
                    val isLandscape = maxWidth >= maxHeight

                    if (!showAiPanel) {
                        FlipClockPanel(
                            now = now,
                            is24Hour = settings.is24Hour,
                            weather = weather,
                            isFullscreen = isFullscreen,
                            showAiPanel = false,
                            onToggleAiPanel = { showAiPanel = true },
                            onToggleTheme = { viewModel.toggleTheme() },
                            onToggleFullscreen = { isFullscreen = !isFullscreen },
                            onOpenSettings = { showSettingsDialog = true },
                            onRefreshWeather = { viewModel.refreshWeather() },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (isLandscape) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            FlipClockPanel(
                                now = now,
                                is24Hour = settings.is24Hour,
                                weather = weather,
                                isFullscreen = isFullscreen,
                                showAiPanel = true,
                                onToggleAiPanel = { showAiPanel = false },
                                onToggleTheme = { viewModel.toggleTheme() },
                                onToggleFullscreen = { isFullscreen = !isFullscreen },
                                onOpenSettings = { showSettingsDialog = true },
                                onRefreshWeather = { viewModel.refreshWeather() },
                                modifier = Modifier
                                    .weight(0.7f)
                                    .fillMaxHeight()
                            )

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(palette.divider)
                            )

                            Box(
                                modifier = Modifier
                                    .weight(0.3f)
                                    .fillMaxHeight()
                            ) {
                                GrokPanel(
                                    providerLabel = settings.aiProvider.shortName,
                                    ambientNote = settings.ambientNote,
                                    isAmbientLoading = isAmbientLoading,
                                    messages = messages,
                                    isChatLoading = isChatLoading,
                                    hasApiKey = settings.activeApiKey.isNotBlank(),
                                    onRefreshAmbient = { viewModel.refreshAmbientNote() },
                                    onSendMessage = { viewModel.sendChatMessage(it) },
                                    onClearHistory = { viewModel.clearChatHistory() },
                                    modifier = Modifier.fillMaxSize()
                                )

                                IconButton(
                                    onClick = { showAiPanel = false },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(28.dp)
                                        .background(palette.cardBackground.copy(alpha = 0.85f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "關閉 AI 面板",
                                        tint = palette.secondaryText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            FlipClockPanel(
                                now = now,
                                is24Hour = settings.is24Hour,
                                weather = weather,
                                isFullscreen = isFullscreen,
                                showAiPanel = true,
                                onToggleAiPanel = { showAiPanel = false },
                                onToggleTheme = { viewModel.toggleTheme() },
                                onToggleFullscreen = { isFullscreen = !isFullscreen },
                                onOpenSettings = { showSettingsDialog = true },
                                onRefreshWeather = { viewModel.refreshWeather() },
                                modifier = Modifier
                                    .weight(0.7f)
                                    .fillMaxWidth()
                            )

                            Box(
                                modifier = Modifier
                                    .height(1.dp)
                                    .fillMaxWidth()
                                    .background(palette.divider)
                            )

                            Box(
                                modifier = Modifier
                                    .weight(0.3f)
                                    .fillMaxWidth()
                            ) {
                                GrokPanel(
                                    providerLabel = settings.aiProvider.shortName,
                                    ambientNote = settings.ambientNote,
                                    isAmbientLoading = isAmbientLoading,
                                    messages = messages,
                                    isChatLoading = isChatLoading,
                                    hasApiKey = settings.activeApiKey.isNotBlank(),
                                    onRefreshAmbient = { viewModel.refreshAmbientNote() },
                                    onSendMessage = { viewModel.sendChatMessage(it) },
                                    onClearHistory = { viewModel.clearChatHistory() },
                                    modifier = Modifier.fillMaxSize()
                                )

                                IconButton(
                                    onClick = { showAiPanel = false },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(28.dp)
                                        .background(palette.cardBackground.copy(alpha = 0.85f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "關閉 AI 面板",
                                        tint = palette.secondaryText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (showSettingsDialog) {
                        SettingsDialog(
                            currentSettings = settings,
                            onDismiss = { showSettingsDialog = false },
                            onSave = { provider, grokKey, geminiKey, grokModel, geminiModel, prompt, theme, is24, useGps, city ->
                                viewModel.saveSettings(
                                    provider,
                                    grokKey,
                                    geminiKey,
                                    grokModel,
                                    geminiModel,
                                    prompt,
                                    theme,
                                    is24,
                                    useGps,
                                    city
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
