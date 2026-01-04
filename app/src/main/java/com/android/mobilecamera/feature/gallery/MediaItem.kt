package com.android.mobilecamera.feature.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
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
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.android.mobilecamera.data.database.MediaEntity
import com.android.mobilecamera.data.database.MediaType
import java.io.File
import kotlin.random.Random

@Composable
fun MediaItem(item: MediaEntity) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(Color.DarkGray)
    ) {
        if (item.path.startsWith("mock_")) {
            val color = remember(item.id) {
                val rnd = Random(item.id)
                Color(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
            }
            Box(modifier = Modifier.fillMaxSize().background(color))
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(item.path))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (item.type == MediaType.VIDEO) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
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
    }
}

fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}