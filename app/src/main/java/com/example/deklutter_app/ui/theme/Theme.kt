package com.example.deklutter_app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NavyBlue80,
    secondary = NavyBlueGrey80,
    tertiary = Green80
)

private val LightColorScheme = lightColorScheme(
    primary = NavyBlue40,
    secondary = NavyBlueGrey40,
    tertiary = Green40
)

@Composable
fun Deklutter_appTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
