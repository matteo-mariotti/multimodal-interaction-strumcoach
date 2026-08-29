package com.example.strumcoach.persistence

import com.example.strumcoach.Exercise
import com.example.strumcoach.SessionStats
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface StrumCoachApi {
    @GET("exercises")
    suspend fun getExercises(@Query("userId") userId: String = ""): Response<List<Exercise>>

    @POST("exercises")
    suspend fun saveExercise(@Body exercise: Exercise): Response<Exercise>

    @DELETE("exercises/{id}")
    suspend fun deleteExercise(@Path("id") id: String): Response<Unit>

    @GET("community")
    suspend fun getCommunityExercises(): Response<List<Exercise>>

    @POST("community")
    suspend fun publishToCommunity(@Body exercise: Exercise): Response<Exercise>

    @DELETE("community/{id}")
    suspend fun deleteFromCommunity(@Path("id") id: String): Response<Unit>

    @GET("sessions")
    suspend fun getSessions(@Query("userId") userId: String = ""): Response<List<SessionStats>>

    @POST("sessions")
    suspend fun saveSession(@Body stats: SessionStats): Response<SessionStats>

    @Multipart
    @POST("audio/upload")
    suspend fun uploadAudio(
        @Part audio: MultipartBody.Part,
        @Part("exerciseId") exerciseId: RequestBody
    ): Response<Map<String, String>>
}
