package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoProjectDao {
    @Query("SELECT * FROM video_projects ORDER BY createdAt DESC")
    fun getAllVideos(): Flow<List<VideoProjectEntity>>

    @Query("SELECT * FROM video_projects WHERE id = :id")
    suspend fun getVideoById(id: Long): VideoProjectEntity?

    @Query("SELECT * FROM video_projects WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavoriteVideos(): Flow<List<VideoProjectEntity>>

    @Query("SELECT * FROM video_projects WHERE prompt LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchVideos(query: String): Flow<List<VideoProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoProjectEntity): Long

    @Update
    suspend fun updateVideo(video: VideoProjectEntity)

    @Delete
    suspend fun deleteVideo(video: VideoProjectEntity)

    @Query("DELETE FROM video_projects WHERE id = :id")
    suspend fun deleteVideoById(id: Long)

    @Query("SELECT COUNT(*) FROM video_projects")
    fun getVideoCount(): Flow<Int>

    @Query("UPDATE video_projects SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)
}
