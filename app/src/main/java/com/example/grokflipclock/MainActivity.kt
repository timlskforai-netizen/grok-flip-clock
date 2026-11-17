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
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
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
            var manualHideAiPanel by remember { mutableStateOf(false) }

            // 偵測系統是否處於分割畫面 / 多重視窗模式
            val configuration = LocalConfiguration.current
            val isInSplitScreen = remember(configuration) {
                this@MainActivity.isInMultiWindowMode
            }
            val showAiPanel = !isInSplitScreen && !manualHideAiPanel

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
                        // 純時鐘模式（進入系統分割畫面時自動啟用，佔滿整個 70% 視窗）
                        Box(modifier = Modifier.fillMaxSize()) {
                            FlipClockPanel(
                                now = now,
                                is24Hour = settings.is24Hour,
                                weather = weather,
                                isFullscreen = isFullscreen,
                                onToggleTheme = { viewModel.toggleTheme() },
                                onToggleFullscreen = { isFullscreen = !isFullscreen },
                                onOpenSettings = { showSettingsDialog = true },
                                modifier = Modifier.fillMaxSize()
                            )

                            // 非分割畫面時保留一個低調按鈕，方便手動切換顯示內置 AI 面板
                            if (!isInSplitScreen) {
                                IconButton(
                                    onClick = { manualHideAiPanel = !manualHideAiPanel },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(12.dp)
                                        .size(32.dp)
                                        .alpha(0.35f)
                                        .background(palette.cardBackground, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = "顯示/隱藏內置 AI 面板",
                                        tint = palette.secondaryText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    } else if (isLandscape) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .weight(0.7f)
                                    .fillMaxHeight()
                            ) {
                                FlipClockPanel(
                                    now = now,
                                    is24Hour = settings.is24Hour,
                                    weather = weather,
                                    isFullscreen = isFullscreen,
                                    onToggleTheme = { viewModel.toggleTheme() },
                                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                                    onOpenSettings = { showSettingsDialog = true },
                                    modifier = Modifier.fillMaxSize()
                                )

                                IconButton(
                                    onClick = { manualHideAiPanel = true },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(12.dp)
                                        .size(32.dp)
                                        .alpha(0.35f)
                                        .background(palette.cardBackground, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = "隱藏內置 AI 面板",
                                        tint = palette.secondaryText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(palette.divider)
                            )

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
                                modifier = Modifier
                                    .weight(0.3f)
                                    .fillMaxHeight()
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .weight(0.7f)
                                    .fillMaxWidth()
                            ) {
                                FlipClockPanel(
                                    now = now,
                                    is24Hour = settings.is24Hour,
                                    weather = weather,
                                    isFullscreen = isFullscreen,
                                    onToggleTheme = { viewModel.toggleTheme() },
                                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                                    onOpenSettings = { showSettingsDialog = true },
                                    modifier = Modifier.fillMaxSize()
                                )

                                IconButton(
                                    onClick = { manualHideAiPanel = true },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(12.dp)
                                        .size(32.dp)
                                        .alpha(0.35f)
                                        .background(palette.cardBackground, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = "隱藏內置 AI 面板",
                                        tint = palette.secondaryText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .height(1.dp)
                                    .fillMaxWidth()
                                    .background(palette.divider)
                            )

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
                                modifier = Modifier
                                    .weight(0.3f)
                                    .fillMaxWidth()
                            )
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
