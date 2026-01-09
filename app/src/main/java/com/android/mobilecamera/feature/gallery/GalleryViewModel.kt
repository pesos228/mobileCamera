package com.android.mobilecamera.feature.gallery

import android.app.Application
import android.content.IntentSender
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.mobilecamera.R
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

private const val TAG = "GalleryViewModel"

sealed interface GalleryMessage {
    data class DeleteSuccess(val count: Int) : GalleryMessage
    data class SimpleMessage(val resId: Int) : GalleryMessage
}

data class GalleryUiState(
    val mediaList: List<MediaEntity> = emptyList(),
    val selectedItems: Set<Int> = emptySet(),
    val isSelectionMode: Boolean = false,
    val isSyncing: Boolean = false,
    val syncProgress: Pair<Int, Int>? = null,
    val message: GalleryMessage? = null
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
    private val _message = MutableStateFlow<GalleryMessage?>(null)

    private val _pendingDeleteRequest = MutableStateFlow<IntentSender?>(null)
    val pendingDeleteRequest: StateFlow<IntentSender?> = _pendingDeleteRequest.asStateFlow()

    val uiState: StateFlow<GalleryUiState> = combine(
        repository.allMedia,
        _selectedItems,
        _isSelectionMode
    ) { media, selected, selectionMode ->
        Triple(media, selected, selectionMode)
    }.combine(
        combine(_isSyncing, _syncProgress, _message) { syncing, progress, message ->
            Triple(syncing, progress, message)
        }
    ) { first, second ->
        GalleryUiState(
            mediaList = first.first,
            selectedItems = first.second,
            isSelectionMode = first.third,
            isSyncing = second.first,
            syncProgress = second.second,
            message = second.third
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, GalleryUiState())

    init {
        if (permissionManager.hasStoragePermissions()) {
            syncMediaFromStorage()
        }
    }

    private fun syncMediaFromStorage() {
        if (_isSyncing.value) {
            Log.w(TAG, "Sync already in progress")
            return
        }

        viewModelScope.launch {
            try {
                if (!repository.isEmpty()) {
                    Log.d(TAG, "Database not empty, skipping sync")
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
                    Log.d(TAG, "Restored $restoredCount files")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
            } finally {
                _isSyncing.value = false
                _syncProgress.value = null
            }
        }
    }

    fun createMocks() {
        viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create mocks", e)
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
                    _message.value = GalleryMessage.DeleteSuccess(result.deletedCount)
                    clearSelection()
                }
                is DeleteResult.RequiresPermission -> {
                    _pendingDeleteRequest.value = result.intentSender
                }
                is DeleteResult.Error -> {
                    _message.value = GalleryMessage.SimpleMessage(R.string.msg_delete_error)
                }
            }
        }
    }

    fun onDeletePermissionResult(granted: Boolean) {
        _pendingDeleteRequest.value = null
        if (granted) {
            deleteSelected()
        } else {
            _message.value = GalleryMessage.SimpleMessage(R.string.msg_delete_cancelled)
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun clearAll() {
        viewModelScope.launch {
            try {
                repository.clearAll()
                clearSelection()
                _message.value = GalleryMessage.SimpleMessage(R.string.msg_cleared_all)
            } catch (e: Exception) {
                Log.e(TAG, "Clear all failed", e)
            }
        }
    }
}

class GalleryViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return GalleryViewModel(application) as T
    }
}