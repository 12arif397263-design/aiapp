package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.BuildConfig
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
import com.example.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

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
        return prefs.getString("selected_model_id", "gemini_2_5_flash_image") ?: "gemini_2_5_flash_image"
    }

    fun saveModelId(id: String) {
        prefs.edit().putString("selected_model_id", id).apply()
    }

    fun getSavedArchitectureId(): String {
        return prefs.getString("selected_arch_id", "hybrid") ?: "hybrid"
    }

    fun saveArchitectureId(id: String) {
        prefs.edit().putString("selected_arch_id", id).apply()
    }

    suspend fun enhancePrompt(rawPrompt: String, styleId: String, isBengali: Boolean): String = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isNotBlank()) {
            try {
                val style = VideoPresets.STYLES.find { it.id == styleId }
                val systemPrompt = "You are a world-class Hollywood cinematographer and AI video prompt engineer. " +
                        "Convert this brief user idea into a hyper-detailed cinematic prompt for Veo/Sora. " +
                        "Include camera motion, lighting (volumetric, anamorphic lens flares), depth of field, color grading, atmosphere, and texture. " +
                        "Keep it vivid, concise (under 70 words), and return ONLY the enhanced prompt text with no quotation marks or meta commentary."

                val promptToSend = "$systemPrompt\n\nStyle: ${style?.name ?: "Cinematic"}\nUser Idea: $rawPrompt"

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

        // Algorithmic offline enhancer
        val style = VideoPresets.STYLES.find { it.id == styleId }
        val suffix = style?.promptSuffix ?: "photorealistic 8k, cinematic lighting, 35mm lens"
        if (isBengali) {
            "$rawPrompt, সিনেমাটিক মাস্টারপিস, ৮কে রেজোলিউশন, ড্রামাটিক লাইটিং, হাইপার-ডিটেইল্ড ভিজ্যুয়ালস, $suffix"
        } else {
            "Masterpiece cinematic shot of $rawPrompt, captured on ARRI Alexa 65, 35mm anamorphic lens, volumetric lighting, photorealistic textures, 8k resolution, $suffix"
        }
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
        modelId: String = "gemini_2_5_flash_image",
        architectureId: String = "hybrid",
        onProgress: (Float, String) -> Unit
    ): VideoProjectEntity = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        var aiBitmap: Bitmap? = null
        val effectivePrompt = enhancedPrompt.ifBlank { prompt }
        val model = VideoPresets.MODELS.find { it.id == modelId } ?: VideoPresets.MODELS[1]

        val isEdgeOnly = architectureId == "edge_only" || modelId == "on_device_neural"

        if (!isEdgeOnly && apiKey.isNotBlank()) {
            try {
                onProgress(0.08f, "Connecting to ${model.name}...")

                val imageRequest = GeminiGenerateContentRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(
                                GeminiPart(text = "High resolution masterpiece cinematic shot of $effectivePrompt, detailed textures, dramatic lighting, 8k")
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
                    onProgress(0.28f, "AI visual generated by ${model.name}! Synthesizing motion...")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (isEdgeOnly) {
            onProgress(0.10f, "Using On-Device Engine (Zero-Quota)...")
        }

        val (videoPath, thumbPath) = VideoSynthesizer.synthesizeVideo(
            context = context,
            prompt = effectivePrompt,
            styleId = styleId,
            motionId = motionId,
            aspectRatio = aspectRatio,
            durationSeconds = durationSeconds,
            fps = fps,
            sourceBitmap = aiBitmap,
            onProgress = onProgress
        )

        val title = prompt.take(35).ifBlank { "AI Video Project" }

        val entity = VideoProjectEntity(
            title = title,
            prompt = prompt,
            enhancedPrompt = enhancedPrompt,
            style = styleId,
            cameraMotion = motionId,
            aspectRatio = aspectRatio,
            durationSeconds = durationSeconds,
            fps = fps,
            resolution = resolution,
            videoPath = videoPath,
            thumbnailPath = thumbPath,
            generationEngine = "${model.name} (${if (aiBitmap != null) "Cloud AI Active" else "Neural Synthesizer"})",
            sceneCount = sceneCount
        )

        val newId = dao.insertVideo(entity)
        entity.copy(id = newId)
    }
}
