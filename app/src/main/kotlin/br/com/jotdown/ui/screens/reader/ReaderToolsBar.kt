package br.com.jotdown.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// Paleta de cores das ferramentas de desenho
private val toolPalette = listOf(
    0xFF000000.toInt(),
    0xFFEF4444.toInt(),
    0xFF3B82F6.toInt(),
    0xFF10B981.toInt(),
    0xFFF59E0B.toInt(),
    0xFF8B5CF6.toInt(),
)

// Ferramentas disponíveis: (enum, ícone, rótulo tooltip)
private val toolButtons = listOf(
    Triple(Tool.PEN,         Icons.Default.Edit,                          "Caneta"),
    Triple(Tool.PENCIL,      Icons.Default.Brush,                         "Lápis"),
    Triple(Tool.HIGHLIGHTER, Icons.Default.BorderColor,                   "Marca-texto"),
    Triple(Tool.ERASER,      Icons.Default.AutoFixHigh,                   "Borracha"),
    Triple(Tool.ANNOTATION,  Icons.AutoMirrored.Filled.StickyNote2,       "Post-it"),
    Triple(Tool.SELECT,      Icons.Default.Crop,                          "Selecionar"),
)

/**
 * Barra de ferramentas flutuante inferior do leitor.
 *
 * Exibe os botões de ferramenta como uma pílula elevada.
 * Quando a ferramenta ativa suporta cor (caneta, lápis, marca-texto),
 * anima a exibição do seletor de cores acima da barra.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderToolsBar(
    activeTool: Tool,
    strokeColor: Int,
    onToolSelect: (Tool) -> Unit,
    onColorSelect: (Int) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    val showColorPicker = activeTool == Tool.PEN
            || activeTool == Tool.PENCIL
            || activeTool == Tool.HIGHLIGHTER

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Seletor de cores (aparece/some com animação) ──────────────────
        AnimatedVisibility(
            visible = showColorPicker,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit  = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            Surface(
                shape         = RoundedCornerShape(50),
                color         = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    toolPalette.forEach { color ->
                        ColorDot(
                            color      = Color(color),
                            isSelected = strokeColor == color,
                            onClick    = { onColorSelect(color) },
                        )
                    }
                }
            }
        }

        // ── Barra de ferramentas ──────────────────────────────────────────
        Surface(
            shape           = RoundedCornerShape(50),
            color           = MaterialTheme.colorScheme.surface,
            tonalElevation  = 8.dp,
            shadowElevation = 16.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                toolButtons.forEach { (tool, icon, label) ->
                    ToolIconButton(
                        icon       = icon,
                        label      = label,
                        isSelected = activeTool == tool,
                        onClick    = { onToolSelect(tool) },
                    )
                }

                VerticalDivider(
                    modifier = Modifier
                        .height(28.dp)
                        .padding(horizontal = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                // Undo / Redo
                IconButton(onClick = onUndo, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Desfazer",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                IconButton(onClick = onRedo, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Refazer",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

// ── Componentes internos ──────────────────────────────────────────────────────

/** Círculo de cor com animação de tamanho e anel de seleção. */
@Composable
private fun ColorDot(color: Color, isSelected: Boolean, onClick: () -> Unit) {
    val size by animateDpAsState(targetValue = if (isSelected) 32.dp else 26.dp, label = "dotSize")
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .then(
                if (isSelected)
                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), CircleShape)
                else Modifier
            )
            .clickable { onClick() },
    )
}

/** Botão de ferramenta com fundo circular + borda quando selecionado (Material 3 selected state). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolIconButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else Color.Transparent
                )
                .then(
                    if (isSelected)
                        Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), CircleShape)
                    else Modifier
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
