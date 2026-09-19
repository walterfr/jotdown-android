package br.com.jotdown.ui.screens.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    var showCustomColorDialog by remember { mutableStateOf(false) }
    var showThicknessDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
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
                            // Botão para cor customizada
                            val isCustomColor = strokeColor !in toolPalette
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isCustomColor) Color(strokeColor) else MaterialTheme.colorScheme.surface)
                                    .border(
                                        width = if (isCustomColor) 2.5.dp else 1.dp,
                                        color = if (isCustomColor) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable { showCustomColorDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = stringResource(R.string.custom_color_title),
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isCustomColor) {
                                        val c = Color(strokeColor)
                                        if (c.red * 0.299 + c.green * 0.587 + c.blue * 0.114 > 0.5) Color.Black else Color.White
                                    } else MaterialTheme.colorScheme.onSurfaceVariant
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
                        // Botão de ajuste fino de espessura
                        val isCustomWidth = strokeWidths.none { kotlin.math.abs(it.first - strokeWidthMultiplier) < 0.01f }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (isCustomWidth) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                .border(
                                    width = if (isCustomWidth) 2.dp else 1.dp,
                                    color = if (isCustomWidth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                                .clickable { showThicknessDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = stringResource(R.string.custom_thickness_title),
                                modifier = Modifier.size(16.dp),
                                tint = if (isCustomWidth) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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

        if (showCustomColorDialog) {
            CustomColorDialog(
                initialColor = strokeColor,
                onColorSelected = { onColorSelect(it); showCustomColorDialog = false },
                onDismiss = { showCustomColorDialog = false }
            )
        }

        if (showThicknessDialog) {
            CustomThicknessDialog(
                currentMultiplier = strokeWidthMultiplier,
                strokeColor = strokeColor,
                isEraser = activeTool == Tool.ERASER,
                onWidthSelected = { onWidthSelect(it); showThicknessDialog = false },
                onDismiss = { showThicknessDialog = false }
            )
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

/** Diálogo de seleção de cor personalizada com paleta estendida e sliders RGB. */
@Composable
fun CustomColorDialog(
    initialColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val initialComposeColor = Color(initialColor)
    var red by remember { mutableFloatStateOf(initialComposeColor.red * 255f) }
    var green by remember { mutableFloatStateOf(initialComposeColor.green * 255f) }
    var blue by remember { mutableFloatStateOf(initialComposeColor.blue * 255f) }

    val currentColor = Color(red.toInt(), green.toInt(), blue.toInt())

    val presetPalettes = remember {
        listOf(
            // Tons escuros / neutros
            0xFF000000.toInt(), 0xFF1F2937.toInt(), 0xFF4B5563.toInt(), 0xFF9CA3AF.toInt(), 0xFF78350F.toInt(),
            // Tons fortes
            0xFFDC2626.toInt(), 0xFFEA580C.toInt(), 0xFFD97706.toInt(), 0xFF16A34A.toInt(), 0xFF059669.toInt(),
            0xFF0891B2.toInt(), 0xFF2563EB.toInt(), 0xFF1E3A8A.toInt(), 0xFF7C3AED.toInt(), 0xFFDB2777.toInt(),
            // Marca-texto & pastéis
            0xFFFEF08A.toInt(), 0xFFFED7AA.toInt(), 0xFFBBF7D0.toInt(), 0xFFBAE6FD.toInt(), 0xFFFBCFE8.toInt(),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.custom_color_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Pré-visualização da cor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                    Column {
                        val hex = String.format("#%02X%02X%02X", red.toInt(), green.toInt(), blue.toInt())
                        Text(
                            hex,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "R: ${red.toInt()}  G: ${green.toInt()}  B: ${blue.toInt()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    stringResource(R.string.custom_palette_more),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                // Grid de cores rápidas
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presetPalettes.chunked(5).forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            rowColors.forEach { c ->
                                val isSelected = (currentColor.toArgb() and 0x00FFFFFF) == (c and 0x00FFFFFF)
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(c))
                                        .then(
                                            if (isSelected)
                                                Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                            else Modifier.border(0.5.dp, Color.Black.copy(alpha = 0.2f), CircleShape)
                                        )
                                        .clickable {
                                            val selected = Color(c)
                                            red = selected.red * 255f
                                            green = selected.green * 255f
                                            blue = selected.blue * 255f
                                        }
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Sliders RGB
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("R", color = Color.Red, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                        Slider(
                            value = red,
                            onValueChange = { red = it },
                            valueRange = 0f..255f,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${red.toInt()}", modifier = Modifier.width(32.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("G", color = Color(0xFF16A34A), fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                        Slider(
                            value = green,
                            onValueChange = { green = it },
                            valueRange = 0f..255f,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${green.toInt()}", modifier = Modifier.width(32.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("B", color = Color.Blue, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp))
                        Slider(
                            value = blue,
                            onValueChange = { blue = it },
                            valueRange = 0f..255f,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${blue.toInt()}", modifier = Modifier.width(32.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onColorSelected(currentColor.toArgb()) }) {
                Text(stringResource(R.string.common_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/** Diálogo de seleção contínua de espessura de traço com pré-visualização. */
@Composable
fun CustomThicknessDialog(
    currentMultiplier: Float,
    strokeColor: Int,
    isEraser: Boolean,
    onWidthSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var multiplier by remember { mutableFloatStateOf(currentMultiplier.coerceIn(0.2f, 4.0f)) }
    val presetMultipliers = listOf(0.5f, 1.0f, 1.5f, 2.0f, 3.0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.custom_thickness_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Linha de pré-visualização
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                        val baseWidth = 4.dp.toPx()
                        val strokePx = baseWidth * multiplier
                        val start = Offset(x = 30.dp.toPx(), y = size.height / 2)
                        val end = Offset(x = size.width - 30.dp.toPx(), y = size.height / 2)
                        drawLine(
                            color = if (isEraser) Color.Gray else Color(strokeColor),
                            start = start,
                            end = end,
                            strokeWidth = strokePx,
                            cap = StrokeCap.Round
                        )
                    }
                }

                Text(
                    String.format(java.util.Locale.US, "%.2fx", multiplier),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Slider(
                    value = multiplier,
                    onValueChange = { multiplier = it },
                    valueRange = 0.2f..4.0f,
                    modifier = Modifier.fillMaxWidth()
                )

                // Presets rápidos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    presetMultipliers.forEach { p ->
                        OutlinedButton(
                            onClick = { multiplier = p },
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = if (kotlin.math.abs(multiplier - p) < 0.05f) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text("${p}x", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onWidthSelected(multiplier) }) {
                Text(stringResource(R.string.common_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
