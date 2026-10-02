package com.example.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateContentRequest
    ): GeminiGenerateContentResponse

    @POST("v1beta/models/veo-3.1-fast-generate-preview:generateVideos")
    suspend fun generateVideoVeo(
        @Query("key") apiKey: String,
        @Body request: VeoGenerateVideoRequest
    ): VeoOperationResponse

    @GET("v1beta/{operationName}")
    suspend fun getOperation(
        @Path(value = "operationName", encoded = true) operationName: String,
        @Query("key") apiKey: String
    ): VeoOperationResponse
}
