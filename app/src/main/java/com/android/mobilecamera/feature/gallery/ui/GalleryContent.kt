package com.android.mobilecamera.feature.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.mobilecamera.data.database.MediaEntity
import com.android.mobilecamera.feature.gallery.MediaItem

@Composable
fun GalleryContent(
    mediaList: List<MediaEntity>,
    onClearClick: () -> Unit,
    onAddMockClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp,
                start = 2.dp,
                end = 2.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(mediaList, key = { it.id }) { item ->
                MediaItem(item)
            }
        }

        if (mediaList.isEmpty()) {
            Text(
                text = "Галерея пуста...\nНажми + для теста",
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.Center),
                textAlign = TextAlign.Center
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
        )

        FloatingActionButton(
            onClick = onClearClick,
            containerColor = Color(0xFFF44336),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Очистить")
        }

        FloatingActionButton(
            onClick = onAddMockClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Icon(Icons.Default.Add, contentDescription = "Добавить")
        }
    }
}