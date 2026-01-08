package com.android.mobilecamera.feature.gallery

import android.app.Application
import android.content.IntentSender
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.mobilecamera.data.database.AppDatabase
import com.android.mobilecamera.data.database.MediaEntity
import com.android.mobilecamera.data.database.MediaType
import com.android.mobilecamera.data.repository.MediaRepository
import com.android.mobilecamera.infrastructure.media.DeleteResult
import com.android.mobilecamera.infrastructure.media.MediaManager
import com.android.mobilecamera.infrastructure.media.MediaSyncManager
import com.android.mobilecamera.infrastructure.permissions.PermissionManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

data class GalleryUiState(
    val mediaList: List<MediaEntity> = emptyList(),
    val selectedItems: Set<Int> = emptySet(),
    val isSelectionMode: Boolean = false,
    val isSyncing: Boolean = false,
    val syncProgress: Pair<Int, Int>? = null,
    val deleteMessage: String? = null
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val mediaManager = MediaManager(application)
    private val repository = MediaRepository(database.mediaDao(), mediaManager)
    private val mediaSyncManager = MediaSyncManager(application)
    private val permissionManager = PermissionManager(application)

    private val _selectedItems = MutableStateFlow<Set<Int>>(emptySet())
    private val _isSelectionMode = MutableStateFlow(false)
    private val _isSyncing = MutableStateFlow(false)
    private val _syncProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    private val _deleteMessage = MutableStateFlow<String?>(null)

    private val _pendingDeleteRequest = MutableStateFlow<IntentSender?>(null)
    val pendingDeleteRequest: StateFlow<IntentSender?> = _pendingDeleteRequest.asStateFlow()

    val uiState: StateFlow<GalleryUiState> = combine(
        repository.allMedia,
        _selectedItems,
        _isSelectionMode
    ) { media, selected, selectionMode ->
        Triple(media, selected, selectionMode)
    }.combine(
        combine(_isSyncing, _syncProgress, _deleteMessage) { syncing, progress, message ->
            Triple(syncing, progress, message)
        }
    ) { first, second ->
        GalleryUiState(
            mediaList = first.first,
            selectedItems = first.second,
            isSelectionMode = first.third,
            isSyncing = second.first,
            syncProgress = second.second,
            deleteMessage = second.third
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, GalleryUiState())

    init {
        if (permissionManager.hasStoragePermissions()) {
            syncMediaFromStorage()
        }
    }

    private fun syncMediaFromStorage() {
        if (_isSyncing.value) {
            Log.w("GalleryViewModel", "Sync already in progress")
            return
        }

        viewModelScope.launch {
            try {
                if (!repository.isEmpty()) {
                    Log.d("GalleryViewModel", "Database not empty, skipping sync")
                    return@launch
                }

                _isSyncing.value = true
                _syncProgress.value = null

                val restoredCount = mediaSyncManager.syncMedia(
                    onProgress = { current, total ->
                        _syncProgress.value = current to total
                    },
                    onSave = { uri, type, duration, timestamp, thumbPath ->
                        repository.saveMedia(
                            path = uri,
                            type = type,
                            duration = duration,
                            timestamp = timestamp,
                            thumbnailPath = thumbPath
                        )
                    }
                )

                if (restoredCount > 0) {
                    Log.d("GalleryViewModel", "Restored $restoredCount files")
                }

            } catch (e: Exception) {
                Log.e("GalleryViewModel", "Sync failed", e)
            } finally {
                _isSyncing.value = false
                _syncProgress.value = null
            }
        }
    }

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
            if (itemsToDelete.isEmpty()) return@launch

            when (val result = repository.deleteMultipleMedia(itemsToDelete)) {
                is DeleteResult.Success -> {
                    _deleteMessage.value = "Удалено файлов: ${result.deletedCount}"
                    clearSelection()
                }
                is DeleteResult.RequiresPermission -> {
                    _pendingDeleteRequest.value = result.intentSender
                }
                is DeleteResult.Error -> {
                    _deleteMessage.value = result.message
                }
            }
        }
    }

    fun onDeletePermissionResult(granted: Boolean) {
        _pendingDeleteRequest.value = null
        if (granted) {
            deleteSelected()
        } else {
            _deleteMessage.value = "Удаление отменено"
        }
    }

    fun clearDeleteMessage() {
        _deleteMessage.value = null
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
            clearSelection()
            _deleteMessage.value = "Все записи очищены из БД"
        }
    }
}

@Suppress("UNCHECKED_CAST")
class GalleryViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GalleryViewModel(application) as T
    }
}