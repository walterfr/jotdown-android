package br.com.jotdown.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import br.com.jotdown.R
import br.com.jotdown.ui.viewmodel.SettingsViewModel
import br.com.jotdown.util.WebDavSyncUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val signedInEmail by viewModel.signedInEmail.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val isPro by viewModel.isPro.collectAsState()
    val proPrice by viewModel.proPrice.collectAsState()
    val context = LocalContext.current
    val activity = context as? android.app.Activity

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
            if (!isPro) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.WorkspacePremium, contentDescription = null)
                            Text(stringResource(R.string.pro_unlock_title), style = MaterialTheme.typography.titleLarge)
                        }
                        Text(stringResource(R.string.pro_unlock_desc), style = MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = { activity?.let { viewModel.launchPurchase(it) } },
                            enabled = activity != null
                        ) {
                            Text(
                                if (proPrice != null) stringResource(R.string.pro_unlock_button, proPrice!!)
                                else stringResource(R.string.pro_unlock_button_loading)
                            )
                        }
                    }
                }
            } else {
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
            } // isPro

            // ── Open FOSS WebDAV Sync (available in both foss and full) ────────
            WebDavSyncSection(viewModel = viewModel)

            HorizontalDivider()

            Text(stringResource(R.string.settings_drawing), style = MaterialTheme.typography.titleLarge)
            val drawWithFinger by viewModel.drawWithFinger.collectAsState()
            val doubleTapToDeselect by viewModel.doubleTapToDeselect.collectAsState()
            val twoFingerScroll by viewModel.twoFingerScroll.collectAsState()
            val twoThreeFingerUndo by viewModel.twoThreeFingerUndo.collectAsState()
            val holdToShape by viewModel.holdToShape.collectAsState()
            val stylusButtonAction by viewModel.stylusButtonAction.collectAsState()
            DrawingToggleRow(
                titleRes = R.string.settings_draw_with_finger,
                descRes = R.string.settings_draw_with_finger_desc,
                checked = drawWithFinger,
                onCheckedChange = { viewModel.setDrawWithFinger(it) }
            )
            DrawingToggleRow(
                titleRes = R.string.settings_double_tap_to_deselect,
                descRes = R.string.settings_double_tap_to_deselect_desc,
                checked = doubleTapToDeselect,
                enabled = drawWithFinger,
                onCheckedChange = { viewModel.setDoubleTapToDeselect(it) }
            )
            DrawingToggleRow(
                titleRes = R.string.settings_two_finger_scroll,
                descRes = R.string.settings_two_finger_scroll_desc,
                checked = twoFingerScroll,
                enabled = drawWithFinger,
                onCheckedChange = { viewModel.setTwoFingerScroll(it) }
            )
            DrawingToggleRow(
                titleRes = R.string.settings_two_three_finger_undo,
                descRes = R.string.settings_two_three_finger_undo_desc,
                checked = twoThreeFingerUndo,
                onCheckedChange = { viewModel.setTwoThreeFingerUndo(it) }
            )
            DrawingToggleRow(
                titleRes = R.string.settings_hold_to_shape,
                descRes = R.string.settings_hold_to_shape_desc,
                checked = holdToShape,
                onCheckedChange = { viewModel.setHoldToShape(it) }
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(stringResource(R.string.settings_stylus_button_action), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.settings_stylus_button_action_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "ERASER" to R.string.settings_stylus_action_eraser,
                        "UNDO" to R.string.settings_stylus_action_undo,
                        "NONE" to R.string.settings_stylus_action_none
                    ).forEach { (value, labelRes) ->
                        FilterChip(
                            selected = stylusButtonAction == value,
                            onClick = { viewModel.setStylusButtonAction(value) },
                            label = { Text(stringResource(labelRes)) }
                        )
                    }
                }
            }
            HorizontalDivider()

            if (viewModel.isBillingSupported) {
                Text(stringResource(R.string.settings_reader_tabs), style = MaterialTheme.typography.titleLarge)
                val tabsInFullscreen by viewModel.tabsInFullscreen.collectAsState()
                DrawingToggleRow(
                    titleRes = R.string.settings_tabs_in_fullscreen,
                    descRes = R.string.settings_tabs_in_fullscreen_desc,
                    checked = tabsInFullscreen,
                    onCheckedChange = { viewModel.setTabsInFullscreen(it) }
                )
                HorizontalDivider()
            }

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
fun DrawingToggleRow(
    titleRes: Int,
    descRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(descRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
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

@Composable
private fun WebDavSyncSection(viewModel: SettingsViewModel) {
    val webdavUrl by viewModel.webdavUrl.collectAsState()
    val webdavFilename by viewModel.webdavFilename.collectAsState()
    val webdavUsername by viewModel.webdavUsername.collectAsState()
    val webdavPassword by viewModel.webdavPassword.collectAsState()
    val webdavBusy by viewModel.webdavBusy.collectAsState()
    val webdavStatus by viewModel.webdavStatus.collectAsState()

    var showPassword by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    HorizontalDivider()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.settings_webdav_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(
                stringResource(R.string.settings_webdav_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = webdavUrl,
                onValueChange = { viewModel.updateWebdavConfig(url = it) },
                label = { Text(stringResource(R.string.settings_webdav_url_label)) },
                placeholder = { Text(stringResource(R.string.settings_webdav_url_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = webdavFilename,
                onValueChange = { viewModel.updateWebdavConfig(filename = it) },
                label = { Text(stringResource(R.string.settings_webdav_filename_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = webdavUsername,
                onValueChange = { viewModel.updateWebdavConfig(username = it) },
                label = { Text(stringResource(R.string.settings_webdav_user_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = webdavPassword,
                onValueChange = { viewModel.updateWebdavConfig(password = it) },
                label = { Text(stringResource(R.string.settings_webdav_pass_label)) },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = stringResource(
                                if (showPassword) R.string.settings_webdav_hide_pass else R.string.settings_webdav_show_pass
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (WebDavSyncUtil.isPlainHttp(webdavUrl)) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.settings_webdav_http_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            val canRunWebdav = !webdavBusy && webdavUrl.isNotBlank() && !WebDavSyncUtil.isPlainHttp(webdavUrl)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.testWebdavConnection() },
                    enabled = canRunWebdav
                ) {
                    Text(stringResource(R.string.settings_webdav_test))
                }
                Button(
                    onClick = { viewModel.uploadWebdavBackup() },
                    enabled = canRunWebdav
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_webdav_upload))
                }
                Button(
                    onClick = { showRestoreConfirm = true },
                    enabled = canRunWebdav,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_webdav_restore))
                }
            }

            if (webdavBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (!webdavStatus.isNullOrBlank()) {
                Text(
                    text = webdavStatus ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text(stringResource(R.string.settings_webdav_restore)) },
            text = { Text(stringResource(R.string.dlg_import_backup_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirm = false
                        viewModel.restoreWebdavBackup()
                    }
                ) {
                    Text(
                        stringResource(R.string.dlg_import_confirm),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
