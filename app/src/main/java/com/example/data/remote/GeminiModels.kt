package com.example.data.remote

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

// PART B: OFFICIAL VEO REST REQUEST STRUCTURE
data class VeoPredictLongRunningRequest(
    val instances: List<VeoInstance>,
    val parameters: VeoParameters
)

data class VeoInstance(
    val prompt: String
)

data class VeoParameters(
    val aspectRatio: String? = "9:16",
    val durationSeconds: String? = "8",
    val resolution: String? = "720p",
    val numberOfVideos: Int? = 1
)

typealias VeoGenerateVideoRequest = VeoPredictLongRunningRequest

// PART G: OFFICIAL VEO REST RESPONSE STRUCTURE
data class GeneratedVideoFile(
    val uri: String? = null,
    val downloadUri: String? = null,
    val bytesBase64Encoded: String? = null
)

typealias VeoVideoPayload = GeneratedVideoFile

data class GeneratedSample(
    val video: GeneratedVideoFile? = null
)

typealias VeoGeneratedSample = GeneratedSample
typealias VeoGeneratedVideo = GeneratedSample

data class GenerateVideoResponse(
    val generatedSamples: List<GeneratedSample>? = null,
    val generatedVideos: List<GeneratedSample>? = null
)

typealias VeoGenerateVideoResponse = GenerateVideoResponse

data class VeoResult(
    val generateVideoResponse: GenerateVideoResponse? = null,
    val generatedSamples: List<GeneratedSample>? = null,
    val generatedVideos: List<GeneratedSample>? = null,
    val videoUri: String? = null,
    val uri: String? = null,
    val video: GeneratedVideoFile? = null
)

data class VeoOperationResponse(
    val name: String? = null,
    val done: Boolean? = false,
    val response: VeoResult? = null,
    val error: GeminiErrorDetails? = null,
    val metadata: Map<String, Any?>? = null
) {
    fun extractVideoUri(): String? {
        // Requirement 8: Use exactly response.generateVideoResponse.generatedSamples[0].video.uri
        val directUri = response?.generateVideoResponse?.generatedSamples?.firstOrNull()?.video?.uri
        if (!directUri.isNullOrBlank()) return directUri

        val downloadUri = response?.generateVideoResponse?.generatedSamples?.firstOrNull()?.video?.downloadUri
        if (!downloadUri.isNullOrBlank()) return downloadUri

        val sampleUri = response?.generatedSamples?.firstOrNull()?.video?.uri
            ?: response?.generatedSamples?.firstOrNull()?.video?.downloadUri
        if (!sampleUri.isNullOrBlank()) return sampleUri

        return response?.videoUri ?: response?.uri
    }

    fun extractVideoBase64(): String? {
        response?.generateVideoResponse?.generatedSamples?.firstOrNull()?.video?.bytesBase64Encoded?.let {
            if (it.isNotBlank()) return it
        }
        response?.generatedSamples?.firstOrNull()?.video?.bytesBase64Encoded?.let {
            if (it.isNotBlank()) return it
        }
        response?.video?.bytesBase64Encoded?.let {
            if (it.isNotBlank()) return it
        }
        return null
    }
}

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
