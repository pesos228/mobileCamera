package com.android.mobilecamera.fragments.gallery

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.android.mobilecamera.data.database.MediaEntity
import com.android.mobilecamera.data.database.MediaType
import com.android.mobilecamera.databinding.ItemMediaBinding
import java.io.File
import kotlin.random.Random

class GalleryAdapter : ListAdapter<MediaEntity, GalleryAdapter.MediaViewHolder>(MediaDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MediaViewHolder(private val binding: ItemMediaBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MediaEntity) {
            // ЛОГИКА МОКОВ
            if (item.path.startsWith("mock_")) {
                // Генерируем стабильный цвет на основе ID (чтобы не моргало при скролле)
                val color = generateColor(item.id)
                binding.imageView.setImageDrawable(ColorDrawable(color))
            } else {
                // ЛОГИКА РЕАЛЬНЫХ ФАЙЛОВ
                binding.imageView.load(File(item.path)) {
                    crossfade(true)
                    placeholder(android.R.color.darker_gray)
                    error(android.R.color.holo_red_dark) // Если файл удален, будет красным
                }
            }

            // Иконка видео
            binding.videoIcon.visibility = if (item.type == MediaType.VIDEO) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }

        private fun generateColor(seed: Int): Int {
            val rnd = Random(seed)
            return Color.rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
        }
    }

    class MediaDiffCallback : DiffUtil.ItemCallback<MediaEntity>() {
        override fun areItemsTheSame(oldItem: MediaEntity, newItem: MediaEntity): Boolean =
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MediaEntity, newItem: MediaEntity): Boolean =
            oldItem == newItem
    }
}