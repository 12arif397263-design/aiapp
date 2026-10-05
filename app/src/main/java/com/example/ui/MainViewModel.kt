package com.example.ui

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.VideoProjectEntity
import com.example.data.model.GenerationUiState
import com.example.data.model.PresetPrompt
import com.example.data.model.StoryScene
import com.example.data.model.VideoPresets
import com.example.data.remote.GeminiContent
import com.example.data.remote.GeminiGenerateContentRequest
import com.example.data.remote.GeminiPart
import com.example.data.remote.RetrofitClient
import com.example.data.repository.VeoDebugInfo
import com.example.data.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

enum class AppScreen {
    GENERATOR,
    STORY_STUDIO,
    PLAYER,
    LIBRARY,
    SETTINGS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VideoRepository(application)

    // Current Screen
    private val _currentScreen = MutableStateFlow(AppScreen.GENERATOR)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Language: true for Bengali (বাংলা), false for English
    private val _isBengali = MutableStateFlow(true)
    val isBengali: StateFlow<Boolean> = _isBengali.asStateFlow()

    // Prompt
    private val _promptText = MutableStateFlow("")
    val promptText: StateFlow<String> = _promptText.asStateFlow()

    private val _enhancedPrompt = MutableStateFlow("")
    val enhancedPrompt: StateFlow<String> = _enhancedPrompt.asStateFlow()

    // AI Enhance Mode vs Direct Mode (PART L)
    private val _isAiEnhanceMode = MutableStateFlow(false)
    val isAiEnhanceMode: StateFlow<Boolean> = _isAiEnhanceMode.asStateFlow()

    private val _isEnhancing = MutableStateFlow(false)
    val isEnhancing: StateFlow<Boolean> = _isEnhancing.asStateFlow()

    // Model
    private val _selectedModel = MutableStateFlow(
        VideoPresets.MODELS.find { it.id == repository.getSavedModelId() } ?: VideoPresets.MODELS[0]
    )
    val selectedModel: StateFlow<com.example.data.model.AiVideoModelOption> = _selectedModel.asStateFlow()

    // Architecture Selection
    private val _selectedArchitecture = MutableStateFlow(
        VideoPresets.ARCHITECTURES.find { it.id == repository.getSavedArchitectureId() } ?: VideoPresets.ARCHITECTURES[0]
    )
    val selectedArchitecture: StateFlow<com.example.data.model.SystemArchitectureOption> = _selectedArchitecture.asStateFlow()

    // Aspect Ratio (Default 9:16 for Shorts/Reels - PART F)
    private val _selectedAspectRatio = MutableStateFlow(VideoPresets.ASPECT_RATIOS.first())
    val selectedAspectRatio: StateFlow<com.example.data.model.AspectRatioOption> = _selectedAspectRatio.asStateFlow()

    // Duration (Default 8s - PART E)
    private val _selectedDuration = MutableStateFlow(8)
    val selectedDuration: StateFlow<Int> = _selectedDuration.asStateFlow()

    // Resolution (Default 720p - PART B & PART E)
    private val _selectedResolution = MutableStateFlow("720p")
    val selectedResolution: StateFlow<String> = _selectedResolution.asStateFlow()

    // Style & Motion (Optional aesthetic enhancements)
    private val _selectedStyle = MutableStateFlow(VideoPresets.STYLES.first())
    val selectedStyle: StateFlow<com.example.data.model.VideoStyle> = _selectedStyle.asStateFlow()

    private val _selectedMotion = MutableStateFlow(VideoPresets.MOTIONS.first())
    val selectedMotion: StateFlow<com.example.data.model.CameraMotion> = _selectedMotion.asStateFlow()

    private val _motionStrength = MutableStateFlow(5)
    val motionStrength: StateFlow<Int> = _motionStrength.asStateFlow()

    // API Key
    private val _userApiKey = MutableStateFlow(repository.getUserApiKey())
    val userApiKey: StateFlow<String> = _userApiKey.asStateFlow()

    // Development Debug Info (PART N)
    private val _debugInfo = MutableStateFlow(repository.currentDebugInfo)
    val debugInfo: StateFlow<VeoDebugInfo> = _debugInfo.asStateFlow()

    // Generation state
    private val _generationState = MutableStateFlow<GenerationUiState>(GenerationUiState.Idle)
    val generationState: StateFlow<GenerationUiState> = _generationState.asStateFlow()

