package com.example.soundboard_esp.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import com.example.soundboard_esp.data.database.Sound
import com.example.soundboard_esp.data.database.SoundDatabase
import com.example.soundboard_esp.data.repository.SoundRepository
import kotlinx.coroutines.launch

class SoundboardViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: SoundRepository

    init {
        val soundDao = SoundDatabase.getDatabase(application).soundDao()
        repository = SoundRepository(soundDao)
    }

    private val _currentPage = MutableLiveData(0)
    val currentPage: Int get() = _currentPage.value ?: 0
    
    val currentPageLiveData: LiveData<Int> = _currentPage

    val currentPageSounds: LiveData<List<Sound>> = _currentPage.switchMap { page ->
        repository.getSoundsForPage(page).asLiveData()
    }

    fun nextPage() {
        _currentPage.value = (_currentPage.value ?: 0) + 1
    }

    fun previousPage() {
        val current = _currentPage.value ?: 0
        if (current > 0) {
            _currentPage.value = current - 1
        }
    }

    fun insertSound(
        name: String,
        filePath: String,
        buttonPosition: Int,
        pageNumber: Int = currentPage,
        buttonColor: String = "#4ECDC4"
    ) = viewModelScope.launch {
        val sound = Sound(
            name = name,
            filePath = filePath,
            buttonPosition = buttonPosition,
            pageNumber = pageNumber,
            buttonColor = buttonColor
        )
        repository.insertSound(sound)
    }

    fun updateSound(sound: Sound) = viewModelScope.launch {
        repository.updateSound(sound)
    }

    fun deleteSound(sound: Sound) = viewModelScope.launch {
        repository.deleteSound(sound)
    }

    fun deleteSoundById(soundId: Int) = viewModelScope.launch {
        repository.deleteSoundById(soundId)
    }

    suspend fun getSoundAtPosition(page: Int, position: Int): Sound? {
        return repository.getSoundAtPosition(page, position)
    }
    
    suspend fun getSoundByFilePath(filePath: String): Sound? {
        return repository.getSoundByFilePath(filePath)
    }

    suspend fun getPageCount(): Int {
        return repository.getPageCount()
    }

    fun getAllSounds(): LiveData<List<Sound>> {
        return repository.getAllSounds().asLiveData()
    }
}
