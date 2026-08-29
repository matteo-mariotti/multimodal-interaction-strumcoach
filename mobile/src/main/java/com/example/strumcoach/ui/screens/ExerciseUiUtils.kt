package com.example.strumcoach.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.strumcoach.ui.theme.Success
import com.example.strumcoach.ui.theme.Warning

fun getAccuracyColor(accuracy: Int): Color {
    return when {
        accuracy >= 85 -> Success
        accuracy >= 60 -> Warning
        else -> Color(0xFFF44336)
    }
}

@Composable
fun getDifficultyColor(difficulty: String): Color {
    return when (difficulty) {
        "Easy" -> Success
        "Medium" -> Warning
        "Hard" -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.outline
    }
}

@Composable
fun SurfaceTag(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
