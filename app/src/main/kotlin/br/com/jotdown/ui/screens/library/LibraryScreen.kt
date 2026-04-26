package br.com.jotdown.ui.screens.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.jotdown.data.dao.DocumentSummary
import br.com.jotdown.data.entity.FolderEntity
import br.com.jotdown.ui.theme.*
import br.com.jotdown.ui.viewmodel.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: LibraryViewModel, onOpenDocument: (String) -> Unit) {
    val context = LocalContext.current
    val displayDocuments by viewModel.displayDocuments.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val currentFolder by viewModel.currentFolder.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var docToDelete by remember { mutableStateOf<DocumentSummary?>(null) }
    var showDeleteFolder by remember { mutableStateOf(false) }

    var folderToRename by remember { mutableStateOf<FolderEntity?>(null) }
    var docToRename by remember { mutableStateOf<DocumentSummary?>(null) }
    var renameText by remember { mutableStateOf("") }
    var docToTag by remember { mutableStateOf<DocumentSummary?>(null) }
    var tagText by remember { mutableStateOf("") }

    // 📒 Estados para o Menu e Criação do Caderno
    var showFabMenu by remember { mutableStateOf(false) }
    var showCreateNoteDialog by remember { mutableStateOf(false) }
    var noteTitle by remember { mutableStateOf("") }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri ?: return@rememberLauncherForActivityResult; viewModel.importPdf(context, uri) }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri ?: return@rememberLauncherForActivityResult; viewModel.importBackup(context, uri) }
    var showMenu by remember { mutableStateOf(false) }
    var showImportWarning by remember { mutableStateOf(false) }

    var globalDragPos by remember { mutableStateOf(Offset.Zero) }
    var draggingDocId by remember { mutableStateOf<String?>(null) }
    var targetDocId by remember { mutableStateOf<String?>(null) }
    var targetFolderId by remember { mutableStateOf<Long?>(null) }
    val boundsMap = remember { mutableMapOf<String, Rect>() }
    val folderBoundsMap = remember { mutableMapOf<Long, Rect>() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                Spacer(Modifier.height(16.dp))
                Text("Jotdown", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Black, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                HorizontalDivider()
                NavigationDrawerItem(icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null) }, label = { Text("Tudo") }, selected = currentFilter == "Tudo", onClick = { viewModel.setFilter("Tudo"); scope.launch { drawerState.close() } })
                NavigationDrawerItem(icon = { Icon(Icons.Default.Schedule, contentDescription = null) }, label = { Text("Recentes") }, selected = currentFilter == "Recentes", onClick = { viewModel.setFilter("Recentes"); scope.launch { drawerState.close() } })
                NavigationDrawerItem(icon = { Icon(Icons.Default.Favorite, contentDescription = null) }, label = { Text("Favoritos") }, selected = currentFilter == "Favoritos", onClick = { viewModel.setFilter("Favoritos"); scope.launch { drawerState.close() } })
                NavigationDrawerItem(icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) }, label = { Text("Lixo") }, selected = currentFilter == "Lixo", onClick = { viewModel.setFilter("Lixo"); scope.launch { drawerState.close() } })
                
                if (availableTags.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Meus Rótulos", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                    availableTags.forEach { tag ->
                        NavigationDrawerItem(icon = { Icon(Icons.Default.Label, contentDescription = null, tint = Color(0xFFF59E0B)) }, label = { Text(tag) }, selected = currentFilter == "Tag:$tag", onClick = { viewModel.setFilter("Tag:$tag"); scope.launch { drawerState.close() } })
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (currentFolder == null) {
                            Text(if (currentFilter.startsWith("Tag:")) currentFilter.removePrefix("Tag:") else if (currentFilter == "Tudo") "Jotdown" else currentFilter, fontWeight = FontWeight.Black, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text(currentFolder!!.name, fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    navigationIcon = {
                        if (currentFolder != null) {
                            IconButton(onClick = { viewModel.enterFolder(null) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
                        }
                    },
                    actions = {
                        if (currentFolder != null) { IconButton(onClick = { showDeleteFolder = true }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Apagar", tint = MaterialTheme.colorScheme.error) } }
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Menu") }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("Exportar Backup", fontWeight = FontWeight.Bold) }, onClick = { showMenu = false; viewModel.exportBackup(context) })
                                DropdownMenuItem(text = { Text("Importar Backup") }, onClick = { showMenu = false; showImportWarning = true })
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            floatingActionButton = {
                // 📒 Botão Flutuante Atualizado (Expande as Opções)
                Column(horizontalAlignment = Alignment.End) {
                    if (showFabMenu) {
                        ExtendedFloatingActionButton(
                            onClick = { showFabMenu = false; pdfPicker.launch("application/pdf") },
                            icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                            text = { Text("Importar PDF", fontWeight = FontWeight.Bold) },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        ExtendedFloatingActionButton(
                            onClick = { showFabMenu = false; showCreateNoteDialog = true; noteTitle = "Caderno Sem Título" },
                            icon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                            text = { Text("Novo Caderno", fontWeight = FontWeight.Bold) },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                    FloatingActionButton(
                        onClick = { showFabMenu = !showFabMenu },
                        containerColor = Indigo600, contentColor = Color.White
                    ) {
                        Icon(if (showFabMenu) Icons.Default.Close else Icons.Default.Add, contentDescription = "Novo")
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { showFabMenu = false }) {
                
                val tabs = listOf("Tudo", "PDF", "Nota", "Pasta")
                val selectedTabIndex = tabs.indexOf(currentTab).coerceAtLeast(0)
                
                ScrollableTabRow(selectedTabIndex = selectedTabIndex, edgePadding = 0.dp, containerColor = Color.Transparent, divider = {}) {
                    tabs.forEachIndexed { index, title ->
                        Tab(selected = selectedTabIndex == index, onClick = { viewModel.setTab(title) }, text = { Text(title, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal, color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) })
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                val showFolders = currentFolder == null && currentFilter == "Tudo" && (currentTab == "Tudo" || currentTab == "Pasta")

                if (displayDocuments.isEmpty() && (!showFolders || folders.isEmpty())) {
                    EmptyLibrary(currentFilter, currentTab)
                } else {
                    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 140.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxSize()) {
                        if (showFolders) {
                            items(folders, key = { "folder_${it.id}" }) { folder ->
                                FolderCard(folder = folder, isTarget = targetFolderId == folder.id, onClick = { viewModel.enterFolder(folder) }, onRename = { folderToRename = folder; renameText = folder.name }, onGloballyPositioned = { coordinates -> folderBoundsMap[folder.id] = coordinates })
                            }
                        }
                        if (currentTab != "Pasta") {
                            items(displayDocuments, key = { it.id }) { doc ->
                                DocumentCoverCard(
                                    doc = doc, isDragging = draggingDocId == doc.id, isTarget = targetDocId == doc.id, inFolder = currentFolder != null, currentFilter = currentFilter, context = context,
                                    onCardClick = { if (currentFilter != "Lixo") onOpenDocument(doc.id) },
                                    onRename = { docToRename = doc; renameText = doc.title.ifBlank { doc.fileName } },
                                    onEditTags = { docToTag = doc; tagText = doc.labels },
                                    onDelete = { docToDelete = doc },
                                    onRemoveFromFolder = { viewModel.removeFromFolder(doc.id) },
                                    onMoveToTrash = { viewModel.moveToTrash(doc.id) },
                                    onRestore = { viewModel.restoreFromTrash(doc.id) },
                                    onToggleFavorite = { viewModel.toggleFavorite(doc.id, !doc.isFavorite) },
                                    onGloballyPositioned = { coordinates -> boundsMap[doc.id] = coordinates },
                                    onDragStart = { offset -> globalDragPos = boundsMap[doc.id]!!.topLeft + offset; draggingDocId = doc.id; showFabMenu = false },
                                    onDrag = { dragAmount -> globalDragPos += dragAmount; targetDocId = boundsMap.entries.find { it.key != doc.id && it.value.contains(globalDragPos) }?.key; targetFolderId = folderBoundsMap.entries.find { it.value.contains(globalDragPos) }?.key },
                                    onDragEnd = { if (targetDocId != null) viewModel.mergeIntoFolder(doc.id, targetDocId!!) else if (targetFolderId != null) viewModel.moveToFolder(doc.id, targetFolderId!!); draggingDocId = null; targetDocId = null; targetFolderId = null }
                                )
                            }
                        }
                        item { Spacer(Modifier.height(100.dp)) }
                    }
                }
            }
        }
    }

    // 📒 NOVO: Diálogo para Criar Caderno
    if (showCreateNoteDialog) {
        AlertDialog(
            onDismissRequest = { showCreateNoteDialog = false },
            title = { Text("Novo Caderno em Branco") },
            text = { OutlinedTextField(value = noteTitle, onValueChange = { noteTitle = it }, singleLine = true, label = { Text("Título do Caderno") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { if (noteTitle.isNotBlank()) { viewModel.createBlankNote(context, noteTitle.trim()) }; showCreateNoteDialog = false }) { Text("Criar", color = Indigo600, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showCreateNoteDialog = false }) { Text("Cancelar") } }
        )
    }

    folderToRename?.let { folder -> AlertDialog(onDismissRequest = { folderToRename = null }, title = { Text("Renomear Pasta") }, text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, label = { Text("Nome da pasta") }) }, confirmButton = { TextButton(onClick = { if (renameText.isNotBlank()) { viewModel.renameFolder(folder.id, renameText.trim()) }; folderToRename = null }) { Text("Salvar", color = Indigo600, fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = { folderToRename = null }) { Text("Cancelar") } }) }
    docToRename?.let { doc -> AlertDialog(onDismissRequest = { docToRename = null }, title = { Text("Renomear Arquivo") }, text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, label = { Text("Novo titulo") }) }, confirmButton = { TextButton(onClick = { if (renameText.isNotBlank()) { viewModel.renameDocument(doc.id, renameText.trim()) }; docToRename = null }) { Text("Salvar", color = Indigo600, fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = { docToRename = null }) { Text("Cancelar") } }) }
    docToDelete?.let { doc -> AlertDialog(onDismissRequest = { docToDelete = null }, title = { Text("Apagar Definitivamente?") }, text = { Text("\"${doc.title}\" será removido permanentemente do tablet.") }, confirmButton = { TextButton(onClick = { viewModel.deleteDocument(context, doc.id); docToDelete = null }) { Text("Apagar", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { docToDelete = null }) { Text("Cancelar") } }) }
    docToTag?.let { doc -> AlertDialog(onDismissRequest = { docToTag = null }, title = { Text("Editar Rótulos") }, text = { Column { Text("Separe os rótulos por vírgula.", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline); Spacer(Modifier.height(8.dp)); OutlinedTextField(value = tagText, onValueChange = { tagText = it }, label = { Text("Rótulos") }, modifier = Modifier.fillMaxWidth()) } }, confirmButton = { TextButton(onClick = { viewModel.updateLabels(doc.id, tagText); docToTag = null }) { Text("Salvar", color = Indigo600, fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = { docToTag = null }) { Text("Cancelar") } }) }
    if (showImportWarning) { AlertDialog(onDismissRequest = { showImportWarning = false }, title = { Text("Importar Backup") }, text = { Text("ATENCAO: A sua biblioteca atual será substituída.") }, confirmButton = { TextButton(onClick = { showImportWarning = false; backupPicker.launch("application/zip") }) { Text("Sim, Importar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = { showImportWarning = false }) { Text("Cancelar") } }) }
    if (showDeleteFolder) { AlertDialog(onDismissRequest = { showDeleteFolder = false }, title = { Text("Apagar Pasta?") }, text = { Text("Os PDFs voltarão para a biblioteca principal.") }, confirmButton = { TextButton(onClick = { viewModel.deleteCurrentFolder(); showDeleteFolder = false }) { Text("Apagar", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { showDeleteFolder = false }) { Text("Cancelar") } }) }
}

@Composable
fun FolderCard(folder: FolderEntity, isTarget: Boolean, onClick: () -> Unit, onRename: () -> Unit, onGloballyPositioned: (Rect) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).border(if (isTarget) 2.dp else 0.dp, if (isTarget) Indigo600 else Color.Transparent, RoundedCornerShape(12.dp)).onGloballyPositioned { coordinates -> onGloballyPositioned(coordinates.boundsInWindow()) }.clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = Indigo400, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text(folder.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { expanded = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Opções", tint = Indigo400) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { DropdownMenuItem(text = { Text("Renomear Pasta") }, onClick = { expanded = false; onRename() }) }
            }
        }
    }
}

@Composable
private fun DocumentCoverCard(
    doc: DocumentSummary, isDragging: Boolean, isTarget: Boolean, inFolder: Boolean, currentFilter: String, context: android.content.Context,
    onCardClick: () -> Unit, onRename: () -> Unit, onEditTags: () -> Unit, onDelete: () -> Unit, onRemoveFromFolder: () -> Unit, onMoveToTrash: () -> Unit, onRestore: () -> Unit, onToggleFavorite: () -> Unit,
    onGloballyPositioned: (Rect) -> Unit, onDragStart: (Offset) -> Unit, onDrag: (Offset) -> Unit, onDragEnd: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yy", Locale("pt", "BR")) }
    var expanded by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val animatedScale by animateFloatAsState(if (isDragging) 1.05f else 1f, label = "scale")
    val animatedAlpha by animateFloatAsState(if (isDragging) 0.85f else 1f, label = "alpha")
    val animatedElevation by animateDpAsState(if (isDragging) 12.dp else 2.dp, label = "elevation")
    
    val coverFile = File(context.filesDir, "covers/${doc.id}.jpg")
    var coverBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    
    LaunchedEffect(doc.id) {
        withContext(Dispatchers.IO) { if (coverFile.exists()) { try { coverBitmap = android.graphics.BitmapFactory.decodeFile(coverFile.absolutePath)?.asImageBitmap() } catch (e: Exception) {} } }
    }

    Column(
        modifier = Modifier.fillMaxWidth().offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }.graphicsLayer(alpha = animatedAlpha, scaleX = animatedScale, scaleY = animatedScale).onGloballyPositioned { coordinates -> onGloballyPositioned(coordinates.boundsInWindow()) }
            .pointerInput(doc.id) {
                if (currentFilter != "Lixo") { detectDragGesturesAfterLongPress(onDragStart = { offset -> onDragStart(offset) }, onDrag = { change, dragAmount -> change.consume(); dragOffset += dragAmount; onDrag(dragAmount) }, onDragEnd = { dragOffset = Offset.Zero; onDragEnd() }, onDragCancel = { dragOffset = Offset.Zero; onDragEnd() }) }
            }.clickable { onCardClick() }
    ) {
        Card(modifier = Modifier.fillMaxWidth().aspectRatio(0.7f).border(if (isTarget) 2.dp else 0.dp, if (isTarget) Indigo600 else Color.Transparent, RoundedCornerShape(8.dp)), shape = RoundedCornerShape(8.dp), elevation = CardDefaults.cardElevation(animatedElevation), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (coverBitmap != null) { Image(bitmap = coverBitmap!!, contentDescription = "Capa do PDF", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } 
                else { Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE5E7EB)), contentAlignment = Alignment.Center) { Icon(if (doc.docType == "nota") Icons.Default.EditNote else Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp)) } }
                
                IconButton(onClick = onToggleFavorite, modifier = Modifier.align(Alignment.TopStart).padding(4.dp)) { Icon(if (doc.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "Favoritar", tint = if (doc.isFavorite) Color.Red else Color.DarkGray) }
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    IconButton(onClick = { expanded = true }, modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(50))) { Icon(Icons.Default.MoreVert, contentDescription = "Opções", tint = Color.Black, modifier = Modifier.size(20.dp)) }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        if (currentFilter == "Lixo") { DropdownMenuItem(text = { Text("Restaurar") }, onClick = { expanded = false; onRestore() }); DropdownMenuItem(text = { Text("Apagar Definitivamente", color = Color.Red) }, onClick = { expanded = false; onDelete() }) } 
                        else { DropdownMenuItem(text = { Text("Renomear") }, onClick = { expanded = false; onRename() }); DropdownMenuItem(text = { Text("Editar Rótulos") }, onClick = { expanded = false; onEditTags() }); if (inFolder) { DropdownMenuItem(text = { Text("Remover da Pasta") }, onClick = { expanded = false; onRemoveFromFolder() }) }; DropdownMenuItem(text = { Text("Mover para o Lixo") }, onClick = { expanded = false; onMoveToTrash() }) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(doc.title.ifBlank { doc.fileName }, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (doc.labels.isNotBlank()) {
            val tags = doc.labels.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (tags.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tags.take(2).forEach { tag -> Box(modifier = Modifier.background(Color(0xFFFEF3C7), RoundedCornerShape(4.dp)).border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) { Text(tag, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706)) } }
                    if (tags.size > 2) { Text("+${tags.size - 2}", fontSize = 9.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.align(Alignment.CenterVertically)) }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
             Text("${doc.highlightCount} Cit. \u2022 ${doc.annotationCount} Notas", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
             Text(dateFormat.format(Date(doc.dateAdded)), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun EmptyLibrary(filter: String, tab: String) {
    val message = when {
        filter == "Favoritos" -> "Nenhum PDF favoritado."
        filter == "Lixo" -> "A lixeira está vazia."
        filter.startsWith("Tag:") -> "Nenhum texto encontrado para esta etiqueta."
        tab == "Pasta" -> "Nenhuma pasta criada."
        tab == "Nota" -> "Nenhum caderno ou PDF fichado."
        else -> "A sua estante está vazia."
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(if (filter == "Lixo") Icons.Default.DeleteOutline else if (filter.startsWith("Tag:")) Icons.Default.Label else if (tab == "Nota") Icons.Default.EditNote else Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(message, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}