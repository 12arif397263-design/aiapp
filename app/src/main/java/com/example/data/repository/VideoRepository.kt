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
import com.example.data.remote.VeoGenerateVideoRequest
import com.example.data.remote.VeoOperationResponse
import com.example.data.remote.VeoVideoConfig
import android.media.MediaMetadataRetriever
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import okhttp3.Request

class VideoRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.videoProjectDao()
    private val prefs = context.getSharedPreferences("omnivideo_prefs", Context.MODE_PRIVATE)

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
        val finalVideoPrompt = enhancedPrompt.ifBlank { directive.enhancedCinematicPrompt }
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

        Log.d("VideoRepository", "REQUEST STARTED: Calling ${model.modelTag}")
        onProgress(0.08f, "Submitting video generation request to ${model.name}...")

        val targetDuration = durationSeconds.coerceIn(4, 10)
        val validAspectRatio = when (aspectRatio) {
            "9:16" -> "9:16"
            "1:1" -> "1:1"
            "4:3" -> "4:3"
            else -> "16:9"
        }

        val request = VeoGenerateVideoRequest(
            prompt = finalVideoPrompt,
            durationSeconds = targetDuration,
            aspectRatio = validAspectRatio,
            fps = 24,
            videoConfig = VeoVideoConfig(
                durationSeconds = targetDuration,
                aspectRatio = validAspectRatio,
                fps = 24
            )
        )

        val initialResponse = try {
            RetrofitClient.geminiService.generateVideoVeo(
                model = model.modelTag,
                apiKey = apiKey,
                request = request
            )
        } catch (e: retrofit2.HttpException) {
            val errBody = e.response()?.errorBody()?.string() ?: e.message()
            Log.e("VideoRepository", "Veo API HTTP Error: ${e.code()} - $errBody")
            throw IllegalStateException("Google Veo Request Failed (${e.code()}): $errBody")
        } catch (e: Exception) {
            Log.e("VideoRepository", "Veo API Network Error: ${e.message}")
            throw IllegalStateException("Failed to connect to Google Veo API: ${e.localizedMessage}")
        }

        if (initialResponse.error != null) {
            throw IllegalStateException("Google Veo Error (${initialResponse.error.code}): ${initialResponse.error.message ?: initialResponse.error.status}")
        }

        val operationName = initialResponse.name
        Log.d("VideoRepository", "OPERATION NAME: $operationName")

        if (operationName.isNullOrBlank() && initialResponse.done != true) {
            throw IllegalStateException("Google Veo did not return an operation job name.")
        }

        var completedResponse: VeoOperationResponse = initialResponse

        // Asynchronous Polling Loop
        if (initialResponse.done != true && !operationName.isNullOrBlank()) {
            val cleanOpName = if (operationName.startsWith("v1beta/")) {
                operationName.removePrefix("v1beta/")
            } else {
                operationName
            }

            val startTime = System.currentTimeMillis()
            val maxWaitMillis = 240_000L // 4 minutes max timeout
            val pollIntervalMillis = 4_000L // Poll every 4 seconds

            while (true) {
                delay(pollIntervalMillis)
                val elapsedSec = (System.currentTimeMillis() - startTime) / 1000

                // Progress advances from 0.15f to 0.85f based on elapsed time
                val progress = 0.15f + minOf(0.70f, (elapsedSec.toFloat() / 85f) * 0.70f)
                onProgress(progress, "Google Veo generating cinematic video... (${elapsedSec}s)")

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
                        throw IllegalStateException("Google Veo Generation Failed (${pollResult.error.code}): ${pollResult.error.message ?: pollResult.error.status}")
                    }

                    if (pollResult.done == true) {
                        completedResponse = pollResult
                        break
                    }
                }

                if (System.currentTimeMillis() - startTime > maxWaitMillis) {
                    throw IllegalStateException("Google Veo generation timed out after ${elapsedSec}s. Please try again.")
                }
            }
        }

        onProgress(0.86f, "Video generated by Veo! Preparing download...")

        val videoUri = completedResponse.extractVideoUri()
        val base64Bytes = completedResponse.extractVideoBase64()

        if (videoUri.isNullOrBlank() && base64Bytes.isNullOrBlank()) {
            throw IllegalStateException("Google Veo completed generation but did not return video data or download link.")
        }

        Log.d("VideoRepository", "VIDEO DOWNLOAD STARTED: ${videoUri ?: "Base64 bytes"}")
        onProgress(0.88f, "Downloading generated video file from Google Cloud...")

        if (!base64Bytes.isNullOrBlank()) {
            val bytes = Base64.decode(base64Bytes, Base64.DEFAULT)
            videoFile.writeBytes(bytes)
        } else if (!videoUri.isNullOrBlank()) {
            downloadFileFromUri(videoUri, apiKey, videoFile, onProgress)
        }

        Log.d("VideoRepository", "VIDEO DOWNLOAD COMPLETED: ${videoFile.length()} bytes saved to ${videoFile.name}")
        onProgress(0.96f, "Processing video metadata & preview frame...")

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

    private suspend fun downloadFileFromUri(
        uriString: String,
        apiKey: String,
        destinationFile: File,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val finalUrl = if (uriString.contains("generativelanguage.googleapis.com") && !uriString.contains("key=")) {
            if (uriString.contains("?")) "$uriString&key=$apiKey" else "$uriString?key=$apiKey"
        } else {
            uriString
        }

        val request = Request.Builder()
            .url(finalUrl)
            .build()

        val response = RetrofitClient.okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string()?.take(200)
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