    // Currently playing/inspected video
    private val _activeVideo = MutableStateFlow<VideoProjectEntity?>(null)
    val activeVideo: StateFlow<VideoProjectEntity?> = _activeVideo.asStateFlow()

    // Library Search & Filter
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterOnlyFavorites = MutableStateFlow(false)
    val filterOnlyFavorites: StateFlow<Boolean> = _filterOnlyFavorites.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow("All")
    val selectedCategoryFilter: StateFlow<String> = _selectedCategoryFilter.asStateFlow()

    // Story Studio State
    private val _storyIdea = MutableStateFlow("")
    val storyIdea: StateFlow<String> = _storyIdea.asStateFlow()

    private val _storyScenes = MutableStateFlow<List<StoryScene>>(emptyList())
    val storyScenes: StateFlow<List<StoryScene>> = _storyScenes.asStateFlow()

    private val _isGeneratingStoryScenes = MutableStateFlow(false)
    val isGeneratingStoryScenes: StateFlow<Boolean> = _isGeneratingStoryScenes.asStateFlow()

    // Video List
    val videoList: StateFlow<List<VideoProjectEntity>> = combine(
        repository.getAllVideos(),
        _searchQuery,
        _filterOnlyFavorites,
        _selectedCategoryFilter
    ) { all, query, onlyFavs, category ->
        all.filter { video ->
            val matchesQuery = query.isBlank() ||
                    video.prompt.contains(query, ignoreCase = true) ||
                    video.title.contains(query, ignoreCase = true)
            val matchesFav = !onlyFavs || video.isFavorite
            val matchesCat = category == "All" ||
                    video.style.contains(category, ignoreCase = true) ||
                    video.cameraMotion.contains(category, ignoreCase = true)
            matchesQuery && matchesFav && matchesCat
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalVideoCount: StateFlow<Int> = repository.getVideoCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    init {
        repository.onDebugUpdate = { info ->
            _debugInfo.value = info
        }
    }

    fun hasApiKey(): Boolean {
        return repository.getEffectiveApiKey().isNotBlank()
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun toggleLanguage() {
        _isBengali.value = !_isBengali.value
    }

    fun setPrompt(text: String) {
        _promptText.value = text
        if (_isAiEnhanceMode.value) {
            _enhancedPrompt.value = "" // invalidate cached enhancement
        }
    }

    fun toggleAiEnhanceMode() {
        val newMode = !_isAiEnhanceMode.value
        _isAiEnhanceMode.value = newMode
        if (newMode && _enhancedPrompt.value.isBlank() && _promptText.value.isNotBlank()) {
            enhanceCurrentPrompt()
        }
    }

    fun enhanceCurrentPrompt() {
        val raw = _promptText.value.trim()
        if (raw.isBlank()) return
        viewModelScope.launch {
            _isEnhancing.value = true
            val enhanced = repository.enhancePrompt(raw, _selectedStyle.value.id, _isBengali.value)
            _enhancedPrompt.value = enhanced
            _isEnhancing.value = false
        }
    }

    fun setEnhancedPromptText(text: String) {
        _enhancedPrompt.value = text
    }

    fun selectModel(model: com.example.data.model.AiVideoModelOption) {
        _selectedModel.value = model
        repository.saveModelId(model.id)
    }

    fun setAspectRatio(ratio: com.example.data.model.AspectRatioOption) {
        _selectedAspectRatio.value = ratio
    }

    fun setDuration(duration: Int) {
        if (duration > 8) {
            _selectedDuration.value = 8
            Toast.makeText(
                getApplication(),
                if (_isBengali.value) "Veo প্রতি জেনারেশনে সর্বোচ্চ ৮ সেকেন্ড সমর্থন করে।" else "Veo supports up to 8 seconds per generation.",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            _selectedDuration.value = duration
        }
    }

    fun setResolution(res: String) {
        _selectedResolution.value = res
    }

    fun setStyle(style: com.example.data.model.VideoStyle) {
        _selectedStyle.value = style
    }

    fun setMotion(motion: com.example.data.model.CameraMotion) {
        _selectedMotion.value = motion
    }

    fun setMotionStrength(strength: Int) {
        _motionStrength.value = strength
    }

    fun setMotionStrength(strength: Float) {
        _motionStrength.value = strength.toInt()
    }

    fun selectArchitecture(arch: com.example.data.model.SystemArchitectureOption) {
        _selectedArchitecture.value = arch
        repository.saveArchitectureId(arch.id)
    }

    fun applyPreset(preset: PresetPrompt) {
        _promptText.value = preset.prompt
        _enhancedPrompt.value = ""
        VideoPresets.STYLES.find { it.id == preset.styleId }?.let { _selectedStyle.value = it }
        VideoPresets.MOTIONS.find { it.id == preset.motionId }?.let { _selectedMotion.value = it }
    }

    fun enhancePromptWithGemini() {
        val raw = _promptText.value.trim()
        if (raw.isBlank()) {
            Toast.makeText(
                getApplication(),
                if (_isBengali.value) "অনুগ্রহ করে প্রম্পট লিখুন" else "Please enter a prompt first",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        viewModelScope.launch {
            _generationState.value = GenerationUiState.EnhancingPrompt(raw)
            try {
                val enhanced = repository.enhancePrompt(raw, _selectedStyle.value.id, _isBengali.value)
                _promptText.value = enhanced
                _enhancedPrompt.value = enhanced
                _generationState.value = GenerationUiState.Idle
            } catch (e: Exception) {
                _generationState.value = GenerationUiState.Idle
                Toast.makeText(getApplication(), "Enhance failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun saveUserApiKey(key: String) {
        _userApiKey.value = key.trim()
        repository.saveUserApiKey(key.trim())
    }

    fun testApiConnection(onResult: (Boolean, String) -> Unit) {
        val key = repository.getEffectiveApiKey()
        if (key.isBlank()) {
            onResult(false, if (_isBengali.value) "কোনো এপিআই কি দেওয়া হয়নি" else "No API key configured")
            return
        }

        viewModelScope.launch {
            try {
                val req = GeminiGenerateContentRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(GeminiPart(text = "Hello")),
                            role = "user"
                        )
                    )
                )
                val res = RetrofitClient.geminiService.generateContent(key, req)
                if (res.candidates?.isNotEmpty() == true) {
                    onResult(true, if (_isBengali.value) "এপিআই কি সফলভাবে কানেক্ট হয়েছে!" else "API Key verified and active!")
                } else if (res.error != null) {
                    onResult(false, res.error.message ?: "API error")
                } else {
                    onResult(true, "Connected successfully")
                }
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Connection failed")
            }
        }
    }

    fun generateVideo() {
        val currentPrompt = _promptText.value.trim()
        if (currentPrompt.isBlank()) {
            Toast.makeText(
                getApplication(),
                if (_isBengali.value) "অনুগ্রহ করে ভিডিওর প্রম্পট লিখুন" else "Please enter a video prompt",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (_selectedModel.value.isCloud && !hasApiKey()) {
            Toast.makeText(
                getApplication(),
                if (_isBengali.value) "গুগল ভিও ব্যবহারের জন্য সেটিংসে API Key দিন" else "Please configure your Gemini API Key in Settings to use Veo",
                Toast.LENGTH_LONG
            ).show()
            _currentScreen.value = AppScreen.SETTINGS
            return
        }

        viewModelScope.launch {
            _generationState.value = GenerationUiState.Generating(
                progress = 0.05f,
                stage = "Preparing request...",
                stageBn = "প্রম্পট প্রস্তুত করা হচ্ছে..."
            )

            try {
                // If AI Enhance mode is enabled, send enhanced prompt; otherwise send raw user prompt directly
                val finalPromptToSend = if (_isAiEnhanceMode.value && _enhancedPrompt.value.isNotBlank()) {
                    _enhancedPrompt.value.trim()
                } else {
                    "" // Blank signals repository to strictly use user's prompt verbatim
                }

                val project = repository.createVideoProject(
                    prompt = currentPrompt,
                    enhancedPrompt = finalPromptToSend,
                    styleId = _selectedStyle.value.id,
                    motionId = _selectedMotion.value.id,
                    aspectRatio = _selectedAspectRatio.value.id,
                    durationSeconds = _selectedDuration.value,
                    fps = 24,
                    resolution = _selectedResolution.value,
                    sceneCount = 1,
                    modelId = _selectedModel.value.id,
                    architectureId = if (_selectedModel.value.isCloud) "cloud_first" else "edge_only"
                ) { progress, stage ->
                    val stageBn = when {
                        progress < 0.15f -> "Veo-তে পাঠানো হচ্ছে..."
                        progress < 0.85f -> "ভিডিও তৈরি হচ্ছে ($stage)..."
                        progress < 0.95f -> "ভিডিও ডাউনলোড হচ্ছে..."
                        else -> "সম্পন্ন হচ্ছে..."
                    }
                    _generationState.value = GenerationUiState.Generating(progress, stage, stageBn)
                }

                _activeVideo.value = project
                _generationState.value = GenerationUiState.Success(project.id, project.videoPath)
                _currentScreen.value = AppScreen.PLAYER

            } catch (e: Exception) {
                e.printStackTrace()
                _generationState.value = GenerationUiState.Error(
                    message = e.localizedMessage ?: "Failed to generate video",
                    messageBn = "ভিডিও তৈরি করা যায়নি: ${e.localizedMessage ?: "অজ্ঞাত ত্রুটি"}"
                )
            }
        }
    }

    fun dismissGenerationState() {
        _generationState.value = GenerationUiState.Idle
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavoritesOnly() {
        _filterOnlyFavorites.value = !_filterOnlyFavorites.value
    }

    fun setFilterOnlyFavorites(filter: Boolean) {
        _filterOnlyFavorites.value = filter
    }

    fun setCategoryFilter(category: String) {
        _selectedCategoryFilter.value = category
    }

    fun openVideoPlayer(video: VideoProjectEntity) {
        setActiveVideo(video)
    }

    fun exportToGallery(video: VideoProjectEntity) {
        saveVideoToGallery(video)
    }

    fun setStoryIdea(idea: String) {
        _storyIdea.value = idea
    }

    fun generateStoryBreakdown() {
        val idea = _storyIdea.value.trim()
        if (idea.isBlank()) {
            Toast.makeText(
                getApplication(),
                if (_isBengali.value) "অনুগ্রহ করে সিনেমার ধারণা লিখুন" else "Please enter your story idea",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        viewModelScope.launch {
            _isGeneratingStoryScenes.value = true
            try {
                val scenes = repository.generateStoryScenes(idea)
                _storyScenes.value = scenes
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                _isGeneratingStoryScenes.value = false
            }
        }
    }

    fun generateStoryVideo() {
        val scenes = _storyScenes.value
        if (scenes.isEmpty()) return

        val compositePrompt = scenes.joinToString(" then ") { it.visualPrompt }
        _promptText.value = compositePrompt
        generateVideo()
    }

    fun setActiveVideo(video: VideoProjectEntity) {
        _activeVideo.value = video
        _currentScreen.value = AppScreen.PLAYER
    }

    fun toggleFavorite(video: VideoProjectEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(video.id, !video.isFavorite)
            if (_activeVideo.value?.id == video.id) {
                _activeVideo.value = _activeVideo.value?.copy(isFavorite = !video.isFavorite)
            }
        }
    }

    fun deleteVideo(video: VideoProjectEntity) {
        viewModelScope.launch {
            repository.deleteVideo(video)
            if (_activeVideo.value?.id == video.id) {
                _activeVideo.value = null
                _currentScreen.value = AppScreen.LIBRARY
            }
        }
    }

    fun shareVideo(video: VideoProjectEntity) {
        try {
            val file = File(video.videoPath)
            if (!file.exists()) return
            val uri = FileProvider.getUriForFile(
                getApplication(),
                "${getApplication<Application>().packageName}.provider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(
                Intent.createChooser(intent, "Share AI Video").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveVideoToGallery(video: VideoProjectEntity) {
        viewModelScope.launch {
            try {
                val sourceFile = File(video.videoPath)
                if (!sourceFile.exists()) {
                    Toast.makeText(getApplication(), "Video file not found", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "VeoStudio_${System.currentTimeMillis()}.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VeoStudio")
                    }
                }

                val resolver = getApplication<Application>().contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(sourceFile).use { input ->
                            input.copyTo(out)
                        }
                    }
                    Toast.makeText(
                        getApplication(),
                        if (_isBengali.value) "গ্যালারিতে ভিডিও সেভ হয়েছে!" else "Saved to Movies/VeoStudio!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(getApplication(), "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
