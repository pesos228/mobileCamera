package com.android.mobilecamera.fragments.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.android.mobilecamera.data.database.AppDatabase
import com.android.mobilecamera.data.database.MediaType
import com.android.mobilecamera.data.repository.MediaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = MediaRepository(database.mediaDao())

    val mediaList = repository.allMedia
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun createMocks() {
        viewModelScope.launch {
            for (i in 1..12) {
                val isVideo = i % 3 == 0
                repository.saveMedia(
                    path = "mock_$i",
                    type = if (isVideo) MediaType.VIDEO else MediaType.PHOTO
                )
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }
}

class GalleryViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GalleryViewModel(application) as T
    }
}