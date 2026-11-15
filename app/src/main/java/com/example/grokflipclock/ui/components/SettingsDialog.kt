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
import com.example.grokflipclock.data.local.ClockThemeMode
import com.example.grokflipclock.data.local.UserSettings

@Composable
fun SettingsDialog(
    currentSettings: UserSettings,
    onDismiss: () -> Unit,
    onSave: (
        apiKey: String,
        modelName: String,
        systemPrompt: String,
        themeMode: ClockThemeMode,
        is24Hour: Boolean,
        useDeviceLocation: Boolean,
        manualCity: String
    ) -> Unit
) {
    var apiKey by remember { mutableStateOf(currentSettings.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var modelName by remember { mutableStateOf(currentSettings.modelName) }
    var systemPrompt by remember { mutableStateOf(currentSettings.systemPrompt) }
    var themeMode by remember { mutableStateOf(currentSettings.themeMode) }
    var is24Hour by remember { mutableStateOf(currentSettings.is24Hour) }
    var useDeviceLocation by remember { mutableStateOf(currentSettings.useDeviceLocation) }
    var manualCity by remember { mutableStateOf(currentSettings.manualCity) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "時鐘與 Grok 設定", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // xAI API Key
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("xAI API Key (xai-...)") },
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

                // Grok Model
                OutlinedTextField(
                    value = modelName,
                    onValueChange = { modelName = it },
                    label = { Text("Grok 模型 (如 grok-3 / grok-3-mini)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // System Prompt
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("Grok 人設與回覆風格 (System Prompt)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // 主題選擇
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

                // 24 小時制
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("24 小時制顯示", fontSize = 14.sp)
                    Switch(checked = is24Hour, onCheckedChange = { is24Hour = it })
                }

                // GPS 定位天氣
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
                        apiKey,
                        modelName,
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
