package com.musicsquare.player.visual

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.sceneview.SceneView
import io.github.sceneview.node.ModelNode

/**
 * Actual 3D glTF model, rendered by Filament via Apache-2.0 SceneView.
 * The original model in assets has articulated mesh nodes and a looping animation.
 * This replaces the old 2D Canvas character.
 */
@Composable
fun DancingCompanion(playing: Boolean, onDismiss: () -> Unit) {
    var modelNode by remember { mutableStateOf<ModelNode?>(null) }
    Box(
        Modifier.width(170.dp).height(270.dp)
            .background(Color(0xFF171329), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context ->
                SceneView(context).apply {
                    val instance = modelLoader.createModelInstance("models/dancer.gltf")
                    val dancer = ModelNode(
                        modelInstance = instance,
                        autoAnimate = false,
                        scaleToUnits = 1.8f
                    )
                    addChildNode(dancer)
                    modelNode = dancer
                }
            },
            modifier = Modifier.width(170.dp).height(270.dp)
        )
        LaunchedEffect(playing, modelNode) {
            modelNode?.let { dancer ->
                if (playing) dancer.playAnimation("DanceLoop", loop = true)
                else dancer.stopAnimation("DanceLoop")
            }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
            Text("×", color = Color.White, fontSize = 24.sp)
        }
    }
}
