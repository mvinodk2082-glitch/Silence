package com.silence.blocker.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Flat tonal surface with a hairline outline — replaces heavy Material cards. */
@Composable
fun SilenceCard(
    modifier: Modifier = Modifier.fillMaxWidth(),
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    outline: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        elevation = CardDefaults.cardElevation(0.dp),
        border = if (outline) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
        content = content,
    )
}

@Composable
fun ScreenTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
}
