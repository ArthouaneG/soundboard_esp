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

/**
 * Activité principale du soundboard.
 *
 * Affiche une grille de 18 boutons (3 colonnes × 6 lignes) par page.
 * Chaque bouton peut être associé à un fichier audio.
 *
 * Fonctionnalités :
 * - **Appui court** : jouer le son assigné au bouton.
 * - **Appui long (1,5 s)** : afficher le menu options (favori / renommer / supprimer).
 * - **Glisser-déposer** : réorganiser les sons en les faisant glisser d'un bouton à un autre.
 * - **Navigation** : flèches gauche/droite pour changer de page.
 * - **Ajout** : bouton "+ Son" pour assigner un fichier audio à une position libre.
 * - **Favoris** : bouton "★" pour voir et jouer rapidement les sons favoris.
 */
class SoundboardActivity : AppCompatActivity() {

    private lateinit var viewModel: SoundboardViewModel

    /** Pool audio permettant de jouer jusqu'à 5 sons simultanément. */
    private lateinit var soundPool: SoundPool

    /** Association position bouton → ID SoundPool (nécessaire pour appeler soundPool.play). */
    private val soundMap = HashMap<Int, Int>()

    /** Durée en millisecondes de chaque son, pour réinitialiser l'indicateur visuel après lecture. */
    private val soundDurations = HashMap<Int, Long>()

    /** Noms des sons indexés par position, mis en cache pour éviter de requêter la BDD à chaque frame. */
    private val soundNames = HashMap<Int, String>()

    /** Liste ordonnée des 18 boutons de la grille (index 0 = position 1). */
    private val buttonList = mutableListOf<Button>()

    /** Position du bouton sélectionné lors de l'ajout d'un son via le menu. */
    private var selectedButtonPosition: Int? = null

    /** TextView affichant "PAGE 01", "PAGE 02", etc. */
    private lateinit var pageIndicator: TextView

    /** Position du bouton dont le son est actuellement en lecture (pour l'indicateur visuel). */
    private var currentPlayingPosition: Int? = null

    /** Couleurs hex des boutons, indexées par position. */
    private val soundColors = HashMap<Int, String>()

