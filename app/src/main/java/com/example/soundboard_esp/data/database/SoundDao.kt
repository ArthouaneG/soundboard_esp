package com.example.soundboard_esp.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object) pour la table "sounds".
 * Toutes les interactions avec la base de données Room passent par cette interface.
 * Les méthodes retournant [Flow] sont observables en temps réel (mise à jour automatique de l'UI).
 * Les méthodes `suspend` doivent être appelées depuis une coroutine.
 */
@Dao
interface SoundDao {

    /** Retourne en temps réel la liste des sons associés à une page donnée, triés par position. */
    @Query("SELECT * FROM sounds WHERE pageNumber = :page ORDER BY buttonPosition")
    fun getSoundsForPage(page: Int): Flow<List<Sound>>

    /** Retourne en temps réel tous les sons de toutes les pages, triés par page puis par position. */
    @Query("SELECT * FROM sounds ORDER BY pageNumber, buttonPosition")
    fun getAllSounds(): Flow<List<Sound>>

    /** Récupère un son par son identifiant unique, ou null s'il n'existe pas. */
    @Query("SELECT * FROM sounds WHERE id = :soundId")
    suspend fun getSoundById(soundId: Int): Sound?

    /** Récupère le son occupant une position précise sur une page donnée, ou null si vide. */
    @Query("SELECT * FROM sounds WHERE pageNumber = :page AND buttonPosition = :position")
    suspend fun getSoundAtPosition(page: Int, position: Int): Sound?

    /** Vérifie si un fichier audio est déjà utilisé en recherchant son chemin (URI). */
    @Query("SELECT * FROM sounds WHERE filePath = :filePath LIMIT 1")
    suspend fun getSoundByFilePath(filePath: String): Sound?

    /** Retourne en temps réel les sons marqués comme favoris, triés par nom. */
    @Query("SELECT * FROM sounds WHERE isFavorite = 1 ORDER BY name")
    fun getFavoriteSounds(): Flow<List<Sound>>

    /**
     * Insère un son. Si un son avec le même ID existe déjà, il est remplacé.
     * Retourne le rowId du son inséré.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSound(sound: Sound): Long

    /** Insère une liste de sons en une seule transaction. Remplace en cas de conflit. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSounds(sounds: List<Sound>)

    /** Met à jour les données d'un son existant (identifié par son ID). */
    @Update
    suspend fun updateSound(sound: Sound)

    /** Supprime un son de la base de données (identifié par son ID). */
    @Delete
    suspend fun deleteSound(sound: Sound)

    /** Supprime un son par son identifiant unique. */
    @Query("DELETE FROM sounds WHERE id = :soundId")
    suspend fun deleteSoundById(soundId: Int)

    /** Supprime tous les sons appartenant à une page donnée. */
    @Query("DELETE FROM sounds WHERE pageNumber = :page")
    suspend fun deleteSoundsFromPage(page: Int)

    /** Vide entièrement la table sounds. */
    @Query("DELETE FROM sounds")
    suspend fun deleteAllSounds()

    /** Retourne le nombre de pages distinctes qui contiennent au moins un son. */
    @Query("SELECT COUNT(DISTINCT pageNumber) FROM sounds")
    suspend fun getPageCount(): Int

    /** Retourne le numéro de page le plus élevé, ou null si la table est vide. */
    @Query("SELECT MAX(pageNumber) FROM sounds")
    suspend fun getMaxPageNumber(): Int?
}
