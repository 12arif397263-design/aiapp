package com.example.data.repository

import android.content.Context
import com.example.BuildConfig
import com.example.data.generator.VideoSynthesizer
import com.example.data.local.AppDatabase
import com.example.data.local.VideoProjectEntity
import com.example.data.model.StoryScene
import com.example.data.model.VideoPresets
import com.example.data.remote.GeminiContent
import com.example.data.remote.GeminiGenerateContentRequest
import com.example.data.remote.GeminiPart
import com.example.data.remote.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class VideoRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.videoProjectDao()

    fun getAllVideos(): Flow<List<VideoProjectEntity>> = dao.getAllVideos()
    fun getFavoriteVideos(): Flow<List<VideoProjectEntity>> = dao.getFavoriteVideos()
    fun searchVideos(query: String): Flow<List<VideoProjectEntity>> = dao.searchVideos(query)
    fun getVideoCount(): Flow<Int> = dao.getVideoCount()

    suspend fun getVideoById(id: Long): VideoProjectEntity? = dao.getVideoById(id)

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        dao.updateFavorite(id, isFavorite)
    }

    suspend fun deleteVideo(video: VideoProjectEntity) = withContext(Dispatchers.IO) {
        // Delete video file from storage
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

    suspend fun enhancePrompt(rawPrompt: String, styleId: String, isBengali: Boolean): String = withContext(Dispatchers.IO) {
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
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
        val apiKey = try { BuildConfig.GEMINI_API_KEY } catch (_: Exception) { "" }
        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
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

        // Algorithmic default scenes
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
        onProgress: (Float, String) -> Unit
    ): VideoProjectEntity = withContext(Dispatchers.IO) {
        val (videoPath, thumbPath) = VideoSynthesizer.synthesizeVideo(
            context = context,
            prompt = enhancedPrompt.ifBlank { prompt },
            styleId = styleId,
            motionId = motionId,
            aspectRatio = aspectRatio,
            durationSeconds = durationSeconds,
            fps = fps,
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
            generationEngine = "AI Video Synthesizer (Neural H.264)",
            sceneCount = sceneCount
        )

        val newId = dao.insertVideo(entity)
        entity.copy(id = newId)
    }
}
