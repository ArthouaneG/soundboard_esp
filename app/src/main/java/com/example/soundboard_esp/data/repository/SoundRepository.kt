package com.example.soundboard_esp.data.repository

import com.example.soundboard_esp.data.database.Sound
import com.example.soundboard_esp.data.database.SoundDao
import kotlinx.coroutines.flow.Flow

class SoundRepository(private val soundDao: SoundDao) {

    fun getSoundsForPage(page: Int): Flow<List<Sound>> {
        return soundDao.getSoundsForPage(page)
    }

    fun getAllSounds(): Flow<List<Sound>> {
        return soundDao.getAllSounds()
    }

    suspend fun getSoundById(soundId: Int): Sound? {
        return soundDao.getSoundById(soundId)
    }

    suspend fun getSoundAtPosition(page: Int, position: Int): Sound? {
        return soundDao.getSoundAtPosition(page, position)
    }
    
    suspend fun getSoundByFilePath(filePath: String): Sound? {
        return soundDao.getSoundByFilePath(filePath)
    }

    suspend fun insertSound(sound: Sound): Long {
        return soundDao.insertSound(sound)
    }

    suspend fun insertSounds(sounds: List<Sound>) {
        soundDao.insertSounds(sounds)
    }

    suspend fun updateSound(sound: Sound) {
        soundDao.updateSound(sound)
    }

    suspend fun deleteSound(sound: Sound) {
        soundDao.deleteSound(sound)
    }

    suspend fun deleteSoundById(soundId: Int) {
        soundDao.deleteSoundById(soundId)
    }

    suspend fun deleteSoundsFromPage(page: Int) {
        soundDao.deleteSoundsFromPage(page)
    }

    suspend fun deleteAllSounds() {
        soundDao.deleteAllSounds()
    }

    suspend fun getPageCount(): Int {
        return soundDao.getPageCount()
    }

    suspend fun getMaxPageNumber(): Int {
        return soundDao.getMaxPageNumber() ?: 0
    }
}
