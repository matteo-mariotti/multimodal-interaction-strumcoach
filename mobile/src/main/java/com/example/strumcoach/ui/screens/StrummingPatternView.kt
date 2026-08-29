package com.example.strumcoach.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.strumcoach.ui.theme.StrumDown
import com.example.strumcoach.ui.theme.StrumUp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StrummingPatternView(pattern: String, modifier: Modifier = Modifier) {
    if (pattern.isEmpty()) return

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        FlowRow(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            pattern.split(" ").forEach { strum ->
                val icon = when (strum.uppercase()) {
                    "D" -> Icons.Default.ArrowDownward
                    "U" -> Icons.Default.ArrowUpward
                    else -> null
                }
                val color = when (strum.uppercase()) {
                    "D" -> StrumDown
                    "U" -> StrumUp
                    else -> MaterialTheme.colorScheme.outline
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (icon != null) {
                            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                        } else {
                            Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
                        }
                    }
                    Text(
                        text = if (strum == ".") "·" else strum.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
