package com.android.mobilecamera.feature.viewer

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.mobilecamera.data.database.AppDatabase
import com.android.mobilecamera.data.database.MediaEntity
import com.android.mobilecamera.data.repository.MediaRepository
import com.android.mobilecamera.infrastructure.media.MediaManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ViewerUiState {
    object Loading : ViewerUiState()
    data class Success(val media: MediaEntity) : ViewerUiState()
    object NotFound : ViewerUiState()
}

private const val TAG = "MediaViewerViewModel"

class MediaViewerViewModel(
    application: Application,
    private val mediaId: Int
) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val mediaManager = MediaManager(application)
    private val repository = MediaRepository(database.mediaDao(), mediaManager)

    private val _uiState = MutableStateFlow<ViewerUiState>(ViewerUiState.Loading)
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    init {
        loadMedia()
    }

    private fun loadMedia() {
        viewModelScope.launch {
            try {
                val media = repository.getMediaById(mediaId)
                _uiState.value = if (media != null) {
                    ViewerUiState.Success(media)
                } else {
                    ViewerUiState.NotFound
                }
            } catch (e: Exception) {
                Log.e(TAG, "Load media failed", e)
            }
        }
    }
}

class MediaViewerViewModelFactory(
    private val application: Application,
    private val mediaId: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MediaViewerViewModel(application, mediaId) as T
    }
}