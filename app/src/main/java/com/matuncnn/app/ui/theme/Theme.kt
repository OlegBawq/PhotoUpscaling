package com.matuncnn.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3778DD),
    secondary = Color(0xFF5B88D5),
    background = Color(0xFFF3F6FC),
    surface = Color.White
)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF65A5FF),
    onPrimary = Color(0xFF071A35),
    secondary = Color(0xFF8BB9FF),
    background = Color(0xFF0B1020),
    surface = Color(0xFF171D2D),
    surfaceVariant = Color(0xFF252D40)
)

@Composable
fun MatUnCnnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
