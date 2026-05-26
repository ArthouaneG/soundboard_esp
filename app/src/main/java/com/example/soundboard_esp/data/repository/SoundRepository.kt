package com.example.soundboard_esp.data.repository

import com.example.soundboard_esp.data.database.Sound
import com.example.soundboard_esp.data.database.SoundDao
import kotlinx.coroutines.flow.Flow

/**
 * Dépôt (Repository) servant d'intermédiaire entre le ViewModel et le DAO.
 * Centralise toute la logique d'accès aux données et isole le reste de l'app
 * des détails d'implémentation de Room.
 *
 * @param soundDao Le DAO injecté pour accéder à la base de données.
 */
class SoundRepository(private val soundDao: SoundDao) {

    /** Retourne en temps réel les sons d'une page spécifique. */
    fun getSoundsForPage(page: Int): Flow<List<Sound>> {
        return soundDao.getSoundsForPage(page)
    }

    /** Retourne en temps réel tous les sons, toutes pages confondues. */
    fun getAllSounds(): Flow<List<Sound>> {
        return soundDao.getAllSounds()
    }

    /** Récupère un son par son ID unique, ou null. */
    suspend fun getSoundById(soundId: Int): Sound? {
        return soundDao.getSoundById(soundId)
    }

    /** Récupère le son à une position précise sur une page donnée, ou null. */
    suspend fun getSoundAtPosition(page: Int, position: Int): Sound? {
        return soundDao.getSoundAtPosition(page, position)
    }

    /** Vérifie si un fichier (URI) est déjà assigné à un son existant. */
    suspend fun getSoundByFilePath(filePath: String): Sound? {
        return soundDao.getSoundByFilePath(filePath)
    }

    /** Retourne en temps réel la liste des sons favoris. */
    fun getFavoriteSounds(): Flow<List<Sound>> {
        return soundDao.getFavoriteSounds()
    }

    /** Insère un son et retourne son rowId généré. */
    suspend fun insertSound(sound: Sound): Long {
        return soundDao.insertSound(sound)
    }

    /** Insère une liste de sons en une seule opération. */
    suspend fun insertSounds(sounds: List<Sound>) {
        soundDao.insertSounds(sounds)
    }

    /** Met à jour un son existant dans la base de données. */
    suspend fun updateSound(sound: Sound) {
        soundDao.updateSound(sound)
    }

    /** Supprime un son de la base de données. */
    suspend fun deleteSound(sound: Sound) {
        soundDao.deleteSound(sound)
    }

    /** Supprime un son par son identifiant unique. */
    suspend fun deleteSoundById(soundId: Int) {
        soundDao.deleteSoundById(soundId)
    }

    /** Supprime tous les sons appartenant à une page entière. */
    suspend fun deleteSoundsFromPage(page: Int) {
        soundDao.deleteSoundsFromPage(page)
    }

    /** Supprime tous les sons de la base de données. */
    suspend fun deleteAllSounds() {
        soundDao.deleteAllSounds()
    }

    /** Retourne le nombre de pages qui contiennent au moins un son. */
    suspend fun getPageCount(): Int {
        return soundDao.getPageCount()
    }

    /** Retourne le numéro de la page la plus haute (0 si aucune donnée). */
    suspend fun getMaxPageNumber(): Int {
        return soundDao.getMaxPageNumber() ?: 0
    }
}
