package com.example.soundboard_esp.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.soundboard_esp.R
import com.example.soundboard_esp.data.database.Sound
import com.example.soundboard_esp.ui.viewmodel.SoundboardViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SoundboardActivity : AppCompatActivity() {
    
    private lateinit var viewModel: SoundboardViewModel
    private lateinit var soundPool: SoundPool
    private val soundMap = HashMap<Int, Int>()
    private val buttonList = mutableListOf<Button>()
    private var selectedButtonPosition: Int? = null
    private lateinit var pageIndicator: TextView
    
    private val selectAudioLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { handleAudioSelection(it) }
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            selectAudioLauncher.launch(arrayOf("audio/*"))
        } else {
            Toast.makeText(this, "Permission refusée", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_soundboard)
        
        viewModel = ViewModelProvider(this)[SoundboardViewModel::class.java]
        pageIndicator = findViewById(R.id.tv_page_indicator)
        
        initializeSoundPool()
        initializeButtons()
        setupNavigation()
        observeSounds()
        observePageChanges()
        
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
            
            button.setOnClickListener {
                playSound(position)
            }
            
            button.setOnLongClickListener {
                handleLongClick(position, button)
                true
            }
        }
    }
    
    private fun setupNavigation() {
        findViewById<ImageButton>(R.id.btn_previous_page).setOnClickListener {
            viewModel.previousPage()
        }
        
        findViewById<ImageButton>(R.id.btn_next_page).setOnClickListener {
            viewModel.nextPage()
        }
    }
    
    private fun observePageChanges() {
        viewModel.currentPageLiveData.observe(this) { page ->
            pageIndicator.text = "Page ${page + 1}"
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
            resetButtonColor(button, index + 1)
        }
        
        soundMap.clear()
        
        // Charger les sons existants
        sounds.forEach { sound ->
            val buttonIndex = sound.buttonPosition - 1
            if (buttonIndex in buttonList.indices) {
                val button = buttonList[buttonIndex]
                button.text = sound.name
                button.contentDescription = sound.name
                
                try {
                    button.setBackgroundColor(Color.parseColor(sound.buttonColor))
                } catch (e: Exception) {
                    // Garder la couleur par défaut
                }
                
                loadSoundIntoPool(sound)
            }
        }
    }
    
    private fun resetButtonColor(button: Button, position: Int) {
        val defaultColors = listOf(
            "#4ECDC4", "#95E1D3", "#F38181",
            "#AA96DA", "#FCBAD3", "#FFFFD2"
        )
        val defaultColor = defaultColors[(position - 1) % defaultColors.size]
        button.setBackgroundColor(Color.parseColor(defaultColor))
    }
    
    private fun loadSoundIntoPool(sound: Sound) {
        try {
            val uri = Uri.parse(sound.filePath)
            val afd = contentResolver.openAssetFileDescriptor(uri, "r")
            
            if (afd != null) {
                val soundId = soundPool.load(afd.fileDescriptor, afd.startOffset, afd.length, 1)
                soundMap[sound.buttonPosition] = soundId
                afd.close()
            } else {
                Toast.makeText(this, "Impossible de charger: ${sound.name}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Erreur: ${sound.name} - ${e.message}", Toast.LENGTH_SHORT).show()
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
            val existingSound = withContext(Dispatchers.IO) {
                viewModel.getSoundAtPosition(viewModel.currentPage, position)
            }
            
            if (existingSound != null) {
                showDeleteConfirmation(existingSound)
            } else {
                selectedButtonPosition = position
                requestAudioFile()
            }
        }
    }
    
    private fun showDeleteConfirmation(sound: Sound) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Supprimer")
            .setMessage("Supprimer \"${sound.name}\" ?")
            .setPositiveButton("Supprimer") { _, _ ->
                // Révoquer la permission persistante
                try {
                    val uri = Uri.parse(sound.filePath)
                    contentResolver.releasePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Ignorer si la permission n'existe pas
                }
                
                // Supprimer le son de la base de données
                viewModel.deleteSound(sound)
                
                Toast.makeText(this, "Supprimé: ${sound.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
    
    private fun showAddSoundDialog() {
        val positions = (1..18).filter { position ->
            buttonList[position - 1].text.isEmpty()
        }
        
        if (positions.isEmpty()) {
            Toast.makeText(this, "Toutes les positions occupées", Toast.LENGTH_SHORT).show()
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
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            selectAudioLauncher.launch(arrayOf("audio/*"))
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }
    
    private fun handleAudioSelection(uri: Uri) {
        val position = selectedButtonPosition ?: return
        val uriString = uri.toString()
        
        // Vérifier si ce fichier existe déjà dans la base de données
        CoroutineScope(Dispatchers.Main).launch {
            val existingSound = withContext(Dispatchers.IO) {
                viewModel.getSoundByFilePath(uriString)
            }
            
            if (existingSound != null) {
                // Fichier déjà utilisé
                androidx.appcompat.app.AlertDialog.Builder(this@SoundboardActivity)
                    .setTitle("Fichier déjà utilisé")
                    .setMessage("Ce fichier sonore est déjà utilisé pour \"${existingSound.name}\" (Page ${existingSound.pageNumber + 1}, Position ${existingSound.buttonPosition}).\n\nVoulez-vous quand même l'ajouter ?")
                    .setPositiveButton("Oui") { _, _ ->
                        proceedWithAudioSelection(uri, position)
                    }
                    .setNegativeButton("Non") { _, _ ->
                        selectedButtonPosition = null
                    }
                    .show()
            } else {
                // Fichier nouveau, continuer normalement
                proceedWithAudioSelection(uri, position)
            }
        }
    }
    
    private fun proceedWithAudioSelection(uri: Uri, position: Int) {
        // Prendre une permission persistante sur l'URI
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            Toast.makeText(this, "Impossible d'obtenir l'accès permanent: ${e.message}", Toast.LENGTH_LONG).show()
            selectedButtonPosition = null
            return
        }
        
        val input = android.widget.EditText(this)
        input.hint = "Nom du son"
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Nom du son")
            .setView(input)
            .setPositiveButton("Ajouter") { _, _ ->
                val name = input.text.toString().ifEmpty { "Son $position" }
                saveSound(name, uri.toString(), position)
            }
            .setNegativeButton("Annuler") { _, _ ->
                selectedButtonPosition = null
            }
            .show()
    }
    
    private fun saveSound(name: String, filePath: String, position: Int) {
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
        
        Toast.makeText(this, "Son ajouté: $name", Toast.LENGTH_SHORT).show()
        selectedButtonPosition = null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        soundPool.release()
    }
}
