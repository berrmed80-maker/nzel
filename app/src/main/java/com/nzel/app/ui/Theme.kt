package com.nzel.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun NzelTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> darkColorScheme(
            primary = Color(0xFF80D4D5),
            secondary = Color(0xFFA6D8B9),
            tertiary = Color(0xFFEAC58D)
        )
        else -> lightColorScheme(
            primary = Color(0xFF00696B),
            secondary = Color(0xFF2D6A4F),
            tertiary = Color(0xFF795427)
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
