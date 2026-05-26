package com.example.soundboard_esp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.soundboard_esp.ui.SoundboardActivity

/**
 * Point d'entrée de l'application.
 * Cette activité sert uniquement de redirecteur : elle lance immédiatement
 * [SoundboardActivity] puis se termine pour ne pas rester dans la pile.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Rediriger directement vers l'écran principal du soundboard
        val intent = Intent(this, SoundboardActivity::class.java)
        startActivity(intent)
        // Fermer MainActivity pour qu'elle ne reste pas dans la pile de navigation
        finish()
    }
}
