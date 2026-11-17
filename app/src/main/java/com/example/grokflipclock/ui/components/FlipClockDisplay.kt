package com.example.grokflipclock.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.grokflipclock.ui.HourlyForecastItem
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
        when (val h = rawHour % 12) {
            0 -> 12
            else -> h
        }
    }
    val amPmText = if (rawHour < 12) "AM" else "PM"

    val hourStr = String.format(Locale.US, "%02d", displayHour)
    val minStr = String.format(Locale.US, "%02d", now.minute)
    val secStr = String.format(Locale.US, "%02d", now.second)

    val datePart = now.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
    val chineseWeekday = mapChineseWeekday(now.dayOfWeek)
    val englishWeekday = now.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH))

    BoxWithConstraints(
        modifier = modifier
            .background(palette.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        val availWidth = maxWidth
        val availHeight = maxHeight
        val isCompactHeight = availHeight < 340.dp

        // 動態計算翻頁卡片尺寸，確保喺 70% 分割視窗入面依然大字體且唔會超出邊界
        val cardWidth = ((availWidth - 64.dp) / 6.8f).coerceIn(38.dp, 128.dp)
        val cardHeight = (cardWidth * 1.38f).coerceAtMost(availHeight * 0.44f)
        val digitFontSize = (cardHeight.value * 0.62f).sp

        // 1. 右上角功能列（主題切換 / 全螢幕切換 / 設定）
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .alpha(0.75f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleTheme, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Palette,
                    contentDescription = "切換主題",
                    tint = palette.secondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    contentDescription = "切換全螢幕",
                    tint = palette.secondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "設定",
                    tint = palette.secondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 2. 中央主視覺：日期 + 機械翻頁時鐘 + 實時及每 4 小時天氣預報
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 日期與星期
            Text(
                text = "$datePart  ·  $chineseWeekday  ·  $englishWeekday",
                color = palette.secondaryText,
                fontSize = if (isCompactHeight) 13.sp else 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(if (isCompactHeight) 10.dp else 18.dp))

            // 翻頁數字列 (HH : MM : SS)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!is24Hour) {
                    Text(
                        text = amPmText,
                        color = palette.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                }

                FlipDigitCard(
                    digit = hourStr[0],
                    width = cardWidth,
                    height = cardHeight,
                    fontSize = digitFontSize
                )
                FlipDigitCard(
                    digit = hourStr[1],
                    width = cardWidth,
                    height = cardHeight,
                    fontSize = digitFontSize
                )

                ColonSeparator(height = cardHeight)

                FlipDigitCard(
                    digit = minStr[0],
                    width = cardWidth,
                    height = cardHeight,
                    fontSize = digitFontSize
                )
                FlipDigitCard(
                    digit = minStr[1],
                    width = cardWidth,
                    height = cardHeight,
                    fontSize = digitFontSize
                )

                ColonSeparator(height = cardHeight)

                FlipDigitCard(
                    digit = secStr[0],
                    width = cardWidth,
                    height = cardHeight,
                    fontSize = digitFontSize
                )
                FlipDigitCard(
                    digit = secStr[1],
                    width = cardWidth,
                    height = cardHeight,
                    fontSize = digitFontSize
                )
            }

            Spacer(modifier = Modifier.height(if (isCompactHeight) 12.dp else 22.dp))

            // 時鐘下方：實時天氣 + 每 4 小時天氣及溫度預報卡片
            WeatherForecastStrip(
                weather = weather,
                isWideLayout = availWidth >= 460.dp
            )
        }
    }
}

@Composable
private fun WeatherForecastStrip(
    weather: WeatherUiState,
    isWideLayout: Boolean
) {
    val palette = LocalClockPalette.current

    Surface(
        color = palette.cardBackground,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, palette.cardBorder),
        modifier = Modifier.widthIn(max = 660.dp)
    ) {
        if (isWideLayout && weather.hourlyForecasts.isNotEmpty()) {
            // 橫向 / 70% 分割視窗：左邊實時天氣，右邊每 4 小時預報並列
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CurrentWeatherBlock(weather = weather)

                VerticalDivider(
                    modifier = Modifier.height(36.dp),
                    thickness = 1.dp,
                    color = palette.divider
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    weather.hourlyForecasts.forEach { item ->
                        FourHourSlot(item = item)
                    }
                }
            }
        } else {
            // 較窄視窗：上下兩層排列
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CurrentWeatherBlock(weather = weather)

                if (weather.hourlyForecasts.isNotEmpty()) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = palette.divider
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        weather.hourlyForecasts.forEach { item ->
                            FourHourSlot(item = item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentWeatherBlock(weather: WeatherUiState) {
    val palette = LocalClockPalette.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = mapWeatherCodeToIcon(weather.weatherCode),
            contentDescription = weather.conditionText,
            tint = palette.accent,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(
                text = weather.cityName,
                color = palette.secondaryText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = weather.temperatureText,
                    color = palette.primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = weather.conditionText,
                    color = palette.secondaryText,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun FourHourSlot(item: HourlyForecastItem) {
    val palette = LocalClockPalette.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = item.timeLabel,
            color = palette.secondaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Icon(
            imageVector = mapWeatherCodeToIcon(item.weatherCode),
            contentDescription = item.conditionText,
            tint = palette.primaryText,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "${item.temperatureText} ${item.conditionText}",
            color = palette.primaryText,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FlipDigitCard(
    digit: Char,
    width: Dp,
    height: Dp,
    fontSize: TextUnit
) {
    val palette = LocalClockPalette.current

    Surface(
        color = palette.cardBackground,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, palette.cardBorder),
        modifier = Modifier
            .width(width)
            .height(height)
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = digit,
                transitionSpec = {
                    (slideInVertically(animationSpec = tween(220)) { -it / 2 } + fadeIn(tween(180)))
                        .togetherWith(
                            slideOutVertically(animationSpec = tween(220)) { it / 2 } + fadeOut(tween(180))
                        )
                },
                label = "flip_digit"
            ) { targetDigit ->
                Text(
                    text = targetDigit.toString(),
                    color = palette.primaryText,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold
                )
            }

            // 機械翻頁鐘中間分割幼線
            HorizontalDivider(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                thickness = 1.dp,
                color = palette.background.copy(alpha = 0.65f)
            )
        }
    }
}

@Composable
private fun ColonSeparator(height: Dp) {
    val palette = LocalClockPalette.current
    Column(
        modifier = Modifier.height(height * 0.45f),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(palette.secondaryText.copy(alpha = 0.7f), RoundedCornerShape(50))
        )
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(palette.secondaryText.copy(alpha = 0.7f), RoundedCornerShape(50))
        )
    }
}

private fun mapWeatherCodeToIcon(code: Int): ImageVector {
    return when (code) {
        0, 1 -> Icons.Rounded.WbSunny
        2, 3, 45, 48 -> Icons.Rounded.Cloud
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> Icons.Rounded.WaterDrop
        71, 73, 75, 77 -> Icons.Rounded.AcUnit
        95, 96, 99 -> Icons.Rounded.Thunderstorm
        else -> Icons.Rounded.Cloud
    }
}

private fun mapChineseWeekday(dayOfWeek: DayOfWeek): String {
    return when (dayOfWeek) {
        DayOfWeek.MONDAY -> "星期一"
        DayOfWeek.TUESDAY -> "星期二"
        DayOfWeek.WEDNESDAY -> "星期三"
        DayOfWeek.THURSDAY -> "星期四"
        DayOfWeek.FRIDAY -> "星期五"
        DayOfWeek.SATURDAY -> "星期六"
        DayOfWeek.SUNDAY -> "星期日"
    }
}
