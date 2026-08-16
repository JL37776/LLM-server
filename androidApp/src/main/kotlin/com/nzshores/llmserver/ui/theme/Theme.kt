package com.nzshores.llmserver.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LlmDarkColorScheme = darkColorScheme(
    background = BgDeep,
    surface = Surface,
    surfaceVariant = Surface2,
    primary = Accent,
    secondary = Good,
    error = Bad,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onPrimary = TextPrimary,
    outline = Border,
)

@Composable
fun LlmManagerTheme(
    content: @Composable () -> Unit,
) {
    // The mockup is dark-only by design (chosen to already pass contrast checks), so we always
    // apply the dark scheme regardless of system setting.
    MaterialTheme(
        colorScheme = LlmDarkColorScheme,
        typography = LlmTypography,
        content = content,
    )
}
