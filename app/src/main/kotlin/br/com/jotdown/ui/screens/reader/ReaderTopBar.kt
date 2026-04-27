package br.com.jotdown.ui.screens.reader

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*

/**
 * TopBar simplificada do leitor.
 *
 * As ferramentas de desenho e undo/redo foram movidas para [ReaderToolsBar]
 * (barra flutuante inferior). Esta TopBar cuida apenas de: voltar, anotações e menu lateral.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    annotationCount: Int,
    onBack: () -> Unit,
    onMenuClick: () -> Unit,
    onAnnotations: () -> Unit,
) {
    TopAppBar(
        title = { /* título pode ser adicionado futuramente (nome do documento) */ },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
        },
        actions = {
            IconButton(onClick = onAnnotations) {
                BadgedBox(
                    badge = { if (annotationCount > 0) Badge { Text(annotationCount.toString()) } }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "Anotações")
                }
            }
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menu")
            }
        },
    )
}