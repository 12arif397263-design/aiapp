package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null
)

data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String? = "user"
)

data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

data class GeminiInlineData(
    val mimeType: String? = null,
    val data: String? = null
)

data class GeminiImageConfig(
    val aspectRatio: String? = "16:9",
    val imageSize: String? = null
)

data class GeminiGenerationConfig(
    val temperature: Float? = 0.7f,
    val topP: Float? = 0.95f,
    val topK: Int? = 40,
    val maxOutputTokens: Int? = 2048,
    val responseModalities: List<String>? = null,
    val imageConfig: GeminiImageConfig? = null
)

data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiErrorDetails? = null
)

data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null
)

data class GeminiErrorDetails(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

data class VeoGenerateVideoRequest(
    val prompt: String,
    val durationSeconds: Int = 4,
    val aspectRatio: String = "16:9",
    val fps: Int = 30
)

data class VeoOperationResponse(
    val name: String? = null,
    val done: Boolean? = false,
    val response: VeoResult? = null,
    val error: GeminiErrorDetails? = null
)

data class VeoResult(
    val videoUri: String? = null
)

data class AiVideoPromptAnalysis(
    val title: String,
    val enhancedPrompt: String,
    val suggestedStyle: String,
    val suggestedMotion: String,
    val suggestedDuration: Int,
    val suggestedAspectRatio: String,
    val moodTags: List<String>,
    val cinematicLighting: String,
    val audioAtmosphere: String
)
