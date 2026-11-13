package app.cadence

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.cadence.ui.App
import java.awt.Dimension

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Cadence",
        state = rememberWindowState(width = 1240.dp, height = 820.dp),
    ) {
        window.minimumSize = Dimension(900, 640)
        App()
    }
}
