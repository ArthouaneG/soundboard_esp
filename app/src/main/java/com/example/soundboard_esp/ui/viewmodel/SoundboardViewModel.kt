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

/**
 * ViewModel principal du soundboard.
 *
 * Hérite d'[AndroidViewModel] pour avoir accès au contexte applicatif (nécessaire
 * pour initialiser la base de données) sans fuites mémoire liées au cycle de vie.
 *
 * Responsabilités :
 * - Gérer la page courante et exposer les sons de cette page en LiveData.
 * - Fournir des actions CRUD (insérer, modifier, supprimer) via le [SoundRepository].
 * - Maintenir l'état de navigation entre les pages.
 */
class SoundboardViewModel(application: Application) : AndroidViewModel(application) {

    /** Accès aux données via le repository (seule couche autorisée à toucher le DAO). */
    private val repository: SoundRepository

    init {
        // Initialisation du DAO et du repository à partir de la base Room
        val soundDao = SoundDatabase.getDatabase(application).soundDao()
        repository = SoundRepository(soundDao)
    }

    /** Numéro de page actuellement affiché (0-indexé). Valeur interne modifiable. */
    private val _currentPage = MutableLiveData(0)

    /** Lecture directe du numéro de page courant (sans observer). */
    val currentPage: Int get() = _currentPage.value ?: 0

    /** Version observable du numéro de page, pour mettre à jour l'indicateur visuel. */
    val currentPageLiveData: LiveData<Int> = _currentPage

    /**
     * Liste des sons de la page courante, mise à jour automatiquement via [switchMap].
     * Chaque changement de [_currentPage] déclenche une nouvelle requête sur la base de données.
     */
    val currentPageSounds: LiveData<List<Sound>> = _currentPage.switchMap { page ->
        repository.getSoundsForPage(page).asLiveData()
    }

    /** Avance d'une page. */
    fun nextPage() {
        _currentPage.value = (_currentPage.value ?: 0) + 1
    }

    /** Recule d'une page (minimum page 0). */
    fun previousPage() {
        val current = _currentPage.value ?: 0
        if (current > 0) {
            _currentPage.value = current - 1
        }
    }

    /** Navigue directement vers une page par son numéro. */
    fun goToPage(pageNumber: Int) {
        _currentPage.value = pageNumber
    }

    /**
     * Crée et insère un nouveau son dans la base de données.
     *
     * @param name Nom affiché sur le bouton.
     * @param filePath URI du fichier audio (persisté via ContentResolver).
     * @param buttonPosition Position du bouton sur la grille (1 à 18).
     * @param pageNumber Page sur laquelle insérer le son (par défaut la page courante).
     * @param buttonColor Couleur hex du bouton (ex : "#4ECDC4").
     */
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

    /** Met à jour un son existant (nom, couleur, favori, etc.). */
    fun updateSound(sound: Sound) = viewModelScope.launch {
        repository.updateSound(sound)
    }

    /** Supprime un son de la base de données. */
    fun deleteSound(sound: Sound) = viewModelScope.launch {
        repository.deleteSound(sound)
    }

    /** Supprime un son par son identifiant unique. */
    fun deleteSoundById(soundId: Int) = viewModelScope.launch {
        repository.deleteSoundById(soundId)
    }

    /** Retourne le son présent à une position précise sur une page, ou null. */
    suspend fun getSoundAtPosition(page: Int, position: Int): Sound? {
        return repository.getSoundAtPosition(page, position)
    }

    /** Vérifie si un fichier audio (URI) est déjà assigné, et retourne le son correspondant. */
    suspend fun getSoundByFilePath(filePath: String): Sound? {
        return repository.getSoundByFilePath(filePath)
    }

    /** Retourne le nombre de pages qui contiennent au moins un son. */
    suspend fun getPageCount(): Int {
        return repository.getPageCount()
    }

    /** Retourne tous les sons en LiveData (toutes pages confondues). */
    fun getAllSounds(): LiveData<List<Sound>> {
        return repository.getAllSounds().asLiveData()
    }

    /** Retourne les sons favoris en LiveData. */
    fun getFavoriteSounds(): LiveData<List<Sound>> {
        return repository.getFavoriteSounds().asLiveData()
    }

    /**
     * Inverse l'état favori d'un son (favori → non favori et vice-versa).
     * Crée une copie du son avec le champ [Sound.isFavorite] modifié, puis met à jour la BDD.
     */
    fun toggleFavorite(sound: Sound) = viewModelScope.launch {
        val updatedSound = sound.copy(isFavorite = !sound.isFavorite)
        repository.updateSound(updatedSound)
    }
}
