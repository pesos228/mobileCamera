package com.android.mobilecamera.feature.viewer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.android.mobilecamera.data.database.MediaEntity
import com.android.mobilecamera.data.database.MediaType
import kotlin.random.Random
import androidx.compose.ui.res.stringResource
import com.android.mobilecamera.R

@Composable
fun MockViewer(item: MediaEntity) {
    val color = remember(item.id) {
        val rnd = Random(item.id)
        Color(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))
    }

    val titleResId = if (item.type == MediaType.VIDEO) {
        R.string.mock_video_label
    } else {
        R.string.mock_photo_label
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(titleResId),
                style = MaterialTheme.typography.displayMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.mock_item_id, item.id),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}