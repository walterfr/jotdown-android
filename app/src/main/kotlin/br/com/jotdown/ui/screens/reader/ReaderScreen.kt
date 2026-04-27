package br.com.jotdown.ui.screens.reader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.jotdown.data.entity.AnnotationEntity
import br.com.jotdown.data.entity.DrawingEntity
import br.com.jotdown.ui.theme.Indigo600
import br.com.jotdown.ui.viewmodel.ReaderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

enum class Tool { NONE, PEN, PENCIL, HIGHLIGHTER, ERASER, ANNOTATION, SELECT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(viewModel: ReaderViewModel, onBack: () -> Unit) {
    val pdfFile by viewModel.pdfFile.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()
    val activeTool by viewModel.activeTool.collectAsState()
    val strokeColor by viewModel.strokeColor.collectAsState()
    val annotations by viewModel.annotations.collectAsState()
    val highlights by viewModel.highlights.collectAsState()
    val drawings by viewModel.drawings.collectAsState()
    val document by viewModel.document.collectAsState()

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pdf_prefs", android.content.Context.MODE_PRIVATE) }
    val docId = document?.id ?: ""
    
    var numPages by remember { mutableIntStateOf(0) }
    var scrollToPage by remember { mutableIntStateOf(0) }
    var pageOffset by remember(docId) { mutableIntStateOf(if (docId.isNotBlank()) prefs.getInt("offset_$docId", 0) else 0) }
    var showOffsetDialog by remember { mutableStateOf(false) }
    
    // Ã°Å¸â€ºÂ¡Ã¯Â¸Â UNDO / REDO STATE
    var undoTrigger by remember { mutableLongStateOf(0L) }
    var redoTrigger by remember { mutableLongStateOf(0L) }

    LaunchedEffect(pdfFile) {
        val file = pdfFile ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                numPages = renderer.pageCount
                renderer.close(); fd.close()
            } catch (e: Exception) {}
        }
    }

    // Ã°Å¸â€ºÂ¡Ã¯Â¸Â CORREÃƒâ€¡ÃƒÆ’O MESTRE: Carregar ÃƒÂºltima pÃƒÂ¡gina lida
    LaunchedEffect(numPages, docId) {
        if (numPages > 0 && docId.isNotBlank()) {
            val last = prefs.getInt("last_$docId", 1)
            if (last > 1) { scrollToPage = last }
        }
    }

    Scaffold(
        topBar = {
            ReaderTopBar(
                activeTool = activeTool, strokeColor = strokeColor, annotationCount = annotations.size,
                onBack = onBack, onMenuClick = { }, onToolSelect = { viewModel.toggleTool(it) },
                onColorSelect = { viewModel.setStrokeColor(it) }, onAnnotations = { }, onCapture = { },
                onUndo = { undoTrigger = System.currentTimeMillis() }, onRedo = { redoTrigger = System.currentTimeMillis() }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (pdfFile != null && numPages > 0) {
                PdfViewer(
                    pdfFile = pdfFile!!, numPages = numPages, currentPage = currentPage, activeTool = activeTool, 
                    strokeColor = strokeColor, annotations = annotations, drawings = drawings, 
                    scrollToPage = scrollToPage, undoTrigger = undoTrigger, redoTrigger = redoTrigger,
                    onScrollDone = { scrollToPage = 0 }, 
                    onPageChange = { 
                        viewModel.setCurrentPage(it)
                        if (docId.isNotBlank()) prefs.edit().putInt("last_$docId", it).apply()
                    },
                    onSaveDrawing = { page, json -> viewModel.saveDrawing(page, json) }
                )
            }

            // Ã°Å¸â€ºÂ¡Ã¯Â¸Â NAVEGAÃƒâ€¡ÃƒÆ’O: SLIDER + CONTADOR
            if (numPages > 0) {
                Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Slider(
                        value = currentPage.toFloat(),
                        onValueChange = { scrollToPage = it.toInt() },
                        valueRange = 1f..numPages.toFloat(),
                        modifier = Modifier.width(300.dp).padding(bottom = 8.dp)
                    )
                    Surface(
                        onClick = { showOffsetDialog = true },
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text("PDF $currentPage de $numPages", color = Color.Gray, fontSize = 10.sp)
                            Text("Doc ${currentPage + pageOffset} de $numPages", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    
    if (showOffsetDialog) {
        var input by remember { mutableStateOf((currentPage + pageOffset).toString()) }
        AlertDialog(
            onDismissRequest = { showOffsetDialog = false },
            title = { Text("Sincronizar P\u00e1gina") },
            text = { OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("N\u00famero impresso na folha") }) },
            confirmButton = { Button(onClick = { pageOffset = (input.toIntOrNull() ?: currentPage) - currentPage; prefs.edit().putInt("offset_$docId", pageOffset).apply(); showOffsetDialog = false }) { Text("Ok") } }
        )
    }
}

@Composable
fun PdfViewer(
    pdfFile: File, numPages: Int, currentPage: Int, activeTool: Tool, strokeColor: Int, 
    annotations: List<AnnotationEntity>, drawings: List<DrawingEntity>,
    scrollToPage: Int, undoTrigger: Long, redoTrigger: Long,
    onScrollDone: () -> Unit, onPageChange: (Int) -> Unit, onSaveDrawing: (Int, String) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToPage) { if (scrollToPage in 1..numPages) { listState.scrollToItem(scrollToPage - 1); onScrollDone() } }
    LaunchedEffect(listState.firstVisibleItemIndex) { onPageChange(listState.firstVisibleItemIndex + 1) }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(Color(0xFFF0F0F7))) {
        items(numPages) { index ->
            PdfPage(
                pdfFile = pdfFile, pageNumber = index + 1, activeTool = activeTool, strokeColor = strokeColor,
                pageDrawingsJson = drawings.find { it.page == index + 1 }?.pathsJson,
                undoTrigger = undoTrigger, redoTrigger = redoTrigger,
                onSaveDrawing = { onSaveDrawing(index + 1, it) }
            )
        }
    }
}

@Composable
fun PdfPage(
    pdfFile: File, pageNumber: Int, activeTool: Tool, strokeColor: Int,
    pageDrawingsJson: String?, undoTrigger: Long, redoTrigger: Long, onSaveDrawing: (String) -> Unit
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var widthPx by remember { mutableIntStateOf(0) }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        LaunchedEffect(widthPx) {
            withContext(Dispatchers.IO) {
                try {
                    val fd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(fd); val page = renderer.openPage(pageNumber - 1)
                    val h = (page.height * (widthPx.toFloat() / page.width)).toInt()
                    val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap = bmp; page.close(); renderer.close(); fd.close()
                } catch (e: Exception) {}
            }
        }

        Card(elevation = CardDefaults.cardElevation(4.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                bitmap?.let { 
                    Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                    DrawingLayer(activeTool, Color(strokeColor), pageDrawingsJson, undoTrigger, redoTrigger, onSaveDrawing)
                } ?: Box(Modifier.fillMaxWidth().height(400.dp))
            }
        }
    }
}

