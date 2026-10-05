package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.BuildConfig
import com.example.data.director.AiPromptDirector
import com.example.data.generator.VideoSynthesizer
import com.example.data.local.AppDatabase
import com.example.data.local.VideoProjectEntity
import com.example.data.model.AiVideoModelOption
import com.example.data.model.StoryScene
import com.example.data.model.SystemArchitectureOption
import com.example.data.model.VideoPresets
import com.example.data.remote.GeminiContent
import com.example.data.remote.GeminiGenerateContentRequest
import com.example.data.remote.GeminiGenerationConfig
import com.example.data.remote.GeminiImageConfig
import com.example.data.remote.GeminiPart
import com.example.data.director.ParsedPromptDirective
import com.example.data.remote.RetrofitClient
import com.example.data.remote.VeoPredictLongRunningRequest
import com.example.data.remote.VeoInstance
import com.example.data.remote.VeoParameters
import com.example.data.remote.VeoOperationResponse
import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import okhttp3.Request

data class VeoDebugInfo(
    val userPrompt: String = "",
    val finalPrompt: String = "",
    val selectedModel: String = "",
    val actualApiModel: String = "",
    val aspectRatio: String = "",
    val duration: String = "",
    val resolution: String = "",
    val operationId: String = "",
    val generationStatus: String = "Idle",
    val downloadStatus: String = "Idle"
)

class VideoRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.videoProjectDao()
    private val prefs = context.getSharedPreferences("omnivideo_prefs", Context.MODE_PRIVATE)

    var onDebugUpdate: ((VeoDebugInfo) -> Unit)? = null
    var currentDebugInfo = VeoDebugInfo()
        private set

    fun getAllVideos(): Flow<List<VideoProjectEntity>> = dao.getAllVideos()
    fun getFavoriteVideos(): Flow<List<VideoProjectEntity>> = dao.getFavoriteVideos()
    fun searchVideos(query: String): Flow<List<VideoProjectEntity>> = dao.searchVideos(query)
    fun getVideoCount(): Flow<Int> = dao.getVideoCount()

    suspend fun getVideoById(id: Long): VideoProjectEntity? = dao.getVideoById(id)

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        dao.updateFavorite(id, isFavorite)
    }

    suspend fun deleteVideo(video: VideoProjectEntity) = withContext(Dispatchers.IO) {
        try {
            val file = File(video.videoPath)
            if (file.exists()) file.delete()
            video.thumbnailPath?.let {
                val thumb = File(it)
                if (thumb.exists()) thumb.delete()
            }
        } catch (_: Exception) {}
        dao.deleteVideo(video)
    }

    fun getUserApiKey(): String {
        return prefs.getString("gemini_api_key", "") ?: ""
    }

    fun saveUserApiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key.trim()).apply()
    }

    fun getEffectiveApiKey(): String {
        val userKey = getUserApiKey()
        if (userKey.isNotBlank()) return userKey
        return try {
            val buildKey = BuildConfig.GEMINI_API_KEY
            if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") buildKey else ""
        } catch (_: Exception) {
            ""
        }
    }

    fun getSavedModelId(): String {
        return prefs.getString("selected_model_id", "veo_3_1_fast") ?: "veo_3_1_fast"
    }

    fun saveModelId(id: String) {
        prefs.edit().putString("selected_model_id", id).apply()
    }

    fun getSavedArchitectureId(): String {
        return prefs.getString("selected_arch_id", "cloud_first") ?: "cloud_first"
    }

    fun saveArchitectureId(id: String) {
        prefs.edit().putString("selected_arch_id", id).apply()
    }

    suspend fun enhancePrompt(rawPrompt: String, styleId: String, isBengali: Boolean): String = withContext(Dispatchers.IO) {
        val directive = AiPromptDirector.analyzeAndDirect(rawPrompt)
        val apiKey = getEffectiveApiKey()
        if (apiKey.isNotBlank()) {
            try {
                val systemPrompt = "You are an expert AI Video Director. " +
                        "Intelligently enhance this video prompt by adding professional cinematic details (lighting, textures, depth, camera angles) " +
                        "while STRICTLY PRESERVING the user's requested subject, characters, clothing, age, environment, actions, dialogue, and visual style (${directive.visualStyle.label}). " +
                        "Do NOT invent unrelated objects (e.g. no football or cars unless asked). " +
                        "Return ONLY the enhanced prompt in under 80 words."

                val promptToSend = "$systemPrompt\n\nUser Prompt: $rawPrompt"

                val request = GeminiGenerateContentRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(GeminiPart(text = promptToSend)),
                            role = "user"
                        )
                    )
                )

                val response = RetrofitClient.geminiService.generateContent(apiKey, request)
                val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                if (!text.isNullOrBlank()) {
                    return@withContext text
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        directive.enhancedCinematicPrompt
    }

    suspend fun generateStoryScenes(storyIdea: String): List<StoryScene> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isNotBlank()) {
            try {
                val instruction = "You are a movie director. Break down this story into exactly 3 consecutive cinematic scenes. " +
                        "For each scene provide: [Scene Number]: Title | Visual Description | Camera Angle. " +
                        "Story: $storyIdea"

                val request = GeminiGenerateContentRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(GeminiPart(text = instruction)),
                            role = "user"
                        )
                    )
                )
                val response = RetrofitClient.geminiService.generateContent(apiKey, request)
                val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()

                if (!text.isNullOrBlank()) {
                    val lines = text.lines().filter { it.isNotBlank() }
                    val parsedScenes = mutableListOf<StoryScene>()
                    var count = 1
                    for (line in lines) {
                        if (count > 3) break
                        if (line.contains("|")) {
                            val parts = line.split("|").map { it.trim() }
                            val title = parts.getOrNull(0)?.replace(Regex("^\\[?Scene\\s*\\d*]?:?", RegexOption.IGNORE_CASE), "")?.trim() ?: "Scene $count"
                            val visual = parts.getOrNull(1) ?: storyIdea
                            val cam = parts.getOrNull(2) ?: "Cinematic Pan"
                            parsedScenes.add(StoryScene(count, title, visual, cam, 4))
                            count++
                        }
                    }
                    if (parsedScenes.size >= 2) {
                        return@withContext parsedScenes
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        listOf(
            StoryScene(
                sceneNumber = 1,
                title = "The Awakening",
                visualPrompt = "Establishing shot: $storyIdea in early morning dawn with soft mist rising",
                cameraAngle = "Wide Drone Orbit",
                durationSec = 4
            ),
            StoryScene(
                sceneNumber = 2,
                title = "The Discovery",
                visualPrompt = "Dynamic focal shot: Key elements of $storyIdea moving into focus with radiant light",
                cameraAngle = "Dynamic Zoom In",
                durationSec = 4
            ),
            StoryScene(
                sceneNumber = 3,
                title = "The Climax",
                visualPrompt = "Grand panoramic view: Epic conclusion of $storyIdea with vibrant cinematic colors",
                cameraAngle = "FPV Flythrough",
                durationSec = 4
            )
        )
    }

    suspend fun createVideoProject(
        prompt: String,
        enhancedPrompt: String,
        styleId: String,
        motionId: String,
        aspectRatio: String,
        durationSeconds: Int,
        fps: Int = 30,
        resolution: String = "1080p",
        sceneCount: Int = 1,
        modelId: String = "veo_3_1_fast",
        architectureId: String = "cloud_first",
        onProgress: (Float, String) -> Unit
    ): VideoProjectEntity = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        val directive = AiPromptDirector.analyzeAndDirect(prompt)
        val finalVideoPrompt = if (enhancedPrompt.isNotBlank()) enhancedPrompt else prompt
        val model = VideoPresets.MODELS.find { it.id == modelId } ?: VideoPresets.MODELS[0]

        // SAFE DEBUG LOGS (Never log API keys or auth credentials)
        Log.d("VideoRepository", "USER PROMPT: $prompt")
        Log.d("VideoRepository", "FINAL PROMPT: $finalVideoPrompt")
        Log.d("VideoRepository", "SELECTED MODEL: ${model.name} (${model.id})")
        Log.d("VideoRepository", "API MODEL: ${model.modelTag}")

        // 1. REAL GOOGLE VEO VIDEO PIPELINE
        val isVeoModel = model.id.startsWith("veo") || model.modelTag.contains("veo")
        if (isVeoModel) {
            if (apiKey.isBlank()) {
                throw IllegalStateException("API Key is required for Google Veo generation. Please configure your Gemini API Key in the Settings menu.")
            }
            return@withContext generateRealVeoVideo(
                prompt = prompt,
                finalVideoPrompt = finalVideoPrompt,
                model = model,
                aspectRatio = aspectRatio,
                durationSeconds = durationSeconds,
                fps = fps,
                resolution = resolution,
                sceneCount = sceneCount,
                apiKey = apiKey,
                directive = directive,
                onProgress = onProgress
            )
        }

        // 2. GEMINI IMAGE SYNTHESIS (Used only when an image model is intentionally chosen)
        val isImageModel = model.id.contains("image")
        var aiBitmap: Bitmap? = null

        if (isImageModel && apiKey.isNotBlank() && architectureId != "edge_only") {
            try {
                onProgress(0.12f, "Synthesizing AI visual with ${model.name}...")

                val imageRequest = GeminiGenerateContentRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(
                                GeminiPart(text = finalVideoPrompt)
                            ),
                            role = "user"
                        )
                    ),
                    generationConfig = GeminiGenerationConfig(
                        responseModalities = listOf("IMAGE"),
                        imageConfig = GeminiImageConfig(
                            aspectRatio = when (aspectRatio) {
                                "9:16" -> "9:16"
                                "1:1" -> "1:1"
                                "4:3" -> "4:3"
                                else -> "16:9"
                            }
                        )
                    )
                )

                val imageResponse = RetrofitClient.geminiService.generateImageContent(apiKey, imageRequest)
                val base64Data = imageResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.inlineData?.data
                if (!base64Data.isNullOrBlank()) {
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    aiBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    onProgress(0.35f, "Visual frame rendered by ${model.name}...")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (architectureId == "cloud_first") {
                    throw IllegalStateException("Cloud AI Error (${model.name}): ${e.localizedMessage ?: "Failed to generate image"}. Please check your Gemini API key in Settings.")
                }
            }
        }

        // 3. ON-DEVICE ENGINE / DEMO SYNTHESIZER
        val (videoPath, thumbPath) = VideoSynthesizer.synthesizeVideo(
            context = context,
            prompt = prompt,
            styleId = styleId,
            motionId = motionId,
            aspectRatio = aspectRatio,
            durationSeconds = durationSeconds,
            fps = fps,
            sourceBitmap = aiBitmap,
            directive = directive,
            onProgress = onProgress
        )

        val title = prompt.take(35).ifBlank { "Video Project" }

        val engineDescription = when {
            aiBitmap != null -> "${model.name} (Image Synthesis)"
            else -> "On-Device Local Engine (Offline Demo)"
        }

        val entity = VideoProjectEntity(
            title = title,
            prompt = prompt,
            enhancedPrompt = finalVideoPrompt,
            style = directive.visualStyle.badge,
            cameraMotion = motionId,
            aspectRatio = aspectRatio,
            durationSeconds = durationSeconds,
            fps = fps,
            resolution = resolution,
            videoPath = videoPath,
            thumbnailPath = thumbPath,
            generationEngine = engineDescription,
            sceneCount = sceneCount
        )

        val newId = dao.insertVideo(entity)
        entity.copy(id = newId)
    }

    private suspend fun generateRealVeoVideo(
        prompt: String,
        finalVideoPrompt: String,
        model: AiVideoModelOption,
        aspectRatio: String,
        durationSeconds: Int,
        fps: Int,
        resolution: String,
        sceneCount: Int,
        apiKey: String,
        directive: ParsedPromptDirective,
        onProgress: (Float, String) -> Unit
    ): VideoProjectEntity = withContext(Dispatchers.IO) {
        val videosDir = File(context.filesDir, "videos").apply { if (!exists()) mkdirs() }
        val thumbsDir = File(context.filesDir, "thumbs").apply { if (!exists()) mkdirs() }

        val timestamp = System.currentTimeMillis()
        val videoFile = File(videosDir, "veo_${timestamp}.mp4")
        val thumbFile = File(thumbsDir, "veo_thumb_${timestamp}.jpg")

        // 1. Resolution Validation: 720p, 1080p, 4k (Requirement 5)
        val validResolution = when (resolution.lowercase()) {
            "4k" -> "4k"
            "1080p" -> "1080p"
            else -> "720p"
        }

        // 2. Duration Validation: 4, 6, 8 seconds (Requirement 3 & 5)
        // If resolution is 1080p or 4k, force duration to 8 seconds. If user selects 10, use 8.
        val validDuration = when {
            validResolution in listOf("1080p", "4k") -> 8
            durationSeconds == 4 -> 4
            durationSeconds == 6 -> 6
            else -> 8
        }

        // 3. Aspect Ratio Validation: Google Veo supports 9:16 (default) and 16:9 (Requirement 4)
        val validAspectRatio = if (aspectRatio == "16:9") "16:9" else "9:16"

        val request = VeoPredictLongRunningRequest(
            instances = listOf(
                VeoInstance(prompt = finalVideoPrompt)
            ),
            parameters = VeoParameters(
                aspectRatio = validAspectRatio,
                durationSeconds = validDuration.toString(),
                resolution = validResolution,
                numberOfVideos = 1
            )
        )

        val escapedPrompt = finalVideoPrompt.replace("\"", "\\\"").replace("\n", " ")
        val requestJson = """{"instances":[{"prompt":"$escapedPrompt"}],"parameters":{"aspectRatio":"$validAspectRatio","durationSeconds":"$validDuration","resolution":"$validResolution","numberOfVideos":1}}"""

        // SAFE DEBUG LOGS (Never log API keys or auth credentials - Requirement 10)
        Log.d("VideoRepository", "USER PROMPT: $prompt")
        Log.d("VideoRepository", "FINAL PROMPT: $finalVideoPrompt")
        Log.d("VideoRepository", "MODEL: ${model.modelTag}")
        Log.d("VideoRepository", "ENDPOINT: predictLongRunning")
        Log.d("VideoRepository", "REQUEST BODY: $requestJson")

        currentDebugInfo = VeoDebugInfo(
            userPrompt = prompt,
            finalPrompt = finalVideoPrompt,
            selectedModel = model.name,
            actualApiModel = model.modelTag,
            aspectRatio = validAspectRatio,
            duration = "${validDuration}s",
            resolution = validResolution,
            operationId = "Pending...",
            generationStatus = "Submitting request",
            downloadStatus = "Pending"
        )
        onDebugUpdate?.invoke(currentDebugInfo)

        onProgress(0.08f, "Submitting video generation request to ${model.name}...")

        val initialResponse = try {
            RetrofitClient.geminiService.predictLongRunningVeo(
                model = model.modelTag,
                apiKey = apiKey,
                request = request
            )
        } catch (e: retrofit2.HttpException) {
            val errBody = e.response()?.errorBody()?.string()
            val friendlyError = parseGoogleApiError(e.code(), errBody)
            Log.e("VideoRepository", "Veo API HTTP Error: $friendlyError")
            currentDebugInfo = currentDebugInfo.copy(generationStatus = "Failed: $friendlyError")
            onDebugUpdate?.invoke(currentDebugInfo)
            throw IllegalStateException(friendlyError)
        } catch (e: Exception) {
            Log.e("VideoRepository", "Veo API Network Error: ${e.message}")
            currentDebugInfo = currentDebugInfo.copy(generationStatus = "Failed: ${e.message}")
            onDebugUpdate?.invoke(currentDebugInfo)
            throw IllegalStateException("Failed to connect to Google Veo API: ${e.localizedMessage}")
        }

        if (initialResponse.error != null) {
            val errMsg = "Google Veo Error (${initialResponse.error.code}): ${initialResponse.error.message ?: initialResponse.error.status}"
            currentDebugInfo = currentDebugInfo.copy(generationStatus = errMsg)
            onDebugUpdate?.invoke(currentDebugInfo)
            throw IllegalStateException(errMsg)
        }

        val operationName = initialResponse.name
        Log.d("VideoRepository", "OPERATION NAME: $operationName")

        currentDebugInfo = currentDebugInfo.copy(
            operationId = operationName ?: "None",
            generationStatus = "Generating with Veo"
        )
        onDebugUpdate?.invoke(currentDebugInfo)

        if (operationName.isNullOrBlank() && initialResponse.done != true) {
            throw IllegalStateException("Google Veo did not return an operation job name.")
        }

        var completedResponse: VeoOperationResponse = initialResponse

        // Asynchronous Polling Loop (Poll approximately every 8 seconds)
        if (initialResponse.done != true && !operationName.isNullOrBlank()) {
            val cleanOpName = if (operationName.startsWith("v1beta/")) {
                operationName.removePrefix("v1beta/")
            } else {
                operationName
            }

            val startTime = System.currentTimeMillis()
            val maxWaitMillis = 240_000L // 4 minutes max timeout
            val pollIntervalMillis = 8_000L // Poll every 8 seconds per PART H

            while (true) {
                delay(pollIntervalMillis)
                val elapsedSec = (System.currentTimeMillis() - startTime) / 1000

                // 10-80% progress while generating with Veo
                val progress = 0.10f + minOf(0.70f, (elapsedSec.toFloat() / 70f) * 0.70f)
                onProgress(progress, "Generating with Veo... (${elapsedSec}s)")

                currentDebugInfo = currentDebugInfo.copy(
                    generationStatus = "Generating with Veo (${elapsedSec}s elapsed)"
                )
                onDebugUpdate?.invoke(currentDebugInfo)

                val pollResult = try {
                    RetrofitClient.geminiService.getOperation(cleanOpName, apiKey)
                } catch (e: Exception) {
                    try {
                        RetrofitClient.geminiService.getOperationByUrl(operationName, apiKey)
                    } catch (e2: Exception) {
                        Log.w("VideoRepository", "Poll transient error: ${e2.message}, will retry...")
                        null
                    }
                }

                if (pollResult != null) {
                    Log.d("VideoRepository", "OPERATION STATUS: done=${pollResult.done}")

                    if (pollResult.error != null) {
                        val errMsg = "Google Veo Generation Failed (${pollResult.error.code}): ${pollResult.error.message ?: pollResult.error.status}"
                        currentDebugInfo = currentDebugInfo.copy(generationStatus = errMsg)
                        onDebugUpdate?.invoke(currentDebugInfo)
                        throw IllegalStateException(errMsg)
                    }

                    if (pollResult.done == true) {
                        completedResponse = pollResult
                        currentDebugInfo = currentDebugInfo.copy(generationStatus = "Done")
                        onDebugUpdate?.invoke(currentDebugInfo)
                        break
                    }
                }

                if (System.currentTimeMillis() - startTime > maxWaitMillis) {
                    val timeoutMsg = "Google Veo generation timed out after ${elapsedSec}s. Please try again."
                    currentDebugInfo = currentDebugInfo.copy(generationStatus = timeoutMsg)
                    onDebugUpdate?.invoke(currentDebugInfo)
                    throw IllegalStateException(timeoutMsg)
                }
            }
        }

        // 80-90%: Preparing video
        onProgress(0.85f, "Preparing video...")
        currentDebugInfo = currentDebugInfo.copy(downloadStatus = "Extracting video URI")
        onDebugUpdate?.invoke(currentDebugInfo)

        val videoUri = completedResponse.extractVideoUri()
        val base64Bytes = completedResponse.extractVideoBase64()

        if (videoUri.isNullOrBlank() && base64Bytes.isNullOrBlank()) {
            throw IllegalStateException("Google Veo completed generation but did not return video data or download link.")
        }

        Log.d("VideoRepository", "VIDEO DOWNLOAD STARTED: ${videoUri ?: "Base64 bytes"}")
        // 90-100%: Downloading video
        onProgress(0.92f, "Downloading video from Google Cloud...")
        currentDebugInfo = currentDebugInfo.copy(downloadStatus = "Downloading from cloud...")
        onDebugUpdate?.invoke(currentDebugInfo)

        if (!base64Bytes.isNullOrBlank()) {
            val bytes = Base64.decode(base64Bytes, Base64.DEFAULT)
            videoFile.writeBytes(bytes)
        } else if (!videoUri.isNullOrBlank()) {
            downloadFileFromUri(videoUri, apiKey, videoFile, onProgress)
        }

        Log.d("VideoRepository", "VIDEO DOWNLOAD COMPLETED: ${videoFile.length()} bytes saved to ${videoFile.name}")
        currentDebugInfo = currentDebugInfo.copy(downloadStatus = "Completed (${videoFile.length() / 1024} KB)")
        onDebugUpdate?.invoke(currentDebugInfo)

        onProgress(0.98f, "Processing video metadata & preview frame...")

        // Extract real frame from the downloaded MP4 file using MediaMetadataRetriever
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoFile.absolutePath)
            val frame = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            retriever.release()

            if (frame != null) {
                FileOutputStream(thumbFile).use { out ->
                    frame.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            }
        } catch (e: Exception) {
            Log.w("VideoRepository", "Could not extract video thumbnail: ${e.message}")
        }

        onProgress(1.0f, "Google Veo video ready!")

        val title = prompt.take(35).ifBlank { "Veo AI Video" }

        val entity = VideoProjectEntity(
            title = title,
            prompt = prompt,
            enhancedPrompt = finalVideoPrompt,
            style = directive.visualStyle.badge,
            cameraMotion = directive.cameraDirection,
            aspectRatio = aspectRatio,
            durationSeconds = durationSeconds,
            fps = 24,
            resolution = resolution,
            videoPath = videoFile.absolutePath,
            thumbnailPath = if (thumbFile.exists()) thumbFile.absolutePath else null,
            generationEngine = "${model.name} (Real Google Veo Cloud AI)",
            sceneCount = sceneCount
        )

        val newId = dao.insertVideo(entity)
        entity.copy(id = newId)
    }

    companion object {
        fun parseGoogleApiError(code: Int, rawBody: String?): String {
            var message = ""
            var status = ""
            if (!rawBody.isNullOrBlank()) {
                try {
                    val json = org.json.JSONObject(rawBody)
                    if (json.has("error")) {
                        val err = json.getJSONObject("error")
                        status = err.optString("status", "")
                        message = err.optString("message", "")
                    }
                } catch (e: Exception) {
                    message = rawBody.take(400)
                }
            }
            if (message.isBlank()) {
                message = rawBody ?: "HTTP $code Error"
            }

            val isAccessOrQuota = code in listOf(400, 401, 403, 404, 429) ||
                    status in listOf("PERMISSION_DENIED", "RESOURCE_EXHAUSTED", "FAILED_PRECONDITION") ||
                    message.contains("billing", ignoreCase = true) ||
                    message.contains("quota", ignoreCase = true) ||
                    message.contains("API has not been used", ignoreCase = true) ||
                    message.contains("disabled", ignoreCase = true)

            val builder = StringBuilder()
            builder.append("Google Veo API Error ($code")
            if (status.isNotBlank()) builder.append(" - $status")
            builder.append("):\n$message")

            if (isAccessOrQuota) {
                builder.append("\n\n[Google Cloud Notice]: Google DeepMind Veo video generation requires an active Google Cloud project with Generative Language API access and billing enabled. Please verify your project billing and API access at console.cloud.google.com.")
            }
            return builder.toString()
        }
    }

    suspend fun testVeoApiConnection(apiKey: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Pair(false, "API Key is empty. Please configure your Gemini API key in Settings.")
        }
        try {
            val testRequest = VeoPredictLongRunningRequest(
                instances = listOf(VeoInstance(prompt = "minimal test prompt")),
                parameters = VeoParameters(
                    aspectRatio = "9:16",
                    durationSeconds = "8",
                    resolution = "720p",
                    numberOfVideos = 1
                )
            )
            val res = RetrofitClient.geminiService.predictLongRunningVeo(
                model = "veo-3.1-fast-generate-preview",
                apiKey = apiKey,
                request = testRequest
            )
            if (res.error != null) {
                val err = "Veo API Error (${res.error.code} - ${res.error.status}): ${res.error.message}"
                return@withContext Pair(false, err)
            }
            Pair(true, "API connected successfully!\nOperation initialized: ${res.name ?: "OK"}")
        } catch (e: retrofit2.HttpException) {
            val raw = e.response()?.errorBody()?.string()
            val parsed = parseGoogleApiError(e.code(), raw)
            Pair(false, parsed)
        } catch (e: Exception) {
            Pair(false, "Connection Error: ${e.localizedMessage ?: e.message}")
        }
    }

    private suspend fun downloadFileFromUri(
        uriString: String,
        apiKey: String,
        destinationFile: File,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        // Requirement 9: Download the returned video URI with x-goog-api-key: API_KEY. Do NOT append ?key=API_KEY to the URL.
        val request = Request.Builder()
            .url(uriString)
            .addHeader("x-goog-api-key", apiKey)
            .build()

        val response = RetrofitClient.okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string()?.take(300)
            throw IllegalStateException("Failed to download generated video from cloud (HTTP ${response.code}: $errBody)")
        }

        val body = response.body ?: throw IllegalStateException("Empty response body from video download URI")
        val contentLength = body.contentLength()

        body.byteStream().use { input ->
            FileOutputStream(destinationFile).use { output ->
                val buffer = ByteArray(16 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    if (contentLength > 0) {
                        val dlProgress = 0.88f + (totalBytesRead.toFloat() / contentLength) * 0.08f
                        onProgress(dlProgress, "Downloading AI Video (${totalBytesRead / 1024} KB)...")
                    }
                }
                output.flush()
            }
        }
    }
}
