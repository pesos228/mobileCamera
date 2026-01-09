package com.android.mobilecamera.feature.camera.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun CaptureButton(
    isRecording: Boolean,
    isVideoMode: Boolean,
    onClick: () -> Unit
) {
    val color by animateColorAsState(
        if (isVideoMode) Color.Red else Color.White,
        label = "color"
    )
    val outerScale by animateFloatAsState(
        if (isRecording) 1.2f else 1f,
        label = "scale"
    )
    val innerShape = if (isRecording) MaterialTheme.shapes.small else CircleShape
    val innerSize = if (isRecording) 30.dp else 60.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.scale(outerScale)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .border(4.dp, Color.White, CircleShape)
        )
        Button(
            onClick = onClick,
            shape = innerShape,
            colors = ButtonDefaults.buttonColors(containerColor = color),
            modifier = Modifier.size(innerSize),
            contentPadding = PaddingValues(0.dp)
        ) {}
    }
}