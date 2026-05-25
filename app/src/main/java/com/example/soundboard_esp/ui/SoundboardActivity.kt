package com.example.soundboard_esp.ui

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class SoundboardActivity : AppCompatActivity() {
    
    private lateinit var viewModel: SoundboardViewModel
    private lateinit var soundPool: SoundPool
    private val soundMap = HashMap<Int, Int>()
    private val soundDurations = HashMap<Int, Long>()
    private val soundNames = HashMap<Int, String>() // Sauvegarder les noms originaux
    private val buttonList = mutableListOf<Button>()
    private var selectedButtonPosition: Int? = null
    private lateinit var pageIndicator: TextView
    private var currentPlayingPosition: Int? = null
    private val soundColors = HashMap<Int, String>()
    private val handler = Handler(Looper.getMainLooper())
    
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
        
        findViewById<Button>(R.id.btn_favorites).setOnClickListener {
            showFavoritesDialog()
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
            
            var initialX = 0f
            var initialY = 0f
            var hasMoved = false
            var menuShown = false
            var dragStarted = false
            val menuRunnable = Runnable {
                if (!hasMoved && !dragStarted && button.text.isNotEmpty()) {
                    menuShown = true
                    handleLongClick(position, button)
                }
            }
            
            button.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = event.x
                        initialY = event.y
                        hasMoved = false
                        menuShown = false
                        dragStarted = false
                        
                        // Programmer l'affichage du menu après 1,5 seconde
                        if (button.text.isNotEmpty()) {
                            handler.postDelayed(menuRunnable, 1500)
                        }
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = abs(event.x - initialX)
                        val deltaY = abs(event.y - initialY)
                        
                        if (deltaX > 10 || deltaY > 10) {
                            hasMoved = true
                            
                            if (!menuShown && !dragStarted && button.text.isNotEmpty()) {
                                handler.removeCallbacks(menuRunnable)
                                CoroutineScope(Dispatchers.Main).launch {
                                    val sound = withContext(Dispatchers.IO) {
                                        viewModel.getSoundAtPosition(viewModel.currentPage, position)
                                    }
                                    if (sound != null) {
                                        startDrag(button, sound)
                                        dragStarted = true
                                    }
                                }
                            }
                        }
                        false
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(menuRunnable)
                        
                        if (!hasMoved && !menuShown && !dragStarted) {
                            playSound(position)
                        }
                        false
                    }
                    else -> false
                }
            }
            
            button.setOnLongClickListener {
                if (button.text.isEmpty()) {
                    selectedButtonPosition = position
                    requestAudioFile()
                }
                true
            }
            
            button.setOnDragListener { v, dragEvent ->
                handleDragEvent(v as Button, dragEvent, position)
            }
        }
    }
    
    private fun handleLongClick(position: Int, button: Button) {
        CoroutineScope(Dispatchers.Main).launch {
            val existingSound = withContext(Dispatchers.IO) {
                viewModel.getSoundAtPosition(viewModel.currentPage, position)
            }
            
            if (existingSound != null) {
                showSoundOptionsDialog(existingSound)
            }
        }
    }
    
    private fun showSoundOptionsDialog(sound: Sound) {
        val favoriteText = if (sound.isFavorite) "Retirer des favoris" else "Ajouter aux favoris"
        val options = arrayOf(favoriteText, "Renommer", "Supprimer")
        
        val adapter = android.widget.ArrayAdapter(
            this,
            R.layout.simple_list_item_1,
            options
        )
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(sound.name)
            .setAdapter(adapter) { _, which ->
                when (which) {
                    0 -> {
                        viewModel.toggleFavorite(sound)
                        val message = if (sound.isFavorite) "Retiré des favoris" else "Ajouté aux favoris"
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                    1 -> showRenameDialog(sound)
                    2 -> showDeleteConfirmation(sound)
                }
            }
            .setNegativeButton("Annuler", null)
            .create()
        
        dialog.show()
        
        // Forcer les backgrounds sombres après l'affichage
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.listView?.setBackgroundColor(Color.parseColor("#111827"))
        dialog.listView?.divider = null
    }
    
    private fun startDrag(button: Button, sound: Sound) {
        val data = ClipData.newPlainText("position", sound.buttonPosition.toString())
        val shadowBuilder = View.DragShadowBuilder(button)
        button.startDragAndDrop(data, shadowBuilder, sound, 0)
    }
    
    private fun handleDragEvent(targetButton: Button, event: DragEvent, targetPosition: Int): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                return true
            }
            DragEvent.ACTION_DRAG_ENTERED -> {
                targetButton.alpha = 0.5f
                return true
            }
            DragEvent.ACTION_DRAG_EXITED -> {
                targetButton.alpha = 1.0f
                return true
            }
            DragEvent.ACTION_DROP -> {
                targetButton.alpha = 1.0f
                val draggedSound = event.localState as? Sound
                if (draggedSound != null && draggedSound.buttonPosition != targetPosition) {
                    swapSounds(draggedSound.buttonPosition, targetPosition)
                }
                return true
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                targetButton.alpha = 1.0f
                return true
            }
        }
        return false
    }
    
    private fun swapSounds(fromPosition: Int, toPosition: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            val fromSound = withContext(Dispatchers.IO) {
                viewModel.getSoundAtPosition(viewModel.currentPage, fromPosition)
            }
            val toSound = withContext(Dispatchers.IO) {
                viewModel.getSoundAtPosition(viewModel.currentPage, toPosition)
            }
            
            if (fromSound != null) {
                val tempSound = fromSound.copy(buttonPosition = toPosition)
                viewModel.updateSound(tempSound)
                
                if (toSound != null) {
                    val swappedSound = toSound.copy(buttonPosition = fromPosition)
                    viewModel.updateSound(swappedSound)
                    Toast.makeText(this@SoundboardActivity, "Sons échangés", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SoundboardActivity, "Son déplacé", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showRenameDialog(sound: Sound) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edittext, null)
        val input = dialogView.findViewById<android.widget.EditText>(R.id.dialog_edit_text)
        input.setText(sound.name)
        input.selectAll()
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Renommer")
            .setView(dialogView)
            .setPositiveButton("Enregistrer") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val updatedSound = sound.copy(name = newName)
                    soundNames[sound.buttonPosition] = newName
                    viewModel.updateSound(updatedSound)
                    Toast.makeText(this, "Renommé: $newName", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .create()
        
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
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
            pageIndicator.text = "PAGE %02d".format(page + 1)
        }
    }
    
    private fun observeSounds() {
        viewModel.currentPageSounds.observe(this) { sounds ->
            updateButtonsWithSounds(sounds)
        }
    }
    
    private fun updateButtonsWithSounds(sounds: List<Sound>) {
        val playingPos = currentPlayingPosition
        
        buttonList.forEachIndexed { index, button ->
            val position = index + 1
            button.text = ""
            button.contentDescription = getString(R.string.sound_button)
            button.setTextColor(Color.parseColor("#FFFFFF"))
            button.setBackgroundResource(R.drawable.pad_background)
            button.elevation = 0f
        }
        
        soundMap.clear()
        soundColors.clear()
        soundDurations.clear()
        soundNames.clear()
        
        sounds.forEach { sound ->
            val buttonIndex = sound.buttonPosition - 1
            if (buttonIndex in buttonList.indices) {
                val button = buttonList[buttonIndex]
                soundNames[sound.buttonPosition] = sound.name
                
                // Si c'est le bouton en cours de lecture, afficher l'indicateur
                if (playingPos == sound.buttonPosition) {
                    button.text = sound.name
                    button.setTextColor(Color.parseColor("#00E5FF"))
                    button.setBackgroundResource(R.drawable.pad_background_active)
                    button.elevation = 0f
                } else {
                    button.text = sound.name
                    button.setTextColor(Color.parseColor("#FFFFFF"))
                    button.setBackgroundResource(R.drawable.pad_background)
                }
                
                button.contentDescription = sound.name
                soundColors[sound.buttonPosition] = sound.buttonColor
                
                loadSoundIntoPool(sound)
            }
        }
    }
    
    private fun loadSoundIntoPool(sound: Sound) {
        try {
            val uri = Uri.parse(sound.filePath)
            val afd = contentResolver.openAssetFileDescriptor(uri, "r")
            
            if (afd != null) {
                val soundId = soundPool.load(afd.fileDescriptor, afd.startOffset, afd.length, 1)
                soundMap[sound.buttonPosition] = soundId
                
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val duration = durationStr?.toLongOrNull() ?: 2000L
                    soundDurations[sound.buttonPosition] = duration
                    retriever.release()
                } catch (e: Exception) {
                    soundDurations[sound.buttonPosition] = 2000L
                }
                
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
            val button = buttonList[position - 1]
            val soundName = soundNames[position] ?: return@let
            
            // Restaurer l'ancien bouton
            currentPlayingPosition?.let { oldPos ->
                if (oldPos != position && oldPos in 1..18) {
                    val oldButton = buttonList[oldPos - 1]
                    val oldName = soundNames[oldPos]
                    if (oldName != null) {
                        oldButton.text = oldName
                        oldButton.setTextColor(Color.parseColor("#FFFFFF"))
                        oldButton.setBackgroundResource(R.drawable.pad_background)
                        oldButton.elevation = 0f
                    }
                }
            }
            
            handler.removeCallbacksAndMessages("restore_$position")
            
            // Appliquer l'indicateur visuel
            button.text = soundName
            button.setTextColor(Color.parseColor("#00E5FF"))
            button.setBackgroundResource(R.drawable.pad_background_active)
            button.elevation = 0f
            currentPlayingPosition = position
            
            // Jouer le son
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
            
            val duration = soundDurations[position] ?: 2000L
            
            // Restaurer après la durée du son
            val restoreRunnable = Runnable {
                if (currentPlayingPosition == position) {
                    button.text = soundName
                    button.setTextColor(Color.parseColor("#FFFFFF"))
                    button.setBackgroundResource(R.drawable.pad_background)
                    button.elevation = 0f
                    currentPlayingPosition = null
                }
            }
            handler.postDelayed(restoreRunnable, duration)
            
        } ?: run {
            Toast.makeText(this, "Aucun son à cette position", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showDeleteConfirmation(sound: Sound) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_message, null)
        val messageView = dialogView.findViewById<TextView>(R.id.dialog_message)
        messageView.text = "Supprimer \"${sound.name}\" ?"
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Supprimer")
            .setView(dialogView)
            .setPositiveButton("Supprimer") { _, _ ->
                try {
                    val uri = Uri.parse(sound.filePath)
                    contentResolver.releasePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Ignorer
                }
                
                viewModel.deleteSound(sound)
                Toast.makeText(this, "Supprimé: ${sound.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annuler", null)
            .create()
        
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
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
        
        val adapter = android.widget.ArrayAdapter(
            this,
            R.layout.simple_list_item_1,
            positionStrings
        )
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Choisir une position")
            .setAdapter(adapter) { _, which ->
                selectedButtonPosition = positions[which]
                requestAudioFile()
            }
            .setNegativeButton("Annuler", null)
            .create()
        
        dialog.show()
        
        // Forcer les backgrounds sombres après l'affichage
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.listView?.setBackgroundColor(Color.parseColor("#111827"))
        dialog.listView?.divider = null
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
        
        CoroutineScope(Dispatchers.Main).launch {
            val existingSound = withContext(Dispatchers.IO) {
                viewModel.getSoundByFilePath(uriString)
            }
            
            if (existingSound != null) {
                val dialogView = layoutInflater.inflate(R.layout.dialog_message, null)
                val messageView = dialogView.findViewById<TextView>(R.id.dialog_message)
                messageView.text = "Ce fichier sonore est déjà utilisé pour \"${existingSound.name}\" (Page ${existingSound.pageNumber + 1}, Position ${existingSound.buttonPosition}).\n\nVoulez-vous quand même l'ajouter ?"
                
                val dialog = androidx.appcompat.app.AlertDialog.Builder(this@SoundboardActivity)
                    .setTitle("Fichier déjà utilisé")
                    .setView(dialogView)
                    .setPositiveButton("Oui") { _, _ ->
                        proceedWithAudioSelection(uri, position)
                    }
                    .setNegativeButton("Non") { _, _ ->
                        selectedButtonPosition = null
                    }
                    .create()
                
                dialog.show()
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            } else {
                proceedWithAudioSelection(uri, position)
            }
        }
    }
    
    private fun proceedWithAudioSelection(uri: Uri, position: Int) {
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
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_edittext, null)
        val input = dialogView.findViewById<android.widget.EditText>(R.id.dialog_edit_text)
        input.hint = "Nom du son"
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Nom du son")
            .setView(dialogView)
            .setPositiveButton("Ajouter") { _, _ ->
                val name = input.text.toString().ifEmpty { "Son $position" }
                saveSound(name, uri.toString(), position)
            }
            .setNegativeButton("Annuler") { _, _ ->
                selectedButtonPosition = null
            }
            .create()
        
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
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
    
    private fun showFavoritesDialog() {
        // Créer le dialog personnalisé
        val dialogView = layoutInflater.inflate(R.layout.dialog_favorites, null)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_favorites)
        val tvCount = dialogView.findViewById<TextView>(R.id.tv_favorites_count)
        val tvEmpty = dialogView.findViewById<TextView>(R.id.tv_empty_favorites)
        val btnClose = dialogView.findViewById<TextView>(R.id.btn_close_dialog)
        
        // Configurer le RecyclerView
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        val adapter = FavoriteSoundAdapter { sound ->
            // Fermer le dialog
            currentFavoritesDialog?.dismiss()
            
            // Naviguer vers la page du son et le jouer
            CoroutineScope(Dispatchers.Main).launch {
                if (viewModel.currentPage != sound.pageNumber) {
                    viewModel.goToPage(sound.pageNumber)
                    delay(200)
                }
                playSound(sound.buttonPosition)
            }
        }
        recyclerView.adapter = adapter
        
        // Créer le dialog SANS bouton par défaut
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        // Gérer le clic sur la croix (X)
        btnClose.setOnClickListener {
            dialog.dismiss()
        }
        
        // Observer les favoris
        val favoritesLiveData = viewModel.getFavoriteSounds()
        val observer = androidx.lifecycle.Observer<List<Sound>> { favList ->
            if (favList.isEmpty()) {
                recyclerView.visibility = View.GONE
                tvEmpty.visibility = View.VISIBLE
                tvCount.text = "00"
            } else {
                recyclerView.visibility = View.VISIBLE
                tvEmpty.visibility = View.GONE
                tvCount.text = "%02d".format(favList.size)
                adapter.submitList(favList)
            }
        }
        
        favoritesLiveData.observe(this, observer)
        
        // Retirer l'observer quand le dialog est fermé
        dialog.setOnDismissListener {
            favoritesLiveData.removeObserver(observer)
            currentFavoritesDialog = null
        }
        
        currentFavoritesDialog = dialog
        dialog.show()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    
    private var currentFavoritesDialog: androidx.appcompat.app.AlertDialog? = null
    
    override fun onDestroy() {
        super.onDestroy()
        soundPool.release()
        handler.removeCallbacksAndMessages(null)
    }
}
