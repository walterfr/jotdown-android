package br.com.jotdown

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    companion object {
        private const val FILE_CHOOSER_REQUEST = 1001
    }

    @Suppress("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        // ── Configurações do WebView ─────────────────────────────────────────
        with(webView.settings) {
            javaScriptEnabled        = true
            domStorageEnabled        = true
            databaseEnabled          = true
            allowFileAccess          = true
            allowContentAccess       = true
            // Estas duas linhas são CRÍTICAS para carregar scripts locais (libs/)
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            cacheMode                = WebSettings.LOAD_NO_CACHE
            mediaPlaybackRequiresUserGesture = false
            // Permite carregar o FontAwesome via HTTPS enquanto o resto é local
            mixedContentMode         = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        // ── WebViewClient: intercepta erros de carregamento ──────────────────
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                android.util.Log.d("Jotdown/JS", "Página iniciada: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                android.util.Log.d("Jotdown/JS", "Página finalizada: $url")
                // Força a execução de um log para testar a comunicação
                view?.evaluateJavascript("console.log('Jotdown/JS: WebView injetou log com sucesso');", null)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                return if (url.startsWith("file://") || url.startsWith("about:") || url.contains("android_asset")) {
                    false
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        android.util.Log.e("Jotdown/JS", "Erro ao abrir link externo", e)
                    }
                    true
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                val msg = "Erro no WebView: ${error.errorCode} - ${error.description} (URL: ${request.url})"
                android.util.Log.e("Jotdown/JS", msg)
                if (request.isForMainFrame) {
                    Toast.makeText(this@MainActivity, "Erro ao carregar: ${error.description}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // ── WebChromeClient: habilita o seletor de arquivos (PDF picker) ─────
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                // Cancela qualquer seletor anterior pendente
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback

                val intent = params.createIntent()
                return try {
                    startActivityForResult(intent, FILE_CHOOSER_REQUEST)
                    true
                } catch (e: Exception) {
                    filePathCallback = null
                    Toast.makeText(
                        this@MainActivity,
                        "Não foi possível abrir o seletor de arquivos",
                        Toast.LENGTH_SHORT
                    ).show()
                    false
                }
            }

            // Log de erro forçado para ajudar a identificar a tela branca
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                val logMsg = "${msg.message()} — ${msg.sourceId()}:${msg.lineNumber()}"
                android.util.Log.e("Jotdown/JS", logMsg)
                return true
            }
        }

        // ── Carrega o app a partir dos assets locais ─────────────────────────
        // O Service Worker não funciona em WebView, mas o IndexedDB sim.
        // Todos os assets (libs/, css/, webfonts/) estão em app/src/main/assets/
        webView.loadUrl("file:///android_asset/index.html")
    }

    // ── Resultado do seletor de arquivos ─────────────────────────────────────
    @Deprecated("Needed for API < 30 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_CHOOSER_REQUEST) {
            filePathCallback?.onReceiveValue(
                if (resultCode == Activity.RESULT_OK)
                    WebChromeClient.FileChooserParams.parseResult(resultCode, data)
                else
                    null
            )
            filePathCallback = null
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    // ── Botão voltar: navega no histórico do WebView ─────────────────────────
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
