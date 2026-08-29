package com.example.strumcoach.persistence

import com.example.strumcoach.Exercise
import com.example.strumcoach.SessionStats
import com.example.strumcoach.StrumEvent
import kotlinx.coroutines.flow.Flow

interface StrumCoachRepository {
    fun observeExercises(): Flow<List<Exercise>>
    fun observeCommunityExercises(): Flow<List<Exercise>>
    fun observeSessions(): Flow<List<SessionStats>>
    
    suspend fun saveExercise(exercise: Exercise, isCommunity: Boolean = false): Boolean
    suspend fun deleteExercise(exerciseId: String): Boolean
    suspend fun deleteFromCommunity(exerciseId: String): Boolean
    suspend fun saveSession(stats: SessionStats): Boolean
    suspend fun saveReference(exerciseId: String, signal: List<Float>, strums: List<StrumEvent>, audioUrl: String? = null, durationMs: Long? = null): Boolean

    fun getUserId(): String?
}
