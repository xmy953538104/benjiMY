package org.kenjinx.android

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.SimpleStorageHelper
import com.sun.jna.JNIEnv
import org.kenjinx.android.ui.theme.KenjinxAndroidTheme
import org.kenjinx.android.viewmodels.GameModel
import org.kenjinx.android.viewmodels.MainViewModel
import org.kenjinx.android.viewmodels.QuickSettings
import org.kenjinx.android.views.MainView

class MainActivity : BaseActivity() {

    private var physicalControllerManager: PhysicalControllerManager = PhysicalControllerManager(this)
    private lateinit var motionSensorManager: MotionSensorManager
    private var _isInit: Boolean = false

    private val handler = Handler(Looper.getMainLooper())
    private val delayedHandleIntent = object : Runnable {
        override fun run() {
            handleIntent()
        }
    }

    var storedIntent: Intent = Intent()
    var isGameRunning = false
    var isActive = false
    var storageHelper: SimpleStorageHelper? = null
    lateinit var uiHandler: UiHandler

    companion object {
        var mainViewModel: MainViewModel? = null
        var AppPath: String = ""
        var StorageHelper: SimpleStorageHelper? = null
        val performanceMonitor = PerformanceMonitor()

        @JvmStatic
        fun frameEnded() {
            mainViewModel?.activity?.apply {
                if (isActive && QuickSettings(this).enablePerformanceMode) {
                    mainViewModel?.performanceManager?.setTurboMode(true)
                }
            }
            mainViewModel?.gameHost?.hideProgressIndicator()
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
        KenjinxNative.jnaInstance.loggingSetEnabled(LogLevel.Info.ordinal, quickSettings.enableInfoLogs)
        KenjinxNative.jnaInstance.loggingSetEnabled(LogLevel.Stub.ordinal, quickSettings.enableStubLogs)
        KenjinxNative.jnaInstance.loggingSetEnabled(LogLevel.Warning.ordinal, quickSettings.enableWarningLogs)
        KenjinxNative.jnaInstance.loggingSetEnabled(LogLevel.Error.ordinal, quickSettings.enableErrorLogs)
        KenjinxNative.jnaInstance.loggingSetEnabled(LogLevel.AccessLog.ordinal, quickSettings.enableFsAccessLogs)
        KenjinxNative.jnaInstance.loggingSetEnabled(LogLevel.Guest.ordinal, quickSettings.enableGuestLogs)
        KenjinxNative.jnaInstance.loggingSetEnabled(LogLevel.Trace.ordinal, quickSettings.enableTraceLogs)
        KenjinxNative.jnaInstance.loggingSetEnabled(LogLevel.Debug.ordinal, quickSettings.enableDebugLogs)
        KenjinxNative.jnaInstance.loggingEnabledGraphicsLog(quickSettings.enableGraphicsLogs)

        val success = KenjinxNative.jnaInstance.javaInitialize(appPath, JNIEnv.CURRENT)
        _isInit = success
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        motionSensorManager = MotionSensorManager(this)
        Thread.setDefaultUncaughtExceptionHandler(crashHandler)

        // Laufzeitberechtigungen für vollen Speicherzugriff (wie im Original)
        if (!Environment.isExternalStorageManager()) {
            storageHelper?.storage?.requestFullStorageAccess()
        }

        // App-Datenpfad
        AppPath = this.getExternalFilesDir(null)!!.absolutePath

        initialize()

        // Vollbild
        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // ViewModel
        mainViewModel = MainViewModel(this)
        mainViewModel!!.physicalControllerManager = physicalControllerManager
        mainViewModel!!.motionSensorManager = motionSensorManager
        mainViewModel!!.refreshFirmwareVersion()

        // UI
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
        event.apply {
            if (physicalControllerManager.onKeyEvent(this)) return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent?): Boolean {
        ev?.apply { physicalControllerManager.onMotionEvent(this) }
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun onStop() {
        super.onStop()
        isActive = false
        if (isGameRunning) {
            mainViewModel?.performanceManager?.setTurboMode(false)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(delayedHandleIntent, 10)
        isActive = true

        if (isGameRunning) {
            if (QuickSettings(this).enableMotion) motionSensorManager.register()
        }
    }

    override fun onPause() {
        super.onPause()
        isActive = true
        if (isGameRunning) {
            mainViewModel?.performanceManager?.setTurboMode(false)
        }
        motionSensorManager.unregister()
    }

    private fun handleIntent() {
        when (storedIntent.action) {
            Intent.ACTION_VIEW,
            "org.kenjinx.android.LAUNCH_GAME",
            "org.kenjisc.android.LAUNCH_GAME" -> {

                // 1) bootPath aus Extras, 2) Fallback auf Intent.data (z.B. von Shortcuts)
                val bootPath = storedIntent.getStringExtra("bootPath")
                val uri: Uri? = when {
                    bootPath != null -> Uri.parse(bootPath)
                    storedIntent.data != null -> storedIntent.data
                    else -> null
                }

                val forceNceAndPptc = storedIntent.getBooleanExtra("forceNceAndPptc", false)

                if (uri != null) {
                    // Persistente Lese-/Schreibrechte sichern, wenn möglich (SAF/Shortcut)
                    try {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    } catch (_: SecurityException) {
                        // Ignorieren: ggf. kam Uri nicht vom SAF/ohne persistierbare Rechte
                    }

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
}
