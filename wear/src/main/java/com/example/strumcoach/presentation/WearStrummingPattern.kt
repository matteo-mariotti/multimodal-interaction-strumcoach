package com.example.strumcoach.presentation

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay

@Composable
fun WearStrummingPattern(pattern: String) {
    if (pattern.isEmpty()) return

    val scrollState = rememberScrollState()
    val strums = pattern.split(" ")

    // Auto-scroll logic (ping-pong)
    if (strums.size > 4) {
        LaunchedEffect(pattern) {
            while (true) {
                delay(2000) // Start delay
                scrollState.animateScrollTo(
                    value = scrollState.maxValue,
                    animationSpec = tween(durationMillis = strums.size * 500)
                )
                delay(2000) // End delay
                scrollState.animateScrollTo(
                    value = 0,
                    animationSpec = tween(durationMillis = strums.size * 500)
                )
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth(0.65f)
            .horizontalScroll(scrollState, enabled = false),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        strums.forEach { strum ->
            val icon = when (strum.uppercase()) {
                "D" -> Icons.Default.ArrowDownward
                "U" -> Icons.Default.ArrowUpward
                else -> null
            }
            val color = when (strum.uppercase()) {
                "D" -> Color(0xFF4CAF50)
                "U" -> Color(0xFFFF9800)
                else -> Color.Gray
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) {
                        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                    } else {
                        Box(modifier = Modifier.size(3.dp).background(color, CircleShape))
                    }
                }
                Text(
                    text = if (strum == ".") "-" else strum,
                    fontSize = 8.sp,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
