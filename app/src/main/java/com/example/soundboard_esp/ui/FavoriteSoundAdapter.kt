package com.example.soundboard_esp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.soundboard_esp.R
import com.example.soundboard_esp.data.database.Sound

/**
 * Adaptateur RecyclerView pour afficher la liste des sons favoris.
 *
 * Utilise [ListAdapter] avec [DiffUtil] pour des mises à jour efficaces et animées
 * de la liste sans redessiner tous les éléments.
 *
 * @param onSoundClick Callback invoqué lorsque l'utilisateur clique sur un son
 *                     (item ou bouton play). Permet de naviguer vers sa page et de le jouer.
 */
class FavoriteSoundAdapter(
    private val onSoundClick: (Sound) -> Unit
) : ListAdapter<Sound, FavoriteSoundAdapter.FavoriteViewHolder>(SoundDiffCallback()) {

    /** Crée un nouveau ViewHolder en gonflant le layout de chaque item. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite_sound, parent, false)
        return FavoriteViewHolder(view, onSoundClick)
    }

    /** Lie les données du son à la position donnée au ViewHolder correspondant. */
    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder représentant un item de la liste des favoris.
     * Contient les références aux vues et la logique d'affichage d'un [Sound].
     */
    class FavoriteViewHolder(
        itemView: View,
        private val onSoundClick: (Sound) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvSoundName: TextView = itemView.findViewById(R.id.tv_sound_name)
        private val tvSoundLocation: TextView = itemView.findViewById(R.id.tv_sound_location)
        private val tvPositionNumber: TextView = itemView.findViewById(R.id.tv_position_number)
        private val btnPlay: ImageButton = itemView.findViewById(R.id.btn_play_favorite)

        /**
         * Remplit les vues avec les données du [sound] :
         * - Nom affiché en titre
         * - Numéro de position formaté sur 2 chiffres
         * - Page et position en sous-titre
         * - Clic sur l'item ou le bouton play déclenche [onSoundClick]
         */
        fun bind(sound: Sound) {
            tvSoundName.text = sound.name
            tvPositionNumber.text = "%02d".format(sound.buttonPosition)
            tvSoundLocation.text = "PAGE ${sound.pageNumber + 1} · POS %02d".format(sound.buttonPosition)

            // Clic sur la ligne entière
            itemView.setOnClickListener {
                onSoundClick(sound)
            }

            // Clic sur le bouton play (même action)
            btnPlay.setOnClickListener {
                onSoundClick(sound)
            }
        }
    }

    /**
     * Callback DiffUtil pour optimiser les mises à jour de la liste.
     * Compare d'abord les IDs (identité), puis le contenu complet (égalité).
     */
    class SoundDiffCallback : DiffUtil.ItemCallback<Sound>() {
        /** Deux sons sont le même objet s'ils ont le même ID en base de données. */
        override fun areItemsTheSame(oldItem: Sound, newItem: Sound): Boolean {
            return oldItem.id == newItem.id
        }

        /** Deux sons ont le même contenu si tous leurs champs sont identiques (data class). */
        override fun areContentsTheSame(oldItem: Sound, newItem: Sound): Boolean {
            return oldItem == newItem
        }
    }
}
