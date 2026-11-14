package luolang.status.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * MIUIX / HyperOS 风格主题：
 *  - 中性灰底 + 纯白 / 近黑卡片
 *  - MIUI 蓝 (#3482FF) 作为强调色
 *  - 大圆角、低饱和次要文字
 */
private val MiuixBlue = Color(0xFF3482FF)
private val MiuixBlueDark = Color(0xFF5B9BFF)

private val MiuixLight = lightColorScheme(
    primary = MiuixBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEAFF),
    onPrimaryContainer = Color(0xFF0A2A57),
    secondary = Color(0xFF5B8DEF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EEFF),
    onSecondaryContainer = Color(0xFF12345F),
    tertiary = Color(0xFFFF8A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEBD6),
    onTertiaryContainer = Color(0xFF5A3200),
    background = Color(0xFFF2F3F5),
    onBackground = Color(0xFF161616),
    surface = Color(0xFFF2F3F5),
    onSurface = Color(0xFF161616),
    surfaceVariant = Color(0xFFE6E8EB),
    onSurfaceVariant = Color(0xFF7A7A80),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF7F8FA),
    surfaceContainerHigh = Color(0xFFEDEEF1),
    surfaceContainerHighest = Color(0xFFE3E5E9),
    outline = Color(0xFFD5D7DC),
    error = Color(0xFFFF3B30),
    onError = Color.White,
    errorContainer = Color(0xFFFFE0DE),
    onErrorContainer = Color(0xFF6B0F0A),
)

private val MiuixDark = darkColorScheme(
    primary = MiuixBlueDark,
    onPrimary = Color(0xFF00214D),
    primaryContainer = Color(0xFF1B3A66),
    onPrimaryContainer = Color(0xFFD8E6FF),
    secondary = Color(0xFF8FB7FF),
    onSecondary = Color(0xFF001B3D),
    secondaryContainer = Color(0xFF203A5E),
    onSecondaryContainer = Color(0xFFD8E4FF),
    tertiary = Color(0xFFFFB454),
    onTertiary = Color(0xFF472A00),
    tertiaryContainer = Color(0xFF5A3A10),
    onTertiaryContainer = Color(0xFFFFDDAD),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE9E9EC),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE9E9EC),
    surfaceVariant = Color(0xFF1E1E22),
    onSurfaceVariant = Color(0xFF98989F),
    surfaceContainerLowest = Color(0xFF0A0A0A),
    surfaceContainerLow = Color(0xFF1A1A1C),
    surfaceContainer = Color(0xFF1F1F22),
    surfaceContainerHigh = Color(0xFF26262A),
    surfaceContainerHighest = Color(0xFF2E2E33),
    outline = Color(0xFF3A3A40),
    error = Color(0xFFFF6B63),
    onError = Color(0xFF3A0000),
    errorContainer = Color(0xFF4A1414),
    onErrorContainer = Color(0xFFFFD9D6),
)

@Composable
fun WatchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) MiuixDark else MiuixLight,
        typography = AppTypography,
        content = content,
    )
}
