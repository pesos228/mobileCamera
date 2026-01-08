package com.android.mobilecamera.feature.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.mobilecamera.feature.gallery.GalleryUiState
import com.android.mobilecamera.feature.gallery.groupByDate
import com.android.mobilecamera.feature.gallery.ui.components.MediaItemWithSelection
import com.android.mobilecamera.feature.gallery.ui.components.SelectionTopBar
import com.android.mobilecamera.feature.gallery.ui.components.SyncProgressDialog
import com.android.mobilecamera.R

@Composable
fun GalleryContent(
    uiState: GalleryUiState,
    onMediaClick: (Int) -> Unit,
    onMediaLongClick: (Int) -> Unit,
    onClearClick: () -> Unit,
    onAddMockClick: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClearSelection: () -> Unit,
) {
    if (uiState.isSyncing) {
        val (current, total) = uiState.syncProgress ?: (0 to 0)
        SyncProgressDialog(
            current = current,
            total = total
        )
    }

    BackHandler(enabled = uiState.isSelectionMode) {
        onClearSelection()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val groupedMedia = uiState.mediaList.groupByDate()

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = if (uiState.isSelectionMode)
                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 56.dp
                else
                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp,
                start = 2.dp,
                end = 2.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            groupedMedia.forEach { group ->
                item(
                    key = "header_${group.date}",
                    span = { GridItemSpan(3) }
                ) {
                    Text(
                        text = group.date,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                }

                items(
                    items = group.items,
                    key = { it.id }
                ) { item ->
                    Box(
                        modifier = Modifier
                            .animateItem()
                            .combinedClickable(
                                onClick = { onMediaClick(item.id) },
                                onLongClick = { onMediaLongClick(item.id) }
                            )
                    ) {
                        MediaItemWithSelection(
                            item = item,
                            isSelected = item.id in uiState.selectedItems,
                            isSelectionMode = uiState.isSelectionMode
                        )
                    }
                }
            }
        }

        if (uiState.mediaList.isEmpty() && !uiState.isSyncing) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.gallery_empty_title),
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.gallery_empty_hint),
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
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

        if (uiState.isSelectionMode) {
            SelectionTopBar(
                selectedCount = uiState.selectedItems.size,
                onClearSelection = onClearSelection,
                onDeleteSelected = onDeleteSelected,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
            )
        }

        if (!uiState.isSelectionMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FloatingActionButton(
                    onClick = onClearClick,
                    containerColor = Color(0xFFF44336),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_clear_all))
                }

                FloatingActionButton(
                    onClick = onAddMockClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_mocks))
                }
            }
        }
    }
}

