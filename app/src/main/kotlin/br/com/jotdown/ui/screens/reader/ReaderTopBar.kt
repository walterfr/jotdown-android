package br.com.jotdown.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    activeTool: Tool, strokeColor: Int, annotationCount: Int,
    onBack: () -> Unit, onMenuClick: () -> Unit,
    onToolSelect: (Tool) -> Unit, onColorSelect: (Int) -> Unit,
    onAnnotations: () -> Unit, onCapture: () -> Unit,
    onUndo: () -> Unit, onRedo: () -> Unit // 🛡️ NOVOS
) {
    TopAppBar(
        title = {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                ReaderTooltipButton("Selecionar", Icons.Default.Crop, activeTool == Tool.SELECT) { onToolSelect(Tool.SELECT) }
                ReaderTooltipButton("Caneta", Icons.Default.Edit, activeTool == Tool.PEN) { onToolSelect(Tool.PEN) }
                ReaderTooltipButton("Lápis", Icons.Default.Brush, activeTool == Tool.PENCIL) { onToolSelect(Tool.PENCIL) }
                ReaderTooltipButton("Marca-texto", Icons.Default.BorderColor, activeTool == Tool.HIGHLIGHTER) { onToolSelect(Tool.HIGHLIGHTER) }
                ReaderTooltipButton("Borracha", Icons.Default.AutoFixHigh, activeTool == Tool.ERASER) { onToolSelect(Tool.ERASER) }
                ReaderTooltipButton("Post-it", Icons.AutoMirrored.Filled.StickyNote2, activeTool == Tool.ANNOTATION) { onToolSelect(Tool.ANNOTATION) }
                
                VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
                
                // 🛡️ BOTÕES UNDO / REDO
                IconButton(onClick = onUndo) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Desfazer") }
                IconButton(onClick = onRedo) { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Refazer") }
            }
        },
        navigationIcon = { ReaderTooltipButton("Voltar", Icons.AutoMirrored.Filled.ArrowBack, false, onBack) },
        actions = {
            if (activeTool == Tool.PEN || activeTool == Tool.PENCIL || activeTool == Tool.HIGHLIGHTER) {
                Row(modifier = Modifier.padding(end = 8.dp)) {
                    listOf(0xFF000000.toInt(), 0xFFEF4444.toInt(), 0xFF3B82F6.toInt(), 0xFF10B981.toInt(), 0xFFF59E0B.toInt()).forEach { color ->
                        Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(Color(color)).clickable { onColorSelect(color) }.padding(4.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }
            Button(onClick = onCapture, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)), shape = RoundedCornerShape(8.dp), modifier = Modifier.height(36.dp)) {
                Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                Text("Capturar", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
            }
            IconButton(onClick = onAnnotations) {
                BadgedBox(badge = { if (annotationCount > 0) Badge { Text(annotationCount.toString()) } }) {
                    Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null)
                }
            }
            IconButton(onClick = onMenuClick) { Icon(Icons.Default.MoreVert, contentDescription = null) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTooltipButton(text: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(text) } }, state = rememberTooltipState()) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = text, tint = if (isSelected) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
    }
}