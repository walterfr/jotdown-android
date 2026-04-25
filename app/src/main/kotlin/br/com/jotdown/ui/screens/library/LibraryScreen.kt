package br.com.jotdown.ui.screens.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: LibraryViewModel, onOpenDocument: (String) -> Unit) {
    val context = LocalContext.current
    val documents by viewModel.documents.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val currentFolder by viewModel.currentFolder.collectAsState()
    
    var docToDelete by remember { mutableStateOf<DocumentSummary?>(null) }
    var showDeleteFolder by remember { mutableStateOf(false) }
    
    // Estados de Renomeacao
    var folderToRename by remember { mutableStateOf<FolderEntity?>(null) }
    var docToRename by remember { mutableStateOf<DocumentSummary?>(null) }
    var renameText by remember { mutableStateOf("") }
    
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.importPdf(context, uri)
    }

    // Variaveis para rasteio do dedo (Arrastar e Soltar)
    var globalDragPos by remember { mutableStateOf(Offset.Zero) }
    var draggingDocId by remember { mutableStateOf<String?>(null) }
    var targetDocId by remember { mutableStateOf<String?>(null) }
    var targetFolderId by remember { mutableStateOf<Long?>(null) }
    val boundsMap = remember { mutableMapOf<String, Rect>() }
    val folderBoundsMap = remember { mutableMapOf<Long, Rect>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (currentFolder == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Indigo600), contentAlignment = Alignment.Center) { 
                                Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) 
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("Jotdown", fontWeight = FontWeight.Black, fontSize = 22.sp, color = Indigo900)
                        }
                    } else {
                        Text(currentFolder!!.name, fontWeight = FontWeight.Black, fontSize = 20.sp, color = Indigo900)
                    }
                },
                navigationIcon = {
                    if (currentFolder != null) {
                        IconButton(onClick = { viewModel.enterFolder(null) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
                    }
                },
                actions = {
                    if (currentFolder != null) {
                        IconButton(onClick = { showDeleteFolder = true }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Apagar Pasta", tint = MaterialTheme.colorScheme.error) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pdfPicker.launch("application/pdf") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Novo PDF", fontWeight = FontWeight.Bold) },
                containerColor = Indigo600, contentColor = Color.White
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(16.dp))
            if (currentFolder == null) {
                Text("Documentos Salvos", fontSize = 26.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
                Text("Segure um PDF e arraste sobre outro para criar uma pasta.", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
            }

            if (documents.isEmpty() && (folders.isEmpty() || currentFolder != null)) {
                EmptyLibrary()
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (currentFolder == null) {
                        items(folders, key = { "folder_${it.id}" }) { folder ->
                            FolderCard(
                                folder = folder,
                                isTarget = targetFolderId == folder.id,
                                onClick = { viewModel.enterFolder(folder) },
                                onRename = { folderToRename = folder; renameText = folder.name },
                                onGloballyPositioned = { coordinates -> folderBoundsMap[folder.id] = coordinates }
                            )
                        }
                    }
                    items(documents, key = { it.id }) { doc ->
                        DocumentCard(
                            doc = doc, isDragging = draggingDocId == doc.id, isTarget = targetDocId == doc.id, inFolder = currentFolder != null,
                            onCardClick = { onOpenDocument(doc.id) },
                            onRename = { docToRename = doc; renameText = doc.title.ifBlank { doc.fileName } },
                            onDelete = { docToDelete = doc },
                            onRemoveFromFolder = { viewModel.removeFromFolder(doc.id) },
                            onGloballyPositioned = { coordinates -> boundsMap[doc.id] = coordinates },
                            onDragStart = { offset -> globalDragPos = boundsMap[doc.id]!!.topLeft + offset; draggingDocId = doc.id },
                            onDrag = { dragAmount ->
                                globalDragPos += dragAmount
                                targetDocId = boundsMap.entries.find { it.key != doc.id && it.value.contains(globalDragPos) }?.key
                                targetFolderId = folderBoundsMap.entries.find { it.value.contains(globalDragPos) }?.key
                            },
                            onDragEnd = {
                                if (targetDocId != null) viewModel.mergeIntoFolder(doc.id, targetDocId!!)
                                else if (targetFolderId != null) viewModel.moveToFolder(doc.id, targetFolderId!!)
                                draggingDocId = null; targetDocId = null; targetFolderId = null
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    // Modal Renomear Pasta
    folderToRename?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToRename = null }, title = { Text("Renomear Pasta") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, label = { Text("Nome da pasta") }) },
            confirmButton = { TextButton(onClick = { if (renameText.isNotBlank()) { viewModel.renameFolder(folder.id, renameText.trim()) }; folderToRename = null }) { Text("Salvar", color = Indigo600, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { folderToRename = null }) { Text("Cancelar") } }
        )
    }

    // Modal Renomear Arquivo
    docToRename?.let { doc ->
        AlertDialog(
            onDismissRequest = { docToRename = null }, title = { Text("Renomear Arquivo") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, label = { Text("Novo titulo") }) },
            confirmButton = { TextButton(onClick = { if (renameText.isNotBlank()) { viewModel.renameDocument(doc.id, renameText.trim()) }; docToRename = null }) { Text("Salvar", color = Indigo600, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { docToRename = null }) { Text("Cancelar") } }
        )
    }

    docToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { docToDelete = null }, title = { Text("Apagar documento?") }, text = { Text("\"${doc.title}\" sera removido permanentemente.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteDocument(context, doc.id); docToDelete = null }) { Text("Apagar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { docToDelete = null }) { Text("Cancelar") } }
        )
    }

    if (showDeleteFolder) {
        AlertDialog(
            onDismissRequest = { showDeleteFolder = false }, title = { Text("Apagar Pasta?") }, text = { Text("A pasta sera apagada, mas os PDFs dentro dela voltarao para a biblioteca principal.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteCurrentFolder(); showDeleteFolder = false }) { Text("Apagar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteFolder = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun FolderCard(folder: FolderEntity, isTarget: Boolean, onClick: () -> Unit, onRename: () -> Unit, onGloballyPositioned: (Rect) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .border(if (isTarget) 2.dp else 0.dp, if (isTarget) Indigo600 else Color.Transparent, RoundedCornerShape(16.dp))
            .onGloballyPositioned { coordinates -> onGloballyPositioned(coordinates.boundsInWindow()) }
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Indigo50)
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = Indigo400, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Text(folder.name, fontWeight = FontWeight.Bold, color = Indigo900, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Edit, contentDescription = "Renomear", tint = Indigo400, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun DocumentCard(
    doc: DocumentSummary, isDragging: Boolean, isTarget: Boolean, inFolder: Boolean,
    onCardClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onRemoveFromFolder: () -> Unit,
    onGloballyPositioned: (Rect) -> Unit, onDragStart: (Offset) -> Unit, onDrag: (Offset) -> Unit, onDragEnd: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }
    
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val animatedScale by animateFloatAsState(if (isDragging) 1.05f else 1f, label = "scale")
    val animatedAlpha by animateFloatAsState(if (isDragging) 0.85f else 1f, label = "alpha")
    val animatedElevation by animateDpAsState(if (isDragging) 12.dp else 2.dp, label = "elevation")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
            .graphicsLayer(alpha = animatedAlpha, scaleX = animatedScale, scaleY = animatedScale)
            .border(if (isTarget) 2.dp else 0.dp, if (isTarget) Indigo600 else Color.Transparent, RoundedCornerShape(16.dp))
            .onGloballyPositioned { coordinates -> onGloballyPositioned(coordinates.boundsInWindow()) }
            .pointerInput(doc.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset -> onDragStart(offset) },
                    onDrag = { change, dragAmount -> 
                        change.consume()
                        dragOffset += dragAmount
                        onDrag(dragAmount) 
                    },
                    onDragEnd = { dragOffset = Offset.Zero; onDragEnd() },
                    onDragCancel = { dragOffset = Offset.Zero; onDragEnd() }
                )
            }
            .clickable { onCardClick() },
        shape = RoundedCornerShape(16.dp), 
        elevation = CardDefaults.cardElevation(animatedElevation), 
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFE4E6)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(26.dp))
                }
                Row {
                    if (inFolder) {
                        IconButton(onClick = onRemoveFromFolder, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Output, contentDescription = "Tirar", tint = Indigo600, modifier = Modifier.size(18.dp)) }
                    }
                    IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Edit, contentDescription = "Renomear", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.DeleteOutline, contentDescription = "Apagar", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp)) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(doc.title.ifBlank { doc.fileName }, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (doc.authorLastName.isNotBlank()) Text("${doc.authorLastName}${if (doc.authorFirstName.isNotBlank()) ", ${doc.authorFirstName}" else ""}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Indigo600.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                    Text("${doc.highlightCount} Cit.", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Indigo600)
                }
                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFF59E0B).copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                    Text("${doc.annotationCount} Notas", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                }
                Spacer(Modifier.weight(1f))
                Text(dateFormat.format(Date(doc.dateAdded)), fontSize = 9.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun EmptyLibrary() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(24.dp)).background(Indigo50), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = Indigo200, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("A sua estante esta vazia.", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}