    /** Handler sur le thread principal pour planifier des tâches différées (menu long press, reset). */
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Lanceur du sélecteur de fichier système (SAF).
     * S'active après vérification des permissions ; appelle [handleAudioSelection] avec l'URI choisie.
     */
    private val selectAudioLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { handleAudioSelection(it) }
    }

    /**
     * Lanceur de la demande de permission de lecture audio.
     * Si accordée, ouvre le sélecteur de fichier ; sinon affiche un message d'erreur.
     */
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

        // Initialisation du ViewModel lié au cycle de vie de cette activité
        viewModel = ViewModelProvider(this)[SoundboardViewModel::class.java]
        pageIndicator = findViewById(R.id.tv_page_indicator)

        // Séquence d'initialisation de l'interface
        initializeSoundPool()    // Prépare le moteur audio
        initializeButtons()      // Configure les 18 boutons de la grille
        setupNavigation()        // Branche les flèches de pagination
        observeSounds()          // Écoute les changements de sons en base
        observePageChanges()     // Met à jour l'indicateur de page
        
        findViewById<Button>(R.id.btn_add_sound).setOnClickListener {
            showAddSoundDialog()
        }
        
        findViewById<Button>(R.id.btn_favorites).setOnClickListener {
            showFavoritesDialog()
        }
    }
    
    /** Crée le SoundPool avec une limite de 5 flux audio simultanés. */
    private fun initializeSoundPool() {
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .build()
    }

    /**
     * Associe les 18 boutons XML à leur logique de toucher.
     *
     * Pour chaque bouton, un [View.OnTouchListener] personnalisé gère trois cas :
     * - **ACTION_DOWN** : mémoriser la position du doigt et planifier le menu long-press (1,5 s).
     * - **ACTION_MOVE** : si le doigt a bougé de plus de 10 px, annuler le menu et initier un drag.
     * - **ACTION_UP / CANCEL** : si pas de mouvement ni menu → jouer le son (clic court).
     *
     * Un [View.OnDragListener] sur chaque bouton gère la réception des drops.
     */
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
            val position = index + 1   // Positions 1 à 18

            // Variables d'état du geste en cours
            var initialX = 0f
            var initialY = 0f
            var hasMoved = false
            var menuShown = false
            var dragStarted = false

            // Runnable déclenché après 1,5 s d'appui sans mouvement → affiche le menu options
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

                        // Planifier le menu long-press après 1,5 seconde d'appui immobile
                        if (button.text.isNotEmpty()) {
                            handler.postDelayed(menuRunnable, 1500)
                        }
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = abs(event.x - initialX)
                        val deltaY = abs(event.y - initialY)

                        // Seuil de 10 px pour distinguer un clic d'un glissement
                        if (deltaX > 10 || deltaY > 10) {
                            hasMoved = true

                            // Si le doigt glisse sans que le menu soit apparu → initier un drag
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
                        // Annuler le menu programmé si le doigt se lève avant 1,5 s
                        handler.removeCallbacks(menuRunnable)

                        // Clic court : pas de mouvement, pas de menu, pas de drag → jouer le son
                        if (!hasMoved && !menuShown && !dragStarted) {
                            playSound(position)
                        }
                        false
                    }
                    else -> false
                }
            }

            // Appui long sur un bouton VIDE → ouvrir le sélecteur de fichier directement
            button.setOnLongClickListener {
                if (button.text.isEmpty()) {
                    selectedButtonPosition = position
                    requestAudioFile()
                }
                true
            }

            // Chaque bouton peut recevoir des drops pour le glisser-déposer
            button.setOnDragListener { v, dragEvent ->
                handleDragEvent(v as Button, dragEvent, position)
            }
        }
    }

    /**
     * Vérifie si un son existe à la position donnée, et affiche le dialog d'options si c'est le cas.
     * Appelé par le Runnable du long-press.
     */
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

    /**
     * Affiche un dialog avec les options disponibles pour un son existant :
     * - Ajouter/retirer des favoris
     * - Renommer
     * - Supprimer
     */
    private fun showSoundOptionsDialog(sound: Sound) {
        // Le libellé du premier item change selon l'état favori actuel
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

        // Personnalisation visuelle : fond sombre et suppression du séparateur
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.listView?.setBackgroundColor(Color.parseColor("#111827"))
        dialog.listView?.divider = null
    }

    /**
     * Démarre le glisser-déposer depuis le bouton source.
     * Le [Sound] est passé en localState pour être récupéré dans [handleDragEvent].
     */
    private fun startDrag(button: Button, sound: Sound) {
        val data = ClipData.newPlainText("position", sound.buttonPosition.toString())
        val shadowBuilder = View.DragShadowBuilder(button)
        button.startDragAndDrop(data, shadowBuilder, sound, 0)
    }

    /**
     * Gère les événements de drag sur un bouton cible.
     *
     * - [DragEvent.ACTION_DRAG_ENTERED] / [DragEvent.ACTION_DRAG_EXITED] : effet visuel alpha.
     * - [DragEvent.ACTION_DROP] : récupère le [Sound] source et appelle [swapSounds].
     */
    private fun handleDragEvent(targetButton: Button, event: DragEvent, targetPosition: Int): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                return true  // Indiquer qu'on accepte le drag
            }
            DragEvent.ACTION_DRAG_ENTERED -> {
                targetButton.alpha = 0.5f  // Assombrir pour indiquer la zone de drop
                return true
            }
            DragEvent.ACTION_DRAG_EXITED -> {
                targetButton.alpha = 1.0f  // Restaurer l'opacité
                return true
            }
            DragEvent.ACTION_DROP -> {
                targetButton.alpha = 1.0f
                val draggedSound = event.localState as? Sound
                // Échanger uniquement si la source et la cible sont différentes
                if (draggedSound != null && draggedSound.buttonPosition != targetPosition) {
                    swapSounds(draggedSound.buttonPosition, targetPosition)
                }
                return true
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                targetButton.alpha = 1.0f  // Toujours restaurer à la fin
                return true
            }
        }
        return false
    }

    /**
     * Échange les positions de deux sons dans la base de données.
     * Si la position cible est vide, le son est simplement déplacé (pas d'échange).
     *
     * @param fromPosition Position source du son en cours de drag.
     * @param toPosition   Position cible où déposer le son.
     */
    private fun swapSounds(fromPosition: Int, toPosition: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            val fromSound = withContext(Dispatchers.IO) {
                viewModel.getSoundAtPosition(viewModel.currentPage, fromPosition)
            }
            val toSound = withContext(Dispatchers.IO) {
                viewModel.getSoundAtPosition(viewModel.currentPage, toPosition)
            }

            if (fromSound != null) {
                // Déplacer le son source vers la position cible
                val tempSound = fromSound.copy(buttonPosition = toPosition)
                viewModel.updateSound(tempSound)

                if (toSound != null) {
                    // Il y avait un son à la cible : l'échanger vers la position source
                    val swappedSound = toSound.copy(buttonPosition = fromPosition)
                    viewModel.updateSound(swappedSound)
                    Toast.makeText(this@SoundboardActivity, "Sons échangés", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SoundboardActivity, "Son déplacé", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /** Affiche un dialog pour changer le nom d'un son. Pré-remplit le champ avec le nom actuel. */
    private fun showRenameDialog(sound: Sound) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edittext, null)
        val input = dialogView.findViewById<android.widget.EditText>(R.id.dialog_edit_text)
        input.setText(sound.name)
        input.selectAll()  // Sélectionner le texte pour faciliter la saisie

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Renommer")
            .setView(dialogView)
            .setPositiveButton("Enregistrer") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val updatedSound = sound.copy(name = newName)
                    soundNames[sound.buttonPosition] = newName  // Mettre à jour le cache local
                    viewModel.updateSound(updatedSound)
                    Toast.makeText(this, "Renommé: $newName", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    /** Configure les boutons flèches gauche/droite pour changer de page. */
    private fun setupNavigation() {
        findViewById<ImageButton>(R.id.btn_previous_page).setOnClickListener {
            viewModel.previousPage()
        }

        findViewById<ImageButton>(R.id.btn_next_page).setOnClickListener {
            viewModel.nextPage()
        }
    }

    /** Observe le numéro de page et met à jour l'indicateur textuel (ex: "PAGE 02"). */
    private fun observePageChanges() {
        viewModel.currentPageLiveData.observe(this) { page ->
            pageIndicator.text = "PAGE %02d".format(page + 1)
        }
    }

    /** Observe la liste des sons de la page courante et met à jour les boutons. */
    private fun observeSounds() {
        viewModel.currentPageSounds.observe(this) { sounds ->
            updateButtonsWithSounds(sounds)
        }
    }

    /**
     * Met à jour visuellement tous les boutons de la grille selon la liste de sons reçue.
     *
     * Étape 1 : réinitialiser tous les boutons (état vide).
     * Étape 2 : vider les caches audio (soundMap, soundDurations, soundNames, soundColors).
     * Étape 3 : pour chaque son, mettre à jour le bouton correspondant et charger l'audio.
     * Le bouton en cours de lecture conserve son indicateur visuel cyan (#00E5FF).
     */
    private fun updateButtonsWithSounds(sounds: List<Sound>) {
        val playingPos = currentPlayingPosition

        // Réinitialiser tous les boutons à leur état vide
        buttonList.forEachIndexed { index, button ->
            val position = index + 1
            button.text = ""
            button.contentDescription = getString(R.string.sound_button)
            button.setTextColor(Color.parseColor("#FFFFFF"))
            button.setBackgroundResource(R.drawable.pad_background)
            button.elevation = 0f
        }

        // Vider tous les caches pour repartir d'un état propre
        soundMap.clear()
        soundColors.clear()
        soundDurations.clear()
        soundNames.clear()

        sounds.forEach { sound ->
            val buttonIndex = sound.buttonPosition - 1
            if (buttonIndex in buttonList.indices) {
                val button = buttonList[buttonIndex]
                soundNames[sound.buttonPosition] = sound.name

                // Appliquer l'indicateur de lecture si ce bouton est en cours de lecture
                if (playingPos == sound.buttonPosition) {
                    button.text = sound.name
                    button.setTextColor(Color.parseColor("#00E5FF"))  // Cyan = en lecture
                    button.setBackgroundResource(R.drawable.pad_background_active)
                    button.elevation = 0f
                } else {
                    button.text = sound.name
                    button.setTextColor(Color.parseColor("#FFFFFF"))
                    button.setBackgroundResource(R.drawable.pad_background)
                }

                button.contentDescription = sound.name
                soundColors[sound.buttonPosition] = sound.buttonColor

                // Charger le fichier audio dans le SoundPool
                loadSoundIntoPool(sound)
            }
        }
    }

    /**
     * Ouvre le fichier audio associé au [sound] via le ContentResolver et le charge dans le [SoundPool].
     * Extrait aussi la durée réelle du fichier via [MediaMetadataRetriever].
     * En cas d'échec, une durée par défaut de 2000 ms est utilisée.
     */
    private fun loadSoundIntoPool(sound: Sound) {
        try {
            val uri = Uri.parse(sound.filePath)
            val afd = contentResolver.openAssetFileDescriptor(uri, "r")

            if (afd != null) {
                // Charger l'audio dans le pool et mémoriser son ID
                val soundId = soundPool.load(afd.fileDescriptor, afd.startOffset, afd.length, 1)
                soundMap[sound.buttonPosition] = soundId

                try {
                    // Lire les métadonnées pour obtenir la durée exacte
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val duration = durationStr?.toLongOrNull() ?: 2000L
                    soundDurations[sound.buttonPosition] = duration
                    retriever.release()
                } catch (e: Exception) {
                    // Durée par défaut si les métadonnées sont illisibles
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
    
    /**
     * Joue le son associé à la position donnée.
     *
     * - Réinitialise visuellement l'ancien bouton en lecture (s'il en existe un).
     * - Applique l'indicateur visuel cyan sur le bouton actif.
     * - Joue le son via [SoundPool.play] à volume et vitesse maximaux.
     * - Planifie un Runnable pour restaurer le bouton après la durée réelle du son.
     *
     * @param position Numéro du bouton (1 à 18).
     */
    private fun playSound(position: Int) {
        soundMap[position]?.let { soundId ->
            val button = buttonList[position - 1]
            val soundName = soundNames[position] ?: return@let

            // Restaurer l'apparence du bouton qui était précédemment en lecture
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

            // Annuler tout Runnable de restauration précédent pour ce bouton
            handler.removeCallbacksAndMessages("restore_$position")

            // Appliquer l'indicateur visuel (cyan) sur le bouton actif
            button.text = soundName
            button.setTextColor(Color.parseColor("#00E5FF"))
            button.setBackgroundResource(R.drawable.pad_background_active)
            button.elevation = 0f
            currentPlayingPosition = position

            // Lancer la lecture (volume L=1, R=1, priorité=1, loop=0, rate=1x)
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f)

            val duration = soundDurations[position] ?: 2000L

            // Planifier la restauration visuelle après la fin du son
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

    /**
     * Affiche un dialog de confirmation avant de supprimer un son.
     * Libère aussi la permission persistante sur l'URI du fichier audio.
     */
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
                    // Libérer la permission persistante sur l'URI (bonne pratique SAF)
                    contentResolver.releasePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Ignorer si l'URI n'avait pas de permission persistante
                }

                viewModel.deleteSound(sound)
                Toast.makeText(this, "Supprimé: ${sound.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annuler", null)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    /**
     * Affiche un dialog listant les positions libres (boutons sans son).
     * L'utilisateur choisit une position, puis [requestAudioFile] est appelé.
     */
    private fun showAddSoundDialog() {
        // Filtrer uniquement les boutons vides
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

        // Personnalisation visuelle du dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.listView?.setBackgroundColor(Color.parseColor("#111827"))
        dialog.listView?.divider = null
    }

    /**
     * Vérifie et demande la permission de lecture audio adaptée à la version Android,
     * puis ouvre le sélecteur de fichiers.
     *
     * - Android 13+ (TIRAMISU) : [Manifest.permission.READ_MEDIA_AUDIO]
     * - Android < 13 : [Manifest.permission.READ_EXTERNAL_STORAGE]
     */
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

    /**
     * Appelée après la sélection d'un fichier audio.
     * Vérifie si l'URI est déjà utilisée par un autre son et avertit l'utilisateur.
     * Si non, appelle directement [proceedWithAudioSelection].
     */
    private fun handleAudioSelection(uri: Uri) {
        val position = selectedButtonPosition ?: return
        val uriString = uri.toString()

        CoroutineScope(Dispatchers.Main).launch {
            val existingSound = withContext(Dispatchers.IO) {
                viewModel.getSoundByFilePath(uriString)
            }

            if (existingSound != null) {
                // Ce fichier est déjà assigné : demander confirmation à l'utilisateur
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

    /**
     * Prend la permission persistante sur l'URI (nécessaire pour rouvrir le fichier après reboot),
     * puis demande un nom à l'utilisateur avant d'appeler [saveSound].
     *
     * @param uri URI du fichier audio sélectionné.
     * @param position Position du bouton destination.
     */
    private fun proceedWithAudioSelection(uri: Uri, position: Int) {
        try {
            // Obtenir un accès permanent au fichier via le Storage Access Framework
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
                // Utiliser "Son {position}" si le champ est vide
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
    /**
     * Crée et persiste un son dans la base de données.
     * La couleur du bouton est choisie cycliquement parmi une palette de 6 couleurs
     * en fonction de la position (1 à 18).
     */
    private fun saveSound(name: String, filePath: String, position: Int) {
        // Palette de couleurs cyclique pour les boutons
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

    /**
     * Affiche un dialog personnalisé listant les sons favoris.
     *
     * - Un [RecyclerView] affiche les favoris via [FavoriteSoundAdapter].
     * - Un observer LiveData met à jour la liste en temps réel.
     * - Un clic sur un son ferme le dialog, navigue vers sa page et le joue.
     * - L'observer est retiré à la fermeture du dialog pour éviter les fuites mémoire.
     */
    private fun showFavoritesDialog() {
        // Gonfler le layout du dialog personnalisé
        val dialogView = layoutInflater.inflate(R.layout.dialog_favorites, null)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_favorites)
        val tvCount = dialogView.findViewById<TextView>(R.id.tv_favorites_count)
        val tvEmpty = dialogView.findViewById<TextView>(R.id.tv_empty_favorites)
        val btnClose = dialogView.findViewById<TextView>(R.id.btn_close_dialog)

        // Configurer le RecyclerView avec un layout linéaire vertical
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        val adapter = FavoriteSoundAdapter { sound ->
            currentFavoritesDialog?.dismiss()  // Fermer le dialog

            // Naviguer vers la page du son (si différente) puis le jouer
            CoroutineScope(Dispatchers.Main).launch {
                if (viewModel.currentPage != sound.pageNumber) {
                    viewModel.goToPage(sound.pageNumber)
                    delay(200)  // Laisser le temps à l'UI de se mettre à jour
                }
                playSound(sound.buttonPosition)
            }
        }
        recyclerView.adapter = adapter

        // Créer le dialog sans boutons par défaut (la croix est dans le layout)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Bouton de fermeture personnalisé (croix dans le header du dialog)
        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // Observer la liste de favoris en temps réel
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

        // Nettoyer l'observer à la fermeture pour éviter les fuites mémoire
        dialog.setOnDismissListener {
            favoritesLiveData.removeObserver(observer)
            currentFavoritesDialog = null
        }

        currentFavoritesDialog = dialog
        dialog.show()
        // Fond transparent pour utiliser le shape arrondi du layout
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        // Largeur à 85% de l'écran
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    
    /** Référence au dialog des favoris actuellement ouvert, pour pouvoir le fermer programmatiquement. */
    private var currentFavoritesDialog: androidx.appcompat.app.AlertDialog? = null

    /**
     * Libère les ressources lors de la destruction de l'activité.
     * - Relâche le SoundPool (mémoire audio native).
     * - Annule tous les Runnables en attente sur le Handler principal.
     */
    override fun onDestroy() {
        super.onDestroy()
        soundPool.release()                         // Libérer la mémoire audio native
        handler.removeCallbacksAndMessages(null)    // Annuler tous les Runnables différés
    }
}
