package com.kidsenglish.companion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = lightColorScheme(
    primary = Color(0xFF006C4C),
    onPrimary = Color.White,
    secondary = Color(0xFF7A5900),
    background = Color(0xFFFFF8EE),
    surface = Color(0xFFFFF8EE),
    onBackground = Color(0xFF1E1B16),
    onSurface = Color(0xFF1E1B16),
)

@Composable
fun KidsEnglishTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
