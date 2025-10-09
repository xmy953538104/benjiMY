package org.kenjinx.android

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.SimpleStorageHelper
import com.sun.jna.JNIEnv
import org.kenjinx.android.ui.theme.KenjinxAndroidTheme
import org.kenjinx.android.viewmodels.MainViewModel
import org.kenjinx.android.viewmodels.QuickSettings
import org.kenjinx.android.viewmodels.GameModel
import org.kenjinx.android.views.MainView
import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.view.Surface

class MainActivity : BaseActivity() {
    private var physicalControllerManager: PhysicalControllerManager =
        PhysicalControllerManager(this)
    private lateinit var motionSensorManager: MotionSensorManager
    private var _isInit: Boolean = false
    private val handler = Handler(Looper.getMainLooper())
    private val delayedHandleIntent = object : Runnable { override fun run() { handleIntent() } }
    var storedIntent: Intent = Intent()
    var isGameRunning = false
    var isActive = false
    var storageHelper: SimpleStorageHelper? = null
    lateinit var uiHandler: UiHandler

    // Display Rotation + Orientation Handling
    private lateinit var displayManager: DisplayManager
    private var lastKnownRotation: Int? = null
    private var pulsingOrientation = false
    private var lastPulseAt = 0L

    private val TAG_ROT = "RotationDebug"

