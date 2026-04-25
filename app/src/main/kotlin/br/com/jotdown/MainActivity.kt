package br.com.jotdown

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.com.jotdown.ui.JotdownApp
import br.com.jotdown.ui.theme.JotdownTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JotdownTheme {
                JotdownApp()
            }
        }
    }
}
