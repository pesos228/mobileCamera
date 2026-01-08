package com.android.mobilecamera.feature.gallery.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.android.mobilecamera.R
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.size.Precision
import coil.size.Size
import com.android.mobilecamera.data.database.MediaEntity
import com.android.mobilecamera.data.database.MediaType
import com.android.mobilecamera.feature.gallery.ImageLoaderProvider
import java.io.File
import kotlin.random.Random

@Composable
fun MediaItemWithSelection(
    item: MediaEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean
) {
    val context = LocalContext.current
    val imageLoader = remember { ImageLoaderProvider.get(context) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Color.DarkGray)
    ) {
        // === 1. МОКИ ===
        if (item.path.startsWith("mock_")) {
            val color = remember(item.id) {
                val rnd = Random(item.id)
                Color(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
            }
            Box(modifier = Modifier.fillMaxSize().background(color))
        } else {
            // === 2. ЗАГРУЗКА ===
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(
                        if (item.thumbnailPath != null) {
                            File(item.thumbnailPath).toUri()
                        } else {
                            item.path.toUri()
                        }
                    )
                    .apply {
                        if (item.type == MediaType.VIDEO && item.thumbnailPath == null) {
                            videoFrameMillis(1000)
                        }
                    }
                    .size(Size(300, 300))
                    .precision(Precision.EXACT)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .crossfade(false)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Blue.copy(alpha = 0.3f))
            )
        }

        if (item.type == MediaType.VIDEO) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = stringResource(R.string.cd_media_type_video),
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(24.dp)
            )

            item.duration?.let { durationMillis ->
                Text(
                    text = formatDuration(durationMillis),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        val selectionDesc = if (isSelected) {
            stringResource(R.string.cd_state_selected)
        } else {
            stringResource(R.string.cd_state_not_selected)
        }

        AnimatedVisibility(
            visible = isSelectionMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = selectionDesc,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .padding(2.dp)
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(3.dp, MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@SuppressLint("DefaultLocale")
private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}