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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import br.com.jotdown.R
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
    val pickerState by viewModel.pickerState.collectAsState()

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleSignInResult(result.data)
    }

    // Launcher for Drive Library additional permission — opens picker automatically after grant
    val driveLibraryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleDriveLibrarySignIn(result.data, "")
        viewModel.openFolderPicker()
    }

    LaunchedEffect(Unit) {
        viewModel.checkSignInStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drawer_settings_cloud)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
                Text(stringResource(R.string.settings_login_prompt))
                Button(onClick = {
                    val intent = viewModel.getSignInIntent()
                    if (intent != null) {
                        signInLauncher.launch(intent)
                    } else {
                        // Exibe mensagem caso seja a versão FOSS
                        viewModel.setSyncMessage(context.getString(R.string.err_foss_sync))
                    }
                }) {
                    Text(stringResource(R.string.settings_connect_google))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_connected_as, signedInEmail ?: ""), style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(onClick = { viewModel.signOut() }) {
                    Text(stringResource(R.string.settings_disconnect))
                }

                HorizontalDivider()

                Text(stringResource(R.string.settings_auto_backup), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.settings_auto_backup_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)

                var expanded by remember { mutableStateOf(false) }
                val defaultFrequency = stringResource(R.string.freq_1h)
                var selectedFrequency by remember { mutableStateOf(defaultFrequency) }

                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(stringResource(R.string.settings_frequency, selectedFrequency))
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf(
                            stringResource(R.string.freq_1h) to 1L,
                            stringResource(R.string.freq_6h) to 6L,
                            stringResource(R.string.freq_12h) to 12L,
                            stringResource(R.string.freq_24h) to 24L
                        ).forEach { (label, hours) ->
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

                Text(stringResource(R.string.settings_manual_actions), style = MaterialTheme.typography.titleMedium)
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.backupNow() },
                        enabled = !isSyncing
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_backup_now))
                    }

                    Button(
                        onClick = { viewModel.restoreNow() },
                        enabled = !isSyncing,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_restore))
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
            Text(stringResource(R.string.settings_drive_library), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_drive_library_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )

            if (signedInEmail == null) {
                Text(
                    stringResource(R.string.settings_drive_library_login_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else if (driveFolderName != null) {
                // Connected state
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.settings_connected_to), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        driveFolderName ?: "",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (viewModel.hasDriveReadAccess()) {
                                viewModel.openFolderPicker()
                            } else {
                                viewModel.getDriveLibraryIntent()?.let { driveLibraryLauncher.launch(it) }
                                    ?: viewModel.setSyncMessage(context.getString(R.string.err_google_login))
                            }
                        }
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_change_folder))
                    }
                    OutlinedButton(onClick = { viewModel.disconnectDriveFolder() }) {
                        Text(stringResource(R.string.settings_disconnect), color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                // Not connected — show Browse button
                Button(
                    onClick = {
                        if (viewModel.hasDriveReadAccess()) {
                            viewModel.openFolderPicker()
                        } else {
                            // Request DRIVE_READONLY, then open picker after grant
                            viewModel.getDriveLibraryIntent()?.let { driveLibraryLauncher.launch(it) }
                                ?: viewModel.setSyncMessage(context.getString(R.string.err_google_login))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_browse_folder))
                }
            }

            HorizontalDivider()

            Text(stringResource(R.string.settings_dictionaries), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_dictionaries_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            
            Spacer(Modifier.height(8.dp))
            val isPtDownloaded by viewModel.isPtDictDownloaded.collectAsState()
            val isEnDownloaded by viewModel.isEnDictDownloaded.collectAsState()
            val dictProgress by viewModel.dictDownloadProgress.collectAsState()

            DictionaryItemRow(
                langName = stringResource(R.string.lang_portuguese),
                langCode = "pt",
                isDownloaded = isPtDownloaded,
                progress = dictProgress["pt"],
                onDownload = { viewModel.downloadDictionary("pt") },
                onDelete = { viewModel.deleteDictionary("pt") }
            )
            
            DictionaryItemRow(
                langName = stringResource(R.string.lang_english),
                langCode = "en",
                isDownloaded = isEnDownloaded,
                progress = dictProgress["en"],
                onDownload = { viewModel.downloadDictionary("en") },
                onDelete = { viewModel.deleteDictionary("en") }
            )
        }
    }

    // ── Drive folder picker bottom sheet ──────────────────────────────────────
    if (pickerState.isOpen) {
        DriveFolderPickerBottomSheet(
            state = pickerState,
            onDismiss = { viewModel.closeFolderPicker() },
            onNavigateInto = { folder -> viewModel.navigateIntoFolder(folder) },
            onBreadcrumbTap = { index -> viewModel.navigateToBreadcrumb(index) },
            onSelectCurrent = { viewModel.selectCurrentPickerFolder() }
        )
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
                Text(stringResource(R.string.drive_downloaded), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            } else {
                Text(stringResource(R.string.dict_not_downloaded), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        
        if (progress == null) {
            if (isDownloaded) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.dict_remove), tint = MaterialTheme.colorScheme.error)
                }
            } else {
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = stringResource(R.string.dict_download), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
