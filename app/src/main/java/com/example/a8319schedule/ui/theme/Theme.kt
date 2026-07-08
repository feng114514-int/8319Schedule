package com.example.a8319schedule.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme as m3LightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@Composable
fun _8319ScheduleTheme(
    content: @Composable () -> Unit
) {
    val colors = lightColorScheme().copy(
        background = Color.White,
        surface = Color.White
    )

    val m3Colors = m3LightColorScheme(
        background = Color.White,
        surface = Color.White
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MiuixTheme(
        colors = colors,
        content = {
            MaterialTheme(
                colorScheme = m3Colors,
                content = content
            )
        }
    )
}
