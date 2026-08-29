package com.example.strumcoach

import kotlin.math.abs

data class StrumEvent(
    val index: Int,
    val timestamp: Float,
    val value: Float,
    val isDown: Boolean
)

data class AnalysisResult(
    val accuracy: Int,
    val timingOffsets: List<Float>,
    val processedSignal: List<Float>,
    val detectedStrums: List<StrumEvent>,
    val feedback: String = "",
    val indexShift: Int = 0,
    val audioEnvelope: List<Float> = emptyList(),
    // Smoothed gyroscope signal — same array as processedSignal. Kept as a separate
    // field only because SessionStats/exportDebugCsv already key off this name.
    val gyroSignal: List<Float> = emptyList(),
    val debugData: Map<String, String> = emptyMap(),
    // Gyroscope signal before smoothing, for the debug CSV export.
    val rawZ: List<Float> = emptyList(),
    // The audio onset level actually used for this session (see findAudioOnsetStrums) —
    // it's computed per-recording, not a fixed constant, so the debug graph needs it
    // to show the threshold that was really applied.
    val audioOnsetThreshold: Float? = null
)

object StrumAnalyzer {

    const val GYRO_STRUM_THRESHOLD = 1.0f
    private const val MIN_STRUM_DISTANCE_MS = 120L
    private const val GYRO_LOW_PASS_ALPHA = 0.5f

    // Rotazione minima polso
    private const val MIN_GYRO_MAGNITUDE = 0.4f

    private const val FLUX_MIN_LEVEL = 0.2f
    private const val FLUX_PROMINENCE = 0.1f
    private const val FLUX_FLOOR_WINDOW = 8
    private const val FLUX_MIN_STRUM_DISTANCE_MS = 150L
    private const val FLUX_NATIVE_HOP_MS = 10f
    private const val GYRO_DIRECTION_FORWARD_WINDOW = 6

    const val TRIM_START_MS = 0f
    private const val TRIM_END_MS = 0f

    fun analyze(
        gyroZValues: List<Float>,
        timestamps: List<Float>,
        referenceSignal: List<Float>? = null,
        referenceStrums: List<StrumEvent>? = null,
        audioEnvelope: List<Float>? = null,
        spectralFlux: List<Float>? = null
    ): AnalysisResult {
        if (gyroZValues.isEmpty()) return AnalysisResult(0, emptyList(), emptyList(), emptyList())

        // 1. Trimming, opzionale
        val startTime = timestamps.first()
        val endTime = timestamps.last()
        val validIndices = timestamps.indices.filter {
            timestamps[it] >= startTime + TRIM_START_MS &&
            timestamps[it] <= endTime - TRIM_END_MS
        }

        if (validIndices.isEmpty()) return AnalysisResult(0, emptyList(), emptyList(), emptyList(), "Recording too short.")

        val trimmedRawGyro = validIndices.map { gyroZValues[it] }
        val trimmedTS = validIndices.map { timestamps[it] }

        // Filtro per pulire il segnale della registrazione
        val trimmedGyro = mutableListOf<Float>()
        var gyroCurrent = trimmedRawGyro.first()
        trimmedRawGyro.forEach { value ->
            gyroCurrent = gyroCurrent + GYRO_LOW_PASS_ALPHA * (value - gyroCurrent)
            trimmedGyro.add(gyroCurrent)
        }

        val trimmedAudio = audioEnvelope?.let { env ->
            validIndices.map { idx ->
                val ts = timestamps[idx]
                val audioIdx = (ts / 25f).toInt().coerceIn(0, env.size - 1)
                env[audioIdx]
            }
        }

        // 3.1 Resampling
        val trimmedFlux = spectralFlux?.takeIf { it.isNotEmpty() }?.let { flux ->
            trimmedTS.indices.map { k ->
                val tStart = trimmedTS[k]
                val tEnd = if (k + 1 < trimmedTS.size) trimmedTS[k + 1] else tStart + FLUX_NATIVE_HOP_MS * 3
                val startIdx = (tStart / FLUX_NATIVE_HOP_MS).toInt().coerceIn(0, flux.size - 1)
                val endIdx = (tEnd / FLUX_NATIVE_HOP_MS).toInt().coerceIn(startIdx, flux.size - 1)
                (startIdx..endIdx).maxOf { flux[it] }
            }
        }

        // 4. Rileva picchi nell'audio
        var audioOnsetThreshold: Float? = null
        var detectedStrums = if (!trimmedFlux.isNullOrEmpty()) {
            val (strums, minLevel) = findAudioOnsetStrumsByFlux(trimmedGyro, trimmedFlux, trimmedTS)
            audioOnsetThreshold = minLevel
            strums
        } else {
            findGyroStrums(trimmedGyro, trimmedTS)
        }

        // 4.1 Auto-Inversion Logic:
        // If we have reference strums, check if the first strong detected peak
        // matches the direction of the first reference strum.
        /* if (detectedStrums.isNotEmpty() && referenceStrums != null && referenceStrums.isNotEmpty()) {
            val firstDetected = detectedStrums.first()
            val firstRef = referenceStrums.first()

            // If directions don't match, and the detected peak is very strong,
            // it's likely a watch orientation issue.
            if (firstDetected.isDown != firstRef.isDown && abs(firstDetected.value) > GYRO_STRUM_THRESHOLD * 1.5f) {
                detectedStrums = detectedStrums.map { it.copy(isDown = !it.isDown, value = -it.value) }
            }
        }
        */

        // 5. Dati di debug da riporta in app
        val maxAbs = if (trimmedGyro.isNotEmpty()) trimmedGyro.maxOf { abs(it) } else 0f
        val debugData = mapOf(
            "Raw Max" to String.format("%.2f", maxAbs),
            "Audio Confidence" to if (audioEnvelope.isNullOrEmpty()) "N/A" else "Active",
            "Strums Detected" to detectedStrums.size.toString(),
            "Samples" to validIndices.size.toString(),
            "Duration" to String.format("%.1fs", (endTime - startTime) / 1000f)
        )

        // 6. Confronto
        val feedbackPrefix = if (maxAbs < MIN_GYRO_MAGNITUDE) "Weak signal: try moving your wrist with more energy. " else ""

        return if (referenceSignal != null && referenceStrums != null && referenceStrums.isNotEmpty()) {
            val res = compareWithReference(trimmedGyro, detectedStrums, referenceStrums)
            res.copy(
                feedback = feedbackPrefix + res.feedback,
                debugData = debugData,
                audioEnvelope = trimmedAudio ?: emptyList(),
                gyroSignal = trimmedGyro,
                rawZ = trimmedRawGyro,
                audioOnsetThreshold = audioOnsetThreshold
            )
        } else {
            AnalysisResult(
                accuracy = 0,
                timingOffsets = emptyList(),
                processedSignal = trimmedGyro,
                detectedStrums = detectedStrums,
                feedback = feedbackPrefix + "Record a reference to get accuracy feedback.",
                debugData = debugData,
                audioEnvelope = trimmedAudio ?: emptyList(),
                gyroSignal = trimmedGyro,
                rawZ = trimmedRawGyro,
                audioOnsetThreshold = audioOnsetThreshold
            )
        }
    }

