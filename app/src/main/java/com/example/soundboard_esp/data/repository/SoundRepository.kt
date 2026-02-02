package com.example.soundboard_esp.data.repository

import com.example.soundboard_esp.data.database.Sound
import com.example.soundboard_esp.data.database.SoundDao
import kotlinx.coroutines.flow.Flow

/**
 * Repository pour gérer les opérations sur les sons
 */
class SoundRepository(private val soundDao: SoundDao) {
    
    /** Récupérer tous les sons d'une page */
    fun getSoundsForPage(page: Int): Flow<List<Sound>> {
        return soundDao.getSoundsForPage(page)
    }
    
    /** Récupérer tous les sons */
    fun getAllSounds(): Flow<List<Sound>> {
        return soundDao.getAllSounds()
    }
    
    /** Récupérer un son par ID */
    suspend fun getSoundById(soundId: Int): Sound? {
        return soundDao.getSoundById(soundId)
    }
    
    /** Récupérer un son à une position spécifique */
    suspend fun getSoundAtPosition(page: Int, position: Int): Sound? {
        return soundDao.getSoundAtPosition(page, position)
    }
    
    /** Ajouter un nouveau son */
    suspend fun insertSound(sound: Sound): Long {
        return soundDao.insertSound(sound)
    }
    
    /** Ajouter plusieurs sons */
    suspend fun insertSounds(sounds: List<Sound>) {
        soundDao.insertSounds(sounds)
    }
    
    /** Mettre à jour un son */
    suspend fun updateSound(sound: Sound) {
        soundDao.updateSound(sound)
    }
    
    /** Supprimer un son */
    suspend fun deleteSound(sound: Sound) {
        soundDao.deleteSound(sound)
    }
    
    /** Supprimer un son par ID */
    suspend fun deleteSoundById(soundId: Int) {
        soundDao.deleteSoundById(soundId)
    }
    
    /** Supprimer tous les sons d'une page */
    suspend fun deleteSoundsFromPage(page: Int) {
        soundDao.deleteSoundsFromPage(page)
    }
    
    /** Supprimer tous les sons */
    suspend fun deleteAllSounds() {
        soundDao.deleteAllSounds()
    }
    
    /** Obtenir le nombre de pages */
    suspend fun getPageCount(): Int {
        return soundDao.getPageCount()
    }
    
    /** Obtenir le numéro de page maximum */
    suspend fun getMaxPageNumber(): Int {
        return soundDao.getMaxPageNumber() ?: 0
    }
}
