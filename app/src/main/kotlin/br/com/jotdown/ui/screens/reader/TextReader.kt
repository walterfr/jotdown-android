package br.com.jotdown.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.jotdown.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextReader(
    filePath: String,
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var content by remember { mutableStateOf(context.getString(R.string.txt_loading)) }
    var fontSize by remember { mutableStateOf(18f) }

    LaunchedEffect(filePath) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (file.exists()) {
                    content = file.readText()
                } else {
                    content = context.getString(R.string.txt_file_not_found)
                }
            } catch (e: Exception) {
                content = context.getString(R.string.txt_read_error, e.message ?: "")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { if (fontSize > 12f) fontSize -= 2f }) {
                        Icon(Icons.Default.ZoomOut, contentDescription = stringResource(R.string.txt_decrease_font))
                    }
                    IconButton(onClick = { if (fontSize < 48f) fontSize += 2f }) {
                        Icon(Icons.Default.ZoomIn, contentDescription = stringResource(R.string.txt_increase_font))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val newSize = fontSize * zoom
                        if (newSize in 12f..48f) {
                            fontSize = newSize
                        }
                    }
                }
        ) {
            Text(
                text = content,
                fontSize = fontSize.sp,
                fontFamily = FontFamily.Serif,
                lineHeight = (fontSize * 1.5f).sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            )
        }
    }
}
