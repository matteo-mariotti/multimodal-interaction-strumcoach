package com.example.strumcoach.persistence

import com.example.strumcoach.Exercise
import com.example.strumcoach.SessionStats
import com.example.strumcoach.StrumEvent
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class NetworkRepository(
    private val api: StrumCoachApi,
    private val baseUrl: String
) : StrumCoachRepository {
    
    override fun observeExercises(): Flow<List<Exercise>> = flow {
        try {
            val response = api.getExercises(userId = getUserId() ?: "")
            if (response.isSuccessful) {
                emit(response.body() ?: emptyList())
            }
        } catch (e: Exception) {
            android.util.Log.e("NetworkRepository", "Error observing exercises", e)
            emit(emptyList())
        }
    }

    override fun observeCommunityExercises(): Flow<List<Exercise>> = flow {
        try {
            val response = api.getCommunityExercises()
            if (response.isSuccessful) {
                emit(response.body() ?: emptyList())
            }
        } catch (e: Exception) {
            android.util.Log.e("NetworkRepository", "Error observing community exercises", e)
            emit(emptyList())
        }
    }

    override fun observeSessions(): Flow<List<SessionStats>> = flow {
        try {
            val response = api.getSessions(userId = getUserId() ?: "")
            if (response.isSuccessful) {
                emit(response.body() ?: emptyList())
            }
        } catch (e: Exception) {
            android.util.Log.e("NetworkRepository", "Error observing sessions", e)
            emit(emptyList())
        }
    }

    override suspend fun saveExercise(exercise: Exercise, isCommunity: Boolean): Boolean {
        return try {
            val exerciseWithUser = exercise.copy(userId = getUserId())
            if (isCommunity) {
                api.publishToCommunity(exerciseWithUser).isSuccessful
            } else {
                api.saveExercise(exerciseWithUser).isSuccessful
            }
        } catch (e: Exception) {
            android.util.Log.e("NetworkRepository", "Error saving exercise", e)
            false
        }
    }

    override suspend fun deleteExercise(exerciseId: String): Boolean {
        return try {
            api.deleteExercise(exerciseId).isSuccessful
        } catch (e: Exception) {
            android.util.Log.e("NetworkRepository", "Error deleting exercise", e)
            false
        }
    }

    override suspend fun deleteFromCommunity(exerciseId: String): Boolean {
        return try {
            api.deleteFromCommunity(exerciseId).isSuccessful
        } catch (e: Exception) {
            android.util.Log.e("NetworkRepository", "Error deleting community exercise", e)
            false
        }
    }

    override suspend fun saveSession(stats: SessionStats): Boolean {
        return try {
            // Handle audio upload if present and local
            val updatedStats = if (stats.audioUrl != null && stats.audioUrl.startsWith("/")) {
                val audioFile = File(stats.audioUrl)
                if (audioFile.exists()) {
                    val requestFile = audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("audio", audioFile.name, requestFile)
                    val idBody = stats.exerciseId.toRequestBody("text/plain".toMediaTypeOrNull())
                    
                    val uploadResponse = api.uploadAudio(body, idBody)
                    if (uploadResponse.isSuccessful) {
                        val path = uploadResponse.body()?.get("url")
                        val fullUrl = if (path != null && path.startsWith("/")) "${baseUrl.trimEnd('/')}$path" else path
                        stats.copy(audioUrl = fullUrl)
                    } else stats
                } else stats
            } else stats

            api.saveSession(updatedStats.copy(userId = getUserId())).isSuccessful
        } catch (e: Exception) {
            android.util.Log.e("NetworkRepository", "Error saving session", e)
            false
        }
    }

    override suspend fun saveReference(
        exerciseId: String,
        signal: List<Float>,
        strums: List<StrumEvent>,
        audioUrl: String?,
        durationMs: Long?
    ): Boolean {
        return try {
            val uploadedUrl = if (audioUrl != null && audioUrl.startsWith("/") && File(audioUrl).exists()) {
                val audioFile = File(audioUrl)
                val requestFile = audioFile.asRequestBody("audio/m4a".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("audio", audioFile.name, requestFile)
                val idBody = exerciseId.toRequestBody("text/plain".toMediaTypeOrNull())
                val uploadResponse = api.uploadAudio(body, idBody)
                if (uploadResponse.isSuccessful) {
                    val path = uploadResponse.body()?.get("url")
                    if (path != null && path.startsWith("/")) "${baseUrl.trimEnd('/')}$path" else path
                } else audioUrl
            } else audioUrl

            val uid = getUserId() ?: ""
            val response = api.getExercises(userId = uid)
            if (response.isSuccessful) {
                val exercises = response.body() ?: emptyList()
                val target = exercises.find { it.id == exerciseId }
                if (target != null) {
                    val updated = target.copy(
                        referenceSignal = signal,
                        referenceStrums = strums,
                        hasReference = true,
                        referenceAudioUrl = uploadedUrl ?: target.referenceAudioUrl,
                        referenceDurationMs = durationMs ?: target.referenceDurationMs,
                        userId = uid.ifEmpty { null }
                    )
                    api.saveExercise(updated).isSuccessful
                } else true
            } else false
        } catch (e: Exception) {
            android.util.Log.e("NetworkRepository", "Error saving reference", e)
            false
        }
    }


    override fun getUserId(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }
}
