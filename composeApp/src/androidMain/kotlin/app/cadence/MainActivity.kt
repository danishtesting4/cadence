package app.cadence

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import app.cadence.platform.Platform
import app.cadence.ui.App

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        Platform.deliverPermission(granted)
    }

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val cookie = if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra(YouTubeSignInActivity.EXTRA_COOKIE)
        } else null
        Platform.deliverSignIn(cookie)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Platform.attach(applicationContext)
        Platform.permissionLauncher = { permission -> permissionLauncher.launch(permission) }
        Platform.signInLauncher = {
            signInLauncher.launch(Intent(this, YouTubeSignInActivity::class.java))
        }
        enableEdgeToEdge()
        setContent { App() }
    }
}
