package com.android.mobilecamera.feature.viewer.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun PhotoViewer(path: String) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(path.toUri())
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Inside,
        modifier = Modifier.fillMaxSize()
    )
}