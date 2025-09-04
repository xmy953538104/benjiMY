package org.kenjinx.android.views

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.DocumentsContract
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Panorama
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Label
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.extension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.thread
import org.kenjinx.android.MainActivity
import org.kenjinx.android.providers.DocumentProvider
import org.kenjinx.android.viewmodels.DataImportState
import org.kenjinx.android.viewmodels.FirmwareInstallState
import org.kenjinx.android.viewmodels.KeyInstallState
import org.kenjinx.android.viewmodels.MainViewModel
import org.kenjinx.android.viewmodels.MemoryConfiguration
import org.kenjinx.android.viewmodels.SettingsViewModel
import org.kenjinx.android.viewmodels.MemoryManagerMode
import org.kenjinx.android.viewmodels.VSyncMode
import org.kenjinx.android.widgets.ActionButton
import org.kenjinx.android.widgets.DropdownSelector
import org.kenjinx.android.widgets.ExpandableView
import org.kenjinx.android.widgets.SimpleAlertDialog
import org.kenjinx.android.widgets.SwitchSelector

class SettingViews {
    companion object {
        const val EXPANSTION_TRANSITION_DURATION = 450
        const val IMPORT_CODE = 12341

        @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
        @Composable
        fun Main(settingsViewModel: SettingsViewModel, mainViewModel: MainViewModel) {
            val loaded = remember { mutableStateOf(false) }

            // — States mit expliziten Typen —
            val memoryManagerMode = remember { mutableStateOf(MemoryManagerMode.HostMappedUnsafe) }
            val useNce = remember { mutableStateOf(false) }
            val memoryConfiguration = remember { mutableStateOf(MemoryConfiguration.MemoryConfiguration4GiB) }
            val vSyncMode = remember { mutableStateOf(VSyncMode.Switch) }
            val enableDocked = remember { mutableStateOf(false) }
            val enablePptc = remember { mutableStateOf(false) }
            val enableFsIntegrityChecks = remember { mutableStateOf(false) }
            val fsGlobalAccessLogMode = remember { mutableStateOf(0) }
            val ignoreMissingServices = remember { mutableStateOf(false) }
            val enableShaderCache = remember { mutableStateOf(false) }
            val enableTextureRecompression = remember { mutableStateOf(false) }
            val enableMacroHLE = remember { mutableStateOf(false) }
            val resScale = remember { mutableStateOf(1f) }
            val maxAnisotropy = remember { mutableStateOf(0f) }
            val useVirtualController = remember { mutableStateOf(true) }

            val showKeyDialog = remember { mutableStateOf(false) }
            val keyInstallState = remember { mutableStateOf(KeyInstallState.None) }

            val showFirwmareDialog = remember { mutableStateOf(false) }
            val firmwareInstallState = remember { mutableStateOf(FirmwareInstallState.None) }
            val firmwareVersion = remember { mutableStateOf(mainViewModel.firmwareVersion) }

            val showDataImportDialog = remember { mutableStateOf(false) }
            val dataImportState = remember { mutableStateOf(DataImportState.None) }
            val dataFile = remember { mutableStateOf<DocumentFile?>(null) }

            val isGrid = remember { mutableStateOf(true) }
            val useSwitchLayout = remember { mutableStateOf(true) }
            val enableMotion = remember { mutableStateOf(true) }
            val enablePerformanceMode = remember { mutableStateOf(true) }
            val controllerStickSensitivity = remember { mutableStateOf(1.0f) }

            val enableStubLogs = remember { mutableStateOf(true) }
            val enableInfoLogs = remember { mutableStateOf(true) }
            val enableWarningLogs = remember { mutableStateOf(true) }
            val enableErrorLogs = remember { mutableStateOf(true) }
            val enableGuestLogs = remember { mutableStateOf(true) }
            val enableFsAccessLogs = remember { mutableStateOf(true) }
            val enableTraceLogs = remember { mutableStateOf(true) }
            val enableDebugLogs = remember { mutableStateOf(true) }
            val enableGraphicsLogs = remember { mutableStateOf(true) }

            val isNavigating = remember { mutableStateOf(false) }

            // Dialog-State für den Shortcut-Guide
            val showShortcutGuide = remember { mutableStateOf(false) }

            if (!loaded.value) {
                settingsViewModel.initializeState(
                    memoryManagerMode,
                    useNce,
                    memoryConfiguration,
                    vSyncMode,
                    enableDocked,
                    enablePptc,
                    enableFsIntegrityChecks,
                    fsGlobalAccessLogMode,
                    ignoreMissingServices,
                    enableShaderCache,
                    enableTextureRecompression,
                    enableMacroHLE,
                    resScale,
                    maxAnisotropy,
                    useVirtualController,
                    isGrid,
                    useSwitchLayout,
                    enableMotion,
                    enablePerformanceMode,
                    controllerStickSensitivity,
                    enableStubLogs,
                    enableInfoLogs,
                    enableWarningLogs,
                    enableErrorLogs,
                    enableGuestLogs,
                    enableFsAccessLogs,
                    enableTraceLogs,
                    enableDebugLogs,
                    enableGraphicsLogs
                )
                loaded.value = true
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = { Text(text = "Settings") },
                        navigationIcon = {
                            IconButton(onClick = {
                                settingsViewModel.save(
                                    memoryManagerMode,
                                    useNce,
                                    memoryConfiguration,
                                    vSyncMode,
                                    enableDocked,
                                    enablePptc,
                                    enableFsIntegrityChecks,
                                    fsGlobalAccessLogMode,
                                    ignoreMissingServices,
                                    enableShaderCache,
                                    enableTextureRecompression,
                                    enableMacroHLE,
                                    resScale,
                                    maxAnisotropy,
                                    useVirtualController,
                                    isGrid,
                                    useSwitchLayout,
                                    enableMotion,
                                    enablePerformanceMode,
                                    controllerStickSensitivity,
                                    enableStubLogs,
                                    enableInfoLogs,
                                    enableWarningLogs,
                                    enableErrorLogs,
                                    enableGuestLogs,
                                    enableFsAccessLogs,
                                    enableTraceLogs,
                                    enableDebugLogs,
                                    enableGraphicsLogs
                                )

                                if (!isNavigating.value) {
                                    isNavigating.value = true
                                    mainViewModel.navController?.popBackStack()
                                    CoroutineScope(Dispatchers.Main).launch {
                                        delay(500)
                                        isNavigating.value = false
                                    }
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { contentPadding ->
                Column(
                    modifier = Modifier
                        .padding(contentPadding)
                        .verticalScroll(rememberScrollState())
                ) {
                    // UI
                    ExpandableView(
                        onCardArrowClick = { },
                        title = "User Interface",
                        icon = Icons.Outlined.BarChart,
                        isFirst = true
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            isGrid.SwitchSelector("Use Grid")

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "System Firmware",
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                Text(
                                    text = firmwareVersion.value,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ActionButton(
                                    onClick = { settingsViewModel.openGameFolder() },
                                    text = "Add Game Folder",
                                    icon = Icons.Default.Add,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ActionButton(
                                    onClick = { showKeyDialog.value = true },
                                    text = "Install Keys",
                                    icon = Icons.Default.Build,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ActionButton(
                                    onClick = { showFirwmareDialog.value = true },
                                    text = "Install Firmware",
                                    icon = Icons.Default.Build,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ActionButton(
                                    onClick = { showDataImportDialog.value = true },
                                    text = "Import App Data",
                                    icon = Icons.Default.FileDownload,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ActionButton(
                                    onClick = {
                                        fun createIntent(action: String): Intent {
                                            val intent = Intent(action).apply {
                                                addCategory(Intent.CATEGORY_DEFAULT)
                                                data = DocumentsContract.buildRootUri(
                                                    DocumentProvider.AUTHORITY,
                                                    DocumentProvider.ROOT_ID
                                                )
                                                addFlags(
                                                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                                                        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
                                                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                                )
                                            }
                                            return intent
                                        }
                                        try {
                                            mainViewModel.activity.startActivity(createIntent(Intent.ACTION_VIEW))
                                            return@ActionButton
                                        } catch (_: ActivityNotFoundException) {}
                                        try {
                                            mainViewModel.activity.startActivity(createIntent("android.provider.action.BROWSE"))
                                            return@ActionButton
                                        } catch (_: ActivityNotFoundException) {}
                                        try {
                                            mainViewModel.activity.startActivity(createIntent("com.google.android.documentsui"))
                                            return@ActionButton
                                        } catch (_: ActivityNotFoundException) {}
                                        try {
                                            mainViewModel.activity.startActivity(createIntent("com.android.documentsui"))
                                            return@ActionButton
                                        } catch (_: ActivityNotFoundException) {}
                                    },
                                    text = "Open App Folder",
                                    icon = Icons.Default.Home,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false
                                )
                            }

                            // "Shortcut Guide" Button
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ActionButton(
                                    onClick = { showShortcutGuide.value = true },
                                    text = "Shortcut Guide",
                                    icon = Icons.Outlined.Settings,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false
                                )
                            }
                        }
                    }

                    // Keys
                    SimpleAlertDialog.Custom(
                        showDialog = showKeyDialog,
                        onDismissRequest = {
                            if (keyInstallState.value != KeyInstallState.Install) {
                                showKeyDialog.value = false
                                settingsViewModel.clearKeySelection(keyInstallState)
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Key Installation",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                textAlign = TextAlign.Center
                            )

                            when (keyInstallState.value) {
                                KeyInstallState.None -> {
                                    Text(
                                        text = "Select a key file to install key from.",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = { settingsViewModel.selectKey(keyInstallState) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) { Text(text = "Select File") }
                                        Button(
                                            onClick = {
                                                showKeyDialog.value = false
                                                settingsViewModel.clearKeySelection(keyInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) { Text(text = "Cancel") }
                                    }
                                }

                                KeyInstallState.Query -> {
                                    Text(
                                        text = "Key file will be installed. Do you want to continue?",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                settingsViewModel.installKey(keyInstallState)
                                                if (keyInstallState.value == KeyInstallState.None) {
                                                    showKeyDialog.value = false
                                                    settingsViewModel.clearKeySelection(keyInstallState)
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) { Text(text = "Yes") }
                                        Button(
                                            onClick = {
                                                showKeyDialog.value = false
                                                settingsViewModel.clearKeySelection(keyInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) { Text(text = "No") }
                                    }
                                }

                                KeyInstallState.Install -> {
                                    Text(
                                        text = "Installing key file...",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    )
                                }

                                KeyInstallState.Done -> {
                                    Text(
                                        text = "Key file installed.",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                showKeyDialog.value = false
                                                settingsViewModel.clearKeySelection(keyInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) { Text(text = "Close") }
                                    }
                                }

                                KeyInstallState.Cancelled -> {
                                    val file = settingsViewModel.selectedKeyFile
                                    if (file != null) {
                                        if (file.extension == "key") {
                                            Text(
                                                text = "Unknown Error has occurred. Please check logs.",
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 8.dp, bottom = 8.dp),
                                                textAlign = TextAlign.Start
                                            )
                                        } else {
                                            Text(
                                                text = "File type is not supported.",
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 8.dp, bottom = 8.dp),
                                                textAlign = TextAlign.Start
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "File type is not supported.",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 8.dp, bottom = 8.dp),
                                            textAlign = TextAlign.Start
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Button(
                                            onClick = {
                                                showKeyDialog.value = false
                                                settingsViewModel.clearKeySelection(keyInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) { Text(text = "Close") }
                                    }
                                }
                            }
                        }
                    }

                    // Firmware
                    SimpleAlertDialog.Custom(
                        showDialog = showFirwmareDialog,
                        onDismissRequest = {
                            if (firmwareInstallState.value != FirmwareInstallState.Install) {
                                showFirwmareDialog.value = false
                                settingsViewModel.clearFirmwareSelection(firmwareInstallState)
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Firmware Installation",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                textAlign = TextAlign.Center
                            )

                            when (firmwareInstallState.value) {
                                FirmwareInstallState.None -> {
                                    Text(
                                        text = "Select a zip or xci file to install firmware from.",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = { settingsViewModel.selectFirmware(firmwareInstallState) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) { Text(text = "Select File") }
                                        Button(
                                            onClick = {
                                                showFirwmareDialog.value = false
                                                settingsViewModel.clearFirmwareSelection(firmwareInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) { Text(text = "Cancel") }
                                    }
                                }

                                FirmwareInstallState.Query -> {
                                    Text(
                                        text = "Firmware ${settingsViewModel.selectedFirmwareVersion} will be installed. Do you want to continue?",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                settingsViewModel.installFirmware(firmwareInstallState)
                                                if (firmwareInstallState.value == FirmwareInstallState.None) {
                                                    showFirwmareDialog.value = false
                                                    settingsViewModel.clearFirmwareSelection(firmwareInstallState)
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) { Text(text = "Yes") }
                                        Button(
                                            onClick = {
                                                showFirwmareDialog.value = false
                                                settingsViewModel.clearFirmwareSelection(firmwareInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) { Text(text = "No") }
                                    }
                                }

                                FirmwareInstallState.Verifying -> {
                                    Text(
                                        text = "Verifying selected file...",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    )
                                }

                                FirmwareInstallState.Install -> {
                                    Text(
                                        text = "Installing firmware ${settingsViewModel.selectedFirmwareVersion}...",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    )
                                }

                                FirmwareInstallState.Done -> {
                                    Text(
                                        text = "Firmware ${settingsViewModel.selectedFirmwareVersion}.",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    firmwareVersion.value = mainViewModel.firmwareVersion
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                showFirwmareDialog.value = false
                                                settingsViewModel.clearFirmwareSelection(firmwareInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) { Text(text = "Close") }
                                    }
                                }

                                FirmwareInstallState.Cancelled -> {
                                    val file = settingsViewModel.selectedFirmwareFile
                                    if (file != null) {
                                        if (file.extension == "xci" || file.extension == "zip") {
                                            if (settingsViewModel.selectedFirmwareVersion.isEmpty()) {
                                                Text(
                                                    text = "Unable to find version in selected file.",
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 8.dp, bottom = 8.dp),
                                                    textAlign = TextAlign.Start
                                                )
                                            } else {
                                                Text(
                                                    text = "Unknown Error has occurred. Please check logs.",
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 8.dp, bottom = 8.dp),
                                                    textAlign = TextAlign.Start
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "File type is not supported.",
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 8.dp, bottom = 8.dp),
                                                textAlign = TextAlign.Start
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "File type is not supported.",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 8.dp, bottom = 8.dp),
                                            textAlign = TextAlign.Start
                                        )
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Button(
                                            onClick = {
                                                showFirwmareDialog.value = false
                                                settingsViewModel.clearFirmwareSelection(firmwareInstallState)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) { Text(text = "Close") }
                                    }
                                }
                            }
                        }
                    }

                    // Data Import
                    SimpleAlertDialog.Custom(
                        showDialog = showDataImportDialog,
                        onDismissRequest = {
                            if (dataImportState.value != DataImportState.Import) {
                                showDataImportDialog.value = false
                                dataFile.value = null
                                dataImportState.value = DataImportState.None
                            }
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "App Data Import",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                textAlign = TextAlign.Center
                            )

                            when (dataImportState.value) {
                                DataImportState.None -> {
                                    Text(
                                        text = "Select a zip file to import app data from.",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val storage = MainActivity.StorageHelper
                                                storage?.apply {
                                                    val callBack = this.onFileSelected
                                                    onFileSelected = { requestCode, files ->
                                                        onFileSelected = callBack
                                                        if (requestCode == IMPORT_CODE) {
                                                            val filePicked = files.firstOrNull()
                                                            filePicked?.apply {
                                                                if (this.extension == "zip") {
                                                                    dataFile.value = this
                                                                    dataImportState.value = DataImportState.Query
                                                                }
                                                            }
                                                        }
                                                    }
                                                    openFilePicker(
                                                        IMPORT_CODE,
                                                        filterMimeTypes = arrayOf("application/zip")
                                                    )
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) { Text(text = "Select File") }
                                        Button(
                                            onClick = {
                                                showDataImportDialog.value = false
                                                dataFile.value = null
                                                dataImportState.value = DataImportState.None
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) { Text(text = "Cancel") }
                                    }
                                }

                                DataImportState.Query -> {
                                    Text(
                                        text = "Current app data will be wiped and replaced. Do you want to continue?",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val picked = dataFile.value
                                                dataFile.value = null
                                                picked?.apply {
                                                    thread {
                                                        settingsViewModel.importAppData(this, dataImportState)
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) { Text(text = "Yes") }
                                        Button(
                                            onClick = {
                                                showDataImportDialog.value = false
                                                dataFile.value = null
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) { Text(text = "No") }
                                    }
                                }

                                DataImportState.Import -> {
                                    Text(
                                        text = "Importing app data...",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    LinearProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    )
                                }

                                DataImportState.Done -> {
                                    Text(
                                        text = "Data import completed successfully.",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 8.dp, bottom = 8.dp),
                                        textAlign = TextAlign.Start
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                showDataImportDialog.value = false
                                                dataFile.value = null
                                                firmwareVersion.value = mainViewModel.firmwareVersion
                                                dataImportState.value = DataImportState.None
                                                mainViewModel.userViewModel.refreshUsers()
                                                mainViewModel.homeViewModel.requestReload()
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                        ) { Text(text = "Close") }
                                    }
                                }
                            }
                        }
                    }

                    // Input
                    ExpandableView(
                        onCardArrowClick = { },
                        title = "Input",
                        icon = Icons.Outlined.VideogameAsset
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            useVirtualController.SwitchSelector(label = "Use Virtual Controller")
                            useSwitchLayout.SwitchSelector(label = "Use Switch Controller Layout")

                            val interactionSource = remember { MutableInteractionSource() }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Controller Stick Sensitivity",
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                Slider(
                                    modifier = Modifier.width(250.dp),
                                    value = controllerStickSensitivity.value,
                                    onValueChange = { controllerStickSensitivity.value = it },
                                    valueRange = 0.1f..2f,
                                    steps = 20,
                                    interactionSource = interactionSource,
                                    thumb = {
                                        Label(
                                            label = {
                                                PlainTooltip(
                                                    modifier = Modifier
                                                        .sizeIn(45.dp, 25.dp)
                                                        .wrapContentWidth()
                                                ) {
                                                    Text("%.2f".format(controllerStickSensitivity.value))
                                                }
                                            },
                                            interactionSource = interactionSource
                                        ) {
                                            Icon(
                                                imageVector = org.kenjinx.android.Icons.circle(
                                                    color = MaterialTheme.colorScheme.primary
                                                ),
                                                contentDescription = null,
                                                modifier = Modifier.size(ButtonDefaults.IconSize),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                            }

                            enableDocked.SwitchSelector(label = "Docked Mode")
                            enableMotion.SwitchSelector(label = "Motion Sensor")
                        }
                    }

                    // System
                    ExpandableView(
                        onCardArrowClick = { },
                        title = "System",
                        icon = Icons.Outlined.Settings
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            VSyncDropdown(
                                selectedVSyncMode = vSyncMode.value,
                                onModeSelected = { mode -> vSyncMode.value = mode }
                            )
                            MemoryDropdown(
                                selectedMemoryConfiguration = memoryConfiguration.value,
                                onConfigurationSelected = { configuration ->
                                    memoryConfiguration.value = configuration
                                }
                            )

                            enableFsIntegrityChecks.SwitchSelector(label = "Fs Integrity Check")
                            ignoreMissingServices.SwitchSelector(label = "Ignore Missing Services")
                            enablePerformanceMode.SwitchSelector(label = "Performance Mode")
                        }
                    }

                    // CPU
                    ExpandableView(
                        onCardArrowClick = { },
                        title = "Cpu",
                        icon = Icons.Outlined.Memory
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            useNce.SwitchSelector(label = "Enable NCE (Native Code Execution)")
                            enablePptc.SwitchSelector(label = "Enable PPTC (Profiled Persistent Translation Cache)")
                            MemoryModeDropdown(
                                selectedMemoryManagerMode = memoryManagerMode.value,
                                onModeSelected = { mode -> memoryManagerMode.value = mode }
                            )
                        }
                    }

                    // Graphics
                    ExpandableView(
                        onCardArrowClick = { },
                        title = "Graphics",
                        icon = Icons.Outlined.Panorama
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            enableShaderCache.SwitchSelector(label = "Enable Shader Cache")
                            enableTextureRecompression.SwitchSelector(label = "Enable Texture Recompression")
                            enableMacroHLE.SwitchSelector(label = "Enable Macro HLE")

                            ResolutionScaleDropdown(
                                selectedScale = resScale.value,
                                onScaleSelected = { scale -> resScale.value = scale }
                            )
                            AnisotropicFilteringDropdown(
                                selectedAnisotropy = maxAnisotropy.value,
                                onAnisotropySelected = { anisotropy -> maxAnisotropy.value = anisotropy }
                            )

                            val isDriverSelectorOpen = remember { mutableStateOf(false) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ActionButton(
                                    onClick = { isDriverSelectorOpen.value = !isDriverSelectorOpen.value },
                                    text = "Install Driver",
                                    icon = Icons.Default.Build,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false
                                )
                            }

                            SimpleAlertDialog.Custom(
                                showDialog = isDriverSelectorOpen,
                                onDismissRequest = { isDriverSelectorOpen.value = false },
                                properties = DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                VulkanDriverViews.Main(settingsViewModel.activity, isDriverSelectorOpen)
                            }
                        }
                    }

                    // Logging
                    ExpandableView(
                        onCardArrowClick = { },
                        title = "Logging",
                        icon = Icons.Outlined.FileOpen
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            enableStubLogs.SwitchSelector(label = "Enable Stub Logs")
                            enableInfoLogs.SwitchSelector(label = "Enable Info Logs")
                            enableWarningLogs.SwitchSelector(label = "Enable Warning Logs")
                            enableErrorLogs.SwitchSelector(label = "Enable Error Logs")
                            enableGuestLogs.SwitchSelector(label = "Enable Guest Logs")
                            enableTraceLogs.SwitchSelector(label = "Enable Trace Logs")
                            enableFsAccessLogs.SwitchSelector(label = "Enable Fs Access Logs")
                            enableDebugLogs.SwitchSelector(label = "Enable Debug Logs")
                            enableGraphicsLogs.SwitchSelector(label = "Enable Graphics Logs")

                            FsGlobalAccessLogModeDropdown(
                                selectedFsGlobalAccess = fsGlobalAccessLogMode.value,
                                onFsGlobalAccessSelected = { fsGlobalAccess ->
                                    fsGlobalAccessLogMode.value = fsGlobalAccess
                                }
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ActionButton(
                                    onClick = { mainViewModel.logging.requestExport() },
                                    text = "Send Logs",
                                    icon = Icons.Default.MailOutline,
                                    modifier = Modifier.weight(1f),
                                    isFullWidth = false
                                )
                            }
                        }
                    }
                }

                // Der Shortcut-Guide-Dialog kann außerhalb stehen; er ist modal und unabhängig
                ShortcutGuideDialog(
                    show = showShortcutGuide.value,
                    onDismiss = { showShortcutGuide.value = false }
                )
            }
        }

        // Breiter + scrollbar
        @Composable
        private fun ShortcutGuideDialog(
            show: Boolean,
            onDismiss: () -> Unit
        ) {
            if (!show) return
            val scroll = rememberScrollState()
            AlertDialog(
                onDismissRequest = onDismiss,
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .sizeIn(minWidth = 280.dp, maxWidth = 600.dp),
                title = { Text("Shortcut Guide") },
                text = {
                    Column(
                        modifier = Modifier
                            .verticalScroll(scroll)
                            .sizeIn(maxHeight = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("1) Tap the “+” button in the top bar.")
                        Text("2) Select your game file.")
                        Text("3) Enter a shortcut name. Optionally select a custom icon or use the app icon.")
                        Text("4) Confirm the Android prompt.")
                        Text("Tip: The app stays in portrait during the system dialog and returns to landscape after some seconds.")
                    }
                },
                confirmButton = {
                    Button(onClick = onDismiss) { Text("OK") }
                }
            )
        }

        @Composable
        fun MemoryModeDropdown(
            selectedMemoryManagerMode: MemoryManagerMode,
            onModeSelected: (MemoryManagerMode) -> Unit,
            modifier: Modifier = Modifier
        ) {
            val modes = MemoryManagerMode.values().toList()
            DropdownSelector(
                label = "Memory Manager Mode",
                selectedValue = selectedMemoryManagerMode,
                options = modes,
                getDisplayText = { mode ->
                    when (mode) {
                        MemoryManagerMode.SoftwarePageTable -> "Software"
                        MemoryManagerMode.HostMapped -> "Host (fast)"
                        MemoryManagerMode.HostMappedUnsafe -> "Host Unchecked (fastest, unsafe)"
                    }
                },
                onOptionSelected = onModeSelected,
                modifier = modifier
            )
        }

        @Composable
        fun VSyncDropdown(
            selectedVSyncMode: VSyncMode,
            onModeSelected: (VSyncMode) -> Unit,
            modifier: Modifier = Modifier
        ) {
            val modes = VSyncMode.values().toList()
            DropdownSelector(
                label = "VSync",
                selectedValue = selectedVSyncMode,
                options = modes,
                getDisplayText = { mode ->
                    when (mode) {
                        VSyncMode.Switch -> "Switch"
                        VSyncMode.Unbounded -> "Unbounded"
                    }
                },
                onOptionSelected = onModeSelected,
                modifier = modifier
            )
        }

        @Composable
        fun MemoryDropdown(
            selectedMemoryConfiguration: MemoryConfiguration,
            onConfigurationSelected: (MemoryConfiguration) -> Unit,
            modifier: Modifier = Modifier
        ) {
            val modes = MemoryConfiguration.values().toList()
            DropdownSelector(
                label = "Dram Size",
                selectedValue = selectedMemoryConfiguration,
                options = modes,
                getDisplayText = { configuration ->
                    when (configuration) {
                        MemoryConfiguration.MemoryConfiguration4GiB -> "4GB"
                        MemoryConfiguration.MemoryConfiguration6GiB -> "6GB"
                        MemoryConfiguration.MemoryConfiguration8GiB -> "8GB"
                        MemoryConfiguration.MemoryConfiguration10GiB -> "10GB"
                        MemoryConfiguration.MemoryConfiguration12GiB -> "12GB"
                    }
                },
                onOptionSelected = onConfigurationSelected,
                modifier = modifier
            )
        }

        @Composable
        fun ResolutionScaleDropdown(
            selectedScale: Float,
            onScaleSelected: (Float) -> Unit,
            modifier: Modifier = Modifier
        ) {
            val scaleOptions = listOf(
                0.5f to "0.5x (360p/540p)",
                1f to "1.0x (720p/1080p)",
                2f to "2.0x (1440p/2160p)",
                3f to "3.0x (2160p/3240p)",
                4f to "4.0x (2800p/4320p)"
            )
            DropdownSelector(
                label = "Resolution Scale",
                selectedValue = selectedScale,
                options = scaleOptions.map { it.first },
                getDisplayText = { scale ->
                    scaleOptions.find { it.first == scale }?.second ?: "${scale}x"
                },
                onOptionSelected = onScaleSelected,
                modifier = modifier
            )
        }

        @Composable
        fun AnisotropicFilteringDropdown(
            selectedAnisotropy: Float,
            onAnisotropySelected: (Float) -> Unit,
            modifier: Modifier = Modifier
        ) {
            val anisotropyOptions = listOf(
                0.0f to "0x",
                1.0f to "2x",
                2.0f to "4x",
                3.0f to "8x",
                4.0f to "16x"
            )
            DropdownSelector(
                label = "Anisotropic Filtering",
                selectedValue = selectedAnisotropy,
                options = anisotropyOptions.map { it.first },
                getDisplayText = { anisotropy ->
                    anisotropyOptions.find { it.first == anisotropy }?.second ?: "${anisotropy}x"
                },
                onOptionSelected = onAnisotropySelected,
                modifier = modifier
            )
        }

        @Composable
        fun FsGlobalAccessLogModeDropdown(
            selectedFsGlobalAccess: Int,
            onFsGlobalAccessSelected: (Int) -> Unit,
            modifier: Modifier = Modifier
        ) {
            val fsGlobalAccessOptions = listOf(
                0 to "0",
                1 to "1",
                2 to "2",
                3 to "3"
            )
            DropdownSelector(
                label = "Fs Global Access Log Mode",
                selectedValue = selectedFsGlobalAccess,
                options = fsGlobalAccessOptions.map { it.first },
                getDisplayText = { fsGlobalAccess ->
                    fsGlobalAccessOptions.find { it.first == fsGlobalAccess }?.second
                        ?: "$fsGlobalAccess"
                },
                onOptionSelected = onFsGlobalAccessSelected,
                modifier = modifier
            )
        }
    }
}
