package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val PovSurvivalColorScheme =
  darkColorScheme(
    primary = LanternGold,
    onPrimary = InkOutline,
    primaryContainer = NightIndigoElevated,
    onPrimaryContainer = LanternGold,
    secondary = HarborCyan,
    onSecondary = InkOutline,
    secondaryContainer = NightIndigoContainer,
    onSecondaryContainer = HarborCyan,
    tertiary = VitalityLime,
    onTertiary = InkOutline,
    background = NightAbyss,
    onBackground = HudTextPrimary,
    surface = NightIndigoSurface,
    onSurface = HudTextPrimary,
    surfaceVariant = NightIndigoContainer,
    onSurfaceVariant = HudTextMuted,
    outline = HudBorderSubtle,
    error = ThreatCrimson,
    onError = HudTextPrimary,
  )

@Composable
fun PovSurvivalTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = PovSurvivalColorScheme,
    typography = Typography,
    content = content,
  )
}
