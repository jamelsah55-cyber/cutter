package com.cutterpro.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.cutterpro.desktop.state.DesktopStudioController
import com.cutterpro.desktop.ui.DesktopApp

fun main() = application {
    val controller = remember { DesktopStudioController() }
    val windowState = rememberWindowState(width = 1280.dp, height = 820.dp)

    Window(
        onCloseRequest = {
            controller.transport.disconnect()
            exitApplication()
        },
        state = windowState,
        title = "كتر برو - CutterPro Desktop",
        onKeyEvent = { event ->
            if (event.type == KeyEventType.KeyDown) {
                when {
                    event.isCtrlPressed && event.key == Key.Z -> {
                        controller.undo()
                        true
                    }
                    event.isCtrlPressed && event.key == Key.Y -> {
                        controller.redo()
                        true
                    }
                    event.key == Key.Delete -> {
                        controller.deleteSelected()
                        true
                    }
                    else -> false
                }
            } else false
        }
    ) {
        DesktopApp(controller)
    }
}
