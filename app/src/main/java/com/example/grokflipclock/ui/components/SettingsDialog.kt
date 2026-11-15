package com.example.grokflipclock.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grokflipclock.data.local.AiProvider
import com.example.grokflipclock.data.local.ClockThemeMode
import com.example.grokflipclock.data.local.UserSettings

@Composable
fun SettingsDialog(
    currentSettings: UserSettings,
    onDismiss: () -> Unit,
    onSave: (
        aiProvider: AiProvider,
        grokApiKey: String,
        geminiApiKey: String,
        grokModelName: String,
        geminiModelName: String,
        systemPrompt: String,
        themeMode: ClockThemeMode,
        is24Hour: Boolean,
        useDeviceLocation: Boolean,
        manualCity: String
    ) -> Unit
) {
    var aiProvider by remember { mutableStateOf(currentSettings.aiProvider) }
    var grokApiKey by remember { mutableStateOf(currentSettings.grokApiKey) }
    var geminiApiKey by remember { mutableStateOf(currentSettings.geminiApiKey) }
    var showKey by remember { mutableStateOf(false) }
    var grokModelName by remember { mutableStateOf(currentSettings.grokModelName) }
    var geminiModelName by remember { mutableStateOf(currentSettings.geminiModelName) }
    var systemPrompt by remember { mutableStateOf(currentSettings.systemPrompt) }
    var themeMode by remember { mutableStateOf(currentSettings.themeMode) }
    var is24Hour by remember { mutableStateOf(currentSettings.is24Hour) }
    var useDeviceLocation by remember { mutableStateOf(currentSettings.useDeviceLocation) }
    var manualCity by remember { mutableStateOf(currentSettings.manualCity) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "時鐘與 AI 助理設定", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "選擇 AI 引擎", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = aiProvider == AiProvider.GROK,
                        onClick = { aiProvider = AiProvider.GROK },
                        label = { Text("Grok (xAI)") }
                    )
                    FilterChip(
                        selected = aiProvider == AiProvider.GEMINI,
                        onClick = { aiProvider = AiProvider.GEMINI },
                        label = { Text("Gemini (Google)") }
                    )
                }

                if (aiProvider == AiProvider.GROK) {
                    OutlinedTextField(
                        value = grokApiKey,
                        onValueChange = { grokApiKey = it },
                        label = { Text("xAI Grok API Key (xai-...)") },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "顯示/隱藏 API Key"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = grokModelName,
                        onValueChange = { grokModelName = it },
                        label = { Text("Grok 模型 (如 grok-3 / grok-3-mini)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = geminiApiKey,
                        onValueChange = { geminiApiKey = it },
                        label = { Text("Google Gemini API Key (AIza...)") },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "顯示/隱藏 API Key"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = geminiModelName,
                        onValueChange = { geminiModelName = it },
                        label = { Text("Gemini 模型 (如 gemini-2.5-flash)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("AI 人設與回覆風格 (System Prompt)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(text = "視覺主題", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = themeMode == ClockThemeMode.OLED_BLACK,
                        onClick = { themeMode = ClockThemeMode.OLED_BLACK },
                        label = { Text("OLED 純黑") }
                    )
                    FilterChip(
                        selected = themeMode == ClockThemeMode.JAPANESE_MINIMAL,
                        onClick = { themeMode = ClockThemeMode.JAPANESE_MINIMAL },
                        label = { Text("日系簡約") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("24 小時制顯示", fontSize = 14.sp)
                    Switch(checked = is24Hour, onCheckedChange = { is24Hour = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("使用手機 GPS 自動定位天氣", fontSize = 14.sp)
                    Switch(checked = useDeviceLocation, onCheckedChange = { useDeviceLocation = it })
                }

                if (!useDeviceLocation) {
                    OutlinedTextField(
                        value = manualCity,
                        onValueChange = { manualCity = it },
                        label = { Text("手動城市名稱 (如 Hong Kong, Tokyo)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        aiProvider,
                        grokApiKey,
                        geminiApiKey,
                        grokModelName,
                        geminiModelName,
                        systemPrompt,
                        themeMode,
                        is24Hour,
                        useDeviceLocation,
                        manualCity
                    )
                    onDismiss()
                }
            ) {
                Text("儲存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
