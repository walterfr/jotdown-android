package br.com.jotdown.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.jotdown.R
import br.com.jotdown.ui.theme.Indigo600
import br.com.jotdown.util.PaperTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageThumbnailsSheet(
    numPages: Int,
    currentPage: Int,
    pageOffset: Int,
    bookmarkedPages: Set<Int>,
    pdfRenderer: PdfRenderer?,
    renderMutex: Mutex,
    isInsertingPage: Boolean,
    onSelectPage: (Int) -> Unit,
    onInsertPageClick: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.page_grid_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Indigo600
                )
                FilledTonalButton(
                    onClick = onInsertPageClick,
                    enabled = !isInsertingPage
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.page_insert_button))
                }
            }

            if (isInsertingPage) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 480.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(numPages) { index ->
                    val pageNumber = index + 1
                    PageThumbnailCard(
                        pageNumber = pageNumber,
                        printedPageNumber = pageNumber + pageOffset,
                        isCurrent = pageNumber == currentPage,
                        isBookmarked = pageNumber in bookmarkedPages,
                        pdfRenderer = pdfRenderer,
                        renderMutex = renderMutex,
                        onClick = {
                            onSelectPage(pageNumber)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PageThumbnailCard(
    pageNumber: Int,
    printedPageNumber: Int,
    isCurrent: Boolean,
    isBookmarked: Boolean,
    pdfRenderer: PdfRenderer?,
    renderMutex: Mutex,
    onClick: () -> Unit
) {
    var thumbBitmap by remember { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            thumbBitmap?.recycle()
            thumbBitmap = null
        }
    }

    LaunchedEffect(pageNumber, pdfRenderer) {
        val renderer = pdfRenderer ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                renderMutex.withLock {
                    if (pageNumber in 1..renderer.pageCount) {
                        val page = renderer.openPage(pageNumber - 1)
                        val bmp = try {
                            val targetW = 180
                            val scale = targetW.toFloat() / page.width.coerceAtLeast(1)
                            val targetH = (page.height * scale).roundToInt().coerceAtLeast(1)
                            Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888).apply {
                                eraseColor(android.graphics.Color.WHITE)
                                page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        } finally {
                            page.close()
                        }
                        val anterior = thumbBitmap
                        thumbBitmap = bmp
                        anterior?.recycle()
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    val borderColor = if (isCurrent) Indigo600 else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (isCurrent) 2.5.dp else 1.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.707f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .border(borderWidth, borderColor, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            val bmp = thumbBitmap
            if (bmp != null && !bmp.isRecycled) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = stringResource(R.string.reader_page_n, printedPageNumber),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Indigo600
                )
            }

            if (isBookmarked) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 8.dp),
                    color = Indigo600,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .padding(horizontal = 4.dp, vertical = 3.dp)
                            .size(14.dp)
                    )
                }
            }
        }
        Text(
            text = printedPageNumber.toString(),
            fontSize = 12.sp,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrent) Indigo600 else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun InsertTemplatePageDialog(
    currentPage: Int,
    numPages: Int,
    onConfirm: (afterPage: Int, template: PaperTemplate) -> Unit,
    onDismiss: () -> Unit
) {
    var insertAtEnd by remember { mutableStateOf(false) }
    var selectedTemplate by remember { mutableStateOf(PaperTemplate.LINED) }

    val templates = listOf(
        PaperTemplate.BLANK to R.string.paper_template_blank,
        PaperTemplate.LINED to R.string.paper_template_lined,
        PaperTemplate.GRID to R.string.paper_template_grid,
        PaperTemplate.DOTTED to R.string.paper_template_dotted,
        PaperTemplate.CORNELL to R.string.paper_template_cornell
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.page_insert_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.page_insert_position_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { insertAtEnd = false }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = !insertAtEnd, onClick = { insertAtEnd = false })
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.page_insert_after_current, currentPage))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { insertAtEnd = true }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = insertAtEnd, onClick = { insertAtEnd = true })
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.page_insert_at_end))
                    }
                }

                Text(
                    text = stringResource(R.string.page_insert_template_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    templates.forEach { (tpl, labelRes) ->
                        FilterChip(
                            selected = selectedTemplate == tpl,
                            onClick = { selectedTemplate = tpl },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetAfter = if (insertAtEnd) numPages else currentPage.coerceIn(0, numPages)
                    onConfirm(targetAfter, selectedTemplate)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
            ) {
                Text(stringResource(R.string.page_insert_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
