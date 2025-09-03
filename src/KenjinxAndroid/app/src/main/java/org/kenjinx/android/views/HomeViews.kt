package org.kenjinx.android.views

import android.app.Activity
import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.content.pm.ActivityInfo
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import org.kenjinx.android.viewmodels.FileType
import org.kenjinx.android.viewmodels.GameModel
import org.kenjinx.android.viewmodels.HomeViewModel
import org.kenjinx.android.viewmodels.QuickSettings
import org.kenjinx.android.widgets.SimpleAlertDialog
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import android.content.ComponentName


class HomeViews {
    companion object {
        const val ListImageSize = 150
        const val GridImageSize = 300

        private const val PREFS_NAME = "kenjinx_prefs"
        private const val PREF_SKIP_SHORTCUT_INSTR = "skip_shortcut_instruction"

        // ---------- Shortcut-Utils (lokal in dieser Datei) ----------
        private fun suggestLabelFromUri(uri: Uri): String {
            val last = uri.lastPathSegment ?: return "Start Game"
            val raw = last.substringAfterLast("%2F").substringAfterLast("/")
            return Uri.decode(raw).ifBlank { "Start Game" }
        }

        private fun persistReadWrite(activity: Activity, uri: Uri) {
            val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try { activity.contentResolver.takePersistableUriPermission(uri, rw) } catch (_: Exception) {}
            // Nur dem EIGENEN Paket Rechte geben – nicht hart "org.kenjinx.android"
            try { activity.grantUriPermission(activity.packageName, uri, rw) } catch (_: Exception) {}
        }


        private fun loadBitmapFromUri(activity: Activity, uri: Uri): Bitmap? =
            try { activity.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) } }
            catch (_: Exception) { null }

        private fun pinShortcutForGame(
            activity: Activity,
            gameUri: Uri,
            label: String,
            bmp: Bitmap?
        ): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
            val sm = activity.getSystemService(ShortcutManager::class.java) ?: return false

            val launchIntent = Intent(Intent.ACTION_VIEW).apply {
                // Ziel immer das aktuell laufende Paket & MainActivity
                component = ComponentName(activity, org.kenjinx.android.MainActivity::class.java)
                setPackage(activity.packageName)

                setDataAndType(gameUri, activity.contentResolver.getType(gameUri) ?: "*/*")
                clipData = ClipData.newUri(activity.contentResolver, "GameUri", gameUri)
                putExtra("bootPath", gameUri.toString())

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }

            val icon = bmp?.let { Icon.createWithBitmap(it) }
                ?: Icon.createWithResource(activity, R.mipmap.ic_launcher)

            val shortcut = ShortcutInfo.Builder(
                activity,
                "kenji_game_${gameUri.hashCode()}"
            )
                .setShortLabel(label.take(24))
                .setLongLabel(label)
                .setIcon(icon)
                .setIntent(launchIntent)
                .build()

            // --- Portrait-Workaround mit Touch-Erkennung + Fallback ---
            val prev = activity.requestedOrientation
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

            val handler = Handler(Looper.getMainLooper())
            var restored = false

            fun restoreOrientation() {
                if (restored) return
                restored = true
                activity.requestedOrientation = prev
            }

            // 5s Fallback (falls kein Touch erkannt wird)
            val fallback = Runnable { restoreOrientation() }
            handler.postDelayed(fallback, 5000L)

            // Bei erstem Touch -> noch 2s warten, dann zurückdrehen
            val decorView = activity.window?.decorView
            decorView?.setOnTouchListener { v, event ->
                if (event?.action == MotionEvent.ACTION_DOWN) {
                    // Fallback abbrechen und verzögert zurückstellen
                    handler.removeCallbacks(fallback)
                    handler.postDelayed({ restoreOrientation() }, 2000L)
                    // Listener nur einmal
                    v.setOnTouchListener(null)
                }
                false // Event nicht verbrauchen
            }
            // -----------------------------------------------------------

            return if (sm.isRequestPinShortcutSupported) {
                val successIntent = sm.createShortcutResultIntent(shortcut)
                val cb = PendingIntent.getBroadcast(
                    activity,
                    0,
                    successIntent,
                    PendingIntent.FLAG_IMMUTABLE
                ).intentSender
                sm.requestPinShortcut(shortcut, cb)
                true
            } else {
                sm.addDynamicShortcuts(listOf(shortcut))
                true
            }
        }

        // ------------------------------------------------------------

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
            var isFabVisible by remember { mutableStateOf(true)}
            val isNavigating = remember { mutableStateOf(false) }

            // --- State für Shortcut-Flow (Compose) ---
            val context = LocalContext.current
            val activity = context as? Activity
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

            var pendingGameUri by remember { mutableStateOf<Uri?>(null) }
            var showShortcutNameDialog by remember { mutableStateOf(false) }
            var shortcutLabel by remember { mutableStateOf("") }

            // "How-to" Dialog + Checkbox
            var showInstructionDialog by remember { mutableStateOf(false) }
            var dontShowAgain by rememberSaveable { mutableStateOf(false) }

            // --- Default Game Folder aus MainViewModel bereitstellen (falls exposed) ---
            val defaultGameTreeUri: Uri? = remember {
                // TODO: Stelle sicher, dass MainViewModel eine Uri? liefert, z.B. mainViewModel.defaultGameFolderUri
                // Falls du schon eine Pref/Setting hast, einfach hier auslesen und in Uri.parse(...) wandeln.
                viewModel.mainViewModel?.defaultGameFolderUri
            }

            // Intent-basierter Picker, damit wir INITIAL_URI setzen können
            val pickGameLauncher = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
                val dataUri = result.data?.data
                if (dataUri != null) {
                    pendingGameUri = dataUri
                    shortcutLabel = suggestLabelFromUri(dataUri)
                    showShortcutNameDialog = true
                    // Merke den zuletzt benutzten Ort (auch ein Dokument ist ok als EXTRA_INITIAL_URI)
                    viewModel.mainViewModel?.defaultGameFolderUri = dataUri
                    // (4) Optional: Lese-/Schreibrechte sofort persistieren
                    try {
                        activity?.contentResolver?.takePersistableUriPermission(
                            dataUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    } catch (_: Exception) {}

                }
            }

            val pickIconLauncher = rememberLauncherForActivityResult(OpenDocument()) { iconUri ->
                val act = activity
                val gameUri = pendingGameUri
                if (act != null && gameUri != null) {
                    val bmp = iconUri?.let { loadBitmapFromUri(act, it) }
                    persistReadWrite(act, gameUri)
                    val ok = pinShortcutForGame(
                        act,
                        gameUri,
                        shortcutLabel.ifBlank { suggestLabelFromUri(gameUri) },
                        bmp
                    )
                    Toast.makeText(act, if (ok) "Shortcut created." else "Shortcut failed.", Toast.LENGTH_SHORT).show()
                }
                showShortcutNameDialog = false
                pendingGameUri = null
            }
            // ------------------------------------------

            val nestedScrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (available.y < -1) {
                            isFabVisible = false
                        }
                        if (available.y > 1) {
                            isFabVisible = true
                        }
                        return Offset.Zero
                    }
                }
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp,vertical = 8.dp)
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
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = "Settings"
                                )
                            }
                        }

                        OutlinedTextField(
                            value = query.value,
                            onValueChange = {
                                query.value = it
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            placeholder = {
                                Text(
                                    "Search...",
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = "Search"
                                )
                            },
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
                    }
                },
                floatingActionButtonPosition = FabPosition.End
            ) { contentPadding ->
                Column(modifier = Modifier.padding(contentPadding)) {

                    // >>> Grid/List-UI + Shortcut-Button als Overlay
                    Box(modifier = Modifier.fillMaxSize()) {
                        // -- Bestehender Inhalt --
                        val list = remember { viewModel.gameList }
                        val isLoading = remember { viewModel.isLoading }

                        viewModel.filter(query.value)

                        if (!isPreview) {
                            var settings = QuickSettings(viewModel.activity!!)

                            if (isLoading.value) {
                                Box(modifier = Modifier.fillMaxSize())
                                {
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
                                    val size =
                                        GridImageSize / Resources.getSystem().displayMetrics.density
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
                                                        .contains(query.value))
                                                )
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
                                } else {
                                    LazyColumn(Modifier.fillMaxSize()) {
                                        items(list) {
                                            it.titleName?.apply {
                                                if (this.isNotEmpty() && (query.value.trim()
                                                        .isEmpty() || this.lowercase(
                                                        Locale.getDefault()
                                                    )
                                                        .contains(query.value))
                                                )
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

                        // -- Shortcut-Button unten links als Overlay --
                        Button(
                            onClick = {
                                val skip = prefs.getBoolean(PREF_SKIP_SHORTCUT_INSTR, false)
                                if (skip) {
                                    // Direkt starten
                                    (context as? Activity)?.let {
                                        // Starte Dateiauswahl
                                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                            type = "*/*"
                                            // Falls du nur Switch-Container willst: type = "application/octet-stream"
                                            // oder setMimeTypes(new String[] { "application/x-nsp", "application/x-xci" }) – falls du Custom-Typen nutzt.

                                            // Default-Ordner vorschlagen (falls vorhanden und vom SAF akzeptiert)
                                            if (defaultGameTreeUri != null) {
                                                putExtra(DocumentsContract.EXTRA_INITIAL_URI, defaultGameTreeUri)
                                            }

                                            addFlags(
                                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                                                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                            )
                                        }
                                        pickGameLauncher.launch(intent)

                                    }
                                } else {
                                    // Anleitung zuerst
                                    dontShowAgain = false
                                    showInstructionDialog = true
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                        ) {
                            Text("Create shortcut")
                        }
                    }
                }

                // Dialogs & Sheets (bestehender Code)
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

                // --- Name-Dialog für Shortcut ---
                if (showShortcutNameDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showShortcutNameDialog = false
                            pendingGameUri = null
                        },
                        title = { Text("Create shortcut") },
                        text = {
                            OutlinedTextField(
                                value = shortcutLabel,
                                onValueChange = { shortcutLabel = it },
                                label = { Text("Shortcut name") }
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val act = activity
                                val gameUri = pendingGameUri
                                if (act != null && gameUri != null) {
                                    persistReadWrite(act, gameUri)
                                    val ok = pinShortcutForGame(
                                        act,
                                        gameUri,
                                        shortcutLabel.ifBlank { suggestLabelFromUri(gameUri) },
                                        null // -> App-Icon verwenden
                                    )
                                    Toast.makeText(act, if (ok) "Shortcut created." else "Shortcut failed.", Toast.LENGTH_SHORT).show()
                                }
                                showShortcutNameDialog = false
                                pendingGameUri = null
                            }) { Text("Use app icon") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                // Benutzerdefiniertes Icon auswählen
                                pickIconLauncher.launch(arrayOf("image/*"))
                            }) { Text("Pick custom icon") }
                        }
                    )
                }

                // --- Instruction Dialog für "Create shortcut" ---
                if (showInstructionDialog) {
                    AlertDialog(
                        onDismissRequest = { showInstructionDialog = false },
                        title = { Text("How to create a shortcut") },
                        text = {
                            Column {
                                Text(
                                    "After pressing OK, you will be asked by your launcher to confirm shortcut creation.\n\n" +
                                        "1) Choose the game file you want.\n" +
                                        "2) Enter a name and optionally set an icon.\n" +
                                        "3) Press 'Add' in the launcher pop-up to finish."
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 12.dp)
                                ) {
                                    Checkbox(
                                        checked = dontShowAgain,
                                        onCheckedChange = { dontShowAgain = it }
                                    )
                                    Text("Don't show again")
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (dontShowAgain) {
                                    prefs.edit().putBoolean(PREF_SKIP_SHORTCUT_INSTR, true).apply()
                                }
                                showInstructionDialog = false
                                // Starte Dateiauswahl
                                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    type = "*/*"
                                    if (defaultGameTreeUri != null) {
                                        putExtra(DocumentsContract.EXTRA_INITIAL_URI, defaultGameTreeUri)
                                    }
                                    addFlags(
                                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                    )
                                }
                                pickGameLauncher.launch(intent)

                            }) { Text("OK") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showInstructionDialog = false }) { Text("Cancel") }
                        }
                    )
                }
            }

            if(viewModel.mainViewModel?.loadGameModel?.value != null)
                LaunchedEffect(viewModel.mainViewModel.loadGameModel.value) {
                    if (viewModel.mainViewModel.bootPath.value == "gameItem_${viewModel.mainViewModel.loadGameModel.value!!.titleName}") {
                        viewModel.mainViewModel.bootPath.value = null

                        thread {
                            showLoading.value = true
                            val success =
                                viewModel.mainViewModel.loadGame(
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
                                    showError.value =
                                        "Error loading update. Please re-add update file"
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
                                IconButton(onClick = {
                                    if (viewModel.mainViewModel?.selected != null) {
                                        thread {
                                            showLoading.value = true
                                            val success =
                                                viewModel.mainViewModel.loadGame(viewModel.mainViewModel.selected!!)
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
                                val showAppMenu = remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = {
                                        showAppMenu.value = true
                                    }) {
                                        Icon(
                                            Icons.Filled.Menu,
                                            contentDescription = "Menu"
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showAppMenu.value,
                                        onDismissRequest = { showAppMenu.value = false }) {
                                        DropdownMenuItem(text = {
                                            Text(text = "Clear PPTC Cache")
                                        }, onClick = {
                                            showAppMenu.value = false
                                            viewModel.mainViewModel?.clearPptcCache(
                                                viewModel.mainViewModel.selected?.titleId ?: ""
                                            )
                                        })
                                        DropdownMenuItem(text = {
                                            Text(text = "Purge Shader Cache")
                                        }, onClick = {
                                            showAppMenu.value = false
                                            viewModel.mainViewModel?.purgeShaderCache(
                                                viewModel.mainViewModel.selected?.titleId ?: ""
                                            )
                                        })
                                        DropdownMenuItem(text = {
                                            Text(text = "Delete All Cache")
                                        }, onClick = {
                                            showAppMenu.value = false
                                            viewModel.mainViewModel?.deleteCache(
                                                viewModel.mainViewModel.selected?.titleId ?: ""
                                            )
                                        })
                                        DropdownMenuItem(text = {
                                            Text(text = "Manage Updates")
                                        }, onClick = {
                                            showAppMenu.value = false
                                            openTitleUpdateDialog.value = true
                                        })
                                        DropdownMenuItem(text = {
                                            Text(text = "Manage DLC")
                                        }, onClick = {
                                            showAppMenu.value = false
                                            openDlcDialog.value = true
                                        })
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
            remember {
                selectedModel
            }
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
                                viewModel.mainViewModel.apply {
                                    selected = null
                                }
                                selectedModel.value = null
                            } else if (gameModel.titleId.isNullOrEmpty() || gameModel.titleId != "0000000000000000" || gameModel.type == FileType.Nro) {
                                thread {
                                    showLoading.value = true
                                    val success =
                                        viewModel.mainViewModel?.loadGame(gameModel) ?: false
                                    if (success == 1) {
                                        launchOnUiThread {
                                            viewModel.mainViewModel?.navigateToGame()
                                        }
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
                        })
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row {
                        if (!gameModel.titleId.isNullOrEmpty() && (gameModel.titleId != "0000000000000000" || gameModel.type == FileType.Nro)) {
                            if (gameModel.icon?.isNotEmpty() == true) {
                                val pic = decoder.decode(gameModel.icon)
                                val size =
                                    ListImageSize / Resources.getSystem().displayMetrics.density
                                Image(
                                    bitmap = BitmapFactory.decodeByteArray(pic, 0, pic.size)
                                        .asImageBitmap(),
                                    contentDescription = gameModel.titleName + " icon",
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .width(size.roundToInt().dp)
                                        .height(size.roundToInt().dp)
                                )
                            } else if (gameModel.type == FileType.Nro)
                                NROIcon()
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
            remember {
                selectedModel
            }
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
                                viewModel.mainViewModel.apply {
                                    selected = null
                                }
                                selectedModel.value = null
                            } else if (gameModel.titleId.isNullOrEmpty() || gameModel.titleId != "0000000000000000" || gameModel.type == FileType.Nro) {
                                thread {
                                    showLoading.value = true
                                    val success =
                                        viewModel.mainViewModel?.loadGame(gameModel) ?: false
                                    if (success == 1) {
                                        launchOnUiThread {
                                            viewModel.mainViewModel?.navigateToGame()
                                        }
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
                        })
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    if (!gameModel.titleId.isNullOrEmpty() && (gameModel.titleId != "0000000000000000" || gameModel.type == FileType.Nro)) {
                        if (gameModel.icon?.isNotEmpty() == true) {
                            val pic = decoder.decode(gameModel.icon)
                            Image(
                                bitmap = BitmapFactory.decodeByteArray(pic, 0, pic.size)
                                    .asImageBitmap(),
                                contentDescription = gameModel.titleName + " icon",
                                modifier = Modifier
                                    .padding(0.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .align(Alignment.CenterHorizontally)
                            )
                        } else if (gameModel.type == FileType.Nro)
                            NROIcon()
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
