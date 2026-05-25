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

class FavoriteSoundAdapter(
    private val onSoundClick: (Sound) -> Unit
) : ListAdapter<Sound, FavoriteSoundAdapter.FavoriteViewHolder>(SoundDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite_sound, parent, false)
        return FavoriteViewHolder(view, onSoundClick)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FavoriteViewHolder(
        itemView: View,
        private val onSoundClick: (Sound) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val tvSoundName: TextView = itemView.findViewById(R.id.tv_sound_name)
        private val tvSoundLocation: TextView = itemView.findViewById(R.id.tv_sound_location)
        private val tvPositionNumber: TextView = itemView.findViewById(R.id.tv_position_number)
        private val btnPlay: ImageButton = itemView.findViewById(R.id.btn_play_favorite)
        
        fun bind(sound: Sound) {
            tvSoundName.text = sound.name
            tvPositionNumber.text = "%02d".format(sound.buttonPosition)
            tvSoundLocation.text = "PAGE ${sound.pageNumber + 1} · POS %02d".format(sound.buttonPosition)
            
            itemView.setOnClickListener {
                onSoundClick(sound)
            }
            
            btnPlay.setOnClickListener {
                onSoundClick(sound)
            }
        }
    }

    class SoundDiffCallback : DiffUtil.ItemCallback<Sound>() {
        override fun areItemsTheSame(oldItem: Sound, newItem: Sound): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Sound, newItem: Sound): Boolean {
            return oldItem == newItem
        }
    }
}
