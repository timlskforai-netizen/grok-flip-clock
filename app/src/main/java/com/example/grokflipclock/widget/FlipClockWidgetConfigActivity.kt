package com.example.grokflipclock.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

class FlipClockWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(Activity.RESULT_CANCELED)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val initialConfig = FlipClockWidgetProvider.getWidgetConfig(this, appWidgetId)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WidgetConfigScreen(
                        initial = initialConfig,
                        onSave = { updated ->
                            FlipClockWidgetProvider.saveWidgetConfig(this, appWidgetId, updated)
                            val resultIntent = Intent().apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            }
                            setResult(Activity.RESULT_OK, resultIntent)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetConfigScreen(
    initial: WidgetManualConfig,
    onSave: (WidgetManualConfig) -> Unit
) {
    var useManualLocation by remember { mutableStateOf(initial.useManualLocation) }
    var manualQuery by remember { mutableStateOf(initial.manualQuery) }
    var is24Hour by remember { mutableStateOf(initial.is24Hour) }
    var showDate by remember { mutableStateOf(initial.showDate) }
    var showForecast by remember { mutableStateOf(initial.showForecast) }
    var rowLayoutStyle by remember { mutableStateOf(initial.rowLayoutStyle) }
    var showOuterFrame by remember { mutableStateOf(initial.showOuterFrame) }
    var showConfigButton by remember { mutableStateOf(initial.showConfigButton) }
    var themeStyle by remember { mutableStateOf(initial.themeStyle) }
    var bgAlpha by remember { mutableFloatStateOf(initial.bgAlphaPercent.toFloat()) }
    var fontScale by remember { mutableFloatStateOf(initial.fontScalePercent.toFloat()) }

    val quickLocations = listOf("大埔", "沙田", "觀塘", "深水埗", "將軍澳", "屯門", "元朗", "Tokyo", "Osaka")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "桌面 Widget 手動調教",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        // 1. 4x1 橫排佈局選擇
        Text(text = "4×1 橫排佈局樣式", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = rowLayoutStyle == "left_right",
                onClick = { rowLayoutStyle = "left_right" },
                label = { Text("左右並排（左時鐘右天氣）") }
            )
            FilterChip(
                selected = rowLayoutStyle == "center",
                onClick = { rowLayoutStyle = "center" },
                label = { Text("置中三欄（時鐘置中）") }
            )
        }

        HorizontalDivider()

        // 2. 天氣地區設定
        Text(text = "天氣地區", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("手動指定分區／城市")
                Text(
                    text = if (useManualLocation) "使用下方指定地區（香港天文台或全球城市）" else "自動跟隨手機定位及主程式設定",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = useManualLocation,
                onCheckedChange = { useManualLocation = it }
            )
        }

        if (useManualLocation) {
            OutlinedTextField(
                value = manualQuery,
                onValueChange = { manualQuery = it },
                label = { Text("輸入香港分區（如：大埔、觀塘）或城市（如：Tokyo）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                quickLocations.forEach { place ->
                    FilterChip(
                        selected = manualQuery.equals(place, ignoreCase = true),
                        onClick = { manualQuery = place },
                        label = { Text(place) }
                    )
                }
            }
        }

        HorizontalDivider()

        // 3. 顯示內容調教
        Text(text = "顯示項目（不顯示秒數）", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("24 小時制（關閉則顯示 12 小時制）")
            Switch(checked = is24Hour, onCheckedChange = { is24Hour = it })
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("顯示日期與星期")
            Switch(checked = showDate, onCheckedChange = { showDate = it })
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("顯示未來每 4 小時溫度預報")
            Switch(checked = showForecast, onCheckedChange = { showForecast = it })
        }

        HorizontalDivider()

        // 4. 外觀與懸浮無框設定
        Text(text = "外觀與無外框懸浮", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("顯示外層大框")
                Text(
                    text = if (showOuterFrame) "開啟：整組 Widget 帶有半透明外框" else "關閉：純卡片直接懸浮於桌面壁紙上",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = showOuterFrame, onCheckedChange = { showOuterFrame = it })
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("顯示右上角設定齒輪（⚙）")
                Text(
                    text = "關閉後畫面更乾淨，長按 Widget 揀「設定」仍可隨時修改",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = showConfigButton, onCheckedChange = { showConfigButton = it })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = themeStyle == "dark",
                onClick = { themeStyle = "dark" },
                label = { Text("深邃黑") }
            )
            FilterChip(
                selected = themeStyle == "oled",
                onClick = { themeStyle = "oled" },
                label = { Text("純黑 OLED") }
            )
            FilterChip(
                selected = themeStyle == "light",
                onClick = { themeStyle = "light" },
                label = { Text("日系米白") }
            )
        }

        Column {
            Text("背景與卡片透明度：${bgAlpha.roundToInt()}%", fontSize = 13.sp)
            Slider(
                value = bgAlpha,
                onValueChange = { bgAlpha = it },
                valueRange = 0f..100f
            )
        }

        Column {
            Text("時鐘字體縮放：${fontScale.roundToInt()}%", fontSize = 13.sp)
            Slider(
                value = fontScale,
                onValueChange = { fontScale = it },
                valueRange = 80f..130f
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                onSave(
                    WidgetManualConfig(
                        useManualLocation = useManualLocation,
                        manualQuery = manualQuery.ifBlank { "大埔" },
                        is24Hour = is24Hour,
                        showDate = showDate,
                        showForecast = showForecast,
                        rowLayoutStyle = rowLayoutStyle,
                        showOuterFrame = showOuterFrame,
                        showConfigButton = showConfigButton,
                        themeStyle = themeStyle,
                        bgAlphaPercent = bgAlpha.roundToInt(),
                        fontScalePercent = fontScale.roundToInt()
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("套用並更新 Widget", fontWeight = FontWeight.Bold)
        }
    }
}
