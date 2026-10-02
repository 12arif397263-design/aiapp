package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_projects")
data class VideoProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val prompt: String,
    val enhancedPrompt: String,
    val style: String,
    val cameraMotion: String,
    val aspectRatio: String, // "16:9", "9:16", "1:1", "4:3"
    val durationSeconds: Int,
    val fps: Int,
    val resolution: String, // "720p", "1080p", "4K"
    val videoPath: String,
    val thumbnailPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val tags: String = "",
    val generationEngine: String = "AI Video Synthesizer",
    val sceneCount: Int = 1
)
