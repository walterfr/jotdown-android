package br.com.jotdown.ui.screens.reader

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.jotdown.R
import br.com.jotdown.data.entity.DocumentEntity
import br.com.jotdown.data.entity.HighlightEntity
import java.io.File

data class MetadataParams(val docType: String, val authorLastName: String, val authorFirstName: String, val title: String, val subtitle: String, val edition: String, val city: String, val publisher: String, val year: String, val journal: String, val volume: String, val pages: String, val url: String, val accessDate: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataSheet(
    document: DocumentEntity?, highlights: List<HighlightEntity>, pageOffset: Int,
    onSave: (MetadataParams) -> Unit, onUpdateHighlight: (Long, String) -> Unit, onDeleteHighlight: (Long) -> Unit, onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var docType by remember { mutableStateOf(document?.docType?.takeIf { it.isNotBlank() } ?: "Livro / Monografia") }
    var authorLastName by remember { mutableStateOf(document?.authorLastName ?: "") }
    var authorFirstName by remember { mutableStateOf(document?.authorFirstName ?: "") }
    var title by remember { mutableStateOf(document?.title ?: "") }
    var subtitle by remember { mutableStateOf(document?.subtitle ?: "") }
    var edition by remember { mutableStateOf(document?.edition ?: "") }
    var city by remember { mutableStateOf(document?.city ?: "") }
    var publisher by remember { mutableStateOf(document?.publisher ?: "") }
    var year by remember { mutableStateOf(document?.year ?: "") }
    var journal by remember { mutableStateOf(document?.journal ?: "") }
    var volume by remember { mutableStateOf(document?.volume ?: "") }
    var pages by remember { mutableStateOf(document?.pages ?: "") }
    var url by remember { mutableStateOf(document?.url ?: "") }
    var accessDate by remember { mutableStateOf(document?.accessDate ?: "") }

    var expandedType by remember { mutableStateOf(false) }
    val typeOptions = listOf("Livro / Monografia", "Cap\u00edtulo de Livro", "Artigo de Peri\u00f3dico", "Trabalho Acad\u00eamico", "Documento Jur\u00eddico")
    val editableHighlights = remember(highlights) { mutableStateMapOf<Long, String>().apply { highlights.forEach { put(it.id, it.text) } } }

    // 🛡️ MOTOR ABNT: Gera a Referência Principal e aplica o Negrito no lugar certo
    val markdownPreview = buildString {
        val ref = when (docType) {
            "Artigo de Peri\u00f3dico" -> "${authorLastName.uppercase()}, $authorFirstName. $title. **$journal**, $city, v. $volume, n. $edition, p. $pages, $year."
            "Cap\u00edtulo de Livro" -> "${authorLastName.uppercase()}, $authorFirstName. $title. In: ${subtitle.uppercase()}. **$journal**. $city: $publisher, $year. p. $pages."
            "Trabalho Acad\u00eamico" -> "${authorLastName.uppercase()}, $authorFirstName. **$title**. $year. $journal - $publisher, $city, $year."
            "Documento Jur\u00eddico" -> "${authorLastName.uppercase()}. **$title**, $year. $publisher, $city."
            else -> "${authorLastName.uppercase()}, $authorFirstName. **$title**" + (if (subtitle.isNotBlank()) ": $subtitle" else "") + ". " + (if (edition.isNotBlank()) "$edition ed. " else "") + "$city: $publisher, $year."
        }
        val onlineAddon = if (url.isNotBlank()) " Dispon\u00edvel em: $url. Acesso em: $accessDate." else ""
        
        append("## Fichamento\n\n**Refer\u00eancia:** $ref$onlineAddon\n\n## Destaques\n")
        
        val citationName = if (docType == "Documento Jur\u00eddico") authorLastName.uppercase() else authorLastName
        val citationYear = if (year.isNotBlank()) year else "S.d."

        highlights.forEachIndexed { index, h ->
            val text = editableHighlights[h.id] ?: h.text
            // 🛡️ PADRÃO DE CITAÇÃO EXATO: (Sobrenome, Ano, p. X)
            append("[${index + 1}] > $text ($citationName, $citationYear, p. ${h.page + pageOffset})\n\n")
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.95f), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1B4B)).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)); Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.meta_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close), tint = Color.White) }
                }

                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text(stringResource(R.string.meta_abnt_reference), color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))

                    ExposedDropdownMenuBox(expanded = expandedType, onExpandedChange = { expandedType = !expandedType }, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = stringResource(docTypeLabelRes(docType)), onValueChange = {}, readOnly = true, label = { Text(stringResource(R.string.meta_publication_type)) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) }, colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(), modifier = Modifier.menuAnchor().fillMaxWidth())
                        ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) { typeOptions.forEach { selectionOption -> DropdownMenuItem(text = { Text(stringResource(docTypeLabelRes(selectionOption))) }, onClick = { docType = selectionOption; expandedType = false }) } }
                    }; Spacer(Modifier.height(16.dp))

                    // 🛡️ FORMULÁRIO CAMALEÃO: Muda de acordo com o tipo escolhido
                    when (docType) {
                        "Artigo de Peri\u00f3dico" -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = authorLastName, onValueChange = { authorLastName = it }, label = { Text(stringResource(R.string.meta_last_name)) }, modifier = Modifier.weight(1f)); OutlinedTextField(value = authorFirstName, onValueChange = { authorFirstName = it }, label = { Text(stringResource(R.string.meta_first_name)) }, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.meta_article_title)) }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = journal, onValueChange = { journal = it }, label = { Text(stringResource(R.string.meta_journal_title)) }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text(stringResource(R.string.meta_city)) }, modifier = Modifier.weight(1f)); OutlinedTextField(value = volume, onValueChange = { volume = it }, label = { Text(stringResource(R.string.meta_volume)) }, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = edition, onValueChange = { edition = it }, label = { Text(stringResource(R.string.meta_number)) }, modifier = Modifier.weight(1f)); OutlinedTextField(value = pages, onValueChange = { pages = it }, label = { Text(stringResource(R.string.meta_pages_range)) }, modifier = Modifier.weight(1f)); OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text(stringResource(R.string.meta_date_year)) }, modifier = Modifier.weight(1f)) }
                        }
                        "Cap\u00edtulo de Livro" -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = authorLastName, onValueChange = { authorLastName = it }, label = { Text(stringResource(R.string.meta_last_name_chapter)) }, modifier = Modifier.weight(1f)); OutlinedTextField(value = authorFirstName, onValueChange = { authorFirstName = it }, label = { Text(stringResource(R.string.meta_first_name)) }, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.meta_chapter_title)) }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = subtitle, onValueChange = { subtitle = it }, label = { Text(stringResource(R.string.meta_book_author)) }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = journal, onValueChange = { journal = it }, label = { Text(stringResource(R.string.meta_book_title)) }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = edition, onValueChange = { edition = it }, label = { Text(stringResource(R.string.meta_edition)) }, modifier = Modifier.weight(1f)); OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text(stringResource(R.string.meta_location)) }, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = publisher, onValueChange = { publisher = it }, label = { Text(stringResource(R.string.meta_publisher)) }, modifier = Modifier.weight(1f)); OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text(stringResource(R.string.meta_year)) }, modifier = Modifier.weight(1f)); OutlinedTextField(value = pages, onValueChange = { pages = it }, label = { Text(stringResource(R.string.meta_pagination)) }, modifier = Modifier.weight(1f)) }
                        }
                        "Trabalho Acad\u00eamico" -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = authorLastName, onValueChange = { authorLastName = it }, label = { Text(stringResource(R.string.meta_last_name)) }, modifier = Modifier.weight(1f)); OutlinedTextField(value = authorFirstName, onValueChange = { authorFirstName = it }, label = { Text(stringResource(R.string.meta_first_name)) }, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.meta_work_title)) }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = journal, onValueChange = { journal = it }, label = { Text(stringResource(R.string.meta_degree_course)) }, modifier = Modifier.weight(1f)); OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text(stringResource(R.string.meta_deposit_year)) }, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = publisher, onValueChange = { publisher = it }, label = { Text(stringResource(R.string.meta_institution)) }, modifier = Modifier.weight(1f)); OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text(stringResource(R.string.meta_city)) }, modifier = Modifier.weight(1f)) }
                        }
                        "Documento Jur\u00eddico" -> {
                            OutlinedTextField(value = authorLastName, onValueChange = { authorLastName = it }, label = { Text(stringResource(R.string.meta_jurisdiction)) }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.meta_law_title)) }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = edition, onValueChange = { edition = it }, label = { Text(stringResource(R.string.meta_number)) }, modifier = Modifier.weight(1f)); OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text(stringResource(R.string.meta_date)) }, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = publisher, onValueChange = { publisher = it }, label = { Text(stringResource(R.string.meta_publication_data)) }, modifier = Modifier.weight(1f)); OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text(stringResource(R.string.meta_location)) }, modifier = Modifier.weight(1f)) }
                        }
                        else -> { // Livro / Monografia
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = authorLastName, onValueChange = { authorLastName = it }, label = { Text(stringResource(R.string.meta_last_name)) }, modifier = Modifier.weight(1f)); OutlinedTextField(value = authorFirstName, onValueChange = { authorFirstName = it }, label = { Text(stringResource(R.string.meta_first_name)) }, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.meta_opus_title)) }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = subtitle, onValueChange = { subtitle = it }, label = { Text(stringResource(R.string.meta_subtitle)) }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = edition, onValueChange = { edition = it }, label = { Text(stringResource(R.string.meta_edition)) }, modifier = Modifier.weight(1f)); OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text(stringResource(R.string.meta_city)) }, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = publisher, onValueChange = { publisher = it }, label = { Text(stringResource(R.string.meta_publisher)) }, modifier = Modifier.weight(1f)); OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text(stringResource(R.string.meta_year)) }, modifier = Modifier.weight(1f)) }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.meta_online_access), color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(stringResource(R.string.meta_available_at)) }, modifier = Modifier.weight(1.5f)); OutlinedTextField(value = accessDate, onValueChange = { accessDate = it }, label = { Text(stringResource(R.string.meta_accessed_on)) }, modifier = Modifier.weight(1f)) }

                    Spacer(Modifier.height(24.dp))
                    if (highlights.isNotEmpty()) {
                        Text(stringResource(R.string.meta_edit_quotes, highlights.size), color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                        highlights.forEachIndexed { index, h -> OutlinedTextField(value = editableHighlights[h.id] ?: "", onValueChange = { editableHighlights[h.id] = it }, label = { Text(stringResource(R.string.meta_quote_label, index + 1, h.page + pageOffset, h.page)) }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), maxLines = 5, trailingIcon = { IconButton(onClick = { onDeleteHighlight(h.id) }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.meta_delete), tint = Color.Red.copy(alpha = 0.8f)) } }) }; Spacer(Modifier.height(16.dp))
                    }

                    Text(stringResource(R.string.meta_preview), color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF0F172A), RoundedCornerShape(8.dp)).padding(16.dp)) { Text(markdownPreview, color = Color(0xFFE2E8F0), fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                }

                Column(modifier = Modifier.background(Color(0xFF1E1B4B)).padding(16.dp)) {
                    Button(onClick = { highlights.forEach { h -> val newText = editableHighlights[h.id]; if (newText != null && newText != h.text) { onUpdateHighlight(h.id, newText) } }; onSave(MetadataParams(docType, authorLastName, authorFirstName, title, subtitle, edition, city, publisher, year, journal, volume, pages, url, accessDate)); onDismiss() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))) { Text(stringResource(R.string.meta_save)) }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { Button(onClick = { exportFile(context, title, markdownPreview, "pdf") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF450a0a))) { Text(".PDF") }; Button(onClick = { exportFile(context, title, markdownPreview, "txt") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF064E3B))) { Text(".TXT") }; Button(onClick = { exportFile(context, title, markdownPreview, "md") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1B4B))) { Text(".MD") } }
                }
            }
        }
    }
}

fun exportFile(context: Context, title: String, content: String, extension: String) {
    try {
        val fileName = (if (title.isBlank()) "fichamento" else title.replace(" ", "_")) + ".$extension"
        val file = File(context.cacheDir, fileName)
        file.writeText(content)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply { type = if (extension == "md" || extension == "txt") "text/plain" else "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.meta_export_chooser)))
    } catch (e: Exception) { e.printStackTrace() }
}

/**
 * Mapeia a chave interna (estável, persistida em PT) do tipo de documento
 * para o recurso de string localizado exibido ao usuário.
 */
@androidx.annotation.StringRes
private fun docTypeLabelRes(docType: String): Int = when (docType) {
    "Capítulo de Livro"   -> R.string.doctype_chapter
    "Artigo de Periódico" -> R.string.doctype_article
    "Trabalho Acadêmico"  -> R.string.doctype_thesis
    "Documento Jurídico"  -> R.string.doctype_legal
    else                       -> R.string.doctype_book
}
