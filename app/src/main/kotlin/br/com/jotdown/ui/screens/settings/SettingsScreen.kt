package br.com.jotdown.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import br.com.jotdown.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val signedInEmail by viewModel.signedInEmail.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val context = LocalContext.current

    val driveFolderName by viewModel.driveFolderName.collectAsState()
    val driveFolderConnecting by viewModel.driveFolderConnecting.collectAsState()
    // Input for the Drive folder name — kept at top level so the launcher closure can read it
    var driveFolderInput by remember { mutableStateOf("") }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleSignInResult(result.data)
    }

    // Launcher specifically for Drive Library additional permission
    val driveLibraryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleDriveLibrarySignIn(result.data, driveFolderInput)
    }

    LaunchedEffect(Unit) {
        viewModel.checkSignInStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configurações e Nuvem") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Google Drive", style = MaterialTheme.typography.titleLarge)

            if (signedInEmail == null) {
                Text("Faça login para sincronizar seus dados.")
                Button(onClick = {
                    val intent = viewModel.getSignInIntent()
                    if (intent != null) {
                        signInLauncher.launch(intent)
                    } else {
                        // Exibe mensagem caso seja a versão FOSS
                        viewModel.setSyncMessage("Sincronização via Google Drive não está disponível na versão FOSS.")
                    }
                }) {
                    Text("Conectar com o Google")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Conectado como:\n$signedInEmail", style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(onClick = { viewModel.signOut() }) {
                    Text("Desconectar")
                }

                HorizontalDivider()

                Text("Backup Automático", style = MaterialTheme.typography.titleMedium)
                Text("Escolha a frequência para realizar backup em segundo plano.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)

                var expanded by remember { mutableStateOf(false) }
                var selectedFrequency by remember { mutableStateOf("1 Hora") }
                
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text("Frequência: $selectedFrequency")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("1 Hora" to 1L, "6 Horas" to 6L, "12 Horas" to 12L, "24 Horas" to 24L).forEach { (label, hours) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    selectedFrequency = label
                                    expanded = false
                                    viewModel.schedulePeriodicBackup(hours)
                                }
                            )
                        }
                    }
                }

                HorizontalDivider()

                Text("Ações Manuais", style = MaterialTheme.typography.titleMedium)
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.backupNow() },
                        enabled = !isSyncing
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Forçar Backup Agora")
                    }

                    Button(
                        onClick = { viewModel.restoreNow() },
                        enabled = !isSyncing,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Baixar e Restaurar")
                    }
                }

                if (isSyncing) {
                    CircularProgressIndicator()
                }
            }

            if (syncMessage != null) {
                Text(syncMessage ?: "", color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider()

            // ── Drive Library ────────────────────────────────────────────────
            Text("Biblioteca do Drive", style = MaterialTheme.typography.titleMedium)
            Text(
                "Conecte uma pasta do seu Google Drive para acessar e baixar PDFs diretamente no app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )

            if (signedInEmail == null) {
                Text(
                    "Faça login no Google (seção acima) para usar a Biblioteca do Drive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else if (driveFolderName != null) {
                // Connected state
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Conectado a: ", style = MaterialTheme.typography.bodyMedium)
                    Text(driveFolderName ?: "", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var showChangePicker by remember { mutableStateOf(false) }
                    if (showChangePicker) {
                        OutlinedTextField(
                            value = driveFolderInput,
                            onValueChange = { driveFolderInput = it },
                            label = { Text("Nome da nova pasta") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (driveFolderInput.isNotBlank()) {
                                    if (!viewModel.hasDriveReadAccess()) {
                                        viewModel.getDriveLibraryIntent()?.let { intent ->
                                            driveLibraryLauncher.launch(intent)
                                        } ?: viewModel.setSyncMessage("Erro ao iniciar login do Google")
                                    } else {
                                        viewModel.connectDriveFolder(driveFolderInput)
                                        showChangePicker = false
                                    }
                                }
                            },
                            enabled = driveFolderInput.isNotBlank() && !driveFolderConnecting
                        ) { Text("Buscar") }
                    } else {
                        OutlinedButton(onClick = { showChangePicker = true; driveFolderInput = "" }) {
                            Text("Mudar Pasta")
                        }
                        OutlinedButton(onClick = { viewModel.disconnectDriveFolder() }) {
                            Text("Desconectar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (driveFolderConnecting) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
            } else {
                // Not connected — show input to connect
                OutlinedTextField(
                    value = driveFolderInput,
                    onValueChange = { driveFolderInput = it },
                    label = { Text("Nome exato da pasta no Drive") },
                    placeholder = { Text("ex: Minha Biblioteca Acadêmica") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) }
                )
                Button(
                    onClick = {
                        if (driveFolderInput.isNotBlank()) {
                            if (!viewModel.hasDriveReadAccess()) {
                                // Request additional scope — after grant, handleDriveLibrarySignIn will call connectDriveFolder
                                viewModel.getDriveLibraryIntent()?.let { intent ->
                                    driveLibraryLauncher.launch(intent)
                                } ?: viewModel.setSyncMessage("Erro ao iniciar login do Google")
                            } else {
                                viewModel.connectDriveFolder(driveFolderInput)
                            }
                        }
                    },
                    enabled = driveFolderInput.isNotBlank() && !driveFolderConnecting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (driveFolderConnecting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Icon(Icons.Default.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Conectar Pasta")
                }
            }

            HorizontalDivider()

            Text("Gerenciar Dicionários Offline", style = MaterialTheme.typography.titleMedium)
            Text("Baixe ou remova dicionários para consulta rápida.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            
            Spacer(Modifier.height(8.dp))
            val isPtDownloaded by viewModel.isPtDictDownloaded.collectAsState()
            val isEnDownloaded by viewModel.isEnDictDownloaded.collectAsState()
            val dictProgress by viewModel.dictDownloadProgress.collectAsState()

            DictionaryItemRow(
                langName = "Português",
                langCode = "pt",
                isDownloaded = isPtDownloaded,
                progress = dictProgress["pt"],
                onDownload = { viewModel.downloadDictionary("pt") },
                onDelete = { viewModel.deleteDictionary("pt") }
            )
            
            DictionaryItemRow(
                langName = "Inglês",
                langCode = "en",
                isDownloaded = isEnDownloaded,
                progress = dictProgress["en"],
                onDownload = { viewModel.downloadDictionary("en") },
                onDelete = { viewModel.deleteDictionary("en") }
            )
        }
    }
}

@Composable
fun DictionaryItemRow(
    langName: String,
    langCode: String,
    isDownloaded: Boolean,
    progress: Int?,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(langName, style = MaterialTheme.typography.bodyLarge)
            if (progress != null) {
                LinearProgressIndicator(progress = progress / 100f, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                Text("$progress%", style = MaterialTheme.typography.bodySmall)
            } else if (isDownloaded) {
                Text("Baixado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            } else {
                Text("Não baixado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        
        if (progress == null) {
            if (isDownloaded) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Remover Dicionário", tint = MaterialTheme.colorScheme.error)
                }
            } else {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = "Baixar Dicionário", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
