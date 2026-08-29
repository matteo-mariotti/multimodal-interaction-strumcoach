package com.example.strumcoach.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.strumcoach.StrumEvent
import com.example.strumcoach.ui.theme.StrumDown
import com.example.strumcoach.ui.theme.StrumUp
import kotlin.math.abs

// A simplified companion to StrumWaveformGraph: markers at a fixed height per direction
// (down below the line, up above), not scaled by the gyro value at that strum. Useful
// on its own when you just want to see the detected rhythm/pattern without the signal
// magnitude distracting from it.
@Composable
fun StrumMarkersTimeline(
    strums: List<StrumEvent>,
    referenceStrums: List<StrumEvent>? = null,
    totalLength: Int,
    indexShift: Int = 0,
    minStepX: Dp? = null,
    modifier: Modifier
) {
    val outlineColor = MaterialTheme.colorScheme.outline

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val center = height / 2
        val stepX = minStepX?.toPx() ?: (width / totalLength.coerceAtLeast(1))
        val actualContentWidth = totalLength * stepX
        val markerOffset = 14.dp.toPx()
        val markerRadius = 5.dp.toPx()

        drawLine(
            color = outlineColor.copy(alpha = 0.2f),
            start = Offset(0f, center),
            end = Offset(actualContentWidth.coerceAtLeast(width), center),
            strokeWidth = 1.dp.toPx()
        )

        referenceStrums?.forEach { strum ->
            val x = strum.index * stepX
            if (x in 0f..actualContentWidth) {
                val y = if (strum.isDown) center + markerOffset else center - markerOffset
                drawCircle(
                    color = outlineColor.copy(alpha = 0.35f),
                    radius = markerRadius,
                    center = Offset(x, y)
                )
            }
        }

        strums.forEach { strum ->
            val x = (strum.index - indexShift) * stepX
            if (x in 0f..actualContentWidth) {
                val y = if (strum.isDown) center + markerOffset else center - markerOffset
                drawCircle(
                    color = if (strum.isDown) StrumDown else StrumUp,
                    radius = markerRadius,
                    center = Offset(x, y)
                )
            }
        }
    }
}

@Composable
fun StrumWaveformGraph(
    signal: List<Float>,
    strums: List<StrumEvent>,
    referenceSignal: List<Float>? = null,
    referenceStrums: List<StrumEvent>? = null,
    audioEnvelope: List<Float>? = null,
    indexShift: Int = 0,
    minStepX: Dp? = null,
    threshold: Float? = null,
    audioThreshold: Float? = null,
    modifier: Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val outlineColor = MaterialTheme.colorScheme.outline
    val errorColor = MaterialTheme.colorScheme.error

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val center = height / 2

        val maxLen = referenceSignal?.size?.coerceAtLeast(signal.size) ?: signal.size
        val stepX = minStepX?.toPx() ?: (width / maxLen.coerceAtLeast(1))
        val actualContentWidth = maxLen * stepX

        val allValues = signal + (referenceSignal ?: emptyList())
        val maxVal = if (allValues.isNotEmpty()) allValues.maxOf { abs(it) }.coerceAtLeast(6f) else 6f
        val scaleY = (height / 2) / maxVal

        // Center line
        drawLine(
            color = outlineColor.copy(alpha = 0.2f),
            start = Offset(0f, center),
            end = Offset(actualContentWidth.coerceAtLeast(width), center),
            strokeWidth = 1.dp.toPx()
        )

        // Threshold lines
        threshold?.let { t ->
            val dashPath = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
            val yTop = center - t * scaleY
            val yBottom = center + t * scaleY

            drawLine(
                color = errorColor.copy(alpha = 0.3f),
                start = Offset(0f, yTop),
                end = Offset(actualContentWidth.coerceAtLeast(width), yTop),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashPath
            )
            drawLine(
                color = errorColor.copy(alpha = 0.3f),
                start = Offset(0f, yBottom),
                end = Offset(actualContentWidth.coerceAtLeast(width), yBottom),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashPath
            )
        }

        // Audio onset threshold — this is what actually gates strum detection when
        // audio was recorded (see StrumAnalyzer.findAudioOnsetStrums), on the same
        // scale as the tertiary-colored audio envelope trace drawn below.
        audioThreshold?.let { t ->
            val dashPath = PathEffect.dashPathEffect(floatArrayOf(6f, 10f), 0f)
            val audioScale = (height / 2.5f)
            val y = center - t * audioScale
            drawLine(
                color = tertiaryColor.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(actualContentWidth.coerceAtLeast(width), y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashPath
            )
        }

        // Reference Signal
        referenceSignal?.let { ref ->
            if (ref.isNotEmpty()) {
                for (i in 0 until ref.size - 1) {
                    drawLine(
                        color = outlineColor.copy(alpha = 0.4f),
                        start = Offset(i * stepX, center - ref[i] * scaleY),
                        end = Offset((i + 1) * stepX, center - ref[i + 1] * scaleY),
                        strokeWidth = 2.dp.toPx()
                    )
                }

                referenceStrums?.forEach { strum ->
                    drawCircle(
                        color = outlineColor.copy(alpha = 0.4f),
                        radius = 3.dp.toPx(),
                        center = Offset(strum.index * stepX, center - strum.value * scaleY)
                    )
                }
            }
        }

        // Current Signal
        if (signal.isNotEmpty()) {
            for (i in 0 until signal.size - 1) {
                val x1 = (i - indexShift) * stepX
                val x2 = (i + 1 - indexShift) * stepX

                if (x1 >= 0 && x2 <= actualContentWidth) {
                    drawLine(
                        color = primaryColor,
                        start = Offset(x1, center - signal[i] * scaleY),
                        end = Offset(x2, center - signal[i + 1] * scaleY),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // Audio Envelope
            audioEnvelope?.let { env ->
                if (env.isNotEmpty()) {
                    for (i in 0 until env.size - 1) {
                        val x1 = (i - indexShift) * stepX
                        val x2 = (i + 1 - indexShift) * stepX

                        if (x1 >= 0 && x2 <= actualContentWidth) {
                            val audioScale = (height / 2.5f)
                            drawLine(
                                color = tertiaryColor.copy(alpha = 0.5f),
                                start = Offset(x1, center - env[i] * audioScale),
                                end = Offset(x2, center - env[i+1] * audioScale),
                                strokeWidth = 1.5.dp.toPx()
                            )
                        }
                    }
                }
            }

            // Strum markers
            strums.forEach { strum ->
                val x = (strum.index - indexShift) * stepX
                if (x >= 0 && x <= actualContentWidth) {
                    val y = center - strum.value * scaleY
                    val color = if (strum.isDown) StrumDown else StrumUp

                    drawCircle(
                        color = color,
                        radius = 5.dp.toPx(),
                        center = Offset(x, y)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}
