package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ════════ الألوان الأساسية الخاصة بتطبيقك ════════
val AccentBlue = Color(0xFF2196F3)
val AccentPurple = Color(0xFF8B5CF6)
val SuccessGreen = Color(0xFF4CAF50)
val ErrorRed = Color(0xFFF44336)
val Gold = Color(0xFFFFD700)
val TextMuted = Color(0xFFA0A0A0)

// ألوان الوضع الداكن
val DarkBackground = Color(0xFF000000) // أسود نقي
val DarkSurface = Color(0xFF151515)    // رمادي داكن للبطاقات
val DarkBorder = Color(0xFF2A2A2A)

// ════════ 1. نظام الألوان الداكن (Dark Mode) ════════
private val DarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    secondary = AccentPurple,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = Color.White,
    onSurface = Color.White,
    outline = DarkBorder
)

// ════════ 2. نظام الألوان الفاتح (Light Mode) ════════
private val LightColorScheme = lightColorScheme(
    primary = AccentBlue,
    secondary = AccentPurple,
    background = Color(0xFFF5F5F5), // رمادي فاتح جداً للخلفية
    surface = Color(0xFFFFFFFF),    // أبيض نقي للبطاقات
    onBackground = Color(0xFF1A1A1A), // أسود للنصوص
    onSurface = Color(0xFF1A1A1A),
    outline = Color(0xFFE0E0E0)     // حدود رمادية فاتحة
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // تم التغيير إلى false لكي لا يغير نظام أندرويد ألوان تطبيقك الأصلية
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

    // هذا الجزء لتغيير لون شريط الإشعارات العلوي (Status Bar) ليتطابق مع الثيم
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        // Typography يجب أن تكون معرفة في ملف Type.kt الموجود لديك في نفس المجلد
        typography = Typography, 
        content = content
    )
}