    // Fallback se non ho l'audio
    private fun findGyroStrums(
        gyroZ: List<Float>,
        timestamps: List<Float>
    ): List<StrumEvent> {
        if (gyroZ.size < 3) return emptyList()
        val detectedStrums = mutableListOf<StrumEvent>()

        // Raggruppo i movimenti per segno (tutta rotazione su e tutta rotazione giù)
        var lastStrumTimestamp = -1000f
        var segmentSign = 0
        var segmentPeakIndex = -1
        var segmentPeakValue = 0f

        fun flushSegment() {
            if (segmentPeakIndex == -1) return
            val peakIndex = segmentPeakIndex
            val peakValue = segmentPeakValue
            segmentPeakIndex = -1
            segmentPeakValue = 0f

            if (abs(peakValue) < GYRO_STRUM_THRESHOLD) return

            val timestamp = timestamps[peakIndex]
            if (timestamp - lastStrumTimestamp < MIN_STRUM_DISTANCE_MS) return

            detectedStrums.add(
                StrumEvent(
                    index = peakIndex,
                    timestamp = timestamp,
                    value = peakValue,
                    isDown = peakValue < 0
                )
            )
            lastStrumTimestamp = timestamp
        }

        for (i in gyroZ.indices) {
            val value = gyroZ[i]
            val sign = when {
                value > 0f -> 1
                value < 0f -> -1
                else -> 0
            }
            if (sign == 0) continue

            if (sign != segmentSign && segmentSign != 0) {
                flushSegment()
            }
            segmentSign = sign

            if (segmentPeakIndex == -1 || abs(value) > abs(segmentPeakValue)) {
                segmentPeakValue = value
                segmentPeakIndex = i
            }
        }
        flushSegment()

        return detectedStrums
    }

