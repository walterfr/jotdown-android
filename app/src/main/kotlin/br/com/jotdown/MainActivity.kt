package br.com.jotdown

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import br.com.jotdown.ui.JotdownApp
import br.com.jotdown.ui.theme.JotdownTheme
import br.com.jotdown.util.LocalStylusPredictor
import br.com.jotdown.util.QuickNoteUtil
import br.com.jotdown.util.StylusMotionPredictor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    private var stylusPredictor: StylusMotionPredictor? = null
    private val pendingOpenDocId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val predictor = StylusMotionPredictor(window.decorView)
        stylusPredictor = predictor
        if (savedInstanceState == null) {
            handleNoteIntent(intent)
        }
        setContent {
            CompositionLocalProvider(LocalStylusPredictor provides predictor) {
                JotdownTheme {
                    JotdownApp(
                        initialDocumentId = pendingOpenDocId.value,
                        onInitialDocumentConsumed = { pendingOpenDocId.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNoteIntent(intent)
    }

    private fun handleNoteIntent(intent: Intent?) {
        if (intent?.action != ACTION_CREATE_NOTE) return
        val app = application as JotdownApplication
        val defaultTitle = getString(R.string.default_notebook_title)
        lifecycleScope.launch(Dispatchers.IO) {
            val doc = QuickNoteUtil.createNotebook(
                context = this@MainActivity,
                repository = app.repository,
                title = defaultTitle,
                template = "Pautado"
            )
            if (doc != null) {
                withContext(Dispatchers.Main) {
                    pendingOpenDocId.value = doc.id
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        stylusPredictor?.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    companion object {
        const val ACTION_CREATE_NOTE = "android.intent.action.CREATE_NOTE"
    }
}
