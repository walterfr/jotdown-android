package br.com.jotdown.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue

/**
 * TopBar do leitor, expandida com:
 * - Modo tela cheia (B3)
 * - Busca de texto (I1)
 * - Citação ABNT (A3)
 * - Exportar para Markdown (I4)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    annotationCount: Int,
    isFullscreen: Boolean,
    isSearchActive: Boolean,
    searchQuery: String,
    onBack: () -> Unit,
    onMenuClick: () -> Unit,
    onAnnotations: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onAbntClick: () -> Unit,
    onExportClick: () -> Unit,
) {
    var showOverflow by remember { mutableStateOf(false) }

    if (isSearchActive) {
        // ── Barra de busca ─────────────────────────────────────────────
        TopAppBar(
            title = {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Buscar em anotações e fichamentos…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedContainerColor   = androidx.compose.ui.graphics.Color.Transparent,
                    )
                )
            },
            navigationIcon = {
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Fechar busca")
                }
            },
            actions = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpar busca")
                    }
                }
            }
        )
    } else {
        // ── TopBar normal ───────────────────────────────────────────────
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }
            },
            actions = {
                // Busca
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Buscar")
                }
                // Anotações
                IconButton(onClick = onAnnotations) {
                    BadgedBox(
                        badge = { if (annotationCount > 0) Badge { Text(annotationCount.toString()) } }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "Anotações")
                    }
                }
                // Tela cheia
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "Sair da tela cheia" else "Tela cheia"
                    )
                }
                // Overflow menu
                IconButton(onClick = { showOverflow = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                    DropdownMenuItem(
                        text = { Text("Citação ABNT") },
                        leadingIcon = { Icon(Icons.Default.FormatQuote, contentDescription = null) },
                        onClick = { showOverflow = false; onAbntClick() }
                    )
                    DropdownMenuItem(
                        text = { Text("Exportar para Markdown") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = { showOverflow = false; onExportClick() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Mais opções") },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        onClick = { showOverflow = false; onMenuClick() }
                    )
                }
            }
        )
    }
}