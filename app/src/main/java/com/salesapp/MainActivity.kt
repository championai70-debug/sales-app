package com.salesapp

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * The app is the same page as the browser version (web/index.html), bundled inside the
 * APK so it works offline. Data stays on the phone in the page's local storage.
 */
class MainActivity : ComponentActivity() {
    private lateinit var web: WebView
    private var pendingFile: ValueCallback<Array<Uri>>? = null

    private val pickFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingFile?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        pendingFile = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                pendingFile?.onReceiveValue(null)
                pendingFile = callback
                pickFile.launch("*/*")
                return true
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })
        // Keep the page clear of the status bar and the navigation bar.
        ViewCompat.setOnApplyWindowInsetsListener(web) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setContentView(web)
        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        if (web.url == null) {
            // index.html is page content only (the web host adds the document around it), so wrap it here.
            val page = assets.open("index.html").bufferedReader().use { it.readText() }
            web.loadDataWithBaseURL(ORIGIN, SHELL_START + page + SHELL_END, "text/html", "utf-8", null)
        }
    }

    private companion object {
        const val ORIGIN = "https://sales-app.local/"
        const val SHELL_START = "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
            "<style>body{margin:0}img{max-width:100%}[hidden]{display:none!important}</style></head><body>"
        const val SHELL_END = "</body></html>"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }
}
