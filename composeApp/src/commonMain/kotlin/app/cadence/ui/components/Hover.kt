package app.cadence.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Adds a background tint while the pointer is over this element.
 *
 * Compose's `collectIsHoveredAsState` only exists on desktop, so this works
 * from common code and is simply inert on touch-only devices.
 */
fun Modifier.hoverBackground(color: Color): Modifier = composed {
    var hovered by remember { mutableStateOf(false) }
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                when (awaitPointerEvent(PointerEventPass.Initial).type) {
                    PointerEventType.Enter -> hovered = true
                    PointerEventType.Exit -> hovered = false
                    else -> Unit
                }
            }
        }
    }.background(if (hovered) color else Color.Transparent)
}
