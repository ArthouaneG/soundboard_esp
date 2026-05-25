package com.example.soundboard_esp.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entité représentant un son dans la base de données
 */
@Entity(tableName = "sounds")
data class Sound(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    
    /** Nom du son affiché sur le bouton */
    val name: String,
    
    /** Chemin du fichier audio */
    val filePath: String,
    
    /** Position du bouton (1-18) */
    val buttonPosition: Int,
    
    /** Numéro de page pour la navigation */
    val pageNumber: Int = 0,
    
    /** Couleur du bouton en format hex (ex: "#4ECDC4") */
    val buttonColor: String = "#4ECDC4",
    
    /** Indique si le son est marqué comme favori */
    val isFavorite: Boolean = false
)
