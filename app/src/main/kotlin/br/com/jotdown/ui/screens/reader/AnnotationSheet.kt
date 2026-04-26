package br.com.jotdown.ui.screens.reader

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.jotdown.data.entity.AnnotationEntity
import br.com.jotdown.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationSheet(
    annotations: List<AnnotationEntity>,
    onDismiss: () -> Unit,
    onDelete: (Long) -> Unit,
    onGoToPage: ((Int) -> Unit)? = null
) {
    val validAnnotations = annotations.filter { it.text.isNotBlank() }.sortedBy { it.page }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1E1B4B)  // indigo-900
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)) {

            // Header Ã¢mbar estilo HTML
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF59E0B))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.StickyNote2, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Minhas AnotaÃ§Ãµes", fontWeight = FontWeight.Bold,
                        fontSize = 15.sp, color = Color.White)
                    if (validAnnotations.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF92400E))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text("${validAnnotations.size}", fontSize = 10.sp,
                                fontWeight = FontWeight.Black, color = Color.White)
                        }
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterEnd).size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar",
                        tint = Color(0xFFFDE68A))
                }
            }

            if (validAnnotations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1B4B)),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ChatBubbleOutline, contentDescription = null,
                            tint = Color(0xFF4B5563), modifier = Modifier.size(52.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Nenhuma anotaÃ§Ã£o criada.", color = Color(0xFF6B7280),
                            fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().background(Color(0xFFFFFBEB).copy(alpha = 0.07f)),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(validAnnotations, key = { it.id }) { annot ->
                        // Card estilo HTML: fundo branco, borda Ã¢mbar, clicÃ¡vel para navegar
                        Card(
                            onClick = { onGoToPage?.invoke(annot.page); onDismiss() },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(3.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    // Badge de pÃ¡gina Ã¢mbar
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFFFEF3C7))
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text("PÃ¡g. ${annot.page}", fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                                        }
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                                            tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text("\"${annot.text}\"", fontSize = 13.sp, maxLines = 4,
                                        overflow = TextOverflow.Ellipsis,
                                        fontStyle = FontStyle.Italic,
                                        color = Color(0xFF374151),
                                        lineHeight = 18.sp)
                                }
                                IconButton(onClick = { onDelete(annot.id) },
                                    modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Apagar",
                                        tint = Color(0xFF9CA3AF), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}


