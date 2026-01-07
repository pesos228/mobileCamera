package com.android.mobilecamera.feature.viewer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.mobilecamera.data.database.AppDatabase
import com.android.mobilecamera.data.database.MediaEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ViewerUiState {
    object Loading : ViewerUiState()
    data class Success(val media: MediaEntity) : ViewerUiState()
    object NotFound : ViewerUiState()
}

class MediaViewerViewModel(
    application: Application,
    private val mediaId: Int
) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val mediaDao = database.mediaDao()

    private val _uiState = MutableStateFlow<ViewerUiState>(ViewerUiState.Loading)
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    init {
        loadMedia()
    }

    private fun loadMedia() {
        viewModelScope.launch {
            val media = mediaDao.getMediaById(mediaId)
            _uiState.value = if (media != null) {
                ViewerUiState.Success(media)
            } else {
                ViewerUiState.NotFound
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
class MediaViewerViewModelFactory(
    private val application: Application,
    private val mediaId: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MediaViewerViewModel(application, mediaId) as T
    }
}