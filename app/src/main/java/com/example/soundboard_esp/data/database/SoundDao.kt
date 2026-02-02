package com.example.soundboard_esp.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Interface DAO pour les opérations sur les sons
 */
@Dao
interface SoundDao {
    
    /** Récupérer tous les sons d'une page spécifique */
    @Query("SELECT * FROM sounds WHERE pageNumber = :page ORDER BY buttonPosition")
    fun getSoundsForPage(page: Int): Flow<List<Sound>>
    
    /** Récupérer tous les sons */
    @Query("SELECT * FROM sounds ORDER BY pageNumber, buttonPosition")
    fun getAllSounds(): Flow<List<Sound>>
    
    /** Récupérer un son par son ID */
    @Query("SELECT * FROM sounds WHERE id = :soundId")
    suspend fun getSoundById(soundId: Int): Sound?
    
    /** Récupérer un son à une position spécifique sur une page */
    @Query("SELECT * FROM sounds WHERE pageNumber = :page AND buttonPosition = :position")
    suspend fun getSoundAtPosition(page: Int, position: Int): Sound?
    
    /** Insérer un nouveau son */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSound(sound: Sound): Long
    
    /** Insérer plusieurs sons */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSounds(sounds: List<Sound>)
    
    /** Mettre à jour un son existant */
    @Update
    suspend fun updateSound(sound: Sound)
    
    /** Supprimer un son */
    @Delete
    suspend fun deleteSound(sound: Sound)
    
    /** Supprimer un son par ID */
    @Query("DELETE FROM sounds WHERE id = :soundId")
    suspend fun deleteSoundById(soundId: Int)
    
    /** Supprimer tous les sons d'une page */
    @Query("DELETE FROM sounds WHERE pageNumber = :page")
    suspend fun deleteSoundsFromPage(page: Int)
    
    /** Supprimer tous les sons */
    @Query("DELETE FROM sounds")
    suspend fun deleteAllSounds()
    
    /** Compter le nombre de pages utilisées */
    @Query("SELECT COUNT(DISTINCT pageNumber) FROM sounds")
    suspend fun getPageCount(): Int
    
    /** Obtenir le nombre maximum de pages */
    @Query("SELECT MAX(pageNumber) FROM sounds")
    suspend fun getMaxPageNumber(): Int?
}
