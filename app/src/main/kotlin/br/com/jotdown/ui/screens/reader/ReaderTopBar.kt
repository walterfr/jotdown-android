package br.com.jotdown.ui.screens.reader
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
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
import br.com.jotdown.ui.theme.*
import br.com.jotdown.ui.viewmodel.Tool

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    activeTool: Tool,
    strokeColor: Int,
    onBack: () -> Unit,
    onMenuClick: () -> Unit,
    onToolSelect: (Tool) -> Unit,
    onColorSelect: (Int) -> Unit,
    onAnnotations: () -> Unit,
    onCapture: () -> Unit
) {
    val colors = listOf(
        0xFF000000.toInt(), 0xFFEF4444.toInt(), 0xFF3B82F6.toInt(),
        0xFF10B981.toInt(), 0xFFFDE047.toInt(), 0xFF8B5CF6.toInt()
    )

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ToolGroup {
                    // SeleÃ§Ã£o de texto (toggle)
                    ToolButton(
                        icon     = Icons.Default.TextFields,
                        selected = activeTool == Tool.SELECT,
                        tooltip  = "Selecionar texto para captura (toque para ligar/desligar)",
                        onClick  = { onToolSelect(Tool.SELECT) }
                    )
                    ToolDivider()
                    ToolButton(
                        icon    = Icons.Default.Edit,
                        selected = activeTool == Tool.PEN,
                        tooltip = "Caneta (desenho livre)",
                        onClick = { onToolSelect(Tool.PEN) }
                    )
                    ToolButton(
                        icon    = Icons.Default.Draw,
                        selected = activeTool == Tool.PENCIL,
                        tooltip = "LÃ¡pis (traÃ§o fino)",
                        onClick = { onToolSelect(Tool.PENCIL) }
                    )
                    ToolButton(
                        icon    = Icons.Default.Highlight,
                        selected = activeTool == Tool.HIGHLIGHTER,
                        tooltip = "Marcador de texto",
                        onClick = { onToolSelect(Tool.HIGHLIGHTER) }
                    )
                    ToolButton(
                        icon    = Icons.Default.AutoFixNormal,
                        selected = activeTool == Tool.ERASER,
                        tooltip = "Borracha",
                        onClick = { onToolSelect(Tool.ERASER) }
                    )
                    ToolDivider()
                    ToolButton(
                        icon     = Icons.Default.StickyNote2,
                        selected = activeTool == Tool.ANNOTATION,
                        tint     = if (activeTool == Tool.ANNOTATION) Color(0xFFF59E0B) else null,
                        tooltip  = "Post-it: toque no PDF para adicionar anotaÃ§Ã£o",
                        onClick  = { onToolSelect(Tool.ANNOTATION) }
                    )
                }

                // Paleta de cores â€” visÃ­vel quando uma ferramenta de desenho estÃ¡ ativa
                if (activeTool == Tool.PEN || activeTool == Tool.PENCIL || activeTool == Tool.HIGHLIGHTER) {
                    Spacer(Modifier.width(4.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        colors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color(color))
                                    .then(if (strokeColor == color)
                                        Modifier.border(2.dp, Color.White, CircleShape)
                                    else Modifier)
                                    .clickable { onColorSelect(color) }
                            )
                        }
                    }
                }

                // BotÃ£o Capturar â€” sÃ³ aparece quando SELECT estÃ¡ ativo
                if (activeTool == Tool.SELECT) {
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = onCapture,
                        colors  = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.FormatQuote, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Capturar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        navigationIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu")
                }
            }
        },
        actions = {
            IconButton(onClick = onAnnotations) {
                Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = "Anotacoes",
                    tint = MaterialTheme.colorScheme.onSurface)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
    )
}

@Composable
private fun ToolGroup(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment     = Alignment.CenterVertically,
        content               = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolButton(
    icon: ImageVector,
    selected: Boolean,
    tooltip: String,
    tint: Color? = null,
    onClick: () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(tooltip, fontSize = 11.sp)
            }
        },
        state = rememberTooltipState()
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = tooltip,
                tint     = tint ?: if (selected) Indigo600 else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ToolDivider() {
    Box(modifier = Modifier.width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
}


