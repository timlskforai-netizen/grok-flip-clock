package com.example.grokflipclock.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grokflipclock.ui.WeatherUiState
import com.example.grokflipclock.ui.theme.LocalClockPalette
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun FlipClockPanel(
    now: LocalDateTime,
    is24Hour: Boolean,
    weather: WeatherUiState,
    isFullscreen: Boolean,
    onToggleTheme: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalClockPalette.current

    val rawHour = now.hour
    val displayHour = if (is24Hour) {
        rawHour
    } else {
        val h = rawHour % 12
        if (h == 0) 12 else h
    }
    val amPmLabel = if (rawHour < 12) "AM" else "PM"

    val hourStr = String.format(Locale.US, "%02d", displayHour)
    val minStr = String.format(Locale.US, "%02d", now.minute)
    val secStr = String.format(Locale.US, "%02d", now.second)

    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy年MM月dd日", Locale.TRADITIONAL_CHINESE) }
    val dateText = now.format(dateFormatter)
    val dayOfWeekText = formatDayOfWeek(now.dayOfWeek)

    Box(
        modifier = modifier
            .background(palette.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 頂部工具列：天氣資訊 + 主題切換 / 全螢幕 / 設定
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeatherBadge(weather = weather)

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleTheme) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = "切換主題",
                        tint = palette.secondaryText
                    )
                }
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                        contentDescription = "全螢幕切換",
                        tint = palette.secondaryText
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "設定",
                        tint = palette.secondaryText
                    )
                }
            }
        }

        // 中央主體：日期星期 + 3D 翻頁鐘 (HH : MM : SS)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = dateText,
                    color = palette.primaryText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "·",
                    color = palette.accent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dayOfWeekText,
                    color = palette.secondaryText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal
                )
                if (!is24Hour) {
                    Surface(
                        color = palette.cardBackground,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, palette.cardBorder)
                    ) {
                        Text(
                            text = amPmLabel,
                            color = palette.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // 根據可用寬度自動計算 3 組雙位翻頁卡尺寸
                val availableWidth = maxWidth
                val cardWidth = ((availableWidth - 56.dp) / 6f).coerceIn(38.dp, 125.dp)
                val cardHeight = cardWidth * 1.42f
                val fontSize = (cardWidth.value * 0.86f).sp
                val colonSize = (cardWidth.value * 0.55f).sp

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 時 (HH)
                    FlipDigitCard(digit = hourStr[0].toString(), width = cardWidth, height = cardHeight, fontSize = fontSize)
                    FlipDigitCard(digit = hourStr[1].toString(), width = cardWidth, height = cardHeight, fontSize = fontSize)

                    ColonSeparator(size = colonSize)

                    // 分 (MM)
                    FlipDigitCard(digit = minStr[0].toString(), width = cardWidth, height = cardHeight, fontSize = fontSize)
                    FlipDigitCard(digit = minStr[1].toString(), width = cardWidth, height = cardHeight, fontSize = fontSize)

                    ColonSeparator(size = colonSize)

                    // 秒 (SS)
                    FlipDigitCard(digit = secStr[0].toString(), width = cardWidth, height = cardHeight, fontSize = fontSize)
                    FlipDigitCard(digit = secStr[1].toString(), width = cardWidth, height = cardHeight, fontSize = fontSize)
                }
            }
        }
    }
}

@Composable
private fun ColonSeparator(size: TextUnit) {
    val palette = LocalClockPalette.current
    Text(
        text = ":",
        color = palette.secondaryText,
        fontSize = size,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 1.dp)
    )
}

@Composable
fun FlipDigitCard(
    digit: String,
    width: Dp,
    height: Dp,
    fontSize: TextUnit
) {
    val palette = LocalClockPalette.current
    var oldDigit by remember { mutableStateOf(digit) }
    val flipAnim = remember { Animatable(1f) }

    LaunchedEffect(digit) {
        if (digit != oldDigit) {
            flipAnim.snapTo(0f)
            flipAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
            )
            oldDigit = digit
        }
    }

    val progress = flipAnim.value
    val cornerShape = RoundedCornerShape(12.dp)

    val topHalfShape = remember {
        GenericShape { size, _ ->
            addRect(Rect(0f, 0f, size.width, size.height / 2f))
        }
    }
    val bottomHalfShape = remember {
        GenericShape { size, _ ->
            addRect(Rect(0f, size.height / 2f, size.width, size.height))
        }
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(cornerShape)
            .border(1.dp, palette.cardBorder, cornerShape)
    ) {
        // 1. 靜態上半部（顯示新數字）
        DigitHalfFace(
            digit = digit,
            fontSize = fontSize,
            clipShape = topHalfShape
        )

        // 2. 靜態下半部（動畫未過半前顯示舊數字，過半後顯示新數字）
        DigitHalfFace(
            digit = if (progress >= 1f) digit else oldDigit,
            fontSize = fontSize,
            clipShape = bottomHalfShape
        )

        // 3. 翻動頁片（上半段由 0° 向下翻至 -90°；下半段由 +90° 向下翻至 0°）
        if (progress < 1f) {
            if (progress <= 0.5f) {
                val angle = -progress * 180f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            rotationX = angle
                            cameraDistance = 14f * density
                        }
                ) {
                    DigitHalfFace(
                        digit = oldDigit,
                        fontSize = fontSize,
                        clipShape = topHalfShape
                    )
                }
            } else {
                val angle = (1f - progress) * 180f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                            rotationX = angle
                            cameraDistance = 14f * density
                        }
                ) {
                    DigitHalfFace(
                        digit = digit,
                        fontSize = fontSize,
                        clipShape = bottomHalfShape
                    )
                }
            }
        }

        // 4. 中央機械轉軸細縫
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(palette.hingeLine)
                .align(Alignment.Center)
        )
    }
}

@Composable
private fun DigitHalfFace(
    digit: String,
    fontSize: TextUnit,
    clipShape: GenericShape
) {
    val palette = LocalClockPalette.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(clipShape)
            .background(palette.cardBackground),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit,
            color = palette.digitText,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun WeatherBadge(weather: WeatherUiState) {
    val palette = LocalClockPalette.current
    Surface(
        color = palette.cardBackground,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, palette.cardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = weatherCodeToIcon(weather.weatherCode),
                contentDescription = weather.conditionText,
                tint = palette.accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "${weather.cityName} · ${weather.temperatureText}",
                color = palette.primaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = weather.conditionText,
                color = palette.secondaryText,
                fontSize = 12.sp
            )
        }
    }
}

private fun weatherCodeToIcon(code: Int): ImageVector {
    return when (code) {
        0, 1 -> Icons.Rounded.WbSunny
        2, 3, 45, 48 -> Icons.Rounded.Cloud
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> Icons.Rounded.WaterDrop
        71, 73, 75, 77, 85, 86 -> Icons.Rounded.AcUnit
        95, 96, 99 -> Icons.Rounded.Thunderstorm
        else -> Icons.Rounded.Cloud
    }
}

private fun formatDayOfWeek(day: DayOfWeek): String {
    return when (day) {
        DayOfWeek.MONDAY -> "星期一 · Mon"
        DayOfWeek.TUESDAY -> "星期二 · Tue"
        DayOfWeek.WEDNESDAY -> "星期三 · Wed"
        DayOfWeek.THURSDAY -> "星期四 · Thu"
        DayOfWeek.FRIDAY -> "星期五 · Fri"
        DayOfWeek.SATURDAY -> "星期六 · Sat"
        DayOfWeek.SUNDAY -> "星期日 · Sun"
    }
}
