package app.cadence

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import app.cadence.platform.Platform
import app.cadence.ui.App

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        Platform.deliverPermission(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Platform.attach(applicationContext)
        Platform.permissionLauncher = { permission -> permissionLauncher.launch(permission) }
        enableEdgeToEdge()
        setContent { App() }
    }
}
