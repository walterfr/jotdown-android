package br.com.jotdown

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.jotdown.data.entity.DocumentEntity
import br.com.jotdown.ui.screens.reader.ReaderScreen
import br.com.jotdown.ui.theme.JotdownTheme
import br.com.jotdown.ui.viewmodel.ReaderViewModel
import br.com.jotdown.ui.viewmodel.ReaderViewModelFactory
import br.com.jotdown.util.BiometricLockUtil
import br.com.jotdown.util.LocalStylusPredictor
import br.com.jotdown.util.StylusMotionPredictor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Secondary reader activity launched via FLAG_ACTIVITY_LAUNCH_ADJACENT for Android split-screen.
 */
class MultiReaderActivity : FragmentActivity() {
    private var stylusPredictor: StylusMotionPredictor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val documentId = intent?.getStringExtra(EXTRA_DOCUMENT_ID)
        if (documentId.isNullOrBlank()) {
            finish()
            return
        }

        val app = application as JotdownApplication
        val predictor = StylusMotionPredictor(window.decorView)
        stylusPredictor = predictor

        setContent {
            CompositionLocalProvider(LocalStylusPredictor provides predictor) {
                JotdownTheme {
                    var loadedDoc by remember { mutableStateOf<DocumentEntity?>(null) }
                    var loadDone by remember { mutableStateOf(false) }
                    var unlocked by remember { mutableStateOf(false) }
                    var authError by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(documentId) {
                        val doc = withContext(Dispatchers.IO) {
                            app.repository.getDocumentById(documentId)
                        }
                        loadedDoc = doc
                        loadDone = true
                        if (doc != null && !doc.isLocked) {
                            unlocked = true
                        }
                    }

                    val doc = loadedDoc
                    if (!loadDone) {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (doc == null) {
                        LaunchedEffect(Unit) { finish() }
                    } else if (doc.isLocked && !unlocked) {
                        val docTitle = doc.title.ifBlank { doc.fileName }
                        val promptTitle = stringResource(R.string.biometric_prompt_title)
                        val promptSubtitle = stringResource(R.string.biometric_prompt_subtitle, docTitle)

                        fun triggerAuth() {
                            authError = null
                            BiometricLockUtil.authenticate(
                                activity = this@MultiReaderActivity,
                                title = promptTitle,
                                subtitle = promptSubtitle,
                                onSuccess = { unlocked = true },
                                onError = { msg -> authError = msg.takeIf { it.isNotBlank() } }
                            )
                        }

                        LaunchedEffect(doc.id) {
                            triggerAuth()
                        }

                        Surface(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = docTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.biometric_locked_screen_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                if (authError != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = authError ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                Spacer(Modifier.height(20.dp))
                                Button(onClick = { triggerAuth() }) {
                                    Text(stringResource(R.string.biometric_unlock_button))
                                }
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = { finish() }) {
                                    Text(stringResource(R.string.common_close))
                                }
                            }
                        }
                    } else {
                        val vm: ReaderViewModel = viewModel(
                            factory = ReaderViewModelFactory(app.repository, documentId, app.dictionaryRepository)
                        )
                        ReaderScreen(
                            viewModel = vm,
                            onBack = { finish() }
                        )
                    }
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        stylusPredictor?.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    companion object {
        const val EXTRA_DOCUMENT_ID = "document_id"

        fun launchAdjacent(context: Context, documentId: String) {
            val intent = Intent(context, MultiReaderActivity::class.java).apply {
                putExtra(EXTRA_DOCUMENT_ID, documentId)
                addFlags(
                    Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                )
            }
            context.startActivity(intent)
        }
    }
}
