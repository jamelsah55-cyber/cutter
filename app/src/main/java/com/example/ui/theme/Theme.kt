package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = LaserCyan,
    onPrimary = OnLaserCyan,
    primaryContainer = LaserCyanContainer,
    onPrimaryContainer = OnLaserCyanContainer,
    secondary = BladeAmber,
    onSecondary = OnBladeAmber,
    secondaryContainer = BladeAmberContainer,
    onSecondaryContainer = OnBladeAmberContainer,
    tertiary = SteelBlue,
    onTertiary = Color(0xFF001C38),
    tertiaryContainer = SteelBlueContainer,
    onTertiaryContainer = OnSteelBlueContainer,
    background = StudioBgDark,
    onBackground = TextPrimaryDark,
    surface = StudioSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = StudioSurfaceElevatedDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = StudioBgDark,
    surfaceContainerLow = StudioSurfaceDark,
    surfaceContainer = StudioSurfaceElevatedDark,
    surfaceContainerHigh = StudioSurfaceHighestDark,
    outline = StudioOutlineDark,
    outlineVariant = StudioOutlineVariantDark,
    error = AlertCrimson,
    onError = Color(0xFF3E0007),
    errorContainer = AlertCrimsonContainer,
    onErrorContainer = Color(0xFFFFDAD9)
)

private val StudioLightColorScheme = lightColorScheme(
    primary = LaserCyanLight,
    onPrimary = Color.White,
    primaryContainer = LaserCyanContainerLight,
    onPrimaryContainer = OnLaserCyanContainerLight,
    secondary = BladeAmberLight,
    onSecondary = Color.White,
    secondaryContainer = BladeAmberContainerLight,
    onSecondaryContainer = OnBladeAmberContainerLight,
    tertiary = Color(0xFF1D62B5),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD6E6FF),
    onTertiaryContainer = Color(0xFF001C3A),
    background = StudioBgLight,
    onBackground = TextPrimaryLight,
    surface = StudioSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = StudioSurfaceElevatedLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = StudioSurfaceLight,
    surfaceContainerLow = StudioBgLight,
    surfaceContainer = StudioSurfaceElevatedLight,
    surfaceContainerHigh = StudioSurfaceHighestLight,
    outline = StudioOutlineLight,
    outlineVariant = StudioOutlineVariantLight,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun CutterProTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> StudioDarkColorScheme
        else -> StudioLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = StudioShapes,
        content = content
    )
}
