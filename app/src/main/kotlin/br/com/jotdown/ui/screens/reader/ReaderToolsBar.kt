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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.jotdown.R

// Paleta de cores das ferramentas de desenho
private val toolPalette = listOf(
    0xFF000000.toInt(),
    0xFFEF4444.toInt(),
    0xFF3B82F6.toInt(),
    0xFF10B981.toInt(),
    0xFFF59E0B.toInt(),
    0xFF8B5CF6.toInt(),
)

// Espessuras pré-definidas: (multiplicador, tamanho visual do indicador)
private val strokeWidths = listOf(
    Pair(0.5f,  6.dp),
    Pair(1.0f,  10.dp),
    Pair(2.0f,  15.dp),
)

// Ferramentas disponíveis: (enum, ícone, id do rótulo/tooltip)
private val toolButtons = listOf(
    Triple(Tool.PEN,         Icons.Default.Edit,                          R.string.tool_pen),
    Triple(Tool.PENCIL,      Icons.Default.Brush,                         R.string.tool_pencil),
    Triple(Tool.HIGHLIGHTER, Icons.Default.BorderColor,                   R.string.tool_highlighter),
    Triple(Tool.ERASER,      Icons.Default.AutoFixHigh,                   R.string.tool_eraser),
    Triple(Tool.ANNOTATION,  Icons.AutoMirrored.Filled.StickyNote2,       R.string.tool_postit),
    Triple(Tool.SELECT,      Icons.Default.Crop,                          R.string.tool_select),
    Triple(Tool.DICTIONARY,  Icons.Default.Translate,                     R.string.tool_dictionary),
)

/**
 * Barra de ferramentas flutuante inferior do leitor.
 *
 * - Ferramentas de desenho/borracha: exibe seletor de cor + seletor de espessura.
 * - Borracha: exibe apenas seletor de espessura.
 * - A espessura é um multiplicador sobre a largura base de cada ferramenta;
 *   a pressão da caneta continua afetando individualmente cada ponto do traço.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderToolsBar(
    activeTool: Tool,
    strokeColor: Int,
    strokeWidthMultiplier: Float,
    onToolSelect: (Tool) -> Unit,
    onColorSelect: (Int) -> Unit,
    onWidthSelect: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    val showColorPicker     = activeTool == Tool.PEN || activeTool == Tool.PENCIL || activeTool == Tool.HIGHLIGHTER
    val showThicknessPicker = showColorPicker || activeTool == Tool.ERASER

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Seletores de cor e/ou espessura ──────────────────────────────
        AnimatedVisibility(
            visible = showThicknessPicker,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit  = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Seletor de cores (apenas para ferramentas de desenho, não borracha)
                if (showColorPicker) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 6.dp,
                        shadowElevation = 6.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
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

                // Seletor de espessura (para desenho e borracha)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        strokeWidths.forEach { (multiplier, dotSize) ->
                            ThicknessDot(
                                visualSize = dotSize,
                                isSelected = strokeWidthMultiplier == multiplier,
                                dotColor   = if (activeTool == Tool.ERASER)
                                                 MaterialTheme.colorScheme.onSurfaceVariant
                                             else Color(strokeColor),
                                onClick    = { onWidthSelect(multiplier) },
                            )
                        }
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
                toolButtons.forEach { (tool, icon, labelRes) ->
                    ToolIconButton(
                        icon       = icon,
                        label      = stringResource(labelRes),
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
                        contentDescription = stringResource(R.string.tool_undo),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
                IconButton(onClick = onRedo, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.Redo,
                        contentDescription = stringResource(R.string.tool_redo),
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

/**
 * Indicador de espessura: círculo sólido na cor atual da ferramenta,
 * com borda de seleção quando ativo. Tamanho fixo de toque (32dp),
 * o círculo visual cresce conforme a espessura representada.
 */
@Composable
private fun ThicknessDot(
    visualSize: Dp,
    isSelected: Boolean,
    dotColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(visualSize)
                .clip(CircleShape)
                .background(dotColor)
                .then(
                    if (isSelected)
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    else Modifier
                ),
        )
    }
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
