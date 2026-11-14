package app.cadence

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.cadence.platform.Platform
import app.cadence.ui.theme.Accent
import app.cadence.ui.theme.Bg0
import app.cadence.ui.theme.CadenceTheme
import app.cadence.ui.theme.TextPrimary
import app.cadence.ui.theme.TextSecondary

class YouTubeSignInActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CadenceTheme {
                SignInScreen(
                    onDone = { finishWith(readCookies()) },
                    onCancel = { finishWith(null) },
                )
            }
        }
    }

    private fun readCookies(): String? {
        val cm = CookieManager.getInstance()
        val domains = listOf(
            "https://music.youtube.com",
            "https://www.youtube.com",
            "https://youtube.com",
            "https://accounts.google.com",
        )
        val all = linkedSetOf<String>()
        domains.forEach { url ->
            cm.getCookie(url)?.split(';')?.forEach { part ->
                val c = part.trim()
                if (c.isNotEmpty()) all += c
            }
        }
        val cookie = all.joinToString("; ")
        return cookie.takeIf { it.isNotBlank() }
    }

    private fun finishWith(cookie: String?) {
        if (cookie != null) {
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_COOKIE, cookie))
        } else {
            setResult(Activity.RESULT_CANCELED)
        }
        Platform.deliverSignIn(cookie)
        finish()
    }

    companion object {
        const val EXTRA_COOKIE = "cookie"
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SignInScreen(onDone: () -> Unit, onCancel: () -> Unit) {
    val loading = remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().background(Bg0)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Sign in to YouTube Music", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    "Sign in, then tap Done to save your session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            Row {
                TextButton(onClick = onCancel) { Text("Cancel", color = TextSecondary) }
                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) { Text("Done", color = Color.Black) }
            }
        }
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading.value = false
                            }
                        }
                        webChromeClient = WebChromeClient()
                        loadUrl("https://accounts.google.com/ServiceLogin?service=youtube&continue=https://music.youtube.com")
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
