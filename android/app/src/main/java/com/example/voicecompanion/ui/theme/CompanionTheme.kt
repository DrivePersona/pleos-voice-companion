package com.example.voicecompanion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 보이스 컴패니언의 시각 토큰.
 *
 * 차량 시스템 색상(경고·규제·주행)을 침범하지 않고, 정차 중 개인화 행동에만
 * 저채도 바이올렛을 쓰는 이 앱 고유의 차량용 테마다.
 */
object CompanionTokens {
    // 차량 시스템의 경고·주의·성공 색상은 이 앱에서 재정의하지 않는다.
    val DayCanvas = Color(0xFFF3F5F7)
    val DaySurface = Color(0xFFFFFFFF)
    val DayRaisedSurface = Color(0xFFE9EDF1)
    val DayText = Color(0xFF18212B)
    val DaySecondaryText = Color(0xFF5C6875)
    val DayOutline = Color(0xFFD7DDE3)

    val NightCanvas = Color(0xFF0E1218)
    val NightSurface = Color(0xFF171D25)
    val NightRaisedSurface = Color(0xFF222A34)
    val NightText = Color(0xFFF2F5F7)
    val NightSecondaryText = Color(0xFFB7C0CA)
    val NightOutline = Color(0xFF36414D)

    // 캐릭터·음성 팩처럼 정차 중 개인화에만 쓰는 브랜드 포인트다.
    val VioletAction = Color(0xFF5B4DB3)
    val VioletOnAction = Color(0xFFFFFFFF)
    val VioletContainer = Color(0xFFEEECFF)
    val VioletOnContainer = Color(0xFF28204F)
    val VioletNightAction = Color(0xFFC9C2FF)
    val VioletNightOnAction = Color(0xFF28204F)
    val VioletNightContainer = Color(0xFF2A2549)
    val VioletNightOnContainer = Color(0xFFE9E5FF)

    val PagePadding = 20.dp
    val CompactPagePadding = 16.dp
    val SectionGap = 20.dp
    val CardRadius = 20.dp
    val RowRadius = 16.dp
    val ButtonRadius = 14.dp
    val MinimumTouchHeight = 52.dp
}

private val LightColors = lightColorScheme(
    primary = CompanionTokens.VioletAction,
    onPrimary = CompanionTokens.VioletOnAction,
    primaryContainer = CompanionTokens.VioletContainer,
    onPrimaryContainer = CompanionTokens.VioletOnContainer,
    secondary = CompanionTokens.DaySecondaryText,
    onSecondary = Color.White,
    secondaryContainer = CompanionTokens.DayRaisedSurface,
    onSecondaryContainer = CompanionTokens.DayText,
    background = CompanionTokens.DayCanvas,
    onBackground = CompanionTokens.DayText,
    surface = CompanionTokens.DaySurface,
    onSurface = CompanionTokens.DayText,
    surfaceVariant = CompanionTokens.DayRaisedSurface,
    onSurfaceVariant = CompanionTokens.DaySecondaryText,
    outlineVariant = CompanionTokens.DayOutline,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = CompanionTokens.VioletNightAction,
    onPrimary = CompanionTokens.VioletNightOnAction,
    primaryContainer = CompanionTokens.VioletNightContainer,
    onPrimaryContainer = CompanionTokens.VioletNightOnContainer,
    secondary = CompanionTokens.NightSecondaryText,
    onSecondary = CompanionTokens.NightCanvas,
    secondaryContainer = CompanionTokens.NightRaisedSurface,
    onSecondaryContainer = CompanionTokens.NightText,
    background = CompanionTokens.NightCanvas,
    onBackground = CompanionTokens.NightText,
    surface = CompanionTokens.NightSurface,
    onSurface = CompanionTokens.NightText,
    surfaceVariant = CompanionTokens.NightRaisedSurface,
    onSurfaceVariant = CompanionTokens.NightSecondaryText,
    outlineVariant = CompanionTokens.NightOutline,
    error = Color(0xFFFFB3B8),
    onError = Color(0xFF690013),
    errorContainer = Color(0xFF93001F),
    onErrorContainer = Color(0xFFFFD9DB),
)

private val CompanionTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
)

@Composable
fun CompanionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = CompanionTypography,
        shapes = MaterialTheme.shapes.copy(
            small = RoundedCornerShape(CompanionTokens.ButtonRadius),
            medium = RoundedCornerShape(CompanionTokens.RowRadius),
            large = RoundedCornerShape(CompanionTokens.CardRadius),
        ),
        content = content,
    )
}
