package com.android.mobilecamera.feature.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.mobilecamera.data.database.AppDatabase
import com.android.mobilecamera.data.database.MediaEntity
import com.android.mobilecamera.data.database.MediaType
import com.android.mobilecamera.data.repository.MediaRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

data class GalleryUiState(
    val mediaList: List<MediaEntity> = emptyList(),
    val selectedItems: Set<Int> = emptySet(),
    val isSelectionMode: Boolean = false
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = MediaRepository(database.mediaDao())

    private val _selectedItems = MutableStateFlow<Set<Int>>(emptySet())
    private val _isSelectionMode = MutableStateFlow(false)

    val uiState: StateFlow<GalleryUiState> = combine(
        repository.allMedia,
        _selectedItems,
        _isSelectionMode
    ) { media, selected, selectionMode ->
        GalleryUiState(
            mediaList = media,
            selectedItems = selected,
            isSelectionMode = selectionMode
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, GalleryUiState())

    fun createMocks() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val oneDay = 24 * 60 * 60 * 1000L

            for (i in 1..12) {
                val isVideo = i % 3 == 0
                val duration = if (isVideo) Random.nextLong(5000, 120000) else null
                val daysAgo = (i - 1) / 4
                val timestamp = now - (daysAgo * oneDay) - (i * 60 * 1000L)

                repository.saveMedia(
                    path = "mock_$i",
                    type = if (isVideo) MediaType.VIDEO else MediaType.PHOTO,
                    duration = duration,
                    timestamp = timestamp
                )
            }
        }
    }

    fun toggleSelection(itemId: Int) {
        _selectedItems.value = if (itemId in _selectedItems.value) {
            _selectedItems.value - itemId
        } else {
            _selectedItems.value + itemId
        }

        if (_selectedItems.value.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    fun startSelectionMode(itemId: Int) {
        _isSelectionMode.value = true
        _selectedItems.value = setOf(itemId)
    }

    fun clearSelection() {
        _isSelectionMode.value = false
        _selectedItems.value = emptySet()
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val itemsToDelete = uiState.value.mediaList.filter {
                it.id in _selectedItems.value
            }
            itemsToDelete.forEach { repository.deleteMedia(it) }
            clearSelection()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
            clearSelection()
        }
    }
}

class GalleryViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GalleryViewModel(application) as T
    }
}