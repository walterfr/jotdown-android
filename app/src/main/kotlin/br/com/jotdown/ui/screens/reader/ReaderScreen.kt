package br.com.jotdown.ui.screens.reader

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import br.com.jotdown.MultiReaderActivity
import br.com.jotdown.R
import br.com.jotdown.data.entity.*
import br.com.jotdown.ui.theme.*
import br.com.jotdown.ui.viewmodel.*
import br.com.jotdown.util.AbntUtil
import br.com.jotdown.util.AudioNoteManager
import br.com.jotdown.util.AudioSyncUtil
import br.com.jotdown.util.BiometricLockUtil
import br.com.jotdown.util.ExportUtil
import br.com.jotdown.util.GeomPoint
import br.com.jotdown.util.GeomRect
import br.com.jotdown.util.LocalStylusPredictor
import br.com.jotdown.util.NormRect
import br.com.jotdown.util.OcrUtil
import br.com.jotdown.util.PagePaddingSpec
import br.com.jotdown.util.PaperTemplate
import br.com.jotdown.util.PdfPageTemplateUtil
import br.com.jotdown.util.PdfTextLayerUtil
import br.com.jotdown.util.PdfVectorExportUtil
import br.com.jotdown.util.PenBoxPresetUtil
import br.com.jotdown.util.ReadingThemeSpec
import br.com.jotdown.util.ShapeRecognitionUtil
import br.com.jotdown.util.StrokeGeometryUtil
import br.com.jotdown.util.StrokeSmoothingUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.File
import kotlin.math.roundToInt

