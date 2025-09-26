package org.kenjinx.android
import androidx.activity.result.contract.ActivityResultContracts

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.net.Uri
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
import androidx.preference.PreferenceManager
import com.anggrayudi.storage.SimpleStorageHelper
import com.sun.jna.JNIEnv
import org.kenjinx.android.ui.theme.KenjinxAndroidTheme
import org.kenjinx.android.viewmodels.MainViewModel
import org.kenjinx.android.viewmodels.QuickSettings
import org.kenjinx.android.viewmodels.GameModel
import org.kenjinx.android.views.MainView
import java.io.File
import android.content.res.Configuration
import android.content.Context
import android.content.pm.ActivityInfo
import android.hardware.display.DisplayManager
import android.view.Surface
import org.kenjinx.android.BuildConfig

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

    // Display-Rotation + Orientation-Handling
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
            if (display?.displayId != displayId) return
            val rot = display?.rotation
            if (rot == lastKnownRotation) return

            rotLog("onDisplayChanged: display.rotation=$rot → ${deg(rot)}°")

            val pref = QuickSettings(this@MainActivity).orientationPreference
            val old = lastKnownRotation
            lastKnownRotation = rot

            // 1) Native/Renderer informieren (gilt für Sensor & SensorLandscape)
            try { KenjinxNative.setSurfaceRotationByAndroidRotation(rot) } catch (_: Throwable) {}

            // 2) Host-Resize anstoßen (gilt für Sensor & SensorLandscape)
            if (isGameRunning) {
                handler.post {
                    try { mainViewModel?.gameHost?.onOrientationOrSizeChanged(rot) } catch (_: Throwable) {}
                }
            }

            // 3) Nur bei SENSOR_LANDSCAPE: sanfter Pulse bei echtem 90↔270-Flip
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

        // kurzer Lock auf die Ziel-Seite (verhindert Flackern)
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

        // nach kurzer Zeit zurück auf SENSOR_LANDSCAPE
        handler.postDelayed({
            try { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE } catch (_: Throwable) {}
            handler.post {
                if (isGameRunning) {
                    try { KenjinxNative.setSurfaceRotationByAndroidRotation(display?.rotation) } catch (_: Throwable) {}
                    try { mainViewModel?.gameHost?.onOrientationOrSizeChanged(display?.rotation) } catch (_: Throwable) {}
                }
            }
            pulsingOrientation = false
        }, 250)
    }

    companion object {
        var mainViewModel: MainViewModel? = null
        var AppPath: String = ""
        var StorageHelper: SimpleStorageHelper? = null

        const val EXTRA_BOOT_PATH = "bootPath"
        const val EXTRA_FORCE_NCE_PPTC = "forceNceAndPptc"
        const val EXTRA_TITLE_ID = "titleId"
        const val EXTRA_TITLE_NAME = "titleName"

        @JvmStatic
        fun frameEnded() {
            mainViewModel?.activity?.apply {
                if (isActive && QuickSettings(this).enablePerformanceMode) {
                    mainViewModel?.performanceManager?.setTurboMode(true)
                }
            }
            mainViewModel?.gameHost?.hideProgressIndicator()
        }

        @JvmStatic
        fun updateProgress(info: String, percent: Float) {
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

        val quickSettings = QuickSettings(this)
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

        if (!Environment.isExternalStorageManager()) {
            storageHelper?.storage?.requestFullStorageAccess()
        }

        AppPath = this.getExternalFilesDir(null)!!.absolutePath
        initialize()

        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Ausrichtung anwenden
        applyOrientationPreference()

        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        uiHandler = UiHandler()
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

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
        rotLog("onCreate: initial display.rotation=${display?.rotation} → ${deg(display?.rotation)}°")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        storedIntent = intent
        handleIntent()
        storedIntent = Intent()
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
        // Ausrichtung ggf. erneut anwenden
        applyOrientationPreference()

        // Display-Listener aktivieren
        lastKnownRotation = display?.rotation
        rotLog("onResume: display.rotation=${display?.rotation} → ${deg(display?.rotation)}°")
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
        val action = storedIntent.action ?: return
        when (action) {
            Intent.ACTION_VIEW,
            "org.kenjinx.android.LAUNCH_GAME",
            "org.kenjisc.android.LAUNCH_GAME" -> {

                val bootPathExtra = storedIntent.getStringExtra(EXTRA_BOOT_PATH)
                val forceNceAndPptc = storedIntent.getBooleanExtra(EXTRA_FORCE_NCE_PPTC, false)
                val titleId = storedIntent.getStringExtra(EXTRA_TITLE_ID) ?: ""
                val titleName = storedIntent.getStringExtra(EXTRA_TITLE_NAME) ?: ""
                val dataUri: Uri? = storedIntent.data

                Log.d(
                    "ShortcutDebug",
                    "handleIntent(): action=$action, bootPathExtra=$bootPathExtra, dataUri=$dataUri, titleId=$titleId, titleName=$titleName"
                )

                val chosenUri: Uri? = when {
                    !bootPathExtra.isNullOrEmpty() -> bootPathExtra.toUri()
                    dataUri != null -> dataUri
                    else -> null
                }

                if (chosenUri != null) {
                    val doc = when (chosenUri.scheme?.lowercase()) {
                        "content" -> DocumentFile.fromSingleUri(this, chosenUri)
                        "file" -> chosenUri.path?.let { File(it) }?.let { DocumentFile.fromFile(it) }
                        else -> {
                            chosenUri.path?.let { File(it) }?.takeIf { it.exists() }?.let { DocumentFile.fromFile(it) }
                                ?: DocumentFile.fromSingleUri(this, chosenUri)
                        }
                    }

                    if (doc != null && doc.exists()) {
                        val gameModel = GameModel(doc, this)
                        gameModel.getGameInfo()
                        mainViewModel?.loadGameModel?.value = gameModel
                        mainViewModel?.bootPath?.value = "gameItem_${gameModel.titleName}"
                        mainViewModel?.forceNceAndPptc?.value = forceNceAndPptc
                        storedIntent = Intent()
                        return
                    } else {
                        Log.w("ShortcutDebug", "DocumentFile not found or not accessible: $chosenUri")
                    }
                }

                if (titleId.isNotEmpty() || titleName.isNotEmpty()) {
                    resolveGameByTitleIdOrName(titleId, titleName)?.let { doc ->
                        val gameModel = GameModel(doc, this)
                        gameModel.getGameInfo()
                        mainViewModel?.loadGameModel?.value = gameModel
                        mainViewModel?.bootPath?.value = "gameItem_${gameModel.titleName}"
                        mainViewModel?.forceNceAndPptc?.value = forceNceAndPptc
                        storedIntent = Intent()
                        return
                    }
                }
            }
        }
    }

    private fun applyOrientationPreference() {
        val pref = QuickSettings(this).orientationPreference
        requestedOrientation = pref.value
        val rot = this.display?.rotation
        rotLog("applyOrientationPreference: rot=$rot → ${deg(rot)}°, pref=${pref.name}")
        try { KenjinxNative.setSurfaceRotationByAndroidRotation(rot) } catch (_: Throwable) {}
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val rot = this.display?.rotation
        val old = lastKnownRotation
        lastKnownRotation = rot

        rotLog("onConfigurationChanged: display.rotation=$rot → ${deg(rot)}°")

        try { KenjinxNative.setSurfaceRotationByAndroidRotation(rot) } catch (_: Throwable) {}

        val pref = QuickSettings(this).orientationPreference
        val shouldPropagate =
            pref == QuickSettings.OrientationPreference.Sensor ||
                pref == QuickSettings.OrientationPreference.SensorLandscape

        if (shouldPropagate && isGameRunning) {
            handler.post { try { mainViewModel?.gameHost?.onOrientationOrSizeChanged(rot) } catch (_: Throwable) {} }
        }

        // Pulse bei 90↔270, falls SensorLandscape
        if (pref == QuickSettings.OrientationPreference.SensorLandscape && old != null && rot != null) {
            val isSideFlip = (old == Surface.ROTATION_90 && rot == Surface.ROTATION_270) ||
                (old == Surface.ROTATION_270 && rot == Surface.ROTATION_90)
            if (isSideFlip) doOrientationPulse(rot)
        }
    }

    // --- Hilfsfunktionen für Shortcut-Fallback ---
    private fun resolveGameByTitleIdOrName(titleIdHex: String?, displayName: String?): DocumentFile? {
        val gamesRoot = getDefaultGamesTree() ?: return null
        for (child in gamesRoot.listFiles()) {
            if (!child.isFile) continue
            if (!displayName.isNullOrBlank()) {
                val n = child.name ?: ""
                if (n.contains(displayName, ignoreCase = true)) return child
            }
            if (!titleIdHex.isNullOrBlank()) {
                val tid = getTitleIdFast(child)
                if (tid != null && tid.equals(titleIdHex, ignoreCase = true)) return child
            }
        }
        if (!titleIdHex.isNullOrBlank()) {
            for (child in gamesRoot.listFiles()) {
                if (!child.isFile) continue
                val tid = getTitleIdFast(child)
                if (tid != null && tid.equals(titleIdHex, ignoreCase = true)) return child
            }
        }
        return null
    }

    private fun getDefaultGamesTree(): DocumentFile? {
        val vm = mainViewModel
        if (vm?.defaultGameFolderUri != null) {
            return DocumentFile.fromTreeUri(this, vm.defaultGameFolderUri!!)
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val legacyPath = prefs.getString("gameFolder", null)
        if (!legacyPath.isNullOrEmpty()) {
            // Ohne SAF-URI kein Tree-Listing möglich
        }
        return null
    }

    private fun getTitleIdFast(file: DocumentFile): String? {
        val name = file.name ?: return null
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot >= name.length - 1) return null
        val ext = name.substring(dot + 1).lowercase()
        return try {
            contentResolver.openFileDescriptor(file.uri, "r")?.use { pfd ->
                val info = org.kenjinx.android.viewmodels.GameInfo()
                KenjinxNative.deviceGetGameInfo(pfd.fd, ext, info)
                info.TitleId?.lowercase()
            }
        } catch (_: Exception) { null }
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
