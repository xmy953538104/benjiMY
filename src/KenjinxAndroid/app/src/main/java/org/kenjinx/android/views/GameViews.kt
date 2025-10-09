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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.draw.alpha
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
import java.util.Locale
import kotlin.math.roundToInt

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
                val progressValue = remember { mutableFloatStateOf(0.0f) }
                val progress = remember { mutableStateOf("Loading") }

                // --- Read overlay settings
                val overlayPositionState = remember {
                    mutableStateOf(QuickSettings(mainViewModel.activity).overlayMenuPosition)
                }
                val overlayOpacityState = remember {
                    mutableFloatStateOf(QuickSettings(mainViewModel.activity).overlayMenuOpacity.coerceIn(0f, 1f))
                }

                // Auxiliary mapping position → alignment
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

                    // --- Button at any corner/edge + transparency
                    Row(
                        modifier = Modifier
                            .align(overlayAlignment())
                            .padding(8.dp)
                            .alpha(overlayOpacityState.floatValue) // 0f = invisible, but still clickable
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
                            alignment = overlayAlignment(), // --- Panel in the same position
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
                                }
                            }
                        }
                    }
                }

                val showBackNotice = remember { mutableStateOf(false) }

                // If the software keyboard is open, catch Back and close ONLY the dialog.
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
                    progressValue = progressValue.floatValue
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
                        Text(text = "${String.format(Locale.getDefault(), "%.3f", fifo.doubleValue)} %")
                        Text(text = "${String.format(Locale.getDefault(), "%.3f", gameFps.doubleValue)} FPS")
                        Text(text = "${String.format(Locale.getDefault(), "%.3f", gameTimeVal)} ms")
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
