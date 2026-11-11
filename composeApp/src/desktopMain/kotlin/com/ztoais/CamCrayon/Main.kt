package com.ztoais.CamCrayon

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.Alignment

fun main() = application {
    val windowState = rememberWindowState(
        size = DpSize(1240.dp, 820.dp),
        position = WindowPosition(Alignment.Center)
    )
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "CamCrayon ✨ Turn Kids' Camera Drawings into Living Animated Stories"
    ) {
        App()
    }
}
