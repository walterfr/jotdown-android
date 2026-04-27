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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import br.com.jotdown.data.entity.*
import br.com.jotdown.ui.theme.*
import br.com.jotdown.ui.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.File

enum class Tool { NONE, SELECT, PEN, PENCIL, HIGHLIGHTER, ERASER, ANNOTATION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit
) {
    val pdfFile         by viewModel.pdfFile.collectAsState()
    val currentPage     by viewModel.currentPage.collectAsState()
    val activeTool      by viewModel.activeTool.collectAsState()
    val strokeColor     by viewModel.strokeColor.collectAsState()
    val annotations     by viewModel.annotations.collectAsState()
    val highlights      by viewModel.highlights.collectAsState()
    val drawings        by viewModel.drawings.collectAsState()
    val document        by viewModel.document.collectAsState()

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pdf_prefs", android.content.Context.MODE_PRIVATE) }
    val docId = document?.id ?: ""

    var numPages        by remember { mutableIntStateOf(0) }
    var showSidebar     by remember { mutableStateOf(false) }
    var showAnnotations by remember { mutableStateOf(false) }

    var ocrResult        by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var textToEdit       by remember { mutableStateOf("") }

    var pendingAnnotation by remember { mutableStateOf<Triple<Int, Float, Float>?>(null) }
    var editingAnnotation by remember { mutableStateOf<AnnotationEntity?>(null) }
    var annotationText    by remember { mutableStateOf("") }

    var scrollToPage by remember { mutableIntStateOf(0) }
    
    var pageOffset by remember(docId) { mutableIntStateOf(if (docId.isNotBlank()) prefs.getInt("offset_$docId", 0) else 0) }
    var showOffsetDialog by remember { mutableStateOf(false) }
    var undoTrigger by remember { mutableLongStateOf(0L) }
    var redoTrigger by remember { mutableLongStateOf(0L) }

    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    val renderMutex = remember { Mutex() }

    LaunchedEffect(pdfFile) {
        val file = pdfFile ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                fileDescriptor = fd
                pdfRenderer = renderer
                numPages = renderer.pageCount
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    DisposableEffect(pdfFile) {
        onDispose {
            pdfRenderer?.close()
            fileDescriptor?.close()
        }
    }

    LaunchedEffect(numPages, docId) {
        if (numPages > 0 && docId.isNotBlank()) {
            val last = prefs.getInt("last_$docId", 1)
            if (last > 1) { scrollToPage = last }
        }
    }

    Scaffold(
        topBar = {
            ReaderTopBar(
                annotationCount = annotations.size,
                onBack          = onBack,
                onMenuClick     = { showSidebar = true },
                onAnnotations   = { showAnnotations = true },
            )
        },
        bottomBar = {
            ReaderToolsBar(
                activeTool    = activeTool,
                strokeColor   = strokeColor,
                onToolSelect  = { viewModel.toggleTool(it) },
                onColorSelect = { viewModel.setStrokeColor(it) },
                onUndo        = { undoTrigger = System.currentTimeMillis() },
                onRedo        = { redoTrigger = System.currentTimeMillis() },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val file = pdfFile
            if (file != null && numPages > 0) {
                PdfViewer(
                    pdfFile         = file,
                    numPages        = numPages,
                    currentPage     = currentPage,
                    activeTool      = activeTool,
                    strokeColor     = strokeColor,
                    annotations     = annotations,
                    drawings        = drawings,
                    scrollToPage    = scrollToPage,
                    undoTrigger     = undoTrigger,
                    redoTrigger     = redoTrigger,
                    pdfRenderer     = pdfRenderer,
                    renderMutex     = renderMutex,
                    onScrollDone    = { scrollToPage = 0 },
                    onOcrSuccess    = { page, text ->
                        textToEdit = text
                        ocrResult = Pair(page, text)
                    },
                    onPageChange    = { 
                        viewModel.setCurrentPage(it)
                        if (docId.isNotBlank()) prefs.edit().putInt("last_$docId", it).apply()
                    },
                    onAddAnnotation = { page, x, y ->
                        pendingAnnotation = Triple(page, x, y)
                        annotationText = ""
                    },
                    onOpenAnnotation = { annot ->
                        editingAnnotation = annot
                        annotationText = annot.text
                    },
                    onSaveDrawing   = { page, json -> viewModel.saveDrawing(page, json) }
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Indigo600)
                }
            }

            if (activeTool == Tool.ANNOTATION) {
                val pulse = rememberInfiniteTransition(label = "pulse")
                val alpha by pulse.animateFloat(
                    initialValue = 0.75f, targetValue = 1f, label = "alpha",
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse)
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                    shape = RoundedCornerShape(24.dp), color = Color(0xFFF59E0B).copy(alpha = alpha), shadowElevation = 8.dp
                ) {
                    Row(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Toque onde deseja adicionar uma anotação", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            if (numPages > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        // A bottomBar do Scaffold já cria espaço suficiente;
                        // o padding aqui apenas afasta a pílula do limite do conteúdo.
                        .padding(bottom = 8.dp, start = 16.dp, end = 16.dp)
                ) {
                    // Pílula centralizada
                    Surface(
                        onClick = { showOffsetDialog = true },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text("PDF $currentPage de $numPages", color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f), fontSize = 10.sp)
                            Text("Doc ${currentPage + pageOffset} de $numPages", color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Slider ancorado à direita
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Slider(
                            value = currentPage.toFloat(),
                            onValueChange = { scrollToPage = it.toInt() },
                            valueRange = 1f..numPages.toFloat(),
                            modifier = Modifier.width(130.dp).padding(horizontal = 12.dp)
                        )
                    }
                }
            }
        }
    }

    if (showOffsetDialog) {
        var input by remember { mutableStateOf((currentPage + pageOffset).toString()) }
        AlertDialog(
            onDismissRequest = { showOffsetDialog = false },
            title = { Text("Sincronizar Página") },
            text = { OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("Número impresso na folha") }) },
            confirmButton = { Button(onClick = { pageOffset = (input.toIntOrNull() ?: currentPage) - currentPage; prefs.edit().putInt("offset_$docId", pageOffset).apply(); showOffsetDialog = false }) { Text("Ok") } }
        )
    }

    if (showSidebar) {
        MetadataSheet(
            document = document, 
            highlights = highlights,
            pageOffset = pageOffset,
            onSave = { p -> viewModel.saveMetadata(p.docType, p.authorLastName, p.authorFirstName, p.title, p.subtitle, p.edition, p.city, p.publisher, p.year, p.journal, p.volume, p.pages, p.url, p.accessDate) },
            onUpdateHighlight = { _, _ -> },
            onDeleteHighlight = { id -> viewModel.deleteHighlight(id) },
            onDismiss = { showSidebar = false }
        )
    }
    
    // 🛡️ A PEÇA QUE FALTAVA: AnnotationSheet volta a receber o pageOffset!
    if (showAnnotations) {
        AnnotationSheet(
            annotations = annotations,
            pageOffset = pageOffset,
            onDismiss = { showAnnotations = false },
            onDelete = { id -> viewModel.deleteAnnotation(id) },
            onGoToPage = { page -> viewModel.setCurrentPage(page); scrollToPage = page }
        )
    }

    if (ocrResult != null) {
        AlertDialog(
            onDismissRequest = { ocrResult = null },
            title = { Text("Revisar Texto Extraído") },
            text = {
                OutlinedTextField(
                    value = textToEdit,
                    onValueChange = { textToEdit = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addHighlight(ocrResult!!.first, textToEdit.trim())
                        ocrResult = null
                        viewModel.setActiveTool(Tool.NONE)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
                ) { Text("Salvar Fichamento") }
            },
            dismissButton = {
                TextButton(onClick = { ocrResult = null; viewModel.setActiveTool(Tool.NONE) }) { Text("Cancelar") }
            }
        )
    }

    if (pendingAnnotation != null || editingAnnotation != null) {
        AlertDialog(
            onDismissRequest = { pendingAnnotation = null; editingAnnotation = null },
            title = { Text(if (pendingAnnotation != null) "Nova Anotação" else "Consultar Anotação") },
            text = {
                OutlinedTextField(
                    value = annotationText,
                    onValueChange = { annotationText = it },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    label = { Text("Escreva a sua nota aqui...") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pendingAnnotation != null) {
                            val (p, x, y) = pendingAnnotation!!
                            viewModel.addAnnotation(p, x, y, annotationText.trim())
                            pendingAnnotation = null
                        } else if (editingAnnotation != null) {
                            viewModel.updateAnnotation(editingAnnotation!!.id, annotationText.trim())
                            editingAnnotation = null
                        }
                        viewModel.setActiveTool(Tool.NONE)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
                ) { Text("Guardar Post-it") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAnnotation = null; editingAnnotation = null; viewModel.setActiveTool(Tool.NONE) }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun PdfViewer(
    pdfFile: File, numPages: Int, currentPage: Int, activeTool: Tool, strokeColor: Int,
    annotations: List<AnnotationEntity>, drawings: List<DrawingEntity>,
    scrollToPage: Int, undoTrigger: Long, redoTrigger: Long,
    pdfRenderer: PdfRenderer?, renderMutex: Mutex,
    onScrollDone: () -> Unit, onOcrSuccess: (Int, String) -> Unit, onPageChange: (Int) -> Unit,
    onAddAnnotation: (Int, Float, Float) -> Unit, onOpenAnnotation: (AnnotationEntity) -> Unit, onSaveDrawing: (Int, String) -> Unit
) {
    val isScrollEnabled = activeTool == Tool.NONE || activeTool == Tool.SELECT || activeTool == Tool.ANNOTATION
    val listState = rememberLazyListState()

    LaunchedEffect(scrollToPage) {
        if (scrollToPage in 1..numPages) {
            listState.scrollToItem(scrollToPage - 1)
            onScrollDone()
        }
    }

    LaunchedEffect(listState.firstVisibleItemIndex) { onPageChange(listState.firstVisibleItemIndex + 1) }

    LazyColumn(
        state = listState,
        userScrollEnabled = isScrollEnabled,
        modifier = Modifier.fillMaxSize().background(Color(0xFFE5E5F7)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(numPages) { index ->
            val pageNumber = index + 1
            PdfPage(
                pdfFile = pdfFile, pageNumber = pageNumber, activeTool = activeTool, strokeColor = strokeColor,
                annotations = annotations.filter { it.page == pageNumber },
                pageDrawingsJson = drawings.find { it.page == pageNumber }?.pathsJson,
                undoTrigger = undoTrigger, redoTrigger = redoTrigger,
                pdfRenderer = pdfRenderer, renderMutex = renderMutex,
                onOcrSuccess = { text -> onOcrSuccess(pageNumber, text) },
                onAddAnnotation = { x, y -> onAddAnnotation(pageNumber, x, y) },
                onOpenAnnotation = onOpenAnnotation,
                onSaveDrawing = { json -> onSaveDrawing(pageNumber, json) }
            )
        }
    }
}

@Composable
fun PdfPage(
    pdfFile: File, pageNumber: Int, activeTool: Tool, strokeColor: Int,
    annotations: List<AnnotationEntity>, pageDrawingsJson: String?,
    undoTrigger: Long, redoTrigger: Long, pdfRenderer: PdfRenderer?, renderMutex: Mutex,
    onOcrSuccess: (String) -> Unit, onAddAnnotation: (Float, Float) -> Unit, onOpenAnnotation: (AnnotationEntity) -> Unit, onSaveDrawing: (String) -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    var selectionRect by remember { mutableStateOf<Rect?>(null) }
    var startOffset by remember { mutableStateOf<Offset?>(null) }
    var dragAction by remember { mutableStateOf("NEW") }
    var initialRect by remember { mutableStateOf<Rect?>(null) }
    val selectionRectRef = rememberUpdatedState(selectionRect)

    LaunchedEffect(activeTool) { if (activeTool != Tool.SELECT) selectionRect = null }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }

        LaunchedEffect(pageNumber, widthPx, pdfRenderer) {
            if (widthPx > 0 && pdfRenderer != null) {
                withContext(Dispatchers.IO) {
                    try {
                        renderMutex.withLock {
                            val page = pdfRenderer.openPage(pageNumber - 1)
                            val scale = widthPx.toFloat() / page.width.coerceAtLeast(1)
                            val bmpH = (page.height * scale).toInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(widthPx, bmpH, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close()
                            bitmap = bmp
                        }
                    } catch (e: Exception) { }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(4.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                val bmp = bitmap
                if (bmp != null) {
                    Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                    
                    DrawingLayer(
                        modifier = Modifier.matchParentSize(), activeTool = activeTool, strokeColor = Color(strokeColor),
                        annotations = annotations, pageDrawingsJson = pageDrawingsJson, undoTrigger = undoTrigger, redoTrigger = redoTrigger,
                        onAddAnnotation = onAddAnnotation, onOpenAnnotation = onOpenAnnotation, onSaveDrawing = onSaveDrawing
                    )

                    if (activeTool == Tool.SELECT) {
                        Canvas(modifier = Modifier.matchParentSize().pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val rect = selectionRectRef.value
                                    val t = 100f
                                    if (rect != null) {
                                        when {
                                            kotlin.math.abs(offset.x - rect.left) < t && kotlin.math.abs(offset.y - rect.top) < t -> dragAction = "TL"
                                            kotlin.math.abs(offset.x - rect.right) < t && kotlin.math.abs(offset.y - rect.top) < t -> dragAction = "TR"
                                            kotlin.math.abs(offset.x - rect.left) < t && kotlin.math.abs(offset.y - rect.bottom) < t -> dragAction = "BL"
                                            kotlin.math.abs(offset.x - rect.right) < t && kotlin.math.abs(offset.y - rect.bottom) < t -> dragAction = "BR"
                                            rect.contains(offset) -> dragAction = "MOVE"
                                            else -> dragAction = "NEW"
                                        }
                                    } else dragAction = "NEW"
                                    initialRect = rect
                                    if (dragAction == "NEW") { startOffset = offset; selectionRect = Rect(offset, offset) }
                                    else if (dragAction == "MOVE") { startOffset = offset }
                                },
                                onDrag = { change, _ ->
                                    val pos = change.position
                                    if (dragAction == "NEW") {
                                        startOffset?.let { start -> selectionRect = Rect(minOf(start.x, pos.x), minOf(start.y, pos.y), maxOf(start.x, pos.x), maxOf(start.y, pos.y)) }
                                    } else if (dragAction == "MOVE") {
                                        startOffset?.let { start ->
                                            val dx = pos.x - start.x; val dy = pos.y - start.y
                                            initialRect?.let { init -> selectionRect = init.translate(dx, dy) }
                                        }
                                    } else {
                                        initialRect?.let { init ->
                                            val left = if (dragAction.contains("L")) minOf(pos.x, init.right - 40f) else init.left
                                            val right = if (dragAction.contains("R")) maxOf(pos.x, init.left + 40f) else init.right
                                            val top = if (dragAction.contains("T")) minOf(pos.y, init.bottom - 40f) else init.top
                                            val bottom = if (dragAction.contains("B")) maxOf(pos.y, init.top + 40f) else init.bottom
                                            selectionRect = Rect(left, top, right, bottom)
                                        }
                                    }
                                },
                                onDragEnd = { startOffset = null; dragAction = "NEW" }
                            )
                        }) {
                            selectionRect?.let {
                                drawRect(color = Color(0xFF3B82F6).copy(alpha = 0.3f), topLeft = it.topLeft, size = it.size)
                                drawRect(color = Color(0xFF3B82F6), topLeft = it.topLeft, size = it.size, style = Stroke(width = 2.dp.toPx()))
                                val r = 8.dp.toPx()
                                val blue = Color(0xFF3B82F6)
                                drawCircle(Color.White, r, it.topLeft); drawCircle(blue, r, it.topLeft, style = Stroke(4f))
                                drawCircle(Color.White, r, it.topRight); drawCircle(blue, r, it.topRight, style = Stroke(4f))
                                drawCircle(Color.White, r, it.bottomLeft); drawCircle(blue, r, it.bottomLeft, style = Stroke(4f))
                                drawCircle(Color.White, r, it.bottomRight); drawCircle(blue, r, it.bottomRight, style = Stroke(4f))
                            }
                        }
                        
                        selectionRect?.let { rect ->
                            if (rect.width > 40f && rect.height > 40f) {
                                Box(modifier = Modifier.offset { IntOffset(rect.right.toInt() - 130, rect.bottom.toInt() + 16) }) {
                                    Button(
                                        onClick = {
                                            try {
                                                val safeLeft = maxOf(0, rect.left.toInt())
                                                val safeTop = maxOf(0, rect.top.toInt())
                                                val safeWidth = minOf(bmp.width - safeLeft, rect.width.toInt())
                                                val safeHeight = minOf(bmp.height - safeTop, rect.height.toInt())
                                                
                                                if (safeWidth > 0 && safeHeight > 0) {
                                                    val crop = Bitmap.createBitmap(bmp, safeLeft, safeTop, safeWidth, safeHeight)
                                                    val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS)
                                                    val image = com.google.mlkit.vision.common.InputImage.fromBitmap(crop, 0)
                                                    recognizer.process(image).addOnSuccessListener { visionText ->
                                                        if (visionText.text.isNotBlank()) onOcrSuccess(visionText.text)
                                                    }
                                                    selectionRect = null
                                                }
                                            } catch (e: Exception) { e.printStackTrace() }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), modifier = Modifier.height(36.dp), shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Capturar", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(600.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF4F46E5), modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DrawingLayer(
    modifier: Modifier, activeTool: Tool, strokeColor: Color, annotations: List<AnnotationEntity>,
    pageDrawingsJson: String?, undoTrigger: Long, redoTrigger: Long,
    onAddAnnotation: (Float, Float) -> Unit, onOpenAnnotation: (AnnotationEntity) -> Unit, onSaveDrawing: (String) -> Unit
) {
    val paths = remember { mutableStateListOf<DrawnPath>() }
    val redoStack = remember { mutableStateListOf<DrawnPath>() }
    var currentPath by remember { mutableStateOf<DrawnPath?>(null) }
    // Posição do cursor da borracha (null = não está apagando)
    var eraserCursorPos by remember { mutableStateOf<Offset?>(null) }
    val annotationsRef = rememberUpdatedState(annotations)

    LaunchedEffect(pageDrawingsJson) {
        if (!pageDrawingsJson.isNullOrBlank()) { paths.clear(); paths.addAll(parseDrawingsJson(pageDrawingsJson)) }
    }

    LaunchedEffect(undoTrigger) { if (undoTrigger > 0 && paths.isNotEmpty()) { redoStack.add(paths.removeAt(paths.size - 1)); onSaveDrawing(paths.toJson()) } }
    LaunchedEffect(redoTrigger) { if (redoTrigger > 0 && redoStack.isNotEmpty()) { paths.add(redoStack.removeAt(redoStack.size - 1)); onSaveDrawing(paths.toJson()) } }

    Canvas(
        // O graphicsLayer cria um layer de composição dedicado, necessário para que
        // BlendMode.Clear funcione corretamente (apaga apenas os pixels deste layer).
        modifier = modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .pointerInput(activeTool, strokeColor) {
            if (activeTool == Tool.NONE || activeTool == Tool.SELECT) return@pointerInput

            if (activeTool == Tool.ANNOTATION) {
                detectTapGestures { offset ->
                    val clickedAnnot = annotationsRef.value.find {
                        val dx = it.x - offset.x; val dy = it.y - offset.y
                        (dx * dx + dy * dy) < 1500f
                    }
                    if (clickedAnnot != null) onOpenAnnotation(clickedAnnot) else onAddAnnotation(offset.x, offset.y)
                }
                return@pointerInput
            }

            awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                redoStack.clear()
                var pathInProgress = DrawnPath(activeTool, strokeColor, mutableListOf(PathPoint(down.position.x, down.position.y, down.pressure)))
                currentPath = pathInProgress
                do {
                    val event = awaitPointerEvent()
                    val drag = event.changes.firstOrNull { it.id == down.id }
                    if (drag != null && drag.pressed) {
                        drag.consume()
                        val pts = ArrayList(pathInProgress.points).apply { add(PathPoint(drag.position.x, drag.position.y, drag.pressure)) }
                        pathInProgress = pathInProgress.copy(points = pts); currentPath = pathInProgress
                        // Atualiza posição do cursor da borracha
                        if (activeTool == Tool.ERASER) eraserCursorPos = drag.position
                    }
                } while (drag != null && drag.pressed)
                currentPath?.let { paths.add(it); onSaveDrawing(paths.toJson()) }
                currentPath = null
                eraserCursorPos = null  // Esconde o cursor ao soltar
            }
        }
    ) {
        paths.forEach { drawDrawnPath(it) }
        currentPath?.let { drawDrawnPath(it) }

        // Cursor circular da borracha
        if (activeTool == Tool.ERASER) {
            eraserCursorPos?.let { pos ->
                val r = 15.dp.toPx()
                drawCircle(
                    color = Color(0xFF666666),
                    radius = r,
                    center = pos,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawCircle(
                    color = Color(0x22000000),
                    radius = r,
                    center = pos
                )
            }
        }

        annotations.forEach { annot ->
            val cx = annot.x; val cy = annot.y
            val r      = 14.dp.toPx() / 3f
            val tailH  = r * 0.65f
            val bodyY  = cy - tailH * 0.45f

            drawCircle(color = Color(0xFFF59E0B), radius = r, center = Offset(cx, bodyY))
            val tailPath = Path().apply { moveTo(cx - r * 0.32f, bodyY + r * 0.72f); lineTo(cx, bodyY + r * 0.72f + tailH); lineTo(cx + r * 0.32f, bodyY + r * 0.72f); close() }
            drawPath(tailPath, color = Color(0xFFF59E0B))
            drawCircle(color = Color(0xFFB45309), radius = r, center = Offset(cx, bodyY), style = Stroke(width = 1.dp.toPx()))
        }
    }
}

data class PathPoint(val x: Float, val y: Float, val pressure: Float = 1f)
data class DrawnPath(val tool: Tool, val color: Color, val points: MutableList<PathPoint> = mutableListOf())

fun DrawScope.drawDrawnPath(path: DrawnPath) {
    if (path.points.size < 2) return
    val baseWidth = when(path.tool) { Tool.PEN -> 3f; Tool.PENCIL -> 4f; Tool.HIGHLIGHTER -> 25f; Tool.ERASER -> 30f; else -> 2f }
    // A borracha usa BlendMode.Clear para apagar os pixels do próprio layer de anotações,
    // sem afetar a imagem do PDF que está numa camada inferior.
    val alpha = when (path.tool) { Tool.HIGHLIGHTER -> 0.35f; Tool.ERASER -> 1f; else -> 0.9f }
    val blend = when (path.tool) { Tool.HIGHLIGHTER -> BlendMode.Multiply; Tool.ERASER -> BlendMode.Clear; else -> BlendMode.SrcOver }

    if (path.tool == Tool.HIGHLIGHTER || path.tool == Tool.ERASER) {
        // Marca-texto e borracha: um único drawPath para que alpha/blendMode sejam aplicados
        // UMA SÓ VEZ em todo o traço — evita o artefato de "bolinhas" nas sobreposições de segmentos.
        val shapePath = androidx.compose.ui.graphics.Path().apply {
            moveTo(path.points[0].x, path.points[0].y)
            for (i in 1 until path.points.size) {
                lineTo(path.points[i].x, path.points[i].y)
            }
        }
        drawPath(
            path = shapePath,
            color = if (path.tool == Tool.ERASER) Color.Transparent else path.color,
            alpha = alpha,
            style = Stroke(width = baseWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            blendMode = blend
        )
    } else {
        // Caneta e lápis: segmentos individuais para suportar variação de pressão por ponto.
        for (i in 1 until path.points.size) {
            val p1 = path.points[i-1]; val p2 = path.points[i]
            val pFact = if (path.tool == Tool.PEN) (0.5f + 1.5f * p2.pressure).coerceIn(0.5f, 2.5f) else 1f
            drawLine(
                color = path.color,
                start = Offset(p1.x, p1.y), end = Offset(p2.x, p2.y),
                strokeWidth = baseWidth * pFact, cap = StrokeCap.Round, alpha = alpha, blendMode = blend
            )
        }
    }
}

fun List<DrawnPath>.toJson() = JSONArray(map { p -> mapOf("tool" to p.tool.name, "color" to p.color.value.toString(), "points" to p.points.map { mapOf("x" to it.x, "y" to it.y, "p" to it.pressure) }) }).toString()

fun parseDrawingsJson(json: String): List<DrawnPath> {
    val res = mutableListOf<DrawnPath>()
    try {
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val tool = Tool.valueOf(obj.getString("tool"))
            val color = Color(obj.getString("color").toULong())
            val ptsArr = obj.getJSONArray("points"); val pts = mutableListOf<PathPoint>()
            for (j in 0 until ptsArr.length()) { 
                val p = ptsArr.getJSONObject(j)
                val pressure = if (p.has("p")) p.getDouble("p").toFloat() else 1f
                pts.add(PathPoint(p.getDouble("x").toFloat(), p.getDouble("y").toFloat(), pressure)) 
            }
            res.add(DrawnPath(tool, color, pts))
        }
    } catch(e: Exception) {}
    return res
}