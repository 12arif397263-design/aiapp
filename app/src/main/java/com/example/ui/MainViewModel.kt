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

    // Prompt & Configuration
    private val _promptText = MutableStateFlow("")
    val promptText: StateFlow<String> = _promptText.asStateFlow()

    private val _enhancedPrompt = MutableStateFlow("")
    val enhancedPrompt: StateFlow<String> = _enhancedPrompt.asStateFlow()

    private val _selectedStyle = MutableStateFlow(VideoPresets.STYLES.first())
    val selectedStyle: StateFlow<com.example.data.model.VideoStyle> = _selectedStyle.asStateFlow()

    private val _selectedMotion = MutableStateFlow(VideoPresets.MOTIONS.first())
    val selectedMotion: StateFlow<com.example.data.model.CameraMotion> = _selectedMotion.asStateFlow()

    private val _selectedAspectRatio = MutableStateFlow(VideoPresets.ASPECT_RATIOS.first())
    val selectedAspectRatio: StateFlow<com.example.data.model.AspectRatioOption> = _selectedAspectRatio.asStateFlow()

    private val _selectedModel = MutableStateFlow(
        VideoPresets.MODELS.find { it.id == repository.getSavedModelId() } ?: VideoPresets.MODELS[0]
    )
    val selectedModel: StateFlow<com.example.data.model.AiVideoModelOption> = _selectedModel.asStateFlow()

    private val _selectedArchitecture = MutableStateFlow(
        VideoPresets.ARCHITECTURES.find { it.id == repository.getSavedArchitectureId() } ?: VideoPresets.ARCHITECTURES[0]
    )
    val selectedArchitecture: StateFlow<com.example.data.model.SystemArchitectureOption> = _selectedArchitecture.asStateFlow()

    private val _userApiKey = MutableStateFlow(repository.getUserApiKey())
    val userApiKey: StateFlow<String> = _userApiKey.asStateFlow()

    fun selectModel(model: com.example.data.model.AiVideoModelOption) {
        _selectedModel.value = model
        repository.saveModelId(model.id)
    }

    fun selectArchitecture(arch: com.example.data.model.SystemArchitectureOption) {
        _selectedArchitecture.value = arch
        repository.saveArchitectureId(arch.id)
    }

    fun saveUserApiKey(key: String) {
        _userApiKey.value = key.trim()
        repository.saveUserApiKey(key.trim())
    }

    private val _selectedDuration = MutableStateFlow(4) // 4 seconds
    val selectedDuration: StateFlow<Int> = _selectedDuration.asStateFlow()

    private val _selectedFps = MutableStateFlow(30)
    val selectedFps: StateFlow<Int> = _selectedFps.asStateFlow()

    private val _motionStrength = MutableStateFlow(7)
    val motionStrength: StateFlow<Int> = _motionStrength.asStateFlow()

    // Generation state
    private val _generationState = MutableStateFlow<GenerationUiState>(GenerationUiState.Idle)
    val generationState: StateFlow<GenerationUiState> = _generationState.asStateFlow()

    // Currently playing/inspected video
    private val _activeVideo = MutableStateFlow<VideoProjectEntity?>(null)
    val activeVideo: StateFlow<VideoProjectEntity?> = _activeVideo.asStateFlow()

    // Story Studio state
    private val _storyIdea = MutableStateFlow("A lone explorer robot discovers a glowing crystal in a futuristic alien desert")
    val storyIdea: StateFlow<String> = _storyIdea.asStateFlow()

    private val _storyScenes = MutableStateFlow<List<StoryScene>>(emptyList())
    val storyScenes: StateFlow<List<StoryScene>> = _storyScenes.asStateFlow()

    private val _isGeneratingStoryScenes = MutableStateFlow(false)
    val isGeneratingStoryScenes: StateFlow<Boolean> = _isGeneratingStoryScenes.asStateFlow()

    // Library Search & Filter
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterOnlyFavorites = MutableStateFlow(false)
    val filterOnlyFavorites: StateFlow<Boolean> = _filterOnlyFavorites.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow("All")
    val selectedCategoryFilter: StateFlow<String> = _selectedCategoryFilter.asStateFlow()

    // Video List
    val videoList: StateFlow<List<VideoProjectEntity>> = combine(
        repository.getAllVideos(),
        _searchQuery,
        _filterOnlyFavorites,
        _selectedCategoryFilter
    ) { all, query, onlyFavs, cat ->
        all.filter { video ->
            val matchesQuery = query.isBlank() ||
                    video.prompt.contains(query, ignoreCase = true) ||
                    video.title.contains(query, ignoreCase = true)
            val matchesFav = !onlyFavs || video.isFavorite
            val matchesCat = cat == "All" || video.style.equals(cat, ignoreCase = true)
            matchesQuery && matchesFav && matchesCat
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalVideoCount: StateFlow<Int> = repository.getVideoCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun toggleLanguage() {
        _isBengali.value = !_isBengali.value
    }

    fun setPrompt(text: String) {
        _promptText.value = text
        _enhancedPrompt.value = "" // Invalidate cached prompt immediately
    }

    fun setStyle(style: com.example.data.model.VideoStyle) {
        _selectedStyle.value = style
    }

    fun setMotion(motion: com.example.data.model.CameraMotion) {
        _selectedMotion.value = motion
    }

    fun setAspectRatio(ratio: com.example.data.model.AspectRatioOption) {
        _selectedAspectRatio.value = ratio
    }

    fun setDuration(duration: Int) {
        _selectedDuration.value = duration
    }

    fun setMotionStrength(strength: Int) {
        _motionStrength.value = strength
    }

    fun applyPreset(preset: PresetPrompt) {
        _promptText.value = preset.prompt
        _enhancedPrompt.value = "" // Invalidate cached prompt
        VideoPresets.STYLES.find { it.id == preset.styleId }?.let { _selectedStyle.value = it }
        VideoPresets.MOTIONS.find { it.id == preset.motionId }?.let { _selectedMotion.value = it }
    }

    fun enhancePromptWithGemini() {
        val prompt = _promptText.value.trim()
        if (prompt.isBlank()) {
            Toast.makeText(
                getApplication(),
                if (_isBengali.value) "অনুগ্রহ করে ভিডিওর প্রম্পট লিখুন" else "Please enter a video prompt",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        viewModelScope.launch {
            _generationState.value = GenerationUiState.EnhancingPrompt(prompt)
            val enhanced = repository.enhancePrompt(prompt, _selectedStyle.value.id, _isBengali.value)
            _enhancedPrompt.value = enhanced
            _promptText.value = enhanced
            _generationState.value = GenerationUiState.Idle
            Toast.makeText(
                getApplication(),
                if (_isBengali.value) "প্রম্পট সিনেমাটিক স্টাইলে বর্ধিত করা হয়েছে! ✨" else "Prompt enhanced with AI magic! ✨",
                Toast.LENGTH_SHORT
            ).show()
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
            _generationState.value = GenerationUiState.Error(
                message = "Please enter a video prompt.",
                messageBn = "অনুগ্রহ করে ভিডিওর প্রম্পট লিখুন।"
            )
            return
        }

        viewModelScope.launch {
            _generationState.value = GenerationUiState.Generating(
                progress = 0.05f,
                stage = "Analyzing prompt & directing scene...",
                stageBn = "প্রম্পট বিশ্লেষণ ও সিনারিও পরিচালনা হচ্ছে..."
            )

            try {
                val enhanced = _enhancedPrompt.value.trim()

                val project = repository.createVideoProject(
                    prompt = currentPrompt,
                    enhancedPrompt = enhanced,
                    styleId = _selectedStyle.value.id,
                    motionId = _selectedMotion.value.id,
                    aspectRatio = _selectedAspectRatio.value.id,
                    durationSeconds = _selectedDuration.value,
                    fps = _selectedFps.value,
                    resolution = "1080p",
                    sceneCount = 1,
                    modelId = _selectedModel.value.id,
                    architectureId = _selectedArchitecture.value.id
                ) { progress, stage ->
                    val stageBn = when {
                        progress < 0.2f -> "সিনারিও ও ক্যামেরা ট্রাজেক্টরি কম্পোজ হচ্ছে..."
                        progress < 0.7f -> "ভিজ্যুয়াল ফ্রেম সিন্থেসিস চলছে ($stage)..."
                        else -> "এইচ.২৬৪ এমপি৪ এনকোডিং ও সাউন্ড মিক্সিং..."
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
                    messageBn = "ভিডিও জেনারেট করতে সমস্যা হয়েছে: ${e.localizedMessage ?: "অজ্ঞাত ত্রুটি"}"
                )
            }
        }
    }

    fun setStoryIdea(idea: String) {
        _storyIdea.value = idea
    }

    fun generateStoryBreakdown() {
        val idea = _storyIdea.value.trim()
        if (idea.isBlank()) return

        viewModelScope.launch {
            _isGeneratingStoryScenes.value = true
            val scenes = repository.generateStoryScenes(idea)
            _storyScenes.value = scenes
            _isGeneratingStoryScenes.value = false
        }
    }

    fun generateStoryVideo() {
        val scenes = _storyScenes.value
        if (scenes.isEmpty()) {
            generateStoryBreakdown()
            return
        }

        viewModelScope.launch {
            val combinedPrompt = scenes.joinToString(". ") { it.visualPrompt }
            _promptText.value = combinedPrompt
            generateVideo()
        }
    }

    fun openVideoPlayer(video: VideoProjectEntity) {
        _activeVideo.value = video
        _currentScreen.value = AppScreen.PLAYER
    }

    fun dismissGenerationState() {
        _generationState.value = GenerationUiState.Idle
    }

    fun toggleFavorite(video: VideoProjectEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(video.id, !video.isFavorite)
        }
    }

    fun deleteVideo(video: VideoProjectEntity) {
        viewModelScope.launch {
            repository.deleteVideo(video)
            if (_activeVideo.value?.id == video.id) {
                _activeVideo.value = null
                _currentScreen.value = AppScreen.LIBRARY
            }
            Toast.makeText(
                getApplication(),
                if (_isBengali.value) "ভিডিও মুছে ফেলা হয়েছে" else "Video deleted",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterOnlyFavorites(onlyFavs: Boolean) {
        _filterOnlyFavorites.value = onlyFavs
    }

    fun setCategoryFilter(category: String) {
        _selectedCategoryFilter.value = category
    }

    fun shareVideo(video: VideoProjectEntity) {
        try {
            val file = File(video.videoPath)
            if (!file.exists()) {
                Toast.makeText(getApplication(), "Video file not found", Toast.LENGTH_SHORT).show()
                return
            }

            val uri: Uri = try {
                FileProvider.getUriForFile(
                    getApplication(),
                    "${getApplication<Application>().packageName}.fileprovider",
                    file
                )
            } catch (e: Exception) {
                Uri.fromFile(file)
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, video.title)
                putExtra(Intent.EXTRA_TEXT, "🎬 Generated with OmniVideo AI: ${video.prompt}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Video via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            getApplication<Application>().startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(getApplication(), "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun exportToGallery(video: VideoProjectEntity) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val sourceFile = File(video.videoPath)
            if (!sourceFile.exists()) {
                Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
                return@launch
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Video.Media.DISPLAY_NAME, "OmniVideo_${System.currentTimeMillis()}.mp4")
                        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                        put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/OmniVideoAI")
                    }
                    val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            FileInputStream(sourceFile).use { input ->
                                input.copyTo(out)
                            }
                        }
                    }
                }
                Toast.makeText(
                    context,
                    if (_isBengali.value) "ভিডিও সফলভাবে গ্যালারিতে সেভ করা হয়েছে! 🎬" else "Saved to Gallery / Movies! 🎬",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
