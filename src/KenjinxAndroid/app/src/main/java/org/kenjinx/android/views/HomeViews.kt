package org.kenjinx.android.views

import android.app.Activity
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.anggrayudi.storage.extension.launchOnUiThread
import java.util.Base64
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.kenjinx.android.R
import org.kenjinx.android.ShortcutUtils
import org.kenjinx.android.ShortcutWizardActivity
import org.kenjinx.android.viewmodels.FileType
import org.kenjinx.android.viewmodels.GameModel
import org.kenjinx.android.viewmodels.HomeViewModel
import org.kenjinx.android.viewmodels.QuickSettings
import org.kenjinx.android.widgets.SimpleAlertDialog

// NEW
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile

class HomeViews {
    companion object {
        const val ListImageSize = 150
        const val GridImageSize = 300

        // --- Versions-Badge unten links
        @Composable
        private fun VersionBadge(modifier: Modifier = Modifier) {
            Text(
                text = "v" + org.kenjinx.android.BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = modifier.padding(8.dp)
            )
        }

        // -- Hilfen für Shortcut-Flow (direkt aus ausgewähltem Game)
        private fun resolveGameUri(gm: GameModel): Uri? = gm.file.uri

        private fun decodeGameIcon(gm: GameModel): Bitmap? {
            return try {
                val b64 = gm.icon ?: return null
                val bytes = Base64.getDecoder().decode(b64)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (_: Throwable) {
                null
            }
        }

        @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
        @Composable
        fun Home(
            viewModel: HomeViewModel = HomeViewModel(),
            navController: NavHostController? = null,
            isPreview: Boolean = false
        ) {
            viewModel.ensureReloadIfNecessary()
            val showAppActions = remember { mutableStateOf(false) }
            val showLoading = remember { mutableStateOf(false) }
            val openTitleUpdateDialog = remember { mutableStateOf(false) }
            val canClose = remember { mutableStateOf(true) }
            val openDlcDialog = remember { mutableStateOf(false) }
            val showError = remember { mutableStateOf("") }
            val selectedModel = remember { mutableStateOf(viewModel.mainViewModel?.selected) }
            val query = remember { mutableStateOf("") }
            var refreshUser by remember { mutableStateOf(true) }
            var isFabVisible by remember { mutableStateOf(true) }
            val isNavigating = remember { mutableStateOf(false) }

            // NEW: Amiibo slot picker state
            val showAmiiboSlotDialog = remember { mutableStateOf(false) }
            val pendingSlot = remember { mutableStateOf(1) }

            // Shortcut-Dialog-State
            val showShortcutDialog = remember { mutableStateOf(false) }
            val shortcutName = remember { mutableStateOf("") }

            val context = LocalContext.current
            val activity = LocalContext.current as? Activity

            // NEW: Launcher für Amiibo (OpenDocument)
            val pickAmiiboLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri != null && activity != null) {
                    try {
                        activity.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}
                    val name = DocumentFile.fromSingleUri(activity, uri)?.name ?: "amiibo.bin"
                    val qs = QuickSettings(activity)
                    when (pendingSlot.value) {
                        1 -> { qs.amiibo1Uri = uri.toString(); qs.amiibo1Name = name }
                        2 -> { qs.amiibo2Uri = uri.toString(); qs.amiibo2Name = name }
                        3 -> { qs.amiibo3Uri = uri.toString(); qs.amiibo3Name = name }
                        4 -> { qs.amiibo4Uri = uri.toString(); qs.amiibo4Name = name }
                        5 -> { qs.amiibo5Uri = uri.toString(); qs.amiibo5Name = name }
                    }
                    qs.save()
                    Toast.makeText(activity, "Amiibo saved to slot ${pendingSlot.value}", Toast.LENGTH_SHORT).show()
                }
            }

            // Launcher für "Custom icon" (OpenDocument)
            val pickImageLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                val gm = viewModel.mainViewModel?.selected
                if (uri != null && gm != null && activity != null) {
                    // Bitmap laden
                    val bmp = runCatching {
                        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) }
                    }.getOrNull()