enum class Tool { NONE, SELECT, PEN, PENCIL, HIGHLIGHTER, ERASER, LASSO, TAPE, ANNOTATION, DICTIONARY }
enum class EraserMode { STROKE, PIXEL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onFullscreenChanged: (Boolean) -> Unit = {},
    hasTopTabsBar: Boolean = false,
) {
    val pdfFile         by viewModel.pdfFile.collectAsState()
    val currentPage     by viewModel.currentPage.collectAsState()
    val activeTool      by viewModel.activeTool.collectAsState()
    val strokeColor     by viewModel.strokeColor.collectAsState()
    val annotations     by viewModel.annotations.collectAsState()
    val highlights      by viewModel.highlights.collectAsState()
    val drawings        by viewModel.drawings.collectAsState()
    val bookmarks       by viewModel.bookmarks.collectAsState()
    val audioRecordings by viewModel.audioRecordings.collectAsState()
    val allDocuments    by viewModel.allDocuments.collectAsState()
    val document        by viewModel.document.collectAsState()
    val strokeWidthMultiplier by viewModel.strokeWidthMultiplier.collectAsState()

    val bookmarkedPages = remember(bookmarks) { bookmarks.map { it.page }.toSet() }
    val isCurrentPageBookmarked = currentPage in bookmarkedPages

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pdf_prefs", android.content.Context.MODE_PRIVATE) }
    val docId = document?.id ?: ""

    var penPresets by remember {
        mutableStateOf(PenBoxPresetUtil.parsePresets(prefs.getString("penbox_presets", null)))
    }
    var tapeRevealAll by remember(docId) { mutableStateOf(false) }
    var revealedTapeByPage by remember(docId) { mutableStateOf<Map<Int, Set<Int>>>(emptyMap()) }
    var showSplitScreenPicker by remember { mutableStateOf(false) }

    val audioNoteManager = remember { AudioNoteManager() }
    DisposableEffect(Unit) {
        onDispose { audioNoteManager.releaseAll() }
    }
    val isRecordingAudio by audioNoteManager.isRecording.collectAsState()
    val activeRecordingId by audioNoteManager.recordingId.collectAsState()
    val recordingElapsedMs by audioNoteManager.recordingElapsedMs.collectAsState()
    val playingRecordingId by audioNoteManager.playingRecordingId.collectAsState()
    val isPlayingAudio by audioNoteManager.isPlaying.collectAsState()
    val playbackPositionMs by audioNoteManager.playbackPositionMs.collectAsState()
    val playbackDurationMs by audioNoteManager.playbackDurationMs.collectAsState()

    var showAudioBar by remember { mutableStateOf(false) }
    var selectedAudioRecordingId by remember(docId) { mutableStateOf<Long?>(null) }
    val selectedAudioRecording = remember(audioRecordings, selectedAudioRecordingId, playingRecordingId) {
        val targetId = playingRecordingId ?: selectedAudioRecordingId
        audioRecordings.find { it.id == targetId } ?: audioRecordings.firstOrNull()
    }
    var activeRecordingMeta by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var pendingRecordStart by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    fun beginAudioRecording() {
        if (docId.isBlank()) return
        val nextIndex = audioRecordings.size + 1
        val title = context.getString(R.string.audio_recording_title_prefix, nextIndex)
        val audioDir = File(context.filesDir, "audio/$docId").also { it.mkdirs() }
        val outFile = File(audioDir, "rec_${System.currentTimeMillis()}.m4a")
        coroutineScope.launch {
            val recId = viewModel.createAudioRecording(outFile.absolutePath, title)
            if (recId > 0L) {
                val started = audioNoteManager.startRecording(context, recId, outFile)
                if (started) {
                    activeRecordingMeta = recId to title
                    selectedAudioRecordingId = recId
                    showAudioBar = true
                } else {
                    viewModel.deleteAudioRecording(
                        AudioRecordingEntity(id = recId, documentId = docId, filePath = outFile.absolutePath, title = title)
                    )
                    Toast.makeText(context, context.getString(R.string.audio_recording_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingRecordStart) {
            pendingRecordStart = false
            beginAudioRecording()
        } else {
            pendingRecordStart = false
            if (!granted) {
                Toast.makeText(context, context.getString(R.string.audio_permission_denied), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleAudioRecord() {
        if (isRecordingAudio) {
            val durMs = audioNoteManager.stopRecording()
            val meta = activeRecordingMeta
            if (meta != null) {
                viewModel.updateAudioRecording(meta.first, durMs, meta.second)
            }
            activeRecordingMeta = null
        } else {
            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                beginAudioRecording()
            } else {
                pendingRecordStart = true
                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    fun seekAndPlayAudio(recordingId: Long, positionMs: Long) {
        val rec = audioRecordings.find { it.id == recordingId } ?: return
        selectedAudioRecordingId = rec.id
        showAudioBar = true
        audioNoteManager.playRecording(rec.id, File(rec.filePath), positionMs)
    }
    // Global toggles from Settings: passive pens report as Touch, so they need this.
    // Sub-options only take effect with drawWithFinger (same dependency as in Settings).
    val drawWithFinger = prefs.getBoolean("draw_with_finger", false)
    val doubleTapToDeselect = drawWithFinger && prefs.getBoolean("double_tap_to_deselect", false)
    val twoFingerScroll = drawWithFinger && prefs.getBoolean("two_finger_scroll", false)
    val twoThreeFingerUndo = prefs.getBoolean("two_three_finger_undo", true)
    val holdToShape = prefs.getBoolean("hold_to_shape", true)
    val stylusButtonAction = prefs.getString("stylus_button_action", "ERASER") ?: "ERASER"

    var eraserMode by remember {
        mutableStateOf(
            try {
                EraserMode.valueOf(prefs.getString("eraser_mode", EraserMode.STROKE.name) ?: EraserMode.STROKE.name)
            } catch (_: Exception) {
                EraserMode.STROKE
            }
        )
    }

    val pagePaddingMode = document?.pagePaddingMode ?: PagePaddingSpec.MODE_NONE
    val pagePaddingColor = document?.pagePaddingColor ?: PagePaddingSpec.COLOR_WHITE
    var pagePaddingDotted by remember(docId) {
        mutableStateOf(if (docId.isNotBlank()) prefs.getBoolean("padding_grid_$docId", true) else true)
    }

    val readingTheme = document?.readingTheme ?: ReadingThemeSpec.THEME_LIGHT
    val readingMode = document?.readingMode ?: ReadingThemeSpec.MODE_VERTICAL
    val readingThemeSpec = remember(readingTheme) { ReadingThemeSpec.fromId(readingTheme) }

    LaunchedEffect(document?.id) {
        val doc = document ?: return@LaunchedEffect
        val legacyKey = "dark_mode_${doc.id}"
        if (prefs.contains(legacyKey)) {
            val wasDark = prefs.getBoolean(legacyKey, false)
            if (wasDark && doc.readingTheme == ReadingThemeSpec.THEME_LIGHT) {
                viewModel.updateReadingTheme(ReadingThemeSpec.THEME_DARK)
            }
            prefs.edit().remove(legacyKey).apply()
        }
    }

    val initialScrollOffset = prefs.getInt("scroll_offset_$docId", 0)

    var numPages        by remember { mutableIntStateOf(0) }
    var showSidebar     by remember { mutableStateOf(false) }
    var showAnnotations by remember { mutableStateOf(false) }
    var showHighlightNote by remember { mutableStateOf(false) }
    var selectedHighlightId by remember { mutableStateOf<Long?>(null) }
    var editingHighlight by remember { mutableStateOf<HighlightEntity?>(null) }
    var showPagePaddingDialog by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var showPageGrid by remember { mutableStateOf(false) }
    var showInsertPageDialog by remember { mutableStateOf(false) }
    var isInsertingPage by remember { mutableStateOf(false) }
    var pdfReloadVersion by remember { mutableIntStateOf(0) }
    var editingBookmark by remember { mutableStateOf<BookmarkEntity?>(null) }
    var showAnnotatedPdfExportDialog by remember { mutableStateOf(false) }

    var ocrResult        by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var pendingOcrRectsJson by remember { mutableStateOf("") }
    var textToEdit       by remember { mutableStateOf("") }

    var pendingAnnotation by remember { mutableStateOf<Triple<Int, Float, Float>?>(null) }
    var pendingAnnotationAudio by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var editingAnnotation by remember { mutableStateOf<AnnotationEntity?>(null) }
    var annotationText    by remember { mutableStateOf("") }

    var scrollToPage by remember { mutableIntStateOf(0) }
    
    var pageOffset by remember(docId) { mutableIntStateOf(if (docId.isNotBlank()) prefs.getInt("page_offset_$docId", 0) else 0) }
    var showOffsetDialog by remember { mutableStateOf(false) }
    var undoTrigger by remember { mutableLongStateOf(0L) }
    var redoTrigger by remember { mutableLongStateOf(0L) }
    var lastEditedPage by remember { mutableIntStateOf(0) }

    // Estado do dicionário vem do ViewModel
    val dictionaryState by viewModel.dictionaryState.collectAsState()

    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    val renderMutex = remember { Mutex() }

    var isFullscreen by remember { mutableStateOf(false) }
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }
    LaunchedEffect(isFullscreen) {
        onFullscreenChanged(isFullscreen)
    }
    DisposableEffect(Unit) {
        onDispose { onFullscreenChanged(false) }
    }
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var pdfSearchResults by remember { mutableStateOf<List<br.com.jotdown.util.PdfSearchResult>>(emptyList()) }
    var isPdfSearching by remember { mutableStateOf(false) }
    var activeSearchQuery by remember { mutableStateOf("") }
    var activeSearchHits by remember { mutableStateOf<List<br.com.jotdown.util.PdfSearchResult>>(emptyList()) }
    var activeSearchIndex by remember { mutableIntStateOf(0) }
    var isExportingFlattenedPdf by remember { mutableStateOf(false) }
    var showAbntDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showOutline by remember { mutableStateOf(false) }
    var pdfBookmarks by remember { mutableStateOf<List<br.com.jotdown.util.PdfBookmark>>(emptyList()) }

    val isTextFormat = pdfFile?.name?.endsWith(".txt", true) == true || pdfFile?.name?.endsWith(".md", true) == true

    LaunchedEffect(isSearchActive, searchQuery, pdfFile) {
        val file = pdfFile
        if (!isSearchActive || searchQuery.trim().length < 3 || file == null || isTextFormat) {
            pdfSearchResults = emptyList()
            isPdfSearching = false
            return@LaunchedEffect
        }
        isPdfSearching = true
        kotlinx.coroutines.delay(350)
        pdfSearchResults = br.com.jotdown.util.PdfSearchUtil.search(file, searchQuery)
        isPdfSearching = false
    }

    LaunchedEffect(pdfFile, pdfReloadVersion) {
        val file = pdfFile ?: return@LaunchedEffect
        // Documentos txt/md são renderizados pelo TextReader; não abre PdfRenderer
        if (isTextFormat) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                renderMutex.withLock {
                    try { pdfRenderer?.close() } catch (_: Exception) {}
                    try { fileDescriptor?.close() } catch (_: Exception) {}
                    pdfRenderer = null
                    fileDescriptor = null

                    val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = try {
                        PdfRenderer(fd)
                    } catch (e: Exception) {
                        fd.close() // sem isso o descriptor vaza quando o arquivo não é um PDF válido
                        throw e
                    }
                    fileDescriptor = fd
                    pdfRenderer = renderer
                    numPages = renderer.pageCount
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // Sumário só quando pedido. Carregá-lo na abertura fazia o PDFBox percorrer
    // o arquivo inteiro: num PDF de 198 MB isso queima dezenas de segundos de CPU
    // e enche o heap de lixo, competindo com a renderização das páginas — era o
    // travamento que aparecia "depois de pouco tempo de leitura".
    var bookmarksLoading by remember { mutableStateOf(false) }
    LaunchedEffect(showOutline, pdfReloadVersion) {
        val file = pdfFile
        if (showOutline && pdfBookmarks.isEmpty() && file != null && !isTextFormat) {
            bookmarksLoading = true
            pdfBookmarks = br.com.jotdown.util.PdfOutlineUtil.getBookmarks(file)
            bookmarksLoading = false
        }
    }

    DisposableEffect(pdfFile) {
        onDispose {
            try { pdfRenderer?.close() } catch (_: Exception) {}
            try { fileDescriptor?.close() } catch (_: Exception) {}
            pdfRenderer = null
            fileDescriptor = null
        }
    }

    var hasRestoredInitialPage by remember(docId) { mutableStateOf(false) }
    LaunchedEffect(numPages, docId) {
        if (!hasRestoredInitialPage && numPages > 0 && docId.isNotBlank()) {
            hasRestoredInitialPage = true
            val last = prefs.getInt("last_$docId", 1).coerceIn(1, numPages)
            if (last > 1) { scrollToPage = last }
            
            // Registra a data de acesso atual (B8)
            viewModel.saveMetadata(
                document?.docType ?: "livro", document?.authorLastName ?: "", document?.authorFirstName ?: "", 
                document?.title ?: "", document?.subtitle ?: "", document?.edition ?: "", document?.city ?: "", 
                document?.publisher ?: "", document?.year ?: "", document?.journal ?: "", document?.volume ?: "", 
                document?.pages ?: "", document?.url ?: "", accessDate = System.currentTimeMillis().toString()
            )
        }
    }

    if (isTextFormat && pdfFile != null) {
        TextReader(
            filePath = pdfFile!!.absolutePath,
            title = document?.title.takeIf { !it.isNullOrBlank() } ?: document?.fileName ?: "Documento",
            onBack = onBack
        )
        return
    }

    Scaffold(
        contentWindowInsets = if (hasTopTabsBar) {
            ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        topBar = {
            AnimatedVisibility(
                visible = !isFullscreen,
                enter = slideInHorizontally() + fadeIn(),
                exit = slideOutHorizontally() + fadeOut()
            ) {
                ReaderTopBar(
                    annotationCount = annotations.size,
                    isFullscreen = isFullscreen,
                    isSearchActive = isSearchActive,
                    searchQuery = searchQuery,
                    onBack = onBack,
                    onMenuClick = { showSidebar = true },
                    onAnnotations = { showAnnotations = true },
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    onToggleSearch = { isSearchActive = !isSearchActive; if (!isSearchActive) searchQuery = "" },
                    onSearchQueryChange = { searchQuery = it },
                    onAbntClick = { showAbntDialog = true },
                    onExportClick = { showExportDialog = true },
                    readingTheme = readingTheme,
                    onOpenAppearance = { showAppearanceDialog = true },
                    onSharePdf = {
                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", pdfFile!!)
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.reader_share_pdf_chooser)))
                    },
                    onOutlineClick = { showOutline = true },
                    onExportAnnotatedPdf = { showAnnotatedPdfExportDialog = true },
                    onPagePaddingClick = { showPagePaddingDialog = true },
                    isCurrentPageBookmarked = isCurrentPageBookmarked,
                    onToggleBookmark = {
                        val wasBookmarked = isCurrentPageBookmarked
                        viewModel.toggleBookmark(currentPage)
                        val msg = if (wasBookmarked) {
                            context.getString(R.string.bookmark_removed_toast)
                        } else {
                            context.getString(R.string.bookmark_added_toast, currentPage + pageOffset)
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                    onOpenPageGrid = { showPageGrid = true },
                    isAudioBarVisible = showAudioBar || isRecordingAudio || isPlayingAudio,
                    isRecordingAudio = isRecordingAudio,
                    onToggleAudioBar = { showAudioBar = !showAudioBar },
                    onOpenSplitScreen = { showSplitScreenPicker = true },
                    windowInsets = if (hasTopTabsBar) {
                        TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Horizontal)
                    } else {
                        TopAppBarDefaults.windowInsets
                    },
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = !isFullscreen,
                enter = slideInHorizontally() + fadeIn(),
                exit = slideOutHorizontally() + fadeOut()
            ) {
                ReaderToolsBar(
                    activeTool            = activeTool,
                    strokeColor           = strokeColor,
                    strokeWidthMultiplier = strokeWidthMultiplier,
                    onToolSelect          = { viewModel.toggleTool(it) },
                    onColorSelect         = { viewModel.setStrokeColor(it) },
                    onWidthSelect         = { viewModel.setStrokeWidthMultiplier(it) },
                    onUndo                = { undoTrigger = System.nanoTime() },
                    onRedo                = { redoTrigger = System.nanoTime() },
                    eraserMode            = eraserMode,
                    onEraserModeSelect    = { mode ->
                        eraserMode = mode
                        prefs.edit().putString("eraser_mode", mode.name).apply()
                    },
                    penPresets            = penPresets,
                    onSelectPreset        = { preset ->
                        runCatching { Tool.valueOf(preset.toolName) }.getOrNull()?.let { tool ->
                            viewModel.setActiveTool(tool)
                            viewModel.setStrokeColor(preset.colorArgb)
                            viewModel.setStrokeWidthMultiplier(preset.widthMultiplier)
                        }
                    },
                    onAddCurrentPreset    = {
                        val updated = PenBoxPresetUtil.addPreset(
                            existing = penPresets,
                            toolName = activeTool.name,
                            colorArgb = strokeColor,
                            widthMultiplier = strokeWidthMultiplier
                        )
                        if (updated != penPresets) {
                            penPresets = updated
                            prefs.edit().putString("penbox_presets", PenBoxPresetUtil.serializePresets(updated)).apply()
                            Toast.makeText(context, context.getString(R.string.penbox_saved_toast), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRemovePreset        = { presetId ->
                        val updated = PenBoxPresetUtil.removePreset(penPresets, presetId)
                        penPresets = updated
                        prefs.edit().putString("penbox_presets", PenBoxPresetUtil.serializePresets(updated)).apply()
                        Toast.makeText(context, context.getString(R.string.penbox_removed_toast), Toast.LENGTH_SHORT).show()
                    },
                    tapeRevealAll         = tapeRevealAll,
                    onToggleTapeRevealAll = {
                        tapeRevealAll = !tapeRevealAll
                        if (!tapeRevealAll) {
                            revealedTapeByPage = emptyMap()
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(if (isFullscreen) PaddingValues(0.dp) else padding)) {
            val file = pdfFile
            if (file != null && numPages > 0) {
                val targetUndoPage = lastEditedPage.takeIf { it in 1..numPages } ?: currentPage
                PdfViewer(
                    pdfFile         = file,
                    numPages        = numPages,
                    currentPage     = currentPage,
                    activeTool      = activeTool,
                    strokeColor     = strokeColor,
                    annotations     = annotations,
                    highlights      = highlights,
                    drawings        = drawings,
                    scrollToPage    = scrollToPage,
                    undoTrigger          = undoTrigger,
                    redoTrigger          = redoTrigger,
                    targetUndoPage  = targetUndoPage,
                    strokeWidthMultiplier = strokeWidthMultiplier,
                    pdfRenderer          = pdfRenderer,
                    renderMutex     = renderMutex,
                    readingThemeSpec = readingThemeSpec,
                    readingMode     = readingMode,
                    pdfReloadVersion = pdfReloadVersion,
                    holdToShape     = holdToShape,
                    initialScrollOffset = initialScrollOffset,
                    pagePaddingMode = pagePaddingMode,
                    pagePaddingColor = pagePaddingColor,
                    pagePaddingDotted = pagePaddingDotted,
                    activeSearchQuery = activeSearchQuery,
                    eraserMode      = eraserMode,
                    twoThreeFingerUndo = twoThreeFingerUndo,
                    stylusButtonAction = stylusButtonAction,
                    drawWithFinger  = drawWithFinger,
                    doubleTapToDeselect = doubleTapToDeselect,
                    twoFingerScroll = twoFingerScroll,
                    activeRecordingId = activeRecordingId,
                    recordingElapsedMs = recordingElapsedMs,
                    playingRecordingId = playingRecordingId,
                    playbackPositionMs = playbackPositionMs,
                    tapeRevealAll = tapeRevealAll,
                    revealedTapeByPage = revealedTapeByPage,
                    onToggleTapeReveal = { page, strokeIdx ->
                        val currentSet = revealedTapeByPage[page].orEmpty()
                        val updatedSet = if (strokeIdx in currentSet) currentSet - strokeIdx else currentSet + strokeIdx
                        revealedTapeByPage = revealedTapeByPage.toMutableMap().apply { put(page, updatedSet) }
                    },
                    onSeekAudioFromStroke = { audioId, timestampMs ->
                        seekAndPlayAudio(audioId, timestampMs)
                    },
                    onDoubleTapDeselect = { viewModel.setActiveTool(Tool.NONE) },
                    onScrollDone    = { scrollToPage = 0 },
                    onOcrSuccess    = { page, text, rectsJson ->
                        textToEdit = text
                        pendingOcrRectsJson = rectsJson
                        ocrResult = Pair(page, text)
                    },
                    onQuickHighlight = { page, text, rectsJson, style, colorHex ->
                        viewModel.addHighlight(page, text, rectsJson = rectsJson, style = style, color = colorHex)
                        viewModel.setActiveTool(Tool.NONE)
                    },
                    onTapHighlight = { hl -> editingHighlight = hl },
                    onDictionaryRequest = { _, text -> viewModel.captureForDictionary(text) },
                    onScrollChange  = { page, offset ->
                        viewModel.setCurrentPage(page)
                        if (docId.isNotBlank()) {
                            prefs.edit()
                                .putInt("last_$docId", page)
                                .putInt("scroll_offset_$docId", offset)
                                .apply()
                        }
                    },
                    onAddAnnotation = { page, x, y ->
                        pendingAnnotation = Triple(page, x, y)
                        pendingAnnotationAudio = activeRecordingId?.let { it to recordingElapsedMs }
                        annotationText = ""
                    },
                    onOpenAnnotation = { annot ->
                        editingAnnotation = annot
                        annotationText = annot.text
                    },
                    onSaveDrawing   = { page, json ->
                        lastEditedPage = page
                        viewModel.saveDrawing(page, json)
                    },
                    onUndo = { undoTrigger = System.nanoTime() },
                    onRedo = { redoTrigger = System.nanoTime() },
                    onToggleFullscreen = { isFullscreen = !isFullscreen }
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF4F46E5))
                }
            }

            AnimatedVisibility(
                visible = (showAudioBar || isRecordingAudio || isPlayingAudio) && !isFullscreen,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                AudioNotesBar(
                    recordings = audioRecordings,
                    selectedRecording = selectedAudioRecording,
                    isRecording = isRecordingAudio,
                    recordingElapsedMs = recordingElapsedMs,
                    playingRecordingId = playingRecordingId,
                    isPlaying = isPlayingAudio,
                    playbackPositionMs = playbackPositionMs,
                    playbackDurationMs = playbackDurationMs,
                    onSelectRecording = { rec ->
                        selectedAudioRecordingId = rec.id
                        audioNoteManager.playRecording(rec.id, File(rec.filePath), 0L)
                    },
                    onToggleRecord = { toggleAudioRecord() },
                    onTogglePlayPause = {
                        val rec = selectedAudioRecording ?: return@AudioNotesBar
                        if (playingRecordingId == rec.id) {
                            audioNoteManager.togglePlayPause()
                        } else {
                            audioNoteManager.playRecording(rec.id, File(rec.filePath), 0L)
                        }
                    },
                    onSeekTo = { posMs -> audioNoteManager.seekTo(posMs) },
                    onDeleteRecording = { rec ->
                        if (playingRecordingId == rec.id) {
                            audioNoteManager.stopPlayback()
                        }
                        if (selectedAudioRecordingId == rec.id) {
                            selectedAudioRecordingId = null
                        }
                        viewModel.deleteAudioRecording(rec)
                    },
                    onClose = { showAudioBar = false }
                )
            }

            // ── Overlay de Busca Offline (I1) ──────────────────────────────────
            AnimatedVisibility(
                visible = isSearchActive && searchQuery.isNotBlank(),
                enter = fadeIn(), exit = fadeOut()
            ) {
                val matchingAnns = annotations.filter { it.text.contains(searchQuery, ignoreCase = true) }
                val matchingHighs = highlights.filter { it.text.contains(searchQuery, ignoreCase = true) }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(16.dp)) {
                        if (isPdfSearching) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                                    Text(stringResource(R.string.reader_pdf_searching), color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                        if (matchingAnns.isEmpty() && matchingHighs.isEmpty() && pdfSearchResults.isEmpty() && !isPdfSearching) {
                            item { Text(stringResource(R.string.reader_no_results), modifier = Modifier.padding(16.dp)) }
                        }
                        if (pdfSearchResults.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.reader_pdf_text_results, pdfSearchResults.size),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                            items(pdfSearchResults.size) { i ->
                                val res = pdfSearchResults[i]
                                Card(
                                    onClick = {
                                        activeSearchQuery = searchQuery.trim()
                                        activeSearchHits = pdfSearchResults
                                        activeSearchIndex = i
                                        scrollToPage = res.page
                                        isSearchActive = false
                                        searchQuery = ""
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(stringResource(R.string.reader_page_n, res.page), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                        Text(res.snippet, maxLines = 3, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                        if (matchingAnns.isNotEmpty()) {
                            item { Text(stringResource(R.string.reader_annotations_postits), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp)) }
                            items(matchingAnns.size) { i ->
                                val a = matchingAnns[i]
                                Card(
                                    onClick = { scrollToPage = a.page; isSearchActive = false; searchQuery = "" },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(stringResource(R.string.reader_page_n, a.page), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                        Text(a.text, maxLines = 3, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                        if (matchingHighs.isNotEmpty()) {
                            item { Text(stringResource(R.string.reader_highlights), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp)) }
                            items(matchingHighs.size) { i ->
                                val h = matchingHighs[i]
                                Card(
                                    onClick = { scrollToPage = h.page; isSearchActive = false; searchQuery = "" },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text(stringResource(R.string.reader_page_n, h.page), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                        Text(h.text, maxLines = 3, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (activeSearchQuery.isNotBlank() && activeSearchHits.isNotEmpty() && !isSearchActive && activeTool != Tool.ANNOTATION) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                val prev = if (activeSearchIndex > 0) activeSearchIndex - 1 else activeSearchHits.lastIndex
                                activeSearchIndex = prev
                                scrollToPage = activeSearchHits[prev].page
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                contentDescription = stringResource(R.string.search_prev_match),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.search_match_counter, activeSearchIndex + 1, activeSearchHits.size),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        IconButton(
                            onClick = {
                                val next = if (activeSearchIndex < activeSearchHits.lastIndex) activeSearchIndex + 1 else 0
                                activeSearchIndex = next
                                scrollToPage = activeSearchHits[next].page
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = stringResource(R.string.search_next_match),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                activeSearchQuery = ""
                                activeSearchHits = emptyList()
                                activeSearchIndex = 0
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.search_clear_highlights),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
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
                        Text(stringResource(R.string.reader_tap_to_annotate), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            AnimatedVisibility(
                visible = !isFullscreen && numPages > 0,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
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
                            Text(stringResource(R.string.reader_pdf_page_of, currentPage, numPages), color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f), fontSize = 10.sp)
                            Text(stringResource(R.string.reader_doc_page_of, currentPage + pageOffset, numPages), color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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

            // Botão flutuante para sair da tela cheia e restaurar as barras de ferramentas
            AnimatedVisibility(
                visible = isFullscreen,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .then(if (hasTopTabsBar) Modifier else Modifier.statusBarsPadding())
                    .padding(16.dp)
            ) {
                FilledTonalIconButton(
                    onClick = { isFullscreen = false },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = stringResource(R.string.reader_exit_fullscreen)
                    )
                }
            }
        }
    }

    if (showOffsetDialog) {
        var input by remember { mutableStateOf((currentPage + pageOffset).toString()) }
        AlertDialog(
            onDismissRequest = { showOffsetDialog = false },
            title = { Text(stringResource(R.string.reader_sync_page_title)) },
            text = { OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text(stringResource(R.string.reader_printed_page_number)) }) },
            confirmButton = { Button(onClick = { pageOffset = (input.toIntOrNull() ?: currentPage) - currentPage; prefs.edit().putInt("page_offset_$docId", pageOffset).apply(); showOffsetDialog = false }) { Text(stringResource(R.string.common_ok)) } }
        )
    }

    if (showSidebar) {
        val doiImportStatus by viewModel.doiImportStatus.collectAsState()
        val isbnImportStatus by viewModel.isbnImportStatus.collectAsState()
        MetadataSheet(
            document = document,
            highlights = highlights,
            pageOffset = pageOffset,
            onSave = { p -> viewModel.saveMetadata(p.docType, p.authorLastName, p.authorFirstName, p.title, p.subtitle, p.edition, p.city, p.publisher, p.year, p.journal, p.volume, p.pages, p.url, p.accessDate, p.doi, p.isbn) },
            onUpdateHighlight = { id, text -> viewModel.updateHighlight(id, text) },
            onDeleteHighlight = { id -> viewModel.deleteHighlight(id) },
            onDismiss = { showSidebar = false },
            onImportDOI = { doi -> viewModel.importDOI(doi) },
            doiImportStatus = doiImportStatus,
            onImportISBN = { isbn -> viewModel.importISBN(isbn) },
            isbnImportStatus = isbnImportStatus,
            onEditHighlightNote = { id -> selectedHighlightId = id; showHighlightNote = true }
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

    if (showHighlightNote && selectedHighlightId != null) {
        val target = highlights.find { it.id == selectedHighlightId }
        if (target != null) {
            HighlightNoteDialog(
                quote = target.text,
                initialNote = target.note,
                onSave = { texto -> viewModel.setHighlightNote(target.id, texto) },
                onDismiss = { showHighlightNote = false; selectedHighlightId = null }
            )
        }
    }

    if (ocrResult != null) {
        AlertDialog(
            onDismissRequest = { ocrResult = null; pendingOcrRectsJson = "" },
            title = { Text(stringResource(R.string.reader_review_ocr_title)) },
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
                        viewModel.addHighlight(
                            page = ocrResult!!.first,
                            text = textToEdit.trim(),
                            rectsJson = pendingOcrRectsJson
                        )
                        ocrResult = null
                        pendingOcrRectsJson = ""
                        viewModel.setActiveTool(Tool.NONE)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
                ) { Text(stringResource(R.string.reader_save_highlight)) }
            },
            dismissButton = {
                TextButton(onClick = { ocrResult = null; pendingOcrRectsJson = ""; viewModel.setActiveTool(Tool.NONE) }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (editingHighlight != null) {
        val hl = editingHighlight!!
        AlertDialog(
            onDismissRequest = { editingHighlight = null },
            title = { Text(stringResource(R.string.highlight_actions_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = hl.text,
                        fontSize = 13.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (hl.note.isNotBlank()) {
                        HorizontalDivider()
                        Text(
                            text = hl.note,
                            fontSize = 13.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedHighlightId = hl.id
                        showHighlightNote = true
                        editingHighlight = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
                ) { Text(stringResource(R.string.highlight_action_edit_note)) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.deleteHighlight(hl.id)
                            editingHighlight = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.common_delete)) }
                    TextButton(onClick = { editingHighlight = null }) {
                        Text(stringResource(R.string.common_close))
                    }
                }
            }
        )
    }

    if (showAnnotatedPdfExportDialog) {
        val errorMsg = stringResource(R.string.export_annotated_pdf_error)
        val shareChooserTitle = stringResource(R.string.reader_share_pdf_chooser)
        AlertDialog(
            onDismissRequest = { showAnnotatedPdfExportDialog = false },
            title = { Text(stringResource(R.string.export_annotated_pdf_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedCard(
                        onClick = {
                            val file = pdfFile
                            showAnnotatedPdfExportDialog = false
                            if (file != null && !isExportingFlattenedPdf) {
                                isExportingFlattenedPdf = true
                                coroutineScope.launch {
                                    val vectorFile = PdfVectorExportUtil.exportVectorPdf(
                                        context = context,
                                        pdfFile = file,
                                        drawings = drawings,
                                        annotations = annotations,
                                        highlights = highlights,
                                        title = document?.title ?: file.nameWithoutExtension,
                                        pagePaddingMode = pagePaddingMode,
                                        pagePaddingColor = pagePaddingColor
                                    )
                                    isExportingFlattenedPdf = false
                                    if (vectorFile != null) {
                                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", vectorFile)
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(intent, shareChooserTitle))
                                    } else {
                                        Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.EditNote, contentDescription = null, tint = Indigo600)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.export_vector_pdf_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(stringResource(R.string.export_vector_pdf_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    OutlinedCard(
                        onClick = {
                            val file = pdfFile
                            showAnnotatedPdfExportDialog = false
                            if (file != null && !isExportingFlattenedPdf) {
                                isExportingFlattenedPdf = true
                                coroutineScope.launch {
                                    val flatFile = br.com.jotdown.util.PdfFlattenUtil.flattenPdf(
                                        context = context,
                                        pdfFile = file,
                                        drawings = drawings,
                                        annotations = annotations,
                                        title = document?.title ?: file.nameWithoutExtension,
                                        highlights = highlights,
                                        pagePaddingMode = pagePaddingMode,
                                        pagePaddingColor = pagePaddingColor
                                    )
                                    isExportingFlattenedPdf = false
                                    if (flatFile != null) {
                                        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", flatFile)
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "application/pdf"
                                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(android.content.Intent.createChooser(intent, shareChooserTitle))
                                    } else {
                                        Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.export_flattened_pdf_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(stringResource(R.string.export_flattened_pdf_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAnnotatedPdfExportDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showPagePaddingDialog) {
        PagePaddingDialog(
            initialMode = pagePaddingMode,
            initialColor = pagePaddingColor,
            initialDotted = pagePaddingDotted,
            onConfirm = { mode, color, dotted ->
                viewModel.updatePagePadding(mode, color)
                pagePaddingDotted = dotted
                if (docId.isNotBlank()) {
                    prefs.edit().putBoolean("padding_grid_$docId", dotted).apply()
                }
                showPagePaddingDialog = false
            },
            onDismiss = { showPagePaddingDialog = false }
        )
    }

    // Diálogo do Dicionário — estado real do ViewModel
    val dictState = dictionaryState

    if (dictState != br.com.jotdown.ui.viewmodel.DictionaryLookupState.Idle) {
        AlertDialog(
            onDismissRequest = { viewModel.clearDictionaryState() },
            title = {
                Text(
                    text = stringResource(R.string.dict_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                val currentLang by viewModel.dictionaryLanguage.collectAsState()
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        FilterChip(
                            selected = currentLang == "en",
                            onClick = { viewModel.setDictionaryLanguage("en") },
                            label = { Text(stringResource(R.string.dict_en_chip)) }
                        )
                        Spacer(Modifier.width(16.dp))
                        FilterChip(
                            selected = currentLang == "pt",
                            onClick = { viewModel.setDictionaryLanguage("pt") },
                            label = { Text(stringResource(R.string.dict_pt_chip)) }
                        )
                    }

                    when (dictState) {
                        is br.com.jotdown.ui.viewmodel.DictionaryLookupState.Loading -> {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color(0xFF3B82F6))
                                    Spacer(Modifier.height(12.dp))
                                    Text(stringResource(R.string.dict_searching, dictState.word), fontSize = 13.sp, color = Color(0xFF6B7280))
                                }
                            }
                        }
                        is br.com.jotdown.ui.viewmodel.DictionaryLookupState.Downloading -> {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color(0xFF10B981), progress = { dictState.progress / 100f })
                                    Spacer(Modifier.height(12.dp))
                                    Text(stringResource(R.string.dict_downloading, dictState.progress), fontSize = 13.sp, color = Color(0xFF6B7280))
                                }
                            }
                        }
                        is br.com.jotdown.ui.viewmodel.DictionaryLookupState.NotDownloaded -> {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.dict_not_installed, dictState.language),
                                fontSize = 13.sp,
                                color = Color(0xFF4B5563)
                            )
                        }
                        is br.com.jotdown.ui.viewmodel.DictionaryLookupState.NotFound -> {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.dict_no_definition, dictState.word),
                                fontSize = 13.sp,
                                color = Color(0xFF92400E)
                            )
                        }
                        is br.com.jotdown.ui.viewmodel.DictionaryLookupState.Success -> {
                            val entry = dictState.entry

                            Surface(
                                color = Color(0xFFEFF6FF),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = entry.word,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color(0xFF1D4ED8),
                                    modifier = Modifier.padding(12.dp)
                                )
                            }

                            if (!entry.translation.isNullOrBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.dict_translation),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF374151)
                                )
                                Spacer(Modifier.height(4.dp))
                                Surface(
                                    color = Color(0xFFECFDF5),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = entry.translation,
                                        fontSize = 15.sp,
                                        lineHeight = 21.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF065F46),
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            if (!entry.definition.isNullOrBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.dict_definition),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF374151)
                                )
                                Spacer(Modifier.height(4.dp))
                                Surface(
                                    color = Color(0xFFF9FAFB),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = entry.definition,
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }
                        is br.com.jotdown.ui.viewmodel.DictionaryLookupState.Error -> {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.dict_error, dictState.message),
                                fontSize = 13.sp,
                                color = Color(0xFFDC2626)
                            )
                        }
                        is br.com.jotdown.ui.viewmodel.DictionaryLookupState.PhraseLoading -> {
                            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color(0xFF8B5CF6))
                                    Spacer(Modifier.height(12.dp))
                                    Text(stringResource(R.string.dict_translating_phrase), fontSize = 13.sp, color = Color(0xFF6B7280))
                                }
                            }
                        }
                        is br.com.jotdown.ui.viewmodel.DictionaryLookupState.PhraseNotFound -> {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.dict_no_phrase_translation),
                                fontSize = 13.sp,
                                color = Color(0xFF92400E)
                            )
                        }
                        is br.com.jotdown.ui.viewmodel.DictionaryLookupState.PhraseSuccess -> {
                            val entries = dictState.entries
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = pluralStringResource(R.plurals.dict_phrase_words, entries.size, entries.size),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF374151)
                            )
                            Spacer(Modifier.height(8.dp))
                            entries.forEach { entry ->
                                Surface(
                                    color = Color(0xFFEFF6FF),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = entry.word,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF1D4ED8)
                                        )
                                        if (!entry.translation.isNullOrBlank()) {
                                            Text(
                                                text = entry.translation,
                                                fontSize = 13.sp,
                                                color = Color(0xFF065F46),
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                        if (!entry.definition.isNullOrBlank()) {
                                            Text(
                                                text = entry.definition,
                                                fontSize = 11.sp,
                                                color = Color(0xFF6B7280),
                                                lineHeight = 16.sp,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }
            },
            confirmButton = {
                if (dictState is br.com.jotdown.ui.viewmodel.DictionaryLookupState.NotDownloaded) {
                    Button(
                        onClick = { viewModel.downloadDictionary(dictState.language, dictState.word) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) { Text(stringResource(R.string.dict_download)) }
                } else if (dictState !is br.com.jotdown.ui.viewmodel.DictionaryLookupState.Downloading) {
                    Button(
                        onClick = { viewModel.clearDictionaryState() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                    ) { Text(stringResource(R.string.common_close)) }
                }
            },
            dismissButton = {
                if (dictState is br.com.jotdown.ui.viewmodel.DictionaryLookupState.NotDownloaded ||
                    dictState is br.com.jotdown.ui.viewmodel.DictionaryLookupState.Error) {
                    TextButton(onClick = { viewModel.clearDictionaryState() }) { Text(stringResource(R.string.common_cancel)) }
                }
            }
        )
    }

    if (pendingAnnotation != null || editingAnnotation != null) {
        val currentEditAnnot = editingAnnotation
        val linkedAudioRec = currentEditAnnot?.audioRecordingId?.let { recId ->
            audioRecordings.find { it.id == recId }
        }
        AlertDialog(
            onDismissRequest = { pendingAnnotation = null; pendingAnnotationAudio = null; editingAnnotation = null },
            title = { Text(if (pendingAnnotation != null) stringResource(R.string.annot_new_title) else stringResource(R.string.annot_view_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = annotationText,
                        onValueChange = { annotationText = it },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        label = { Text(stringResource(R.string.annot_write_here)) }
                    )
                    if (currentEditAnnot != null && linkedAudioRec != null) {
                        OutlinedButton(
                            onClick = {
                                seekAndPlayAudio(linkedAudioRec.id, currentEditAnnot.audioTimestampMs)
                                editingAnnotation = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(
                                    R.string.audio_play_at,
                                    AudioSyncUtil.formatDurationMs(currentEditAnnot.audioTimestampMs)
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pendingAnnotation != null) {
                            val (p, x, y) = pendingAnnotation!!
                            val audioTag = pendingAnnotationAudio
                            viewModel.addAnnotation(
                                page = p,
                                x = x,
                                y = y,
                                text = annotationText.trim(),
                                audioRecordingId = audioTag?.first,
                                audioTimestampMs = audioTag?.second ?: 0L
                            )
                            pendingAnnotation = null
                            pendingAnnotationAudio = null
                        } else if (editingAnnotation != null) {
                            viewModel.updateAnnotation(editingAnnotation!!.id, annotationText.trim())
                            editingAnnotation = null
                        }
                        viewModel.setActiveTool(Tool.NONE)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
                ) { Text(stringResource(R.string.annot_save_postit)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingAnnotation = null; pendingAnnotationAudio = null; editingAnnotation = null; viewModel.setActiveTool(Tool.NONE) }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
    if (isExportingFlattenedPdf) {
        androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Indigo600)
                    Text(stringResource(R.string.reader_exporting_annotated_pdf), fontSize = 14.sp)
                }
            }
        }
    }

    if (showAbntDialog && document != null) {
        AlertDialog(
            onDismissRequest = { showAbntDialog = false },
            title = { Text(stringResource(R.string.abnt_citation_title)) },
            text = {
                val abntText = AbntUtil.format(document!!)
                OutlinedTextField(
                    value = abntText,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().height(150.dp)
                )
            },
            confirmButton = {
                Button(onClick = { showAbntDialog = false }) { Text(stringResource(R.string.common_ok)) }
            }
        )
    }

    if (showExportDialog && document != null) {
        val wordCount = annotations.sumOf { it.text.trim().split(Regex("\\s+")).count { word -> word.isNotBlank() } } +
                        highlights.sumOf { it.text.trim().split(Regex("\\s+")).count { word -> word.isNotBlank() } }

        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(R.string.export_notes_title)) },
            text = { Text(stringResource(R.string.export_notes_msg, wordCount)) },
            confirmButton = {
                Button(onClick = {
                    val mdContent = ExportUtil.toMarkdown(document!!, annotations, highlights)
                    val fileName = ExportUtil.suggestedFileName(document!!)
                    val file = java.io.File(context.cacheDir, fileName)
                    file.writeText(mdContent)
                    
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/markdown"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.export_markdown_chooser)))
                    showExportDialog = false
                }) { Text(stringResource(R.string.common_share)) }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showOutline) {
        var selectedOutlineTab by remember { mutableIntStateOf(0) }
        ModalBottomSheet(onDismissRequest = { showOutline = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    stringResource(R.string.reader_outline_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Indigo600
                )
                Spacer(Modifier.height(10.dp))
                TabRow(selectedTabIndex = selectedOutlineTab) {
                    Tab(
                        selected = selectedOutlineTab == 0,
                        onClick = { selectedOutlineTab = 0 },
                        text = { Text(stringResource(R.string.outline_tab_toc)) }
                    )
                    Tab(
                        selected = selectedOutlineTab == 1,
                        onClick = { selectedOutlineTab = 1 },
                        text = { Text(stringResource(R.string.outline_tab_bookmarks)) }
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (selectedOutlineTab == 0) {
                    if (bookmarksLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Indigo600)
                            Text(stringResource(R.string.reader_outline_loading), color = MaterialTheme.colorScheme.outline)
                        }
                    } else if (pdfBookmarks.isEmpty()) {
                        Text(stringResource(R.string.reader_outline_empty), color = MaterialTheme.colorScheme.outline)
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                            items(pdfBookmarks.size) { i ->
                                BookmarkItem(bookmark = pdfBookmarks[i], depth = 0, onPageSelected = { p -> scrollToPage = p; showOutline = false })
                            }
                        }
                    }
                } else {
                    if (bookmarks.isEmpty()) {
                        Text(stringResource(R.string.bookmarks_empty), color = MaterialTheme.colorScheme.outline)
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                            items(bookmarks.size) { i ->
                                val bm = bookmarks[i]
                                val displayPage = bm.page + pageOffset
                                val titleText = bm.label.ifBlank { stringResource(R.string.reader_page_n, displayPage) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { scrollToPage = bm.page; showOutline = false }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bookmark,
                                        contentDescription = null,
                                        tint = Indigo600,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(titleText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        if (bm.label.isNotBlank()) {
                                            Text(
                                                stringResource(R.string.reader_page_n, displayPage),
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                    IconButton(onClick = { editingBookmark = bm }) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = stringResource(R.string.bookmark_edit_desc),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteBookmark(bm.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.common_delete),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingBookmark != null) {
        val targetBm = editingBookmark!!
        var labelInput by remember(targetBm.id) { mutableStateOf(targetBm.label) }
        AlertDialog(
            onDismissRequest = { editingBookmark = null },
            title = { Text(stringResource(R.string.bookmark_edit_title)) },
            text = {
                OutlinedTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it },
                    placeholder = { Text(stringResource(R.string.bookmark_label_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateBookmarkLabel(targetBm.id, labelInput.trim())
                        editingBookmark = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
                ) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editingBookmark = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showAppearanceDialog) {
        ReadingAppearanceDialog(
            currentTheme = readingTheme,
            currentMode = readingMode,
            onSelectTheme = { viewModel.updateReadingTheme(it) },
            onSelectMode = { viewModel.updateReadingMode(it) },
            onDismiss = { showAppearanceDialog = false }
        )
    }

    if (showPageGrid) {
        PageThumbnailsSheet(
            numPages = numPages,
            currentPage = currentPage,
            pageOffset = pageOffset,
            bookmarkedPages = bookmarkedPages,
            pdfRenderer = pdfRenderer,
            renderMutex = renderMutex,
            isInsertingPage = isInsertingPage,
            onSelectPage = { p -> scrollToPage = p },
            onInsertPageClick = { showInsertPageDialog = true },
            onDismiss = { showPageGrid = false }
        )
    }

    if (showInsertPageDialog) {
        InsertTemplatePageDialog(
            currentPage = currentPage,
            numPages = numPages,
            onConfirm = { afterPage, template ->
                val file = pdfFile ?: return@InsertTemplatePageDialog
                showInsertPageDialog = false
                if (isInsertingPage) return@InsertTemplatePageDialog
                isInsertingPage = true
                coroutineScope.launch {
                    val inserted = renderMutex.withLock {
                        withContext(Dispatchers.IO) {
                            try { pdfRenderer?.close() } catch (_: Exception) {}
                            try { fileDescriptor?.close() } catch (_: Exception) {}
                            pdfRenderer = null
                            fileDescriptor = null
                            PdfPageTemplateUtil.insertTemplatePage(file, afterPage, template)
                        }
                    }
                    if (inserted) {
                        viewModel.shiftPagesAfterInsert(afterPage)
                        PdfTextLayerUtil.invalidateFile(file)
                        pdfBookmarks = emptyList()
                        scrollToPage = afterPage + 1
                        Toast.makeText(context, context.getString(R.string.page_insert_success), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, context.getString(R.string.page_insert_error), Toast.LENGTH_SHORT).show()
                    }
                    pdfReloadVersion++
                    isInsertingPage = false
                }
            },
            onDismiss = { showInsertPageDialog = false }
        )
    }

    if (showSplitScreenPicker) {
        val splitCandidates = remember(allDocuments, docId) {
            allDocuments.filter { it.id != docId && !it.isTrashed }
        }
        SplitScreenDocPickerDialog(
            documents = splitCandidates,
            onSelectDocument = { targetDoc ->
                showSplitScreenPicker = false
                if (targetDoc.isLocked) {
                    val activity = context as? FragmentActivity
                    if (activity != null && BiometricLockUtil.canAuthenticate(context)) {
                        val docTitle = targetDoc.title.ifBlank { targetDoc.fileName }
                        BiometricLockUtil.authenticate(
                            activity = activity,
                            title = context.getString(R.string.biometric_prompt_title),
                            subtitle = context.getString(R.string.biometric_prompt_subtitle, docTitle),
                            onSuccess = { MultiReaderActivity.launchAdjacent(context, targetDoc.id) },
                            onError = { msg ->
                                if (msg.isNotBlank()) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        Toast.makeText(context, context.getString(R.string.biometric_unavailable), Toast.LENGTH_LONG).show()
                    }
                } else {
                    MultiReaderActivity.launchAdjacent(context, targetDoc.id)
                }
            },
            onDismiss = { showSplitScreenPicker = false }
        )
    }
}

@Composable
fun SplitScreenDocPickerDialog(
    documents: List<DocumentEntity>,
    onSelectDocument: (DocumentEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.split_screen_picker_title)) },
        text = {
            if (documents.isEmpty()) {
                Text(
                    stringResource(R.string.split_screen_picker_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(documents.size) { index ->
                        val item = documents[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectDocument(item) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (item.isLocked) Icons.Default.Lock else Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = if (item.isLocked) MaterialTheme.colorScheme.error else Indigo600,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = item.title.ifBlank { item.fileName },
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
fun ReadingAppearanceDialog(
    currentTheme: String,
    currentMode: String,
    onSelectTheme: (String) -> Unit,
    onSelectMode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val themes = listOf(
        Triple(ReadingThemeSpec.THEME_LIGHT, R.string.theme_light, Color(0xFFFFFFFF)),
        Triple(ReadingThemeSpec.THEME_SEPIA, R.string.theme_sepia, Color(0xFFF4ECD8)),
        Triple(ReadingThemeSpec.THEME_GREEN, R.string.theme_green, Color(0xFFE3F2E3)),
        Triple(ReadingThemeSpec.THEME_DARK, R.string.theme_dark, Color(0xFF1E1E22))
    )
    val modes = listOf(
        ReadingThemeSpec.MODE_VERTICAL to R.string.reading_mode_vertical,
        ReadingThemeSpec.MODE_HORIZONTAL to R.string.reading_mode_horizontal
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.appearance_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    stringResource(R.string.appearance_theme_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    themes.forEach { (themeId, labelRes, swatchColor) ->
                        FilterChip(
                            selected = currentTheme == themeId,
                            onClick = { onSelectTheme(themeId) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(swatchColor)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                )
                            },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }

                Text(
                    stringResource(R.string.appearance_layout_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    modes.forEach { (modeId, labelRes) ->
                        FilterChip(
                            selected = currentMode == modeId,
                            onClick = { onSelectMode(modeId) },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
            ) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}

@Composable
fun BookmarkItem(bookmark: br.com.jotdown.util.PdfBookmark, depth: Int, onPageSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = (depth * 16).dp).clickable { if (bookmark.page > 0) onPageSelected(bookmark.page); expanded = !expanded }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (bookmark.children.isNotEmpty()) {
                Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight, contentDescription = null, tint = Indigo400, modifier = Modifier.size(20.dp))
            } else {
                Spacer(Modifier.width(20.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(bookmark.title, fontSize = 14.sp, fontWeight = if (depth == 0) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
            if (bookmark.page > 0) {
                Text(bookmark.page.toString(), fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        
        AnimatedVisibility(visible = expanded) {
            Column {
                bookmark.children.forEach { child ->
                    BookmarkItem(child, depth + 1, onPageSelected)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PdfViewer(
    pdfFile: File, numPages: Int, currentPage: Int, activeTool: Tool, strokeColor: Int,
    annotations: List<AnnotationEntity>, highlights: List<HighlightEntity>, drawings: List<DrawingEntity>,
    scrollToPage: Int, undoTrigger: Long, redoTrigger: Long, targetUndoPage: Int, strokeWidthMultiplier: Float,
    pdfRenderer: PdfRenderer?, renderMutex: Mutex,
    readingThemeSpec: ReadingThemeSpec,
    readingMode: String = ReadingThemeSpec.MODE_VERTICAL,
    pdfReloadVersion: Int = 0,
    holdToShape: Boolean = true,
    initialScrollOffset: Int,
    pagePaddingMode: String = PagePaddingSpec.MODE_NONE,
    pagePaddingColor: String = PagePaddingSpec.COLOR_WHITE,
    pagePaddingDotted: Boolean = true,
    activeSearchQuery: String = "",
    eraserMode: EraserMode = EraserMode.STROKE,
    twoThreeFingerUndo: Boolean = true,
    stylusButtonAction: String = "ERASER",
    onScrollDone: () -> Unit,
    onOcrSuccess: (Int, String, String) -> Unit,
    onQuickHighlight: (Int, String, String, String, String) -> Unit,
    onTapHighlight: (HighlightEntity) -> Unit,
    onDictionaryRequest: (Int, String) -> Unit,
    onScrollChange: (Int, Int) -> Unit,
    onAddAnnotation: (Int, Float, Float) -> Unit, onOpenAnnotation: (AnnotationEntity) -> Unit, onSaveDrawing: (Int, String) -> Unit,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    drawWithFinger: Boolean = false,
    doubleTapToDeselect: Boolean = false,
    twoFingerScroll: Boolean = false,
    onDoubleTapDeselect: () -> Unit = {},
    activeRecordingId: Long? = null,
    recordingElapsedMs: Long = 0L,
    playingRecordingId: Long? = null,
    playbackPositionMs: Long = 0L,
    tapeRevealAll: Boolean = false,
    revealedTapeByPage: Map<Int, Set<Int>> = emptyMap(),
    onToggleTapeReveal: (page: Int, strokeIdx: Int) -> Unit = { _, _ -> },
    onSeekAudioFromStroke: (audioId: Long, timestampMs: Long) -> Unit = { _, _ -> },
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val onUndoRef by rememberUpdatedState(onUndo)
    val onRedoRef by rememberUpdatedState(onRedo)

    // Stylus-only by default (palm rejection): finger scrolls. With drawWithFinger,
    // finger/passive pen draws, so scroll must yield while a stroke tool is active —
    // unless twoFingerScroll keeps scroll enabled (one finger draws, two scroll).
    // DICTIONARY é exceção: usa drag de dedo pra desenhar o retângulo de seleção.
    val isStrokeTool = activeTool == Tool.PEN || activeTool == Tool.PENCIL ||
            activeTool == Tool.HIGHLIGHTER || activeTool == Tool.ERASER ||
            activeTool == Tool.LASSO || activeTool == Tool.TAPE
    val isScrollEnabled = activeTool != Tool.DICTIONARY && scale == 1f &&
            !(drawWithFinger && isStrokeTool && !twoFingerScroll)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(readingThemeSpec.backdropArgb))
            .pointerInput(twoThreeFingerUndo) {
                if (!twoThreeFingerUndo) return@pointerInput
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (firstDown.type != PointerType.Touch) return@awaitEachGesture
                    val startTime = System.currentTimeMillis()
                    val initialPositions = mutableMapOf<PointerId, Offset>(firstDown.id to firstDown.position)
                    var maxPointers = 1
                    var validTouch = true

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val changes = event.changes
                        for (c in changes) {
                            if (c.type != PointerType.Touch) {
                                validTouch = false
                            }
                            if (c.pressed && !initialPositions.containsKey(c.id)) {
                                initialPositions[c.id] = c.position
                            }
                            val startPos = initialPositions[c.id]
                            if (startPos != null && (c.position - startPos).getDistance() >= 30f) {
                                validTouch = false
                            }
                        }
                        if (initialPositions.size > maxPointers) {
                            maxPointers = initialPositions.size
                        }
                        if (changes.none { it.pressed }) {
                            val elapsed = System.currentTimeMillis() - startTime
                            if (validTouch && elapsed < 260L) {
                                when (maxPointers) {
                                    2 -> onUndoRef()
                                    3 -> onRedoRef()
                                }
                            }
                            break
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 4f)
                    if (scale > 1f) {
                        val maxX = (size.width * (scale - 1)) / 2
                        val maxY = (size.height * (scale - 1)) / 2
                        offset = Offset(
                            x = (offset.x + pan.x * scale).coerceIn(-maxX, maxX),
                            y = (offset.y + pan.y * scale).coerceIn(-maxY, maxY)
                        )
                    } else {
                        offset = Offset.Zero
                    }
                }
            }
    ) {
        if (readingMode == ReadingThemeSpec.MODE_HORIZONTAL) {
            val pagerState = rememberPagerState(
                initialPage = (currentPage - 1).coerceIn(0, (numPages - 1).coerceAtLeast(0)),
                pageCount = { numPages }
            )

            LaunchedEffect(scrollToPage, numPages) {
                if (scrollToPage in 1..numPages) {
                    pagerState.scrollToPage(scrollToPage - 1)
                    onScrollDone()
                }
            }

            LaunchedEffect(pagerState.currentPage) {
                onScrollChange(pagerState.currentPage + 1, 0)
            }

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = isScrollEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                contentPadding = PaddingValues(16.dp),
                pageSpacing = 16.dp
            ) { index ->
                val pageNumber = index + 1
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState(), enabled = isScrollEnabled),
                    contentAlignment = Alignment.Center
                ) {
                    PdfPage(
                        pdfFile = pdfFile, pageNumber = pageNumber, activeTool = activeTool, strokeColor = strokeColor,
                        annotations = annotations.filter { it.page == pageNumber },
                        pageHighlights = highlights.filter { it.page == pageNumber },
                        pageDrawingsJson = drawings.find { it.page == pageNumber }?.pathsJson,
                        undoTrigger = undoTrigger, redoTrigger = redoTrigger,
                        isUndoRedoTarget = (pageNumber == targetUndoPage),
                        strokeWidthMultiplier = strokeWidthMultiplier,
                        pdfRenderer = pdfRenderer, renderMutex = renderMutex,
                        readingThemeSpec = readingThemeSpec,
                        zoomScale = scale,
                        isNearCurrentPage = kotlin.math.abs(pageNumber - currentPage) <= 1,
                        pdfReloadVersion = pdfReloadVersion,
                        holdToShape = holdToShape,
                        pagePaddingMode = pagePaddingMode,
                        pagePaddingColor = pagePaddingColor,
                        pagePaddingDotted = pagePaddingDotted,
                        activeSearchQuery = activeSearchQuery,
                        eraserMode = eraserMode,
                        stylusButtonAction = stylusButtonAction,
                        drawWithFinger = drawWithFinger,
                        doubleTapToDeselect = doubleTapToDeselect,
                        twoFingerScroll = twoFingerScroll,
                        onDoubleTapDeselect = onDoubleTapDeselect,
                        onWordSelected = {},
                        onOcrSuccess = { text, rectsJson -> onOcrSuccess(pageNumber, text, rectsJson) },
                        onQuickHighlight = { text, rectsJson, style, colorHex ->
                            onQuickHighlight(pageNumber, text, rectsJson, style, colorHex)
                        },
                        onTapHighlight = onTapHighlight,
                        onDictionaryRequest = { text -> onDictionaryRequest(pageNumber, text) },
                        onAddAnnotation = { x, y -> onAddAnnotation(pageNumber, x, y) },
                        onOpenAnnotation = onOpenAnnotation,
                        onSaveDrawing = { json -> onSaveDrawing(pageNumber, json) },
                        onToggleFullscreen = onToggleFullscreen,
                        activeRecordingId = activeRecordingId,
                        recordingElapsedMs = recordingElapsedMs,
                        playingRecordingId = playingRecordingId,
                        playbackPositionMs = playbackPositionMs,
                        tapeRevealAll = tapeRevealAll,
                        revealedTapeIndices = revealedTapeByPage[pageNumber].orEmpty(),
                        onToggleTapeReveal = { idx -> onToggleTapeReveal(pageNumber, idx) },
                        onSeekAudioFromStroke = onSeekAudioFromStroke
                    )
                }
            }
        } else {
            // Inicia o LazyList com a página e a exata altura da rolagem do último acesso
            val listState = rememberLazyListState(
                initialFirstVisibleItemIndex = (currentPage - 1).coerceAtLeast(0),
                initialFirstVisibleItemScrollOffset = initialScrollOffset
            )

            LaunchedEffect(scrollToPage, numPages) {
                if (scrollToPage in 1..numPages) {
                    listState.scrollToItem(scrollToPage - 1)
                    onScrollDone()
                }
            }

            // Dispara atualizações de scroll em tempo real
            LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
                onScrollChange(listState.firstVisibleItemIndex + 1, listState.firstVisibleItemScrollOffset)
            }

            LazyColumn(
                state = listState,
                userScrollEnabled = isScrollEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(numPages) { index ->
                    val pageNumber = index + 1
                    PdfPage(
                        pdfFile = pdfFile, pageNumber = pageNumber, activeTool = activeTool, strokeColor = strokeColor,
                        annotations = annotations.filter { it.page == pageNumber },
                        pageHighlights = highlights.filter { it.page == pageNumber },
                        pageDrawingsJson = drawings.find { it.page == pageNumber }?.pathsJson,
                        undoTrigger = undoTrigger, redoTrigger = redoTrigger,
                        isUndoRedoTarget = (pageNumber == targetUndoPage),
                        strokeWidthMultiplier = strokeWidthMultiplier,
                        pdfRenderer = pdfRenderer, renderMutex = renderMutex,
                        readingThemeSpec = readingThemeSpec,
                        zoomScale = scale,
                        isNearCurrentPage = kotlin.math.abs(pageNumber - currentPage) <= 1,
                        pdfReloadVersion = pdfReloadVersion,
                        holdToShape = holdToShape,
                        pagePaddingMode = pagePaddingMode,
                        pagePaddingColor = pagePaddingColor,
                        pagePaddingDotted = pagePaddingDotted,
                        activeSearchQuery = activeSearchQuery,
                        eraserMode = eraserMode,
                        stylusButtonAction = stylusButtonAction,
                        drawWithFinger = drawWithFinger,
                        doubleTapToDeselect = doubleTapToDeselect,
                        twoFingerScroll = twoFingerScroll,
                        onDoubleTapDeselect = onDoubleTapDeselect,
                        onWordSelected = {},
                        onOcrSuccess = { text, rectsJson -> onOcrSuccess(pageNumber, text, rectsJson) },
                        onQuickHighlight = { text, rectsJson, style, colorHex ->
                            onQuickHighlight(pageNumber, text, rectsJson, style, colorHex)
                        },
                        onTapHighlight = onTapHighlight,
                        onDictionaryRequest = { text -> onDictionaryRequest(pageNumber, text) },
                        onAddAnnotation = { x, y -> onAddAnnotation(pageNumber, x, y) },
                        onOpenAnnotation = onOpenAnnotation,
                        onSaveDrawing = { json -> onSaveDrawing(pageNumber, json) },
                        onToggleFullscreen = onToggleFullscreen,
                        activeRecordingId = activeRecordingId,
                        recordingElapsedMs = recordingElapsedMs,
                        playingRecordingId = playingRecordingId,
                        playbackPositionMs = playbackPositionMs,
                        tapeRevealAll = tapeRevealAll,
                        revealedTapeIndices = revealedTapeByPage[pageNumber].orEmpty(),
                        onToggleTapeReveal = { idx -> onToggleTapeReveal(pageNumber, idx) },
                        onSeekAudioFromStroke = onSeekAudioFromStroke
                    )
                }
            }
        }
    }
}

@Composable
fun PdfPage(
    pdfFile: File, pageNumber: Int, activeTool: Tool, strokeColor: Int,
    annotations: List<AnnotationEntity>, pageHighlights: List<HighlightEntity>, pageDrawingsJson: String?,
    undoTrigger: Long, redoTrigger: Long, isUndoRedoTarget: Boolean, strokeWidthMultiplier: Float,
    pdfRenderer: PdfRenderer?, renderMutex: Mutex,
    readingThemeSpec: ReadingThemeSpec,
    zoomScale: Float = 1f,
    isNearCurrentPage: Boolean = false,
    pdfReloadVersion: Int = 0,
    holdToShape: Boolean = true,
    pagePaddingMode: String = PagePaddingSpec.MODE_NONE,
    pagePaddingColor: String = PagePaddingSpec.COLOR_WHITE,
    pagePaddingDotted: Boolean = true,
    activeSearchQuery: String = "",
    eraserMode: EraserMode = EraserMode.STROKE,
    stylusButtonAction: String = "ERASER",
    onWordSelected: (String) -> Unit,
    onOcrSuccess: (String, String) -> Unit,
    onQuickHighlight: (String, String, String, String) -> Unit,
    onTapHighlight: (HighlightEntity) -> Unit,
    onDictionaryRequest: (String) -> Unit,
    onAddAnnotation: (Float, Float) -> Unit, onOpenAnnotation: (AnnotationEntity) -> Unit, onSaveDrawing: (String) -> Unit,
    onToggleFullscreen: () -> Unit = {},
    drawWithFinger: Boolean = false,
    doubleTapToDeselect: Boolean = false,
    twoFingerScroll: Boolean = false,
    onDoubleTapDeselect: () -> Unit = {},
    activeRecordingId: Long? = null,
    recordingElapsedMs: Long = 0L,
    playingRecordingId: Long? = null,
    playbackPositionMs: Long = 0L,
    tapeRevealAll: Boolean = false,
    revealedTapeIndices: Set<Int> = emptySet(),
    onToggleTapeReveal: (Int) -> Unit = {},
    onSeekAudioFromStroke: (Long, Long) -> Unit = { _, _ -> },
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Página que sai de tela devolve os ~14 MB na hora. Sem isto eles ficam
    // aguardando o coletor, e uma dúzia de páginas já soma centenas de MB de
    // heap nativo e textura de GPU.
    DisposableEffect(Unit) {
        onDispose {
            bitmap?.recycle()
            bitmap = null
        }
    }

    var settledZoom by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(zoomScale, isNearCurrentPage) {
        if (zoomScale <= 1.05f || !isNearCurrentPage) {
            settledZoom = 1f
        } else {
            kotlinx.coroutines.delay(220L)
            settledZoom = zoomScale
        }
    }

    var selectionRect by remember { mutableStateOf<Rect?>(null) }
    var startOffset by remember { mutableStateOf<Offset?>(null) }
    var dragAction by remember { mutableStateOf("NEW") }
    var initialRect by remember { mutableStateOf<Rect?>(null) }
    val selectionRectRef = rememberUpdatedState(selectionRect)
    var searchMatchRects by remember { mutableStateOf<List<NormRect>>(emptyList()) }

    LaunchedEffect(activeTool) { if (activeTool != Tool.SELECT && activeTool != Tool.DICTIONARY) selectionRect = null }

    LaunchedEffect(pdfFile, pageNumber, activeSearchQuery, pdfReloadVersion) {
        searchMatchRects = if (activeSearchQuery.isNotBlank()) {
            PdfTextLayerUtil.findMatchRectsOnPage(pdfFile, pageNumber, activeSearchQuery)
        } else {
            emptyList()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        val paddingSpec = remember(pagePaddingMode) { PagePaddingSpec.fromMode(pagePaddingMode) }
        val visualScale = paddingSpec.visualScale
        val pdfBmpW = (widthPx * visualScale).roundToInt().coerceAtLeast(1)
        var basePageHeightPx by remember(pageNumber, pdfBmpW, pdfReloadVersion) {
            mutableIntStateOf((pdfBmpW * 1.414f).roundToInt().coerceAtLeast(1))
        }
        val targetRenderScale = ReadingThemeSpec.computeTargetRenderScale(
            settledZoom = settledZoom,
            isNearCurrentPage = isNearCurrentPage,
            baseW = pdfBmpW,
            baseH = basePageHeightPx
        )

        LaunchedEffect(pageNumber, widthPx, pdfRenderer, pdfReloadVersion, pagePaddingMode, targetRenderScale) {
            if (pdfBmpW > 0 && pdfRenderer != null) {
                withContext(Dispatchers.IO) {
                    try {
                        renderMutex.withLock {
                            if (pageNumber !in 1..pdfRenderer.pageCount) return@withLock
                            val page = pdfRenderer.openPage(pageNumber - 1)
                            val bmp = try {
                                val baseScale = pdfBmpW.toFloat() / page.width.coerceAtLeast(1)
                                val actualBaseH = (page.height * baseScale).roundToInt().coerceAtLeast(1)
                                basePageHeightPx = actualBaseH
                                val effectiveScale = ReadingThemeSpec.computeTargetRenderScale(
                                    settledZoom = settledZoom,
                                    isNearCurrentPage = isNearCurrentPage,
                                    baseW = pdfBmpW,
                                    baseH = actualBaseH
                                )
                                val scaledW = (pdfBmpW * effectiveScale).roundToInt().coerceAtLeast(1)
                                val scaledH = (actualBaseH * effectiveScale).roundToInt().coerceAtLeast(1)
                                // ARGB_8888 é obrigatório: PdfRenderer.Page.render() recusa
                                // qualquer outra configuração. Custa 4 bytes por pixel e não
                                // há como baratear por aqui — só reduzindo as dimensões.
                                try {
                                    Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.ARGB_8888).apply {
                                        eraseColor(android.graphics.Color.WHITE)
                                        page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    }
                                } catch (_: OutOfMemoryError) {
                                    Bitmap.createBitmap(pdfBmpW, actualBaseH, Bitmap.Config.ARGB_8888).apply {
                                        eraseColor(android.graphics.Color.WHITE)
                                        page.render(this, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    }
                                }
                            } finally {
                                page.close()
                            }
                            // Troca de zoom re-renderiza a página: o bitmap anterior
                            // vira lixo de 14 MB. Libera depois que o novo já está
                            // publicado, para nunca reciclar o que ainda é desenhado.
                            val anterior = bitmap
                            bitmap = bmp
                            anterior?.recycle()
                        }
                    } catch (e: Exception) {
                        // Sem isto, um render que falha deixa a página em branco e
                        // silenciosa — foi assim que uma troca de Bitmap.Config passou
                        // despercebida por várias medições.
                        android.util.Log.w("PdfPage", "falha ao renderizar página $pageNumber", e)
                    }
                }
            }
        }

        val padRgb = remember(pagePaddingColor, readingThemeSpec) {
            readingThemeSpec.resolveMarginRgb(pagePaddingColor)
        }
        val padBgColor = Color(padRgb.first, padRgb.second, padRgb.third)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(4.dp),
            colors = CardDefaults.cardColors(containerColor = padBgColor)
        ) {
            val bmp = bitmap
            if (bmp != null && !bmp.isRecycled) {
                val pdfHeightPx = (pdfBmpW.toFloat() * bmp.height.toFloat() / bmp.width.coerceAtLeast(1))
                    .roundToInt().coerceAtLeast(1)
                val totalHeightPx = (pdfHeightPx * (1f + paddingSpec.extraBottomFraction))
                    .roundToInt().coerceAtLeast(pdfHeightPx)
                val pdfWidthDp = with(density) { pdfBmpW.toDp() }
                val pdfHeightDp = with(density) { pdfHeightPx.toDp() }
                val totalHeightDp = with(density) { totalHeightPx.toDp() }

                val pageHighlightsRef by rememberUpdatedState(pageHighlights)

                fun runSelectionExtraction(
                    rect: Rect,
                    sourceBmp: Bitmap,
                    allowOcrFallback: Boolean = true,
                    onExtracted: (extractedText: String, rectsJson: String) -> Unit
                ) {
                    val normRect = NormRect(
                        left = (rect.left / pdfBmpW.toFloat()).coerceIn(0f, 1f),
                        top = (rect.top / pdfHeightPx.toFloat()).coerceIn(0f, 1f),
                        right = (rect.right / pdfBmpW.toFloat()).coerceIn(0f, 1f),
                        bottom = (rect.bottom / pdfHeightPx.toFloat()).coerceIn(0f, 1f)
                    )
                    coroutineScope.launch {
                        val extraction = PdfTextLayerUtil.extractInRect(pdfFile, pageNumber, normRect)
                        if (extraction.text.isNotBlank()) {
                            val rectsJson = PdfTextLayerUtil.serializeRects(
                                extraction.lineRects.ifEmpty { listOf(normRect) }
                            )
                            onExtracted(extraction.text, rectsJson)
                        } else {
                            val fallbackRects = PdfTextLayerUtil.serializeRects(listOf(normRect))
                            if (!allowOcrFallback) {
                                onExtracted("", fallbackRects)
                                return@launch
                            }
                            if (sourceBmp.isRecycled) return@launch
                            try {
                                val bmpScaleX = sourceBmp.width.toFloat() / pdfBmpW.toFloat()
                                val bmpScaleY = sourceBmp.height.toFloat() / pdfHeightPx.toFloat()
                                val safeLeft = maxOf(0, (rect.left * bmpScaleX).toInt())
                                val safeTop = maxOf(0, (rect.top * bmpScaleY).toInt())
                                val safeWidth = minOf(sourceBmp.width - safeLeft, (rect.width * bmpScaleX).toInt())
                                val safeHeight = minOf(sourceBmp.height - safeTop, (rect.height * bmpScaleY).toInt())
                                if (safeWidth > 0 && safeHeight > 0) {
                                    val crop = Bitmap.createBitmap(sourceBmp, safeLeft, safeTop, safeWidth, safeHeight)
                                    OcrUtil.extractTextFromBitmap(
                                        bitmap = crop,
                                        onSuccess = { ocrText ->
                                            try {
                                                if (ocrText.isNotBlank()) {
                                                    onExtracted(ocrText, fallbackRects)
                                                }
                                            } finally {
                                                crop.recycle()
                                            }
                                        },
                                        onError = { e ->
                                            crop.recycle()
                                            e.printStackTrace()
                                        }
                                    )
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    selectionRect = null
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(totalHeightDp)
                        .background(padBgColor)
                ) {
                    if (paddingSpec.hasPadding) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val pdfW = pdfBmpW.toFloat()
                            val pdfH = pdfHeightPx.toFloat()
                            val dividerColor = if (readingThemeSpec.isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.12f)
                            if (paddingSpec.extraRightFraction > 0f) {
                                drawLine(
                                    color = dividerColor,
                                    start = Offset(pdfW, 0f),
                                    end = Offset(pdfW, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                            if (paddingSpec.extraBottomFraction > 0f) {
                                drawLine(
                                    color = dividerColor,
                                    start = Offset(0f, pdfH),
                                    end = Offset(pdfW, pdfH),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                            if (pagePaddingDotted) {
                                val step = 18.dp.toPx()
                                val dotColor = if (readingThemeSpec.isDark) Color.White.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.20f)
                                val points = mutableListOf<Offset>()
                                var y = step
                                while (y < size.height - 4f) {
                                    var x = step
                                    while (x < size.width - 4f) {
                                        if (x > pdfW + 4f || y > pdfH + 4f) {
                                            points.add(Offset(x, y))
                                        }
                                        x += step
                                    }
                                    y += step
                                }
                                if (points.isNotEmpty()) {
                                    drawPoints(
                                        points = points,
                                        pointMode = PointMode.Points,
                                        color = dotColor,
                                        strokeWidth = 2.2.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }

                    val filter = remember(readingThemeSpec) {
                        readingThemeSpec.colorMatrixValues?.let { ColorFilter.colorMatrix(ColorMatrix(it)) }
                    }
                    val parsedDrawingsForTap = remember(pageDrawingsJson) {
                        if (pageDrawingsJson.isNullOrBlank()) emptyList() else parseDrawingsJson(pageDrawingsJson)
                    }
                    val parsedDrawingsRef by rememberUpdatedState(parsedDrawingsForTap)
                    val onToggleTapeRevealRef by rememberUpdatedState(onToggleTapeReveal)
                    val onSeekAudioFromStrokeRef by rememberUpdatedState(onSeekAudioFromStroke)
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(pdfWidthDp, pdfHeightDp)
                            .pointerInput(activeTool, pdfBmpW, pdfHeightPx, visualScale) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        onWordSelected("example")
                                    },
                                    onTap = { tapOffset ->
                                        if (activeTool == Tool.NONE) {
                                            val logicalX = tapOffset.x / visualScale
                                            val logicalY = tapOffset.y / visualScale
                                            val currentStrokes = parsedDrawingsRef
                                            val tapeHitIdx = currentStrokes.indices.reversed().firstOrNull { idx ->
                                                val stroke = currentStrokes[idx]
                                                stroke.tool == Tool.TAPE && AudioSyncUtil.isTapOnAudioStroke(
                                                    tapX = logicalX,
                                                    tapY = logicalY,
                                                    strokePoints = stroke.points.map { GeomPoint(it.x, it.y) },
                                                    hitRadius = 14f * stroke.widthMultiplier + 16f
                                                )
                                            }
                                            if (tapeHitIdx != null) {
                                                onToggleTapeRevealRef(tapeHitIdx)
                                                return@detectTapGestures
                                            }

                                            val nx = (tapOffset.x / pdfBmpW.toFloat()).coerceIn(0f, 1f)
                                            val ny = (tapOffset.y / pdfHeightPx.toFloat()).coerceIn(0f, 1f)
                                            val hit = pageHighlightsRef.firstOrNull { hl ->
                                                PdfTextLayerUtil.parseRects(hl.rectsJson).any { r ->
                                                    nx in r.left..r.right && ny in r.top..r.bottom
                                                }
                                            }
                                            if (hit != null) {
                                                onTapHighlight(hit)
                                                return@detectTapGestures
                                            }

                                            val audioHit = currentStrokes.reversed().firstOrNull { stroke ->
                                                stroke.audioId != null && AudioSyncUtil.isTapOnAudioStroke(
                                                    tapX = logicalX,
                                                    tapY = logicalY,
                                                    strokePoints = stroke.points.map { GeomPoint(it.x, it.y) },
                                                    hitRadius = 16f * stroke.widthMultiplier + 14f
                                                )
                                            }
                                            if (audioHit?.audioId != null) {
                                                onSeekAudioFromStrokeRef(audioHit.audioId, audioHit.audioTimestampMs)
                                            } else {
                                                onToggleFullscreen()
                                            }
                                        }
                                    }
                                )
                            },
                        contentScale = ContentScale.FillBounds,
                        colorFilter = filter
                    )

                    if (pageHighlights.isNotEmpty() || searchMatchRects.isNotEmpty()) {
                        Canvas(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .size(pdfWidthDp, pdfHeightDp)
                        ) {
                            for (hl in pageHighlights) {
                                val rects = PdfTextLayerUtil.parseRects(hl.rectsJson)
                                if (rects.isEmpty()) continue
                                val rgb = PagePaddingSpec.parseHexRgb(hl.color, Triple(253, 224, 71))
                                val c = Color(rgb.first, rgb.second, rgb.third)
                                for (r in rects) {
                                    val l = r.left * size.width
                                    val t = r.top * size.height
                                    val rt = r.right * size.width
                                    val b = r.bottom * size.height
                                    when (hl.style) {
                                        "UNDERLINE" -> drawLine(
                                            color = c,
                                            start = Offset(l, b),
                                            end = Offset(rt, b),
                                            strokeWidth = 2.dp.toPx()
                                        )
                                        "STRIKEOUT" -> {
                                            val my = (t + b) * 0.5f
                                            drawLine(
                                                color = c,
                                                start = Offset(l, my),
                                                end = Offset(rt, my),
                                                strokeWidth = 2.dp.toPx()
                                            )
                                        }
                                        else -> drawRect(
                                            color = c.copy(alpha = 0.35f),
                                            topLeft = Offset(l, t),
                                            size = Size(rt - l, b - t)
                                        )
                                    }
                                }
                            }

                            for (mr in searchMatchRects) {
                                val l = mr.left * size.width
                                val t = mr.top * size.height
                                val w = (mr.right - mr.left) * size.width
                                val h = (mr.bottom - mr.top) * size.height
                                drawRect(
                                    color = Color(0xFFF59E0B).copy(alpha = 0.35f),
                                    topLeft = Offset(l, t),
                                    size = Size(w, h)
                                )
                                drawRect(
                                    color = Color(0xFFD97706).copy(alpha = 0.75f),
                                    topLeft = Offset(l, t),
                                    size = Size(w, h),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                            }
                        }
                    }

                    DrawingLayer(
                        modifier = Modifier.matchParentSize(),
                        activeTool = activeTool, strokeColor = Color(strokeColor),
                        strokeWidthMultiplier = strokeWidthMultiplier,
                        annotations = annotations,
                        pageDrawingsJson = pageDrawingsJson,
                        undoTrigger = undoTrigger, redoTrigger = redoTrigger,
                        isUndoRedoTarget = isUndoRedoTarget,
                        visualScale = visualScale,
                        eraserMode = eraserMode,
                        stylusButtonAction = stylusButtonAction,
                        holdToShape = holdToShape,
                        drawWithFinger = drawWithFinger,
                        doubleTapToDeselect = doubleTapToDeselect,
                        twoFingerScroll = twoFingerScroll,
                        onDoubleTapDeselect = onDoubleTapDeselect,
                        onAddAnnotation = onAddAnnotation, onOpenAnnotation = onOpenAnnotation, onSaveDrawing = onSaveDrawing,
                        activeRecordingId = activeRecordingId,
                        recordingElapsedMs = recordingElapsedMs,
                        playingRecordingId = playingRecordingId,
                        playbackPositionMs = playbackPositionMs,
                        tapeRevealAll = tapeRevealAll,
                        revealedTapeIndices = revealedTapeIndices,
                        onToggleTapeReveal = onToggleTapeReveal
                    )

                    if (activeTool == Tool.SELECT || activeTool == Tool.DICTIONARY) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .size(pdfWidthDp, pdfHeightDp)
                        ) {
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
                                        if (dragAction == "NEW") {
                                            startOffset = offset
                                            selectionRect = Rect(offset, offset)
                                        } else if (dragAction == "MOVE") {
                                            startOffset = offset
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        val pos = change.position
                                        if (dragAction == "NEW") {
                                            startOffset?.let { start ->
                                                selectionRect = Rect(
                                                    minOf(start.x, pos.x),
                                                    minOf(start.y, pos.y),
                                                    maxOf(start.x, pos.x),
                                                    maxOf(start.y, pos.y)
                                                )
                                            }
                                        } else if (dragAction == "MOVE") {
                                            startOffset?.let { start ->
                                                val dx = pos.x - start.x
                                                val dy = pos.y - start.y
                                                initialRect?.let { init ->
                                                    selectionRect = init.translate(dx, dy)
                                                }
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
                                    val isNearBottom = rect.bottom + 150 > pdfHeightPx
                                    val yOffset = if (isNearBottom) {
                                        maxOf(0, rect.top.toInt() - 120)
                                    } else {
                                        rect.bottom.toInt() + 16
                                    }
                                    Box(
                                        modifier = Modifier
                                            .offset { IntOffset(0, yOffset) }
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (activeTool == Tool.DICTIONARY) {
                                            Button(
                                                onClick = {
                                                    runSelectionExtraction(rect, bmp) { text, _ ->
                                                        onDictionaryRequest(text)
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                modifier = Modifier.height(36.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    stringResource(R.string.reader_action_define_translate),
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        if (activeTool == Tool.SELECT) {
                                            Surface(
                                                shape = RoundedCornerShape(24.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                tonalElevation = 6.dp,
                                                shadowElevation = 8.dp
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .horizontalScroll(rememberScrollState())
                                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    TextButton(
                                                        onClick = {
                                                            runSelectionExtraction(rect, bmp, allowOcrFallback = false) { text, rectsJson ->
                                                                onQuickHighlight(text, rectsJson, "HIGHLIGHT", "#FDE047")
                                                            }
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(0xFFFDE047))
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(stringResource(R.string.reader_action_highlight), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                    }
                                                    TextButton(
                                                        onClick = {
                                                            runSelectionExtraction(rect, bmp, allowOcrFallback = false) { text, rectsJson ->
                                                                onQuickHighlight(text, rectsJson, "UNDERLINE", "#3B82F6")
                                                            }
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(0xFF3B82F6))
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(stringResource(R.string.reader_action_underline), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                    }
                                                    TextButton(
                                                        onClick = {
                                                            runSelectionExtraction(rect, bmp, allowOcrFallback = false) { text, rectsJson ->
                                                                onQuickHighlight(text, rectsJson, "STRIKEOUT", "#EF4444")
                                                            }
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(12.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(0xFFEF4444))
                                                        )
                                                        Spacer(Modifier.width(6.dp))
                                                        Text(stringResource(R.string.reader_action_strikeout), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                                    }
                                                    Button(
                                                        onClick = {
                                                            runSelectionExtraction(rect, bmp) { text, rectsJson ->
                                                                onOcrSuccess(text, rectsJson)
                                                            }
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(32.dp),
                                                        shape = RoundedCornerShape(16.dp)
                                                    ) {
                                                        Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(stringResource(R.string.reader_action_quote), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingLayer(
    modifier: Modifier, activeTool: Tool, strokeColor: Color, strokeWidthMultiplier: Float,
    annotations: List<AnnotationEntity>,
    pageDrawingsJson: String?, undoTrigger: Long, redoTrigger: Long,
    isUndoRedoTarget: Boolean = true,
    visualScale: Float = 1f,
    eraserMode: EraserMode = EraserMode.STROKE,
    stylusButtonAction: String = "ERASER",
    holdToShape: Boolean = true,
    onAddAnnotation: (Float, Float) -> Unit, onOpenAnnotation: (AnnotationEntity) -> Unit, onSaveDrawing: (String) -> Unit,
    drawWithFinger: Boolean = false,
    doubleTapToDeselect: Boolean = false,
    twoFingerScroll: Boolean = false,
    onDoubleTapDeselect: () -> Unit = {},
    activeRecordingId: Long? = null,
    recordingElapsedMs: Long = 0L,
    playingRecordingId: Long? = null,
    playbackPositionMs: Long = 0L,
    tapeRevealAll: Boolean = false,
    revealedTapeIndices: Set<Int> = emptySet(),
    onToggleTapeReveal: (Int) -> Unit = {},
) {
    val paths = remember { mutableStateListOf<DrawnPath>() }
    val undoHistory = remember { ArrayDeque<List<DrawnPath>>() }
    val redoHistory = remember { ArrayDeque<List<DrawnPath>>() }
    val pendingSavedJsons = remember { mutableListOf<String>() }
    var lastAppliedExternalJson by remember { mutableStateOf<String?>(null) }
    var hasLoadedInitial by remember { mutableStateOf(false) }
    val composedAt = remember { System.nanoTime() }

    var currentPath by remember { mutableStateOf<DrawnPath?>(null) }
    var predictedTail by remember { mutableStateOf<List<PathPoint>>(emptyList()) }
    var eraserCursorPos by remember { mutableStateOf<Offset?>(null) }
    var hitStrokeIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var canvasCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    var lassoPolygon by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var lassoSelectedIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var lassoDragDelta by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var pillSize by remember { mutableStateOf(IntSize.Zero) }

    // Double-tap tracking: último toque de dedo (tempo + posição).
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var lastTapPos by remember { mutableStateOf(Offset.Zero) }

    val stylusPredictor = LocalStylusPredictor.current
    val hapticFeedback = LocalHapticFeedback.current

    val isUndoRedoTargetRef by rememberUpdatedState(isUndoRedoTarget)
    val onAddAnnotationRef by rememberUpdatedState(onAddAnnotation)
    val onSaveDrawingRef by rememberUpdatedState(onSaveDrawing)
    val onDoubleTapDeselectRef by rememberUpdatedState(onDoubleTapDeselect)
    val holdToShapeRef by rememberUpdatedState(holdToShape)
    val activeRecordingIdRef by rememberUpdatedState(activeRecordingId)
    val recordingElapsedMsRef by rememberUpdatedState(recordingElapsedMs)
    val onToggleTapeRevealRef by rememberUpdatedState(onToggleTapeReveal)

    fun snapshotPaths(): List<DrawnPath> = paths.map { it.copy(points = it.points.toMutableList()) }

    fun pushUndoSnapshot() {
        if (undoHistory.size >= 40) undoHistory.removeFirst()
        undoHistory.addLast(snapshotPaths())
        redoHistory.clear()
    }

    fun commitPaths() {
        val json = paths.toJson()
        if (pendingSavedJsons.size >= 32) pendingSavedJsons.removeAt(0)
        pendingSavedJsons.add(json)
        onSaveDrawingRef(json)
    }

    fun performUndo() {
        if (undoHistory.isEmpty()) return
        if (redoHistory.size >= 40) redoHistory.removeFirst()
        redoHistory.addLast(snapshotPaths())
        val prev = undoHistory.removeLast()
        paths.clear()
        paths.addAll(prev.map { it.copy(points = it.points.toMutableList()) })
        lassoSelectedIndices = emptySet()
        lassoPolygon = emptyList()
        lassoDragDelta = Offset.Zero
        commitPaths()
    }

    fun performRedo() {
        if (redoHistory.isEmpty()) return
        if (undoHistory.size >= 40) undoHistory.removeFirst()
        undoHistory.addLast(snapshotPaths())
        val next = redoHistory.removeLast()
        paths.clear()
        paths.addAll(next.map { it.copy(points = it.points.toMutableList()) })
        lassoSelectedIndices = emptySet()
        lassoPolygon = emptyList()
        lassoDragDelta = Offset.Zero
        commitPaths()
    }

    LaunchedEffect(pageDrawingsJson) {
        val incoming = pageDrawingsJson ?: ""
        if (pendingSavedJsons.isNotEmpty()) {
            val matchIdx = pendingSavedJsons.indexOfFirst { pending ->
                pending == incoming || (incoming.isBlank() && pending == "[]")
            }
            if (matchIdx >= 0) {
                pendingSavedJsons.subList(0, matchIdx + 1).clear()
                lastAppliedExternalJson = incoming
            }
            return@LaunchedEffect
        }
        if (hasLoadedInitial && incoming == lastAppliedExternalJson) {
            return@LaunchedEffect
        }
        hasLoadedInitial = true
        lastAppliedExternalJson = incoming
        if (incoming.isBlank()) {
            paths.clear()
        } else {
            paths.clear()
            paths.addAll(parseDrawingsJson(incoming))
        }
        undoHistory.clear()
        redoHistory.clear()
        lassoSelectedIndices = emptySet()
        lassoPolygon = emptyList()
        lassoDragDelta = Offset.Zero
    }

    LaunchedEffect(activeTool) {
        if (activeTool != Tool.LASSO) {
            lassoSelectedIndices = emptySet()
            lassoPolygon = emptyList()
            lassoDragDelta = Offset.Zero
        }
    }

    LaunchedEffect(undoTrigger) {
        if (undoTrigger > composedAt && isUndoRedoTargetRef) {
            performUndo()
        }
    }
    LaunchedEffect(redoTrigger) {
        if (redoTrigger > composedAt && isUndoRedoTargetRef) {
            performRedo()
        }
    }

    val selectedBounds = remember(lassoSelectedIndices, paths.toList(), lassoDragDelta) {
        if (lassoSelectedIndices.isEmpty()) {
            null
        } else {
            val selectedPts = lassoSelectedIndices.mapNotNull { idx ->
                paths.getOrNull(idx)?.points?.map { pt ->
                    GeomPoint(pt.x + lassoDragDelta.x, pt.y + lassoDragDelta.y)
                }
            }
            StrokeGeometryUtil.boundingBoxOfPaths(selectedPts)
        }
    }

    // Box envolve o Canvas de desenho + os ícones de anotação como composables reais.
    // Isso permite que os balões tenham área de toque grande e abram a nota correta
    // independente da ferramenta ativa.
    Box(modifier = modifier.onSizeChanged { boxSize = it }) {
        Canvas(
            // graphicsLayer Offscreen é necessário para BlendMode.Clear da borracha.
            modifier = Modifier
                .matchParentSize()
                .onGloballyPositioned { canvasCoords = it }
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .pointerInput(
                    activeTool,
                    strokeColor,
                    strokeWidthMultiplier,
                    drawWithFinger,
                    doubleTapToDeselect,
                    twoFingerScroll,
                    visualScale,
                    eraserMode,
                    stylusButtonAction,
                    holdToShape
                ) {
                    if (activeTool == Tool.NONE || activeTool == Tool.SELECT || activeTool == Tool.DICTIONARY) return@pointerInput

                    if (activeTool == Tool.ANNOTATION) {
                        // Toque em espaço vazio → nova anotação em coordenadas lógicas.
                        // Toque em balão existente → tratado pelo composable clicável abaixo.
                        detectTapGestures { offset ->
                            onAddAnnotationRef(offset.x / visualScale, offset.y / visualScale)
                        }
                        return@pointerInput
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val initialEvent = currentEvent

                        // Double-tap com o dedo desliga a caneta e volta a rolar.
                        // Só observa (não consome): o scroll não é afetado.
                        if (doubleTapToDeselect && down.type == PointerType.Touch) {
                            val now = System.currentTimeMillis()
                            if (now - lastTapTime < 350 && (down.position - lastTapPos).getDistance() < 100f) {
                                lastTapTime = 0L
                                onDoubleTapDeselectRef()
                                return@awaitEachGesture
                            }
                            lastTapTime = now
                            lastTapPos = down.position
                        }

                        // Default: só stylus desenha, dedo rola (palm rejection). Com
                        // drawWithFinger, Touch (dedo/caneta passiva) também desenha.
                        if (!drawWithFinger && down.type != PointerType.Stylus && down.type != PointerType.Eraser) {
                            return@awaitEachGesture
                        }

                        val isSideButton = down.type == PointerType.Stylus &&
                            (initialEvent.buttons.isSecondaryPressed || initialEvent.buttons.isPrimaryPressed)
                        if (isSideButton && stylusButtonAction == "UNDO") {
                            down.consume()
                            performUndo()
                            return@awaitEachGesture
                        }

                        val effectiveTool = when {
                            down.type == PointerType.Eraser -> Tool.ERASER
                            isSideButton && stylusButtonAction == "ERASER" -> Tool.ERASER
                            else -> activeTool
                        }

                        val downLogical = Offset(down.position.x / visualScale, down.position.y / visualScale)

                        if (effectiveTool == Tool.LASSO) {
                            down.consume()
                            val currentBounds = if (lassoSelectedIndices.isNotEmpty()) {
                                val pts = lassoSelectedIndices.mapNotNull { idx ->
                                    paths.getOrNull(idx)?.points?.map { GeomPoint(it.x, it.y) }
                                }
                                StrokeGeometryUtil.boundingBoxOfPaths(pts)
                            } else {
                                null
                            }
                            val pad = 16f / visualScale
                            val insideSelection = currentBounds != null &&
                                downLogical.x in (currentBounds.left - pad)..(currentBounds.right + pad) &&
                                downLogical.y in (currentBounds.top - pad)..(currentBounds.bottom + pad)

                            if (insideSelection) {
                                var aborted = false
                                lassoDragDelta = Offset.Zero
                                do {
                                    val event = awaitPointerEvent()
                                    if (twoFingerScroll && event.changes.size > 1) {
                                        aborted = true
                                        break
                                    }
                                    val drag = event.changes.firstOrNull { it.id == down.id }
                                    if (drag != null && drag.pressed) {
                                        drag.consume()
                                        val dragLogical = Offset(drag.position.x / visualScale, drag.position.y / visualScale)
                                        lassoDragDelta = dragLogical - downLogical
                                    }
                                } while (drag != null && drag.pressed)
                                if (!aborted && lassoDragDelta.getDistance() > 1f) {
                                    pushUndoSnapshot()
                                    for (idx in lassoSelectedIndices) {
                                        val orig = paths.getOrNull(idx) ?: continue
                                        paths[idx] = orig.copy(
                                            points = orig.points.map { pt ->
                                                pt.copy(x = pt.x + lassoDragDelta.x, y = pt.y + lassoDragDelta.y)
                                            }.toMutableList()
                                        )
                                    }
                                    commitPaths()
                                }
                                lassoDragDelta = Offset.Zero
                            } else {
                                lassoSelectedIndices = emptySet()
                                lassoDragDelta = Offset.Zero
                                val poly = mutableListOf(downLogical)
                                lassoPolygon = poly.toList()
                                var aborted = false
                                do {
                                    val event = awaitPointerEvent()
                                    if (twoFingerScroll && event.changes.size > 1) {
                                        aborted = true
                                        break
                                    }
                                    val drag = event.changes.firstOrNull { it.id == down.id }
                                    if (drag != null && drag.pressed) {
                                        drag.consume()
                                        val dragLogical = Offset(drag.position.x / visualScale, drag.position.y / visualScale)
                                        if ((dragLogical - poly.last()).getDistance() >= 3f) {
                                            poly.add(dragLogical)
                                            lassoPolygon = poly.toList()
                                        }
                                    }
                                } while (drag != null && drag.pressed)
                                if (!aborted && poly.size >= 3) {
                                    val geomPoly = poly.map { GeomPoint(it.x, it.y) }
                                    lassoSelectedIndices = paths.indices.filter { idx ->
                                        val p = paths[idx]
                                        p.tool != Tool.ERASER && StrokeGeometryUtil.isPathSelectedByLasso(
                                            pathPoints = p.points.map { GeomPoint(it.x, it.y) },
                                            polygon = geomPoly
                                        )
                                    }.toSet()
                                }
                                lassoPolygon = emptyList()
                            }
                            return@awaitEachGesture
                        }

                        if (effectiveTool == Tool.ERASER && eraserMode == EraserMode.STROKE) {
                            down.consume()
                            val eraserTrail = mutableListOf(downLogical)
                            eraserCursorPos = downLogical
                            val eraserRadius = 15f * strokeWidthMultiplier
                            val hits = mutableSetOf<Int>()

                            fun checkHitsForSegment(segPts: List<GeomPoint>) {
                                paths.forEachIndexed { idx, p ->
                                    if (idx in hits || p.tool == Tool.ERASER) return@forEachIndexed
                                    val prevHalfWidth = when (p.tool) {
                                        Tool.PEN -> 1.5f
                                        Tool.PENCIL -> 2f
                                        Tool.HIGHLIGHTER -> 12.5f
                                        Tool.TAPE -> 14f
                                        else -> 1f
                                    } * p.widthMultiplier
                                    if (StrokeGeometryUtil.doesEraserHitPath(
                                            eraserPoints = segPts,
                                            pathPoints = p.points.map { GeomPoint(it.x, it.y) },
                                            hitRadius = eraserRadius + prevHalfWidth
                                        )
                                    ) {
                                        hits.add(idx)
                                    }
                                }
                                hitStrokeIndices = hits.toSet()
                            }

                            checkHitsForSegment(listOf(GeomPoint(downLogical.x, downLogical.y)))
                            var aborted = false
                            do {
                                val event = awaitPointerEvent()
                                if (twoFingerScroll && event.changes.size > 1) {
                                    aborted = true
                                    break
                                }
                                val drag = event.changes.firstOrNull { it.id == down.id }
                                if (drag != null && drag.pressed) {
                                    drag.consume()
                                    val dragLogical = Offset(drag.position.x / visualScale, drag.position.y / visualScale)
                                    eraserCursorPos = dragLogical
                                    val prevPt = eraserTrail.last()
                                    if ((dragLogical - prevPt).getDistance() >= 2f) {
                                        eraserTrail.add(dragLogical)
                                        checkHitsForSegment(
                                            listOf(
                                                GeomPoint(prevPt.x, prevPt.y),
                                                GeomPoint(dragLogical.x, dragLogical.y)
                                            )
                                        )
                                    }
                                }
                            } while (drag != null && drag.pressed)

                            if (!aborted && hits.isNotEmpty()) {
                                pushUndoSnapshot()
                                val remaining = paths.filterIndexed { idx, _ -> idx !in hits }
                                paths.clear()
                                paths.addAll(remaining)
                                commitPaths()
                            }
                            hitStrokeIndices = emptySet()
                            eraserCursorPos = null
                            return@awaitEachGesture
                        }

                        down.consume()
                        var pathInProgress = DrawnPath(
                            tool = effectiveTool,
                            color = strokeColor,
                            widthMultiplier = strokeWidthMultiplier,
                            points = mutableListOf(PathPoint(downLogical.x, downLogical.y, down.pressure))
                        )
                        var preSnapPoints: MutableList<PathPoint>? = null
                        var snapped = false
                        var snapAnchor = downLogical
                        var holdAnchor = downLogical
                        var lastSignificantMoveTime = System.currentTimeMillis()
                        var attemptedSnapSinceMove = false

                        currentPath = pathInProgress
                        predictedTail = emptyList()
                        if (effectiveTool == Tool.ERASER) eraserCursorPos = downLogical
                        var aborted = false
                        while (true) {
                            val canHoldSnap = holdToShapeRef &&
                                effectiveTool != Tool.ERASER &&
                                !snapped &&
                                !attemptedSnapSinceMove &&
                                pathInProgress.points.size >= 4
                            val event = if (canHoldSnap) {
                                val elapsed = System.currentTimeMillis() - lastSignificantMoveTime
                                val remaining = (450L - elapsed).coerceAtLeast(1L)
                                withTimeoutOrNull(remaining) { awaitPointerEvent() }
                            } else {
                                awaitPointerEvent()
                            }

                            if (event == null) {
                                attemptedSnapSinceMove = true
                                val geom = pathInProgress.points.map { GeomPoint(it.x, it.y) }
                                val recognized = ShapeRecognitionUtil.recognize(geom)
                                if (recognized != null) {
                                    preSnapPoints = pathInProgress.points.toMutableList()
                                    val avgPressure = pathInProgress.points.map { it.pressure }.average().toFloat()
                                    val snappedPts = recognized.points
                                        .map { PathPoint(it.x, it.y, avgPressure) }
                                        .toMutableList()
                                    pathInProgress = pathInProgress.copy(points = snappedPts)
                                    currentPath = pathInProgress
                                    predictedTail = emptyList()
                                    snapped = true
                                    snapAnchor = geom.last().let { Offset(it.x, it.y) }
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                continue
                            }

                            // Segundo dedo vira scroll: abandona o traço parcial e
                            // deixa o gesto seguir para o LazyColumn / HorizontalPager.
                            if (twoFingerScroll && event.changes.size > 1) {
                                aborted = true
                                break
                            }
                            val drag = event.changes.firstOrNull { it.id == down.id }
                            if (drag == null || !drag.pressed) {
                                break
                            }
                            drag.consume()
                            val dragLogical = Offset(drag.position.x / visualScale, drag.position.y / visualScale)

                            if (snapped) {
                                if ((dragLogical - snapAnchor).getDistance() < 16f) {
                                    continue
                                }
                                val restored = preSnapPoints ?: pathInProgress.points
                                pathInProgress = pathInProgress.copy(points = restored.toMutableList())
                                preSnapPoints = null
                                snapped = false
                                holdAnchor = dragLogical
                                lastSignificantMoveTime = System.currentTimeMillis()
                                attemptedSnapSinceMove = false
                            }

                            if ((dragLogical - holdAnchor).getDistance() >= 4f) {
                                holdAnchor = dragLogical
                                lastSignificantMoveTime = System.currentTimeMillis()
                                attemptedSnapSinceMove = false
                            }

                            val newPts = ArrayList<PathPoint>(pathInProgress.points.size + drag.historical.size + 1)
                            newPts.addAll(pathInProgress.points)
                            for (hist in drag.historical) {
                                val hx = hist.position.x / visualScale
                                val hy = hist.position.y / visualScale
                                val prev = newPts.last()
                                if (kotlin.math.hypot((hx - prev.x).toDouble(), (hy - prev.y).toDouble()) >= 0.75) {
                                    newPts.add(PathPoint(hx, hy, drag.pressure))
                                }
                            }
                            val prev = newPts.last()
                            if (kotlin.math.hypot((dragLogical.x - prev.x).toDouble(), (dragLogical.y - prev.y).toDouble()) >= 0.75) {
                                newPts.add(PathPoint(dragLogical.x, dragLogical.y, drag.pressure))
                            }
                            pathInProgress = pathInProgress.copy(points = newPts)
                            currentPath = pathInProgress

                            if (effectiveTool == Tool.ERASER) {
                                eraserCursorPos = dragLogical
                                predictedTail = emptyList()
                            } else if (down.type == PointerType.Stylus) {
                                val predWin = stylusPredictor?.latestPredictedWindowPoints.orEmpty()
                                val coords = canvasCoords
                                predictedTail = if (predWin.isNotEmpty() && coords != null && coords.isAttached) {
                                    predWin.map { (wx, wy) ->
                                        val local = coords.windowToLocal(Offset(wx, wy))
                                        PathPoint(local.x / visualScale, local.y / visualScale, drag.pressure)
                                    }
                                } else {
                                    emptyList()
                                }
                            } else {
                                predictedTail = emptyList()
                            }
                        }
                        if (!aborted) {
                            currentPath?.let { finished ->
                                val cleanPoints = if (snapped) {
                                    finished.points
                                } else {
                                    val deduped = StrokeSmoothingUtil.deduplicatePoints(
                                        finished.points.map { Triple(it.x, it.y, it.pressure) },
                                        minDistance = 0.75f
                                    )
                                    deduped.mapTo(ArrayList(deduped.size)) { PathPoint(it.first, it.second, it.third) }
                                }
                                val isTapGesture = cleanPoints.size <= 2 &&
                                    (cleanPoints.lastOrNull()?.let { last ->
                                        val first = cleanPoints.first()
                                        kotlin.math.hypot((last.x - first.x).toDouble(), (last.y - first.y).toDouble()) < 10.0
                                    } ?: true)

                                if (effectiveTool == Tool.TAPE && isTapGesture && cleanPoints.isNotEmpty()) {
                                    val tapPt = cleanPoints.first()
                                    val hitIdx = paths.indices.reversed().firstOrNull { idx ->
                                        val existing = paths[idx]
                                        existing.tool == Tool.TAPE && AudioSyncUtil.isTapOnAudioStroke(
                                            tapX = tapPt.x,
                                            tapY = tapPt.y,
                                            strokePoints = existing.points.map { GeomPoint(it.x, it.y) },
                                            hitRadius = 14f * existing.widthMultiplier + 16f
                                        )
                                    }
                                    if (hitIdx != null) {
                                        onToggleTapeRevealRef(hitIdx)
                                    }
                                } else {
                                    val recId = activeRecordingIdRef
                                    val taggedAudioId = if (recId != null && effectiveTool in listOf(Tool.PEN, Tool.PENCIL, Tool.HIGHLIGHTER, Tool.TAPE)) recId else null
                                    val taggedAudioTs = if (taggedAudioId != null) recordingElapsedMsRef else 0L
                                    pushUndoSnapshot()
                                    paths.add(
                                        finished.copy(
                                            points = cleanPoints,
                                            audioId = taggedAudioId,
                                            audioTimestampMs = taggedAudioTs
                                        )
                                    )
                                    commitPaths()
                                }
                            }
                        }
                        predictedTail = emptyList()
                        currentPath = null
                        eraserCursorPos = null
                    }
                }
        ) {
            scale(scaleX = visualScale, scaleY = visualScale, pivot = Offset.Zero) {
                paths.forEachIndexed { idx, path ->
                    val isTapeRevealed = tapeRevealAll || (idx in revealedTapeIndices)
                    val isDimmed = AudioSyncUtil.shouldDimStrokeForPlayback(
                        strokeAudioId = path.audioId,
                        strokeTimestampMs = path.audioTimestampMs,
                        playingAudioId = playingRecordingId,
                        playbackPositionMs = playbackPositionMs
                    )
                    val dimAlpha = if (isDimmed) 0.18f else 1f
                    if (idx in lassoSelectedIndices && lassoDragDelta != Offset.Zero) {
                        translate(left = lassoDragDelta.x, top = lassoDragDelta.y) {
                            drawDrawnPath(path, alphaMultiplier = dimAlpha, isTapeRevealed = isTapeRevealed)
                        }
                    } else {
                        val alphaMult = (if (idx in hitStrokeIndices) 0.25f else 1f) * dimAlpha
                        drawDrawnPath(path, alphaMultiplier = alphaMult, isTapeRevealed = isTapeRevealed)
                    }
                }
                currentPath?.let { active ->
                    if (predictedTail.isNotEmpty() && active.points.isNotEmpty()) {
                        drawDrawnPath(active.copy(points = (active.points + predictedTail).toMutableList()))
                    } else {
                        drawDrawnPath(active)
                    }
                }

                if (lassoPolygon.size >= 2) {
                    val polyPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(lassoPolygon[0].x, lassoPolygon[0].y)
                        for (i in 1 until lassoPolygon.size) {
                            lineTo(lassoPolygon[i].x, lassoPolygon[i].y)
                        }
                    }
                    drawPath(
                        path = polyPath,
                        color = Color(0xFF4F46E5),
                        style = Stroke(
                            width = 2.dp.toPx() / visualScale,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f / visualScale, 8f / visualScale))
                        )
                    )
                }

                if (activeTool == Tool.LASSO && selectedBounds != null) {
                    val pad = 8f / visualScale
                    drawRect(
                        color = Color(0xFF4F46E5),
                        topLeft = Offset(selectedBounds.left - pad, selectedBounds.top - pad),
                        size = Size(
                            (selectedBounds.right - selectedBounds.left) + pad * 2f,
                            (selectedBounds.bottom - selectedBounds.top) + pad * 2f
                        ),
                        style = Stroke(
                            width = 1.5.dp.toPx() / visualScale,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f / visualScale, 6f / visualScale))
                        )
                    )
                }

                // Cursor circular da borracha
                eraserCursorPos?.let { pos ->
                    val r = 15.dp.toPx() * strokeWidthMultiplier
                    drawCircle(color = Color(0xFF666666), radius = r, center = pos, style = Stroke(width = 1.5.dp.toPx() / visualScale))
                    drawCircle(color = Color(0x22000000), radius = r, center = pos)
                }
            }
        }

        // Balões de anotação como composables clicáveis.
        // Sempre visíveis e sempre clicáveis, independente da ferramenta ativa.
        val density = LocalDensity.current
        annotations.forEach { annot ->
            val iconSize = 27.dp  // 40dp * 2/3
            val iconSizePx = with(density) { iconSize.toPx() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (annot.x * visualScale - iconSizePx / 2f).toInt(),
                            y = (annot.y * visualScale - iconSizePx).toInt()
                        )
                    }
                    .size(iconSize)
                    .clickable { onOpenAnnotation(annot) },
                contentAlignment = Alignment.Center
            ) {
                // Sombra
                Icon(
                    imageVector = Icons.Default.ChatBubble,
                    contentDescription = null,
                    tint = Color(0xFF92400E).copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxSize().offset(1.dp, 2.dp)
                )
                // Balão principal
                Icon(
                    imageVector = Icons.Default.ChatBubble,
                    contentDescription = stringResource(R.string.reader_open_annotation),
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.fillMaxSize()
                )
                // Linhas internas simulando texto da anotação
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val w = size.width; val h = size.height
                    val lineColor = Color.White.copy(alpha = 0.88f)
                    val sw = 1.1.dp.toPx()
                    val x0 = w * 0.22f
                    drawLine(lineColor, Offset(x0, h * 0.28f), Offset(w * 0.76f, h * 0.28f), sw)
                    drawLine(lineColor, Offset(x0, h * 0.43f), Offset(w * 0.70f, h * 0.43f), sw)
                    drawLine(lineColor, Offset(x0, h * 0.58f), Offset(w * 0.55f, h * 0.58f), sw)
                }
            }
        }

        if (activeTool == Tool.LASSO && selectedBounds != null && lassoSelectedIndices.isNotEmpty()) {
            val lassoSwatches = listOf(
                0xFF000000.toInt(),
                0xFFEF4444.toInt(),
                0xFF3B82F6.toInt(),
                0xFF10B981.toInt(),
                0xFFF59E0B.toInt()
            )
            val centerX = ((selectedBounds.left + selectedBounds.right) * 0.5f * visualScale).roundToInt()
            val desiredX = (centerX - pillSize.width / 2)
                .coerceIn(8, (boxSize.width - pillSize.width - 8).coerceAtLeast(8))
            val aboveY = (selectedBounds.top * visualScale).roundToInt() - pillSize.height - 16
            val belowY = (selectedBounds.bottom * visualScale).roundToInt() + 16
            val desiredY = (if (aboveY >= 8) aboveY else belowY)
                .coerceIn(8, (boxSize.height - pillSize.height - 8).coerceAtLeast(8))

            Surface(
                modifier = Modifier
                    .offset { IntOffset(desiredX, desiredY) }
                    .onSizeChanged { pillSize = it },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    lassoSwatches.forEach { swatch ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(swatch))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable {
                                    pushUndoSnapshot()
                                    for (idx in lassoSelectedIndices) {
                                        val orig = paths.getOrNull(idx) ?: continue
                                        paths[idx] = orig.copy(color = Color(swatch))
                                    }
                                    commitPaths()
                                }
                        )
                    }
                    VerticalDivider(modifier = Modifier.height(22.dp))
                    IconButton(
                        onClick = {
                            pushUndoSnapshot()
                            val remaining = paths.filterIndexed { idx, _ -> idx !in lassoSelectedIndices }
                            paths.clear()
                            paths.addAll(remaining)
                            lassoSelectedIndices = emptySet()
                            lassoDragDelta = Offset.Zero
                            commitPaths()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            lassoSelectedIndices = emptySet()
                            lassoDragDelta = Offset.Zero
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.lasso_deselect),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

data class PathPoint(val x: Float, val y: Float, val pressure: Float = 1f)
data class DrawnPath(
    val tool: Tool,
    val color: Color,
    val widthMultiplier: Float = 1f,  // 0.5 = fino, 1.0 = médio, 2.0 = grosso
    val points: MutableList<PathPoint> = mutableListOf(),
    val audioId: Long? = null,
    val audioTimestampMs: Long = 0L
)

fun DrawScope.drawDrawnPath(
    path: DrawnPath,
    alphaMultiplier: Float = 1f,
    isTapeRevealed: Boolean = false
) {
    if (path.points.size < 2) return
    // widthMultiplier ajusta a espessura base; pressão da caneta continua afetando individualmente cada segmento
    val baseWidth = when (path.tool) {
        Tool.PEN -> 3f
        Tool.PENCIL -> 4f
        Tool.HIGHLIGHTER -> 25f
        Tool.ERASER -> 30f
        Tool.TAPE -> 28f
        else -> 2f
    } * path.widthMultiplier
    // A borracha usa BlendMode.Clear para apagar os pixels do próprio layer de anotações,
    // sem afetar a imagem do PDF que está numa camada inferior.
    val alpha = when (path.tool) {
        Tool.HIGHLIGHTER -> 0.35f
        Tool.ERASER -> 1f
        Tool.TAPE -> if (isTapeRevealed) 0.14f else 1f
        else -> 0.9f
    } * alphaMultiplier
    val blend = when (path.tool) {
        Tool.HIGHLIGHTER -> BlendMode.Multiply
        Tool.ERASER -> BlendMode.Clear
        else -> BlendMode.SrcOver
    }

    val segments = StrokeSmoothingUtil.buildQuadraticSegments(
        path.points.map { Triple(it.x, it.y, it.pressure) }
    )
    if (segments.isEmpty()) return

    if (path.tool == Tool.PEN) {
        // Caneta: segmentos quadráticos individuais para suportar variação contínua de pressão.
        val segPath = androidx.compose.ui.graphics.Path()
        for (seg in segments) {
            val avgP = (seg.startPressure + seg.endPressure) * 0.5f
            val pFact = (0.5f + 1.5f * avgP).coerceIn(0.5f, 2.5f)
            segPath.reset()
            segPath.moveTo(seg.start.x, seg.start.y)
            segPath.quadraticBezierTo(seg.control.x, seg.control.y, seg.end.x, seg.end.y)
            drawPath(
                path = segPath,
                color = path.color,
                alpha = alpha,
                style = Stroke(width = baseWidth * pFact, cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = blend
            )
        }
    } else {
        // Marca-texto, borracha, fita de estudo e lápis: um único drawPath suavizado para evitar sobreposições de alpha.
        val shapePath = androidx.compose.ui.graphics.Path().apply {
            moveTo(segments[0].start.x, segments[0].start.y)
            for (seg in segments) {
                quadraticBezierTo(seg.control.x, seg.control.y, seg.end.x, seg.end.y)
            }
        }
        val cap = if (path.tool == Tool.TAPE) StrokeCap.Square else StrokeCap.Round
        drawPath(
            path = shapePath,
            color = if (path.tool == Tool.ERASER) Color.Transparent else path.color,
            alpha = alpha,
            style = Stroke(width = baseWidth, cap = cap, join = StrokeJoin.Round),
            blendMode = blend
        )
        if (path.tool == Tool.TAPE && !isTapeRevealed) {
            drawPath(
                path = shapePath,
                color = Color.White,
                alpha = 0.25f * alphaMultiplier,
                style = Stroke(width = 8f * path.widthMultiplier, cap = StrokeCap.Square, join = StrokeJoin.Round),
                blendMode = BlendMode.SrcOver
            )
        }
    }
}

fun List<DrawnPath>.toJson() = JSONArray(map { p ->
    buildMap<String, Any> {
        put("tool", p.tool.name)
        put("color", p.color.value.toString())
        put("wm", p.widthMultiplier)
        put("points", p.points.map { mapOf("x" to it.x, "y" to it.y, "p" to it.pressure) })
        if (p.audioId != null) {
            put("aid", p.audioId)
            put("ats", p.audioTimestampMs)
        }
    }
}).toString()

fun parseDrawingsJson(json: String): List<DrawnPath> {
    val res = mutableListOf<DrawnPath>()
    try {
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val tool = Tool.valueOf(obj.getString("tool"))
            val color = Color(obj.getString("color").toULong())
            val wm = if (obj.has("wm")) obj.getDouble("wm").toFloat() else 1f  // retro-compat
            val audioId = if (obj.has("aid") && !obj.isNull("aid")) obj.getLong("aid") else null
            val audioTimestampMs = if (obj.has("ats") && !obj.isNull("ats")) obj.getLong("ats") else 0L
            val ptsArr = obj.getJSONArray("points"); val pts = mutableListOf<PathPoint>()
            for (j in 0 until ptsArr.length()) {
                val p = ptsArr.getJSONObject(j)
                val pressure = if (p.has("p")) p.getDouble("p").toFloat() else 1f
                pts.add(PathPoint(p.getDouble("x").toFloat(), p.getDouble("y").toFloat(), pressure))
            }
            res.add(DrawnPath(tool, color, wm, pts, audioId, audioTimestampMs))
        }
    } catch(e: Exception) {}
    return res
}

@Composable
fun PagePaddingDialog(
    initialMode: String,
    initialColor: String,
    initialDotted: Boolean,
    onConfirm: (mode: String, color: String, dotted: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMode by remember(initialMode) { mutableStateOf(initialMode) }
    var selectedColor by remember(initialColor) { mutableStateOf(initialColor) }
    var dotted by remember(initialDotted) { mutableStateOf(initialDotted) }

    val modes = listOf(
        PagePaddingSpec.MODE_NONE to R.string.page_padding_mode_none,
        PagePaddingSpec.MODE_RIGHT to R.string.page_padding_mode_right,
        PagePaddingSpec.MODE_BOTTOM to R.string.page_padding_mode_bottom,
        PagePaddingSpec.MODE_BOTH to R.string.page_padding_mode_both
    )
    val colors = listOf(
        PagePaddingSpec.COLOR_WHITE to R.string.page_padding_color_white,
        PagePaddingSpec.COLOR_SEPIA to R.string.page_padding_color_sepia,
        PagePaddingSpec.COLOR_GRAY to R.string.page_padding_color_gray
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.page_padding_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    stringResource(R.string.page_padding_mode_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    modes.forEach { (mode, labelRes) ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }

                Text(
                    stringResource(R.string.page_padding_color_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    colors.forEach { (hex, labelRes) ->
                        val rgb = PagePaddingSpec.parseHexRgb(hex)
                        val swatchColor = Color(rgb.first, rgb.second, rgb.third)
                        val isSelected = selectedColor.equals(hex, ignoreCase = true)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.clickable { selectedColor = hex }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) Indigo600 else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Indigo600,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Text(
                                stringResource(labelRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) Indigo600 else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.page_padding_dotted_grid),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = dotted,
                        onCheckedChange = { dotted = it },
                        enabled = selectedMode != PagePaddingSpec.MODE_NONE
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedMode, selectedColor, dotted) },
                colors = ButtonDefaults.buttonColors(containerColor = Indigo600)
            ) { Text(stringResource(R.string.common_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
fun HighlightNoteDialog(
    quote: String,
    initialNote: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var note by remember(initialNote) { mutableStateOf(initialNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.highlight_note_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // A citação fica à vista enquanto se escreve sobre ela.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.width(3.dp).height(IntrinsicSize.Min).background(Indigo600.copy(alpha = 0.5f)))
                    Text(quote, fontSize = 13.sp, maxLines = 6, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text(stringResource(R.string.highlight_note_hint)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(note); onDismiss() }) {
                Text(stringResource(R.string.common_save), color = Indigo600, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}
