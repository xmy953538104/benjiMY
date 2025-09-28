package org.kenjinx.android.views

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import androidx.compose.ui.draw.alpha // ← NEU
import compose.icons.CssGgIcons
import compose.icons.cssggicons.ToolbarBottom
import org.kenjinx.android.GameController
import org.kenjinx.android.GameHost
import org.kenjinx.android.Icons
import org.kenjinx.android.MainActivity
import org.kenjinx.android.KenjinxNative
import org.kenjinx.android.viewmodels.MainViewModel
import org.kenjinx.android.viewmodels.QuickSettings
import org.kenjinx.android.viewmodels.VSyncMode
import org.kenjinx.android.widgets.SimpleAlertDialog
import kotlin.math.roundToInt

// MINIMAL ADD:
import android.net.Uri
import android.widget.Toast

class GameViews {
    companion object {
        @Composable
        fun Main() {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                GameView(mainViewModel = MainActivity.mainViewModel!!)
            }
        }

        @Composable
        fun GameView(mainViewModel: MainViewModel) {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        GameHost(context, mainViewModel)
                    }
                )
                GameOverlay(mainViewModel)
            }
        }

        @OptIn(ExperimentalMaterial3Api::class)
        @Composable
        fun GameOverlay(mainViewModel: MainViewModel) {
            Box(modifier = Modifier.fillMaxSize()) {
                val showStats = remember { mutableStateOf(false) }
                val showController = remember { mutableStateOf(QuickSettings(mainViewModel.activity).useVirtualController) }
                val vSyncMode = remember { mutableStateOf(QuickSettings(mainViewModel.activity).vSyncMode) }
                val enableMotion = remember { mutableStateOf(QuickSettings(mainViewModel.activity).enableMotion) }
                val showMore = remember { mutableStateOf(false) }
                val showLoading = remember { mutableStateOf(true) }
                val progressValue = remember { mutableStateOf(0.0f) }
                val progress = remember { mutableStateOf("Loading") }

                // --- NEU: Overlay-Settings lesen
                val overlayPositionState = remember {
                    mutableStateOf(QuickSettings(mainViewModel.activity).overlayMenuPosition)
                }
                val overlayOpacityState = remember {
                    mutableStateOf(QuickSettings(mainViewModel.activity).overlayMenuOpacity.coerceIn(0f, 1f))
                }

                // Hilfs-Mapping Position → Alignment
                fun overlayAlignment(): Alignment {
                    return when (overlayPositionState.value) {
                        QuickSettings.OverlayMenuPosition.BottomMiddle -> Alignment.BottomCenter
                        QuickSettings.OverlayMenuPosition.BottomLeft   -> Alignment.BottomStart
                        QuickSettings.OverlayMenuPosition.BottomRight  -> Alignment.BottomEnd
                        QuickSettings.OverlayMenuPosition.TopMiddle    -> Alignment.TopCenter
                        QuickSettings.OverlayMenuPosition.TopLeft      -> Alignment.TopStart
                        QuickSettings.OverlayMenuPosition.TopRight     -> Alignment.TopEnd
                    }
                }


                // helper: slot label
                fun qsLabel(name: String?, slot: Int): String =
                    if (name.isNullOrBlank()) "Slot $slot" else name

                if (showStats.value) {
                    GameStats(mainViewModel)
                }

                mainViewModel.setProgressStates(showLoading, progressValue, progress)

                // touch surface
                Surface(color = Color.Transparent, modifier = Modifier
                    .fillMaxSize()
                    .padding(0.dp)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (showController.value)
                                    continue

                                val change = event
                                    .component1()
                                    .firstOrNull()
                                change?.apply {
                                    val position = this.position

                                    when (event.type) {
                                        PointerEventType.Press -> {
                                            KenjinxNative.inputSetTouchPoint(
                                                position.x.roundToInt(),
                                                position.y.roundToInt()
                                            )
                                        }
                                        PointerEventType.Release -> {
                                            KenjinxNative.inputReleaseTouchPoint()
                                        }
                                        PointerEventType.Move -> {
                                            KenjinxNative.inputSetTouchPoint(
                                                position.x.roundToInt(),
                                                position.y.roundToInt()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }) {
                }

                if (!showLoading.value) {
                    GameController.Compose(mainViewModel)

                    // --- NEU: Button an frei wählbarer Ecke/Kante + Transparenz
                    Row(
                        modifier = Modifier
                            .align(overlayAlignment())
                            .padding(8.dp)
                            .alpha(overlayOpacityState.value) // 0f = unsichtbar, aber weiter klickbar
                    ) {
                        IconButton(modifier = Modifier.padding(4.dp), onClick = {
                            showMore.value = true
                        }) {
                            Icon(
                                imageVector = CssGgIcons.ToolbarBottom,
                                contentDescription = "Open Panel"
                            )
                        }
                    }

                    if (showMore.value) {
                        Popup(
                            alignment = overlayAlignment(), // --- NEU: Panel an gleicher Position
                            onDismissRequest = { showMore.value = false }
                        ) {
                            Surface(
                                modifier = Modifier.padding(16.dp),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        IconButton(modifier = Modifier.padding(4.dp), onClick = {
                                            showMore.value = false
                                            showController.value = !showController.value
                                            KenjinxNative.inputReleaseTouchPoint()
                                            mainViewModel.controller?.setVisible(showController.value)
                                        }) {
                                            Icon(
                                                imageVector = Icons.videoGame(),
                                                tint = if (showController.value) Color.Green else Color.Red,
                                                contentDescription = "Toggle Virtual Pad"
                                            )
                                        }
                                        IconButton(modifier = Modifier.padding(4.dp), onClick = {
                                            showMore.value = false
                                            if(vSyncMode.value == VSyncMode.Switch) {
                                                vSyncMode.value= VSyncMode.Unbounded
                                            } else {
                                                vSyncMode.value= VSyncMode.Switch
                                            }
                                            KenjinxNative.graphicsRendererSetVsync(
                                                vSyncMode.value.ordinal
                                            )
                                        }) {
                                            Icon(
                                                imageVector = Icons.vSync(),
                                                tint = if (vSyncMode.value == VSyncMode.Switch) Color.Green else Color.Red,
                                                contentDescription = "Toggle VSync"
                                            )
                                        }
                                        IconButton(modifier = Modifier.padding(4.dp), onClick = {
                                            showMore.value = false
                                            enableMotion.value = !enableMotion.value
                                            val settings = QuickSettings(mainViewModel.activity)
                                            settings.enableMotion = enableMotion.value
                                            settings.save()
                                            if (enableMotion.value)
                                                mainViewModel.motionSensorManager?.register()
                                            else
                                                mainViewModel.motionSensorManager?.unregister()
                                        }) {
                                            Icon(
                                                imageVector = Icons.motionSensor(),
                                                tint = if (enableMotion.value) Color.Green else Color.Red,
                                                contentDescription = "Toggle Motion Sensor"
                                            )
                                        }
                                        IconButton(modifier = Modifier.padding(4.dp), onClick = {
                                            showMore.value = false
                                            showStats.value = !showStats.value
                                        }) {
                                            Icon(
                                                imageVector = Icons.barChart(),
                                                tint = if (showStats.value) Color.Green else Color.Red,
                                                contentDescription = "Toggle Game Stats"
                                            )
                                        }
                                    }

                                    // MINIMAL ADD: Amiibo slot buttons
                                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                                        Text(text = "Amiibo Slots")

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            androidx.compose.material3.Button(onClick = {
                                                val qs = QuickSettings(mainViewModel.activity)
                                                val u = qs.amiibo1Uri
                                                val name = qs.amiibo1Name ?: "Slot 1"
                                                if (u.isNullOrEmpty()) {
                                                    Toast.makeText(mainViewModel.activity, "Slot 1 is empty.", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    try {
                                                        val bytes = mainViewModel.activity.contentResolver.openInputStream(Uri.parse(u))?.use { it.readBytes() }
                                                        if (bytes != null && bytes.isNotEmpty()) {
                                                            val ok = KenjinxNative.amiiboLoadBin(bytes, bytes.size)
                                                            if (ok) Toast.makeText(mainViewModel.activity, "Loaded: $name", Toast.LENGTH_SHORT).show()
                                                            else     Toast.makeText(mainViewModel.activity, "Load failed (check log)", Toast.LENGTH_SHORT).show()
                                                        } else Toast.makeText(mainViewModel.activity, "File not readable.", Toast.LENGTH_SHORT).show()
                                                    } catch (t: Throwable) { Toast.makeText(mainViewModel.activity, "Error: ${t.message}", Toast.LENGTH_SHORT).show() }
                                                }
                                            }) { androidx.compose.material3.Text(qsLabel(QuickSettings(mainViewModel.activity).amiibo1Name, 1)) }

                                            androidx.compose.material3.Button(onClick = {
                                                val qs = QuickSettings(mainViewModel.activity)
                                                val u = qs.amiibo2Uri
                                                val name = qs.amiibo2Name ?: "Slot 2"
                                                if (u.isNullOrEmpty()) {
                                                    Toast.makeText(mainViewModel.activity, "Slot 2 is empty.", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    try {
                                                        val bytes = mainViewModel.activity.contentResolver.openInputStream(Uri.parse(u))?.use { it.readBytes() }
                                                        if (bytes != null && bytes.isNotEmpty()) {
                                                            val ok = KenjinxNative.amiiboLoadBin(bytes, bytes.size)
                                                            if (ok) Toast.makeText(mainViewModel.activity, "Loaded: $name", Toast.LENGTH_SHORT).show()
                                                            else     Toast.makeText(mainViewModel.activity, "Load failed (check log)", Toast.LENGTH_SHORT).show()
                                                        } else Toast.makeText(mainViewModel.activity, "File not readable.", Toast.LENGTH_SHORT).show()
                                                    } catch (t: Throwable) { Toast.makeText(mainViewModel.activity, "Error: ${t.message}", Toast.LENGTH_SHORT).show() }
                                                }
                                            }) { androidx.compose.material3.Text(qsLabel(QuickSettings(mainViewModel.activity).amiibo2Name, 2)) }

                                            androidx.compose.material3.Button(onClick = {
                                                val qs = QuickSettings(mainViewModel.activity)
                                                val u = qs.amiibo3Uri
                                                val name = qs.amiibo3Name ?: "Slot 3"
                                                if (u.isNullOrEmpty()) {
                                                    Toast.makeText(mainViewModel.activity, "Slot 3 is empty.", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    try {
                                                        val bytes = mainViewModel.activity.contentResolver.openInputStream(Uri.parse(u))?.use { it.readBytes() }
                                                        if (bytes != null && bytes.isNotEmpty()) {
                                                            val ok = KenjinxNative.amiiboLoadBin(bytes, bytes.size)
                                                            if (ok) Toast.makeText(mainViewModel.activity, "Loaded: $name", Toast.LENGTH_SHORT).show()
                                                            else     Toast.makeText(mainViewModel.activity, "Load failed (check log)", Toast.LENGTH_SHORT).show()
                                                        } else Toast.makeText(mainViewModel.activity, "File not readable.", Toast.LENGTH_SHORT).show()
                                                    } catch (t: Throwable) { Toast.makeText(mainViewModel.activity, "Error: ${t.message}", Toast.LENGTH_SHORT).show() }
                                                }
                                            }) { androidx.compose.material3.Text(qsLabel(QuickSettings(mainViewModel.activity).amiibo3Name, 3)) }
                                        }

                                        Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            androidx.compose.material3.Button(onClick = {
                                                val qs = QuickSettings(mainViewModel.activity)
                                                val u = qs.amiibo4Uri
                                                val name = qs.amiibo4Name ?: "Slot 4"
                                                if (u.isNullOrEmpty()) {
                                                    Toast.makeText(mainViewModel.activity, "Slot 4 is empty.", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    try {
                                                        val bytes = mainViewModel.activity.contentResolver.openInputStream(Uri.parse(u))?.use { it.readBytes() }
                                                        if (bytes != null && bytes.isNotEmpty()) {
                                                            val ok = KenjinxNative.amiiboLoadBin(bytes, bytes.size)
                                                            if (ok) Toast.makeText(mainViewModel.activity, "Loaded: $name", Toast.LENGTH_SHORT).show()
                                                            else     Toast.makeText(mainViewModel.activity, "Load failed (check log)", Toast.LENGTH_SHORT).show()
                                                        } else Toast.makeText(mainViewModel.activity, "File not readable.", Toast.LENGTH_SHORT).show()
                                                    } catch (t: Throwable) { Toast.makeText(mainViewModel.activity, "Error: ${t.message}", Toast.LENGTH_SHORT).show() }
                                                }
                                            }) { androidx.compose.material3.Text(qsLabel(QuickSettings(mainViewModel.activity).amiibo4Name, 4)) }

                                            androidx.compose.material3.Button(onClick = {
                                                val qs = QuickSettings(mainViewModel.activity)
                                                val u = qs.amiibo5Uri
                                                val name = qs.amiibo5Name ?: "Slot 5"
                                                if (u.isNullOrEmpty()) {
                                                    Toast.makeText(mainViewModel.activity, "Slot 5 is empty.", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    try {
                                                        val bytes = mainViewModel.activity.contentResolver.openInputStream(Uri.parse(u))?.use { it.readBytes() }
                                                        if (bytes != null && bytes.isNotEmpty()) {
                                                            val ok = KenjinxNative.amiiboLoadBin(bytes, bytes.size)
                                                            if (ok) Toast.makeText(mainViewModel.activity, "Loaded: $name", Toast.LENGTH_SHORT).show()
                                                            else     Toast.makeText(mainViewModel.activity, "Load failed (check log)", Toast.LENGTH_SHORT).show()
                                                        } else Toast.makeText(mainViewModel.activity, "File not readable.", Toast.LENGTH_SHORT).show()
                                                    } catch (t: Throwable) { Toast.makeText(mainViewModel.activity, "Error: ${t.message}", Toast.LENGTH_SHORT).show() }
                                                }
                                            }) { androidx.compose.material3.Text(qsLabel(QuickSettings(mainViewModel.activity).amiibo5Name, 5)) }

                                            androidx.compose.material3.OutlinedButton(onClick = {
                                                KenjinxNative.amiiboClear()
                                                Toast.makeText(mainViewModel.activity, "Amiibo cleared", Toast.LENGTH_SHORT).show()
                                            }) { androidx.compose.material3.Text("Clear") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val showBackNotice = remember { mutableStateOf(false) }

                // NEU: Wenn das Software-Keyboard offen ist, fange Back ab und schließe NUR den Dialog.
                val uiHandler = mainViewModel.activity.uiHandler
                BackHandler(enabled = uiHandler.showMessage.value) {
                    KenjinxNative.uiHandlerSetResponse(false, "")
                    uiHandler.showMessage.value = false
                }
                BackHandler {
                    showBackNotice.value = true
                }

                SimpleAlertDialog.Progress(
                    showDialog = showLoading,
                    progressText = progress.value,
                    progressValue = progressValue.value
                )

                SimpleAlertDialog.Confirmation(
                    showDialog = showBackNotice,
                    title = "Exit Game",
                    message = "Are you sure you want to exit the game? All unsaved data will be lost!",
                    confirmText = "Exit Game",
                    dismissText = "Dismiss",
                    onConfirm = {
                        mainViewModel.closeGame()
                        mainViewModel.navController?.popBackStack()
                        mainViewModel.activity.isGameRunning = false
                    }
                )

                mainViewModel.activity.uiHandler.Compose()
            }
        }

        @Composable
        fun GameStats(mainViewModel: MainViewModel) {
            val fifo = remember { mutableDoubleStateOf(0.0) }
            val gameFps = remember { mutableDoubleStateOf(0.0) }
            val gameTime = remember { mutableDoubleStateOf(0.0) }
            val usedMem = remember { mutableIntStateOf(0) }
            val totalMem = remember { mutableIntStateOf(0) }
            val frequencies = remember { mutableListOf<Double>() }

            Surface(
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.background.copy(0.4f)
            ) {
                CompositionLocalProvider(LocalTextStyle provides TextStyle(fontSize = 10.sp)) {
                    Column {
                        var gameTimeVal = 0.0
                        if (!gameTime.doubleValue.isInfinite())
                            gameTimeVal = gameTime.doubleValue
                        Text(text = "${String.format("%.3f", fifo.doubleValue)} %")
                        Text(text = "${String.format("%.3f", gameFps.doubleValue)} FPS")
                        Text(text = "${String.format("%.3f", gameTimeVal)} ms")
                        Box(modifier = Modifier.width(96.dp)) {
                            Column {
                                LazyColumn {
                                    items(count = frequencies.size) { i ->
                                        if (i < frequencies.size) {
                                            val t = frequencies[i]
                                            Row {
                                                Text(modifier = Modifier.padding(2.dp), text = "CPU $i")
                                                Spacer(Modifier.weight(1f))
                                                Text(text = "$t MHz")
                                            }
                                        }
                                    }
                                }
                                Row {
                                    Text(modifier = Modifier.padding(2.dp), text = "Used")
                                    Spacer(Modifier.weight(1f))
                                    Text(text = "${usedMem.intValue} MB")
                                }
                                Row {
                                    Text(modifier = Modifier.padding(2.dp), text = "Total")
                                    Spacer(Modifier.weight(1f))
                                    Text(text = "${totalMem.intValue} MB")
                                }
                            }
                        }
                    }
                }
            }

            mainViewModel.setStatStates(fifo, gameFps, gameTime, usedMem, totalMem, frequencies)
        }
    }
}
