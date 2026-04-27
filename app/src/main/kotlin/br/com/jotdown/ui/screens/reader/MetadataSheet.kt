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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
                        Text("Metadados & Fichamento", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White) }
                }

                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text("Refer\u00eancia ABNT", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                    
                    ExposedDropdownMenuBox(expanded = expandedType, onExpandedChange = { expandedType = !expandedType }, modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(value = docType, onValueChange = {}, readOnly = true, label = { Text("Tipo de Publica\u00e7\u00e3o") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedType) }, colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(), modifier = Modifier.menuAnchor().fillMaxWidth())
                        ExposedDropdownMenu(expanded = expandedType, onDismissRequest = { expandedType = false }) { typeOptions.forEach { selectionOption -> DropdownMenuItem(text = { Text(selectionOption) }, onClick = { docType = selectionOption; expandedType = false }) } }
                    }; Spacer(Modifier.height(16.dp))

                    // 🛡️ FORMULÁRIO CAMALEÃO: Muda de acordo com o tipo escolhido
                    when (docType) {
                        "Artigo de Peri\u00f3dico" -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = authorLastName, onValueChange = { authorLastName = it }, label = { Text("Sobrenome") }, modifier = Modifier.weight(1f)); OutlinedTextField(value = authorFirstName, onValueChange = { authorFirstName = it }, label = { Text("Nome") }, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("T\u00edtulo do Artigo") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = journal, onValueChange = { journal = it }, label = { Text("T\u00edtulo da Revista / Jornal") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("Local (Cidade)") }, modifier = Modifier.weight(1f)); OutlinedTextField(value = volume, onValueChange = { volume = it }, label = { Text("Volume (v.)") }, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = edition, onValueChange = { edition = it }, label = { Text("N\u00famero (n.)") }, modifier = Modifier.weight(1f)); OutlinedTextField(value = pages, onValueChange = { pages = it }, label = { Text("P\u00e1ginas (In\u00edcio-Fim)") }, modifier = Modifier.weight(1f)); OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Data / Ano") }, modifier = Modifier.weight(1f)) }
                        }
                        "Cap\u00edtulo de Livro" -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = authorLastName, onValueChange = { authorLastName = it }, label = { Text("Sobrenome (Autor do Cap.)") }, modifier = Modifier.weight(1f)); OutlinedTextField(value = authorFirstName, onValueChange = { authorFirstName = it }, label = { Text("Nome") }, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("T\u00edtulo do Cap\u00edtulo") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = subtitle, onValueChange = { subtitle = it }, label = { Text("Autor do Livro (Nome e Sobrenome)") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = journal, onValueChange = { journal = it }, label = { Text("T\u00edtulo do Livro") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = edition, onValueChange = { edition = it }, label = { Text("Edi\u00e7\u00e3o") }, modifier = Modifier.weight(1f)); OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("Local") }, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = publisher, onValueChange = { publisher = it }, label = { Text("Editora") }, modifier = Modifier.weight(1f)); OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Ano") }, modifier = Modifier.weight(1f)); OutlinedTextField(value = pages, onValueChange = { pages = it }, label = { Text("Pagina\u00e7\u00e3o") }, modifier = Modifier.weight(1f)) }
                        }
                        "Trabalho Acad\u00eamico" -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = authorLastName, onValueChange = { authorLastName = it }, label = { Text("Sobrenome") }, modifier = Modifier.weight(1f)); OutlinedTextField(value = authorFirstName, onValueChange = { authorFirstName = it }, label = { Text("Nome") }, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("T\u00edtulo do Trabalho") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = journal, onValueChange = { journal = it }, label = { Text("Grau / Curso (ex: Disserta\u00e7\u00e3o)") }, modifier = Modifier.weight(1f)); OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Ano Dep\u00f3sito") }, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = publisher, onValueChange = { publisher = it }, label = { Text("Institui\u00e7\u00e3o") }, modifier = Modifier.weight(1f)); OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("Local (Cidade)") }, modifier = Modifier.weight(1f)) }
                        }
                        "Documento Jur\u00eddico" -> {
                            OutlinedTextField(value = authorLastName, onValueChange = { authorLastName = it }, label = { Text("Jurisdi\u00e7\u00e3o (ex: BRASIL)") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("T\u00edtulo / Lei / Ementa") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = edition, onValueChange = { edition = it }, label = { Text("N\u00famero") }, modifier = Modifier.weight(1f)); OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Data (Ano)") }, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = publisher, onValueChange = { publisher = it }, label = { Text("Dados Publica\u00e7\u00e3o (ex: DOU)") }, modifier = Modifier.weight(1f)); OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("Local") }, modifier = Modifier.weight(1f)) }
                        }
                        else -> { // Livro / Monografia
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = authorLastName, onValueChange = { authorLastName = it }, label = { Text("Sobrenome") }, modifier = Modifier.weight(1f)); OutlinedTextField(value = authorFirstName, onValueChange = { authorFirstName = it }, label = { Text("Nome") }, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("T\u00edtulo da Obra") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                            OutlinedTextField(value = subtitle, onValueChange = { subtitle = it }, label = { Text("Subt\u00edtulo") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = edition, onValueChange = { edition = it }, label = { Text("Edi\u00e7\u00e3o") }, modifier = Modifier.weight(1f)); OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("Cidade") }, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = publisher, onValueChange = { publisher = it }, label = { Text("Editora") }, modifier = Modifier.weight(1f)); OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("Ano") }, modifier = Modifier.weight(1f)) }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Acesso Online (Opcional)", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("Dispon\u00edvel em (URL)") }, modifier = Modifier.weight(1.5f)); OutlinedTextField(value = accessDate, onValueChange = { accessDate = it }, label = { Text("Acesso em") }, modifier = Modifier.weight(1f)) }
                    
                    Spacer(Modifier.height(24.dp))
                    if (highlights.isNotEmpty()) {
                        Text("Editar Cita\u00e7\u00f5es Capturadas (${highlights.size})", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                        highlights.forEachIndexed { index, h -> OutlinedTextField(value = editableHighlights[h.id] ?: "", onValueChange = { editableHighlights[h.id] = it }, label = { Text("Cita\u00e7\u00e3o [${index + 1}] - P\u00e1gina ${h.page + pageOffset} (PDF: ${h.page})") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), maxLines = 5, trailingIcon = { IconButton(onClick = { onDeleteHighlight(h.id) }) { Icon(Icons.Default.Close, contentDescription = "Excluir", tint = Color.Red.copy(alpha = 0.8f)) } }) }; Spacer(Modifier.height(16.dp))
                    }

                    Text("Preview do Fichamento (Markdown)", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFF0F172A), RoundedCornerShape(8.dp)).padding(16.dp)) { Text(markdownPreview, color = Color(0xFFE2E8F0), fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                }

                Column(modifier = Modifier.background(Color(0xFF1E1B4B)).padding(16.dp)) {
                    Button(onClick = { highlights.forEach { h -> val newText = editableHighlights[h.id]; if (newText != null && newText != h.text) { onUpdateHighlight(h.id, newText) } }; onSave(MetadataParams(docType, authorLastName, authorFirstName, title, subtitle, edition, city, publisher, year, journal, volume, pages, url, accessDate)); onDismiss() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))) { Text("Salvar Metadados e Cita\u00e7\u00f5es") }
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
        context.startActivity(Intent.createChooser(intent, "Exportar Fichamento"))
    } catch (e: Exception) { e.printStackTrace() }
}