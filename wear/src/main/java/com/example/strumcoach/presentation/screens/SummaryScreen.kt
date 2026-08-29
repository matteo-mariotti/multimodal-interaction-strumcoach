package com.example.strumcoach.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.Text

@Composable
fun SummaryScreen(score: Int, grade: String, isWaitingForResult: Boolean, isReference: Boolean, isAmbientMode: Boolean, onClose: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            if (isWaitingForResult) {
                CircularProgressIndicator(
                    modifier = Modifier.size(44.dp),
                    colors = ProgressIndicatorDefaults.colors(indicatorColor = Color(0xFF4CAF50))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Calculating accuracy...", fontSize = 12.sp, color = Color.White)
            } else {
                Text(
                    text = if (isReference) "Reference Saved" else "Exercise Completed",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )

                if (!isReference) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "$score%", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = if (isAmbientMode) Color.White else Color(0xFF4CAF50))
                    Text(text = grade, fontSize = 18.sp, color = Color.White)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(0.8f).height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("CLOSE", fontSize = 14.sp)
                }
            }
        }
    }
}
