package org.kenjinx.android

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
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

    // last known Android rotation (0,1,2,3)
    private var lastRotation: Int? = null

    // Debounce for resize kick
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
            // Requery Surface / Window handle and report to C#
            currentSurface = _nativeWindow.requeryWindowHandle()
            _nativeWindow.swapInterval = 0
            try { KenjinxNative.deviceSetWindowHandle(currentWindowHandle) } catch (_: Throwable) {}
        }

        _width = width
        _height = height

        // Start renderer (if not already started)
        start(holder)

        // Do not set size immediately → Stabilizer takes over
        startStabilizedResize(expectedRotation = lastRotation)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // no-op (renderer lives in its own thread; close via close())
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

        // Initialize input
        KenjinxNative.inputInitialize(width, height)

        val id = mainViewModel.physicalControllerManager?.connect()
        mainViewModel.motionSensorManager?.setControllerId(id ?: -1)

        // No initial "flip" special case: we give the real rotation downwards
        val currentRot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mainViewModel.activity.display?.rotation
        } else {
            TODO("VERSION.SDK_INT < R")
        }
        lastRotation = currentRot
        try {
            KenjinxNative.setSurfaceRotationByAndroidRotation(currentRot)
            // Pass the window handle for safety reasons (if Surface has just been refreshed)
            try { KenjinxNative.deviceSetWindowHandle(currentWindowHandle) } catch (_: Throwable) {}
            // gentle kick: set identical size again
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
     * Called by the activity when the rotation/layout changes.
     * Detects 90°↔270° and forces (debounces) a requery/resize.
     */
    fun onOrientationOrSizeChanged(rotation: Int? = null) {
        if (_isClosed) return

        val old = lastRotation
        lastRotation = rotation

        val isSideFlip = (old == 1 && rotation == 3) || (old == 3 && rotation == 1)

        if (isSideFlip) {
            // 1) Report NativeRotation
            try { KenjinxNative.setSurfaceRotationByAndroidRotation(rotation) } catch (_: Throwable) {}

            // 2) Requery NativeWindow immediately (forces real rebind) + window handle to C#
            try {
                currentSurface = _nativeWindow.requeryWindowHandle()
                _nativeWindow.swapInterval = 0
                try { KenjinxNative.deviceSetWindowHandle(currentWindowHandle) } catch (_: Throwable) {}
            } catch (_: Throwable) {}

            // 3) Debounced kick of identical size (update swap chain/viewport)
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
     * Wait a moment until the surface has its final dimensions after rotation,
     * checks plausibility (portrait/landscape) and only then sets the size.
     */
    private fun startStabilizedResize(expectedRotation: Int?) {
        if (_isClosed) return

        // Restart if already active
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

                // Prefer real frame size
                var w = holder.surfaceFrame.width()
                var h = holder.surfaceFrame.height()

                // Fallbacks
                if (w <= 0 || h <= 0) {
                    w = width
                    h = height
                }

                // If rotation is known: Force plausibility (Landscape ↔ Portrait)
                expectedRotation?.let { rot ->
                    // ROTATION_90 (1) / ROTATION_270 (3) => Landscape
                    val landscape = (rot == 1 || rot == 3)
                    if (landscape && h > w) {
                        val t = w; w = h; h = t
                    } else if (!landscape && w > h) {
                        val t = w; w = h; h = t
                    }
                }

                // Stability test
                if (w == lastW && h == lastH && w > 0 && h > 0) {
                    stableCount++
                } else {
                    stableCount = 0
                    lastW = w
                    lastH = h
                }

                attempts++

                // slightly tightened: 1 stable tick or max. 12 attempts
                if ((stableCount >= 1 || attempts >= 12) && w > 0 && h > 0) {
                    ghLog("resize stabilized after $attempts ticks → ${w}x$h")
                    safeSetSize(w, h)
                    stabilizerActive = false
                    return
                }

                // continue to pollen
                if (stabilizerActive) {
                    mainHandler.postDelayed(this, 16)
                }
            }
        }

        mainHandler.post(task)
    }
}
