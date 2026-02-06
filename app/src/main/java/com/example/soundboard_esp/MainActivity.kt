package com.example.soundboard_esp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.soundboard_esp.ui.SoundboardActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Lancer directement SoundboardActivity
        val intent = Intent(this, SoundboardActivity::class.java)
        startActivity(intent)
        finish()
    }
}
