package org.kenjinx.android

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.MutableState
import org.kenjinx.android.viewmodels.GameModel
import org.kenjinx.android.viewmodels.MainViewModel
import kotlin.concurrent.thread

@SuppressLint("ViewConstructor")
class GameHost(context: Context?, private val mainViewModel: MainViewModel) : SurfaceView(context),
    SurfaceHolder.Callback {

    private var isProgressHidden: Boolean = false
    private var progress: MutableState<String>? = null
    private var progressValue: MutableState<Float>? = null
    private var showLoading: MutableState<Boolean>? = null
    private var game: GameModel? = null
    private var _isClosed: Boolean = false
    private var _renderingThreadWatcher: Thread? = null
    private var _height: Int = 0
    private var _width: Int = 0
    private var _updateThread: Thread? = null
    private var _guestThread: Thread? = null
    private var _isInit: Boolean = false
    private var _isStarted: Boolean = false
    private val _nativeWindow: NativeWindow

    private val mainHandler = Handler(Looper.getMainLooper())

    // Stabilizer-State
    private var stabilizerActive = false

    // letzte bekannte Android-Rotation (0,1,2,3)
    private var lastRotation: Int? = null

    // Debounce für Resize-Kick
    private var lastKickAt = 0L

    var currentSurface: Long = -1
        private set

    val currentWindowHandle: Long
        get() = _nativeWindow.nativePointer

    init {
        holder.addCallback(this)
        _nativeWindow = NativeWindow(this)
        mainViewModel.gameHost = this
    }

    private fun ghLog(msg: String) {
        val enabled = BuildConfig.DEBUG && org.kenjinx.android.viewmodels.QuickSettings(mainViewModel.activity).enableDebugLogs
        if (enabled) Log.d("GameHost", msg)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // no-op
    }

    fun setProgress(info: String, progressVal: Float) {
        showLoading?.apply {
            progressValue?.apply { this.value = progressVal }
            progress?.apply { this.value = info }
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (_isClosed) return

        val sizeChanged = (_width != width || _height != height)

        if (sizeChanged) {
            // Surface / Window-Handle neu abfragen und an C# melden
            currentSurface = _nativeWindow.requeryWindowHandle()
            _nativeWindow.swapInterval = 0
            try { KenjinxNative.deviceSetWindowHandle(currentWindowHandle) } catch (_: Throwable) {}
        }

        _width = width
        _height = height

        // Renderer starten (falls noch nicht gestartet)
        start(holder)

        // Größe nicht sofort setzen → Stabilizer übernimmt
        startStabilizedResize(expectedRotation = lastRotation)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // no-op (Renderer lebt in eigenem Thread; schließen via close())
    }

    fun close() {
        _isClosed = true
        _isInit = false
        _isStarted = false

        KenjinxNative.uiHandlerSetResponse(false, "")

        try { _updateThread?.join(200) } catch (_: Throwable) {}
        try { _renderingThreadWatcher?.join(200) } catch (_: Throwable) {}
    }

    private fun start(surfaceHolder: SurfaceHolder) {
        if (_isStarted) return
        _isStarted = true

        game = if (mainViewModel.isMiiEditorLaunched) null else mainViewModel.gameModel

        // Input initialisieren
        KenjinxNative.inputInitialize(width, height)

        val id = mainViewModel.physicalControllerManager?.connect()
        mainViewModel.motionSensorManager?.setControllerId(id ?: -1)

        // Kein initialer "flip"-Sonderfall: wir geben die echte Rotation nach unten
        val currentRot = mainViewModel.activity.display?.rotation
        lastRotation = currentRot
        try {
            KenjinxNative.setSurfaceRotationByAndroidRotation(currentRot)
            // Window-Handle sicherheitshalber durchreichen (falls Surface gerade frisch wurde)
            try { KenjinxNative.deviceSetWindowHandle(currentWindowHandle) } catch (_: Throwable) {}
            // sanfter Kick: identische Größe nochmal setzen
            if (width > 0 && height > 0) {
                try { KenjinxNative.resizeRendererAndInput(width, height) } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}

        val qs = org.kenjinx.android.viewmodels.QuickSettings(mainViewModel.activity)
        try {
            KenjinxNative.graphicsSetFullscreenStretch(qs.stretchToFullscreen)
        } catch (_: Throwable) {}
        _guestThread = thread(start = true, name = "KenjinxGuest") {
            runGame()
        }

        _updateThread = thread(start = true, name = "KenjinxInput/Stats") {
            var c = 0
            while (_isStarted) {
                KenjinxNative.inputUpdate()
                Thread.sleep(1)
                c++
                if (c >= 1000) {
                    if (progressValue?.value == -1f) {
                        progress?.apply {
                            this.value = "Loading ${if (mainViewModel.isMiiEditorLaunched) "Mii Editor" else game?.titleName ?: ""}"
                        }
                    }
                    c = 0
                    mainViewModel.updateStats(
                        KenjinxNative.deviceGetGameFifo(),
                        KenjinxNative.deviceGetGameFrameRate(),
                        KenjinxNative.deviceGetGameFrameTime()
                    )
                }
            }
        }
    }

    private fun runGame() {
        KenjinxNative.graphicsRendererRunLoop()
        game?.close()
    }

    fun setProgressStates(
        showLoading: MutableState<Boolean>?,
        progressValue: MutableState<Float>?,
        progress: MutableState<String>?
    ) {
        this.showLoading = showLoading
        this.progressValue = progressValue
        this.progress = progress
        showLoading?.apply { value = !isProgressHidden }
    }

    fun hideProgressIndicator() {
        isProgressHidden = true
        showLoading?.apply {
            if (value == isProgressHidden) value = !isProgressHidden
        }
    }

    /**
     * Sicheres Setzen der Renderer-/Input-Größe.
     */
    @Synchronized
    private fun safeSetSize(w: Int, h: Int) {
        if (_isClosed) return
        if (w <= 0 || h <= 0) return
        try {
            ghLog("safeSetSize: ${w}x$h (started=$_isStarted)")
            KenjinxNative.graphicsRendererSetSize(w, h)
            if (_isStarted) {
                KenjinxNative.inputSetClientSize(w, h)
            }
        } catch (t: Throwable) {
            Log.e("GameHost", "safeSetSize failed: ${t.message}", t)
        }
    }

    /**
     * Von der Activity bei Rotations-/Layoutwechsel aufgerufen.
     * Erkenne 90°↔270° und erzwinge (debounced) ein Requery/Resize.
     */
    fun onOrientationOrSizeChanged(rotation: Int? = null) {
        if (_isClosed) return

        val old = lastRotation
        lastRotation = rotation

        val isSideFlip = (old == 1 && rotation == 3) || (old == 3 && rotation == 1)

        if (isSideFlip) {
            // 1) NativeRotation melden
            try { KenjinxNative.setSurfaceRotationByAndroidRotation(rotation) } catch (_: Throwable) {}

            // 2) NativeWindow sofort neu abfragen (erzwingt echten Rebind) + Window-Handle an C#
            try {
                currentSurface = _nativeWindow.requeryWindowHandle()
                _nativeWindow.swapInterval = 0
                try { KenjinxNative.deviceSetWindowHandle(currentWindowHandle) } catch (_: Throwable) {}
            } catch (_: Throwable) {}

            // 3) Debounced Kick der identischen Größe (Swapchain/Viewport aktualisieren)
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastKickAt >= 300L) {
                lastKickAt = now
                val w = if (holder.surfaceFrame.width() > 0) holder.surfaceFrame.width() else width
                val h = if (holder.surfaceFrame.height() > 0) holder.surfaceFrame.height() else height
                if (w > 0 && h > 0) {
                    try { KenjinxNative.resizeRendererAndInput(w, h) } catch (_: Throwable) {}
                }
            }
        }

        startStabilizedResize(rotation)
    }

    /**
     * Wartet kurz, bis das Surface seine finalen Maße nach der Drehung hat,
     * prüft Plausibilität (Portrait/Landscape) und setzt erst dann die Größe.
     */
    private fun startStabilizedResize(expectedRotation: Int?) {
        if (_isClosed) return

        // Neustarten, falls schon aktiv
        if (stabilizerActive) {
            stabilizerActive = false
        }
        stabilizerActive = true

        var attempts = 0
        var stableCount = 0
        var lastW = -1
        var lastH = -1

        val task = object : Runnable {
            override fun run() {
                if (!_isStarted || _isClosed) {
                    stabilizerActive = false
                    return
                }

                // Echte Framegröße bevorzugen
                var w = holder.surfaceFrame.width()
                var h = holder.surfaceFrame.height()

                // Fallbacks
                if (w <= 0 || h <= 0) {
                    w = width
                    h = height
                }

                // Falls Rotation bekannt: Plausibilität erzwingen (Landscape ↔ Portrait)
                expectedRotation?.let { rot ->
                    // ROTATION_90 (1) / ROTATION_270 (3) => Landscape
                    val landscape = (rot == 1 || rot == 3)
                    if (landscape && h > w) {
                        val t = w; w = h; h = t
                    } else if (!landscape && w > h) {
                        val t = w; w = h; h = t
                    }
                }

                // Stabilitätsprüfung
                if (w == lastW && h == lastH && w > 0 && h > 0) {
                    stableCount++
                } else {
                    stableCount = 0
                    lastW = w
                    lastH = h
                }

                attempts++

                // leicht gestrafft: 1 stabiler Tick oder max. 12 Versuche
                if ((stableCount >= 1 || attempts >= 12) && w > 0 && h > 0) {
                    ghLog("resize stabilized after $attempts ticks → ${w}x$h")
                    safeSetSize(w, h)
                    stabilizerActive = false
                    return
                }

                // weiter pollen
                if (stabilizerActive) {
                    mainHandler.postDelayed(this, 16)
                }
            }
        }

        mainHandler.post(task)
    }
}
