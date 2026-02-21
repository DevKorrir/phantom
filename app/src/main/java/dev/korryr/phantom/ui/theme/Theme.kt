package dev.korryr.phantom.ui.theme

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

// ─── DARK SCHEME — Deep space, electric cyan signal ───────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary              = PhantomCyan,
    onPrimary            = PhantomVoid,
    primaryContainer     = PhantomCyanDark,
    onPrimaryContainer   = PhantomCyan,

    secondary            = PhantomPurple,
    onSecondary          = PhantomGhost,
    secondaryContainer   = Color(0xFF1F1535),
    onSecondaryContainer = PhantomPurple,

    tertiary             = PhantomGreen,
    onTertiary           = PhantomVoid,
    tertiaryContainer    = Color(0xFF0D2A14),
    onTertiaryContainer  = PhantomGreen,

    error                = PhantomRed,
    onError              = PhantomGhost,
    errorContainer       = Color(0xFF2A0D0D),
    onErrorContainer     = PhantomRed,

    background           = PhantomAbyss,
    onBackground         = PhantomGhost,

    surface              = PhantomSurface,
    onSurface            = PhantomGhost,
    surfaceVariant       = PhantomElevated,
    onSurfaceVariant     = PhantomMist,

    outline              = PhantomBorder,
    outlineVariant       = Color(0xFF2D333B),

    scrim                = PhantomVoid,
    inverseSurface       = PhantomGhost,
    inverseOnSurface     = PhantomShadow,
    inversePrimary       = PhantomCyanDark,
)

// ─── LIGHT SCHEME — Frosted glass, crisp and clean ────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary              = PhantomCyanDark,
    onPrimary            = PhantomWhite,
    primaryContainer     = Color(0xFFCCF5FF),
    onPrimaryContainer   = Color(0xFF004D61),

    secondary            = Color(0xFF6B44C4),
    onSecondary          = PhantomWhite,
    secondaryContainer   = Color(0xFFEDE7FF),
    onSecondaryContainer = Color(0xFF3D1F8A),

    tertiary             = Color(0xFF2A8C3F),
    onTertiary           = PhantomWhite,
    tertiaryContainer    = Color(0xFFD4F5DC),
    onTertiaryContainer  = Color(0xFF0A3D18),

    error                = Color(0xFFCC1A2A),
    onError              = PhantomWhite,
    errorContainer       = Color(0xFFFFDDDD),
    onErrorContainer     = Color(0xFF6B0010),

    background           = PhantomFrost,
    onBackground         = PhantomShadow,

    surface              = PhantomWhite,
    onSurface            = PhantomShadow,
    surfaceVariant       = PhantomSurfaceLight,
    onSurfaceVariant     = PhantomSlate,

    outline              = PhantomBorderLight,
    outlineVariant       = Color(0xFFE2E8EF),

    scrim                = Color(0xFF000000),
    inverseSurface       = PhantomSurface,
    inverseOnSurface     = PhantomGhost,
    inversePrimary       = PhantomCyan,
)

// ─── THEME ────────────────────────────────────────────────────────────────────
@Composable
fun PhantomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // disabled — Phantom has its own identity
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    // Make status bar match the theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}