    private fun rotLog(msg: String) {
        val enabled = BuildConfig.DEBUG && QuickSettings(this).enableDebugLogs
        if (enabled) Log.d(TAG_ROT, msg)
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display?.displayId != displayId
                } else {
                    TODO("VERSION.SDK_INT < R")
                }
            ) return
            val rot = display?.rotation
            if (rot == lastKnownRotation) return

            rotLog("onDisplayChanged: display.rotation=$rot → ${deg(rot)}°")

            val pref = QuickSettings(this@MainActivity).orientationPreference
            val old = lastKnownRotation
            lastKnownRotation = rot

            // 1) Inform Native/Renderer (applies to Sensor & SensorLandscape)
            try { KenjinxNative.setSurfaceRotationByAndroidRotation(rot) } catch (_: Throwable) {}

            // 2) Initiate host resize (applies to Sensor & SensorLandscape)
            if (isGameRunning) {
                handler.post {
                    try { mainViewModel?.gameHost?.onOrientationOrSizeChanged(rot) } catch (_: Throwable) {}
                }
            }

            // 3) Only with SENSOR_LANDSCAPE: gentle pulse with real 90↔270 flip
            if (pref == QuickSettings.OrientationPreference.SensorLandscape && old != null && rot != null) {
                val isSideFlip = (old == Surface.ROTATION_90 && rot == Surface.ROTATION_270) ||
                    (old == Surface.ROTATION_270 && rot == Surface.ROTATION_90)
                if (isSideFlip) doOrientationPulse(rot)
            }
        }
    }

    private fun deg(r: Int?): Int = when (r) {
        Surface.ROTATION_0 -> 0
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> -1
    }

    private fun doOrientationPulse(currentRot: Int) {
        val now = android.os.SystemClock.uptimeMillis()
        if (pulsingOrientation || now - lastPulseAt < 350L) return
        pulsingOrientation = true
        lastPulseAt = now

        // Short lock on the target page (prevents flickering)
        val lock = if (currentRot == Surface.ROTATION_90)
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        else
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE

        try { requestedOrientation = lock } catch (_: Throwable) {}
        handler.post {
            if (isGameRunning) {
                try { KenjinxNative.setSurfaceRotationByAndroidRotation(currentRot) } catch (_: Throwable) {}
                try { mainViewModel?.gameHost?.onOrientationOrSizeChanged(currentRot) } catch (_: Throwable) {}
            }
        }

        // After a short time back to SENSOR_LANDSCAPE
        handler.postDelayed({
            try { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE } catch (_: Throwable) {}
            handler.post {
                if (isGameRunning) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            KenjinxNative.setSurfaceRotationByAndroidRotation(display?.rotation)
                        }
                    } catch (_: Throwable) {}
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            mainViewModel?.gameHost?.onOrientationOrSizeChanged(display?.rotation)
                        }
                    } catch (_: Throwable) {}
                }
            }
            pulsingOrientation = false
        }, 250)
    }

    companion object {
        var mainViewModel: MainViewModel? = null
        var AppPath: String = ""
        var StorageHelper: SimpleStorageHelper? = null

        @JvmStatic
        fun frameEnded() {
            mainViewModel?.activity?.apply {
                if (isActive && QuickSettings(this).enablePerformanceMode) {
                    mainViewModel?.performanceManager?.setTurboMode(true)
                }
            }
            mainViewModel?.gameHost?.hideProgressIndicator()
        }

        // <<< is called from the Native/Lib page to set the loading progress
        @JvmStatic
        fun updateProgress(info: String, percent: Float) {
            // Route directly via the GameHost – it takes care of the progress states
            mainViewModel?.gameHost?.setProgress(info, percent)
        }
    }

    init {
        storageHelper = SimpleStorageHelper(this)
        StorageHelper = storageHelper
        System.loadLibrary("kenjinxjni")
        initVm()
    }

    private external fun initVm()

    private fun initialize() {
        if (_isInit) return
        val appPath: String = AppPath

        var quickSettings = QuickSettings(this)
        KenjinxNative.loggingSetEnabled(LogLevel.Info, quickSettings.enableInfoLogs)
        KenjinxNative.loggingSetEnabled(LogLevel.Stub, quickSettings.enableStubLogs)
        KenjinxNative.loggingSetEnabled(LogLevel.Warning, quickSettings.enableWarningLogs)
        KenjinxNative.loggingSetEnabled(LogLevel.Error, quickSettings.enableErrorLogs)
        KenjinxNative.loggingSetEnabled(LogLevel.AccessLog, quickSettings.enableFsAccessLogs)
        KenjinxNative.loggingSetEnabled(LogLevel.Guest, quickSettings.enableGuestLogs)
        KenjinxNative.loggingSetEnabled(LogLevel.Trace, quickSettings.enableTraceLogs)
        KenjinxNative.loggingSetEnabled(LogLevel.Debug, quickSettings.enableDebugLogs)
        KenjinxNative.loggingEnabledGraphicsLog(quickSettings.enableGraphicsLogs)

        _isInit = KenjinxNative.javaInitialize(appPath, JNIEnv.CURRENT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        motionSensorManager = MotionSensorManager(this)
        Thread.setDefaultUncaughtExceptionHandler(crashHandler)

        if (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                !Environment.isExternalStorageManager()
            } else {
                !Environment.isExternalStorageLegacy()
            }
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                storageHelper?.storage?.requestFullStorageAccess()
            }
        }

        AppPath = this.getExternalFilesDir(null)!!.absolutePath
        initialize()

        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Apply alignment
        applyOrientationPreference()

        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        uiHandler = UiHandler()
        displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager

        mainViewModel = MainViewModel(this)
        mainViewModel!!.physicalControllerManager = physicalControllerManager
        mainViewModel!!.motionSensorManager = motionSensorManager
        mainViewModel!!.refreshFirmwareVersion()

        mainViewModel?.apply {
            setContent {
                KenjinxAndroidTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainView.Main(mainViewModel = this)
                    }
                }
            }
        }

        storedIntent = intent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            rotLog("onCreate: initial display.rotation=${display?.rotation} → ${deg(display?.rotation)}°")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        storedIntent = intent
    }

    override fun onSaveInstanceState(outState: Bundle) {
        storageHelper?.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        storageHelper?.onRestoreInstanceState(savedInstanceState)
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        event.apply { if (physicalControllerManager.onKeyEvent(this)) return true }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent?): Boolean {
        ev?.apply { physicalControllerManager.onMotionEvent(this) }
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun onStop() {
        super.onStop()
        isActive = false
        if (isGameRunning) mainViewModel?.performanceManager?.setTurboMode(false)
    }

    override fun onResume() {
        super.onResume()
        // Reapply alignment if necessary
        applyOrientationPreference()

        // Enable display listener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            lastKnownRotation = display?.rotation
        rotLog("onResume: display.rotation=${display?.rotation} → ${deg(display?.rotation)}°")
        }
        try { displayManager.registerDisplayListener(displayListener, handler) } catch (_: Throwable) {}

        handler.postDelayed(delayedHandleIntent, 10)
        isActive = true
        if (isGameRunning && QuickSettings(this).enableMotion) motionSensorManager.register()
    }

    override fun onPause() {
        super.onPause()
        isActive = false
        if (isGameRunning) mainViewModel?.performanceManager?.setTurboMode(false)
        motionSensorManager.unregister()
        try { displayManager.unregisterDisplayListener(displayListener) } catch (_: Throwable) {}
    }

    private fun handleIntent() {
        when (storedIntent.action) {
            Intent.ACTION_VIEW, "org.kenjinx.android.LAUNCH_GAME" -> {
                val bootPath = storedIntent.getStringExtra("bootPath")
                val forceNceAndPptc = storedIntent.getBooleanExtra("forceNceAndPptc", false)

                if (bootPath != null) {
                    val uri = bootPath.toUri()
                    val documentFile = DocumentFile.fromSingleUri(this, uri)

                    if (documentFile != null) {
                        val gameModel = GameModel(documentFile, this)
                        gameModel.getGameInfo()
                        mainViewModel?.loadGameModel?.value = gameModel
                        mainViewModel?.bootPath?.value = "gameItem_${gameModel.titleName}"
                        mainViewModel?.forceNceAndPptc?.value = forceNceAndPptc
                    }
                }
            }
        }
    }

    private fun applyOrientationPreference() {
        val pref = QuickSettings(this).orientationPreference
        requestedOrientation = pref.value
        val rot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            this.display?.rotation
        } else {
            TODO("VERSION.SDK_INT < R")
        }
        rotLog("applyOrientationPreference: rot=$rot → ${deg(rot)}°, pref=${pref.name}")
        try { KenjinxNative.setSurfaceRotationByAndroidRotation(rot) } catch (_: Throwable) {}
    }

    fun shutdownAndRestart() {
        val packageManager = packageManager
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val componentName = intent?.component
        val restartIntent = Intent.makeRestartActivityTask(componentName)
        mainViewModel?.let { it.performanceManager?.setTurboMode(false) }
        startActivity(restartIntent)
        Runtime.getRuntime().exit(0)
    }
}
