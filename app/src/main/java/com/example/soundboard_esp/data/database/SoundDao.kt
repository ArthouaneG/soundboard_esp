package com.example.soundboard_esp.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SoundDao {
    
    @Query("SELECT * FROM sounds WHERE pageNumber = :page ORDER BY buttonPosition")
    fun getSoundsForPage(page: Int): Flow<List<Sound>>
    
    @Query("SELECT * FROM sounds ORDER BY pageNumber, buttonPosition")
    fun getAllSounds(): Flow<List<Sound>>
    
    @Query("SELECT * FROM sounds WHERE id = :soundId")
    suspend fun getSoundById(soundId: Int): Sound?
    
    @Query("SELECT * FROM sounds WHERE pageNumber = :page AND buttonPosition = :position")
    suspend fun getSoundAtPosition(page: Int, position: Int): Sound?
    
    @Query("SELECT * FROM sounds WHERE filePath = :filePath LIMIT 1")
    suspend fun getSoundByFilePath(filePath: String): Sound?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSound(sound: Sound): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSounds(sounds: List<Sound>)

    @Update
    suspend fun updateSound(sound: Sound)

    @Delete
    suspend fun deleteSound(sound: Sound)

    @Query("DELETE FROM sounds WHERE id = :soundId")
    suspend fun deleteSoundById(soundId: Int)

    @Query("DELETE FROM sounds WHERE pageNumber = :page")
    suspend fun deleteSoundsFromPage(page: Int)

    @Query("DELETE FROM sounds")
    suspend fun deleteAllSounds()

    @Query("SELECT COUNT(DISTINCT pageNumber) FROM sounds")
    suspend fun getPageCount(): Int

    @Query("SELECT MAX(pageNumber) FROM sounds")
    suspend fun getMaxPageNumber(): Int?
}
