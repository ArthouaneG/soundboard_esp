package com.example.soundboard_esp.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de données Room pour le soundboard
 */
@Database(
    entities = [Sound::class],
    version = 2,
    exportSchema = false
)
abstract class SoundDatabase : RoomDatabase() {
    
    abstract fun soundDao(): SoundDao
    
    companion object {
        @Volatile
        private var INSTANCE: SoundDatabase? = null
        
        fun getDatabase(context: Context): SoundDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SoundDatabase::class.java,
                    "soundboard_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
