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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import br.com.jotdown.R

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
    isDarkMode: Boolean,
    onToggleDarkMode: () -> Unit,
    onSharePdf: () -> Unit,
    onOutlineClick: () -> Unit,
) {
    var showOverflow by remember { mutableStateOf(false) }

    if (isSearchActive) {
        // ── Barra de busca ─────────────────────────────────────────────
        TopAppBar(
            title = {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text(stringResource(R.string.readerbar_search_placeholder)) },
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.lib_close_search))
                }
            },
            actions = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.readerbar_clear_search))
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
            },
            actions = {
                // Busca
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.common_search))
                }
                // Metadados
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.MenuBook, contentDescription = stringResource(R.string.readerbar_metadata))
                }
                // Modo Escuro
                IconButton(onClick = onToggleDarkMode) {
                    Icon(if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode, contentDescription = stringResource(R.string.readerbar_night_mode))
                }
                // Tela cheia
                IconButton(onClick = onToggleFullscreen) {
                    Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, contentDescription = stringResource(R.string.readerbar_fullscreen))
                }
                // Anotações
                IconButton(onClick = onAnnotations) {
                    BadgedBox(
                        badge = { if (annotationCount > 0) Badge { Text(annotationCount.toString()) } }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Message, contentDescription = stringResource(R.string.readerbar_annotations))
                    }
                }
                // Overflow menu
                IconButton(onClick = { showOverflow = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_menu))
                }
                DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.readerbar_book_outline), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                        onClick = { showOverflow = false; onOutlineClick() },
                        leadingIcon = { Icon(Icons.Default.FormatListBulleted, null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.abnt_citation_title)) },
                        leadingIcon = { Icon(Icons.Default.FormatQuote, contentDescription = null) },
                        onClick = { showOverflow = false; onAbntClick() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export_markdown_chooser)) },
                        onClick = { showOverflow = false; onExportClick() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.readerbar_share_original_pdf)) },
                        onClick = { showOverflow = false; onSharePdf() }
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            )
        )
    }
}
