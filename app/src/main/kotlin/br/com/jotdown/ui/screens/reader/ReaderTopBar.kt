package br.com.jotdown.ui.screens.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.jotdown.R
import br.com.jotdown.util.ReadingThemeSpec

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
    readingTheme: String,
    onOpenAppearance: () -> Unit,
    onSharePdf: () -> Unit,
    onOutlineClick: () -> Unit,
    onExportAnnotatedPdf: () -> Unit = {},
    onPagePaddingClick: () -> Unit = {},
    isCurrentPageBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    onOpenPageGrid: () -> Unit = {},
    isAudioBarVisible: Boolean = false,
    isRecordingAudio: Boolean = false,
    onToggleAudioBar: () -> Unit = {},
    onOpenSplitScreen: () -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
) {
    var showOverflow by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
        if (isSearchActive) {
            // ── Barra de busca ─────────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(windowInsets)
                        .height(50.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleSearch,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.lib_close_search),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.readerbar_search_placeholder),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(R.string.readerbar_clear_search),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // ── TopBar normal ───────────────────────────────────────────────
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(windowInsets)
                        .height(50.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // Busca
                    IconButton(
                        onClick = onToggleSearch,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.common_search),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    // Metadados
                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = stringResource(R.string.readerbar_metadata),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    // Marcador de página
                    IconButton(
                        onClick = onToggleBookmark,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = stringResource(R.string.readerbar_bookmark_page),
                            tint = if (isCurrentPageBookmarked) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    // Aparência & Layout
                    IconButton(
                        onClick = onOpenAppearance,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (readingTheme == ReadingThemeSpec.THEME_DARK) Icons.Default.LightMode else Icons.Default.Palette,
                            contentDescription = stringResource(R.string.readerbar_appearance),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    // Notas de Áudio
                    IconButton(
                        onClick = onToggleAudioBar,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = stringResource(R.string.readerbar_audio_notes),
                            tint = when {
                                isRecordingAudio -> androidx.compose.ui.graphics.Color(0xFFDC2626)
                                isAudioBarVisible -> MaterialTheme.colorScheme.primary
                                else -> LocalContentColor.current
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    // Tela cheia
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = stringResource(R.string.readerbar_fullscreen),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    // Anotações
                    IconButton(
                        onClick = onAnnotations,
                        modifier = Modifier.size(40.dp)
                    ) {
                        BadgedBox(
                            badge = { if (annotationCount > 0) Badge { Text(annotationCount.toString()) } }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Message,
                                contentDescription = stringResource(R.string.readerbar_annotations),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    // Overflow menu
                    Box {
                        IconButton(
                            onClick = { showOverflow = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.common_menu),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.readerbar_book_outline), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                                onClick = { showOverflow = false; onOutlineClick() },
                                leadingIcon = { Icon(Icons.Default.FormatListBulleted, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.readerbar_page_grid)) },
                                leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null) },
                                onClick = { showOverflow = false; onOpenPageGrid() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.readerbar_open_split_screen)) },
                                leadingIcon = { Icon(Icons.Default.VerticalSplit, contentDescription = null) },
                                onClick = { showOverflow = false; onOpenSplitScreen() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.readerbar_page_padding)) },
                                leadingIcon = { Icon(Icons.Default.AspectRatio, contentDescription = null) },
                                onClick = { showOverflow = false; onPagePaddingClick() }
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
                                text = { Text(stringResource(R.string.readerbar_share_annotated_pdf)) },
                                onClick = { showOverflow = false; onExportAnnotatedPdf() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.readerbar_share_original_pdf)) },
                                onClick = { showOverflow = false; onSharePdf() }
                            )
                        }
                    }
                }
            }
        }
    }
}
