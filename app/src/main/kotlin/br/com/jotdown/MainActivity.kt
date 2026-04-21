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
            javaScriptEnabled        = true   // Necessário para o app HTML/JS
            domStorageEnabled        = true   // localStorage
            databaseEnabled          = true   // IndexedDB (salva os PDFs)
            allowFileAccess          = true   // Acesso aos assets locais
            allowContentAccess       = true
            cacheMode                = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            // Bloqueia conteúdo misto HTTP dentro de HTTPS
            mixedContentMode         = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        // ── WebViewClient: intercepta erros de carregamento ──────────────────
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                // Mantém navegação interna no WebView;
                // links externos abrem no browser do sistema
                val url = request.url.toString()
                return if (url.startsWith("file://") || url.startsWith("about:")) {
                    false
                } else {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    true
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    Toast.makeText(
                        this@MainActivity,
                        "Erro: ${error.description}",
                        Toast.LENGTH_LONG
                    ).show()
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

            // Permite que o JS use console.log() (útil durante o desenvolvimento)
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                if (BuildConfig.DEBUG) {
                    android.util.Log.d(
                        "Jotdown/JS",
                        "${msg.message()} — ${msg.sourceId()}:${msg.lineNumber()}"
                    )
                }
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
