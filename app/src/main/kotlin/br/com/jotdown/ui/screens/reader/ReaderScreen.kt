package br.com.jotdown.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
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
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
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
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(viewModel: ReaderViewModel, onBack: () -> Unit) {
    val pdfFile         by viewModel.pdfFile.collectAsState()
    val currentPage     by viewModel.currentPage.collectAsState()
    val activeTool      by viewModel.activeTool.collectAsState()
    val strokeColor     by viewModel.strokeColor.collectAsState()
    val annotations     by viewModel.annotations.collectAsState()
    val drawings        by viewModel.drawings.collectAsState() // 🧠 O cérebro envia os desenhos!
    val highlights      by viewModel.highlights.collectAsState()
    
    var numPages        by remember { mutableIntStateOf(0) }
    var showSidebar     by remember { mutableStateOf(false) }
    var showAnnotations by remember { mutableStateOf(false) }
    val document        by viewModel.document.collectAsState()

    var captureTrigger       by remember { mutableLongStateOf(0L) }
    var activeSelectionPage  by remember { mutableIntStateOf(-1) }

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
                onAnnotations = { showAnnotations = true }, onCapture = { captureTrigger = System.currentTimeMillis() }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val file = pdfFile
            if (file != null && numPages > 0) {
                PdfViewer(
                    pdfFile = file, numPages = numPages, activeTool = activeTool, strokeColor = strokeColor,
                    annotations = annotations, drawings = drawings, captureTrigger = captureTrigger, activeSelectionPage = activeSelectionPage, scrollToPage = scrollToPage,
                    onScrollDone = { scrollToPage = 0 }, onSelectionStarted = { activeSelectionPage = it },
                    onOcrSuccess = { page, text -> textToEdit = text; ocrResult = Pair(page, text) },
                    onPageChange = { viewModel.setCurrentPage(it) },
                    onAddAnnotation = { page, x, y -> pendingAnnotation = Triple(page, x, y); annotationText = "" },
                    onOpenAnnotation = { annot -> editingAnnotation = annot; annotationText = annot.text },
                    onSaveDrawing = { page, json -> viewModel.saveDrawing(page, json) }
                )
            } else { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Indigo600) } }

            if (activeTool == Tool.ANNOTATION) {
                val pulse = rememberInfiniteTransition(label = "pulse")
                val alpha by pulse.animateFloat(initialValue = 0.75f, targetValue = 1f, label = "alpha", animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse))
                Surface(modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp), shape = RoundedCornerShape(24.dp), color = Color(0xFFF59E0B).copy(alpha = alpha), shadowElevation = 8.dp) {
                    Row(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Toque onde deseja adicionar uma anotação", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
        AlertDialog(onDismissRequest = { ocrResult = null }, title = { Text("Revisar Texto Extraído") }, text = { OutlinedTextField(value = textToEdit, onValueChange = { textToEdit = it }, modifier = Modifier.fillMaxWidth().height(200.dp), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)) }, confirmButton = { Button(onClick = { viewModel.addHighlight(ocrResult!!.first, textToEdit.trim()); ocrResult = null; viewModel.setActiveTool(Tool.NONE) }, colors = ButtonDefaults.buttonColors(containerColor = Indigo600)) { Text("Salvar Fichamento") } }, dismissButton = { TextButton(onClick = { ocrResult = null; viewModel.setActiveTool(Tool.NONE) }) { Text("Cancelar") } })
    }

    if (pendingAnnotation != null || editingAnnotation != null) {
        AlertDialog(
            onDismissRequest = { pendingAnnotation = null; editingAnnotation = null },
            title = { Text(if (pendingAnnotation != null) "Nova Anotação" else "Consultar Anotação") },
            text = { OutlinedTextField(value = annotationText, onValueChange = { annotationText = it }, modifier = Modifier.fillMaxWidth().height(150.dp), label = { Text("Escreva a sua nota aqui...") }) },
            confirmButton = {
                Button(onClick = {
                    if (pendingAnnotation != null) { val (p, x, y) = pendingAnnotation!!; viewModel.addAnnotation(p, x, y, annotationText.trim()); pendingAnnotation = null }
                    else if (editingAnnotation != null) { viewModel.updateAnnotation(editingAnnotation!!.id, annotationText.trim()); editingAnnotation = null }
                    viewModel.setActiveTool(Tool.NONE)
                }, colors = ButtonDefaults.buttonColors(containerColor = Indigo600)) { Text("Guardar Post-it") }
            },
            dismissButton = { TextButton(onClick = { pendingAnnotation = null; editingAnnotation = null; viewModel.setActiveTool(Tool.NONE) }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun PdfViewer(
    pdfFile: File, numPages: Int, activeTool: Tool, strokeColor: Int, annotations: List<AnnotationEntity>, drawings: List<DrawingEntity>,
    captureTrigger: Long, activeSelectionPage: Int, scrollToPage: Int = 0, onScrollDone: () -> Unit = {}, 
    onOcrSuccess: (Int, String) -> Unit, onPageChange: (Int) -> Unit, onSelectionStarted: (Int) -> Unit,
    onAddAnnotation: (Int, Float, Float) -> Unit, onOpenAnnotation: (AnnotationEntity) -> Unit, onSaveDrawing: (Int, String) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToPage) { if (scrollToPage in 1..numPages) { listState.scrollToItem(scrollToPage - 1); onScrollDone() } }
    LaunchedEffect(listState.firstVisibleItemIndex) { onPageChange(listState.firstVisibleItemIndex + 1) }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(16.dp)) {
        items(numPages) { index ->
            val pageNumber = index + 1
            PdfPage(
                pdfFile = pdfFile, pageNumber = pageNumber, activeTool = activeTool, strokeColor = strokeColor, 
                annotations = annotations.filter { it.page == pageNumber },
                drawing = drawings.find { it.page == pageNumber }, // 🧠 Passa o desenho exato desta página!
                captureTrigger = captureTrigger, activeSelectionPage = activeSelectionPage, onSelectionStarted = { onSelectionStarted(pageNumber) }, 
                onOcrSuccess = { text -> onOcrSuccess(pageNumber, text) }, onAddAnnotation = { x, y -> onAddAnnotation(pageNumber, x, y) }, 
                onOpenAnnotation = onOpenAnnotation, onSaveDrawing = { json -> onSaveDrawing(pageNumber, json) }
            )
        }
    }
}

@Composable
fun PdfPage(
    pdfFile: File, pageNumber: Int, activeTool: Tool, strokeColor: Int, annotations: List<AnnotationEntity>, drawing: DrawingEntity?, captureTrigger: Long,
    activeSelectionPage: Int, onSelectionStarted: () -> Unit, onOcrSuccess: (String) -> Unit, onAddAnnotation: (Float, Float) -> Unit, 
    onOpenAnnotation: (AnnotationEntity) -> Unit, onSaveDrawing: (String) -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectionRect by remember { mutableStateOf<Rect?>(null) }
    var pageHeightPx by remember { mutableFloatStateOf(1f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(activeSelectionPage, activeTool) { if (activeSelectionPage != pageNumber || activeTool != Tool.SELECT) selectionRect = null }

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
                    pageHeightPx = bmpH.toFloat()
                    val bmp = Bitmap.createBitmap(widthPx, bmpH, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close(); renderer.close(); fd.close()
                    bitmap = bmp
                } catch (e: Exception) { }
            }
        }

        LaunchedEffect(captureTrigger) {
            if (captureTrigger > 0L && selectionRect != null && bitmap != null) {
                val bmp = bitmap!!; val rect = selectionRect!!; val pad = 15f
                val left = (rect.left - pad).coerceIn(0f, bmp.width.toFloat()).toInt()
                val top = (rect.top - pad).coerceIn(0f, bmp.height.toFloat()).toInt()
                val right = (rect.right + pad).coerceIn(0f, bmp.width.toFloat()).toInt()
                val bottom = (rect.bottom + pad).coerceIn(0f, bmp.height.toFloat()).toInt()
                val w = right - left; val h = bottom - top

                if (w > 20 && h > 20) {
                    val cropped = Bitmap.createBitmap(bmp, left, top, w, h)
                    br.com.jotdown.util.OcrUtil.extractTextFromBitmap(bitmap = cropped, onSuccess = { text -> onOcrSuccess(if (text.isBlank()) "Nenhum texto reconhecido." else text) }, onError = { e -> onOcrSuccess("Erro no OCR: ${e.message}") })
                } else { onOcrSuccess("Área muito pequena. Tente novamente.") }
                selectionRect = null 
            }
        }

        Card(modifier = Modifier.fillMaxWidth().clipToBounds(), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Box(
                modifier = Modifier.fillMaxWidth()
                    .pointerInput(activeTool) { 
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val pointers = event.changes
                                if (pointers.any { it.type == PointerType.Stylus || it.type == PointerType.Eraser }) continue
                                if (pointers.size >= 2) {
                                    val zoomChange = event.calculateZoom(); val panChange = event.calculatePan()
                                    scale = (scale * zoomChange).coerceIn(1f, 4f)
                                    if (scale > 1f) {
                                        val maxX = (widthPx * (scale - 1)) / 2f; val maxY = (pageHeightPx * (scale - 1)) / 2f
                                        offset = Offset((offset.x + panChange.x).coerceIn(-maxX, maxX), (offset.y + panChange.y).coerceIn(-maxY, maxY))
                                    } else { offset = Offset.Zero }
                                    pointers.forEach { if (!it.isConsumed) it.consume() }
                                } 
                                else if (scale > 1f && pointers.size == 1 && activeTool == Tool.NONE) {
                                    val panChange = event.calculatePan()
                                    val maxX = (widthPx * (scale - 1)) / 2f; val maxY = (pageHeightPx * (scale - 1)) / 2f
                                    val proposedX = offset.x + panChange.x; val proposedY = offset.y + panChange.y
                                    val clampedX = proposedX.coerceIn(-maxX, maxX); val clampedY = proposedY.coerceIn(-maxY, maxY)
                                    val consumedX = clampedX != offset.x; val consumedY = clampedY != offset.y
                                    offset = Offset(clampedX, clampedY)
                                    if (consumedX || consumedY) { if (consumedY || kotlin.math.abs(panChange.x) > kotlin.math.abs(panChange.y)) pointers.forEach { if (!it.isConsumed) it.consume() } }
                                }
                            } while (pointers.any { it.pressed })
                        }
                    }.graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
            ) {
                if (bitmap != null) {
                    Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                    DrawingLayer(
                        modifier = Modifier.matchParentSize(), activeTool = activeTool, strokeColor = Color(strokeColor), annotations = annotations, drawing = drawing, selectionRect = selectionRect,
                        onSelectionStarted = onSelectionStarted, onSelectionRectChange = { selectionRect = it }, onAddAnnotation = onAddAnnotation, onOpenAnnotation = onOpenAnnotation, onSaveDrawing = onSaveDrawing
                    )
                } else { Box(modifier = Modifier.fillMaxWidth().height(600.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Indigo600, modifier = Modifier.size(32.dp)) } }
            }
        }
    }
}

