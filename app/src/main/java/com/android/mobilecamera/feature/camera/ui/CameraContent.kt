package com.android.mobilecamera.feature.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun CameraScreenContent(
    hasPermission: Boolean,
    showRationaleDialog: Boolean,
    onPermissionRequest: () -> Unit,
    onRationaleDismiss: () -> Unit,
    onRationaleConfirm: () -> Unit,
    onNavigateToGallery: () -> Unit
) {
    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = onRationaleDismiss,
            title = { Text("Нужен доступ") },
            text = { Text("Дай доступ к камере, пожалуйста.") },
            confirmButton = { Button(onClick = onRationaleConfirm) { Text("ОК") } },
            dismissButton = { TextButton(onClick = onRationaleDismiss) { Text("Нет") } }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasPermission) {
            Text(
                text = "ТУТ БУДЕТ КАМЕРА",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Button(
                onClick = onPermissionRequest,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text("Дать доступ к камере")
            }
        }

        Button(
            onClick = onNavigateToGallery,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(32.dp)
                .navigationBarsPadding()
        ) {
            Text("В Галерею")
        }
    }
}