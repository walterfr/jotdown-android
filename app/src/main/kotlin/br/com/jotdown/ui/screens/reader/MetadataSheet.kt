package br.com.jotdown.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.jotdown.data.entity.DocumentEntity
import br.com.jotdown.data.entity.HighlightEntity
import br.com.jotdown.ui.theme.*
import br.com.jotdown.util.ExportUtil
import java.util.Locale

data class MetadataParams(
    val docType: String, val authorLastName: String, val authorFirstName: String,
    val title: String, val subtitle: String, val edition: String,
    val city: String, val publisher: String, val year: String,
    val journal: String, val volume: String, val pages: String,
    val url: String, val accessDate: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataSheet(
    document: DocumentEntity?,
    highlights: List<HighlightEntity>,
    onSave: (MetadataParams) -> Unit,
    onDismiss: () -> Unit
) {
    val doc = document ?: return

    var docType         by remember { mutableStateOf(doc.docType) }
    var authorLastName  by remember { mutableStateOf(doc.authorLastName) }
    var authorFirstName by remember { mutableStateOf(doc.authorFirstName) }
    var title           by remember { mutableStateOf(doc.title) }
    var subtitle        by remember { mutableStateOf(doc.subtitle) }
    var edition         by remember { mutableStateOf(doc.edition) }
    var city            by remember { mutableStateOf(doc.city) }
    var publisher       by remember { mutableStateOf(doc.publisher) }
    var year            by remember { mutableStateOf(doc.year) }
    var journal         by remember { mutableStateOf(doc.journal) }
    var volume          by remember { mutableStateOf(doc.volume) }
    var pages           by remember { mutableStateOf(doc.pages) }
    var url             by remember { mutableStateOf(doc.url) }
    var accessDate      by remember { mutableStateOf(doc.accessDate) }

    val context = LocalContext.current

    fun buildCurrentDoc() = doc.copy(
        docType = docType, authorLastName = authorLastName,
        authorFirstName = authorFirstName, title = title,
        subtitle = subtitle, edition = edition, city = city,
        publisher = publisher, year = year, journal = journal,
        volume = volume, pages = pages, url = url, accessDate = accessDate
    )

    fun currentParams() = MetadataParams(
        docType, authorLastName, authorFirstName,
        title, subtitle, edition, city, publisher,
        year, journal, volume, pages, url, accessDate
    )

    val mdPreview = remember(authorLastName, title, year, highlights) {
        val lastName = authorLastName.uppercase(Locale.getDefault())
        val authorFmt = authorLastName.lowercase(Locale.getDefault()).replaceFirstChar { it.uppercase() }
        buildString {
            appendLine("## $title")
            appendLine()
            appendLine("**Refer\u00eancia:** $lastName, $authorFirstName. **$title**. $year.")
            appendLine()
            if (highlights.isNotEmpty()) {
                appendLine("## Destaques")
                highlights.forEachIndexed { i, h ->
                    appendLine("[${i+1}] > ${h.text} ($authorFmt, $year, p. ${h.page})")
                    appendLine()
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.94f)) {

            Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1B4B)).padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFFC7D2FE), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Metadados & Fichamento", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White, letterSpacing = 0.3.sp)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd).size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color(0xFFA5B4FC))
                }
            }

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionLabel("Refer\u00eancia ABNT")

                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = when (docType) {
                            "livro"  -> "Livro / Monografia / Tese"
                            "artigo" -> "Artigo de Revista / Peri\u00f3dico"
                            else     -> "Documento Eletr\u00f4nico (Site)"
                        },
                        onValueChange = {}, readOnly = true, label = { Text("Tipo") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(), colors = metaFieldColors()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf(
                            "livro" to "Livro / Monografia / Tese",
                            "artigo" to "Artigo de Revista / Peri\u00f3dico",
                            "site" to "Documento Eletr\u00f4nico (Site)"
                        ).forEach { (v, l) ->
                            DropdownMenuItem(text = { Text(l) }, onClick = { docType = v; expanded = false })
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetaField("Sobrenome", authorLastName, { authorLastName = it }, Modifier.weight(1f))
                    MetaField("Nome", authorFirstName, { authorFirstName = it }, Modifier.weight(1f))
                }
                MetaField("T\u00edtulo da Obra", title, { title = it })
                MetaField("Subt\u00edtulo", subtitle, { subtitle = it })

                when (docType) {
                    "livro" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetaField("Edi\u00e7\u00e3o", edition, { edition = it }, Modifier.weight(1f))
                            MetaField("Cidade", city, { city = it }, Modifier.weight(2f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetaField("Editora", publisher, { publisher = it }, Modifier.weight(2f))
                            MetaField("Ano", year, { year = it }, Modifier.weight(1f))
                        }
                    }
                    "artigo" -> {
                        MetaField("Revista / Peri\u00f3dico", journal, { journal = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetaField("Cidade", city, { city = it }, Modifier.weight(1f))
                            MetaField("Ano", year, { year = it }, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetaField("Vol / N\u00fam", volume, { volume = it }, Modifier.weight(1f))
                            MetaField("P\u00e1ginas", pages, { pages = it }, Modifier.weight(1f))
                        }
                    }
                    "site" -> {
                        MetaField("URL", url, { url = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetaField("Acesso em", accessDate, { accessDate = it }, Modifier.weight(2f))
                            MetaField("Ano", year, { year = it }, Modifier.weight(1f))
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                SectionLabel("Exportar Cita\u00e7\u00f5es (${highlights.size})")
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0F172A)).padding(14.dp)) {
                    Text(
                        text = if (highlights.isEmpty()) "// nenhuma cita\u00e7\u00e3o capturada ainda" else mdPreview,
                        fontSize = 11.sp, lineHeight = 17.sp, color = Color(0xFFCBD5E1), fontFamily = FontFamily.Monospace
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF0F172A)).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onSave(currentParams()); onDismiss() },
                    modifier = Modifier.fillMaxWidth().height(44.dp), colors = ButtonDefaults.buttonColors(containerColor = Indigo600), shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Salvar Metadados", fontWeight = FontWeight.Bold)
                }

                if (highlights.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportButton(label = ".PDF", containerColor = Color(0xFFDC2626), modifier = Modifier.weight(1f)) {
                            onSave(currentParams())
                            ExportUtil.exportPDF(context, buildCurrentDoc(), highlights)
                        }
                        ExportButton(label = ".TXT", containerColor = Color(0xFF059669), modifier = Modifier.weight(1f)) {
                            onSave(currentParams())
                            ExportUtil.exportTXT(context, buildCurrentDoc(), highlights)
                        }
                        ExportButton(label = ".MD", containerColor = Color(0xFF4F46E5), modifier = Modifier.weight(1f)) {
                            onSave(currentParams())
                            ExportUtil.exportMD(context, buildCurrentDoc(), highlights)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline, letterSpacing = 1.sp)
}

@Composable
private fun ExportButton(label: String, containerColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.height(40.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor.copy(alpha = 0.15f)), shape = RoundedCornerShape(8.dp)
    ) { Text(label, fontWeight = FontWeight.Black, fontSize = 12.sp, color = containerColor) }
}

@Composable
private fun metaFieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = Indigo600, focusedLabelColor = Indigo600, cursorColor = Indigo600)

@Composable
private fun MetaField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label, fontSize = 12.sp) },
        modifier = modifier, singleLine = true, colors = metaFieldColors()
    )
}
