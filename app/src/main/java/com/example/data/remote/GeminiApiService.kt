package com.example.data.remote

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateContentRequest
    ): GeminiGenerateContentResponse

    @POST("v1beta/models/gemini-2.5-flash-image:generateContent")
    suspend fun generateImageContent(
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateContentRequest
    ): GeminiGenerateContentResponse

    @POST("v1beta/models/{model}:predictLongRunning")
    suspend fun predictLongRunningVeo(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: VeoPredictLongRunningRequest
    ): VeoOperationResponse

    @POST("v1beta/models/{model}:predictLongRunning")
    suspend fun generateVideoVeo(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: VeoPredictLongRunningRequest
    ): VeoOperationResponse

    @GET("v1beta/{operationName}")
    suspend fun getOperation(
        @Path(value = "operationName", encoded = true) operationName: String,
        @Query("key") apiKey: String
    ): VeoOperationResponse

    @GET
    suspend fun getOperationByUrl(
        @Url url: String,
        @Query("key") apiKey: String
    ): VeoOperationResponse

    @Streaming
    @GET
    suspend fun downloadVideoFile(
        @Url url: String
    ): ResponseBody
}
