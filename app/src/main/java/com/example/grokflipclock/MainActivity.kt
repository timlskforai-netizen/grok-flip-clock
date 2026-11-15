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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

        // 保持螢幕長亮（桌面時鐘防止自動休眠）
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

            // 啟動時請求粗略定位權限以取得本地天氣
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

            // 沉浸式全螢幕狀態列控制
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

                    if (isLandscape) {
                        // 橫向模式：左 70% 翻頁鐘 + 右 30% Grok
                        Row(modifier = Modifier.fillMaxSize()) {
                            FlipClockPanel(
                                now = now,
                                is24Hour = settings.is24Hour,
                                weather = weather,
                                isFullscreen = isFullscreen,
                                onToggleTheme = { viewModel.toggleTheme() },
                                onToggleFullscreen = { isFullscreen = !isFullscreen },
                                onOpenSettings = { showSettingsDialog = true },
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

                            GrokPanel(
                                ambientNote = settings.ambientNote,
                                isAmbientLoading = isAmbientLoading,
                                messages = messages,
                                isChatLoading = isChatLoading,
                                hasApiKey = settings.apiKey.isNotBlank(),
                                onRefreshAmbient = { viewModel.refreshAmbientNote() },
                                onSendMessage = { viewModel.sendChatMessage(it) },
                                onClearHistory = { viewModel.clearChatHistory() },
                                modifier = Modifier
                                    .weight(0.3f)
                                    .fillMaxHeight()
                            )
                        }
                    } else {
                        // 直向模式：上 70% 翻頁鐘 + 下 30% Grok
                        Column(modifier = Modifier.fillMaxSize()) {
                            FlipClockPanel(
                                now = now,
                                is24Hour = settings.is24Hour,
                                weather = weather,
                                isFullscreen = isFullscreen,
                                onToggleTheme = { viewModel.toggleTheme() },
                                onToggleFullscreen = { isFullscreen = !isFullscreen },
                                onOpenSettings = { showSettingsDialog = true },
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

                            GrokPanel(
                                ambientNote = settings.ambientNote,
                                isAmbientLoading = isAmbientLoading,
                                messages = messages,
                                isChatLoading = isChatLoading,
                                hasApiKey = settings.apiKey.isNotBlank(),
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
                            onSave = { key, model, prompt, theme, is24, useGps, city ->
                                viewModel.saveSettings(key, model, prompt, theme, is24, useGps, city)
                            }
                        )
                    }
                }
            }
        }
    }
}
