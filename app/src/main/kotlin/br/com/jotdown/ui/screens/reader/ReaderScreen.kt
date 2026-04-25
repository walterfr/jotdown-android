package br.com.jotdown.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import br.com.jotdown.data.entity.*
import br.com.jotdown.ui.theme.*
import br.com.jotdown.ui.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(viewModel: ReaderViewModel, onBack: () -> Unit) {
    val pdfFile         by viewModel.pdfFile.collectAsState()
    val currentPage     by viewModel.currentPage.collectAsState()
    val activeTool      by viewModel.activeTool.collectAsState()
    val strokeColor     by viewModel.strokeColor.collectAsState()
    val annotations     by viewModel.annotations.collectAsState()
    val highlights      by viewModel.highlights.collectAsState()
    var numPages        by remember { mutableIntStateOf(0) }
    var showSidebar     by remember { mutableStateOf(false) }
    var showAnnotations by remember { mutableStateOf(false) }
    val document        by viewModel.document.collectAsState()

    var captureRequested by remember { mutableStateOf(false) }
    var ocrResult        by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var textToEdit       by remember { mutableStateOf("") }

    var pendingAnnotation by remember { mutableStateOf<Triple<Int, Float, Float>?>(null) }
    var editingAnnotation by remember { mutableStateOf<AnnotationEntity?>(null) }
    var annotationText    by remember { mutableStateOf("") }
    var scrollToPage      by remember { mutableIntStateOf(0) }

    LaunchedEffect(pdfFile) {
        val file = pdfFile ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                numPages = renderer.pageCount
                renderer.close(); fd.close()
            } catch (e: Exception) { }
        }
    }

    Scaffold(
        topBar = {
            ReaderTopBar(
                activeTool = activeTool, strokeColor = strokeColor, onBack = onBack, onMenuClick = { showSidebar = true },
                onToolSelect = { viewModel.toggleTool(it) }, onColorSelect = { viewModel.setStrokeColor(it) },
                onAnnotations = { showAnnotations = true }, onCapture = { captureRequested = true }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val file = pdfFile
            if (file != null && numPages > 0) {
                PdfViewer(
                    pdfFile = file, numPages = numPages, currentPage = currentPage, activeTool = activeTool, strokeColor = strokeColor,
                    annotations = annotations, captureRequested = captureRequested, scrollToPage = scrollToPage,
                    onScrollDone = { scrollToPage = 0 }, onCaptureDone = { captureRequested = false },
                    onOcrSuccess = { page, text -> textToEdit = text; ocrResult = Pair(page, text) },
                    onPageChange = { viewModel.setCurrentPage(it) },
                    onAddAnnotation = { page, x, y -> pendingAnnotation = Triple(page, x, y); annotationText = "" },
                    onOpenAnnotation = { annot -> editingAnnotation = annot; annotationText = annot.text },
                    onSaveDrawing = { page, json -> viewModel.saveDrawing(page, json) }
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Indigo600) }
            }

            if (activeTool == Tool.ANNOTATION) {
                val pulse = rememberInfiniteTransition(label = "pulse")
                val alpha by pulse.animateFloat(initialValue = 0.75f, targetValue = 1f, label = "alpha", animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse))
                Surface(modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp), shape = RoundedCornerShape(24.dp), color = Color(0xFFF59E0B).copy(alpha = alpha), shadowElevation = 8.dp) {
                    Row(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Toque onde deseja adicionar uma anota\u00e7\u00e3o", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            if (numPages > 0) {
                Surface(modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), shape = RoundedCornerShape(16.dp), shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("$currentPage", fontWeight = FontWeight.Bold, color = Indigo600, fontSize = 14.sp)
                        Text("/", color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
                        Text("$numPages", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showSidebar) { MetadataSheet(document = document, highlights = highlights, onSave = { p -> viewModel.saveMetadata(p.docType, p.authorLastName, p.authorFirstName, p.title, p.subtitle, p.edition, p.city, p.publisher, p.year, p.journal, p.volume, p.pages, p.url, p.accessDate) }, onDismiss = { showSidebar = false }) }
    if (showAnnotations) { AnnotationSheet(annotations = annotations, onDismiss = { showAnnotations = false }, onDelete = { viewModel.deleteAnnotation(it) }, onGoToPage = { page -> viewModel.setCurrentPage(page); scrollToPage = page }) }

    if (ocrResult != null) {
        AlertDialog(
            onDismissRequest = { ocrResult = null }, title = { Text("Revisar Texto Extra\u00eddo") },
            text = { OutlinedTextField(value = textToEdit, onValueChange = { textToEdit = it }, modifier = Modifier.fillMaxWidth().height(200.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)) },
            confirmButton = { Button(onClick = { viewModel.addHighlight(ocrResult!!.first, textToEdit.trim()); ocrResult = null; viewModel.setActiveTool(Tool.NONE) }, colors = ButtonDefaults.buttonColors(containerColor = Indigo600)) { Text("Salvar Fichamento") } },
            dismissButton = { TextButton(onClick = { ocrResult = null; viewModel.setActiveTool(Tool.NONE) }) { Text("Cancelar") } }
        )
    }

    if (pendingAnnotation != null || editingAnnotation != null) {
        AlertDialog(
            onDismissRequest = { pendingAnnotation = null; editingAnnotation = null },
            title = { Text(if (pendingAnnotation != null) "Nova Anota\u00e7\u00e3o" else "Consultar Anota\u00e7\u00e3o") },
            text = { OutlinedTextField(value = annotationText, onValueChange = { annotationText = it }, modifier = Modifier.fillMaxWidth().height(150.dp), label = { Text("Escreva a sua nota aqui...") }) },
            confirmButton = {
                Button(
                    onClick = {
                        if (pendingAnnotation != null) { val (p, x, y) = pendingAnnotation!!; viewModel.addAnnotation(p, x, y, annotationText.trim()); pendingAnnotation = null }
                        else if (editingAnnotation != null) { viewModel.updateAnnotation(editingAnnotation!!.id, annotationText.trim()); editingAnnotation = null }
                        viewModel.setActiveTool(Tool.NONE)
                    }, colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
                ) { Text("Guardar Post-it") }
            },
            dismissButton = { TextButton(onClick = { pendingAnnotation = null; editingAnnotation = null; viewModel.setActiveTool(Tool.NONE) }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun PdfViewer(
    pdfFile: File, numPages: Int, currentPage: Int, activeTool: Tool, strokeColor: Int, annotations: List<AnnotationEntity>, captureRequested: Boolean,
    scrollToPage: Int = 0, onScrollDone: () -> Unit = {}, onCaptureDone: () -> Unit, onOcrSuccess: (Int, String) -> Unit, onPageChange: (Int) -> Unit,
    onAddAnnotation: (Int, Float, Float) -> Unit, onOpenAnnotation: (AnnotationEntity) -> Unit, onSaveDrawing: (Int, String) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(scrollToPage) {
        if (scrollToPage in 1..numPages) { listState.scrollToItem(scrollToPage - 1); onScrollDone() }
    }

    LaunchedEffect(listState.firstVisibleItemIndex) { onPageChange(listState.firstVisibleItemIndex + 1) }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(Color(0xFFE5E5F7)), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(16.dp)) {
        items(numPages) { index ->
            val pageNumber = index + 1
            PdfPage(
                pdfFile = pdfFile, pageNumber = pageNumber, activeTool = activeTool, strokeColor = strokeColor, annotations = annotations.filter { it.page == pageNumber },
                captureRequested = captureRequested, onCaptureDone = onCaptureDone, onOcrSuccess = { text -> onOcrSuccess(pageNumber, text) },
                onAddAnnotation = { x, y -> onAddAnnotation(pageNumber, x, y) }, onOpenAnnotation = onOpenAnnotation, onSaveDrawing = { json -> onSaveDrawing(pageNumber, json) }
            )
        }
    }
}

@Composable
fun PdfPage(
    pdfFile: File, pageNumber: Int, activeTool: Tool, strokeColor: Int, annotations: List<AnnotationEntity>, captureRequested: Boolean, onCaptureDone: () -> Unit,
    onOcrSuccess: (String) -> Unit, onAddAnnotation: (Float, Float) -> Unit, onOpenAnnotation: (AnnotationEntity) -> Unit, onSaveDrawing: (String) -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectionRect by remember { mutableStateOf<Rect?>(null) }
    
    // Estados do Zoom de Pinça
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        if (scale > 1f) offset += offsetChange else offset = Offset.Zero
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }

        LaunchedEffect(pageNumber, widthPx) {
            if (widthPx <= 0) return@LaunchedEffect
            withContext(Dispatchers.IO) {
                try {
                    val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(fd)
                    val page = renderer.openPage(pageNumber - 1)
                    val scaleFactor = widthPx.toFloat() / page.width.coerceAtLeast(1)
                    val bmpH = (page.height * scaleFactor).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(widthPx, bmpH, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close(); renderer.close(); fd.close()
                    bitmap = bmp
                } catch (e: Exception) { }
            }
        }

        LaunchedEffect(captureRequested) {
            if (captureRequested && selectionRect != null) {
                if (bitmap != null) {
                    val bmp = bitmap!!
                    val rect = selectionRect!!
                    val pad = 15f
                    val left = (rect.left - pad).coerceIn(0f, bmp.width.toFloat()).toInt()
                    val top = (rect.top - pad).coerceIn(0f, bmp.height.toFloat()).toInt()
                    val right = (rect.right + pad).coerceIn(0f, bmp.width.toFloat()).toInt()
                    val bottom = (rect.bottom + pad).coerceIn(0f, bmp.height.toFloat()).toInt()
                    val w = right - left; val h = bottom - top

                    if (w > 20 && h > 20) {
                        val cropped = Bitmap.createBitmap(bmp, left, top, w, h)
                        br.com.jotdown.util.OcrUtil.extractTextFromBitmap(
                            bitmap = cropped,
                            onSuccess = { text -> val res = if (text.isBlank()) "Nenhum texto reconhecido." else text; onOcrSuccess(res) },
                            onError = { e -> onOcrSuccess("Erro no OCR: ${e.message}") }
                        )
                    } else { onOcrSuccess("\u00c1rea muito pequena. Tente novamente.") }
                }
                onCaptureDone()
                selectionRect = null
            }
        }

        Card(modifier = Modifier.fillMaxWidth().clipToBounds(), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Box(
                modifier = Modifier.fillMaxWidth().transformable(state = transformState)
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
            ) {
                if (bitmap != null) {
                    Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                    DrawingLayer(
                        modifier = Modifier.matchParentSize(), activeTool = activeTool, strokeColor = Color(strokeColor), annotations = annotations, selectionRect = selectionRect,
                        onSelectionRectChange = { selectionRect = it }, onAddAnnotation = onAddAnnotation, onOpenAnnotation = onOpenAnnotation, onSaveDrawing = onSaveDrawing
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(600.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Indigo600, modifier = Modifier.size(32.dp)) }
                }
            }
        }
    }
}

@Composable
fun DrawingLayer(
    modifier: Modifier, activeTool: Tool, strokeColor: Color, annotations: List<AnnotationEntity>,
    selectionRect: Rect?, onSelectionRectChange: (Rect?) -> Unit, onAddAnnotation: (Float, Float) -> Unit, onOpenAnnotation: (AnnotationEntity) -> Unit, onSaveDrawing: (String) -> Unit
) {
    val paths = remember { mutableStateListOf<DrawnPath>() }
    var currentPath by remember { mutableStateOf<DrawnPath?>(null) }
    var startOffset by remember { mutableStateOf<Offset?>(null) }
    val annotationsRef = rememberUpdatedState(annotations)

    Canvas(
        modifier = modifier.pointerInput(activeTool, strokeColor) {
            if (activeTool == Tool.NONE) return@pointerInput

            if (activeTool == Tool.ANNOTATION) {
                detectTapGestures { offset ->
                    val clickedAnnot = annotationsRef.value.find { val dx = it.x - offset.x; val dy = it.y - offset.y; (dx * dx + dy * dy) < 1500f }
                    if (clickedAnnot != null) onOpenAnnotation(clickedAnnot) else onAddAnnotation(offset.x, offset.y)
                }
                return@pointerInput
            }
            if (activeTool == Tool.SELECT) {
                detectDragGestures(
                    onDragStart = { offset -> startOffset = offset; onSelectionRectChange(Rect(offset, offset)) },
                    onDrag = { change, _ ->
                        startOffset?.let { start ->
                            val left = minOf(start.x, change.position.x); val top = minOf(start.y, change.position.y)
                            val right = maxOf(start.x, change.position.x); val bottom = maxOf(start.y, change.position.y)
                            onSelectionRectChange(Rect(left, top, right, bottom))
                        }
                    },
                    onDragEnd = { startOffset = null }
                )
                return@pointerInput
            }
            detectDragGestures(
                onDragStart = { offset -> currentPath = DrawnPath(tool = activeTool, color = strokeColor, points = mutableListOf(offset)) },
                onDrag = { change, _ -> currentPath?.points?.add(change.position) },
                onDragEnd = { currentPath?.let { path -> paths.add(path); onSaveDrawing(paths.toJson()) }; currentPath = null }
            )
        }
    ) {
        paths.forEach { drawDrawnPath(it) }
        currentPath?.let { drawDrawnPath(it) }
        
        // Novo Balão de Fala
        annotations.forEach { annot ->
            val cx = annot.x; val cy = annot.y
            val w = 42f; val h = 26f; val radius = 8f; val tail = 10f

            val path = Path().apply {
                addRoundRect(RoundRect(left = cx - w/2, top = cy - h - tail, right = cx + w/2, bottom = cy - tail, cornerRadius = CornerRadius(radius, radius)))
                moveTo(cx - 6f, cy - tail)
                lineTo(cx, cy)
                lineTo(cx + 6f, cy - tail)
            }

            // Sombra
            val shadowPath = Path().apply { addPath(path, Offset(2f, 3f)) }
            drawPath(shadowPath, color = Color.Black.copy(alpha = 0.25f))

            // Preenchimento Amarelo Vivo
            drawPath(path, color = Color(0xFFFDE047))

            // Borda Âmbar
            drawPath(path, color = Color(0xFFEAB308), style = Stroke(width = 2.5f))

            // Linhas internas a simular texto
            drawLine(color = Color(0xFFD97706), start = Offset(cx - 10f, cy - tail - 15f), end = Offset(cx + 10f, cy - tail - 15f), strokeWidth = 2.5f, cap = StrokeCap.Round)
            drawLine(color = Color(0xFFD97706), start = Offset(cx - 10f, cy - tail - 8f), end = Offset(cx + 2f, cy - tail - 8f), strokeWidth = 2.5f, cap = StrokeCap.Round)
        }
        
        selectionRect?.let {
            drawRect(color = Color(0xFF3B82F6).copy(alpha = 0.3f), topLeft = it.topLeft, size = it.size)
            drawRect(color = Color(0xFF3B82F6), topLeft = it.topLeft, size = it.size, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

data class DrawnPath(val tool: Tool, val color: Color, val points: MutableList<Offset> = mutableListOf())

fun DrawScope.drawDrawnPath(path: DrawnPath) {
    if (path.points.size < 2) return
    val composePath = Path().apply {
        moveTo(path.points[0].x, path.points[0].y)
        var lastX = path.points[0].x; var lastY = path.points[0].y
        for (i in 1 until path.points.size) {
            val pt = path.points[i]
            val midX = (lastX + pt.x) / 2f; val midY = (lastY + pt.y) / 2f
            if (i == 1) lineTo(midX, midY) else quadraticBezierTo(lastX, lastY, midX, midY)
            lastX = pt.x; lastY = pt.y
        }
        lineTo(lastX, lastY)
    }
    val width = when (path.tool) { Tool.HIGHLIGHTER -> 20f; Tool.PENCIL -> 3f; Tool.ERASER -> 30f; else -> 5f }
    val alpha = when (path.tool) { Tool.HIGHLIGHTER -> 0.4f; Tool.PENCIL -> 0.7f; else -> 1f }
    val color = if (path.tool == Tool.ERASER) Color.White else path.color
    drawPath(path = composePath, color = color.copy(alpha = alpha), style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

fun List<DrawnPath>.toJson(): String {
    val sb = StringBuilder("[")
    forEachIndexed { i, path ->
        if (i > 0) sb.append(",")
        sb.append("{\"tool\":\"${path.tool}\",\"points\":[")
        path.points.forEachIndexed { j, pt -> if (j > 0) sb.append(","); sb.append("{\"x\":${pt.x},\"y\":${pt.y}}") }
        sb.append("]}")
    }
    sb.append("]")
    return sb.toString()
}