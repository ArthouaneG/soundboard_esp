package com.example.soundboard_esp.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de données Room pour le soundboard.
 *
 * Utilise le pattern Singleton pour garantir une seule instance en mémoire.
 * La version est 2 ; [fallbackToDestructiveMigration] permet de recréer la base
 * automatiquement en cas de changement de schéma sans migration explicite.
 */
@Database(
    entities = [Sound::class],
    version = 2,
    exportSchema = false
)
abstract class SoundDatabase : RoomDatabase() {

    /** Fournit l'accès aux requêtes sur la table "sounds". */
    abstract fun soundDao(): SoundDao

    companion object {
        /**
         * Instance unique de la base de données.
         * L'annotation @Volatile garantit la visibilité immédiate entre threads.
         */
        @Volatile
        private var INSTANCE: SoundDatabase? = null

        /**
         * Retourne l'instance unique de [SoundDatabase], en la créant si nécessaire.
         * Le bloc [synchronized] évite les créations concurrentes (double-checked locking).
         *
         * @param context Contexte Android utilisé pour construire la base.
         */
        fun getDatabase(context: Context): SoundDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SoundDatabase::class.java,
                    "soundboard_database"   // Nom du fichier SQLite sur le disque
                )
                    .fallbackToDestructiveMigration() // Recrée la BDD si la version change
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
