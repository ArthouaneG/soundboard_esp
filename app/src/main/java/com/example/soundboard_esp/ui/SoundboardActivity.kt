package com.example.soundboard_esp.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.SoundPool
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.soundboard_esp.R
import com.example.soundboard_esp.data.database.Sound
import com.example.soundboard_esp.ui.viewmodel.SoundboardViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Activity principale du Soundboard
 */
class SoundboardActivity : AppCompatActivity() {
    
    private lateinit var viewModel: SoundboardViewModel
    private lateinit var soundPool: SoundPool
    private val soundMap = HashMap<Int, Int>() // buttonPosition -> soundId
    private val buttonList = mutableListOf<Button>()
    
    // Position sélectionnée pour ajouter un son
    private var selectedButtonPosition: Int? = null
    
    // Launcher pour sélectionner un fichier audio
    private val selectAudioLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleAudioSelection(it) }
    }
    
    // Demande de permission
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            selectAudioLauncher.launch("audio/*")
        } else {
            Toast.makeText(this, "Permission refusée", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_soundboard)
        
        // Initialiser ViewModel
        viewModel = ViewModelProvider(this)[SoundboardViewModel::class.java]
        
        // Initialiser SoundPool
        initializeSoundPool()
        
        // Initialiser les boutons
        initializeButtons()
        
        // Configurer la navigation
        setupNavigation()
        
        // Observer les changements de sons
        observeSounds()
        
        // Bouton d'ajout
        findViewById<Button>(R.id.btn_add_sound).setOnClickListener {
            showAddSoundDialog()
        }
    }
    
    private fun initializeSoundPool() {
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .build()
    }
    
    private fun initializeButtons() {
        // Récupérer tous les boutons de sons
        val buttonIds = listOf(
            R.id.btn_sound_1, R.id.btn_sound_2, R.id.btn_sound_3,
            R.id.btn_sound_4, R.id.btn_sound_5, R.id.btn_sound_6,
            R.id.btn_sound_7, R.id.btn_sound_8, R.id.btn_sound_9,
            R.id.btn_sound_10, R.id.btn_sound_11, R.id.btn_sound_12,
            R.id.btn_sound_13, R.id.btn_sound_14, R.id.btn_sound_15,
            R.id.btn_sound_16, R.id.btn_sound_17, R.id.btn_sound_18
        )
        
        buttonIds.forEachIndexed { index, buttonId ->
            val button = findViewById<Button>(buttonId)
            buttonList.add(button)
            
            val position = index + 1
            
            // Clic normal : jouer le son
            button.setOnClickListener {
                playSound(position)
            }
            
            // Clic long : supprimer ou ajouter un son
            button.setOnLongClickListener {
                handleLongClick(position, button)
                true
            }
        }
    }
    
    private fun setupNavigation() {
        findViewById<ImageButton>(R.id.btn_previous_page).setOnClickListener {
            viewModel.previousPage()
            Toast.makeText(this, "Page ${viewModel.currentPage + 1}", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<ImageButton>(R.id.btn_next_page).setOnClickListener {
            viewModel.nextPage()
            Toast.makeText(this, "Page ${viewModel.currentPage + 1}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun observeSounds() {
        viewModel.currentPageSounds.observe(this) { sounds ->
            updateButtonsWithSounds(sounds)
        }
    }
    
    private fun updateButtonsWithSounds(sounds: List<Sound>) {
        // Réinitialiser tous les boutons
        buttonList.forEachIndexed { index, button ->
            button.text = ""
            button.contentDescription = getString(R.string.sound_button)
        }
        
        // Vider le soundMap
        soundMap.clear()
        
        // Charger les sons
        sounds.forEach { sound ->
            val buttonIndex = sound.buttonPosition - 1
            if (buttonIndex in buttonList.indices) {
                val button = buttonList[buttonIndex]
                button.text = sound.name
                button.contentDescription = sound.name
                
                // Changer la couleur du bouton
                try {
                    button.setBackgroundColor(Color.parseColor(sound.buttonColor))
                } catch (e: Exception) {
                    // Garder la couleur par défaut si erreur
                }
                
                // Charger le son dans SoundPool
                loadSoundIntoPool(sound)
            }
        }
    }
    
    private fun loadSoundIntoPool(sound: Sound) {
        try {
            val soundId = soundPool.load(sound.filePath, 1)
            soundMap[sound.buttonPosition] = soundId
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Erreur de chargement: ${sound.name}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun playSound(position: Int) {
        soundMap[position]?.let { soundId ->
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        } ?: run {
            Toast.makeText(this, "Aucun son à cette position", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun handleLongClick(position: Int, button: Button) {
        CoroutineScope(Dispatchers.Main).launch {
            val existingSound = viewModel.getSoundAtPosition(viewModel.currentPage, position)
            
            if (existingSound != null) {
                // Son existe : proposer de supprimer
                showDeleteConfirmation(existingSound, button)
            } else {
                // Pas de son : proposer d'ajouter
                selectedButtonPosition = position
                requestAudioFile()
            }
        }
    }
    
    private fun showDeleteConfirmation(sound: Sound, button: Button) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Supprimer le son")
            .setMessage("Voulez-vous supprimer \"${sound.name}\" ?")
            .setPositiveButton("Supprimer") { _, _ ->
                viewModel.deleteSound(sound)
                button.text = ""
                Toast.makeText(this, "Son supprimé", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
    
    private fun showAddSoundDialog() {
        val positions = (1..18).filter { position ->
            buttonList[position - 1].text.isEmpty()
        }
        
        if (positions.isEmpty()) {
            Toast.makeText(this, "Toutes les positions sont occupées", Toast.LENGTH_SHORT).show()
            return
        }
        
        val positionStrings = positions.map { "Position $it" }.toTypedArray()
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Choisir une position")
            .setItems(positionStrings) { _, which ->
                selectedButtonPosition = positions[which]
                requestAudioFile()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
    
    private fun requestAudioFile() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            selectAudioLauncher.launch("audio/*")
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    
    private fun handleAudioSelection(uri: Uri) {
        val position = selectedButtonPosition ?: return
        
        // Demander le nom du son
        val input = android.widget.EditText(this)
        input.hint = "Nom du son"
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Nom du son")
            .setView(input)
            .setPositiveButton("Ajouter") { _, _ ->
                val name = input.text.toString().ifEmpty { "Son $position" }
                saveSound(name, uri.toString(), position)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
    
    private fun saveSound(name: String, filePath: String, position: Int) {
        // Couleurs alternées
        val colors = listOf(
            "#4ECDC4", "#95E1D3", "#F38181",
            "#AA96DA", "#FCBAD3", "#FFFFD2"
        )
        val color = colors[(position - 1) % colors.size]
        
        viewModel.insertSound(
            name = name,
            filePath = filePath,
            buttonPosition = position,
            pageNumber = viewModel.currentPage,
            buttonColor = color
        )
        
        Toast.makeText(this, "Son ajouté : $name", Toast.LENGTH_SHORT).show()
        selectedButtonPosition = null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        soundPool.release()
    }
}