                    val label = shortcutName.value.ifBlank { gm.titleName ?: "Start Game" }
                    val gameUri = resolveGameUri(gm)
                    if (gameUri != null) {
                        // Persistente Rechte für die Spieldatei (wichtig für Shortcut)
                        ShortcutUtils.persistReadWrite(activity, gameUri)

                        ShortcutUtils.pinShortcutForGame(
                            activity = activity,
                            gameUri = gameUri,
                            label = label,
                            iconBitmap = bmp
                        ) {
                            // optionaler Callback nach System-Dialog
                        }
                    } else {
                        showError.value = "Shortcut failed (no game URI found)."
                    }
                }
            }

            val nestedScrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (available.y < -1) isFabVisible = false
                        if (available.y > 1) isFabVisible = true
                        return Offset.Zero
                    }
                }
            }

            // --- Box um Scaffold, damit wir das Badge overlayen können
            Box(Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .background(MaterialTheme.colorScheme.surface),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (refreshUser && viewModel.mainViewModel?.userViewModel?.openedUser?.userPicture?.isNotEmpty() == true) {
                                                Color.Transparent
                                            } else {
                                                MaterialTheme.colorScheme.surface
                                            }
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            if (!isNavigating.value) {
                                                isNavigating.value = true
                                                val currentRoute = navController?.currentDestination?.route
                                                if (currentRoute != "user") {
                                                    navController?.navigate("user") {
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                                CoroutineScope(Dispatchers.Main).launch {
                                                    delay(500)
                                                    isNavigating.value = false
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (refreshUser && viewModel.mainViewModel?.userViewModel?.openedUser?.userPicture?.isNotEmpty() == true) {
                                        val pic = viewModel.mainViewModel.userViewModel.openedUser.userPicture
                                        Image(
                                            bitmap = BitmapFactory.decodeByteArray(
                                                pic,
                                                0,
                                                pic?.size ?: 0
                                            ).asImageBitmap(),
                                            contentDescription = "user image",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.Person,
                                            contentDescription = "User",
                                            modifier = Modifier.size(28.dp),
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                // Settings
                                IconButton(
                                    onClick = {
                                        if (!isNavigating.value) {
                                            isNavigating.value = true
                                            val currentRoute = navController?.currentDestination?.route
                                            if (currentRoute != "settings") {
                                                navController?.navigate("settings") {
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                            CoroutineScope(Dispatchers.Main).launch {
                                                delay(500)
                                                isNavigating.value = false
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                                }

                                // Shortcut-Wizard (allgemein)
                                IconButton(
                                    onClick = {
                                        context.startActivity(
                                            Intent(context, ShortcutWizardActivity::class.java)
                                        )
                                    },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Add Shortcut")
                                }
                            }

                            OutlinedTextField(
                                value = query.value,
                                onValueChange = { query.value = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                placeholder = {
                                    Text("Search...", modifier = Modifier.padding(bottom = 4.dp))
                                },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                )
                            )
                        }
                    },
                    floatingActionButton = {
                        AnimatedVisibility(visible = isFabVisible) {
                            // NEW: two FABs in a row: Refresh + Import Amiibo
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FloatingActionButton(
                                    onClick = {
                                        viewModel.requestReload()
                                        viewModel.ensureReloadIfNecessary()
                                    },
                                    shape = MaterialTheme.shapes.small,
                                    containerColor = MaterialTheme.colorScheme.tertiary
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "refresh")
                                }
                                FloatingActionButton(
                                    onClick = { showAmiiboSlotDialog.value = true },
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Icon(
                                        org.kenjinx.android.Icons.folderOpen(MaterialTheme.colorScheme.onSurface),
                                        contentDescription = "Import Amiibo"
                                    )
                                }
                            }
                        }
                    },
                    floatingActionButtonPosition = FabPosition.End
                ) { contentPadding ->
                    Column(modifier = Modifier.padding(contentPadding)) {
                        Box {
                            val list = remember { viewModel.gameList }
                            val isLoading = remember { viewModel.isLoading }

                            viewModel.filter(query.value)

                            if (!isPreview) {
                                val settings = QuickSettings(viewModel.activity!!)
                                if (isLoading.value) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .width(64.dp)
                                                .align(Alignment.Center),
                                            color = MaterialTheme.colorScheme.secondary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    }
                                } else {
                                    if (settings.isGrid) {
                                        val size = GridImageSize / Resources.getSystem().displayMetrics.density
                                        LazyVerticalGrid(
                                            columns = GridCells.Adaptive(minSize = (size + 4).dp),
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(4.dp)
                                                .nestedScroll(nestedScrollConnection),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            items(list) {
                                                it.titleName?.apply {
                                                    if (this.isNotEmpty() && (query.value.trim()
                                                            .isEmpty() || this.lowercase(Locale.getDefault())
                                                            .contains(query.value))) {
                                                        GridGameItem(
                                                            it,
                                                            viewModel,
                                                            showAppActions,
                                                            showLoading,
                                                            selectedModel,
                                                            showError
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        LazyColumn(Modifier.fillMaxSize()) {
                                            items(list) {
                                                it.titleName?.apply {
                                                    if (this.isNotEmpty() && (query.value.trim()
                                                            .isEmpty() || this.lowercase(Locale.getDefault())
                                                            .contains(query.value))) {
                                                        Box(modifier = Modifier.animateItemPlacement()) {
                                                            ListGameItem(
                                                                it,
                                                                viewModel,
                                                                showAppActions,
                                                                showLoading,
                                                                selectedModel,
                                                                showError
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    SimpleAlertDialog.Loading(showDialog = showLoading)
                    SimpleAlertDialog.Custom(
                        showDialog = openTitleUpdateDialog,
                        onDismissRequest = { openTitleUpdateDialog.value = false },
                        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        val titleId = viewModel.mainViewModel?.selected?.titleId ?: ""
                        val name = viewModel.mainViewModel?.selected?.titleName ?: ""
                        TitleUpdateViews.Main(titleId, name, openTitleUpdateDialog, canClose)
                    }
                    SimpleAlertDialog.Custom(
                        showDialog = openDlcDialog,
                        onDismissRequest = { openDlcDialog.value = false },
                        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        val titleId = viewModel.mainViewModel?.selected?.titleId ?: ""
                        val name = viewModel.mainViewModel?.selected?.titleName ?: ""
                        DlcViews.Main(titleId, name, openDlcDialog, canClose)
                    }

                    // NEW: Amiibo slot chooser dialog (outside of game)
                    if (showAmiiboSlotDialog.value) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showAmiiboSlotDialog.value = false },
                            title = { Text("Import Amiibo") },
                            text = {
                                Column {
                                    Text("Choose a slot to save this Amiibo:", modifier = Modifier.padding(bottom = 8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = {
                                            pendingSlot.value = 1
                                            pickAmiiboLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                                            showAmiiboSlotDialog.value = false
                                        }) { Text("Slot 1") }
                                        TextButton(onClick = {
                                            pendingSlot.value = 2
                                            pickAmiiboLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                                            showAmiiboSlotDialog.value = false
                                        }) { Text("Slot 2") }
                                        TextButton(onClick = {
                                            pendingSlot.value = 3
                                            pickAmiiboLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                                            showAmiiboSlotDialog.value = false
                                        }) { Text("Slot 3") }
                                    }
                                    Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = {
                                            pendingSlot.value = 4
                                            pickAmiiboLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                                            showAmiiboSlotDialog.value = false
                                        }) { Text("Slot 4") }
                                        TextButton(onClick = {
                                            pendingSlot.value = 5
                                            pickAmiiboLauncher.launch(arrayOf("application/octet-stream", "*/*"))
                                            showAmiiboSlotDialog.value = false
                                        }) { Text("Slot 5") }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showAmiiboSlotDialog.value = false }) { Text("Close") }
                            }
                        )
                    }
                }

                if (viewModel.mainViewModel?.loadGameModel?.value != null)
                    LaunchedEffect(viewModel.mainViewModel.loadGameModel.value) {
                        if (viewModel.mainViewModel.bootPath.value ==
                            "gameItem_${viewModel.mainViewModel.loadGameModel.value!!.titleName}"
                        ) {
                            viewModel.mainViewModel.bootPath.value = null

                            thread {
                                showLoading.value = true
                                val success = viewModel.mainViewModel.loadGame(
                                    viewModel.mainViewModel.loadGameModel.value!!,
                                    true,
                                    viewModel.mainViewModel.forceNceAndPptc.value
                                ) ?: false
                                if (success == 1) {
                                    launchOnUiThread {
                                        viewModel.mainViewModel.navigateToGame()
                                    }
                                } else {
                                    if (success == -2)
                                        showError.value = "Error loading update. Please re-add update file"
                                    viewModel.mainViewModel.loadGameModel.value!!.close()
                                }
                                showLoading.value = false
                            }
                        }
                    }

                if (showAppActions.value)
                    ModalBottomSheet(
                        content = {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                if (showAppActions.value) {
                                    // Start (Play)
                                    IconButton(onClick = {
                                        if (viewModel.mainViewModel?.selected != null) {
                                            thread {
                                                showLoading.value = true
                                                val success = viewModel.mainViewModel.loadGame(
                                                    viewModel.mainViewModel.selected!!
                                                )
                                                if (success == 1) {
                                                    launchOnUiThread {
                                                        viewModel.mainViewModel.navigateToGame()
                                                    }
                                                } else {
                                                    if (success == -2)
                                                        showError.value =
                                                            "Error loading update. Please re-add update file"
                                                    viewModel.mainViewModel.selected!!.close()
                                                }
                                                showLoading.value = false
                                            }
                                        }
                                    }) {
                                        Icon(
                                            org.kenjinx.android.Icons.playArrow(MaterialTheme.colorScheme.onSurface),
                                            contentDescription = "Run"
                                        )
                                    }

                                    // Shortcut erstellen (direkt fürs ausgewählte Spiel)
                                    IconButton(onClick = {
                                        val gm = viewModel.mainViewModel?.selected
                                        if (gm != null) {
                                            shortcutName.value = gm.titleName ?: ""
                                            showShortcutDialog.value = true
                                        }
                                    }) {
                                        Icon(
                                            Icons.Filled.Add,
                                            contentDescription = "Create Shortcut"
                                        )
                                    }

                                    // Menü
                                    val showAppMenu = remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(onClick = { showAppMenu.value = true }) {
                                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                                        }
                                        DropdownMenu(
                                            expanded = showAppMenu.value,
                                            onDismissRequest = { showAppMenu.value = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(text = "Clear PPTC Cache") },
                                                onClick = {
                                                    showAppMenu.value = false
                                                    viewModel.mainViewModel?.clearPptcCache(
                                                        viewModel.mainViewModel.selected?.titleId ?: ""
                                                    )
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(text = "Purge Shader Cache") },
                                                onClick = {
                                                    showAppMenu.value = false
                                                    viewModel.mainViewModel?.purgeShaderCache(
                                                        viewModel.mainViewModel.selected?.titleId ?: ""
                                                    )
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(text = "Delete All Cache") },
                                                onClick = {
                                                    showAppMenu.value = false
                                                    viewModel.mainViewModel?.deleteCache(
                                                        viewModel.mainViewModel.selected?.titleId ?: ""
                                                    )
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(text = "Manage Updates") },
                                                onClick = {
                                                    showAppMenu.value = false
                                                    openTitleUpdateDialog.value = true
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(text = "Manage DLC") },
                                                onClick = {
                                                    showAppMenu.value = false
                                                    openDlcDialog.value = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        onDismissRequest = {
                            showAppActions.value = false
                            selectedModel.value = null
                        }
                    )

                // --- Shortcut-Dialog: Name + Icon-Quelle
                if (showShortcutDialog.value) {
                    val gm = viewModel.mainViewModel?.selected
                    AlertDialog(
                        onDismissRequest = { showShortcutDialog.value = false },
                        title = { Text("Create shortcut") },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = shortcutName.value,
                                    onValueChange = { shortcutName.value = it },
                                    label = { Text("Name") },
                                    singleLine = true
                                )
                                Text(
                                    text = "Choose icon:",
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    TextButton(onClick = {
                                        // App icon (Grid image)
                                        if (gm != null && activity != null) {
                                            val gameUri = resolveGameUri(gm)
                                            if (gameUri != null) {
                                                // persist rights for the game file
                                                ShortcutUtils.persistReadWrite(activity, gameUri)

                                                val bmp = decodeGameIcon(gm)
                                                val label = shortcutName.value.ifBlank { gm.titleName ?: "Start Game" }

                                                ShortcutUtils.pinShortcutForGame(
                                                    activity = activity,
                                                    gameUri = gameUri,
                                                    label = label,
                                                    iconBitmap = bmp
                                                ) { }
                                                showShortcutDialog.value = false
                                            } else {
                                                showShortcutDialog.value = false
                                            }
                                        } else {
                                            showShortcutDialog.value = false
                                        }
                                    }) { Text("App icon") }

                                    TextButton(onClick = {
                                        // Custom icon: open picker
                                        pickImageLauncher.launch(arrayOf("image/*"))
                                        showShortcutDialog.value = false
                                    }) { Text("Custom icon") }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showShortcutDialog.value = false }) {
                                Text("Close")
                            }
                        }
                    )
                }

                // --- Version-Badge unten links
                VersionBadge(
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            } // Box
        }

        @OptIn(ExperimentalFoundationApi::class)
        @Composable
        fun ListGameItem(
            gameModel: GameModel,
            viewModel: HomeViewModel,
            showAppActions: MutableState<Boolean>,
            showLoading: MutableState<Boolean>,
            selectedModel: MutableState<GameModel?>,
            showError: MutableState<String>
        ) {
            remember { selectedModel }
            val color =
                if (selectedModel.value == gameModel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface

            val decoder = Base64.getDecoder()
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .combinedClickable(
                        onClick = {
                            if (viewModel.mainViewModel?.selected != null) {
                                showAppActions.value = false
                                viewModel.mainViewModel.apply { selected = null }
                                selectedModel.value = null
                            } else if (gameModel.titleId.isNullOrEmpty()
                                || gameModel.titleId != "0000000000000000"
                                || gameModel.type == FileType.Nro
                            ) {
                                thread {
                                    showLoading.value = true
                                    val success = viewModel.mainViewModel?.loadGame(gameModel) ?: false
                                    if (success == 1) {
                                        launchOnUiThread { viewModel.mainViewModel?.navigateToGame() }
                                    } else {
                                        if (success == -2)
                                            showError.value =
                                                "Error loading update. Please re-add update file"
                                        gameModel.close()
                                    }
                                    showLoading.value = false
                                }
                            }
                        },
                        onLongClick = {
                            viewModel.mainViewModel?.selected = gameModel
                            showAppActions.value = true
                            selectedModel.value = gameModel
                        }
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row {
                        if (!gameModel.titleId.isNullOrEmpty()
                            && (gameModel.titleId != "0000000000000000" || gameModel.type == FileType.Nro)
                        ) {
                            if (gameModel.icon?.isNotEmpty() == true) {
                                val pic = decoder.decode(gameModel.icon)
                                val size = ListImageSize / Resources.getSystem().displayMetrics.density
                                Image(
                                    bitmap = BitmapFactory.decodeByteArray(pic, 0, pic.size).asImageBitmap(),
                                    contentDescription = gameModel.titleName + " icon",
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .width(size.roundToInt().dp)
                                        .height(size.roundToInt().dp)
                                )
                            } else if (gameModel.type == FileType.Nro) NROIcon()
                            else NotAvailableIcon()
                        } else NotAvailableIcon()
                        Column {
                            Text(text = gameModel.titleName ?: "")
                            Text(text = gameModel.developer ?: "")
                            Text(text = gameModel.titleId ?: "")
                        }
                    }
                    Column {
                        Text(text = gameModel.version ?: "")
                        Text(text = String.format("%.3f", gameModel.fileSize))
                    }
                }
            }
        }

        @OptIn(ExperimentalFoundationApi::class)
        @Composable
        fun GridGameItem(
            gameModel: GameModel,
            viewModel: HomeViewModel,
            showAppActions: MutableState<Boolean>,
            showLoading: MutableState<Boolean>,
            selectedModel: MutableState<GameModel?>,
            showError: MutableState<String>
        ) {
            remember { selectedModel }
            val color =
                if (selectedModel.value == gameModel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface

            val decoder = Base64.getDecoder()
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .combinedClickable(
                        onClick = {
                            if (viewModel.mainViewModel?.selected != null) {
                                showAppActions.value = false
                                viewModel.mainViewModel.apply { selected = null }
                                selectedModel.value = gameModel
                            } else if (gameModel.titleId.isNullOrEmpty()
                                || gameModel.titleId != "0000000000000000"
                                || gameModel.type == FileType.Nro
                            ) {
                                thread {
                                    showLoading.value = true
                                    val success = viewModel.mainViewModel?.loadGame(gameModel) ?: false
                                    if (success == 1) {
                                        launchOnUiThread { viewModel.mainViewModel?.navigateToGame() }
                                    } else {
                                        if (success == -2)
                                            showError.value =
                                                "Error loading update. Please re-add update file"
                                        gameModel.close()
                                    }
                                    showLoading.value = false
                                }
                            }
                        },
                        onLongClick = {
                            viewModel.mainViewModel?.selected = gameModel
                            showAppActions.value = true
                            selectedModel.value = gameModel
                        }
                    )
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    if (!gameModel.titleId.isNullOrEmpty()
                        && (gameModel.titleId != "0000000000000000" || gameModel.type == FileType.Nro)
                    ) {
                        if (gameModel.icon?.isNotEmpty() == true) {
                            val pic = decoder.decode(gameModel.icon)
                            Image(
                                bitmap = BitmapFactory.decodeByteArray(pic, 0, pic.size).asImageBitmap(),
                                contentDescription = gameModel.titleName + " icon",
                                modifier = Modifier
                                    .padding(0.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .align(Alignment.CenterHorizontally)
                            )
                        } else if (gameModel.type == FileType.Nro) NROIcon()
                        else NotAvailableIcon()
                    } else NotAvailableIcon()
                    Text(
                        text = gameModel.titleName ?: "N/A",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .basicMarquee()
                    )
                }
            }
        }

        @Composable
        fun NotAvailableIcon() {
            val size = ListImageSize / Resources.getSystem().displayMetrics.density
            Icon(
                Icons.Filled.Add,
                contentDescription = "N/A",
                modifier = Modifier
                    .padding(end = 8.dp)
                    .width(size.roundToInt().dp)
                    .height(size.roundToInt().dp)
            )
        }

        @Composable
        fun NROIcon() {
            val size = ListImageSize / Resources.getSystem().displayMetrics.density
            Image(
                painter = painterResource(id = R.drawable.icon_nro),
                contentDescription = "NRO",
                modifier = Modifier
                    .padding(end = 8.dp)
                    .width(size.roundToInt().dp)
                    .height(size.roundToInt().dp)
            )
        }
    }

    @Preview
    @Composable
    fun HomePreview() {
        Home(isPreview = true)
    }
}