    private fun findAudioOnsetStrumsByFlux(
        gyroZ: List<Float>,
        flux: List<Float>,
        timestamps: List<Float>
    ): Pair<List<StrumEvent>, Float?> {
        if (flux.size < 3) return emptyList<StrumEvent>() to null

        val detectedStrums = mutableListOf<StrumEvent>()
        var lastStrumTimestamp = -1000f

        for (i in 1 until flux.lastIndex) {
            val curr = flux[i]
            if (curr < flux[i - 1] || curr < flux[i + 1]) continue // not a local peak

            val floorStart = maxOf(0, i - FLUX_FLOOR_WINDOW)
            val floor = if (floorStart < i) flux.subList(floorStart, i).min() else curr
            val prominence = curr - floor
            if (curr < FLUX_MIN_LEVEL || prominence < FLUX_PROMINENCE) continue

            val timestamp = timestamps[i]
            if (timestamp - lastStrumTimestamp < FLUX_MIN_STRUM_DISTANCE_MS) continue

            val dirEnd = minOf(gyroZ.lastIndex, i + GYRO_DIRECTION_FORWARD_WINDOW)
            val gyroValue = if (i <= dirEnd) {
                gyroZ.subList(i, dirEnd + 1).maxByOrNull { abs(it) } ?: gyroZ[i]
            } else {
                gyroZ.getOrElse(i) { 0f }
            }
            if (abs(gyroValue) < MIN_GYRO_MAGNITUDE) continue

            detectedStrums.add(
                StrumEvent(
                    index = i,
                    timestamp = timestamp,
                    value = gyroValue,
                    isDown = gyroValue < 0
                )
            )
            lastStrumTimestamp = timestamp
        }

        return detectedStrums to FLUX_MIN_LEVEL
    }

    private fun compareWithReference(
        currentSignal: List<Float>,
        currentStrums: List<StrumEvent>,
        refStrums: List<StrumEvent>
    ): AnalysisResult {
        if (currentStrums.isEmpty()) return AnalysisResult(0, emptyList(), currentSignal, emptyList(), "No strums detected.")

        // Allineo le sessioni
        val firstRefStrum = refStrums.first()
        val firstCurrStrum = currentStrums.first()
        
        val indexShift = firstCurrStrum.index - firstRefStrum.index
        
        var matchCount = 0
        var totalTimingError = 0f
        val timingOffsets = mutableListOf<Float>()
        val usedRefIndices = mutableSetOf<Int>()

        currentStrums.forEach { curr ->
            // Trova corrispondenza nella reference
            val expectedIndex = curr.index - indexShift
            val closestRef = refStrums
                .withIndex()
                .filter { it.index !in usedRefIndices }
                .minByOrNull { abs(it.value.index - expectedIndex) }

            if (closestRef != null && abs(closestRef.value.index - expectedIndex) < 15) {
                usedRefIndices.add(closestRef.index)

                // controlla il verso della pennata
                if (curr.isDown == closestRef.value.isDown) matchCount++

                // Verifica il timing della pennata
                val error = ((expectedIndex - closestRef.value.index) / 10f).coerceIn(-1f, 1f)
                timingOffsets.add(error)
                totalTimingError += abs(error)
            }
        }

        // Pennate in eccesso
        val extraStrums = (currentStrums.size - usedRefIndices.size).coerceAtLeast(0)

        // Calcola accuracy
        val directionAcc: Float
        val timingPenalty: Float
        val extraPenalty: Float
        val accuracy = if (refStrums.isNotEmpty()) {
            directionAcc = matchCount.toFloat() / refStrums.size * 100
            timingPenalty = if (usedRefIndices.isNotEmpty()) (totalTimingError / usedRefIndices.size) * 15f else 0f
            extraPenalty = extraStrums * 10f
            (directionAcc - timingPenalty - extraPenalty).toInt().coerceIn(0, 100)
        } else {
            directionAcc = 0f; timingPenalty = 0f; extraPenalty = 0f
            0
        }

        val missedStrums = refStrums.size - usedRefIndices.size
        val directionErrors = usedRefIndices.size - matchCount

        val feedback = buildString {
            when {
                accuracy > 85 -> append("Fantastic! You're perfectly on time.")
                accuracy > 60 -> append("Good, keep it up!")
                else -> append("Keep practicing — here's what to work on:")
            }
            val issues = mutableListOf<String>()
            if (directionErrors > 0)
                issues += "$directionErrors strum${if (directionErrors > 1) "s" else ""} in the wrong direction (↓/↑ mix-up)"
            if (missedStrums > 0)
                issues += "$missedStrums missed strum${if (missedStrums > 1) "s" else ""}"
            if (extraStrums > 0)
                issues += "$extraStrums extra strum${if (extraStrums > 1) "s" else ""} (over-strumming)"
            if (timingPenalty > 5f)
                issues += "timing is off — aim for the centre of the beat"
            if (issues.isNotEmpty()) {
                append("\n")
                issues.forEach { append("\n• $it") }
            }
        }

        return AnalysisResult(
            accuracy = accuracy,
            timingOffsets = timingOffsets,
            processedSignal = currentSignal,
            detectedStrums = currentStrums,
            feedback = feedback,
            indexShift = indexShift
        )
    }
}
