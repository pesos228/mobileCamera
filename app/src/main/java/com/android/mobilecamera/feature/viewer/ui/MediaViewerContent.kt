package com.android.mobilecamera.feature.viewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.android.mobilecamera.R
import com.android.mobilecamera.data.database.MediaType
import com.android.mobilecamera.feature.viewer.ViewerUiState
import com.android.mobilecamera.feature.viewer.ui.components.MockViewer
import com.android.mobilecamera.feature.viewer.ui.components.PhotoViewer
import com.android.mobilecamera.feature.viewer.ui.components.VideoPlayer

@Composable
fun MediaViewerContent(
    uiState: ViewerUiState,
) {
    var isVideoReady by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when (uiState) {
            is ViewerUiState.Loading -> {
                CircularProgressIndicator(color = Color.White)
            }
            is ViewerUiState.Success -> {
                val item = uiState.media
                if (item.path.startsWith("mock_")) {
                    MockViewer(item)
                } else {
                    when (item.type) {
                        MediaType.PHOTO -> PhotoViewer(item.path)
                        MediaType.VIDEO -> {
                            VideoPlayer(
                                path = item.path,
                                onReady = { isVideoReady = true }
                            )

                            if (!isVideoReady) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                    }
                }
            }
            is ViewerUiState.NotFound -> {
                Text(
                    text = stringResource(R.string.viewer_file_not_found),
                    color = Color.White
                )
            }
        }
    }
}