@Composable
fun DrawingLayer(activeTool: Tool, strokeColor: Color, json: String?, undo: Long, redo: Long, onSave: (String) -> Unit) {
    val paths = remember { mutableStateListOf<DrawnPath>() }
    val redoStack = remember { mutableStateListOf<DrawnPath>() }
    var currentPath by remember { mutableStateOf<DrawnPath?>(null) }

    LaunchedEffect(json) {
        if (!json.isNullOrBlank() && paths.isEmpty()) { paths.addAll(parseDrawingsJson(json)) }
    }

    // Ã°Å¸â€ºÂ¡Ã¯Â¸Â LÃƒâ€œGICA UNDO / REDO
    LaunchedEffect(undo) { if (undo > 0 && paths.isNotEmpty()) { redoStack.add(paths.removeAt(paths.size - 1)); onSave(paths.toJson()) } }
    LaunchedEffect(redo) { if (redo > 0 && redoStack.isNotEmpty()) { paths.add(redoStack.removeAt(redoStack.size - 1)); onSave(paths.toJson()) } }

    Canvas(modifier = Modifier.fillMaxSize().pointerInput(activeTool, strokeColor) {
        if (activeTool == Tool.NONE || activeTool == Tool.ANNOTATION || activeTool == Tool.SELECT) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown()
            // Trava de Stylus removida para permitir qualquer tipo de toque
            redoStack.clear()
            var pathInProgress = DrawnPath(activeTool, strokeColor, mutableListOf(PathPoint(down.position.x, down.position.y, down.pressure)))
            currentPath = pathInProgress
            do {
                val event = awaitPointerEvent(); val drag = event.changes.firstOrNull { it.id == down.id }
                if (drag != null && drag.pressed) {
                    drag.consume()
                    val pts = ArrayList(pathInProgress.points).apply { add(PathPoint(drag.position.x, drag.position.y, drag.pressure)) }
                    pathInProgress = pathInProgress.copy(points = pts); currentPath = pathInProgress
                }
            } while (drag != null && drag.pressed)
            currentPath?.let { paths.add(it); onSave(paths.toJson()) }; currentPath = null
        }
    }) {
        paths.forEach { drawDrawnPath(it) }
        currentPath?.let { drawDrawnPath(it) }
    }
}

data class PathPoint(val x: Float, val y: Float, val pressure: Float)
data class DrawnPath(val tool: Tool, val color: Color, val points: MutableList<PathPoint>)

fun DrawScope.drawDrawnPath(path: DrawnPath) {
    if (path.points.size < 2) return
    val baseWidth = when(path.tool) { Tool.PEN -> 3f; Tool.PENCIL -> 4f; Tool.HIGHLIGHTER -> 25f; Tool.ERASER -> 30f; else -> 2f }
    
    // Ã°Å¸â€ºÂ¡Ã¯Â¸Â CORREÃƒâ€¡ÃƒÆ’O MESTRE: Marca-texto translÃƒÂºcido com BlendMode
    val alpha = if (path.tool == Tool.HIGHLIGHTER) 0.35f else 0.9f
    val blend = if (path.tool == Tool.HIGHLIGHTER) BlendMode.Multiply else BlendMode.SrcOver

    for (i in 1 until path.points.size) {
        val p1 = path.points[i-1]; val p2 = path.points[i]
        val pFact = if (path.tool == Tool.PEN) (0.5f + 1.5f * p2.pressure).coerceIn(0.5f, 2.5f) else 1f
        drawLine(
            color = if (path.tool == Tool.ERASER) Color.White else path.color,
            start = Offset(p1.x, p1.y), end = Offset(p2.x, p2.y),
            strokeWidth = baseWidth * pFact, cap = StrokeCap.Round,
            alpha = alpha, blendMode = blend
        )
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
            for (j in 0 until ptsArr.length()) { val p = ptsArr.getJSONObject(j); pts.add(PathPoint(p.getDouble("x").toFloat(), p.getDouble("y").toFloat(), p.getDouble("p").toFloat())) }
            res.add(DrawnPath(tool, color, pts))
        }
    } catch(e: Exception) {}
    return res
}