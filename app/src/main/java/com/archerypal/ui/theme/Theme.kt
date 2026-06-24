package com.archerypal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val ArcheryColorScheme = darkColorScheme(
    primary = FieldGreen,
    onPrimary = TextPrimary,
    secondary = TargetGold,
    onSecondary = OutdoorBackground,
    background = OutdoorBackground,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    error = ScoreRed
)

@Composable
fun ArcherypalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ArcheryColorScheme,
        typography = MaterialTheme.typography.copy(
            headlineLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            ),
            titleLarge = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp
            ),
            bodyLarge = TextStyle(fontSize = 18.sp)
        ),
        content = content
    )
}