@Composable
fun DrawingLayer(
    modifier: Modifier, activeTool: Tool, strokeColor: Color, annotations: List<AnnotationEntity>, drawing: DrawingEntity?,
    selectionRect: Rect?, onSelectionStarted: () -> Unit, onSelectionRectChange: (Rect?) -> Unit, 
    onAddAnnotation: (Float, Float) -> Unit, onOpenAnnotation: (AnnotationEntity) -> Unit, onSaveDrawing: (String) -> Unit
) {
    // 🧠 A MAGIA ESTÁ AQUI: Puxa os desenhos salvos no BD apenas 1 vez quando a página aparece!
    val initialPaths = remember { drawing?.pathsJson?.toDrawnPaths() ?: emptyList() }
    val paths = remember { mutableStateListOf(*initialPaths.toTypedArray()) }
    
    var currentPath by remember { mutableStateOf<DrawnPath?>(null) }
    var startOffset by remember { mutableStateOf<Offset?>(null) }
    val annotationsRef = rememberUpdatedState(annotations)

    Canvas(
        modifier = modifier.pointerInput(activeTool, strokeColor) {
            if (activeTool == Tool.NONE) { detectTapGestures { offset -> val clickedAnnot = annotationsRef.value.find { val dx = it.x - offset.x; val dy = it.y - offset.y; (dx * dx + dy * dy) < 2500f }; if (clickedAnnot != null) onOpenAnnotation(clickedAnnot) }; return@pointerInput }
            if (activeTool == Tool.ANNOTATION) { detectTapGestures { offset -> val clickedAnnot = annotationsRef.value.find { val dx = it.x - offset.x; val dy = it.y - offset.y; (dx * dx + dy * dy) < 2500f }; if (clickedAnnot != null) onOpenAnnotation(clickedAnnot) else onAddAnnotation(offset.x, offset.y) }; return@pointerInput }
            if (activeTool == Tool.SELECT) {
                detectDragGestures(
                    onDragStart = { offset -> onSelectionStarted(); startOffset = offset; onSelectionRectChange(Rect(offset, offset)) },
                    onDrag = { change, _ -> startOffset?.let { start -> onSelectionRectChange(Rect(minOf(start.x, change.position.x), minOf(start.y, change.position.y), maxOf(start.x, change.position.x), maxOf(start.y, change.position.y))) } },
                    onDragEnd = { startOffset = null }
                )
                return@pointerInput
            }
            
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (down.type != PointerType.Stylus && down.type != PointerType.Eraser) return@awaitEachGesture
                
                down.consume()
                var pathInProgress = DrawnPath(tool = activeTool, color = strokeColor, points = mutableListOf(PathPoint(down.position.x, down.position.y, down.pressure)))
                currentPath = pathInProgress
                
                do {
                    val event = awaitPointerEvent()
                    val drag = event.changes.firstOrNull { it.id == down.id }
                    if (drag != null && drag.pressed) {
                        drag.consume()
                        val updatedPoints = ArrayList(pathInProgress.points).apply { add(PathPoint(drag.position.x, drag.position.y, drag.pressure)) }
                        pathInProgress = pathInProgress.copy(points = updatedPoints)
                        currentPath = pathInProgress
                    }
                } while (drag != null && drag.pressed)
                
                currentPath?.let { path -> 
                    paths.add(path)
                    onSaveDrawing(paths.toJson())
                }
                currentPath = null
            }
        }
    ) {
        paths.forEach { drawDrawnPath(it) }
        currentPath?.let { drawDrawnPath(it) }
        
        annotations.forEach { annot ->
            val cx = annot.x; val cy = annot.y; val w = 42f; val h = 26f; val radius = 8f; val tail = 10f
            val path = Path().apply { addRoundRect(RoundRect(left = cx - w/2, top = cy - h - tail, right = cx + w/2, bottom = cy - tail, cornerRadius = CornerRadius(radius, radius))); moveTo(cx - 6f, cy - tail); lineTo(cx, cy); lineTo(cx + 6f, cy - tail) }
            val shadowPath = Path().apply { addPath(path, Offset(2f, 3f)) }
            drawPath(shadowPath, color = Color.Black.copy(alpha = 0.25f))
            drawPath(path, color = Color(0xFFFDE047))
            drawPath(path, color = Color(0xFFEAB308), style = Stroke(width = 2.5f))
            drawLine(color = Color(0xFFD97706), start = Offset(cx - 10f, cy - tail - 15f), end = Offset(cx + 10f, cy - tail - 15f), strokeWidth = 2.5f, cap = StrokeCap.Round)
            drawLine(color = Color(0xFFD97706), start = Offset(cx - 10f, cy - tail - 8f), end = Offset(cx + 2f, cy - tail - 8f), strokeWidth = 2.5f, cap = StrokeCap.Round)
        }
        
        selectionRect?.let {
            drawRect(color = Color(0xFF3B82F6).copy(alpha = 0.3f), topLeft = it.topLeft, size = it.size)
            drawRect(color = Color(0xFF3B82F6), topLeft = it.topLeft, size = it.size, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

data class PathPoint(val x: Float, val y: Float, val pressure: Float = 1f)
data class DrawnPath(val tool: Tool, val color: Color, val points: MutableList<PathPoint> = mutableListOf())

fun DrawScope.drawDrawnPath(path: DrawnPath) {
    if (path.points.size < 2) return
    val baseWidth = when (path.tool) { Tool.HIGHLIGHTER -> 20f; Tool.PENCIL -> 3f; Tool.ERASER -> 30f; else -> 5f }
    val alpha = when (path.tool) { Tool.HIGHLIGHTER -> 0.4f; Tool.PENCIL -> 0.7f; else -> 1f }
    val color = if (path.tool == Tool.ERASER) Color.White else path.color

    if (path.tool == Tool.PENCIL) {
        for (i in 1 until path.points.size) {
            val p1 = path.points[i - 1]; val p2 = path.points[i]
            val w1 = baseWidth * (0.3f + 1.2f * p1.pressure); val w2 = baseWidth * (0.3f + 1.2f * p2.pressure)
            drawLine(color = color.copy(alpha = alpha), start = Offset(p1.x, p1.y), end = Offset(p2.x, p2.y), strokeWidth = (w1 + w2) / 2f, cap = StrokeCap.Round)
        }
    } else {
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
        drawPath(path = composePath, color = color.copy(alpha = alpha), style = Stroke(width = baseWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

// 🎨 NOVO: Ensina a base de dados a guardar e ler a cor que você usou no traço!
fun List<DrawnPath>.toJson(): String {
    val sb = StringBuilder("[")
    forEachIndexed { i, path ->
        if (i > 0) sb.append(",")
        sb.append("{\"tool\":\"${path.tool}\",\"color\":${path.color.toArgb()},\"points\":[")
        path.points.forEachIndexed { j, pt -> if (j > 0) sb.append(","); sb.append("{\"x\":${pt.x},\"y\":${pt.y},\"p\":${pt.pressure}}") }
        sb.append("]}")
    }
    sb.append("]")
    return sb.toString()
}

fun String.toDrawnPaths(): List<DrawnPath> {
    val result = mutableListOf<DrawnPath>()
    if (this.isBlank()) return result
    try {
        val array = JSONArray(this)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val toolStr = obj.optString("tool", "PEN")
            val tool = runCatching { Tool.valueOf(toolStr) }.getOrDefault(Tool.PEN)
            val colorInt = obj.optInt("color", if (tool == Tool.HIGHLIGHTER) 0xFFFDE047.toInt() else 0xFF000000.toInt())
            val pointsArray = obj.getJSONArray("points")
            val points = mutableListOf<PathPoint>()
            for (j in 0 until pointsArray.length()) {
                val ptObj = pointsArray.getJSONObject(j)
                points.add(PathPoint(ptObj.getDouble("x").toFloat(), ptObj.getDouble("y").toFloat(), ptObj.optDouble("p", 1.0).toFloat()))
            }
            result.add(DrawnPath(tool, Color(colorInt), points))
        }
    } catch (e: Exception) { e.printStackTrace() }
    return result
}