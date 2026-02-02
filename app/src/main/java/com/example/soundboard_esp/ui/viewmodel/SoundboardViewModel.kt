package com.example.soundboard_esp.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.soundboard_esp.data.database.Sound
import com.example.soundboard_esp.data.database.SoundDatabase
import com.example.soundboard_esp.data.repository.SoundRepository
import kotlinx.coroutines.launch

/**
 * ViewModel pour gérer l'état et la logique du Soundboard
 */
class SoundboardViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: SoundRepository
    
    init {
        val soundDao = SoundDatabase.getDatabase(application).soundDao()
        repository = SoundRepository(soundDao)
    }
    
    /** Page actuelle affichée */
    private var _currentPage = 0
    val currentPage: Int get() = _currentPage
    
    /** Sons de la page actuelle */
    private var _currentPageSounds: LiveData<List<Sound>>? = null
    val currentPageSounds: LiveData<List<Sound>>
        get() = _currentPageSounds ?: repository.getSoundsForPage(_currentPage).asLiveData()
    
    /** Charger les sons d'une page spécifique */
    fun loadPage(pageNumber: Int) {
        _currentPage = pageNumber
        _currentPageSounds = repository.getSoundsForPage(pageNumber).asLiveData()
    }
    
    /** Aller à la page suivante */
    fun nextPage() {
        _currentPage++
        loadPage(_currentPage)
    }
    
    /** Aller à la page précédente */
    fun previousPage() {
        if (_currentPage > 0) {
            _currentPage--
            loadPage(_currentPage)
        }
    }
    
    /** Ajouter un nouveau son */
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
    
    /** Mettre à jour un son existant */
    fun updateSound(sound: Sound) = viewModelScope.launch {
        repository.updateSound(sound)
    }
    
    /** Supprimer un son */
    fun deleteSound(sound: Sound) = viewModelScope.launch {
        repository.deleteSound(sound)
    }
    
    /** Supprimer un son par ID */
    fun deleteSoundById(soundId: Int) = viewModelScope.launch {
        repository.deleteSoundById(soundId)
    }
    
    /** Récupérer un son à une position spécifique */
    suspend fun getSoundAtPosition(page: Int, position: Int): Sound? {
        return repository.getSoundAtPosition(page, position)
    }
    
    /** Obtenir le nombre de pages */
    suspend fun getPageCount(): Int {
        return repository.getPageCount()
    }
    
    /** Obtenir tous les sons */
    fun getAllSounds(): LiveData<List<Sound>> {
        return repository.getAllSounds().asLiveData()
    }
}
